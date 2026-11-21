package com.unmuted.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.unmuted.app.core.AudioPlayerManager
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.TranscriptionService
import com.unmuted.app.core.bakeVoiceDisguise
import com.unmuted.app.core.buildSyncedCaptionSegments
import com.unmuted.app.data.AliasGenerator
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.ReportCategory
import com.unmuted.app.data.SafetyRelation
import com.unmuted.app.data.SafetyRelationType
import com.unmuted.app.data.SafetyReportTarget
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.repository.AudioStorageRepository
import com.unmuted.app.data.repository.FeedRepository
import com.unmuted.app.data.repository.SafetyRepository
import com.unmuted.app.data.repository.nextReverseChronoId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class FeedViewModel(
    private val appContext: Context,
    private val feedRepository: FeedRepository,
    private val audioStorageRepository: AudioStorageRepository,
    private val safetyRepository: SafetyRepository,
    private val audioPlayer: AudioPlayerManager,
    private val audioRecorder: AudioRecorderController,
    private val transcriptionService: TranscriptionService,
    private val messageBus: AppMessageBus
) : ViewModel() {

    val currentUid: String = Firebase.auth.currentUser?.uid.orEmpty()
    val snackbarMessage: StateFlow<String?> = messageBus.message

    // Paging 3 paginated voice feed (incremental Firestore loading)
    val pagedPosts: Flow<PagingData<VoicePost>> = feedRepository.pagedPostsFlow(pageSize = 15)
        .cachedIn(viewModelScope)

    // Bounded real-time window for 24h Voice Drops, Pulses, and Profile takes
    val recentPostsState: StateFlow<DataUiState<List<VoicePost>>> = feedRepository.observeRecentPosts(limit = 35L)
        .map<List<VoicePost>, DataUiState<List<VoicePost>>> { DataUiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load voices")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    // Optimistic local counter overrides so Paging 3 items reflect likes/dislikes/echoes/comments instantly
    private val _postOverrides = MutableStateFlow<Map<String, VoicePost>>(emptyMap())
    val postOverrides: StateFlow<Map<String, VoicePost>> = _postOverrides.asStateFlow()

    val reactionsState: StateFlow<Map<String, UserPostReaction>> = feedRepository.observeUserReactions()
        .catch { error ->
            if (unwrapIsPureCancellation(error)) throw error
            emit(emptyMap())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    private val _hiddenPostIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPostIds: StateFlow<Set<String>> = _hiddenPostIds.asStateFlow()

    // --- PLAY STORE COMPLIANT SAFETY RAILS ---

    val safetySettings: StateFlow<SafetySettings> = safetyRepository.observeSafetySettings()
        .catch { error ->
            if (unwrapIsPureCancellation(error)) throw error
            emit(SafetySettings())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SafetySettings())

    val safetyRelations: StateFlow<List<SafetyRelation>> = safetyRepository.observeSafetyRelations()
        .catch { error ->
            if (unwrapIsPureCancellation(error)) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val blockedUids: StateFlow<Set<String>> = safetyRelations
        .map { list -> list.filter { it.typeEnum() == SafetyRelationType.BLOCK }.map { it.targetUid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val mutedUids: StateFlow<Set<String>> = safetyRelations
        .map { list -> list.filter { it.typeEnum() == SafetyRelationType.MUTE }.map { it.targetUid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val filteredAuthorUids: StateFlow<Set<String>> = safetyRelations
        .map { list -> list.map { it.targetUid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    // --- COMPOSER STATE ---

    private val _composerState = MutableStateFlow(ComposerDraftState())
    val composerState: StateFlow<ComposerDraftState> = _composerState.asStateFlow()

    // --- COMMENTS SHEET ---

    private val _activeCommentsPost = MutableStateFlow<VoicePost?>(null)
    val activeCommentsPost: StateFlow<VoicePost?> = _activeCommentsPost.asStateFlow()

    private val _activeCommentsInitialStance = MutableStateFlow(DebateStance.NONE)
    val activeCommentsInitialStance: StateFlow<DebateStance> = _activeCommentsInitialStance.asStateFlow()

    private val _commentsState = MutableStateFlow<DataUiState<List<VoiceComment>>>(DataUiState.Success(emptyList()))
    val commentsState: StateFlow<DataUiState<List<VoiceComment>>> = _commentsState.asStateFlow()
    private var commentsJob: Job? = null

    fun clearSnackbar() = messageBus.clear()

    fun showMessage(msg: String) = messageBus.emit(msg)

    fun refreshPosts() {
        _postOverrides.value = emptyMap()
        feedRepository.invalidatePostsPaging()
    }

    // --- SAFETY ACTIONS ---

    fun updateSafetySettings(settings: SafetySettings) {
        viewModelScope.launch {
            safetyRepository.updateSafetySettings(settings).fold(
                onSuccess = { showMessage("Safety preferences updated") },
                onFailure = { showMessage("Could not update safety settings.") }
            )
        }
    }

    fun muteSpeaker(targetUid: String, targetName: String) {
        if (targetUid.isBlank() || targetUid == currentUid) return
        viewModelScope.launch {
            safetyRepository.setUserRelation(targetUid, targetName, SafetyRelationType.MUTE).fold(
                onSuccess = { showMessage("Muted $targetName") },
                onFailure = { showMessage("Could not mute speaker.") }
            )
        }
    }

    fun blockSpeaker(targetUid: String, targetName: String) {
        if (targetUid.isBlank() || targetUid == currentUid) return
        viewModelScope.launch {
            safetyRepository.setUserRelation(targetUid, targetName, SafetyRelationType.BLOCK).fold(
                onSuccess = { showMessage("Blocked $targetName") },
                onFailure = { showMessage("Could not block speaker.") }
            )
        }
    }

    fun removeSafetyRelation(targetUid: String) {
        viewModelScope.launch {
            safetyRepository.removeUserRelation(targetUid).fold(
                onSuccess = { showMessage("Speaker restored to your feed") },
                onFailure = { showMessage("Could not update speaker filter.") }
            )
        }
    }

    fun submitSafetyReport(
        target: SafetyReportTarget,
        category: ReportCategory,
        details: String,
        alsoBlockSpeaker: Boolean
    ) {
        viewModelScope.launch {
            safetyRepository.submitSafetyReport(
                targetType = target.targetType,
                targetId = target.targetId,
                category = category,
                details = details,
                targetAuthorUid = target.targetAuthorUid
            ).fold(
                onSuccess = {
                    if (target.targetType == "POST") {
                        _hiddenPostIds.value = _hiddenPostIds.value + target.targetId
                    }
                    if (alsoBlockSpeaker && !target.targetAuthorUid.isNullOrBlank() && target.targetAuthorUid != currentUid) {
                        safetyRepository.setUserRelation(
                            targetUid = target.targetAuthorUid,
                            targetName = target.targetDisplayName,
                            relationType = SafetyRelationType.BLOCK
                        )
                    }
                    showMessage(
                        if (category.urgentLegalReview) {
                            "Urgent illegal-content report submitted and escalated."
                        } else {
                            "Report submitted for safety review."
                        }
                    )
                },
                onFailure = {
                    showMessage("Could not submit report. Please try again.")
                }
            )
        }
    }

    // --- COMPOSER STUDIO ---

    fun prepareComposer(
        kind: PostKind = PostKind.FEED_VOICE,
        room: CommunityRoom? = null,
        hotTake: Boolean = false,
        quotedPost: VoicePost? = null
    ) {
        audioRecorder.reset()
        audioPlayer.stop()
        val sourceDur = quotedPost?.durationMs?.coerceAtLeast(1000L) ?: 0L
        val defaultEnd = if (quotedPost != null) {
            minOf(sourceDur, 10_000L).coerceAtLeast(minOf(sourceDur, 5_000L))
        } else 0L
        val anonRoom = room?.anonymousRoom == true
        val initialNonce = if (anonRoom) (System.currentTimeMillis() % 97).toInt() else 0
        _composerState.value = ComposerDraftState(
            postKind = kind,
            selectedRoom = room,
            anonymousMode = anonRoom,
            burnerNonce = initialNonce,
            voiceDisguise = if (anonRoom) VoiceDisguise.DEEP else VoiceDisguise.NONE,
            hotTakeMode = hotTake,
            quotedPost = quotedPost,
            quoteStartMs = 0L,
            quoteEndMs = defaultEnd
        )
    }

    fun updateComposerKind(kind: PostKind) {
        _composerState.value = _composerState.value.copy(postKind = kind)
    }

    fun selectComposerRoom(room: CommunityRoom?) {
        val forceAnon = room?.anonymousRoom == true
        val cur = _composerState.value
        _composerState.value = cur.copy(
            selectedRoom = room,
            anonymousMode = if (forceAnon) true else cur.anonymousMode,
            burnerNonce = if (forceAnon && cur.burnerNonce == 0) (System.currentTimeMillis() % 97).toInt() + 1 else cur.burnerNonce,
            voiceDisguise = if (forceAnon && cur.voiceDisguise == VoiceDisguise.NONE) {
                VoiceDisguise.DEEP
            } else cur.voiceDisguise
        )
    }

    fun toggleComposerAnonymous(enabled: Boolean) {
        val lockedByRoom = _composerState.value.selectedRoom?.anonymousRoom == true
        val effective = lockedByRoom || enabled
        val cur = _composerState.value
        _composerState.value = cur.copy(
            anonymousMode = effective,
            voiceDisguise = when {
                !effective -> VoiceDisguise.NONE
                cur.voiceDisguise == VoiceDisguise.NONE -> VoiceDisguise.DEEP
                else -> cur.voiceDisguise
            }
        )
    }

    fun rollBurnerIdentity() {
        val cur = _composerState.value
        _composerState.value = cur.copy(burnerNonce = cur.burnerNonce + 1 + (System.currentTimeMillis() % 17).toInt())
    }

    fun toggleComposerHotTake(enabled: Boolean) {
        _composerState.value = _composerState.value.copy(hotTakeMode = enabled)
    }

    fun updateQuoteRange(startMs: Long, endMs: Long) {
        val qPost = _composerState.value.quotedPost ?: return
        val maxDur = qPost.durationMs.coerceAtLeast(1000L)
        val clampedStart = startMs.coerceIn(0L, (maxDur - 1000L).coerceAtLeast(0L))
        val minSpan = minOf(5_000L, maxDur)
        val clampedEnd = endMs.coerceIn((clampedStart + minSpan).coerceAtMost(maxDur), minOf(clampedStart + 15_000L, maxDur))
        _composerState.value = _composerState.value.copy(
            quoteStartMs = clampedStart,
            quoteEndMs = clampedEnd
        )
    }

    fun setComposerDisguise(disguise: VoiceDisguise) {
        val cur = _composerState.value
        _composerState.value = cur.copy(voiceDisguise = disguise)
        audioPlayer.updateDisguisePreview(disguise)
        val file = cur.recordedFile
        if (file != null && file.exists() && disguise != VoiceDisguise.NONE) {
            viewModelScope.launch {
                try {
                    val baked = bakeVoiceDisguise(appContext, file, disguise)
                    _composerState.value = _composerState.value.copy(recordedFile = baked)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun updateComposerHeadline(text: String) {
        _composerState.value = _composerState.value.copy(headlineText = text.take(280))
    }

    fun updateComposerHashtags(text: String) {
        _composerState.value = _composerState.value.copy(hashtagsInput = text.take(120))
    }

    fun updateComposerTranscript(text: String) {
        val current = _composerState.value
        val segs = buildSyncedCaptionSegments(text, current.durationMs.coerceAtLeast(1500L))
        _composerState.value = current.copy(transcript = text.take(4000), captionSegments = segs)
    }

    fun startStudioRecording() {
        audioPlayer.stop()
        val maxMs = if (_composerState.value.postKind == PostKind.VOICE_DROP) 30_000L else 60_000L
        audioRecorder.startRecording(maxDurationMs = maxMs) { file, dur, peaks ->
            onStudioRecordingFinished(file, dur, peaks)
        }
    }

    fun stopStudioRecording() {
        val result = audioRecorder.stopRecording()
        if (result != null) {
            onStudioRecordingFinished(result.first, result.second, result.third)
        }
    }

    fun retakeStudioRecording() {
        audioPlayer.stop()
        audioRecorder.reset()
        _composerState.value = _composerState.value.copy(
            recordedFile = null,
            durationMs = 0L,
            waveformPeaks = emptyList(),
            transcribing = false,
            transcript = "",
            captionSegments = emptyList(),
            transcriptionError = null,
            publishError = null
        )
    }

    private fun onStudioRecordingFinished(file: File, durationMs: Long, peaks: List<Float>) {
        _composerState.value = _composerState.value.copy(
            recordedFile = file,
            durationMs = durationMs,
            waveformPeaks = peaks,
            transcribing = true,
            transcriptionError = null
        )
        runComposerTranscription(file, durationMs)
    }

    fun retryComposerTranscription() {
        val st = _composerState.value
        val file = st.recordedFile ?: return
        _composerState.value = st.copy(transcribing = true, transcriptionError = null)
        runComposerTranscription(file, st.durationMs)
    }

    private fun runComposerTranscription(file: File, durationMs: Long) {
        viewModelScope.launch {
            val draft = _composerState.value
            val effectiveDisguise = if (draft.anonymousMode && draft.voiceDisguise == VoiceDisguise.NONE) {
                VoiceDisguise.DEEP
            } else {
                draft.voiceDisguise
            }
            val readyFile = if (effectiveDisguise != VoiceDisguise.NONE) {
                try {
                    bakeVoiceDisguise(appContext, file, effectiveDisguise)
                } catch (_: Exception) {
                    file
                }
            } else {
                file
            }
            _composerState.value = _composerState.value.copy(
                recordedFile = readyFile,
                voiceDisguise = effectiveDisguise
            )
            val result = transcriptionService.transcribeAudioFile(readyFile, durationMs)
            result.fold(
                onSuccess = { tr ->
                    _composerState.value = _composerState.value.copy(
                        transcribing = false,
                        transcript = tr.transcript,
                        captionSegments = tr.segments,
                        transcriptionError = null
                    )
                },
                onFailure = { err ->
                    _composerState.value = _composerState.value.copy(
                        transcribing = false,
                        transcriptionError = err.message ?: "Transcription failed."
                    )
                }
            )
        }
    }

    fun attachComposerImage(uri: Uri) {
        viewModelScope.launch {
            val res = ImageCompressor.compressUriToBase64(appContext, uri, maxDimension = 720, quality = 70)
            res.fold(
                onSuccess = { b64 -> _composerState.value = _composerState.value.copy(attachmentImageBase64 = b64) },
                onFailure = { showMessage("Could not attach image.") }
            )
        }
    }

    fun clearComposerImage() {
        _composerState.value = _composerState.value.copy(attachmentImageBase64 = null)
    }

    fun publishComposerPost(author: UserProfile, onPublished: () -> Unit) {
        val draft = _composerState.value
        val file = draft.recordedFile
        if (file == null || !file.exists()) {
            _composerState.value = draft.copy(publishError = "Record your voice first before publishing.")
            return
        }
        viewModelScope.launch {
            _composerState.value = draft.copy(publishing = true, publishError = null)
            val effectiveDisguise = if (draft.anonymousMode && draft.voiceDisguise == VoiceDisguise.NONE) {
                VoiceDisguise.DEEP
            } else {
                draft.voiceDisguise
            }
            val uploadFile = if (effectiveDisguise != VoiceDisguise.NONE) {
                try {
                    bakeVoiceDisguise(appContext, file, effectiveDisguise)
                } catch (_: Exception) {
                    file
                }
            } else {
                file
            }
            _composerState.value = _composerState.value.copy(recordedFile = uploadFile)
            val tempPostId = nextReverseChronoId("post")
            val uploadResult = audioStorageRepository.uploadPostAudio(tempPostId, uploadFile)
            val audioUrl = uploadResult.getOrElse { err ->
                _composerState.value = _composerState.value.copy(
                    publishing = false,
                    publishError = err.localizedMessage ?: "Audio upload failed."
                )
                return@launch
            }

            val parsedTags = draft.hashtagsInput
                .split(Regex("[,\\s]+"))
                .map { it.trim().removePrefix("#") }
                .filter { it.isNotEmpty() }
                .take(8)

            val customAlias = if (draft.anonymousMode || draft.selectedRoom?.anonymousRoom == true) {
                AliasGenerator.generateForRoom(currentUid, draft.selectedRoom?.id ?: "global_unfiltered", draft.burnerNonce)
            } else null

            val qPost = draft.quotedPost
            val quotedSliceTranscript = if (qPost != null) {
                val matchingSegs = qPost.captionSegments.filter { seg ->
                    seg.endMs >= draft.quoteStartMs && seg.startMs <= draft.quoteEndMs
                }
                if (matchingSegs.isNotEmpty()) matchingSegs.joinToString(" ") { it.text }
                else qPost.transcript.take(200)
            } else null

            val quotedSlicePeaks = if (qPost != null && qPost.waveformPeaks.isNotEmpty() && qPost.durationMs > 0L) {
                val total = qPost.waveformPeaks.size
                val sIdx = ((draft.quoteStartMs.toFloat() / qPost.durationMs) * total).toInt().coerceIn(0, total - 1)
                val eIdx = ((draft.quoteEndMs.toFloat() / qPost.durationMs) * total).toInt().coerceIn(sIdx + 1, total)
                qPost.waveformPeaks.subList(sIdx, eIdx).map { it.toFloat() }
            } else emptyList()

            val postResult = feedRepository.createVoicePost(
                author = author,
                postKind = draft.postKind,
                audioUrl = audioUrl,
                durationMs = draft.durationMs,
                waveformPeaks = draft.waveformPeaks,
                transcript = draft.transcript,
                captionSegments = draft.captionSegments,
                room = draft.selectedRoom,
                anonymousMode = draft.anonymousMode,
                voiceDisguise = draft.voiceDisguise,
                headlineText = draft.headlineText,
                attachmentImageBase64 = draft.attachmentImageBase64,
                hashtags = parsedTags,
                customAnonymousAlias = customAlias,
                hotTakeMode = draft.hotTakeMode,
                quotedPost = qPost,
                quotedStartMs = draft.quoteStartMs,
                quotedEndMs = draft.quoteEndMs,
                quotedTranscript = quotedSliceTranscript,
                quotedWaveformPeaks = quotedSlicePeaks,
                explicitPostId = tempPostId
            )

            postResult.fold(
                onSuccess = {
                    audioPlayer.stop()
                    audioRecorder.reset()
                    _composerState.value = ComposerDraftState()
                    showMessage(
                        when {
                            qPost != null -> "Voice Clapback published!"
                            draft.hotTakeMode -> "Hot Take Debate is live!"
                            else -> "Voice published to Unmuted!"
                        }
                    )
                    onPublished()
                },
                onFailure = { err ->
                    _composerState.value = _composerState.value.copy(
                        publishing = false,
                        publishError = err.localizedMessage ?: "Could not publish post."
                    )
                }
            )
        }
    }

    // --- SOCIAL INTERACTIONS ---

    fun votePost(post: VoicePost, voteType: VoteType) {
        val effectivePost = _postOverrides.value[post.id] ?: post
        val current = reactionsState.value[post.id]
        viewModelScope.launch {
            feedRepository.toggleVote(effectivePost, current, voteType).fold(
                onSuccess = { (newLikes, newDislikes) ->
                    _postOverrides.value = _postOverrides.value + (
                        post.id to effectivePost.copy(likeCount = newLikes, dislikeCount = newDislikes)
                    )
                },
                onFailure = { showMessage("Could not update vote.") }
            )
        }
    }

    fun echoPost(post: VoicePost) {
        val effectivePost = _postOverrides.value[post.id] ?: post
        val current = reactionsState.value[post.id]
        viewModelScope.launch {
            feedRepository.toggleEcho(effectivePost, current).onSuccess { newEchoes ->
                _postOverrides.value = _postOverrides.value + (post.id to effectivePost.copy(echoCount = newEchoes))
                val echoed = !(current?.echoed ?: false)
                showMessage(if (echoed) "Echoed to your profile" else "Removed echo")
            }
        }
    }

    fun bookmarkPost(post: VoicePost) {
        val current = reactionsState.value[post.id]
        viewModelScope.launch {
            feedRepository.toggleBookmark(post, current).onSuccess {
                val saved = !(current?.bookmarked ?: false)
                showMessage(if (saved) "Saved to your studio" else "Removed from saved")
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            feedRepository.deletePost(postId).onSuccess {
                _hiddenPostIds.value = _hiddenPostIds.value + postId
                showMessage("Voice deleted")
            }.onFailure {
                showMessage("Could not delete voice")
            }
        }
    }

    fun hidePostLocally(postId: String) {
        _hiddenPostIds.value = _hiddenPostIds.value + postId
        showMessage("Voice hidden from your feed")
    }

    // --- COMMENTS ---

    fun openComments(post: VoicePost, initialStance: DebateStance = DebateStance.NONE) {
        _activeCommentsPost.value = _postOverrides.value[post.id] ?: post
        _activeCommentsInitialStance.value = if (post.hotTakeMode) {
            if (initialStance == DebateStance.NONE) DebateStance.AGREE else initialStance
        } else DebateStance.NONE
        commentsJob?.cancel()
        _commentsState.value = DataUiState.Loading
        commentsJob = viewModelScope.launch {
            feedRepository.observeComments(post.id)
                .map<List<VoiceComment>, DataUiState<List<VoiceComment>>> { DataUiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load replies")) }
                .collect { _commentsState.value = it }
        }
    }

    fun closeComments() {
        commentsJob?.cancel()
        commentsJob = null
        _activeCommentsPost.value = null
        _activeCommentsInitialStance.value = DebateStance.NONE
    }

    fun submitTextComment(
        post: VoicePost,
        author: UserProfile,
        text: String,
        stance: DebateStance = DebateStance.NONE,
        onDone: () -> Unit
    ) {
        if (text.isBlank()) return
        val effectivePost = _postOverrides.value[post.id] ?: post
        viewModelScope.launch {
            feedRepository.addComment(
                post = effectivePost,
                author = author,
                textBody = text,
                audioUrl = null,
                durationMs = 0L,
                waveformPeaks = emptyList(),
                transcript = null,
                captionSegments = emptyList(),
                voiceDisguise = VoiceDisguise.NONE,
                stance = stance
            ).onSuccess {
                val updated = effectivePost.copy(
                    commentCount = effectivePost.commentCount + 1L,
                    agreeCount = effectivePost.agreeCount + if (effectivePost.hotTakeMode && stance == DebateStance.AGREE) 1L else 0L,
                    disagreeCount = effectivePost.disagreeCount + if (effectivePost.hotTakeMode && stance == DebateStance.DISAGREE) 1L else 0L
                )
                _postOverrides.value = _postOverrides.value + (post.id to updated)
                if (_activeCommentsPost.value?.id == post.id) _activeCommentsPost.value = updated
                onDone()
            }.onFailure {
                showMessage("Could not post reply.")
            }
        }
    }

    fun submitVoiceComment(
        post: VoicePost,
        author: UserProfile,
        audioFile: File,
        durationMs: Long,
        peaks: List<Float>,
        disguise: VoiceDisguise,
        stance: DebateStance = DebateStance.NONE,
        onDone: () -> Unit
    ) {
        val effectivePost = _postOverrides.value[post.id] ?: post
        viewModelScope.launch {
            val effectiveDisguise = if (post.anonymousMode && disguise == VoiceDisguise.NONE) {
                VoiceDisguise.DEEP
            } else {
                disguise
            }
            val readyFile = if (effectiveDisguise != VoiceDisguise.NONE) {
                try {
                    bakeVoiceDisguise(appContext, audioFile, effectiveDisguise)
                } catch (_: Exception) {
                    audioFile
                }
            } else {
                audioFile
            }
            val transcriptRes = transcriptionService.transcribeAudioFile(readyFile, durationMs)
            val tr = transcriptRes.getOrNull()
            val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "").take(18)}"
            val uploadRes = audioStorageRepository.uploadCommentAudio(commentId, readyFile)
            val audioUrl = uploadRes.getOrElse {
                showMessage("Could not upload voice reply.")
                return@launch
            }
            feedRepository.addComment(
                post = effectivePost,
                author = author,
                textBody = null,
                audioUrl = audioUrl,
                durationMs = durationMs,
                waveformPeaks = peaks,
                transcript = tr?.transcript ?: "Voice reply",
                captionSegments = tr?.segments ?: emptyList(),
                voiceDisguise = effectiveDisguise,
                stance = stance,
                explicitCommentId = commentId
            ).onSuccess {
                val updated = effectivePost.copy(
                    commentCount = effectivePost.commentCount + 1L,
                    agreeCount = effectivePost.agreeCount + if (effectivePost.hotTakeMode && stance == DebateStance.AGREE) 1L else 0L,
                    disagreeCount = effectivePost.disagreeCount + if (effectivePost.hotTakeMode && stance == DebateStance.DISAGREE) 1L else 0L
                )
                _postOverrides.value = _postOverrides.value + (post.id to updated)
                if (_activeCommentsPost.value?.id == post.id) _activeCommentsPost.value = updated
                onDone()
            }.onFailure {
                showMessage("Could not save voice reply.")
            }
        }
    }

    fun upvoteComment(postId: String, comment: VoiceComment) {
        viewModelScope.launch {
            feedRepository.upvoteComment(postId, comment)
        }
    }

    class Factory(private val container: UnmutedContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedViewModel(
                appContext = container.appContext,
                feedRepository = container.feedRepository,
                audioStorageRepository = container.audioStorageRepository,
                safetyRepository = container.safetyRepository,
                audioPlayer = container.audioPlayer,
                audioRecorder = container.audioRecorder,
                transcriptionService = container.transcriptionService,
                messageBus = container.messageBus
            ) as T
        }
    }
}
