package com.unmuted.app.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.unmuted.app.R
import com.unmuted.app.data.UserProfile
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.StudioHaptics
import com.unmuted.app.ui.components.UserAvatarBadge
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioBannerMood
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

@Composable
fun ProfileHeader(
    profile: UserProfile,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    accent: ProfileAccentPalette,
    bannerMood: StudioBannerMood,
    myTakesCount: Int,
    joinedRoomsCount: Int,
    haptics: StudioHaptics,
    onBack: (() -> Unit)?,
    onOpenSafetyControls: () -> Unit,
    onCustomizeStudio: () -> Unit,
    onSignOut: () -> Unit,
    onMuteSpeaker: (String, String) -> Unit,
    onBlockSpeaker: (String, String) -> Unit,
    onReportProfile: (UserProfile) -> Unit,
    onMessageUser: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showProfileOverflow by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
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
                                    onCustomizeStudio()
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

        // Identity Row
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
                            text = "· $myTakesCount takes · $joinedRoomsCount rooms",
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
        }
    }
}
