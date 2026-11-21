package com.unmuted.app.ui.navigation

import androidx.compose.runtime.Composable
import com.unmuted.app.ui.viewmodel.AudioPlaybackViewModel
import com.unmuted.app.ui.viewmodel.DiscoverViewModel
import com.unmuted.app.ui.viewmodel.FeedViewModel
import com.unmuted.app.ui.viewmodel.InboxViewModel
import com.unmuted.app.ui.viewmodel.ProfileViewModel
import com.unmuted.app.ui.viewmodel.RoomsViewModel

/**
 * Navigation entry-point Composable for UnmutedAppShell.
 */
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
    com.unmuted.app.ui.UnmutedAppShell(
        audioPlaybackViewModel = audioPlaybackViewModel,
        feedViewModel = feedViewModel,
        discoverViewModel = discoverViewModel,
        roomsViewModel = roomsViewModel,
        inboxViewModel = inboxViewModel,
        profileViewModel = profileViewModel,
        onSignedOut = onSignedOut
    )
}
