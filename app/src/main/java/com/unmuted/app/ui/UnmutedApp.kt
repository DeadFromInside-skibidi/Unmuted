package com.unmuted.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.unmuted.app.core.signOutUser
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.SafetyReportTarget
import com.unmuted.app.data.VoicePost
import com.unmuted.app.ui.components.CommentsBottomSheet
import com.unmuted.app.ui.components.FloatingMiniPlayerBar
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.SafetyControlsBottomSheet
import com.unmuted.app.ui.components.SafetyReportBottomSheet
import com.unmuted.app.ui.components.rememberStudioHaptics
import com.unmuted.app.ui.screens.ComposerStudioSheet
import com.unmuted.app.ui.screens.DiscoverScreen
import com.unmuted.app.ui.screens.DiscoverSubTab
import com.unmuted.app.ui.screens.FeedScreen
import com.unmuted.app.ui.screens.FullLyricsFocusDialog
import com.unmuted.app.ui.screens.InboxScreen
import com.unmuted.app.ui.screens.ProfileScreen
import com.unmuted.app.ui.screens.RoomsScreen
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.theme.UnmutedTheme
import com.unmuted.app.ui.viewmodel.AudioPlaybackViewModel
import com.unmuted.app.ui.viewmodel.DataUiState
import com.unmuted.app.ui.viewmodel.DiscoverViewModel
import com.unmuted.app.ui.viewmodel.FeedViewModel
import com.unmuted.app.ui.viewmodel.InboxViewModel
import com.unmuted.app.ui.viewmodel.ProfileViewModel
import com.unmuted.app.ui.viewmodel.RoomsViewModel

enum class UnmutedTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FEED("Feed", Icons.Filled.Home, Icons.Outlined.Home),
    DISCOVER("Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    ROOMS("Rooms", Icons.Filled.Forum, Icons.Outlined.Forum),
    INBOX("Inbox", Icons.Filled.Mail, Icons.Outlined.MailOutline),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.PersonOutline)
}

