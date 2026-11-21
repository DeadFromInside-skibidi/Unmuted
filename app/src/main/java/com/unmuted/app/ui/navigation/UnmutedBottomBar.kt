package com.unmuted.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

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
fun UnmutedBottomBar(
    currentTab: UnmutedTab,
    onSelectTab: (UnmutedTab) -> Unit,
    onOpenStudioMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianSurface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = ObsidianHairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(UnmutedTab.FEED, currentTab == UnmutedTab.FEED) { onSelectTab(UnmutedTab.FEED) }
                BottomNavItem(UnmutedTab.DISCOVER, currentTab == UnmutedTab.DISCOVER) { onSelectTab(UnmutedTab.DISCOVER) }

                // Center Tactile Midnight Studio Mic Button
                Box(
                    modifier = Modifier
                        .size(50.dp)
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
                        modifier = Modifier.size(24.dp)
                    )
                }

                BottomNavItem(UnmutedTab.ROOMS, currentTab == UnmutedTab.ROOMS) { onSelectTab(UnmutedTab.ROOMS) }
                BottomNavItem(UnmutedTab.INBOX, currentTab == UnmutedTab.INBOX) { onSelectTab(UnmutedTab.INBOX) }
                BottomNavItem(UnmutedTab.PROFILE, currentTab == UnmutedTab.PROFILE) { onSelectTab(UnmutedTab.PROFILE) }
            }
        }
    }
}

@Composable
fun BottomNavItem(
    tab: UnmutedTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("nav_tab_${tab.name.lowercase()}")
    ) {
        Icon(
            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.label,
            tint = if (selected) ElectricCoral else TitaniumMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ElectricCoral else TitaniumMuted
        )
    }
}
