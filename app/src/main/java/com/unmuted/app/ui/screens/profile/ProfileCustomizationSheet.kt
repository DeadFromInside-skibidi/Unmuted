package com.unmuted.app.ui.screens.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.rememberStudioHaptics
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.StudioBannerMood
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCustomizationSheet(
    profile: UserProfile,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    onDismiss: () -> Unit,
    onSave: (
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
        newVoiceBioPeaks: List<Float>
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberStudioHaptics()
    var displayName by remember { mutableStateOf(profile.displayName) }
    var handle by remember { mutableStateOf(profile.handle) }
    var bio by remember { mutableStateOf(profile.bio) }
    var accentKey by remember { mutableStateOf(profile.accentKey) }
    var bannerKey by remember { mutableStateOf(profile.bannerKey) }
    var waveStyle by remember { mutableStateOf(profile.waveformStyle) }
    var interestsText by remember { mutableStateOf(profile.interests.joinToString(", ")) }
    var selectedAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var voiceBioFile by remember { mutableStateOf<File?>(null) }
    var voiceBioDur by remember { mutableStateOf(0L) }
    var voiceBioPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }

    val previewAccent = ProfileAccentPalette.fromKey(accentKey)
    val previewWaveStyle = WaveformStyle.fromKey(waveStyle)

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedAvatarUri = uri }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            haptics(HapticCue.RECORD_START)
            audioRecorder.startRecording(maxDurationMs = 15_000L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Customize Profile Studio", style = MaterialTheme.typography.headlineSmall, color = StudioWhite)

            // Live Accent & Waveform Signature Preview
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, previewAccent.primary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth().testTag("studio_waveform_live_preview")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE WAVEFORM PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = previewAccent.primary
                        )
                        Text(
                            text = "${previewAccent.label} · ${previewWaveStyle.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TitaniumMuted
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    InteractiveWaveform(
                        peaks = remember {
                            listOf(
                                0.3f, 0.55f, 0.8f, 0.45f, 0.92f, 0.7f, 0.4f, 0.85f,
                                0.6f, 0.35f, 0.78f, 0.95f, 0.5f, 0.65f, 0.88f, 0.42f,
                                0.72f, 0.58f, 0.9f, 0.52f, 0.38f, 0.76f, 0.62f, 0.44f
                            )
                        },
                        progress = 0.68f,
                        playing = true,
                        style = previewWaveStyle,
                        accentColor = previewAccent.primary,
                        secondaryColor = previewAccent.secondary,
                        modifier = Modifier.height(38.dp)
                    )
                }
            }

            Text("STUDIO ACCENT COLOR", style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAccentPalette.entries.forEach { pal ->
                    val chosen = pal.key == accentKey
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(pal.primary, pal.secondary)))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = if (chosen) StudioWhite else ObsidianHairline,
                                shape = CircleShape
                            )
                            .clickable {
                                haptics(HapticCue.CAPTION_TOGGLE)
                                accentKey = pal.key
                            }
                            .testTag("accent_pick_${pal.key.lowercase()}")
                    )
                }
            }

            Text("WAVEFORM SIGNATURE STYLE", style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WaveformStyle.entries) { style ->
                    val chosen = waveStyle == style.key
                    FilterChip(
                        selected = chosen,
                        onClick = {
                            haptics(HapticCue.CAPTION_TOGGLE)
                            waveStyle = style.key
                        },
                        label = { Text(style.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = previewAccent.primary.copy(alpha = 0.24f),
                            selectedLabelColor = StudioWhite
                        ),
                        modifier = Modifier.testTag("wave_style_${style.key.lowercase()}")
                    )
                }
            }

            Text("BANNER MOOD", style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StudioBannerMood.entries) { mood ->
                    FilterChip(
                        selected = bannerKey == mood.key,
                        onClick = {
                            haptics(HapticCue.CAPTION_TOGGLE)
                            bannerKey = mood.key
                        },
                        label = { Text(mood.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = previewAccent.primary.copy(alpha = 0.24f),
                            selectedLabelColor = StudioWhite
                        )
                    )
                }
            }

            // Record 15s Voice Bio
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (recordingState.recording) ElectricCoral else previewAccent.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("15s Pinned Voice Bio", style = MaterialTheme.typography.titleSmall, color = StudioWhite)
                        Text(
                            text = when {
                                recordingState.recording -> "Recording · ${formatDurationMs(recordingState.elapsedMs)}"
                                voiceBioFile != null -> "New Voice Bio ready (${formatDurationMs(voiceBioDur)})"
                                else -> "Auto-transcribed with word-synced captions"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TitaniumMuted
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (recordingState.recording) ElectricCoral else previewAccent.primary,
                        modifier = Modifier
                            .size(46.dp)
                            .clickable {
                                if (recordingState.recording) {
                                    haptics(HapticCue.RECORD_STOP)
                                    val res = audioRecorder.stopRecording()
                                    if (res != null) {
                                        voiceBioFile = res.first
                                        voiceBioDur = res.second
                                        voiceBioPeaks = res.third
                                    }
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        haptics(HapticCue.RECORD_START)
                                        audioRecorder.startRecording(maxDurationMs = 15_000L)
                                    } else {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            .testTag("studio_record_voice_bio_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (recordingState.recording) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = "Record voice bio",
                                tint = StudioWhite
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedAvatarUri != null) "Avatar Selected (Ready to Save)" else "Choose Custom Avatar Photo")
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it.take(60) },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it.lowercase().replace(Regex("[^a-z0-9_.]"), "").take(30) },
                label = { Text("@Handle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(240) },
                label = { Text("Bio") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = interestsText,
                onValueChange = { interestsText = it.take(180) },
                label = { Text("Topics / Interests (comma separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    haptics(HapticCue.REACTION)
                    saving = true
                    val interests = interestsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(12)
                    onSave(
                        displayName,
                        handle,
                        bio,
                        accentKey,
                        bannerKey,
                        waveStyle,
                        interests,
                        selectedAvatarUri,
                        voiceBioFile,
                        voiceBioDur,
                        voiceBioPeaks
                    )
                },
                enabled = !saving && !recordingState.recording && displayName.isNotBlank() && handle.length >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = previewAccent.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_profile_studio_btn")
            ) {
                Text(if (saving) "Saving Studio..." else "Save Profile Studio", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
