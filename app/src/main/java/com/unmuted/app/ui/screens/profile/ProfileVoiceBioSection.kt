package com.unmuted.app.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.StudioHaptics
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

@Composable
fun ProfileVoiceBioSection(
    profile: UserProfile,
    isOwnProfile: Boolean,
    playback: PlaybackState,
    accent: ProfileAccentPalette,
    waveStyle: WaveformStyle,
    haptics: StudioHaptics,
    onPlayVoiceBio: (String) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onOpenCustomizeSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBioActive = !profile.voiceBioMediaId.isNullOrBlank() &&
        playback.activeMediaId == profile.voiceBioMediaId
    val isBioPlaying = isBioActive && playback.playing
    val isBioBuffering = isBioActive && playback.buffering
    val bioDurationMs = if (isBioActive && playback.durationMs > 0L) {
        playback.durationMs
    } else {
        profile.voiceBioDurationMs.coerceAtLeast(1L)
    }
    val bioPosMs = if (isBioActive) playback.positionMs else 0L
    val bioProgress = (bioPosMs.toFloat() / bioDurationMs.toFloat()).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(
            width = if (isBioPlaying) 1.4.dp else 1.dp,
            color = if (isBioPlaying) accent.primary.copy(alpha = 0.78f) else accent.primary.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pinned_voice_bio_card")
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.primary.copy(alpha = if (isBioPlaying) 0.16f else 0.08f),
                            ObsidianSurface
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = accent.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "VOICE BIO · PINNED INTRO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (profile.voiceBioDurationMs > 0L) {
                        Text(
                            text = if (isBioActive) {
                                "${formatDurationMs(bioPosMs)} / ${formatDurationMs(bioDurationMs)}"
                            } else {
                                formatDurationMs(profile.voiceBioDurationMs)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBioPlaying) StudioWhite else TitaniumMuted
                        )
                    }
                    if (isOwnProfile && !profile.voiceBioMediaId.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceElevated,
                            modifier = Modifier.clickable {
                                haptics(HapticCue.CAPTION_TOGGLE)
                                onOpenCustomizeSheet()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Edit, null, tint = TitaniumMuted, modifier = Modifier.size(11.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Re-record", style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!profile.voiceBioMediaId.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent.primary, accent.secondary)))
                            .clickable {
                                haptics(HapticCue.REACTION)
                                onPlayVoiceBio(profile.voiceBioMediaId)
                            }
                            .testTag("play_voice_bio_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBioBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = StudioWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isBioPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isBioPlaying) "Pause voice bio" else "Play voice bio",
                                tint = StudioWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    InteractiveWaveform(
                        peaks = profile.voiceBioPeaks.map { it.toFloat() },
                        progress = bioProgress,
                        playing = isBioPlaying,
                        style = waveStyle,
                        accentColor = accent.primary,
                        secondaryColor = accent.secondary,
                        onSeekFraction = { frac ->
                            if (!isBioActive) onPlayVoiceBio(profile.voiceBioMediaId)
                            onSeekToMs((bioDurationMs * frac).toLong())
                        },
                        modifier = Modifier.weight(1f).height(36.dp)
                    )
                }
                if (!profile.voiceBioTranscript.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "“${profile.voiceBioTranscript}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioWhite.copy(alpha = 0.82f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isOwnProfile) {
                            "Pin a 15-second spoken intro right below your banner."
                        } else {
                            "No pinned voice bio yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TitaniumMuted,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOwnProfile) {
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                haptics(HapticCue.CAPTION_TOGGLE)
                                onOpenCustomizeSheet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("record_voice_bio_cta")
                        ) {
                            Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Record Intro", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
