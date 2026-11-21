package com.unmuted.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.unmuted.app.core.configureAppCheck
import com.unmuted.app.ui.UnmutedAppShell
import com.unmuted.app.ui.screens.AuthScreen
import com.unmuted.app.ui.theme.UnmutedTheme
import com.unmuted.app.ui.viewmodel.AudioPlaybackViewModel
import com.unmuted.app.ui.viewmodel.DiscoverViewModel
import com.unmuted.app.ui.viewmodel.FeedViewModel
import com.unmuted.app.ui.viewmodel.InboxViewModel
import com.unmuted.app.ui.viewmodel.ProfileViewModel
import com.unmuted.app.ui.viewmodel.RoomsViewModel
import com.unmuted.app.ui.viewmodel.UnmutedContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Dark Midnight Studio edge-to-edge system bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            UnmutedTheme {
                UnmutedRootGate()
            }
        }
    }
}

@Composable
private fun UnmutedRootGate() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        val context = LocalContext.current.applicationContext
        key(uid) {
            val container = remember(uid) { UnmutedContainer(context) }
            DisposableEffect(container) {
                onDispose { container.release() }
            }

            val audioPlaybackViewModel: AudioPlaybackViewModel = viewModel(
                key = "audio_$uid",
                factory = AudioPlaybackViewModel.Factory(container)
            )
            val feedViewModel: FeedViewModel = viewModel(
                key = "feed_$uid",
                factory = FeedViewModel.Factory(container)
            )
            val discoverViewModel: DiscoverViewModel = viewModel(
                key = "discover_$uid",
                factory = DiscoverViewModel.Factory(container)
            )
            val roomsViewModel: RoomsViewModel = viewModel(
                key = "rooms_$uid",
                factory = RoomsViewModel.Factory(container)
            )
            val inboxViewModel: InboxViewModel = viewModel(
                key = "inbox_$uid",
                factory = InboxViewModel.Factory(container)
            )
            val profileViewModel: ProfileViewModel = viewModel(
                key = "profile_$uid",
                factory = ProfileViewModel.Factory(container)
            )

            UnmutedAppShell(
                audioPlaybackViewModel = audioPlaybackViewModel,
                feedViewModel = feedViewModel,
                discoverViewModel = discoverViewModel,
                roomsViewModel = roomsViewModel,
                inboxViewModel = inboxViewModel,
                profileViewModel = profileViewModel,
                onSignedOut = { currentUser = null }
            )
        }
    }
}
