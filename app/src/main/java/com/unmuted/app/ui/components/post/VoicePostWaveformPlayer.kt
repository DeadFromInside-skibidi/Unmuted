package com.unmuted.app.ui.components.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.PlaybackPhase
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.StudioHaptics
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import kotlin.math.roundToInt

@Composable
fun VoicePostWaveformPlayer(
    post: VoicePost,
    playback: PlaybackState,
    isThisActive: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isQuotedPhase: Boolean,
    isClapback: Boolean,
    activePlayColor: Color,
    primaryAccent: Color,
    accentSecondary: Color,
    durationMs: Long,
    displayPosMs: Long,
    progress: Float,
    splitFraction: Float?,
    waveformPeaks: List<Float>,
    waveformStyle: WaveformStyle,
    captionsOpen: Boolean,
    speedLabel: String,
    haptics: StudioHaptics,
    onCaptionsToggle: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (isClapback) {
            val phaseColor = if (isQuotedPhase) SignalCyan else if (isThisActive) primaryAccent else SignalCyan
            val phaseText = when {
                isQuotedPhase -> "QUOTED: ${post.quotedAuthorName ?: "Speaker"}"
                isThisActive -> "CLAPBACK: ${post.displaySpeakerName()}"
                else -> "CLAPBACK · ${formatDurationMs(post.quotedStartMs)}–${formatDurationMs(post.quotedEndMs)}"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("clapback_banner_${post.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatQuote,
                                contentDescription = "Quoted voice clip",
                                tint = SignalCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Clapback to ${post.quotedAuthorName ?: "Voice"}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SignalCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = phaseColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = phaseText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = phaseColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!post.quotedTranscript.isNullOrBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "“${post.quotedTranscript}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isQuotedPhase) StudioWhite else TitaniumMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(activePlayColor)
                    .clickable {
                        haptics(HapticCue.REACTION)
                        onPlayPause()
                    }
                    .testTag("play_button_${post.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = StudioWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause voice" else "Play voice",
                        tint = StudioWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                InteractiveWaveform(
                    peaks = waveformPeaks,
                    progress = progress,
                    playing = isPlaying,
                    style = waveformStyle,
                    accentColor = primaryAccent,
                    secondaryColor = accentSecondary,
                    speakerSplitFraction = splitFraction,
                    quotedAccentColor = SignalCyan,
                    onSeekFraction = { frac ->
                        if (!isThisActive) onPlayPause()
                        onSeekToMs((durationMs * frac).toLong())
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatDurationMs(displayPosMs)} / ${formatDurationMs(durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TitaniumMuted
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.09f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .clickable {
                                    haptics(HapticCue.CAPTION_TOGGLE)
                                    onCycleSpeed()
                                }
                                .testTag("speed_chip_${post.id}")
                        ) {
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioWhite,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (captionsOpen) primaryAccent.copy(alpha = 0.24f) else ObsidianSurface.copy(alpha = 0.72f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (captionsOpen) primaryAccent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f)
                            ),
                            modifier = Modifier
                                .clickable {
                                    haptics(HapticCue.CAPTION_TOGGLE)
                                    onCaptionsToggle()
                                }
                                .testTag("lyrics_toggle_${post.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (captionsOpen) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                                    contentDescription = "Toggle CC / Lyrics captions",
                                    tint = if (captionsOpen) primaryAccent else StudioWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (captionsOpen) "CC / Lyrics On" else "CC / Lyrics",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (captionsOpen) primaryAccent else StudioWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun stitchClapbackPeaks(
    quotedPeaks: List<Float>,
    mainPeaks: List<Float>,
    splitFraction: Float,
    totalBars: Int = 42
): List<Float> {
    val quoteCount = (totalBars * splitFraction).roundToInt().coerceIn(6, totalBars - 8)
    val mainCount = (totalBars - quoteCount).coerceAtLeast(8)
    val qSource = if (quotedPeaks.size >= 4) quotedPeaks else List(quoteCount) { 0.45f }
    val mSource = if (mainPeaks.size >= 4) mainPeaks else List(mainCount) { 0.55f }
    val qResampled = resamplePeaks(qSource, quoteCount)
    val mResampled = resamplePeaks(mSource, mainCount)
    return qResampled + mResampled
}

fun resamplePeaks(source: List<Float>, targetCount: Int): List<Float> {
    if (source.isEmpty() || targetCount <= 0) return emptyList()
    if (source.size == targetCount) return source
    return List(targetCount) { idx ->
        val pos = idx.toFloat() / targetCount.coerceAtLeast(1) * source.size
        source[pos.toInt().coerceIn(0, source.lastIndex)].coerceIn(0.15f, 1f)
    }
}
