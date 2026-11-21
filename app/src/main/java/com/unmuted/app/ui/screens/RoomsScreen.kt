package com.unmuted.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.AliasGenerator
import com.unmuted.app.data.CommunityRoom
import com.unmuted.app.data.DebateStance
import com.unmuted.app.data.JoinedRoom
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.ui.components.GeometricBurnerAvatar
import com.unmuted.app.ui.components.UnfilteredFilterNoticeBanner
import com.unmuted.app.ui.components.VoicePostCard
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

private enum class RoomFilter(val label: String) {
    ALL("All Rooms"),
    ANONYMOUS("Anonymous"),
    JOINED("Joined")
}

private enum class RoomPostFilter(val label: String) {
    ALL("All Voices"),
    HOT_TAKES("Hot Takes"),
    CLAPBACKS("Clapbacks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    pagedRooms: LazyPagingItems<CommunityRoom>,
    roomsWindow: List<CommunityRoom>,
    joinedRooms: Map<String, JoinedRoom>,
    recentPosts: List<VoicePost>,
    postOverrides: Map<String, VoicePost>,
    reactions: Map<String, UserPostReaction>,
    hiddenPostIds: Set<String>,
    filteredAuthorUids: Set<String>,
    safetySettings: SafetySettings,
    playback: PlaybackState,
    currentUid: String,
    selectedRoomId: String?,
    onSelectRoomId: (String?) -> Unit,
    onToggleJoinRoom: (CommunityRoom) -> Unit,
    onCreateRoom: (String, String, String, String, Boolean, String, String, () -> Unit) -> Unit,
    onEnableUnfiltered: () -> Unit,
    onReportRoom: (CommunityRoom) -> Unit,
    onSpeakInRoom: (CommunityRoom) -> Unit,
    onSpeakHotTakeInRoom: (CommunityRoom) -> Unit,
    onPlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onOpenDebateSide: (VoicePost, DebateStance) -> Unit,
    onQuoteClapback: (VoicePost) -> Unit,
    onEcho: (VoicePost) -> Unit,
    onBookmark: (VoicePost) -> Unit,
    onReportPost: (VoicePost) -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onHidePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onExpandLyrics: (VoicePost) -> Unit,
    onOpenSpeakerProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val loadedPagedRooms = pagedRooms.itemSnapshotList.items
    val allKnownRooms = remember(loadedPagedRooms, roomsWindow) {
        (loadedPagedRooms + roomsWindow).distinctBy { it.id }
    }
    val activeRoom = remember(allKnownRooms, selectedRoomId) {
        allKnownRooms.firstOrNull { it.id == selectedRoomId }
    }

    val effectivePosts = remember(recentPosts, postOverrides, hiddenPostIds, filteredAuthorUids) {
        recentPosts
            .map { postOverrides[it.id] ?: it }
            .filterNot { hiddenPostIds.contains(it.id) || filteredAuthorUids.contains(it.authorUid) }
    }

    if (activeRoom != null) {
        BackHandler { onSelectRoomId(null) }
        RoomDetailView(
            room = activeRoom,
            joined = joinedRooms.containsKey(activeRoom.id),
            posts = effectivePosts.filter { it.roomId == activeRoom.id },
            reactions = reactions,
            safetySettings = safetySettings,
            playback = playback,
            currentUid = currentUid,
            onBack = { onSelectRoomId(null) },
            onToggleJoin = { onToggleJoinRoom(activeRoom) },
            onReportRoom = { onReportRoom(activeRoom) },
            onSpeak = { onSpeakInRoom(activeRoom) },
            onSpeakHotTake = { onSpeakHotTakeInRoom(activeRoom) },
            onPlayPost = onPlayPost,
            onSeekToMs = onSeekToMs,
            onCycleSpeed = onCycleSpeed,
            onVote = onVote,
            onOpenComments = onOpenComments,
            onOpenDebateSide = onOpenDebateSide,
            onQuoteClapback = onQuoteClapback,
            onEcho = onEcho,
            onBookmark = onBookmark,
            onReportPost = onReportPost,
            onMuteSpeaker = onMuteSpeaker,
            onBlockSpeaker = onBlockSpeaker,
            onHidePost = onHidePost,
            onDeletePost = onDeletePost,
            onExpandLyrics = onExpandLyrics,
            onOpenSpeakerProfile = onOpenSpeakerProfile,
            onSelectRoomId = onSelectRoomId,
            modifier = modifier
        )
        return
    }

    var filter by rememberSaveable { mutableStateOf(RoomFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }

    val hiddenUnfilteredRoomsCount = remember(loadedPagedRooms, safetySettings) {
        if (safetySettings.showUnfilteredControversial) 0
        else loadedPagedRooms.count { it.isUnfilteredControversialRoom() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Rooms", style = MaterialTheme.typography.headlineMedium, color = StudioWhite)
                    Text("Dual-side debates & anonymous voice spaces", style = MaterialTheme.typography.bodySmall, color = TitaniumMuted)
                }
                Button(
                    onClick = { showCreateSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("create_room_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Room")
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search rooms, topics, or anonymous debates...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TitaniumMuted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("rooms_search_input")
            )

            // Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoomFilter.entries.forEach { tab ->
                    FilterChip(
                        selected = filter == tab,
                        onClick = { filter = tab },
                        label = { Text(tab.label) },
                        leadingIcon = if (tab == RoomFilter.ANONYMOUS) {
                            { Icon(Icons.Filled.Shield, null, tint = StudioViolet, modifier = Modifier.size(15.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (tab == RoomFilter.ANONYMOUS) StudioViolet.copy(0.24f) else ElectricCoral.copy(0.22f),
                            selectedLabelColor = StudioWhite
                        )
                    )
                }
            }

            when (val refreshState = pagedRooms.loadState.refresh) {
                is LoadState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricCoral)
                    }
                }
                is LoadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = refreshState.error.localizedMessage ?: "Failed to load rooms.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { pagedRooms.retry() }) {
                            Text("Retry")
                        }
                    }
                }
                is LoadState.NotLoading -> {
                    val q = searchQuery.trim().lowercase()
                    val visibleCount = loadedPagedRooms.count { room ->
                        roomMatchesFilters(room, filter, q, joinedRooms, safetySettings)
                    }

                    if (visibleCount == 0 && hiddenUnfilteredRoomsCount == 0) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Forum, null, tint = StudioViolet, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No rooms match yet", style = MaterialTheme.typography.titleLarge, color = StudioWhite)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Start a Hot Take debate room or an Anonymous Unfiltered room with burner aliases and voice masking.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TitaniumMuted
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { showCreateSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioViolet),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Create First Room")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 148.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (hiddenUnfilteredRoomsCount > 0) {
                                item(key = "rooms_unfiltered_notice") {
                                    UnfilteredFilterNoticeBanner(
                                        hiddenCount = hiddenUnfilteredRoomsCount,
                                        onEnableUnfiltered = onEnableUnfiltered
                                    )
                                }
                            }

