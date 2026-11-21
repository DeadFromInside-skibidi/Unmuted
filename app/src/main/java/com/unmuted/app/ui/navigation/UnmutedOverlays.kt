package com.unmuted.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.SafetyRelation
import com.unmuted.app.data.SafetyReportTarget
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoicePost
import com.unmuted.app.ui.components.CommentsBottomSheet
import com.unmuted.app.ui.components.SafetyControlsBottomSheet
import com.unmuted.app.ui.components.SafetyReportBottomSheet
import com.unmuted.app.ui.screens.ComposerStudioSheet
import com.unmuted.app.ui.screens.FullLyricsFocusDialog
import com.unmuted.app.ui.viewmodel.AudioPlaybackViewModel
import com.unmuted.app.ui.viewmodel.ComposerDraftState
import com.unmuted.app.ui.viewmodel.DataUiState
import com.unmuted.app.ui.viewmodel.FeedViewModel

@Composable
fun UnmutedOverlays(
    showComposer: Boolean,
    composerDraft: ComposerDraftState,
    recordingState: RecordingState,
    playback: PlaybackState,
    roomsWindow: List<CommunityRoom>,
    currentUid: String,
    myProfile: UserProfile,
    audioPlaybackViewModel: AudioPlaybackViewModel,
    feedViewModel: FeedViewModel,
    pagedPosts: LazyPagingItems<VoicePost>,
    pagedDiscoverPosts: LazyPagingItems<VoicePost>,
    onDismissComposer: () -> Unit,
    activeCommentsPost: VoicePost?,
    activeCommentsInitialStance: DebateStance,
    commentsState: DataUiState<List<VoiceComment>>,
    filteredAuthorUids: Set<String>,
    onCloseComments: () -> Unit,
    pendingReportTarget: SafetyReportTarget?,
    onDismissReport: () -> Unit,
    onSubmitReport: (SafetyReportTarget, com.unmuted.app.data.ReportCategory, String, Boolean) -> Unit,
    showSafetyControlsSheet: Boolean,
    safetySettings: SafetySettings,
    safetyRelations: List<SafetyRelation>,
    onDismissSafetyControls: () -> Unit,
    onUpdateSafetySettings: (SafetySettings) -> Unit,
    onRemoveSafetyRelation: (String) -> Unit,
    globalLyricsPost: VoicePost?,
    onDismissLyricsFocus: () -> Unit,
    onTogglePlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit
) {
    // Center Studio Mic Composer Sheet
    if (showComposer) {
        ComposerStudioSheet(
            draft = composerDraft,
            recordingState = recordingState,
            playback = playback,
            rooms = roomsWindow,
            currentUid = currentUid,
            audioPlayer = audioPlaybackViewModel.audioPlayer,
            onDismiss = {
                audioPlaybackViewModel.audioRecorder.reset()
                audioPlaybackViewModel.stopPlayback()
                onDismissComposer()
            },
            onSelectKind = { feedViewModel.updateComposerKind(it) },
            onSelectRoom = { feedViewModel.selectComposerRoom(it) },
            onToggleAnonymous = { feedViewModel.toggleComposerAnonymous(it) },
            onRollBurner = { feedViewModel.rollBurnerIdentity() },
            onSelectDisguise = { feedViewModel.setComposerDisguise(it) },
            onToggleHotTake = { feedViewModel.toggleComposerHotTake(it) },
            onQuoteRangeChanged = { s, e -> feedViewModel.updateQuoteRange(s, e) },
            onPreviewQuoteClip = {
                composerDraft.quotedPost?.let { qPost ->
                    audioPlaybackViewModel.previewQuotedClip(
                        quotedPost = qPost,
                        startMs = composerDraft.quoteStartMs,
                        endMs = composerDraft.quoteEndMs
                    )
                }
            },
            onStartRecord = { feedViewModel.startStudioRecording() },
            onTogglePauseRecord = { audioPlaybackViewModel.audioRecorder.togglePause() },
            onStopRecord = { feedViewModel.stopStudioRecording() },
            onRetake = { feedViewModel.retakeStudioRecording() },
            onRetryTranscribe = { feedViewModel.retryComposerTranscription() },
            onTranscriptChanged = { feedViewModel.updateComposerTranscript(it) },
            onHeadlineChanged = { feedViewModel.updateComposerHeadline(it) },
            onHashtagsChanged = { feedViewModel.updateComposerHashtags(it) },
            onAttachImageUri = { feedViewModel.attachComposerImage(it) },
            onClearImage = { feedViewModel.clearComposerImage() },
            onPublish = {
                feedViewModel.publishComposerPost(myProfile) {
                    onDismissComposer()
                    pagedPosts.refresh()
                    pagedDiscoverPosts.refresh()
                }
            }
        )
    }

    // Threaded Voice & Text Comments Sheet
    val commentsTarget = activeCommentsPost
    if (commentsTarget != null) {
        CommentsBottomSheet(
            post = commentsTarget,
            initialStance = activeCommentsInitialStance,
            commentsState = commentsState,
            filteredAuthorUids = filteredAuthorUids,
            playback = playback,
            recordingState = recordingState,
            audioRecorder = audioPlaybackViewModel.audioRecorder,
            onDismiss = onCloseComments,
            onPlayVoiceComment = { mediaId, disguise ->
                audioPlaybackViewModel.togglePlayMediaId(
                    mediaId = mediaId,
                    disguise = disguise,
                    speakerName = commentsTarget.displaySpeakerName(),
                    title = "Voice Reply"
                )
            },
            onUpvoteComment = { comment -> feedViewModel.upvoteComment(commentsTarget.id, comment) },
            onReportComment = { comment ->
                onSubmitReport(
                    SafetyReportTarget(
                        targetType = "COMMENT",
                        targetId = comment.id,
                        targetAuthorUid = comment.authorUid,
                        targetDisplayName = comment.displaySpeakerName()
                    ),
                    com.unmuted.app.data.ReportCategory.HARASSMENT_HATE,
                    "Comment reported from thread",
                    false
                )
            },
            onSendTextComment = { text, stance, done ->
                feedViewModel.submitTextComment(commentsTarget, myProfile, text, stance, done)
            },
            onSendVoiceComment = { file, dur, peaks, disguise, stance, done ->
                feedViewModel.submitVoiceComment(
                    post = commentsTarget,
                    author = myProfile,
                    audioFile = file,
                    durationMs = dur,
                    peaks = peaks,
                    disguise = disguise,
                    stance = stance,
                    onDone = done
                )
            }
        )
    }

    // Mandatory Structured Safety Report Sheet (Illegal & Harmful Content Compliance)
    val reportTarget = pendingReportTarget
    if (reportTarget != null) {
        SafetyReportBottomSheet(
            target = reportTarget,
            currentUid = currentUid,
            onDismiss = onDismissReport,
            onSubmit = { category, details, alsoBlock ->
                onSubmitReport(reportTarget, category, details, alsoBlock)
            }
        )
    }

    // User-Controlled Safety & Open Debate Controls Sheet
    if (showSafetyControlsSheet) {
        SafetyControlsBottomSheet(
            settings = safetySettings,
            relations = safetyRelations,
            onUpdateSettings = onUpdateSafetySettings,
            onRemoveRelation = onRemoveSafetyRelation,
            onDismiss = onDismissSafetyControls
        )
    }

    // Full-Screen Spotify-Style Word-Synced Lyrics Focus Dialog
    val lyricsModalPost = globalLyricsPost
    if (lyricsModalPost != null) {
        FullLyricsFocusDialog(
            post = lyricsModalPost,
            playback = playback,
            onPlayPause = { onTogglePlayPost(lyricsModalPost) },
            onSeekToMs = onSeekToMs,
            onDismiss = onDismissLyricsFocus
        )
    }
}
