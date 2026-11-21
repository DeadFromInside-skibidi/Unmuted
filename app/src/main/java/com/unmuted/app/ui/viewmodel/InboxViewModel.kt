package com.unmuted.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.TranscriptionService
import com.unmuted.app.core.bakeVoiceDisguise
import com.unmuted.app.data.Conversation
import com.unmuted.app.data.DirectMessage
import com.unmuted.app.data.MessageKind
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.repository.InboxRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class InboxViewModel(
    private val appContext: Context,
    private val inboxRepository: InboxRepository,
    private val transcriptionService: TranscriptionService,
    private val messageBus: AppMessageBus
) : ViewModel() {

    val conversationsState: StateFlow<DataUiState<List<Conversation>>> = inboxRepository.observeConversations()
        .map<List<Conversation>, DataUiState<List<Conversation>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load inbox")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _messagesState = MutableStateFlow<DataUiState<List<DirectMessage>>>(DataUiState.Success(emptyList()))
    val messagesState: StateFlow<DataUiState<List<DirectMessage>>> = _messagesState.asStateFlow()
    private var messagesJob: Job? = null

    fun openConversation(conversation: Conversation) {
        _activeConversation.value = conversation
        messagesJob?.cancel()
        _messagesState.value = DataUiState.Loading
        messagesJob = viewModelScope.launch {
            inboxRepository.observeMessages(conversation.id)
                .map<List<DirectMessage>, DataUiState<List<DirectMessage>>> { DataUiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load messages")) }
                .collect { _messagesState.value = it }
        }
    }

    fun closeConversation() {
        messagesJob?.cancel()
        messagesJob = null
        _activeConversation.value = null
    }

    fun startConversationWith(myProfile: UserProfile, otherProfile: UserProfile, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            inboxRepository.getOrCreateConversation(myProfile, otherProfile).fold(
                onSuccess = { conv ->
                    openConversation(conv)
                    onOpened()
                },
                onFailure = {
                    messageBus.emit("Could not start conversation.")
                }
            )
        }
    }

    fun sendTextDm(conversation: Conversation, sender: UserProfile, text: String, onSent: () -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            inboxRepository.sendMessage(
                conversation = conversation,
                sender = sender,
                kind = MessageKind.TEXT,
                textBody = text
            ).onSuccess { onSent() }.onFailure { messageBus.emit("Failed to send message.") }
        }
    }

    fun sendVoiceDm(
        conversation: Conversation,
        sender: UserProfile,
        audioFile: File,
        durationMs: Long,
        peaks: List<Float>,
        onSent: () -> Unit
    ) = sendVoiceDm(conversation, sender, audioFile, durationMs, peaks, VoiceDisguise.NONE, onSent)

    fun sendVoiceDm(
        conversation: Conversation,
        sender: UserProfile,
        audioFile: File,
        durationMs: Long,
        peaks: List<Float>,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        onSent: () -> Unit
    ) {
        viewModelScope.launch {
            var bakedFile: File? = null
            var readyFile = audioFile
            try {
                if (disguise != VoiceDisguise.NONE) {
                    try {
                        readyFile = bakeVoiceDisguise(appContext, audioFile, disguise)
                        bakedFile = readyFile
                    } catch (e: Exception) {
                        Log.e("InboxViewModel", "Voice DM disguise baking failed", e)
                        messageBus.emit("Voice masking failed. Recording was not uploaded to protect your identity.")
                        return@launch
                    }
                }
                val transcriptRes = transcriptionService.transcribeAudioFile(readyFile, durationMs)
                transcriptRes.onFailure { err ->
                    Log.e("TranscriptionService", "Voice DM transcription failed", err)
                    messageBus.emit("Could not transcribe voice note. Sending audio.")
                }
                val tr = transcriptRes.getOrNull()
                inboxRepository.sendMessage(
                    conversation = conversation,
                    sender = sender,
                    kind = MessageKind.VOICE,
                    audioFile = readyFile,
                    durationMs = durationMs,
                    waveformPeaks = peaks,
                    transcript = tr?.transcript ?: "Voice note",
                    captionSegments = tr?.segments ?: emptyList()
                ).onSuccess { onSent() }.onFailure { messageBus.emit("Failed to send voice note.") }
            } finally {
                runCatching { audioFile.delete() }
                if (bakedFile != null && bakedFile != audioFile) {
                    runCatching { bakedFile.delete() }
                }
            }
        }
    }

    fun sendImageDm(conversation: Conversation, sender: UserProfile, uri: Uri) {
        viewModelScope.launch {
            val b64 = ImageCompressor.compressUriToBase64(appContext, uri, maxDimension = 640, quality = 68)
                .getOrElse {
                    messageBus.emit("Could not prepare image.")
                    return@launch
                }
            inboxRepository.sendMessage(
                conversation = conversation,
                sender = sender,
                kind = MessageKind.IMAGE,
                imageBase64 = b64
            ).onFailure { messageBus.emit("Failed to send photo.") }
        }
    }

    class Factory(private val container: UnmutedContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InboxViewModel(
                appContext = container.appContext,
                inboxRepository = container.inboxRepository,
                transcriptionService = container.transcriptionService,
                messageBus = container.messageBus
            ) as T
        }
    }
}
