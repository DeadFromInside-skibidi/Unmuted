package com.unmuted.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unmuted.app.core.AudioPlayerManager
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.AliasGenerator
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.components.DisguiseChipRow
import com.unmuted.app.ui.components.GeometricBurnerAvatar
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.rememberStudioHaptics
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AcousticErrorContainer
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.viewmodel.ComposerDraftState

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var micDeniedMessage by remember { mutableStateOf(false) }
    val haptics = rememberStudioHaptics()

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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachImageUri(uri)
    }

    val previewBitmap = remember(draft.attachmentImageBase64) {
        ImageCompressor.decodeBase64ToBitmap(draft.attachmentImageBase64)
    }
    val previewAlias = remember(currentUid, draft.selectedRoom?.id, draft.burnerNonce) {
        AliasGenerator.generateForRoom(
            uid = currentUid,
            roomKey = draft.selectedRoom?.id ?: "global_unfiltered",
            nonce = draft.burnerNonce
        )
    }
    val isPreviewPlaying = playback.activeMediaId == "composer_preview" && playback.playing
    val quotedPost = draft.quotedPost
    val isQuotePreviewPlaying = quotedPost != null &&
        playback.activeMediaId == "quote_preview_${quotedPost.id}" &&
        playback.playing

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
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            quotedPost != null -> Icons.Filled.FormatQuote
                            draft.hotTakeMode -> Icons.Filled.LocalFireDepartment
                            else -> Icons.Filled.GraphicEq
                        },
                        contentDescription = null,
                        tint = when {
                            quotedPost != null -> SignalCyan
                            draft.hotTakeMode -> AmberVinyl
                            else -> ElectricCoral
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            quotedPost != null -> "Voice Clapback"
                            draft.hotTakeMode -> "Hot Take Studio"
                            else -> "Studio Mic"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = StudioWhite
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close studio composer", tint = TitaniumMuted)
                }
            }

            if (quotedPost != null) {
                Spacer(Modifier.height(10.dp))
                val maxDurMs = quotedPost.durationMs.coerceAtLeast(1000L)
                val minSpanMs = minOf(5_000L, maxDurMs)
                val maxSpanMs = minOf(15_000L, maxDurMs)
                val clampedStart = draft.quoteStartMs.coerceIn(0L, (maxDurMs - minSpanMs).coerceAtLeast(0L))
                val clampedEnd = draft.quoteEndMs.coerceIn(
                    (clampedStart + minSpanMs).coerceAtMost(maxDurMs),
                    (clampedStart + maxSpanMs).coerceAtMost(maxDurMs)
                )
                val selectedSpanSec = ((clampedEnd - clampedStart) / 1000L).coerceAtLeast(1L)
                val quoteExcerpt = remember(quotedPost.id, clampedStart, clampedEnd) {
                    val segs = quotedPost.captionSegments.filter { seg ->
                        seg.endMs >= clampedStart && seg.startMs <= clampedEnd
                    }
                    if (segs.isNotEmpty()) segs.joinToString(" ") { it.text }
                    else quotedPost.transcript.take(140)
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SignalCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SignalCyan.copy(alpha = 0.42f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clapback_clipper_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "QUOTING ${quotedPost.displaySpeakerName().uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalCyan
                                )
                                Text(
                                    text = "Pick a 5–15s clip that plays right before your voice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SignalCyan.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .clickable(onClick = onPreviewQuoteClip)
                                    .testTag("preview_quote_clip_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isQuotePreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Preview quoted clip",
                                        tint = SignalCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isQuotePreviewPlaying) "Stop" else "Preview",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalCyan
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (maxDurMs > minSpanMs) {
                            RangeSlider(
                                value = clampedStart.toFloat()..clampedEnd.toFloat(),
                                onValueChange = { range ->
                                    var newStart = range.start.toLong().coerceIn(0L, maxDurMs)
                                    var newEnd = range.endInclusive.toLong().coerceIn(0L, maxDurMs)
                                    val span = newEnd - newStart
                                    if (span < minSpanMs) {
                                        if (newStart != clampedStart) {
                                            newStart = (newEnd - minSpanMs).coerceAtLeast(0L)
                                        } else {
                                            newEnd = (newStart + minSpanMs).coerceAtMost(maxDurMs)
                                        }
                                    } else if (span > maxSpanMs) {
                                        if (newStart != clampedStart) {
                                            newEnd = (newStart + maxSpanMs).coerceAtMost(maxDurMs)
                                        } else {
                                            newStart = (newEnd - maxSpanMs).coerceAtLeast(0L)
                                        }
                                    }
                                    onQuoteRangeChanged(newStart, newEnd)
                                },
                                valueRange = 0f..maxDurMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = SignalCyan,
                                    activeTrackColor = SignalCyan,
                                    inactiveTrackColor = ObsidianSurfaceElevated
                                ),
                                modifier = Modifier.testTag("clapback_range_slider")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Clip: ${formatDurationMs(clampedStart)} – ${formatDurationMs(clampedEnd)} (${selectedSpanSec}s)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = StudioWhite
                            )
                            Text(
                                text = "Total source ${formatDurationMs(maxDurMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TitaniumMuted
                            )
                        }

                        if (quoteExcerpt.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "“$quoteExcerpt”",
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioWhite.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val kindOptions = if (quotedPost != null) {
                    listOf(
                        PostKind.FEED_VOICE to "Feed Clapback",
                        PostKind.PULSE_SHORT to "Pulse Clapback"
                    )
                } else {
                    listOf(
                        PostKind.FEED_VOICE to "Voice Post",
                        PostKind.PULSE_SHORT to "Pulse Short",
                        PostKind.VOICE_DROP to "24h Drop"
                    )
                }
                kindOptions.forEach { (kind, label) ->
                    val selected = draft.postKind == kind
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectKind(kind) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("composer_kind_${kind.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCoral.copy(alpha = 0.24f),
                            selectedLabelColor = StudioWhite
                        )
                    )
                }
            }

            if (rooms.isNotEmpty() && draft.postKind != PostKind.VOICE_DROP) {
                Spacer(Modifier.height(8.dp))
                Text("POST TO ROOM (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = draft.selectedRoom == null,
                            onClick = { onSelectRoom(null) },
                            label = { Text("Main Feed") }
                        )
                    }
                    items(rooms, key = { it.id }) { room ->
                        FilterChip(
                            selected = draft.selectedRoom?.id == room.id,
                            onClick = { onSelectRoom(room) },
                            label = { Text("r/${room.slug}") },
                            leadingIcon = if (room.anonymousRoom) {
                                { Icon(Icons.Filled.Shield, null, tint = StudioViolet, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }

            if (draft.postKind != PostKind.VOICE_DROP) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (draft.hotTakeMode) AmberVinyl.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                    border = if (draft.hotTakeMode) BorderStroke(1.dp, AmberVinyl.copy(alpha = 0.45f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (draft.hotTakeMode) AmberVinyl else TitaniumMuted
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Tag as Hot Take Debate",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioWhite
                                )
                                Text(
                                    text = "Split replies into Agree & Disagree audio threads with a live ratio bar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = draft.hotTakeMode,
                            onCheckedChange = {
                                haptics(HapticCue.CAPTION_TOGGLE)
                                onToggleHotTake(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = StudioWhite,
                                checkedTrackColor = AmberVinyl
                            ),
                            modifier = Modifier.testTag("composer_hottake_switch")
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (draft.anonymousMode) StudioViolet.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                border = if (draft.anonymousMode) BorderStroke(1.dp, StudioViolet.copy(alpha = 0.45f)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (draft.anonymousMode) {
                                GeometricBurnerAvatar(
                                    seed = previewAlias,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, StudioViolet, CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = TitaniumMuted
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (draft.anonymousMode) previewAlias else "Speak Anonymously",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioWhite
                                )
                                Text(
                                    text = if (draft.selectedRoom?.anonymousRoom == true) {
                                        "Burner alias & voice mask enforced in r/${draft.selectedRoom.slug}"
                                    } else if (draft.anonymousMode) {
                                        "Auto-generated burner identity & geometric avatar"
                                    } else {
                                        "Hide your profile & unlock on-device voice masking"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                        Switch(
                            checked = draft.anonymousMode,
                            enabled = draft.selectedRoom?.anonymousRoom != true,
                            onCheckedChange = {
                                haptics(HapticCue.CAPTION_TOGGLE)
                                onToggleAnonymous(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = StudioWhite, checkedTrackColor = StudioViolet),
                            modifier = Modifier.testTag("composer_anon_switch")
                        )
                    }

                    AnimatedVisibility(visible = draft.anonymousMode) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ON-DEVICE VOICE MASKING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioViolet
                                    )
                                    Text(
                                        text = draft.voiceDisguise.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TitaniumMuted
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StudioViolet.copy(alpha = 0.24f),
                                    modifier = Modifier
                                        .clickable {
                                            haptics(HapticCue.CAPTION_TOGGLE)
                                            onRollBurner()
                                        }
                                        .testTag("roll_burner_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Shuffle,
                                            contentDescription = "New burner alias",
                                            tint = StudioWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "New Burner",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StudioWhite
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            DisguiseChipRow(
                                selected = draft.voiceDisguise,
                                onSelect = onSelectDisguise,
                                allowNatural = draft.selectedRoom?.anonymousRoom != true
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurfaceElevated,
                modifier = Modifier
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
                                    quotedPost != null -> "Tap to record your clapback right after their clip"
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

            if (draft.transcribing) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SignalMint.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SignalMint, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Generating word-synced captions...",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioWhite
                        )
                    }
                }
            } else if (draft.transcriptionError != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AcousticErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = draft.transcriptionError,
                            style = MaterialTheme.typography.bodySmall,
                            color = AcousticError,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onRetryTranscribe) {
                            Text("Retry")
                        }
                    }
                }
            } else if (draft.recordedFile != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.transcript,
                    onValueChange = onTranscriptChanged,
                    label = { Text("Word-Synced Captions Transcript") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("composer_transcript_input"),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft.headlineText,
                onValueChange = onHeadlineChanged,
                label = {
                    Text(
                        when {
                            quotedPost != null -> "Optional clapback headline"
                            draft.hotTakeMode -> "Hot Take headline (the claim people will debate)"
                            else -> "Optional topic headline"
                        }
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("composer_headline_input"),
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.hashtagsInput,
                    onValueChange = onHashtagsChanged,
                    label = { Text("Tags (e.g. tech, debate)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(54.dp).testTag("composer_attach_photo_btn")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach optional photo")
                }
            }

            if (previewBitmap != null) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Selected secondary photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp))
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurface.copy(alpha = 0.8f))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = StudioWhite, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (draft.publishError != null) {
                Spacer(Modifier.height(10.dp))
                Text(draft.publishError, style = MaterialTheme.typography.bodySmall, color = AcousticError)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    haptics(HapticCue.REACTION)
                    onPublish()
                },
                enabled = draft.recordedFile != null && !draft.transcribing && !draft.publishing && !recordingState.recording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        quotedPost != null -> SignalCyan
                        draft.hotTakeMode -> AmberVinyl
                        else -> ElectricCoral
                    }
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("publish_voice_btn")
            ) {
                if (draft.publishing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = StudioWhite, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Publishing to Unmuted...")
                } else {
                    Text(
                        text = when {
                            quotedPost != null -> "Publish Voice Clapback"
                            draft.hotTakeMode -> "Publish Hot Take Debate"
                            draft.postKind == PostKind.VOICE_DROP -> "Publish 24h Voice Drop"
                            draft.postKind == PostKind.PULSE_SHORT -> "Publish Pulse Short"
                            else -> "Publish Voice"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
