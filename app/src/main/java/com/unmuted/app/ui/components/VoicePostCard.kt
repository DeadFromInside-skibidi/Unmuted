package com.unmuted.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.PlaybackPhase
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.exportAudiogram
import com.unmuted.app.core.shareAudiogramVideo
import com.unmuted.app.core.sharePostText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.data.formatRelativeTime
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import kotlin.math.roundToInt

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
    var revealedDownvoted by rememberSaveable(post.id) { mutableStateOf(false) }
    val shouldVeil = safetySettings.collapseHeavilyDownvoted &&
        post.isHeavilyDownvoted(safetySettings.downvoteThreshold) &&
        !revealedDownvoted

    if (shouldVeil) {
        DownvotedPostVeil(
            netDownvotes = post.netDownvotes(),
            onReveal = { revealedDownvoted = true },
            modifier = modifier
        )
        return
    }

    val isClapback = post.hasQuoteClip()
    val expectedPlaybackId = if (isClapback) "clap_${post.id}" else post.audioMediaId
    val isThisActive = playback.activeMediaId == post.id ||
        playback.nowPlaying?.sourcePostId == post.id ||
        (!expectedPlaybackId.isNullOrBlank() && playback.activeMediaId == expectedPlaybackId) ||
        (!post.audioUrl.isNullOrBlank() && playback.activeMediaId == post.audioUrl)
    val isPlaying = isThisActive && playback.playing
    val isBuffering = isThisActive && playback.buffering
    val isQuotedPhase = isClapback && isThisActive && playback.activePhase == PlaybackPhase.QUOTED_CLIP

    val durationMs = if (isClapback) {
        if (isThisActive && playback.combinedDurationMs > 0L) playback.combinedDurationMs
        else post.combinedDurationMs().coerceAtLeast(1L)
    } else {
        if (isThisActive && playback.durationMs > 0L) playback.durationMs
        else post.durationMs.coerceAtLeast(1L)
    }
    val displayPosMs = if (isThisActive) {
        if (isClapback) playback.combinedPositionMs else playback.positionMs
    } else 0L
    val progress = (displayPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val splitFraction: Float? = if (isClapback) {
        if (isThisActive && playback.quoteSplitFraction > 0f) {
            playback.quoteSplitFraction
        } else {
            (post.quotedDurationMs().toFloat() / post.combinedDurationMs().coerceAtLeast(1L).toFloat())
                .coerceIn(0.12f, 0.78f)
        }
    } else null

    val waveformPeaks = remember(post.id, post.waveformPeaks, post.quotedWaveformPeaks, splitFraction) {
        if (isClapback && splitFraction != null) {
            stitchClapbackPeaks(
                quotedPeaks = post.quotedWaveformPeaks.map { it.toFloat() },
                mainPeaks = post.waveformPeaks.map { it.toFloat() },
                splitFraction = splitFraction
            )
        } else {
            post.waveformPeaks.map { it.toFloat() }
        }
    }

    var captionsOpen by rememberSaveable(post.id) { mutableStateOf(false) }
    var mediaExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showShareMenu by rememberSaveable(post.id) { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberStudioHaptics()

    val speedLabel = when (playback.speed) {
        1.5f -> "1.5x"
        2.0f -> "2x"
        else -> "1x"
    }

    val accentPalette = ProfileAccentPalette.fromKey(post.authorAccentKey)
    val primaryAccent = if (post.anonymousMode) StudioViolet else accentPalette.primary
    val activePlayColor = if (isQuotedPhase) SignalCyan else primaryAccent
    val vote = reaction?.voteEnum() ?: VoteType.NONE
    val echoed = reaction?.echoed == true
    val bookmarked = reaction?.bookmarked == true
    val attachmentBitmap = remember(post.attachmentImageBase64) {
        ImageCompressor.decodeBase64ToBitmap(post.attachmentImageBase64)
    }
    val isOtherAuthor = post.authorUid.isNotBlank() && post.authorUid != currentUid

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_post_card_${post.id}"),
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface,
        border = BorderStroke(
            width = if (isPlaying) 1.3.dp else 1.dp,
            color = when {
                isPlaying -> activePlayColor.copy(alpha = 0.65f)
                post.hotTakeMode -> AmberVinyl.copy(alpha = 0.35f)
                isClapback -> SignalCyan.copy(alpha = 0.3f)
                else -> ObsidianHairline
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatarBadge(
                    name = post.displaySpeakerName(),
                    avatarBase64 = if (post.anonymousMode) null else post.authorAvatarBase64,
                    accentKey = post.authorAccentKey,
                    anonymousMode = post.anonymousMode,
                    size = 42.dp,
                    showRing = isPlaying,
                    modifier = Modifier.clickable(enabled = !post.anonymousMode) {
                        onSpeakerClick(post.authorUid)
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = post.displaySpeakerName(),
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(enabled = !post.anonymousMode) {
                                    onSpeakerClick(post.authorUid)
                                }
                        )
                        if (post.anonymousMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = StudioViolet.copy(alpha = 0.22f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = "Anonymous voice mask",
                                        tint = StudioViolet,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (post.disguiseEnum() != VoiceDisguise.NONE) post.disguiseEnum().badge else "Anon",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioViolet
                                    )
                                }
                            }
                        }
                        if (post.hotTakeMode) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmberVinyl.copy(alpha = 0.22f),
                                modifier = Modifier.testTag("hot_take_badge_${post.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = "Hot Take Debate",
                                        tint = AmberVinyl,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "HOT TAKE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberVinyl
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${post.displaySpeakerHandle()} · ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TitaniumMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!post.roomSlug.isNullOrBlank() && !post.roomId.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianSurfaceElevated,
                                modifier = Modifier.clickable { onRoomClick(post.roomId) }
                            ) {
                                Text(
                                    text = "r/${post.roomSlug}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primaryAccent,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp).testTag("post_more_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "More post options",
                            tint = TitaniumMuted
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share Take (9:16 Video / Text)") },
                            leadingIcon = { Icon(Icons.Filled.Share, null, tint = primaryAccent) },
                            onClick = {
                                menuExpanded = false
                                showShareMenu = true
                            }
                        )
                        if (onQuoteClapback != null && !post.audioMediaId.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Voice Clapback") },
                                leadingIcon = { Icon(Icons.Filled.FormatQuote, null, tint = SignalCyan) },
                                onClick = {
                                    menuExpanded = false
                                    onQuoteClapback()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Hide from feed") },
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                            onClick = {
                                menuExpanded = false
                                onHide()
                            }
                        )
                        if (isOtherAuthor && onMuteSpeaker != null) {
                            DropdownMenuItem(
                                text = { Text("Mute speaker") },
                                leadingIcon = { Icon(Icons.Filled.VolumeOff, null, tint = AmberVinyl) },
                                onClick = {
                                    menuExpanded = false
                                    onMuteSpeaker()
                                }
                            )
                        }
                        if (isOtherAuthor && onBlockSpeaker != null) {
                            DropdownMenuItem(
                                text = { Text("Block speaker", color = AcousticError) },
                                leadingIcon = { Icon(Icons.Filled.Block, null, tint = AcousticError) },
                                onClick = {
                                    menuExpanded = false
                                    onBlockSpeaker()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Report illegal / harmful content", color = AcousticError) },
                            leadingIcon = { Icon(Icons.Filled.Flag, null, tint = AcousticError) },
                            onClick = {
                                menuExpanded = false
                                onReport()
                            }
                        )
                        if (post.authorUid == currentUid) {
                            DropdownMenuItem(
                                text = { Text("Delete voice", color = AcousticError) },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = AcousticError) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (!post.headlineText.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = post.headlineText,
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioWhite
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                ObsidianSurfaceElevated,
                                activePlayColor.copy(alpha = if (isPlaying) 0.16f else 0.06f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
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
                                secondaryColor = accentPalette.secondary,
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
                                                captionsOpen = !captionsOpen
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

                    AnimatedVisibility(
                        visible = captionsOpen,
                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianSurface.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = if (isQuotedPhase) SignalCyan else SignalMint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = if (isQuotedPhase) {
                                            "QUOTED CLIP PLAYING · SWITCHING SOON"
                                        } else {
                                            "LIVE LYRICS · TAP ANY LINE TO SEEK"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isQuotedPhase) SignalCyan else SignalMint
                                    )
                                }
                                IconButton(
                                    onClick = onExpandLyrics,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.OpenInFull,
                                        contentDescription = "Expand full lyrics stage",
                                        tint = TitaniumMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            if (isClapback && !post.quotedTranscript.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SignalCyan.copy(alpha = if (isQuotedPhase) 0.22f else 0.10f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 6.dp)
                                        .clickable {
                                            if (!isThisActive) onPlayPause()
                                            onSeekToMs(0L)
                                        }
                                ) {
                                    Text(
                                        text = "${post.quotedAuthorName ?: "Quoted"}: “${post.quotedTranscript}”",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isQuotedPhase) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isQuotedPhase) StudioWhite else SignalCyan,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            SyncedLyricsView(
                                segments = post.captionSegments,
                                fallbackTranscript = post.transcript,
                                positionMs = if (isThisActive && !isQuotedPhase) playback.positionMs else 0L,
                                isActiveAudio = isThisActive && !isQuotedPhase,
                                isFullStage = false,
                                accentColor = primaryAccent,
                                onSeekToMs = { target ->
                                    if (!isThisActive) onPlayPause()
                                    val seekTarget = if (isClapback) post.quotedDurationMs() + target else target
                                    onSeekToMs(seekTarget)
                                },
                                modifier = Modifier.heightIn(max = 172.dp)
                            )
                        }
                    }
                }
            }

            if (post.hotTakeMode) {
                Spacer(Modifier.height(10.dp))
                DebateRatioBar(
                    agreeCount = post.agreeCount,
                    disagreeCount = post.disagreeCount,
                    onAgreeClick = {
                        if (onOpenDebateSide != null) onOpenDebateSide(DebateStance.AGREE)
                        else onOpenComments()
                    },
                    onDisagreeClick = {
                        if (onOpenDebateSide != null) onOpenDebateSide(DebateStance.DISAGREE)
                        else onOpenComments()
                    }
                )
            }

            if (attachmentBitmap != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianHairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mediaExpanded = !mediaExpanded }
                        .testTag("secondary_media_toggle_${post.id}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = attachmentBitmap.asImageBitmap(),
                                    contentDescription = "Attached visual thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Image,
                                            contentDescription = null,
                                            tint = primaryAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Attached Visual",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = StudioWhite
                                        )
                                    }
                                    Text(
                                        text = if (mediaExpanded) "Tap to collapse" else "Tap to preview secondary media",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TitaniumMuted
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (mediaExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (mediaExpanded) "Collapse image" else "Expand image",
                                tint = TitaniumMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        AnimatedVisibility(
                            visible = mediaExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Image(
                                bitmap = attachmentBitmap.asImageBitmap(),
                                contentDescription = "Expanded post photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .fillMaxWidth()
                                    .height(186.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            }

            if (post.hashtags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.hashtags.take(4).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryAccent,
                            fontWeight = FontWeight.Medium,
                            modifier = if (onHashtagClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onHashtagClick(tag) }
                                    .padding(horizontal = 2.dp, vertical = 1.dp)
                            } else Modifier
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngagementButton(
                    icon = if (vote == VoteType.LIKE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    label = post.likeCount.toString(),
                    active = vote == VoteType.LIKE,
                    activeColor = ElectricCoral,
                    contentDesc = "Like voice",
                    tag = "like_btn_${post.id}",
                    onClick = {
                        haptics(HapticCue.REACTION)
                        onLike()
                    }
                )
                EngagementButton(
                    icon = if (vote == VoteType.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    label = post.dislikeCount.toString(),
                    active = vote == VoteType.DISLIKE,
                    activeColor = AcousticError,
                    contentDesc = "Dislike voice",
                    tag = "dislike_btn_${post.id}",
                    onClick = {
                        haptics(HapticCue.REACTION)
                        onDislike()
                    }
                )
                EngagementButton(
                    icon = Icons.Filled.ChatBubbleOutline,
                    label = post.commentCount.toString(),
                    active = false,
                    activeColor = StudioWhite,
                    contentDesc = "Open comments",
                    tag = "comment_btn_${post.id}",
                    onClick = onOpenComments
                )
                EngagementButton(
                    icon = Icons.Filled.Repeat,
                    label = post.echoCount.toString(),
                    active = echoed,
                    activeColor = SignalMint,
                    contentDesc = "Echo repost",
                    tag = "echo_btn_${post.id}",
                    onClick = {
                        haptics(HapticCue.REACTION)
                        onEcho()
                    }
                )
                if (onQuoteClapback != null && !post.audioMediaId.isNullOrBlank()) {
                    EngagementButton(
                        icon = Icons.Filled.FormatQuote,
                        label = "Clap",
                        active = isClapback,
                        activeColor = SignalCyan,
                        contentDesc = "Voice Quote Clapback",
                        tag = "clapback_btn_${post.id}",
                        onClick = {
                            haptics(HapticCue.REACTION)
                            onQuoteClapback()
                        }
                    )
                }
                IconButton(
                    onClick = {
                        haptics(HapticCue.REACTION)
                        onBookmark()
                    },
                    modifier = Modifier.size(40.dp).testTag("bookmark_btn_${post.id}")
                ) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save voice",
                        tint = if (bookmarked) primaryAccent else TitaniumMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = {
                        haptics(HapticCue.REACTION)
                        showShareMenu = true
                    },
                    modifier = Modifier.size(40.dp).testTag("share_btn_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share voice take",
                        tint = TitaniumMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }

    if (showShareMenu) {
        AlertDialog(
            onDismissRequest = { showShareMenu = false },
            title = {
                Text(
                    text = "Share Voice Take",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StudioWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Export as vertical 9:16 video or share quote link:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TitaniumMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShareMenu = false
                                isExporting = true
                                exportProgress = 0f
                                exportJob = coroutineScope.launch {
                                    try {
                                        val file = exportAudiogram(context, post) { prog ->
                                            exportProgress = prog
                                        }
                                        isExporting = false
                                        shareAudiogramVideo(context, file, post)
                                    } catch (e: CancellationException) {
                                        isExporting = false
                                    } catch (e: Exception) {
                                        isExporting = false
                                        Toast.makeText(
                                            context,
                                            "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(primaryAccent.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Share Video (9:16 Audiogram)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioWhite
                                )
                                Text(
                                    text = "Stories, WhatsApp, Shorts & Reels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianHairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShareMenu = false
                                sharePostText(context, post)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SignalMint.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = SignalMint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Share Text & Quote",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioWhite
                                )
                                Text(
                                    text = "Copy quote and post link",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareMenu = false }) {
                    Text("Close", color = TitaniumMuted)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {
                exportJob?.cancel()
                isExporting = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Rendering 9:16 Audiogram",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioWhite
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rendering synced lyrics and live waveform...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TitaniumMuted
                    )
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = primaryAccent,
                        trackColor = ObsidianSurfaceElevated
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${(exportProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportJob?.cancel()
                        isExporting = false
                    }
                ) {
                    Text("Cancel", color = AcousticError)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun stitchClapbackPeaks(
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

private fun resamplePeaks(source: List<Float>, targetCount: Int): List<Float> {
    if (source.isEmpty() || targetCount <= 0) return emptyList()
    if (source.size == targetCount) return source
    return List(targetCount) { idx ->
        val pos = idx.toFloat() / targetCount.coerceAtLeast(1) * source.size
        source[pos.toInt().coerceIn(0, source.lastIndex)].coerceIn(0.15f, 1f)
    }
}

@Composable
private fun EngagementButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    contentDesc: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (active) activeColor else TitaniumMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) activeColor else TitaniumMuted
        )
    }
}
