package com.unmuted.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.PlaybackPhase
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

@Composable
fun FloatingMiniPlayerBar(
    playback: PlaybackState,
    onTogglePlayPause: () -> Unit,
    onCycleSpeed: () -> Unit,
    onDismiss: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    allowVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val visible = allowVisible && !playback.activeMediaId.isNullOrBlank() && playback.nowPlaying != null
    val haptics = rememberStudioHaptics()

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val meta = playback.nowPlaying ?: return@AnimatedVisibility
        val isQuotedPhase = meta.isClapback && playback.activePhase == PlaybackPhase.QUOTED_CLIP
        val palette = ProfileAccentPalette.fromKey(meta.accentKey)
        val accent = when {
            isQuotedPhase -> SignalCyan
            meta.anonymousMode -> StudioViolet
            else -> palette.primary
        }

        val totalMs = (if (meta.isClapback && playback.combinedDurationMs > 0L) playback.combinedDurationMs else playback.durationMs)
            .coerceAtLeast(1L)
        val posMs = if (meta.isClapback) playback.combinedPositionMs else playback.positionMs
        val progress = (posMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        val speedLabel = when (playback.speed) {
            1.5f -> "1.5x"
            2.0f -> "2x"
            else -> "1x"
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurfaceElevated.copy(alpha = 0.90f),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.16f),
                        palette.secondary.copy(alpha = 0.55f)
                    )
                )
            ),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable {
                    haptics.trigger(HapticCue.CAPTION_TOGGLE)
                    onOpenNowPlaying()
                }
                .testTag("floating_mini_player")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accent.copy(alpha = 0.18f),
                                Color(0xFF131620).copy(alpha = 0.86f),
                                palette.secondary.copy(alpha = 0.12f)
                            )
                        )
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatarBadge(
                            name = if (isQuotedPhase) (meta.quotedAuthorName ?: "Quoted") else meta.speakerName,
                            avatarBase64 = if (meta.anonymousMode || isQuotedPhase) null else meta.avatarBase64,
                            accentKey = meta.accentKey,
                            anonymousMode = meta.anonymousMode && !isQuotedPhase,
                            size = 40.dp,
                            showRing = playback.playing
                        )

                        Spacer(Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MiniEqualizerBars(
                                    playing = playback.playing,
                                    color = accent
                                )
                                Text(
                                    text = meta.title.ifBlank { meta.speakerName },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isQuotedPhase) {
                                        "Quoted: ${meta.quotedAuthorName ?: "Clip"}"
                                    } else {
                                        meta.speakerName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = "· ${formatDurationMs(posMs)} / ${formatDurationMs(totalMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clickable {
                                    haptics.trigger(HapticCue.CAPTION_TOGGLE)
                                    onCycleSpeed()
                                }
                                .testTag("mini_player_speed_btn")
                        ) {
                            Text(
                                text = speedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StudioWhite,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable {
                                    haptics.trigger(HapticCue.REACTION)
                                    onTogglePlayPause()
                                }
                                .testTag("mini_player_play_pause_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playback.buffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = StudioWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playback.playing) "Pause" else "Play",
                                    tint = StudioWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptics.trigger(HapticCue.CAPTION_TOGGLE)
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("mini_player_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close mini player",
                                tint = TitaniumMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceAtLeast(0.01f))
                                .height(2.5.dp)
                                .background(Brush.horizontalGradient(listOf(accent, palette.secondary)))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniEqualizerBars(
    playing: Boolean,
    color: Color
) {
    val transition = rememberInfiniteTransition(label = "mini_eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(530, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(12.dp)
    ) {
        listOf(
            if (playing) bar1 else 0.35f,
            if (playing) bar2 else 0.65f,
            if (playing) bar3 else 0.4f
        ).forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((12.dp * frac).coerceAtLeast(3.dp))
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