                            items(
                                count = pagedRooms.itemCount,
                                key = { idx -> pagedRooms.peek(idx)?.id ?: "room_placeholder_$idx" }
                            ) { idx ->
                                val room = pagedRooms[idx] ?: return@items
                                if (!roomMatchesFilters(room, filter, q, joinedRooms, safetySettings)) return@items

                                val isJoined = joinedRooms.containsKey(room.id)
                                val palette = ProfileAccentPalette.fromKey(room.accentKey)
                                val accent = if (room.anonymousRoom) StudioViolet else palette.primary
                                val hotTakeCount = remember(effectivePosts, room.id) {
                                    effectivePosts.count { it.roomId == room.id && it.hotTakeMode }
                                }

                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = ObsidianSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (room.anonymousRoom) StudioViolet.copy(alpha = 0.4f) else ObsidianHairline
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectRoomId(room.id) }
                                        .testTag("room_card_${room.id}")
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(13.dp))
                                                        .background(accent.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (room.anonymousRoom) Icons.Filled.Shield else Icons.Filled.Forum,
                                                        contentDescription = null,
                                                        tint = accent
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "r/${room.slug}",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = StudioWhite,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (room.anonymousRoom) {
                                                            Surface(
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = StudioViolet.copy(alpha = 0.24f)
                                                            ) {
                                                                Text(
                                                                    text = "ANON MASK",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = StudioViolet,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = buildString {
                                                            append("${room.category} · ${room.memberCount} listeners · ${room.postCount} voices")
                                                            if (hotTakeCount > 0) append(" · $hotTakeCount hot takes")
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TitaniumMuted
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { onToggleJoinRoom(room) },
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(if (isJoined) "Joined" else "Join")
                                            }
                                        }

                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = room.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = StudioWhite
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = room.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TitaniumMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Paging 3 Append Loader / Retry
                            when (val appendState = pagedRooms.loadState.append) {
                                is LoadState.Loading -> {
                                    item(key = "rooms_append_loading") {
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
                                    item(key = "rooms_append_error") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            OutlinedButton(onClick = { pagedRooms.retry() }) {
                                                Text(appendState.error.localizedMessage ?: "Retry loading more rooms")
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
        }
    }

    if (showCreateSheet) {
        CreateRoomBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, slug, desc, cat, anon, accent, rules ->
                onCreateRoom(name, slug, desc, cat, anon, accent, rules) {
                    showCreateSheet = false
                }
            }
        )
    }
}

private fun roomMatchesFilters(
    room: CommunityRoom,
    filter: RoomFilter,
    queryLower: String,
    joinedRooms: Map<String, JoinedRoom>,
    safetySettings: SafetySettings
): Boolean {
    if (!safetySettings.showUnfilteredControversial && room.isUnfilteredControversialRoom()) {
        return false
    }
    val matchesFilter = when (filter) {
        RoomFilter.ALL -> true
        RoomFilter.ANONYMOUS -> room.anonymousRoom
        RoomFilter.JOINED -> joinedRooms.containsKey(room.id)
    }
    val matchesSearch = queryLower.isEmpty() ||
        room.name.lowercase().contains(queryLower) ||
        room.slug.lowercase().contains(queryLower) ||
        room.description.lowercase().contains(queryLower) ||
        room.category.lowercase().contains(queryLower)
    return matchesFilter && matchesSearch
}

@Composable
private fun RoomDetailView(
    room: CommunityRoom,
    joined: Boolean,
    posts: List<VoicePost>,
    reactions: Map<String, UserPostReaction>,
    safetySettings: SafetySettings,
    playback: PlaybackState,
    currentUid: String,
    onBack: () -> Unit,
    onToggleJoin: () -> Unit,
    onReportRoom: () -> Unit,
    onSpeak: () -> Unit,
    onSpeakHotTake: () -> Unit,
    onPlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onOpenDebateSide: (VoicePost, DebateStance) -> Unit,
    onQuoteClapback: (VoicePost) -> Unit,
    onEcho: (VoicePost) -> Unit,
    onBookmark: (VoicePost) -> Unit,
    onReportPost: (VoicePost) -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onHidePost: (String) -> Unit,
    onDeletePost: (String) -> Unit,
    onExpandLyrics: (VoicePost) -> Unit,
    onOpenSpeakerProfile: (String) -> Unit,
    onSelectRoomId: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val myAlias = remember(currentUid, room.id) { AliasGenerator.generateForRoom(currentUid, room.id) }
    val accent = if (room.anonymousRoom) StudioViolet else ProfileAccentPalette.fromKey(room.accentKey).primary
    var postFilter by rememberSaveable(room.id) { mutableStateOf(RoomPostFilter.ALL) }

    val hotTakeCount = remember(posts) { posts.count { it.hotTakeMode } }
    val clapbackCount = remember(posts) { posts.count { it.hasQuoteClip() } }
    val displayedPosts = remember(posts, postFilter) {
        when (postFilter) {
            RoomPostFilter.ALL -> posts
            RoomPostFilter.HOT_TAKES -> posts.filter { it.hotTakeMode }
            RoomPostFilter.CLAPBACKS -> posts.filter { it.hasQuoteClip() }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(ObsidianBackground)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), ObsidianBackground))
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("room_detail_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to rooms", tint = StudioWhite)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onReportRoom,
                                modifier = Modifier.testTag("report_room_btn")
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = "Report room", tint = TitaniumMuted)
                            }
                            Button(
                                onClick = onToggleJoin,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (joined) ObsidianSurfaceElevated else accent
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(if (joined) "Joined" else "Join Room")
                            }
                        }
                    }

                    Text("r/${room.slug}", style = MaterialTheme.typography.headlineMedium, color = StudioWhite)
                    Text(room.name, style = MaterialTheme.typography.titleMedium, color = accent)
                    Spacer(Modifier.height(6.dp))
                    Text(room.description, style = MaterialTheme.typography.bodyMedium, color = TitaniumMuted)

                    if (room.anonymousRoom) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = StudioViolet.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, StudioViolet.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GeometricBurnerAvatar(
                                    seed = myAlias,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, StudioViolet, CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Burner Identity: $myAlias",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioWhite
                                    )
                                    Text(
                                        text = "Geometric avatar + on-device voice masking (Deep, Radio, Glitch, Shift) active here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TitaniumMuted
                                    )
                                }
                            }
                        }
                    }

                    // Room Feed Filter Chips: All Voices | Hot Takes | Clapbacks
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RoomPostFilter.entries) { tab ->
                            val selected = postFilter == tab
                            val chipTint = when (tab) {
                                RoomPostFilter.HOT_TAKES -> AmberVinyl
                                RoomPostFilter.CLAPBACKS -> SignalCyan
                                RoomPostFilter.ALL -> accent
                            }
                            val badgeCount = when (tab) {
                                RoomPostFilter.ALL -> posts.size
                                RoomPostFilter.HOT_TAKES -> hotTakeCount
                                RoomPostFilter.CLAPBACKS -> clapbackCount
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { postFilter = tab },
                                modifier = Modifier.testTag("room_post_filter_${tab.name.lowercase()}"),
                                label = { Text("${tab.label} ($badgeCount)") },
                                leadingIcon = when (tab) {
                                    RoomPostFilter.HOT_TAKES -> {
                                        { Icon(Icons.Filled.LocalFireDepartment, null, tint = AmberVinyl, modifier = Modifier.size(15.dp)) }
                                    }
                                    RoomPostFilter.CLAPBACKS -> {
                                        { Icon(Icons.Filled.FormatQuote, null, tint = SignalCyan, modifier = Modifier.size(15.dp)) }
                                    }
                                    RoomPostFilter.ALL -> null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipTint.copy(alpha = 0.22f),
                                    selectedLabelColor = StudioWhite
                                )
                            )
                        }
                    }
                }
            }

            if (displayedPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (postFilter) {
                                    RoomPostFilter.HOT_TAKES -> "No Hot Take Debates in r/${room.slug} yet"
                                    RoomPostFilter.CLAPBACKS -> "No Voice Clapbacks in r/${room.slug} yet"
                                    RoomPostFilter.ALL -> "No voices in r/${room.slug} yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = StudioWhite
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (postFilter == RoomPostFilter.HOT_TAKES) {
                                    "Tap Hot Take below to launch an Agree vs Disagree audio debate."
                                } else {
                                    "Tap Speak or Hot Take below to kick off the conversation."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TitaniumMuted
                            )
                        }
                    }
                }
            } else {
                items(displayedPosts, key = { it.id }) { post ->
                    VoicePostCard(
                        post = post,
                        playback = playback,
                        reaction = reactions[post.id],
                        currentUid = currentUid,
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
                        onRoomClick = { onSelectRoomId(it) },
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

        // Dual Action FAB Row: Launch Hot Take Debate + Speak in Room
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = onSpeakHotTake,
                containerColor = AmberVinyl,
                contentColor = ObsidianBackground,
                modifier = Modifier.testTag("speak_hottake_room_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Launch Hot Take Debate")
                    Spacer(Modifier.width(6.dp))
                    Text("Hot Take", fontWeight = FontWeight.Bold)
                }
            }

            FloatingActionButton(
                onClick = onSpeak,
                containerColor = accent,
                contentColor = StudioWhite,
                modifier = Modifier.testTag("speak_in_room_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Speak in room")
                    Spacer(Modifier.width(8.dp))
                    Text(if (room.anonymousRoom) "Speak Anon" else "Speak", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRoomBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, Boolean, String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Debate") }
    var anonymousRoom by remember { mutableStateOf(true) }
    var guidelines by remember { mutableStateOf("Challenge ideas openly. Zero tolerance for illegal content or threats.") }
    var accentKey by remember { mutableStateOf("VIOLET") }

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
            Text("Create a Room", style = MaterialTheme.typography.headlineSmall, color = StudioWhite)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (anonymousRoom) StudioViolet.copy(alpha = 0.18f) else ObsidianSurfaceElevated
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Shield, null, tint = if (anonymousRoom) StudioViolet else TitaniumMuted)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Anonymous Unfiltered Room", style = MaterialTheme.typography.titleSmall, color = StudioWhite)
                            Text(
                                "Burner alias, geometric avatar & on-device voice masking enforced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TitaniumMuted
                            )
                        }
                    }
                    Switch(
                        checked = anonymousRoom,
                        onCheckedChange = { anonymousRoom = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = StudioViolet, checkedThumbColor = StudioWhite),
                        modifier = Modifier.testTag("create_room_anon_switch")
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Room Title (e.g. Unpopular Opinions)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("room_name_input"),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = slug,
                onValueChange = { slug = it.lowercase().replace(Regex("[^a-z0-9_]"), "").take(32) },
                label = { Text("Room Slug (r/...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("room_slug_input"),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(300) },
                label = { Text("What do people speak about here?") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("room_desc_input"),
                shape = RoundedCornerShape(14.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Debate", "Culture", "Tech", "Confessions").forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }

            OutlinedTextField(
                value = guidelines,
                onValueChange = { guidelines = it.take(400) },
                label = { Text("Room Guidelines (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    onCreate(
                        name,
                        slug.ifBlank { name },
                        description,
                        category,
                        anonymousRoom,
                        if (anonymousRoom) "VIOLET" else accentKey,
                        guidelines
                    )
                },
                enabled = name.trim().length >= 2 && description.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (anonymousRoom) StudioViolet else ElectricCoral
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_create_room_btn")
            ) {
                Text(if (anonymousRoom) "Launch Anonymous Room" else "Launch Room", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
