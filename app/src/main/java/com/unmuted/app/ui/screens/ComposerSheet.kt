package com.unmuted.app.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import com.unmuted.app.core.AudioPlayerManager
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.ui.viewmodel.ComposerDraftState

/**
 * Public entry-point Composable for ComposerStudioSheet, delegating to the modular
 * composer subcomponents under `com.unmuted.app.ui.screens.composer`.
 */
@Composable
fun ComposerStudioSheet(
    draft: ComposerDraftState,
    recordingState: RecordingState,
    playback: PlaybackState,
    rooms: List<CommunityRoom>,
    currentUid: String,
    audioPlayer: AudioPlayerManager,
    onDismiss: () -> Unit,
    onSelectKind: (PostKind) -> Unit,
    onSelectRoom: (CommunityRoom?) -> Unit,
    onToggleAnonymous: (Boolean) -> Unit,
    onRollBurner: () -> Unit,
    onSelectDisguise: (VoiceDisguise) -> Unit,
    onToggleHotTake: (Boolean) -> Unit,
    onQuoteRangeChanged: (Long, Long) -> Unit,
    onPreviewQuoteClip: () -> Unit,
    onStartRecord: () -> Unit,
    onTogglePauseRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onRetake: () -> Unit,
    onRetryTranscribe: () -> Unit,
    onTranscriptChanged: (String) -> Unit,
    onHeadlineChanged: (String) -> Unit,
    onHashtagsChanged: (String) -> Unit,
    onAttachImageUri: (Uri) -> Unit,
    onClearImage: () -> Unit,
    onPublish: () -> Unit
) {
    com.unmuted.app.ui.screens.composer.ComposerStudioSheet(
        draft = draft,
        recordingState = recordingState,
        playback = playback,
        rooms = rooms,
        currentUid = currentUid,
        audioPlayer = audioPlayer,
        onDismiss = onDismiss,
        onSelectKind = onSelectKind,
        onSelectRoom = onSelectRoom,
        onToggleAnonymous = onToggleAnonymous,
        onRollBurner = onRollBurner,
        onSelectDisguise = onSelectDisguise,
        onToggleHotTake = onToggleHotTake,
        onQuoteRangeChanged = onQuoteRangeChanged,
        onPreviewQuoteClip = onPreviewQuoteClip,
        onStartRecord = onStartRecord,
        onTogglePauseRecord = onTogglePauseRecord,
        onStopRecord = onStopRecord,
        onRetake = onRetake,
        onRetryTranscribe = onRetryTranscribe,
        onTranscriptChanged = onTranscriptChanged,
        onHeadlineChanged = onHeadlineChanged,
        onHashtagsChanged = onHashtagsChanged,
        onAttachImageUri = onAttachImageUri,
        onClearImage = onClearImage,
        onPublish = onPublish
    )
}
