package com.unmuted.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.data.formatRelativeTime
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.viewmodel.DataUiState
import java.io.File

private enum class DebateThreadTab { ALL, AGREE, DISAGREE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: VoicePost,
    initialStance: DebateStance = DebateStance.NONE,
    commentsState: DataUiState<List<VoiceComment>>,
    filteredAuthorUids: Set<String> = emptySet(),
    playback: PlaybackState,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    onDismiss: () -> Unit,
    onPlayVoiceComment: (String, VoiceDisguise) -> Unit,
    onUpvoteComment: (VoiceComment) -> Unit,
    onReportComment: (VoiceComment) -> Unit,
    onSendTextComment: (String, DebateStance, () -> Unit) -> Unit,
    onSendVoiceComment: (File, Long, List<Float>, VoiceDisguise, DebateStance, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberStudioHaptics()
    var replyText by remember { mutableStateOf("") }
    var sendingVoice by remember { mutableStateOf(false) }
    var replyDisguise by remember(post.id) {
        mutableStateOf(if (post.anonymousMode) VoiceDisguise.STUDIO_RADIO else VoiceDisguise.NONE)
    }
    var selectedStance by remember(post.id, initialStance) {
        mutableStateOf(
            if (post.hotTakeMode) {
                if (initialStance == DebateStance.DISAGREE) DebateStance.DISAGREE else DebateStance.AGREE
            } else DebateStance.NONE
        )
    }
    var activeThreadTab by remember(post.id, initialStance) {
        mutableStateOf(
            when {
                !post.hotTakeMode -> DebateThreadTab.ALL
                initialStance == DebateStance.AGREE -> DebateThreadTab.AGREE
                initialStance == DebateStance.DISAGREE -> DebateThreadTab.DISAGREE
                else -> DebateThreadTab.ALL
            }
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            haptics(HapticCue.RECORD_START)
            audioRecorder.startRecording(maxDurationMs = 30_000L)
        }
    }

    val allComments = remember(commentsState, filteredAuthorUids) {
        (commentsState as? DataUiState.Success)?.data.orEmpty()
            .filterNot { it.authorUid in filteredAuthorUids }
    }
    val agreeComments = remember(allComments) { allComments.filter { it.stanceEnum() == DebateStance.AGREE } }
    val disagreeComments = remember(allComments) { allComments.filter { it.stanceEnum() == DebateStance.DISAGREE } }
    val displayedComments = remember(allComments, agreeComments, disagreeComments, activeThreadTab, post.hotTakeMode) {
        if (!post.hotTakeMode) allComments
        else when (activeThreadTab) {
            DebateThreadTab.ALL -> allComments
            DebateThreadTab.AGREE -> agreeComments
            DebateThreadTab.DISAGREE -> disagreeComments
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            audioRecorder.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (post.hotTakeMode) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = AmberVinyl,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (post.hotTakeMode) "Debate Threads (${post.commentCount})" else "Voice & Text Replies (${post.commentCount})",
                        style = MaterialTheme.typography.titleLarge,
                        color = StudioWhite
                    )
                }
                if (post.anonymousMode) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioViolet.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Shield, null, tint = StudioViolet, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Anon Mask", style = MaterialTheme.typography.labelSmall, color = StudioViolet)
                        }
                    }
                }
            }

            if (post.hotTakeMode) {
                Spacer(Modifier.height(10.dp))
                DebateRatioBar(
                    agreeCount = maxOf(post.agreeCount, agreeComments.size.toLong()),
                    disagreeCount = maxOf(post.disagreeCount, disagreeComments.size.toLong())
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(DebateThreadTab.ALL, "All (${allComments.size})", StudioWhite),
                        Triple(DebateThreadTab.AGREE, "Agree (${agreeComments.size})", SignalMint),
                        Triple(DebateThreadTab.DISAGREE, "Disagree (${disagreeComments.size})", ElectricCoral)
                    ).forEach { (tab, label, tint) ->
                        val active = activeThreadTab == tab
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) tint.copy(alpha = 0.2f) else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (active) tint.copy(alpha = 0.6f) else ObsidianHairline),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptics(HapticCue.CAPTION_TOGGLE)
                                    activeThreadTab = tab
                                    if (tab == DebateThreadTab.AGREE) selectedStance = DebateStance.AGREE
                                    else if (tab == DebateThreadTab.DISAGREE) selectedStance = DebateStance.DISAGREE
                                }
                                .testTag("debate_tab_${tab.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) tint else TitaniumMuted
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ObsidianHairline)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 330.dp)
            ) {
                when (commentsState) {
                    is DataUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = ElectricCoral
                        )
                    }
                    is DataUiState.Error -> {
                        Text(
                            text = commentsState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                    is DataUiState.Success -> {
                        if (displayedComments.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when {
                                        post.hotTakeMode && activeThreadTab == DebateThreadTab.AGREE -> "No Agree audio takes yet"
                                        post.hotTakeMode && activeThreadTab == DebateThreadTab.DISAGREE -> "No Disagree audio takes yet"
                                        else -> "No replies yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StudioWhite
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Pick your side below and tap the mic to drop a voice take.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                items(displayedComments, key = { it.id }) { comment ->
                                    CommentItemRow(
                                        comment = comment,
                                        playback = playback,
                                        onPlay = {
                                            comment.audioMediaId?.let {
                                                onPlayVoiceComment(it, comment.disguiseEnum())
                                            }
                                        },
                                        onUpvote = { onUpvoteComment(comment) },
                                        onReport = { onReportComment(comment) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = ObsidianHairline)
            Spacer(Modifier.height(10.dp))

            if (post.hotTakeMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Your side:", style = MaterialTheme.typography.labelMedium, color = TitaniumMuted)
                    listOf(
                        Triple(DebateStance.AGREE, "Agree", SignalMint),
                        Triple(DebateStance.DISAGREE, "Disagree", ElectricCoral)
                    ).forEach { (stance, label, tint) ->
                        val chosen = selectedStance == stance
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (chosen) tint.copy(alpha = 0.22f) else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (chosen) tint else ObsidianHairline),
                            modifier = Modifier
                                .clickable {
                                    haptics(HapticCue.CAPTION_TOGGLE)
                                    selectedStance = stance
                                }
                                .testTag("reply_stance_${stance.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (stance == DebateStance.AGREE) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
                                    contentDescription = null,
                                    tint = if (chosen) tint else TitaniumMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                    color = if (chosen) tint else TitaniumMuted
                                )
                            }
                        }
                    }
                }
            }

            if (post.anonymousMode && !sendingVoice) {
                DisguiseChipRow(
                    selected = replyDisguise,
                    onSelect = { replyDisguise = it },
                    allowNatural = false,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val activeReplyAccent = when {
                post.hotTakeMode && selectedStance == DebateStance.AGREE -> SignalMint
                post.hotTakeMode && selectedStance == DebateStance.DISAGREE -> ElectricCoral
                post.anonymousMode -> StudioViolet
                else -> ElectricCoral
            }

            if (sendingVoice) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = activeReplyAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Transcribing & posting voice reply...", style = MaterialTheme.typography.bodyMedium, color = StudioWhite)
                }
            } else if (recordingState.recording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(activeReplyAccent.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(activeReplyAccent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Recording ${if (post.hotTakeMode) selectedStance.label else ""} reply · ${formatDurationMs(recordingState.elapsedMs)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioWhite
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = activeReplyAccent,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                haptics(HapticCue.RECORD_STOP)
                                val res = audioRecorder.stopRecording()
                                if (res != null) {
                                    sendingVoice = true
                                    val disguise = if (post.anonymousMode) replyDisguise else VoiceDisguise.NONE
                                    onSendVoiceComment(res.first, res.second, res.third, disguise, selectedStance) {
                                        sendingVoice = false
                                    }
                                }
                            }
                            .testTag("stop_voice_comment_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop and send voice reply", tint = StudioWhite)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                haptics(HapticCue.RECORD_START)
                                audioRecorder.startRecording(maxDurationMs = 30_000L)
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(activeReplyAccent.copy(alpha = 0.18f))
                            .testTag("record_voice_comment_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Record voice reply",
                            tint = activeReplyAccent
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it.take(500) },
                        placeholder = {
                            Text(
                                if (post.hotTakeMode) "Add ${selectedStance.label.lowercase()} take or tap mic..."
                                else "Add a reply or tap mic..."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("comment_text_input"),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val trimmed = replyText.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendTextComment(trimmed, selectedStance) { replyText = "" }
                            }
                        },
                        enabled = replyText.isNotBlank(),
                        modifier = Modifier.size(48.dp).testTag("send_comment_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send text reply",
                            tint = if (replyText.isNotBlank()) activeReplyAccent else TitaniumMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CommentItemRow(
    comment: VoiceComment,
    playback: PlaybackState,
    onPlay: () -> Unit,
    onUpvote: () -> Unit,
    onReport: () -> Unit
) {
    val isVoice = !comment.audioMediaId.isNullOrBlank()
    val isPlaying = isVoice && playback.activeMediaId == comment.audioMediaId && playback.playing
    val haptics = rememberStudioHaptics()
    val stance = comment.stanceEnum()
    val stanceColor = when (stance) {
        DebateStance.AGREE -> SignalMint
        DebateStance.DISAGREE -> ElectricCoral
        DebateStance.NONE -> if (comment.anonymousMode) StudioViolet else ElectricCoral
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurfaceElevated,
        border = if (stance != DebateStance.NONE) BorderStroke(1.dp, stanceColor.copy(alpha = 0.35f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    UserAvatarBadge(
                        name = comment.displaySpeakerName(),
                        anonymousMode = comment.anonymousMode,
                        size = 30.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = comment.displaySpeakerName(),
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioWhite
                    )
                    if (stance != DebateStance.NONE) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = stanceColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = stance.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = stanceColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatRelativeTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumMuted
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics(HapticCue.REACTION)
                                onUpvote()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = "Upvote reply", tint = TitaniumMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(comment.likeCount.toString(), style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
                    }
                    IconButton(
                        onClick = onReport,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = "Report reply",
                            tint = TitaniumMuted.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            if (isVoice) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(stanceColor)
                            .clickable {
                                haptics(HapticCue.REACTION)
                                onPlay()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play voice reply",
                            tint = StudioWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    InteractiveWaveform(
                        peaks = comment.waveformPeaks.map { it.toFloat() },
                        progress = if (isPlaying && playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f,
                        playing = isPlaying,
                        accentColor = stanceColor,
                        modifier = Modifier.weight(1f).height(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatDurationMs(comment.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumMuted
                    )
                }
                if (!comment.transcript.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "“${comment.transcript}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = TitaniumMuted
                    )
                }
            }

            if (!comment.textBody.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = comment.textBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioWhite
                )
            }
        }
    }
}
