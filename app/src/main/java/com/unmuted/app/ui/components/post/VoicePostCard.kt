package com.unmuted.app.ui.components.post

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.PlaybackPhase
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatRelativeTime
import com.unmuted.app.ui.components.DebateRatioBar
import com.unmuted.app.ui.components.DownvotedPostVeil
import com.unmuted.app.ui.components.UserAvatarBadge
import com.unmuted.app.ui.components.rememberStudioHaptics
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

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
    val haptics = rememberStudioHaptics()

    val speedLabel = when (playback.speed) {
        1.5f -> "1.5x"
        2.0f -> "2x"
        else -> "1x"
    }

    val accentPalette = ProfileAccentPalette.fromKey(post.authorAccentKey)
    val primaryAccent = if (post.anonymousMode) StudioViolet else accentPalette.primary
    val activePlayColor = if (isQuotedPhase) SignalCyan else primaryAccent
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
            // Header Row
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
                    VoicePostWaveformPlayer(
                        post = post,
                        playback = playback,
                        isThisActive = isThisActive,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        isQuotedPhase = isQuotedPhase,
                        isClapback = isClapback,
                        activePlayColor = activePlayColor,
                        primaryAccent = primaryAccent,
                        accentSecondary = accentPalette.secondary,
                        durationMs = durationMs,
                        displayPosMs = displayPosMs,
                        progress = progress,
                        splitFraction = splitFraction,
                        waveformPeaks = waveformPeaks,
                        waveformStyle = waveformStyle,
                        captionsOpen = captionsOpen,
                        speedLabel = speedLabel,
                        haptics = haptics,
                        onCaptionsToggle = { captionsOpen = !captionsOpen },
                        onPlayPause = onPlayPause,
                        onSeekToMs = onSeekToMs,
                        onCycleSpeed = onCycleSpeed
                    )

                    VoicePostLyricsBox(
                        post = post,
                        playback = playback,
                        isThisActive = isThisActive,
                        isQuotedPhase = isQuotedPhase,
                        isClapback = isClapback,
                        captionsOpen = captionsOpen,
                        primaryAccent = primaryAccent,
                        onPlayPause = onPlayPause,
                        onSeekToMs = onSeekToMs,
                        onExpandLyrics = onExpandLyrics
                    )
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
            VoicePostActionsBar(
                post = post,
                reaction = reaction,
                primaryAccent = primaryAccent,
                isClapback = isClapback,
                haptics = haptics,
                onLike = onLike,
                onDislike = onDislike,
                onOpenComments = onOpenComments,
                onEcho = onEcho,
                onBookmark = onBookmark,
                onShareClick = { showShareMenu = true },
                onQuoteClapback = onQuoteClapback
            )
        }
    }

    VoicePostShareSheet(
        post = post,
        primaryAccent = primaryAccent,
        showShareMenu = showShareMenu,
        onDismissShareMenu = { showShareMenu = false }
    )
}
