package com.unmuted.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.unmuted.app.R
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.core.RecordingState
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.JoinedRoom
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoiceComment
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.data.formatRelativeTime
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.UserAvatarBadge
import com.unmuted.app.ui.components.VoicePostCard
import com.unmuted.app.ui.components.rememberStudioHaptics
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioBannerMood
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import java.io.File

private enum class ProfileTab(val label: String) {
    TAKES("Takes"),
    REPLIES("Replies"),
    DEBATES("Debates"),
    LIKED("Liked")
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    allPosts: List<VoicePost>,
    reactions: Map<String, UserPostReaction>,
    rooms: List<CommunityRoom>,
    joinedRooms: Map<String, JoinedRoom>,
    safetySettings: SafetySettings,
    profileReplies: List<Pair<VoicePost, VoiceComment>> = emptyList(),
    profileRepliesLoading: Boolean = false,
    playback: PlaybackState,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    currentUid: String,
    onBack: (() -> Unit)? = null,
    onToggleFollow: () -> Unit,
    onMessageUser: () -> Unit,
    onOpenSafetyControls: () -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onReportProfile: (UserProfile) -> Unit,
    onSignOut: () -> Unit,
    onPlayVoiceBio: (String) -> Unit,
    onPreloadVoiceBio: (String?) -> Unit = {},
    onPlayVoiceReply: (VoiceComment) -> Unit = {},
    onPlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onOpenDebateSide: (VoicePost, DebateStance) -> Unit = { post, _ -> onOpenComments(post) },
    onQuoteClapback: (VoicePost) -> Unit = {},
    onEcho: (VoicePost) -> Unit,
    onBookmark: (VoicePost) -> Unit,
    onReportPost: (VoicePost) -> Unit,
    onHidePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onExpandLyrics: (VoicePost) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSpeakerProfile: (String) -> Unit,
    onSaveCustomization: (
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
        newVoiceBioPeaks: List<Float>,
        onDone: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    var selectedTab by rememberSaveable(profile.userId) { mutableStateOf(ProfileTab.TAKES) }
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showProfileOverflow by remember { mutableStateOf(false) }
    val haptics = rememberStudioHaptics()

    val accent = ProfileAccentPalette.fromKey(profile.accentKey)
    val bannerMood = StudioBannerMood.fromKey(profile.bannerKey)
    val waveStyle = WaveformStyle.fromKey(profile.waveformStyle)

    LaunchedEffect(profile.voiceBioMediaId) {
        onPreloadVoiceBio(profile.voiceBioMediaId)
    }

    val myTakes = remember(allPosts, profile.userId) {
        allPosts.filter { it.authorUid == profile.userId && !it.anonymousMode }
    }
    val repliedPostIds = remember(profileReplies) {
        profileReplies.map { it.first.id }.toSet()
    }
    val myDebates = remember(allPosts, profile.userId, repliedPostIds) {
        allPosts.filter { post ->
            post.hotTakeMode && (
                (post.authorUid == profile.userId && !post.anonymousMode) ||
                    repliedPostIds.contains(post.id)
                )
        }
    }
    val likedTakes = remember(allPosts, reactions, isOwnProfile) {
        if (isOwnProfile) allPosts.filter { reactions[it.id]?.voteEnum() == VoteType.LIKE }
        else emptyList()
    }

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
        contentPadding = PaddingValues(bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Customizable Studio Header + Pinned Voice Bio
        item(key = "profile_header") {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(bannerMood.startColor, bannerMood.midColor, bannerMood.endColor)
                            )
                        )
                ) {
                    if (bannerMood.usesArtBackdrop) {
                        Image(
                            painter = painterResource(id = R.drawable.img_studio_banner_1795250456431),
                            contentDescription = "Studio profile banner art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.75f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, ObsidianBackground))
                            )
                    )

                    // Top action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ObsidianSurface.copy(alpha = 0.72f))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StudioWhite)
                            }
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }

                        if (isOwnProfile) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics(HapticCue.CAPTION_TOGGLE)
                                        onOpenSafetyControls()
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ObsidianSurface.copy(alpha = 0.85f))
                                        .testTag("profile_safety_controls_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.GppGood,
                                        contentDescription = "Safety and debate controls",
                                        tint = SignalMint
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ObsidianSurface.copy(alpha = 0.88f),
                                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .clickable {
                                            haptics(HapticCue.CAPTION_TOGGLE)
                                            showCustomizeSheet = true
                                        }
                                        .testTag("customize_profile_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Palette, null, tint = accent.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Studio Theme", style = MaterialTheme.typography.labelLarge, color = StudioWhite)
                                    }
                                }
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ObsidianSurface.copy(alpha = 0.85f))
                                        .testTag("sign_out_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = TitaniumMuted)
                                }
                            }
                        } else {
                            // Overflow safety menu for other speakers (Mute / Block / Report)
                            Box {
                                IconButton(
                                    onClick = { showProfileOverflow = true },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(ObsidianSurface.copy(alpha = 0.82f))
                                        .testTag("profile_safety_overflow_btn")
                                ) {
                                    Icon(Icons.Filled.MoreHoriz, contentDescription = "Speaker safety options", tint = StudioWhite)
                                }
                                DropdownMenu(
                                    expanded = showProfileOverflow,
                                    onDismissRequest = { showProfileOverflow = false },
                                    containerColor = ObsidianSurfaceElevated
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Mute @${profile.handle}", color = StudioWhite) },
                                        leadingIcon = { Icon(Icons.Filled.VolumeOff, null, tint = AmberVinyl) },
                                        onClick = {
                                            showProfileOverflow = false
                                            onMuteSpeaker(profile.userId, profile.displayName)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Block @${profile.handle}", color = AcousticError) },
                                        leadingIcon = { Icon(Icons.Filled.Block, null, tint = AcousticError) },
                                        onClick = {
                                            showProfileOverflow = false
                                            onBlockSpeaker(profile.userId, profile.displayName)
                                            onBack?.invoke()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Report speaker...", color = AcousticError) },
                                        leadingIcon = { Icon(Icons.Filled.Flag, null, tint = AcousticError) },
                                        onClick = {
                                            showProfileOverflow = false
                                            onReportProfile(profile)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Avatar anchored bottom-left
                    UserAvatarBadge(
                        name = profile.displayName,
                        avatarBase64 = profile.avatarBase64,
                        accentKey = profile.accentKey,
                        size = 82.dp,
                        showRing = true,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, bottom = 6.dp)
                    )
                }

                // Identity Row + Pinned Voice Bio directly under banner
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, color = StudioWhite)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("@${profile.handle}", style = MaterialTheme.typography.labelMedium, color = accent.primary)
                                Text(
                                    text = "· ${myTakes.size} takes · ${joinedRooms.size} rooms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                        if (!isOwnProfile) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onMessageUser,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.MailOutline, contentDescription = "Message", modifier = Modifier.size(16.dp))
                                }
                                Button(
                                    onClick = {
                                        haptics(HapticCue.REACTION)
                                        onToggleFollow()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFollowing) ObsidianSurfaceElevated else accent.primary
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(if (isFollowing) "Following" else "Follow")
                                }
                            }
                        }
                    }

                    // Pinned Voice Bio Intro Clip
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(
                            width = if (isBioPlaying) 1.4.dp else 1.dp,
                            color = if (isBioPlaying) accent.primary.copy(alpha = 0.78f) else accent.primary.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
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
                                                showCustomizeSheet = true
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
                                                showCustomizeSheet = true
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

                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = StudioWhite)
                    }

                    // Interests / Topic Tags + Active Studio Theme Pill
                    if (profile.interests.isNotEmpty() || isOwnProfile) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isOwnProfile) {
                                item(key = "theme_chip") {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianSurfaceElevated,
                                        border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            haptics(HapticCue.CAPTION_TOGGLE)
                                            showCustomizeSheet = true
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(accent.primary)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "${accent.label} · ${waveStyle.label}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StudioWhite
                                            )
                                        }
                                    }
                                }
                            }
                            items(profile.interests) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = accent.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4 Profile Tabs: Takes | Replies | Debates | Liked
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ProfileTab.entries.forEach { tab ->
                            val active = tab == selectedTab
                            val count = when (tab) {
                                ProfileTab.TAKES -> myTakes.size
                                ProfileTab.REPLIES -> profileReplies.size
                                ProfileTab.DEBATES -> myDebates.size
                                ProfileTab.LIKED -> if (isOwnProfile) likedTakes.size else 0
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) accent.primary.copy(alpha = 0.22f) else ObsidianSurface,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (active) accent.primary.copy(alpha = 0.55f) else ObsidianHairline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptics(HapticCue.CAPTION_TOGGLE)
                                        selectedTab = tab
                                    }
                                    .testTag("profile_tab_${tab.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) accent.primary else TitaniumMuted,
                                        maxLines = 1
                                    )
                                    if (count > 0) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) StudioWhite else TitaniumMuted.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            ProfileTab.REPLIES -> {
                if (profileRepliesLoading && profileReplies.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent.primary)
                        }
                    }
                } else if (profileReplies.isEmpty()) {
                    item {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.ChatBubbleOutline,
                            accent = accent.primary,
                            title = "No voice replies yet",
                            subtitle = "Jump into a thread or Hot Take debate to see your spoken replies here."
                        )
                    }
                } else {
                    items(profileReplies, key = { (_, comment) -> comment.id }) { (parentPost, comment) ->
                        ProfileReplyCard(
                            parentPost = parentPost,
                            comment = comment,
                            playback = playback,
                            accent = accent.primary,
                            secondaryAccent = accent.secondary,
                            waveStyle = waveStyle,
                            onPlayVoiceReply = { onPlayVoiceReply(comment) },
                            onOpenThread = { onOpenComments(parentPost) },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }

            ProfileTab.DEBATES -> {
                if (myDebates.isEmpty()) {
                    item {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.LocalFireDepartment,
                            accent = AmberVinyl,
                            title = "No audio debates yet",
                            subtitle = "Start a Hot Take in any Room or join an Agree / Disagree side to appear here."
                        )
                    }
                } else {
                    items(myDebates, key = { it.id }) { post ->
                        VoicePostCard(
                            post = post,
                            playback = playback,
                            reaction = reactions[post.id],
                            currentUid = currentUid,
                            waveformStyle = waveStyle,
                            safetySettings = safetySettings,
                            onPlayPause = { onPlayPost(post) },
                            onSeekToMs = onSeekToMs,
                            onCycleSpeed = onCycleSpeed,
                            onLike = { onVote(post, VoteType.LIKE) },
                            onDislike = { onVote(post, VoteType.DISLIKE) },
                            onOpenComments = { onOpenComments(post) },
                            onOpenDebateSide = { stance -> onOpenDebateSide(post, stance) },
                            onQuoteClapback = { onQuoteClapback(post) },
                            onEcho = { onEcho(post) },
                            onBookmark = { onBookmark(post) },
                            onExpandLyrics = { onExpandLyrics(post) },
                            onRoomClick = onOpenRoom,
                            onSpeakerClick = onOpenSpeakerProfile,
                            onReport = { onReportPost(post) },
                            onMuteSpeaker = { onMuteSpeaker(post.authorUid, post.displaySpeakerName()) },
                            onBlockSpeaker = { onBlockSpeaker(post.authorUid, post.displaySpeakerName()) },
                            onHide = { onHidePost(post.id) },
                            onDelete = { onDeletePost(post.id) },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }

            ProfileTab.LIKED -> {
                if (!isOwnProfile) {
                    item {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.ThumbUp,
                            accent = accent.primary,
                            title = "Liked takes are private",
                            subtitle = "Only @${profile.handle} can view their liked voice library."
                        )
                    }
                } else if (likedTakes.isEmpty()) {
                    item {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.ThumbUp,
                            accent = accent.primary,
                            title = "No liked takes yet",
                            subtitle = "Tap Like on any voice take in Feed or Discover to build your collection."
                        )
                    }
                } else {
                    items(likedTakes, key = { it.id }) { post ->
                        VoicePostCard(
                            post = post,
                            playback = playback,
                            reaction = reactions[post.id],
                            currentUid = currentUid,
                            waveformStyle = waveStyle,
                            safetySettings = safetySettings,
                            onPlayPause = { onPlayPost(post) },
                            onSeekToMs = onSeekToMs,
                            onCycleSpeed = onCycleSpeed,
                            onLike = { onVote(post, VoteType.LIKE) },
                            onDislike = { onVote(post, VoteType.DISLIKE) },
                            onOpenComments = { onOpenComments(post) },
                            onOpenDebateSide = { stance -> onOpenDebateSide(post, stance) },
                            onQuoteClapback = { onQuoteClapback(post) },
                            onEcho = { onEcho(post) },
                            onBookmark = { onBookmark(post) },
                            onExpandLyrics = { onExpandLyrics(post) },
                            onRoomClick = onOpenRoom,
                            onSpeakerClick = onOpenSpeakerProfile,
                            onReport = { onReportPost(post) },
                            onMuteSpeaker = { onMuteSpeaker(post.authorUid, post.displaySpeakerName()) },
                            onBlockSpeaker = { onBlockSpeaker(post.authorUid, post.displaySpeakerName()) },
                            onHide = { onHidePost(post.id) },
                            onDelete = { onDeletePost(post.id) },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }

            ProfileTab.TAKES -> {
                if (myTakes.isEmpty()) {
                    item {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.GraphicEq,
                            accent = accent.primary,
                            title = "No voice takes yet",
                            subtitle = "Tap the Studio Mic below to record your first unfiltered take."
                        )
                    }
                } else {
                    items(myTakes, key = { it.id }) { post ->
                        VoicePostCard(
                            post = post,
                            playback = playback,
                            reaction = reactions[post.id],
                            currentUid = currentUid,
                            waveformStyle = waveStyle,
                            safetySettings = safetySettings,
                            onPlayPause = { onPlayPost(post) },
                            onSeekToMs = onSeekToMs,
                            onCycleSpeed = onCycleSpeed,
                            onLike = { onVote(post, VoteType.LIKE) },
                            onDislike = { onVote(post, VoteType.DISLIKE) },
                            onOpenComments = { onOpenComments(post) },
                            onOpenDebateSide = { stance -> onOpenDebateSide(post, stance) },
                            onQuoteClapback = { onQuoteClapback(post) },
                            onEcho = { onEcho(post) },
                            onBookmark = { onBookmark(post) },
                            onExpandLyrics = { onExpandLyrics(post) },
                            onRoomClick = onOpenRoom,
                            onSpeakerClick = onOpenSpeakerProfile,
                            onReport = { onReportPost(post) },
                            onMuteSpeaker = { onMuteSpeaker(post.authorUid, post.displaySpeakerName()) },
                            onBlockSpeaker = { onBlockSpeaker(post.authorUid, post.displaySpeakerName()) },
                            onHide = { onHidePost(post.id) },
                            onDelete = { onDeletePost(post.id) },
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCustomizeSheet && isOwnProfile) {
        CustomizeStudioBottomSheet(
            profile = profile,
            recordingState = recordingState,
            audioRecorder = audioRecorder,
            onDismiss = {
                audioRecorder.reset()
                showCustomizeSheet = false
            },
            onSave = { name, handle, bio, accentKey, bannerKey, wfStyle, interests, avatarUri, bioFile, bioDur, bioPeaks ->
                onSaveCustomization(
                    name, handle, bio, accentKey, bannerKey, wfStyle,
                    interests, avatarUri, bioFile, bioDur, bioPeaks
                ) {
                    showCustomizeSheet = false
                }
            }
        )
    }
}

@Composable
private fun ProfileReplyCard(
    parentPost: VoicePost,
    comment: VoiceComment,
    playback: PlaybackState,
    accent: Color,
    secondaryAccent: Color,
    waveStyle: WaveformStyle,
    onPlayVoiceReply: () -> Unit,
    onOpenThread: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStudioHaptics()
    val isVoice = !comment.audioMediaId.isNullOrBlank()
    val isThisActive = isVoice && playback.activeMediaId == comment.audioMediaId
    val isPlaying = isThisActive && playback.playing
    val stance = comment.stanceEnum()
    val stanceTint = when (stance) {
        DebateStance.AGREE -> SignalMint
        DebateStance.DISAGREE -> ElectricCoral
        DebateStance.NONE -> accent
    }
    val parentSummary = parentPost.headlineText?.takeIf { it.isNotBlank() }
        ?: parentPost.transcript.take(64).ifBlank { "Voice take" }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) stanceTint.copy(alpha = 0.65f) else ObsidianHairline
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenThread)
            .testTag("profile_reply_card_${comment.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Replying to ${parentPost.displaySpeakerName()} · $parentSummary",
                    style = MaterialTheme.typography.labelSmall,
                    color = TitaniumMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = TitaniumMuted
                )
            }

            if (stance != DebateStance.NONE) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = stanceTint.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${stance.label.uppercase()} SIDE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = stanceTint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (isVoice) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(stanceTint)
                            .clickable {
                                haptics(HapticCue.REACTION)
                                onPlayVoiceReply()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play voice reply",
                            tint = StudioWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    InteractiveWaveform(
                        peaks = comment.waveformPeaks.map { it.toFloat() },
                        progress = if (isThisActive && playback.durationMs > 0L) {
                            playback.positionMs.toFloat() / playback.durationMs.toFloat()
                        } else 0f,
                        playing = isPlaying,
                        style = waveStyle,
                        accentColor = stanceTint,
                        secondaryColor = secondaryAccent,
                        modifier = Modifier.weight(1f).height(30.dp)
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
                        color = StudioWhite.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!comment.textBody.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = comment.textBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioWhite
                )
            }
        }
    }
}

@Composable
private fun ProfileEmptyTabCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianHairline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = StudioWhite)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TitaniumMuted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeStudioBottomSheet(
    profile: UserProfile,
    recordingState: RecordingState,
    audioRecorder: AudioRecorderController,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, List<String>, Uri?, File?, Long, List<Float>) -> Unit
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
