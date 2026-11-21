package com.unmuted.app.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.AudioRecorderController
import com.unmuted.app.core.PlaybackPhase
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
import com.unmuted.app.ui.components.VoicePostCard
import com.unmuted.app.ui.components.isPlaybackActive
import com.unmuted.app.ui.components.rememberStudioHaptics
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground),
        contentPadding = PaddingValues(bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Customizable Studio Header + Pinned Voice Bio
        item(key = "profile_header", contentType = "header") {
            Column {
                ProfileHeader(
                    profile = profile,
                    isOwnProfile = isOwnProfile,
                    isFollowing = isFollowing,
                    accent = accent,
                    bannerMood = bannerMood,
                    myTakesCount = myTakes.size,
                    joinedRoomsCount = joinedRooms.size,
                    haptics = haptics,
                    onBack = onBack,
                    onOpenSafetyControls = onOpenSafetyControls,
                    onCustomizeStudio = { showCustomizeSheet = true },
                    onSignOut = onSignOut,
                    onMuteSpeaker = onMuteSpeaker,
                    onBlockSpeaker = onBlockSpeaker,
                    onReportProfile = onReportProfile,
                    onMessageUser = onMessageUser,
                    onToggleFollow = onToggleFollow
                )

                // Identity Row + Pinned Voice Bio directly under banner
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    // Pinned Voice Bio Intro Clip
                    ProfileVoiceBioSection(
                        profile = profile,
                        isOwnProfile = isOwnProfile,
                        playback = playback,
                        accent = accent,
                        waveStyle = waveStyle,
                        haptics = haptics,
                        onPlayVoiceBio = onPlayVoiceBio,
                        onSeekToMs = onSeekToMs,
                        onOpenCustomizeSheet = { showCustomizeSheet = true }
                    )

                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = StudioWhite)
                    }

                    // Interests / Topic Tags + Active Studio Theme Pill
                    if (profile.interests.isNotEmpty() || isOwnProfile) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isOwnProfile) {
                                item(key = "theme_chip", contentType = "theme_chip") {
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
                            items(profile.interests, key = { it }, contentType = { "interest_tag" }) { tag ->
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
                    item(key = "replies_loading", contentType = "state") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accent.primary)
                        }
                    }
                } else if (profileReplies.isEmpty()) {
                    item(key = "replies_empty", contentType = "state") {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.ChatBubbleOutline,
                            accent = accent.primary,
                            title = "No voice replies yet",
                            subtitle = "Jump into a thread or Hot Take debate to see your spoken replies here."
                        )
                    }
                } else {
                    items(
                        profileReplies,
                        key = { (_, comment) -> comment.id },
                        contentType = { "profile_reply" }
                    ) { (parentPost, comment) ->
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
                    item(key = "debates_empty", contentType = "state") {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.LocalFireDepartment,
                            accent = com.unmuted.app.ui.theme.AmberVinyl,
                            title = "No audio debates yet",
                            subtitle = "Start a Hot Take in any Room or join an Agree / Disagree side to appear here."
                        )
                    }
                } else {
                    items(
                        myDebates,
                        key = { it.id },
                        contentType = { "voice_post" }
                    ) { post ->
                        val isPostActive = post.isPlaybackActive(playback)
                        VoicePostCard(
                            post = post,
                            isActive = isPostActive,
                            isPlaying = isPostActive && playback.playing,
                            positionMs = if (isPostActive) {
                                if (post.hasQuoteClip()) playback.combinedPositionMs else playback.positionMs
                            } else 0L,
                            durationMs = if (isPostActive) {
                                if (post.hasQuoteClip() && playback.combinedDurationMs > 0L) playback.combinedDurationMs
                                else if (playback.durationMs > 0L) playback.durationMs
                                else post.durationMs.coerceAtLeast(1L)
                            } else post.durationMs.coerceAtLeast(1L),
                            isBuffering = isPostActive && playback.buffering,
                            isQuotedPhase = isPostActive && post.hasQuoteClip() && playback.activePhase == PlaybackPhase.QUOTED_CLIP,
                            playbackSpeed = playback.speed,
                            quoteSplitFraction = if (isPostActive && playback.quoteSplitFraction > 0f) playback.quoteSplitFraction else null,
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
                    item(key = "liked_private", contentType = "state") {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.ThumbUp,
                            accent = accent.primary,
                            title = "Liked takes are private",
                            subtitle = "Only @${profile.handle} can view their liked voice library."
                        )
                    }
                } else if (likedTakes.isEmpty()) {
                    item(key = "liked_empty", contentType = "state") {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.ThumbUp,
                            accent = accent.primary,
                            title = "No liked takes yet",
                            subtitle = "Tap Like on any voice take in Feed or Discover to build your collection."
                        )
                    }
                } else {
                    items(
                        likedTakes,
                        key = { it.id },
                        contentType = { "voice_post" }
                    ) { post ->
                        val isPostActive = post.isPlaybackActive(playback)
                        VoicePostCard(
                            post = post,
                            isActive = isPostActive,
                            isPlaying = isPostActive && playback.playing,
                            positionMs = if (isPostActive) {
                                if (post.hasQuoteClip()) playback.combinedPositionMs else playback.positionMs
                            } else 0L,
                            durationMs = if (isPostActive) {
                                if (post.hasQuoteClip() && playback.combinedDurationMs > 0L) playback.combinedDurationMs
                                else if (playback.durationMs > 0L) playback.durationMs
                                else post.durationMs.coerceAtLeast(1L)
                            } else post.durationMs.coerceAtLeast(1L),
                            isBuffering = isPostActive && playback.buffering,
                            isQuotedPhase = isPostActive && post.hasQuoteClip() && playback.activePhase == PlaybackPhase.QUOTED_CLIP,
                            playbackSpeed = playback.speed,
                            quoteSplitFraction = if (isPostActive && playback.quoteSplitFraction > 0f) playback.quoteSplitFraction else null,
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
                    item(key = "takes_empty", contentType = "state") {
                        ProfileEmptyTabCard(
                            icon = Icons.Filled.GraphicEq,
                            accent = accent.primary,
                            title = "No voice takes yet",
                            subtitle = "Tap the Studio Mic below to record your first unfiltered take."
                        )
                    }
                } else {
                    items(
                        myTakes,
                        key = { it.id },
                        contentType = { "voice_post" }
                    ) { post ->
                        val isPostActive = post.isPlaybackActive(playback)
                        VoicePostCard(
                            post = post,
                            isActive = isPostActive,
                            isPlaying = isPostActive && playback.playing,
                            positionMs = if (isPostActive) {
                                if (post.hasQuoteClip()) playback.combinedPositionMs else playback.positionMs
                            } else 0L,
                            durationMs = if (isPostActive) {
                                if (post.hasQuoteClip() && playback.combinedDurationMs > 0L) playback.combinedDurationMs
                                else if (playback.durationMs > 0L) playback.durationMs
                                else post.durationMs.coerceAtLeast(1L)
                            } else post.durationMs.coerceAtLeast(1L),
                            isBuffering = isPostActive && playback.buffering,
                            isQuotedPhase = isPostActive && post.hasQuoteClip() && playback.activePhase == PlaybackPhase.QUOTED_CLIP,
                            playbackSpeed = playback.speed,
                            quoteSplitFraction = if (isPostActive && playback.quoteSplitFraction > 0f) playback.quoteSplitFraction else null,
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
        ProfileCustomizationSheet(
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
fun ProfileReplyCard(
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
fun ProfileEmptyTabCard(
    icon: ImageVector,
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
