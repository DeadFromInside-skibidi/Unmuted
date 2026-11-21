package com.unmuted.app.ui.screens.composer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unmuted.app.core.AudioPlayerManager
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.StudioHaptics
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.viewmodel.ComposerDraftState

@Composable
fun ComposerRecorderPane(
    draft: ComposerDraftState,
    recordingState: RecordingState,
    playback: PlaybackState,
    audioPlayer: AudioPlayerManager,
    haptics: StudioHaptics,
    onStartRecord: () -> Unit,
    onTogglePauseRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var micDeniedMessage by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micDeniedMessage = false
            haptics(HapticCue.RECORD_START)
            onStartRecord()
        } else {
            micDeniedMessage = true
        }
    }

    val isPreviewPlaying = playback.activeMediaId == "composer_preview" && playback.playing

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurfaceElevated,
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (recordingState.recording) ElectricCoral else StudioViolet.copy(alpha = 0.25f),
                RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (micDeniedMessage) {
                Text(
                    text = "Microphone permission is needed to record your voice. Tap below to grant access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AcousticError,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            if (recordingState.error != null) {
                Text(
                    text = recordingState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = AcousticError,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            when {
                draft.recordedFile == null && !recordingState.recording -> {
                    Text(
                        text = when {
                            draft.quotedPost != null -> "Tap to record your clapback right after their clip"
                            draft.postKind == PostKind.VOICE_DROP -> "Tap to record up to 30s Voice Drop"
                            else -> "Tap to speak up to 60s"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TitaniumMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricCoral, StudioViolet)))
                            .clickable {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    haptics(HapticCue.RECORD_START)
                                    onStartRecord()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .testTag("start_recording_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Start voice recording",
                            tint = StudioWhite,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                recordingState.recording -> {
                    Text(
                        text = if (recordingState.paused) "PAUSED" else "ON AIR",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCoral
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatDurationMs(recordingState.elapsedMs),
                        style = MaterialTheme.typography.displaySmall,
                        color = StudioWhite
                    )
                    Spacer(Modifier.height(12.dp))
                    InteractiveWaveform(
                        peaks = recordingState.liveAmplitudes,
                        progress = 1f,
                        playing = !recordingState.paused,
                        accentColor = ElectricCoral,
                        secondaryColor = StudioViolet
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                haptics(HapticCue.CAPTION_TOGGLE)
                                onTogglePauseRecord()
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = if (recordingState.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (recordingState.paused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = {
                                haptics(HapticCue.RECORD_STOP)
                                onStopRecord()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("stop_recording_btn")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Finish & Transcribe")
                        }
                    }
                }

                else -> {
                    val recordedFile = draft.recordedFile!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (draft.anonymousMode) StudioViolet else ElectricCoral)
                            .clickable {
                                audioPlayer.togglePlayLocalFile(
                                    mediaId = "composer_preview",
                                    file = recordedFile,
                                    disguise = draft.voiceDisguise
                                )
                            }
                            .testTag("preview_recording_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Preview recording",
                                tint = StudioWhite
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            InteractiveWaveform(
                                peaks = draft.waveformPeaks,
                                progress = if (playback.activeMediaId == "composer_preview" && playback.durationMs > 0) {
                                    playback.positionMs.toFloat() / playback.durationMs
                                } else 0f,
                                playing = isPreviewPlaying,
                                accentColor = if (draft.anonymousMode) StudioViolet else ElectricCoral
                            )
                            Text(
                                text = "${formatDurationMs(draft.durationMs)} · ${draft.voiceDisguise.badge}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TitaniumMuted
                            )
                        }
                        IconButton(onClick = onRetake) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Retake recording", tint = TitaniumMuted)
                        }
                    }
                }
            }
        }
    }
}
