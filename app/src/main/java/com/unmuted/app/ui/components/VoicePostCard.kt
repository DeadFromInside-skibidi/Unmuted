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
 * Determines whether this [VoicePost] matches the currently active media playing in [PlaybackState].
 */
fun VoicePost.isPlaybackActive(playback: PlaybackState): Boolean {
    val isClapback = hasQuoteClip()
    val expectedPlaybackId = if (isClapback) "clap_$id" else audioMediaId
    return playback.activeMediaId == id ||
        playback.nowPlaying?.sourcePostId == id ||
        (!expectedPlaybackId.isNullOrBlank() && playback.activeMediaId == expectedPlaybackId) ||
        (!audioUrl.isNullOrBlank() && playback.activeMediaId == audioUrl)
}

/**
 * 120Hz Zero-Recomposition VoicePostCard.
 * Receives primitives so inactive posts are skipped during playback ticks.
 */
@Composable
fun VoicePostCard(
    post: VoicePost,
    isActive: Boolean,
    isPlaying: Boolean,
    positionMs: Long = 0L,
    durationMs: Long = post.durationMs,
    isBuffering: Boolean = false,
    isQuotedPhase: Boolean = false,
    playbackSpeed: Float = 1.0f,
    playbackPositionMsProvider: (() -> Long)? = null,
    quoteSplitFraction: Float? = null,
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
        isActive = isActive,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        isBuffering = isBuffering,
        isQuotedPhase = isQuotedPhase,
        playbackSpeed = playbackSpeed,
        playbackPositionMsProvider = playbackPositionMsProvider,
        quoteSplitFraction = quoteSplitFraction,
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
