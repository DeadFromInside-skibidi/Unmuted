package com.unmuted.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.unmuted.app.core.PlaybackPhase
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.TrendingAudioTopic
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.UserProfile
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.UnfilteredFilterNoticeBanner
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
import com.unmuted.app.ui.viewmodel.DataUiState
import com.unmuted.app.ui.viewmodel.DiscoverFilter
import kotlinx.coroutines.flow.distinctUntilChanged

enum class DiscoverSubTab(val label: String) {
    EXPLORE("Explore"),
    PULSES("Pulses")
}

private fun DiscoverFilter.icon(): ImageVector = when (this) {
    DiscoverFilter.TRENDING -> Icons.Filled.LocalFireDepartment
    DiscoverFilter.CONTROVERSIAL -> Icons.Filled.Bolt
    DiscoverFilter.ANONYMOUS -> Icons.Filled.Shield
    DiscoverFilter.FOLLOWING -> Icons.Filled.People
    DiscoverFilter.SHORT_CLIPS -> Icons.Filled.GraphicEq
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    pagedPosts: LazyPagingItems<VoicePost>,
    recentPostsState: DataUiState<List<VoicePost>>,
    postOverrides: Map<String, VoicePost>,
    roomsState: DataUiState<List<CommunityRoom>>,
    myProfile: UserProfile?,
    reactions: Map<String, UserPostReaction>,
    followingUids: Set<String>,
    hiddenPostIds: Set<String>,
    filteredAuthorUids: Set<String>,
    safetySettings: SafetySettings,
    searchQuery: String,
    activeFilter: DiscoverFilter,
    playback: PlaybackState,
    currentUid: String,
    subTab: DiscoverSubTab,
    radioModeEnabled: Boolean = playback.radioModeEnabled,
    onToggleRadioMode: () -> Unit = {},
    trendingTopics: List<TrendingAudioTopic> = emptyList(),
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (DiscoverFilter) -> Unit,
    onSubTabChanged: (DiscoverSubTab) -> Unit,
    onEnableUnfiltered: () -> Unit,
    onAutoPlayPost: (VoicePost) -> Unit,
    onPlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onOpenDebateSide: (VoicePost, DebateStance) -> Unit = { post, _ -> onOpenComments(post) },
    onQuoteClapback: (VoicePost) -> Unit = {},
    onEcho: (VoicePost) -> Unit,
    onBookmark: (VoicePost) -> Unit,
    onExpandLyrics: (VoicePost) -> Unit,
    onOpenRoom: (String) -> Unit,
    onOpenSpeakerProfile: (String) -> Unit,
    onRecordNewVoice: () -> Unit,
    onRecordPulse: () -> Unit,
    onReportPost: (VoicePost) -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onHidePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onPreloadPosts: (List<VoicePost>) -> Unit = {},
    onSeedSampleData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (subTab == DiscoverSubTab.PULSES) {
        BackHandler { onSubTabChanged(DiscoverSubTab.EXPLORE) }
    }

    val exploreListState = rememberLazyListState()
    val haptics = rememberStudioHaptics()

    val hiddenControversialCount = remember(pagedPosts.itemCount, safetySettings) {
        if (safetySettings.showUnfilteredControversial) 0
        else {
            (0 until pagedPosts.itemCount).count { idx ->
                val p = pagedPosts.peek(idx)
                p != null && p.isUnfilteredOrControversial()
            }
        }
    }

    val allRooms = remember(roomsState, safetySettings) {
        (roomsState as? DataUiState.Success)?.data.orEmpty()
            .filter { safetySettings.showUnfilteredControversial || !it.isUnfilteredControversialRoom() }
    }
    val preferredWaveStyle = WaveformStyle.fromKey(myProfile?.waveformStyle)

    val trimmedQuery = searchQuery.trim()
    val queryLower = trimmedQuery.lowercase()

    val matchingRooms = remember(allRooms, queryLower) {
        if (queryLower.isEmpty()) emptyList()
        else allRooms.filter { room ->
            room.name.lowercase().contains(queryLower) ||
                room.slug.lowercase().contains(queryLower) ||
                room.description.lowercase().contains(queryLower) ||
                room.category.lowercase().contains(queryLower)
        }
    }

    val matchingHashtags = remember(queryLower, pagedPosts.itemCount) {
        if (queryLower.isEmpty()) emptyList()
        else (0 until pagedPosts.itemCount)
            .mapNotNull { pagedPosts.peek(it) }
            .flatMap { it.hashtags }
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() && it.lowercase().contains(queryLower.removePrefix("#")) }
            .groupingBy { it.lowercase() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key to it.value }
    }

    // Prefetch upcoming audio streams for visible posts
    LaunchedEffect(exploreListState, subTab, pagedPosts.itemCount) {
        if (subTab != DiscoverSubTab.EXPLORE || pagedPosts.itemCount == 0) return@LaunchedEffect
        snapshotFlow {
            val firstIdx = exploreListState.firstVisibleItemIndex
            val lastIdx = exploreListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: firstIdx
            firstIdx to lastIdx
        }
            .distinctUntilChanged()
            .collect { (firstIdx, lastIdx) ->
                val start = firstIdx.coerceIn(0, pagedPosts.itemCount - 1)
                val end = (lastIdx + 3).coerceAtMost(pagedPosts.itemCount - 1)
                if (start <= end) {
                    val postsToPreload = (start..end).mapNotNull { pagedPosts.peek(it) }
                    if (postsToPreload.isNotEmpty()) {
                        onPreloadPosts(postsToPreload)
                    }
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Compact Editorial Header + Explore / Pulses Secondary Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        imageVector = if (subTab == DiscoverSubTab.EXPLORE) Icons.Filled.Explore else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = StudioWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.headlineSmall,
                        color = StudioWhite,
                        maxLines = 1
                    )
                    Text(
                        text = if (subTab == DiscoverSubTab.EXPLORE) "Live audio debates & voices" else "Auto-play vertical voice shorts",
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
                        .height(36.dp)
                        .clickable {
                            haptics(HapticCue.CAPTION_TOGGLE)
                            onToggleRadioMode()
                        }
                        .testTag("discover_radio_mode_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Radio,
                            contentDescription = if (radioModeEnabled) "Radio Mode Active" else "Radio Mode Inactive",
                            tint = if (radioModeEnabled) ElectricCoral else TitaniumMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Radio",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (radioModeEnabled) FontWeight.Bold else FontWeight.Normal,
                            color = if (radioModeEnabled) StudioWhite else TitaniumMuted,
                            maxLines = 1
                        )
                    }
                }

                // Secondary Toggle: Explore | Pulses
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianHairline)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        DiscoverSubTab.entries.forEach { tab ->
                            val selected = tab == subTab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (selected) Brush.linearGradient(listOf(ElectricCoral, StudioViolet))
                                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .clickable {
                                        haptics(HapticCue.CAPTION_TOGGLE)
                                        onSubTabChanged(tab)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("discover_subtab_${tab.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) StudioWhite else TitaniumMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        if (subTab == DiscoverSubTab.PULSES) {
            PulsesScreen(
                postsState = recentPostsState,
                reactions = reactions,
                playback = playback,
                filteredAuthorUids = filteredAuthorUids,
                safetySettings = safetySettings,
                onAutoPlayPost = onAutoPlayPost,
                onTogglePlayPost = onPlayPost,
                onSeekToMs = onSeekToMs,
                onCycleSpeed = onCycleSpeed,
                onVote = onVote,
                onOpenComments = onOpenComments,
                onEcho = onEcho,
                onRecordPulse = onRecordPulse,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            return
        }

        // Top Live Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    "Search topics, #tags, transcripts, or r/rooms...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TitaniumMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = ElectricCoral)
            },
            trailingIcon = {
                AnimatedVisibility(visible = searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("discover_search_clear")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TitaniumMuted)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianSurface,
                unfocusedContainerColor = ObsidianSurface,
                focusedBorderColor = ElectricCoral.copy(alpha = 0.7f),
                unfocusedBorderColor = ObsidianHairline,
                focusedTextColor = StudioWhite,
                unfocusedTextColor = StudioWhite,
                cursorColor = ElectricCoral
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("discover_search_bar")
        )

        // Filter Chips Row: Trending, Controversial, Anonymous Takes, Following, Short Clips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DiscoverFilter.entries, key = { it.name }, contentType = { "filter_chip" }) { chip ->
                val selected = chip == activeFilter
                val chipAccent = when (chip) {
                    DiscoverFilter.ANONYMOUS -> StudioViolet
                    DiscoverFilter.CONTROVERSIAL -> AmberVinyl
                    DiscoverFilter.SHORT_CLIPS -> SignalMint
                    else -> ElectricCoral
                }
                FilterChip(
                    selected = selected,
                    onClick = {
                        haptics(HapticCue.CAPTION_TOGGLE)
                        onSelectFilter(chip)
                    },
                    modifier = Modifier.testTag("discover_filter_${chip.name.lowercase()}"),
                    label = {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = chip.icon(),
                            contentDescription = null,
                            tint = if (selected) chipAccent else TitaniumMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = ObsidianSurface,
                        labelColor = TitaniumMuted,
                        selectedContainerColor = chipAccent.copy(alpha = 0.2f),
                        selectedLabelColor = StudioWhite
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = ObsidianHairline,
                        selectedBorderColor = chipAccent.copy(alpha = 0.55f)
                    )
                )
            }
        }

        // Scrollable Hybrid Explore + Threads Audio Feed (Paging 3 backed)
        val isRefreshing = pagedPosts.loadState.refresh is LoadState.Loading && pagedPosts.itemCount > 0
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { pagedPosts.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = exploreListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 152.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            if (hiddenControversialCount > 0) {
                item(key = "discover_unfiltered_notice") {
                    UnfilteredFilterNoticeBanner(
                        hiddenCount = hiddenControversialCount,
                        onEnableUnfiltered = onEnableUnfiltered
                    )
                }
            }

            // Live Search Matches: Rooms + Hashtags
            if (trimmedQuery.isNotEmpty()) {
                if (matchingRooms.isNotEmpty()) {
                    item(key = "search_rooms_section") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "MATCHING ROOMS (${matchingRooms.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TitaniumMuted,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(matchingRooms, key = { it.id }, contentType = { "room_chip" }) { room ->
                                    DiscoverRoomResultChip(
                                        room = room,
                                        onClick = { onOpenRoom(room.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (matchingHashtags.isNotEmpty()) {
                    item(key = "search_tags_section") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matchingHashtags, key = { it.first }, contentType = { "hashtag_chip" }) { (tag, count) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ObsidianSurfaceElevated,
                                    border = BorderStroke(1.dp, ElectricCoral.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { onSearchQueryChange("#$tag") }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Tag, null, tint = ElectricCoral, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "$tag · $count",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = StudioWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // X-Style Trending Audio Topics Section
            if (trimmedQuery.isEmpty() && trendingTopics.isNotEmpty()) {
                item(key = "trending_audio_topics") {
                    TrendingAudioTopicsCard(
                        topics = trendingTopics,
                        onSelectTopic = { topic ->
                            when {
                                topic.roomId != null -> onOpenRoom(topic.roomId)
                                !topic.searchToken.isNullOrBlank() -> onSearchQueryChange(topic.searchToken)
                            }
                        }
                    )
                }
            }

            // Section Divider Header before Audio Posts Stream
            item(key = "discover_stream_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (trimmedQuery.isNotEmpty()) {
                            "VOICES MATCHING \"$trimmedQuery\""
                        } else {
                            "${activeFilter.label.uppercase()} VOICES"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumMuted
                    )
                    Text(
                        text = "Tap waveform · Toggle CC/Lyrics",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCoral.copy(alpha = 0.85f)
                    )
                }
            }

            // Scrollable VoicePostCard Feed driven by Paging 3 LoadState: Shimmer skeletons during initial load
            when (val refreshState = pagedPosts.loadState.refresh) {
                is LoadState.Loading -> {
                    items(4, key = { "discover_skeleton_$it" }, contentType = { "discover_skeleton" }) {
                        VoicePostSkeleton()
                    }
                }
                is LoadState.Error -> {
                    item(key = "discover_error") {
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
                                    text = refreshState.error.localizedMessage ?: "Failed to load voices.",
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
                    if (pagedPosts.itemCount == 0) {
                        item(key = "discover_empty") {
                            DiscoverEmptyState(
                                hasSearch = trimmedQuery.isNotEmpty(),
                                filterLabel = activeFilter.label,
                                onClearSearch = { onSearchQueryChange("") },
                                onResetFilter = { onSelectFilter(DiscoverFilter.TRENDING) },
                                onSpeak = onRecordNewVoice
                            )
                        }
                    } else {
                        items(
                            count = pagedPosts.itemCount,
                            key = { index -> pagedPosts.peek(index)?.id ?: index },
                            contentType = { "discover_post" }
                        ) { index ->
                            val rawPost = pagedPosts[index]
                            if (rawPost != null) {
                                val post = postOverrides[rawPost.id] ?: rawPost
                                val isHidden = hiddenPostIds.contains(post.id) || filteredAuthorUids.contains(post.authorUid)
                                val isSafetyBlocked = !safetySettings.showUnfilteredControversial && post.isUnfilteredOrControversial()
                                if (!isHidden && !isSafetyBlocked && post.kindEnum() != PostKind.VOICE_DROP) {
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
                                        onExpandLyrics = { onExpandLyrics(post) },
                                        onRoomClick = onOpenRoom,
                                        onSpeakerClick = onOpenSpeakerProfile,
                                        onReport = { onReportPost(post) },
                                        onMuteSpeaker = { onMuteSpeaker(post.authorUid, post.displaySpeakerName()) },
                                        onBlockSpeaker = { onBlockSpeaker(post.authorUid, post.displaySpeakerName()) },
                                        onHide = { onHidePost(post.id) },
                                        onDelete = { onDeletePost(post.id) },
                                        onHashtagClick = { tag -> onSearchQueryChange("#$tag") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Paging 3 Append Footer
            when (val appendState = pagedPosts.loadState.append) {
                is LoadState.Loading -> {
                    item(key = "discover_append_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = ElectricCoral,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                is LoadState.Error -> {
                    item(key = "discover_append_error") {
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
                else -> Unit
            }
        }
        }
    }
}

@Composable
private fun TrendingAudioTopicsCard(
    topics: List<TrendingAudioTopic>,
    onSelectTopic: (TrendingAudioTopic) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianHairline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trending_audio_topics_section")
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = ElectricCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Trending Audio Topics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioWhite
                    )
                }
                Text(
                    text = "LIVE DEBATES",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCoral
                )
            }

            Spacer(Modifier.height(10.dp))

            topics.forEachIndexed { index, topic ->
                val accent = if (topic.isAnonymousHotspot) StudioViolet else ElectricCoral
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTopic(topic) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("trending_topic_item_${index + 1}"),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "%02d".format(index + 1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp, end = 12.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = topic.debateTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${topic.postCount} ${if (topic.postCount == 1L) "voice" else "voices"} · ${topic.totalReactions} reactions",
                                style = MaterialTheme.typography.labelSmall,
                                color = TitaniumMuted
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = topic.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StudioWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (topic.hotTakePreview.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "“${topic.hotTakePreview}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = TitaniumMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (index < topics.lastIndex) {
                    HorizontalDivider(
                        color = ObsidianHairline.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverRoomResultChip(
    room: CommunityRoom,
    onClick: () -> Unit
) {
    val accent = if (room.anonymousRoom) StudioViolet else ElectricCoral
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick)
            .testTag("discover_room_match_${room.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (room.anonymousRoom) Icons.Filled.Shield else Icons.Filled.Forum,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "r/${room.slug}",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${room.postCount} voices · ${if (room.anonymousRoom) "Anon" else room.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TitaniumMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DiscoverEmptyState(
    hasSearch: Boolean,
    filterLabel: String,
    onClearSearch: () -> Unit,
    onResetFilter: () -> Unit,
    onSpeak: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianHairline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(ElectricCoral.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = ElectricCoral, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hasSearch) "No matching voices found" else "No $filterLabel voices yet",
                style = MaterialTheme.typography.titleMedium,
                color = StudioWhite
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (hasSearch) {
                    "Try searching another topic, spoken word, hashtag, or r/room."
                } else {
                    "Drop an unfiltered voice take with live word-synced lyrics to kick off the conversation."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TitaniumMuted
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasSearch) {
                    Button(
                        onClick = onClearSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Clear Search", color = StudioWhite)
                    }
                } else if (filterLabel != DiscoverFilter.TRENDING.label) {
                    Button(
                        onClick = onResetFilter,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Show Trending", color = StudioWhite)
                    }
                }
                Button(
                    onClick = onSpeak,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Speak Now")
                }
            }
        }
    }
}
