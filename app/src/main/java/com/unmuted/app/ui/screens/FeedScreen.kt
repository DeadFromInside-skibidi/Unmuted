package com.unmuted.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.unmuted.app.core.PlaybackPhase
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.data.formatRelativeTime
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.ReactiveOrbBackdrop
import com.unmuted.app.ui.components.SyncedLyricsView
import com.unmuted.app.ui.components.UnfilteredFilterNoticeBanner
import com.unmuted.app.ui.components.UserAvatarBadge
import com.unmuted.app.ui.components.VoicePostCard
import com.unmuted.app.ui.components.VoicePostSkeleton
import com.unmuted.app.ui.components.isPlaybackActive
import com.unmuted.app.ui.components.rememberStudioHaptics
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class FeedFilterTab(val label: String) {
    FOR_YOU("For You"),
    FOLLOWING("Following"),
    TRENDING("Trending")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    pagedPosts: LazyPagingItems<VoicePost>,
    recentPosts: List<VoicePost>,
    postOverrides: Map<String, VoicePost>,
    myProfile: UserProfile?,
    reactions: Map<String, UserPostReaction>,
    followingUids: Set<String>,
    hiddenPostIds: Set<String>,
    filteredAuthorUids: Set<String>,
    safetySettings: SafetySettings,
    playback: PlaybackState,
    currentUid: String,
    radioModeEnabled: Boolean = playback.radioModeEnabled,
    onToggleRadioMode: () -> Unit = {},
    onPlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onOpenDebateSide: (VoicePost, DebateStance) -> Unit = { post, _ -> onOpenComments(post) },
    onQuoteClapback: (VoicePost) -> Unit = {},
    onEcho: (VoicePost) -> Unit,
    onBookmark: (VoicePost) -> Unit,
    onRecordVoiceDrop: () -> Unit,
    onRecordNewVoice: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSafetyControls: () -> Unit,
    onEnableUnfiltered: () -> Unit,
    onOpenSpeakerProfile: (String) -> Unit,
    onOpenRoom: (String) -> Unit,
    onReportPost: (VoicePost) -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onHidePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onPreloadPosts: (List<VoicePost>) -> Unit = {},
    onSeedSampleData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(FeedFilterTab.FOR_YOU) }
    var activeDropIndex by remember { mutableStateOf<Int?>(null) }
    var fullLyricsPost by remember { mutableStateOf<VoicePost?>(null) }
    val listState = rememberLazyListState()
    val haptics = rememberStudioHaptics()

    val now = System.currentTimeMillis()
    val voiceDrops = remember(recentPosts, hiddenPostIds, filteredAuthorUids, safetySettings) {
        recentPosts
            .filterNot { hiddenPostIds.contains(it.id) || filteredAuthorUids.contains(it.authorUid) }
            .filter { safetySettings.showUnfilteredControversial || !it.isUnfilteredOrControversial() }
            .filter { it.kindEnum() == PostKind.VOICE_DROP && (it.expiresAtMs == 0L || it.expiresAtMs > now) }
    }

    val snapshotItems = pagedPosts.itemSnapshotList.items
    val hiddenControversialCount = remember(snapshotItems, safetySettings) {
        if (safetySettings.showUnfilteredControversial) 0
        else snapshotItems.count { it.kindEnum() != PostKind.VOICE_DROP && it.isUnfilteredOrControversial() }
    }

    // Build visible list over currently loaded Paging 3 pages while preserving pagination scroll trigger
    val preferredWaveStyle = WaveformStyle.fromKey(myProfile?.waveformStyle)

    LaunchedEffect(voiceDrops) {
        if (voiceDrops.isNotEmpty()) {
            onPreloadPosts(voiceDrops.take(4))
        }
    }

    LaunchedEffect(listState, snapshotItems.size) {
        if (snapshotItems.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val firstIdx = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
            val lastIdx = ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 3) - 1)
                .coerceAtLeast(firstIdx)
            firstIdx to lastIdx
        }
            .distinctUntilChanged()
            .collect { (firstIdx, lastIdx) ->
                val items = pagedPosts.itemSnapshotList.items
                if (items.isNotEmpty()) {
                    val start = firstIdx.coerceIn(0, items.lastIndex)
                    val end = (lastIdx + 4).coerceAtMost(items.lastIndex)
                    if (start <= end) {
                        onPreloadPosts(items.subList(start, end + 1))
                    }
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Midnight Studio Top Header + Safety Shield Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(ElectricCoral, StudioViolet))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = StudioWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Unmuted",
                        style = MaterialTheme.typography.headlineSmall,
                        color = StudioWhite,
                        maxLines = 1
                    )
                    Text(
                        text = "Tap any waveform to listen",
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (radioModeEnabled) ElectricCoral.copy(alpha = 0.22f) else ObsidianSurface,
                    border = BorderStroke(1.dp, if (radioModeEnabled) ElectricCoral.copy(alpha = 0.70f) else ObsidianHairline),
                    modifier = Modifier
                        .height(40.dp)
                        .clickable {
                            haptics(HapticCue.CAPTION_TOGGLE)
                            onToggleRadioMode()
                        }
                        .testTag("feed_radio_mode_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Radio,
                            contentDescription = if (radioModeEnabled) "Radio Mode Active" else "Radio Mode Inactive",
                            tint = if (radioModeEnabled) ElectricCoral else TitaniumMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Radio",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (radioModeEnabled) FontWeight.Bold else FontWeight.Normal,
                            color = if (radioModeEnabled) StudioWhite else TitaniumMuted
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = ObsidianSurface,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            haptics(HapticCue.CAPTION_TOGGLE)
                            onOpenSafetyControls()
                        }
                        .testTag("feed_safety_controls_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.GppGood,
                            contentDescription = "Safety & Debate Controls",
                            tint = if (safetySettings.showUnfilteredControversial) SignalMint else StudioViolet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                UserAvatarBadge(
                    name = myProfile?.displayName ?: "You",
                    avatarBase64 = myProfile?.avatarBase64,
                    accentKey = myProfile?.accentKey ?: "CORAL",
                    size = 40.dp,
                    showRing = true,
                    modifier = Modifier
                        .clickable(onClick = onOpenProfile)
                        .testTag("top_profile_avatar")
                )
            }
        }

        // Feed Tabs (For You / Following / Trending)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeedFilterTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) ElectricCoral.copy(alpha = 0.2f) else ObsidianSurface,
                    modifier = Modifier
                        .clickable {
                            haptics(HapticCue.CAPTION_TOGGLE)
                            selectedTab = tab
                        }
                        .testTag("feed_tab_${tab.name.lowercase()}")
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) ElectricCoral else TitaniumMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }

        val isRefreshing = pagedPosts.loadState.refresh is LoadState.Loading && pagedPosts.itemCount > 0
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { pagedPosts.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("feed_paging_list"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 152.dp, start = 14.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Audio Stories Rail ("Voice Drops")
            item(key = "voice_drops_rail") {
                Column {
                    Text(
                        text = "VOICE DROPS · 24H AUDIO STORIES",
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable(onClick = onRecordVoiceDrop)
                                    .testTag("add_voice_drop_button")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(ObsidianSurfaceElevated)
                                        .border(
                                            1.5.dp,
                                            Brush.linearGradient(listOf(ElectricCoral, StudioViolet)),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Record Voice Drop story",
                                        tint = ElectricCoral,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = "Your Drop",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        itemsIndexed(
                            voiceDrops,
                            key = { _, drop -> drop.id },
                            contentType = { _, _ -> "voice_drop" }
                        ) { index, drop ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable {
                                        activeDropIndex = index
                                        onPlayPost(drop)
                                    }
                                    .testTag("voice_drop_item_${drop.id}")
                            ) {
                                UserAvatarBadge(
                                    name = drop.displaySpeakerName(),
                                    avatarBase64 = if (drop.anonymousMode) null else drop.authorAvatarBase64,
                                    accentKey = drop.authorAccentKey,
                                    anonymousMode = drop.anonymousMode,
                                    size = 62.dp,
                                    showRing = true
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = drop.displaySpeakerName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (hiddenControversialCount > 0) {
                item(key = "unfiltered_filter_notice") {
                    UnfilteredFilterNoticeBanner(
                        hiddenCount = hiddenControversialCount,
                        onEnableUnfiltered = onEnableUnfiltered
                    )
                }
            }

            // Paging 3 Refresh State: Shimmer skeletons during initial load
            when (val refreshState = pagedPosts.loadState.refresh) {
                is LoadState.Loading -> {
                    items(4, key = { "feed_skeleton_$it" }, contentType = { "feed_skeleton" }) {
                        VoicePostSkeleton()
                    }
                }
                is LoadState.Error -> {
                    item(key = "feed_error") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = ObsidianSurface,
                            border = BorderStroke(1.dp, AcousticError.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = refreshState.error.localizedMessage ?: "Failed to load voices",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = { pagedPosts.retry() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                is LoadState.NotLoading -> {
                    var renderedCount = 0
                    val itemCount = pagedPosts.itemCount
                    // For Trending tab, sort indices by engagement score over loaded pages while still calling pagedPosts[idx] to trigger Paging 3 prefetch
                    val orderedIndices: List<Int> = if (selectedTab == FeedFilterTab.TRENDING) {
                        (0 until itemCount).sortedByDescending { idx ->
                            val p = pagedPosts.peek(idx)?.let { postOverrides[it.id] ?: it }
                            if (p == null) 0L else p.likeCount * 2 + p.commentCount * 3 + p.echoCount * 2
                        }
                    } else {
                        (0 until itemCount).toList()
                    }

                    items(
                        count = orderedIndices.size,
                        key = { i ->
                            val realIdx = orderedIndices[i]
                            pagedPosts.peek(realIdx)?.id ?: "page_slot_$realIdx"
                        },
                        contentType = { "feed_post" }
                    ) { i ->
                        val realIdx = orderedIndices[i]
                        val rawPost = pagedPosts[realIdx] ?: return@items
                        val post = postOverrides[rawPost.id] ?: rawPost
                        val visible = post.kindEnum() != PostKind.VOICE_DROP &&
                            !hiddenPostIds.contains(post.id) &&
                            !filteredAuthorUids.contains(post.authorUid) &&
                            (safetySettings.showUnfilteredControversial || !post.isUnfilteredOrControversial()) &&
                            (selectedTab != FeedFilterTab.FOLLOWING || followingUids.contains(post.authorUid) || post.authorUid == currentUid)

                        if (visible) {
                            renderedCount += 1
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
                                waveformStyle = preferredWaveStyle,
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
                                onExpandLyrics = { fullLyricsPost = post },
                                onRoomClick = onOpenRoom,
                                onSpeakerClick = onOpenSpeakerProfile,
                                onReport = { onReportPost(post) },
                                onMuteSpeaker = { onMuteSpeaker(post.authorUid, post.displaySpeakerName()) },
                                onBlockSpeaker = { onBlockSpeaker(post.authorUid, post.displaySpeakerName()) },
                                onHide = { onHidePost(post.id) },
                                onDelete = { onDeletePost(post.id) }
                            )
                        }
                    }

                    val hasAnyVisible = snapshotItems.any { raw ->
                        val post = postOverrides[raw.id] ?: raw
                        post.kindEnum() != PostKind.VOICE_DROP &&
                            !hiddenPostIds.contains(post.id) &&
                            !filteredAuthorUids.contains(post.authorUid) &&
                            (safetySettings.showUnfilteredControversial || !post.isUnfilteredOrControversial()) &&
                            (selectedTab != FeedFilterTab.FOLLOWING || followingUids.contains(post.authorUid) || post.authorUid == currentUid)
                    }

                    if (!hasAnyVisible && pagedPosts.loadState.append !is LoadState.Loading) {
                        item(key = "feed_empty") {
                            EmptyFeedStudioCard(
                                title = if (selectedTab == FeedFilterTab.FOLLOWING) "No followed voices yet" else "The mic is live",
                                subtitle = if (selectedTab == FeedFilterTab.FOLLOWING) {
                                    "Follow speakers from the For You feed or record your own take."
                                } else {
                                    "Be the first to share an unfiltered voice take with live word-synced captions."
                                },
                                onSpeak = onRecordNewVoice,
                                onSeedSampleData = onSeedSampleData
                            )
                        }
                    }
                }
            }

            // Paging 3 Append Loading / Retry Footer
            when (val appendState = pagedPosts.loadState.append) {
                is LoadState.Loading -> {
                    item(key = "feed_append_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ElectricCoral, strokeWidth = 2.dp)
                        }
                    }
                }
                is LoadState.Error -> {
                    item(key = "feed_append_error") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, AcousticError.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = appendState.error.localizedMessage ?: "Couldn't load more voices",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                OutlinedButton(onClick = { pagedPosts.retry() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        }
    }

    val dropIdx = activeDropIndex
    if (dropIdx != null && dropIdx in voiceDrops.indices) {
        VoiceDropStoryDialog(
            drops = voiceDrops,
            initialIndex = dropIdx,
            playback = playback,
            onPlayDrop = onPlayPost,
            onSeekToMs = onSeekToMs,
            onDismiss = { activeDropIndex = null }
        )
    }

    val lyricsTarget = fullLyricsPost
    if (lyricsTarget != null) {
        FullLyricsFocusDialog(
            post = lyricsTarget,
            playback = playback,
            onPlayPause = { onPlayPost(lyricsTarget) },
            onSeekToMs = onSeekToMs,
            onDismiss = { fullLyricsPost = null }
        )
    }
}

@Composable
private fun EmptyFeedStudioCard(
    title: String,
    subtitle: String,
    onSpeak: () -> Unit,
    onSeedSampleData: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ObsidianSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ElectricCoral.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = ElectricCoral, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = StudioWhite)
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TitaniumMuted
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onSpeak,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("empty_feed_record_btn")
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Record First Voice")
            }
            if (onSeedSampleData != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSeedSampleData,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberVinyl)
                    Spacer(Modifier.width(6.dp))
                    Text("Load Sample Studio Takes", color = StudioWhite)
                }
            }
        }
    }
}

@Composable
private fun VoiceDropStoryDialog(
    drops: List<VoicePost>,
    initialIndex: Int,
    playback: PlaybackState,
    onPlayDrop: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var index by remember { mutableIntStateOf(initialIndex) }
    val drop = drops[index.coerceIn(0, drops.lastIndex)]
    val isThisActive = playback.activeMediaId == drop.audioMediaId
    val durationMs = if (isThisActive && playback.durationMs > 0L) playback.durationMs else drop.durationMs.coerceAtLeast(1L)
    val posMs = if (isThisActive) playback.positionMs else 0L
    val progress = (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    LaunchedEffect(index) {
        onPlayDrop(drops[index.coerceIn(0, drops.lastIndex)])
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
        ) {
            ReactiveOrbBackdrop(
                active = isThisActive && playback.playing,
                primaryColor = ElectricCoral,
                secondaryColor = StudioViolet
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        drops.forEachIndexed { idx, _ ->
                            val segProgress = when {
                                idx < index -> 1f
                                idx == index -> progress
                                else -> 0f
                            }
                            LinearProgressIndicator(
                                progress = { segProgress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(CircleShape),
                                color = ElectricCoral,
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatarBadge(
                                name = drop.displaySpeakerName(),
                                avatarBase64 = if (drop.anonymousMode) null else drop.authorAvatarBase64,
                                anonymousMode = drop.anonymousMode,
                                size = 40.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(drop.displaySpeakerName(), style = MaterialTheme.typography.titleMedium, color = StudioWhite)
                                Text(
                                    "Voice Drop · ${formatRelativeTime(drop.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Voice Drop", tint = StudioWhite)
                        }
                    }
                }

                SyncedLyricsView(
                    segments = drop.captionSegments,
                    fallbackTranscript = drop.transcript,
                    positionMs = posMs,
                    isActiveAudio = isThisActive,
                    isFullStage = true,
                    accentColor = ElectricCoral,
                    onSeekToMs = onSeekToMs,
                    modifier = Modifier.weight(1f).padding(vertical = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        modifier = Modifier.clickable(enabled = index > 0) { index = (index - 1).coerceAtLeast(0) }
                    ) {
                        Text(
                            "Prev",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (index > 0) StudioWhite else TitaniumMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ElectricCoral)
                            .clickable { onPlayDrop(drop) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isThisActive && playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play or pause drop",
                            tint = StudioWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        modifier = Modifier.clickable {
                            if (index < drops.lastIndex) index += 1 else onDismiss()
                        }
                    ) {
                        Text(
                            if (index < drops.lastIndex) "Next" else "Done",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioWhite,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullLyricsFocusDialog(
    post: VoicePost,
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val isClapback = post.hasQuoteClip()
    val expectedId = if (isClapback) "clap_${post.id}" else post.audioMediaId
    val isThisActive = !expectedId.isNullOrBlank() && playback.activeMediaId == expectedId
    val durationMs = if (isThisActive && playback.durationMs > 0L) playback.durationMs else post.durationMs.coerceAtLeast(1L)
    val posMs = if (isThisActive) playback.positionMs else 0L
    val progress = (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1E1124), ObsidianBackground, ObsidianBackground))
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.displaySpeakerName(),
                            style = MaterialTheme.typography.titleLarge,
                            color = StudioWhite
                        )
                        Text(
                            text = "LIVE LYRICS STAGE · TAP ANY LINE TO JUMP",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCoral
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Lyrics Stage", tint = StudioWhite)
                    }
                }

                SyncedLyricsView(
                    segments = post.captionSegments,
                    fallbackTranscript = post.transcript,
                    positionMs = posMs,
                    isActiveAudio = isThisActive,
                    isFullStage = true,
                    accentColor = if (post.anonymousMode) StudioViolet else ElectricCoral,
                    onSeekToMs = onSeekToMs,
                    modifier = Modifier.weight(1f)
                )

                InteractiveWaveform(
                    peaks = post.waveformPeaks.map { it.toFloat() },
                    progress = progress,
                    playing = isThisActive && playback.playing,
                    accentColor = if (post.anonymousMode) StudioViolet else ElectricCoral,
                    onSeekFraction = { frac -> onSeekToMs((durationMs * frac).toLong()) }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDurationMs(posMs), style = MaterialTheme.typography.labelMedium, color = TitaniumMuted)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ElectricCoral)
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isThisActive && playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = StudioWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(formatDurationMs(durationMs), style = MaterialTheme.typography.labelMedium, color = TitaniumMuted)
                }
            }
        }
    }
}
