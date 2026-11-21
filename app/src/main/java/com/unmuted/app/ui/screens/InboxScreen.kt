package com.unmuted.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.Conversation
import com.unmuted.app.data.DirectMessage
import com.unmuted.app.data.MessageKind
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.data.formatRelativeTime
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.UserAvatarBadge
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.viewmodel.DataUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    conversationsState: DataUiState<List<Conversation>>,
    allProfiles: List<UserProfile>,
    blockedUids: Set<String>,
    activeConversation: Conversation?,
    messagesState: DataUiState<List<DirectMessage>>,
    currentUid: String,
    playback: PlaybackState,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    onOpenConversation: (Conversation) -> Unit,
    onCloseConversation: () -> Unit,
    onStartNewConversation: (UserProfile) -> Unit,
    onSendText: (Conversation, String, () -> Unit) -> Unit,
    onSendVoice: (Conversation, File, Long, List<Float>, () -> Unit) -> Unit,
    onSendImage: (Conversation, Uri) -> Unit,
    onPlayVoiceMessage: (DirectMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeConversation != null) {
        BackHandler {
            audioRecorder.reset()
            onCloseConversation()
        }
        ChatDetailView(
            conversation = activeConversation,
            messagesState = messagesState,
            currentUid = currentUid,
            playback = playback,
            recordingState = recordingState,
            audioRecorder = audioRecorder,
            onBack = {
                audioRecorder.reset()
                onCloseConversation()
            },
            onSendText = { text, done -> onSendText(activeConversation, text, done) },
            onSendVoice = { file, dur, peaks, done -> onSendVoice(activeConversation, file, dur, peaks, done) },
            onSendImage = { uri -> onSendImage(activeConversation, uri) },
            onPlayVoiceMessage = onPlayVoiceMessage,
            modifier = modifier
        )
        return
    }

    var showNewChatSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Voice Inbox", style = MaterialTheme.typography.headlineMedium, color = StudioWhite)
                Text("Private voice notes with instant transcripts", style = MaterialTheme.typography.bodySmall, color = TitaniumMuted)
            }
            Button(
                onClick = { showNewChatSheet = true },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("new_dm_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New DM")
            }
        }

        when (conversationsState) {
            is DataUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricCoral)
                }
            }
            is DataUiState.Error -> {
                Text(conversationsState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(18.dp))
            }
            is DataUiState.Success -> {
                val visibleConversations = remember(conversationsState.data, blockedUids, currentUid) {
                    conversationsState.data.filterNot { conv ->
                        val otherUid = conv.otherParticipantUid(currentUid)
                        otherUid != currentUid && blockedUids.contains(otherUid)
                    }
                }
                if (visibleConversations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Mic, null, tint = ElectricCoral, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No voice threads yet", style = MaterialTheme.typography.titleLarge, color = StudioWhite)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Tap New DM to message any speaker with voice notes, transcripts, text, or photos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TitaniumMuted
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showNewChatSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Start a Voice DM")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visibleConversations, key = { it.id }) { conv ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = ObsidianSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenConversation(conv) }
                                    .testTag("conversation_row_${conv.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarBadge(
                                        name = conv.otherParticipantName(currentUid),
                                        size = 46.dp,
                                        showRing = conv.lastMessageKind == MessageKind.VOICE.name
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = conv.otherParticipantName(currentUid),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = StudioWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatRelativeTime(conv.updatedAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TitaniumMuted
                                            )
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (conv.lastMessageKind == MessageKind.VOICE.name) {
                                                Icon(
                                                    imageVector = Icons.Filled.GraphicEq,
                                                    contentDescription = null,
                                                    tint = ElectricCoral,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = conv.lastMessagePreview,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TitaniumMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        val otherUsers = remember(allProfiles, currentUid, blockedUids) {
            allProfiles.filter { it.userId != currentUid && !blockedUids.contains(it.userId) }
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNewChatSheet = false },
            sheetState = sheetState,
            containerColor = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("Start Voice Conversation", style = MaterialTheme.typography.titleLarge, color = StudioWhite)
                Spacer(Modifier.height(12.dp))
                if (otherUsers.isEmpty()) {
                    Text(
                        text = "Invite friends to sign into Unmuted to message them directly—or start a self-note thread below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TitaniumMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    val me = allProfiles.firstOrNull { it.userId == currentUid }
                    if (me != null) {
                        Button(
                            onClick = {
                                showNewChatSheet = false
                                onStartNewConversation(me)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Voice Memo Thread (Self)")
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(otherUsers, key = { it.userId }) { user ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ObsidianSurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showNewChatSheet = false
                                        onStartNewConversation(user)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarBadge(
                                        name = user.displayName,
                                        avatarBase64 = user.avatarBase64,
                                        accentKey = user.accentKey,
                                        size = 40.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = StudioWhite)
                                        Text("@${user.handle}", style = MaterialTheme.typography.bodySmall, color = TitaniumMuted)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ChatDetailView(
    conversation: Conversation,
    messagesState: DataUiState<List<DirectMessage>>,
    currentUid: String,
    playback: PlaybackState,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    onBack: () -> Unit,
    onSendText: (String, () -> Unit) -> Unit,
    onSendVoice: (File, Long, List<Float>, () -> Unit) -> Unit,
    onSendImage: (Uri) -> Unit,
    onPlayVoiceMessage: (DirectMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf("") }
    var sendingVoice by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) audioRecorder.startRecording(maxDurationMs = 45_000L)
    }
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onSendImage(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .imePadding()
    ) {
        // Chat top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_btn")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to inbox", tint = StudioWhite)
            }
            UserAvatarBadge(name = conversation.otherParticipantName(currentUid), size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(conversation.otherParticipantName(currentUid), style = MaterialTheme.typography.titleMedium, color = StudioWhite)
                Text("@${conversation.otherParticipantHandle(currentUid)}", style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
            }
        }
        HorizontalDivider(color = ObsidianHairline)

        // Messages list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is DataUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = ElectricCoral)
                is DataUiState.Error -> Text(messagesState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
                is DataUiState.Success -> {
                    if (messagesState.data.isEmpty()) {
                        Text(
                            text = "Tap the mic below to send your first voice note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TitaniumMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messagesState.data, key = { it.id }) { msg ->
                                DirectMessageBubble(
                                    message = msg,
                                    isMine = msg.senderUid == currentUid,
                                    playback = playback,
                                    onPlayVoice = { onPlayVoiceMessage(msg) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Voice + Text Input Bar
        HorizontalDivider(color = ObsidianHairline)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            when {
                sendingVoice -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCoral, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Transcribing & sending voice note...", style = MaterialTheme.typography.bodySmall, color = StudioWhite)
                    }
                }
                recordingState.recording -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ElectricCoral.copy(alpha = 0.16f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(ElectricCoral))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Voice note · ${formatDurationMs(recordingState.elapsedMs)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = StudioWhite
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = ElectricCoral,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    val res = audioRecorder.stopRecording()
                                    if (res != null) {
                                        sendingVoice = true
                                        onSendVoice(res.first, res.second, res.third) { sendingVoice = false }
                                    }
                                }
                                .testTag("send_voice_dm_stop_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop and send voice note", tint = StudioWhite)
                            }
                        }
                    }
                }
                else -> {
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
                                if (granted) audioRecorder.startRecording(maxDurationMs = 45_000L)
                                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(ElectricCoral)
                                .testTag("record_voice_dm_btn")
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = "Record voice DM", tint = StudioWhite)
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                photoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Send photo", tint = TitaniumMuted)
                        }
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it.take(1000) },
                            placeholder = { Text("Message...") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).testTag("dm_text_input")
                        )
                        IconButton(
                            onClick = {
                                val t = textInput.trim()
                                if (t.isNotEmpty()) onSendText(t) { textInput = "" }
                            },
                            enabled = textInput.isNotBlank(),
                            modifier = Modifier.size(46.dp).testTag("send_text_dm_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send text message",
                                tint = if (textInput.isNotBlank()) ElectricCoral else TitaniumMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectMessageBubble(
    message: DirectMessage,
    isMine: Boolean,
    playback: PlaybackState,
    onPlayVoice: () -> Unit
) {
    val isVoice = message.kindEnum() == MessageKind.VOICE
    val isThisPlaying = isVoice && playback.activeMediaId == "dm_${message.id}" && playback.playing
    var showTranscript by rememberSaveable(message.id) { mutableStateOf(true) }
    val imgBitmap = remember(message.imageBase64) { ImageCompressor.decodeBase64ToBitmap(message.imageBase64) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isMine) ElectricCoral.copy(alpha = 0.22f) else ObsidianSurfaceElevated,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.kindEnum()) {
                    MessageKind.VOICE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isMine) ElectricCoral else StudioViolet)
                                    .clickable(onClick = onPlayVoice),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isThisPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play voice note",
                                    tint = StudioWhite
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            InteractiveWaveform(
                                peaks = message.waveformPeaks.map { it.toFloat() },
                                progress = if (isThisPlaying && playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f,
                                playing = isThisPlaying,
                                accentColor = if (isMine) ElectricCoral else StudioViolet,
                                modifier = Modifier.weight(1f).height(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(formatDurationMs(message.durationMs), style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
                        }
                        if (!message.transcript.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.clickable { showTranscript = !showTranscript },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ClosedCaption, null, tint = TitaniumMuted, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (showTranscript) "Hide transcript" else "Show transcript",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TitaniumMuted
                                )
                            }
                            if (showTranscript) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = message.transcript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioWhite
                                )
                            }
                        }
                    }
                    MessageKind.IMAGE -> {
                        if (imgBitmap != null) {
                            Image(
                                bitmap = imgBitmap.asImageBitmap(),
                                contentDescription = "Shared DM photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                    MessageKind.TEXT -> {
                        Text(
                            text = message.textBody.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioWhite
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatRelativeTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TitaniumMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