@Composable
fun UnmutedAppShell(
    audioPlaybackViewModel: AudioPlaybackViewModel,
    feedViewModel: FeedViewModel,
    discoverViewModel: DiscoverViewModel,
    roomsViewModel: RoomsViewModel,
    inboxViewModel: InboxViewModel,
    profileViewModel: ProfileViewModel,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val haptics = rememberStudioHaptics()

    // Paging 3 streams (incremental Firestore loading across Feed, Discover, and Rooms)
    val pagedPosts = feedViewModel.pagedPosts.collectAsLazyPagingItems()
    val pagedDiscoverPosts = discoverViewModel.pagedDiscoverPosts.collectAsLazyPagingItems()
    val pagedRooms = roomsViewModel.pagedRooms.collectAsLazyPagingItems()

    // Feature ViewModel state flows
    val myProfileState by profileViewModel.myProfileState.collectAsStateWithLifecycle()
    val allProfilesState by profileViewModel.allProfilesState.collectAsStateWithLifecycle()
    val followingUids by profileViewModel.followingUidsState.collectAsStateWithLifecycle()
    val profileReplies by profileViewModel.profileReplies.collectAsStateWithLifecycle()
    val profileRepliesLoading by profileViewModel.profileRepliesLoading.collectAsStateWithLifecycle()

    val recentPostsState by feedViewModel.recentPostsState.collectAsStateWithLifecycle()
    val postOverrides by feedViewModel.postOverrides.collectAsStateWithLifecycle()
    val reactions by feedViewModel.reactionsState.collectAsStateWithLifecycle()
    val hiddenPostIds by feedViewModel.hiddenPostIds.collectAsStateWithLifecycle()
    val safetySettings by feedViewModel.safetySettings.collectAsStateWithLifecycle()
    val safetyRelations by feedViewModel.safetyRelations.collectAsStateWithLifecycle()
    val blockedUids by feedViewModel.blockedUids.collectAsStateWithLifecycle()
    val filteredAuthorUids by feedViewModel.filteredAuthorUids.collectAsStateWithLifecycle()
    val activeCommentsPost by feedViewModel.activeCommentsPost.collectAsStateWithLifecycle()
    val activeCommentsInitialStance by feedViewModel.activeCommentsInitialStance.collectAsStateWithLifecycle()
    val commentsState by feedViewModel.commentsState.collectAsStateWithLifecycle()
    val composerDraft by feedViewModel.composerState.collectAsStateWithLifecycle()
    val snackbarMsg by feedViewModel.snackbarMessage.collectAsStateWithLifecycle()

    val discoverSearchQuery by discoverViewModel.searchQuery.collectAsStateWithLifecycle()
    val discoverActiveFilter by discoverViewModel.activeFilter.collectAsStateWithLifecycle()
    val discoverTrendingTopics by discoverViewModel.trendingTopics.collectAsStateWithLifecycle()

    val roomsState by roomsViewModel.roomsState.collectAsStateWithLifecycle()
    val joinedRooms by roomsViewModel.joinedRoomsState.collectAsStateWithLifecycle()

    val conversationsState by inboxViewModel.conversationsState.collectAsStateWithLifecycle()
    val activeConversation by inboxViewModel.activeConversation.collectAsStateWithLifecycle()
    val messagesState by inboxViewModel.messagesState.collectAsStateWithLifecycle()

    val playback by audioPlaybackViewModel.playbackState.collectAsStateWithLifecycle()
    val recordingState by audioPlaybackViewModel.recordingState.collectAsStateWithLifecycle()

    val currentUid = feedViewModel.currentUid
    val myProfile = (myProfileState as? DataUiState.Success)?.data ?: profileViewModel.currentProfileOrDefault()
    val allProfiles = (allProfilesState as? DataUiState.Success)?.data.orEmpty()
    val recentPosts = remember(recentPostsState, postOverrides) {
        (recentPostsState as? DataUiState.Success)?.data.orEmpty().map { postOverrides[it.id] ?: it }
    }
    val roomsWindow = (roomsState as? DataUiState.Success)?.data.orEmpty()

    var currentTab by rememberSaveable { mutableStateOf(UnmutedTab.FEED) }
    var discoverSubTab by rememberSaveable { mutableStateOf(DiscoverSubTab.EXPLORE) }
    var selectedRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingOtherUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var globalLyricsPost by remember { mutableStateOf<VoicePost?>(null) }
    var pendingReportTarget by remember { mutableStateOf<SafetyReportTarget?>(null) }
    var showSafetyControlsSheet by remember { mutableStateOf(false) }
    var askedNotifPermission by rememberSaveable { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Media3 playback continues regardless of notification permission */ }

    LaunchedEffect(playback.playing) {
        if (playback.playing && !askedNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotifPermission = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Load authored replies whenever viewing own or another speaker's Profile
    val activeProfileUid = when {
        viewingOtherUserId != null -> viewingOtherUserId
        currentTab == UnmutedTab.PROFILE -> currentUid
        else -> null
    }
    LaunchedEffect(activeProfileUid, recentPosts.size) {
        if (!activeProfileUid.isNullOrBlank()) {
            profileViewModel.loadRepliesForProfile(activeProfileUid, recentPosts)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            feedViewModel.clearSnackbar()
        }
    }

    val reportPostAction: (VoicePost) -> Unit = { post ->
        pendingReportTarget = SafetyReportTarget(
            targetType = "POST",
            targetId = post.id,
            targetAuthorUid = post.authorUid,
            targetDisplayName = post.displaySpeakerName()
        )
    }

    val enableUnfilteredAction: () -> Unit = {
        feedViewModel.updateSafetySettings(safetySettings.copy(showUnfilteredControversial = true))
    }

    val isInPulsesMode = currentTab == UnmutedTab.DISCOVER && discoverSubTab == DiscoverSubTab.PULSES && viewingOtherUserId == null
    val showMiniPlayer = !playback.activeMediaId.isNullOrBlank() &&
        playback.nowPlaying != null &&
        !isInPulsesMode &&
        !showComposer

    val userAccent = ProfileAccentPalette.fromKey(myProfile.accentKey)

    UnmutedTheme(accentPalette = userAccent) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBackground)) {
            val isExpanded = maxWidth >= 680.dp

            val contentBody: @Composable (Modifier) -> Unit = { mod ->
                val otherProfile = viewingOtherUserId?.let { uid ->
                    allProfiles.firstOrNull { it.userId == uid }
                }

                if (otherProfile != null && otherProfile.userId != currentUid) {
                    ProfileScreen(
                        profile = otherProfile,
                        isOwnProfile = false,
                        isFollowing = followingUids.contains(otherProfile.userId),
                        allPosts = recentPosts,
                        reactions = reactions,
                        rooms = roomsWindow,
                        joinedRooms = joinedRooms,
                        safetySettings = safetySettings,
                        profileReplies = profileReplies,
                        profileRepliesLoading = profileRepliesLoading,
                        playback = playback,
                        recordingState = recordingState,
                        audioRecorder = audioPlaybackViewModel.audioRecorder,
                        currentUid = currentUid,
                        onBack = { viewingOtherUserId = null },
                        onToggleFollow = { profileViewModel.toggleFollow(otherProfile.userId) },
                        onMessageUser = {
                            viewingOtherUserId = null
                            currentTab = UnmutedTab.INBOX
                            inboxViewModel.startConversationWith(myProfile, otherProfile)
                        },
                        onOpenSafetyControls = { showSafetyControlsSheet = true },
                        onMuteSpeaker = { uid, name -> feedViewModel.muteSpeaker(uid, name) },
                        onBlockSpeaker = { uid, name -> feedViewModel.blockSpeaker(uid, name) },
                        onReportProfile = { targetProfile ->
                            pendingReportTarget = SafetyReportTarget(
                                targetType = "USER",
                                targetId = targetProfile.userId,
                                targetAuthorUid = targetProfile.userId,
                                targetDisplayName = targetProfile.displayName
                            )
                        },
                        onSignOut = {},
                        onPlayVoiceBio = { audioPlaybackViewModel.togglePlayVoiceBio(otherProfile) },
                        onPreloadVoiceBio = { audioPlaybackViewModel.preloadMediaId(it) },
                        onPlayVoiceReply = { comment ->
                            comment.audioMediaId?.let { mediaId ->
                                audioPlaybackViewModel.togglePlayMediaId(
                                    mediaId = mediaId,
                                    disguise = comment.disguiseEnum(),
                                    speakerName = comment.displaySpeakerName(),
                                    title = comment.transcript?.take(56)?.ifBlank { "Voice Reply" } ?: "Voice Reply"
                                )
                            }
                        },
                        onPlayPost = { post -> audioPlaybackViewModel.togglePlayPost(post) },
                        onSeekToMs = { ms -> audioPlaybackViewModel.seekTo(ms) },
                        onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                        onVote = { post, vote -> feedViewModel.votePost(post, vote) },
                        onOpenComments = { post -> feedViewModel.openComments(post) },
                        onOpenDebateSide = { post, stance -> feedViewModel.openComments(post, stance) },
                        onQuoteClapback = { post ->
                            feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, quotedPost = post)
                            showComposer = true
                        },
                        onEcho = { post -> feedViewModel.echoPost(post) },
                        onBookmark = { post -> feedViewModel.bookmarkPost(post) },
                        onReportPost = reportPostAction,
                        onHidePost = { id -> feedViewModel.hidePostLocally(id) },
                        onDeletePost = { id -> feedViewModel.deletePost(id) },
                        onExpandLyrics = { post -> globalLyricsPost = post },
                        onOpenRoom = { roomId ->
                            viewingOtherUserId = null
                            selectedRoomId = roomId
                            currentTab = UnmutedTab.ROOMS
                        },
                        onOpenSpeakerProfile = { uid ->
                            if (uid == currentUid) {
                                viewingOtherUserId = null
                                currentTab = UnmutedTab.PROFILE
                            } else {
                                viewingOtherUserId = uid
                            }
                        },
                        onSaveCustomization = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                        modifier = mod
                    )
                } else {
                    when (currentTab) {
                        UnmutedTab.FEED -> FeedScreen(
                            pagedPosts = pagedPosts,
                            recentPosts = recentPosts,
                            postOverrides = postOverrides,
                            myProfile = myProfile,
                            reactions = reactions,
                            followingUids = followingUids,
                            hiddenPostIds = hiddenPostIds,
                            filteredAuthorUids = filteredAuthorUids,
                            safetySettings = safetySettings,
                            playback = playback,
                            currentUid = currentUid,
                            onPlayPost = { post -> audioPlaybackViewModel.togglePlayPost(post, autoPlay = false) },
                            onSeekToMs = { ms -> audioPlaybackViewModel.seekTo(ms) },
                            onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                            onVote = { post, vote -> feedViewModel.votePost(post, vote) },
                            onOpenComments = { post -> feedViewModel.openComments(post) },
                            onOpenDebateSide = { post, stance -> feedViewModel.openComments(post, stance) },
                            onQuoteClapback = { post ->
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, quotedPost = post)
                                showComposer = true
                            },
                            onEcho = { post -> feedViewModel.echoPost(post) },
                            onBookmark = { post -> feedViewModel.bookmarkPost(post) },
                            onRecordVoiceDrop = {
                                feedViewModel.prepareComposer(kind = PostKind.VOICE_DROP)
                                showComposer = true
                            },
                            onRecordNewVoice = {
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE)
                                showComposer = true
                            },
                            onOpenProfile = { currentTab = UnmutedTab.PROFILE },
                            onOpenSafetyControls = { showSafetyControlsSheet = true },
                            onEnableUnfiltered = enableUnfilteredAction,
                            onOpenSpeakerProfile = { uid ->
                                if (uid == currentUid) currentTab = UnmutedTab.PROFILE
                                else viewingOtherUserId = uid
                            },
                            onOpenRoom = { roomId ->
                                selectedRoomId = roomId
                                currentTab = UnmutedTab.ROOMS
                            },
                            onReportPost = reportPostAction,
                            onMuteSpeaker = { uid, name -> feedViewModel.muteSpeaker(uid, name) },
                            onBlockSpeaker = { uid, name -> feedViewModel.blockSpeaker(uid, name) },
                            onHidePost = { id -> feedViewModel.hidePostLocally(id) },
                            onDeletePost = { id -> feedViewModel.deletePost(id) },
                            onPreloadPosts = { posts -> audioPlaybackViewModel.preloadPostsAudio(posts) },
                            modifier = mod
                        )

                        UnmutedTab.DISCOVER -> DiscoverScreen(
                            pagedPosts = pagedDiscoverPosts,
                            recentPostsState = recentPostsState,
                            postOverrides = postOverrides,
                            roomsState = roomsState,
                            myProfile = myProfile,
                            reactions = reactions,
                            followingUids = followingUids,
                            hiddenPostIds = hiddenPostIds,
                            filteredAuthorUids = filteredAuthorUids,
                            safetySettings = safetySettings,
                            searchQuery = discoverSearchQuery,
                            activeFilter = discoverActiveFilter,
                            playback = playback,
                            currentUid = currentUid,
                            subTab = discoverSubTab,
                            trendingTopics = discoverTrendingTopics,
                            onSearchQueryChange = { discoverViewModel.updateSearchQuery(it) },
                            onSelectFilter = { discoverViewModel.selectFilter(it) },
                            onSubTabChanged = { discoverSubTab = it },
                            onEnableUnfiltered = enableUnfilteredAction,
                            onAutoPlayPost = { post -> audioPlaybackViewModel.togglePlayPost(post, autoPlay = true) },
                            onPlayPost = { post -> audioPlaybackViewModel.togglePlayPost(post, autoPlay = false) },
                            onSeekToMs = { ms -> audioPlaybackViewModel.seekTo(ms) },
                            onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                            onVote = { post, vote -> feedViewModel.votePost(post, vote) },
                            onOpenComments = { post -> feedViewModel.openComments(post) },
                            onOpenDebateSide = { post, stance -> feedViewModel.openComments(post, stance) },
                            onQuoteClapback = { post ->
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, quotedPost = post)
                                showComposer = true
                            },
                            onEcho = { post -> feedViewModel.echoPost(post) },
                            onBookmark = { post -> feedViewModel.bookmarkPost(post) },
                            onExpandLyrics = { post -> globalLyricsPost = post },
                            onOpenRoom = { roomId ->
                                selectedRoomId = roomId
                                currentTab = UnmutedTab.ROOMS
                            },
                            onOpenSpeakerProfile = { uid ->
                                if (uid == currentUid) currentTab = UnmutedTab.PROFILE
                                else viewingOtherUserId = uid
                            },
                            onRecordNewVoice = {
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE)
                                showComposer = true
                            },
                            onRecordPulse = {
                                feedViewModel.prepareComposer(kind = PostKind.PULSE_SHORT)
                                showComposer = true
                            },
                            onReportPost = reportPostAction,
                            onMuteSpeaker = { uid, name -> feedViewModel.muteSpeaker(uid, name) },
                            onBlockSpeaker = { uid, name -> feedViewModel.blockSpeaker(uid, name) },
                            onHidePost = { id -> feedViewModel.hidePostLocally(id) },
                            onDeletePost = { id -> feedViewModel.deletePost(id) },
                            onPreloadPosts = { posts -> audioPlaybackViewModel.preloadPostsAudio(posts) },
                            modifier = mod
                        )

                        UnmutedTab.ROOMS -> RoomsScreen(
                            pagedRooms = pagedRooms,
                            roomsWindow = roomsWindow,
                            joinedRooms = joinedRooms,
                            recentPosts = recentPosts,
                            postOverrides = postOverrides,
                            reactions = reactions,
                            hiddenPostIds = hiddenPostIds,
                            filteredAuthorUids = filteredAuthorUids,
                            safetySettings = safetySettings,
                            playback = playback,
                            currentUid = currentUid,
                            selectedRoomId = selectedRoomId,
                            onSelectRoomId = { selectedRoomId = it },
                            onToggleJoinRoom = { room -> roomsViewModel.toggleJoinRoom(room) },
                            onCreateRoom = { name, slug, desc, cat, anon, acc, rules, done ->
                                roomsViewModel.createRoom(name, slug, desc, cat, anon, acc, rules) {
                                    pagedRooms.refresh()
                                    done()
                                }
                            },
                            onEnableUnfiltered = enableUnfilteredAction,
                            onReportRoom = { room ->
                                pendingReportTarget = SafetyReportTarget(
                                    targetType = "ROOM",
                                    targetId = room.id,
                                    targetAuthorUid = room.creatorUid,
                                    targetDisplayName = "r/${room.slug}"
                                )
                            },
                            onSpeakInRoom = { room ->
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, room = room)
                                showComposer = true
                            },
                            onSpeakHotTakeInRoom = { room ->
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, room = room, hotTake = true)
                                showComposer = true
                            },
                            onPlayPost = { post -> audioPlaybackViewModel.togglePlayPost(post) },
                            onSeekToMs = { ms -> audioPlaybackViewModel.seekTo(ms) },
                            onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                            onVote = { post, vote -> feedViewModel.votePost(post, vote) },
                            onOpenComments = { post -> feedViewModel.openComments(post) },
                            onOpenDebateSide = { post, stance -> feedViewModel.openComments(post, stance) },
                            onQuoteClapback = { post ->
                                val parentRoom = roomsWindow.firstOrNull { it.id == post.roomId }
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, room = parentRoom, quotedPost = post)
                                showComposer = true
                            },
                            onEcho = { post -> feedViewModel.echoPost(post) },
                            onBookmark = { post -> feedViewModel.bookmarkPost(post) },
                            onReportPost = reportPostAction,
                            onMuteSpeaker = { uid, name -> feedViewModel.muteSpeaker(uid, name) },
                            onBlockSpeaker = { uid, name -> feedViewModel.blockSpeaker(uid, name) },
                            onHidePost = { id -> feedViewModel.hidePostLocally(id) },
                            onDeletePost = { id -> feedViewModel.deletePost(id) },
                            onExpandLyrics = { post -> globalLyricsPost = post },
                            onOpenSpeakerProfile = { uid ->
                                if (uid == currentUid) currentTab = UnmutedTab.PROFILE
                                else viewingOtherUserId = uid
                            },
                            modifier = mod
                        )

                        UnmutedTab.INBOX -> InboxScreen(
                            conversationsState = conversationsState,
                            allProfiles = allProfiles,
                            blockedUids = blockedUids,
                            activeConversation = activeConversation,
                            messagesState = messagesState,
                            currentUid = currentUid,
                            playback = playback,
                            recordingState = recordingState,
                            audioRecorder = audioPlaybackViewModel.audioRecorder,
                            onOpenConversation = { conv -> inboxViewModel.openConversation(conv) },
                            onCloseConversation = { inboxViewModel.closeConversation() },
                            onStartNewConversation = { other -> inboxViewModel.startConversationWith(myProfile, other) },
                            onSendText = { conv, text, done -> inboxViewModel.sendTextDm(conv, myProfile, text, done) },
                            onSendVoice = { conv, file, dur, peaks, done ->
                                inboxViewModel.sendVoiceDm(conv, myProfile, file, dur, peaks, onSent = done)
                            },
                            onSendImage = { conv, uri -> inboxViewModel.sendImageDm(conv, myProfile, uri) },
                            onPlayVoiceMessage = { msg -> audioPlaybackViewModel.togglePlayDirectMessage(msg) },
                            modifier = mod
                        )

                        UnmutedTab.PROFILE -> ProfileScreen(
                            profile = myProfile,
                            isOwnProfile = true,
                            isFollowing = false,
                            allPosts = recentPosts,
                            reactions = reactions,
                            rooms = roomsWindow,
                            joinedRooms = joinedRooms,
                            safetySettings = safetySettings,
                            profileReplies = profileReplies,
                            profileRepliesLoading = profileRepliesLoading,
                            playback = playback,
                            recordingState = recordingState,
                            audioRecorder = audioPlaybackViewModel.audioRecorder,
                            currentUid = currentUid,
                            onToggleFollow = {},
                            onMessageUser = {},
                            onOpenSafetyControls = { showSafetyControlsSheet = true },
                            onMuteSpeaker = { uid, name -> feedViewModel.muteSpeaker(uid, name) },
                            onBlockSpeaker = { uid, name -> feedViewModel.blockSpeaker(uid, name) },
                            onReportProfile = {},
                            onSignOut = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeSignOut = {
                                        audioPlaybackViewModel.stopPlayback()
                                        audioPlaybackViewModel.audioRecorder.reset()
                                        feedViewModel.closeComments()
                                        inboxViewModel.closeConversation()
                                    },
                                    onSignOutComplete = onSignedOut
                                )
                            },
                            onPlayVoiceBio = { audioPlaybackViewModel.togglePlayVoiceBio(myProfile) },
                            onPreloadVoiceBio = { audioPlaybackViewModel.preloadMediaId(it) },
                            onPlayVoiceReply = { comment ->
                                comment.audioMediaId?.let { mediaId ->
                                    audioPlaybackViewModel.togglePlayMediaId(
                                        mediaId = mediaId,
                                        disguise = comment.disguiseEnum(),
                                        speakerName = comment.displaySpeakerName(),
                                        title = comment.transcript?.take(56)?.ifBlank { "Voice Reply" } ?: "Voice Reply"
                                    )
                                }
                            },
                            onPlayPost = { post -> audioPlaybackViewModel.togglePlayPost(post) },
                            onSeekToMs = { ms -> audioPlaybackViewModel.seekTo(ms) },
                            onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                            onVote = { post, vote -> feedViewModel.votePost(post, vote) },
                            onOpenComments = { post -> feedViewModel.openComments(post) },
                            onOpenDebateSide = { post, stance -> feedViewModel.openComments(post, stance) },
                            onQuoteClapback = { post ->
                                feedViewModel.prepareComposer(kind = PostKind.FEED_VOICE, quotedPost = post)
                                showComposer = true
                            },
                            onEcho = { post -> feedViewModel.echoPost(post) },
                            onBookmark = { post -> feedViewModel.bookmarkPost(post) },
                            onReportPost = reportPostAction,
                            onHidePost = { id -> feedViewModel.hidePostLocally(id) },
                            onDeletePost = { id -> feedViewModel.deletePost(id) },
                            onExpandLyrics = { post -> globalLyricsPost = post },
                            onOpenRoom = { roomId ->
                                selectedRoomId = roomId
                                currentTab = UnmutedTab.ROOMS
                            },
                            onOpenSpeakerProfile = { uid ->
                                if (uid == currentUid) currentTab = UnmutedTab.PROFILE
                                else viewingOtherUserId = uid
                            },
                            onSaveCustomization = { name, handle, bio, acc, banner, wf, interests, avatarUri, bioFile, bioDur, bioPeaks, done ->
                                profileViewModel.saveProfileCustomization(
                                    name, handle, bio, acc, banner, wf, interests,
                                    avatarUri, bioFile, bioDur, bioPeaks, done
                                )
                            },
                            modifier = mod
                        )
                    }
                }
            }

            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        header = {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(ElectricCoral, StudioViolet)))
                                    .clickable {
                                        haptics(HapticCue.REACTION)
                                        feedViewModel.prepareComposer(PostKind.FEED_VOICE)
                                        showComposer = true
                                    }
                                    .testTag("speak_studio_fab"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "Speak", tint = StudioWhite)
                            }
                        }
                    ) {
                        UnmutedTab.entries.forEach { tab ->
                            val selected = currentTab == tab && viewingOtherUserId == null
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    haptics(HapticCue.CAPTION_TOGGLE)
                                    viewingOtherUserId = null
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        contentBody(Modifier.fillMaxSize())
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = 560.dp)
                                .navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SnackbarHost(hostState = snackbarHostState)
                            FloatingMiniPlayerBar(
                                playback = playback,
                                allowVisible = showMiniPlayer,
                                onTogglePlayPause = { audioPlaybackViewModel.togglePlayPauseActive() },
                                onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                                onDismiss = { audioPlaybackViewModel.stopPlayback() },
                                onOpenNowPlaying = {
                                    playback.nowPlaying?.sourcePostId?.let { id ->
                                        val match = recentPosts.firstOrNull { it.id == id }
                                            ?: pagedPosts.itemSnapshotList.items.firstOrNull { it.id == id }
                                        if (match != null) globalLyricsPost = postOverrides[match.id] ?: match
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                Scaffold(
                    containerColor = ObsidianBackground,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            FloatingMiniPlayerBar(
                                playback = playback,
                                allowVisible = showMiniPlayer,
                                onTogglePlayPause = { audioPlaybackViewModel.togglePlayPauseActive() },
                                onCycleSpeed = { audioPlaybackViewModel.cycleSpeed() },
                                onDismiss = { audioPlaybackViewModel.stopPlayback() },
                                onOpenNowPlaying = {
                                    playback.nowPlaying?.sourcePostId?.let { id ->
                                        val match = recentPosts.firstOrNull { it.id == id }
                                            ?: pagedPosts.itemSnapshotList.items.firstOrNull { it.id == id }
                                        if (match != null) globalLyricsPost = postOverrides[match.id] ?: match
                                    }
                                }
                            )
                            if (activeConversation == null) {
                                UnmutedBottomBar(
                                    currentTab = currentTab,
                                    onSelectTab = { tab ->
                                        haptics(HapticCue.CAPTION_TOGGLE)
                                        viewingOtherUserId = null
                                        currentTab = tab
                                    },
                                    onOpenStudioMic = {
                                        haptics(HapticCue.REACTION)
                                        val defaultKind = if (isInPulsesMode) PostKind.PULSE_SHORT else PostKind.FEED_VOICE
                                        feedViewModel.prepareComposer(kind = defaultKind)
                                        showComposer = true
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    contentBody(Modifier.fillMaxSize().padding(innerPadding))
                }
            }

            // Center Studio Mic Composer Sheet
            if (showComposer) {
                ComposerStudioSheet(
                    draft = composerDraft,
                    recordingState = recordingState,
                    playback = playback,
                    rooms = roomsWindow,
                    currentUid = currentUid,
                    audioPlayer = audioPlaybackViewModel.audioPlayer,
                    onDismiss = {
                        audioPlaybackViewModel.audioRecorder.reset()
                        audioPlaybackViewModel.stopPlayback()
                        showComposer = false
                    },
                    onSelectKind = { feedViewModel.updateComposerKind(it) },
                    onSelectRoom = { feedViewModel.selectComposerRoom(it) },
                    onToggleAnonymous = { feedViewModel.toggleComposerAnonymous(it) },
                    onRollBurner = { feedViewModel.rollBurnerIdentity() },
                    onSelectDisguise = { feedViewModel.setComposerDisguise(it) },
                    onToggleHotTake = { feedViewModel.toggleComposerHotTake(it) },
                    onQuoteRangeChanged = { s, e -> feedViewModel.updateQuoteRange(s, e) },
                    onPreviewQuoteClip = {
                        composerDraft.quotedPost?.let { qPost ->
                            audioPlaybackViewModel.previewQuotedClip(
                                quotedPost = qPost,
                                startMs = composerDraft.quoteStartMs,
                                endMs = composerDraft.quoteEndMs
                            )
                        }
                    },
                    onStartRecord = { feedViewModel.startStudioRecording() },
                    onTogglePauseRecord = { audioPlaybackViewModel.audioRecorder.togglePause() },
                    onStopRecord = { feedViewModel.stopStudioRecording() },
                    onRetake = { feedViewModel.retakeStudioRecording() },
                    onRetryTranscribe = { feedViewModel.retryComposerTranscription() },
                    onTranscriptChanged = { feedViewModel.updateComposerTranscript(it) },
                    onHeadlineChanged = { feedViewModel.updateComposerHeadline(it) },
                    onHashtagsChanged = { feedViewModel.updateComposerHashtags(it) },
                    onAttachImageUri = { feedViewModel.attachComposerImage(it) },
                    onClearImage = { feedViewModel.clearComposerImage() },
                    onPublish = {
                        feedViewModel.publishComposerPost(myProfile) {
                            showComposer = false
                            pagedPosts.refresh()
                            pagedDiscoverPosts.refresh()
                        }
                    }
                )
            }

            // Threaded Voice & Text Comments Sheet
            val commentsTarget = activeCommentsPost
            if (commentsTarget != null) {
                CommentsBottomSheet(
                    post = commentsTarget,
                    initialStance = activeCommentsInitialStance,
                    commentsState = commentsState,
                    filteredAuthorUids = filteredAuthorUids,
                    playback = playback,
                    recordingState = recordingState,
                    audioRecorder = audioPlaybackViewModel.audioRecorder,
                    onDismiss = { feedViewModel.closeComments() },
                    onPlayVoiceComment = { mediaId, disguise ->
                        audioPlaybackViewModel.togglePlayMediaId(
                            mediaId = mediaId,
                            disguise = disguise,
                            speakerName = commentsTarget.displaySpeakerName(),
                            title = "Voice Reply"
                        )
                    },
                    onUpvoteComment = { comment -> feedViewModel.upvoteComment(commentsTarget.id, comment) },
                    onReportComment = { comment ->
                        pendingReportTarget = SafetyReportTarget(
                            targetType = "COMMENT",
                            targetId = comment.id,
                            targetAuthorUid = comment.authorUid,
                            targetDisplayName = comment.displaySpeakerName()
                        )
                    },
                    onSendTextComment = { text, stance, done ->
                        feedViewModel.submitTextComment(commentsTarget, myProfile, text, stance, done)
                    },
                    onSendVoiceComment = { file, dur, peaks, disguise, stance, done ->
                        feedViewModel.submitVoiceComment(
                            post = commentsTarget,
                            author = myProfile,
                            audioFile = file,
                            durationMs = dur,
                            peaks = peaks,
                            disguise = disguise,
                            stance = stance,
                            onDone = done
                        )
                    }
                )
            }

            // Mandatory Structured Safety Report Sheet (Illegal & Harmful Content Compliance)
            val reportTarget = pendingReportTarget
            if (reportTarget != null) {
                SafetyReportBottomSheet(
                    target = reportTarget,
                    currentUid = currentUid,
                    onDismiss = { pendingReportTarget = null },
                    onSubmit = { category, details, alsoBlock ->
                        feedViewModel.submitSafetyReport(reportTarget, category, details, alsoBlock)
                        pendingReportTarget = null
                    }
                )
            }

            // User-Controlled Safety & Open Debate Controls Sheet
            if (showSafetyControlsSheet) {
                SafetyControlsBottomSheet(
                    settings = safetySettings,
                    relations = safetyRelations,
                    onUpdateSettings = { updated -> feedViewModel.updateSafetySettings(updated) },
                    onRemoveRelation = { targetUid -> feedViewModel.removeSafetyRelation(targetUid) },
                    onDismiss = { showSafetyControlsSheet = false }
                )
            }

            // Full-Screen Spotify-Style Word-Synced Lyrics Focus Dialog
            val lyricsModalPost = globalLyricsPost
            if (lyricsModalPost != null) {
                FullLyricsFocusDialog(
                    post = lyricsModalPost,
                    playback = playback,
                    onPlayPause = { audioPlaybackViewModel.togglePlayPost(lyricsModalPost) },
                    onSeekToMs = { ms -> audioPlaybackViewModel.seekTo(ms) },
                    onDismiss = { globalLyricsPost = null }
                )
            }
        }
    }
}

@Composable
private fun UnmutedBottomBar(
    currentTab: UnmutedTab,
    onSelectTab: (UnmutedTab) -> Unit,
    onOpenStudioMic: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = ObsidianHairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(UnmutedTab.FEED, currentTab == UnmutedTab.FEED) { onSelectTab(UnmutedTab.FEED) }
                BottomNavItem(UnmutedTab.DISCOVER, currentTab == UnmutedTab.DISCOVER) { onSelectTab(UnmutedTab.DISCOVER) }

                // Center Tactile Midnight Studio Mic Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCoral, StudioViolet)))
                        .clickable(onClick = onOpenStudioMic)
                        .testTag("speak_studio_fab"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Open Studio Mic to speak",
                        tint = StudioWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }

                BottomNavItem(UnmutedTab.ROOMS, currentTab == UnmutedTab.ROOMS) { onSelectTab(UnmutedTab.ROOMS) }
                BottomNavItem(UnmutedTab.INBOX, currentTab == UnmutedTab.INBOX) { onSelectTab(UnmutedTab.INBOX) }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: UnmutedTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("nav_tab_${tab.name.lowercase()}")
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.label,
            tint = if (selected) ElectricCoral else TitaniumMuted,
            modifier = Modifier.size(23.dp)
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ElectricCoral else TitaniumMuted
        )
    }
}
