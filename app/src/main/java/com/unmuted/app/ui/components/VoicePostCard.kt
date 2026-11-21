package com.unmuted.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.WaveformStyle

/**
 * Public entry-point Composable for VoicePostCard, delegating to the modular
 * post components under `com.unmuted.app.ui.components.post`.
 */
@Composable
fun VoicePostCard(
    post: VoicePost,
    playback: PlaybackState,
    reaction: UserPostReaction?,
    currentUid: String,
    waveformStyle: WaveformStyle = WaveformStyle.BARS,
    safetySettings: SafetySettings = SafetySettings(),
    onPlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenComments: () -> Unit,
    onEcho: () -> Unit,
    onBookmark: () -> Unit,
    onExpandLyrics: () -> Unit,
    onRoomClick: (String) -> Unit,
    onSpeakerClick: (String) -> Unit,
    onReport: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onMuteSpeaker: (() -> Unit)? = null,
    onBlockSpeaker: (() -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onQuoteClapback: (() -> Unit)? = null,
    onOpenDebateSide: ((DebateStance) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    com.unmuted.app.ui.components.post.VoicePostCard(
        post = post,
        playback = playback,
        reaction = reaction,
        currentUid = currentUid,
        waveformStyle = waveformStyle,
        safetySettings = safetySettings,
        onPlayPause = onPlayPause,
        onSeekToMs = onSeekToMs,
        onCycleSpeed = onCycleSpeed,
        onLike = onLike,
        onDislike = onDislike,
        onOpenComments = onOpenComments,
        onEcho = onEcho,
        onBookmark = onBookmark,
        onExpandLyrics = onExpandLyrics,
        onRoomClick = onRoomClick,
        onSpeakerClick = onSpeakerClick,
        onReport = onReport,
        onHide = onHide,
        onDelete = onDelete,
        onMuteSpeaker = onMuteSpeaker,
        onBlockSpeaker = onBlockSpeaker,
        onHashtagClick = onHashtagClick,
        onQuoteClapback = onQuoteClapback,
        onOpenDebateSide = onOpenDebateSide,
        modifier = modifier
    )
}
