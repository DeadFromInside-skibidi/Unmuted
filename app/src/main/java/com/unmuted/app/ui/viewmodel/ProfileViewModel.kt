package com.unmuted.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.TranscriptionService
import com.unmuted.app.core.bakeVoiceDisguise
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.repository.AudioStorageRepository
import com.unmuted.app.data.repository.FeedRepository
import com.unmuted.app.data.repository.ProfileRepository
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

class ProfileViewModel(
    private val appContext: Context,
    private val profileRepository: ProfileRepository,
    private val feedRepository: FeedRepository,
    private val audioStorageRepository: AudioStorageRepository,
    private val transcriptionService: TranscriptionService,
    private val messageBus: AppMessageBus
) : ViewModel() {

    val currentUid: String = Firebase.auth.currentUser?.uid.orEmpty()

    val myProfileState: StateFlow<DataUiState<UserProfile?>> = profileRepository.observeMyProfile()
        .map<UserProfile?, DataUiState<UserProfile?>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load profile")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val allProfilesState: StateFlow<DataUiState<List<UserProfile>>> = profileRepository.observePublicProfiles()
        .map<List<UserProfile>, DataUiState<List<UserProfile>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load speakers")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val followingUidsState: StateFlow<Set<String>> = profileRepository.observeFollowingUids()
        .catch { error ->
            if (unwrapIsPureCancellation(error)) throw error
            emit(emptySet())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val _profileReplies = MutableStateFlow<List<Pair<VoicePost, VoiceComment>>>(emptyList())
    val profileReplies: StateFlow<List<Pair<VoicePost, VoiceComment>>> = _profileReplies.asStateFlow()

    private val _profileRepliesLoading = MutableStateFlow(false)
    val profileRepliesLoading: StateFlow<Boolean> = _profileRepliesLoading.asStateFlow()
    private var profileRepliesJob: Job? = null

    init {
        val defaultName = Firebase.auth.currentUser?.displayName ?: "Unmuted Speaker"
        viewModelScope.launch {
            profileRepository.ensureProfileExists(defaultName)
        }
    }

    fun currentProfileOrDefault(): UserProfile {
        val loaded = (myProfileState.value as? DataUiState.Success)?.data
        if (loaded != null) return loaded
        val name = Firebase.auth.currentUser?.displayName ?: "Unmuted Speaker"
        return UserProfile(
            id = currentUid,
            userId = currentUid,
            displayName = name,
            handle = "speaker_${currentUid.takeLast(4).ifBlank { "01" }}"
        )
    }

    fun loadRepliesForProfile(targetUid: String, candidatePosts: List<VoicePost>) {
        profileRepliesJob?.cancel()
        profileRepliesJob = viewModelScope.launch {
            _profileRepliesLoading.value = true
            val replies = feedRepository.fetchRepliesByAuthor(targetUid, candidatePosts)
            _profileReplies.value = replies
            _profileRepliesLoading.value = false
        }
    }

    fun toggleFollow(targetUid: String) {
        val isFollowing = followingUidsState.value.contains(targetUid)
        viewModelScope.launch {
            profileRepository.toggleFollow(targetUid, isFollowing).onSuccess {
                messageBus.emit(if (isFollowing) "Unfollowed speaker" else "Following speaker")
            }
        }
    }

    fun saveProfileCustomization(
        displayName: String,
        handle: String,
        bio: String,
        accentKey: String,
        bannerKey: String,
        waveformStyle: String,
        interests: List<String>,
        newAvatarUri: Uri?,
        newVoiceBioFile: File?,
        newVoiceBioDurationMs: Long,
        newVoiceBioPeaks: List<Float>,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            var avatarB64: String? = null
            if (newAvatarUri != null) {
                avatarB64 = ImageCompressor.compressUriToBase64(appContext, newAvatarUri, maxDimension = 320, quality = 72)
                    .getOrNull()
            }
            var voiceBioUrl: String? = null
            var voiceBioTranscript: String? = null
            var voiceBioDur: Long? = null
            var voiceBioPks: List<Double>? = null

            if (newVoiceBioFile != null && newVoiceBioFile.exists()) {
                var bakedBioFile: File? = null
                var uploadBioFile = newVoiceBioFile
                try {
                    try {
                        uploadBioFile = bakeVoiceDisguise(appContext, newVoiceBioFile, VoiceDisguise.NONE)
                        bakedBioFile = uploadBioFile
                    } catch (e: Exception) {
                        Log.e("ProfileViewModel", "Voice bio studio polish failed", e)
                    }
                    val trRes = transcriptionService.transcribeAudioFile(uploadBioFile, newVoiceBioDurationMs)
                    trRes.onFailure { err ->
                        Log.e("TranscriptionService", "Voice Bio transcription failed", err)
                        messageBus.emit("Could not generate captions for voice bio. Saving audio.")
                    }
                    val tr = trRes.getOrNull()
                    val upload = audioStorageRepository.uploadBioAudio(currentUid, uploadBioFile)
                    upload.onSuccess { url ->
                        voiceBioUrl = url
                        voiceBioTranscript = tr?.transcript ?: "Pinned Voice Bio"
                        voiceBioDur = newVoiceBioDurationMs
                        voiceBioPks = newVoiceBioPeaks.map { it.toDouble() }
                    }.onFailure { err ->
                        Log.e("ProfileViewModel", "Voice bio upload failed", err)
                        messageBus.emit("Could not upload voice bio.")
                    }
                } finally {
                    runCatching { newVoiceBioFile.delete() }
                    if (bakedBioFile != null && bakedBioFile != newVoiceBioFile) {
                        runCatching { bakedBioFile.delete() }
                    }
                }
            }

            profileRepository.updateProfile(
                displayName = displayName,
                handle = handle,
                bio = bio,
                accentKey = accentKey,
                bannerKey = bannerKey,
                waveformStyle = waveformStyle,
                interests = interests,
                avatarBase64 = avatarB64,
                voiceBioAudioUrl = voiceBioUrl,
                voiceBioTranscript = voiceBioTranscript,
                voiceBioDurationMs = voiceBioDur,
                voiceBioPeaks = voiceBioPks
            ).fold(
                onSuccess = {
                    messageBus.emit("Studio profile updated!")
                    onSaved()
                },
                onFailure = { err ->
                    messageBus.emit(err.localizedMessage ?: "Failed to save profile.")
                }
            )
        }
    }

    class Factory(private val container: UnmutedContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(
                appContext = container.appContext,
                profileRepository = container.profileRepository,
                feedRepository = container.feedRepository,
                audioStorageRepository = container.audioStorageRepository,
                transcriptionService = container.transcriptionService,
                messageBus = container.messageBus
            ) as T
        }
    }
}
