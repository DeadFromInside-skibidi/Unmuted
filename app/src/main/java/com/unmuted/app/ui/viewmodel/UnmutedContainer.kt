package com.unmuted.app.ui.viewmodel

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.unmuted.app.core.AudioPlayerManager
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.TranscriptionService
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.repository.AudioStorageRepository
import com.unmuted.app.data.repository.DiscoverRepository
import com.unmuted.app.data.repository.FeedRepository
import com.unmuted.app.data.repository.FirestoreProvider
import com.unmuted.app.data.repository.InboxRepository
import com.unmuted.app.data.repository.ProfileRepository
import com.unmuted.app.data.repository.RoomsRepository
import com.unmuted.app.data.repository.SafetyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

typealias DiscoverFilter = com.unmuted.app.data.DiscoverFilter

data class ComposerDraftState(
    val postKind: PostKind = PostKind.FEED_VOICE,
    val selectedRoom: CommunityRoom? = null,
    val anonymousMode: Boolean = false,
    val burnerNonce: Int = 0,
    val voiceDisguise: VoiceDisguise = VoiceDisguise.NONE,
    val hotTakeMode: Boolean = false,
    val quotedPost: VoicePost? = null,
    val quoteStartMs: Long = 0L,
    val quoteEndMs: Long = 0L,
    val recordedFile: File? = null,
    val durationMs: Long = 0L,
    val waveformPeaks: List<Float> = emptyList(),
    val transcribing: Boolean = false,
    val transcript: String = "",
    val captionSegments: List<CaptionSegment> = emptyList(),
    val transcriptionError: String? = null,
    val headlineText: String = "",
    val hashtagsInput: String = "",
    val attachmentImageBase64: String? = null,
    val publishing: Boolean = false,
    val publishError: String? = null
)

class AppMessageBus {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun emit(text: String) {
        _message.value = text
    }

    fun clear() {
        _message.value = null
    }
}

internal fun unwrapIsPureCancellation(error: Throwable): Boolean {
    val fsErr = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    return fsErr == null && error is CancellationException
}

internal fun <T> unwrapFlowError(error: Throwable, fallback: String): DataUiState<T> {
    val fsErr = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (fsErr == null && error is CancellationException) throw error
    val root = fsErr ?: error
    return DataUiState.Error(root.message ?: fallback)
}

/**
 * Lightweight Constructor-Injected Dependency Container scoped to an authenticated user session.
 * Wires all modular Repositories, Audio Engines, and ViewModel Factories cleanly without heavy DI frameworks.
 */
class UnmutedContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val db: FirebaseFirestore = FirestoreProvider.getDatabase(appContext)

    val audioStorageRepository: AudioStorageRepository = AudioStorageRepository()
    val feedRepository: FeedRepository = FeedRepository(db)
    val discoverRepository: DiscoverRepository = DiscoverRepository(db)
    val roomsRepository: RoomsRepository = RoomsRepository(db)
    val inboxRepository: InboxRepository = InboxRepository(db, audioStorageRepository)
    val profileRepository: ProfileRepository = ProfileRepository(db)
    val safetyRepository: SafetyRepository = SafetyRepository(db)

    val audioPlayer: AudioPlayerManager = AudioPlayerManager(appContext)
    val audioRecorder: AudioRecorderController = AudioRecorderController(appContext)
    val transcriptionService: TranscriptionService = TranscriptionService()
    val messageBus: AppMessageBus = AppMessageBus()

    fun release() {
        audioPlayer.release()
        audioRecorder.release()
    }
}
