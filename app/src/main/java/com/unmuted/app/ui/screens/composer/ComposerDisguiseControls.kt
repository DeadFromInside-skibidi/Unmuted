package com.unmuted.app.ui.screens.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.ui.components.DisguiseChipRow
import com.unmuted.app.ui.components.GeometricBurnerAvatar
import com.unmuted.app.ui.components.HapticCue
import com.unmuted.app.ui.components.StudioHaptics
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.viewmodel.ComposerDraftState

@Composable
fun ComposerDisguiseControls(
    draft: ComposerDraftState,
    previewAlias: String,
    haptics: StudioHaptics,
    onToggleAnonymous: (Boolean) -> Unit,
    onRollBurner: () -> Unit,
    onSelectDisguise: (VoiceDisguise) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (draft.anonymousMode) StudioViolet.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
        border = if (draft.anonymousMode) BorderStroke(1.dp, StudioViolet.copy(alpha = 0.45f)) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (draft.anonymousMode) {
                        GeometricBurnerAvatar(
                            seed = previewAlias,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, StudioViolet, CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = TitaniumMuted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (draft.anonymousMode) previewAlias else "Speak Anonymously",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = StudioWhite
                        )
                        Text(
                            text = if (draft.selectedRoom?.anonymousRoom == true) {
                                "Burner alias & voice mask enforced in r/${draft.selectedRoom.slug}"
                            } else if (draft.anonymousMode) {
                                "Auto-generated burner identity & geometric avatar"
                            } else {
                                "Hide your profile & unlock on-device voice masking"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TitaniumMuted
                        )
                    }
                }
                Switch(
                    checked = draft.anonymousMode,
                    enabled = draft.selectedRoom?.anonymousRoom != true,
                    onCheckedChange = {
                        haptics(HapticCue.CAPTION_TOGGLE)
                        onToggleAnonymous(it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = StudioWhite, checkedTrackColor = StudioViolet),
                    modifier = Modifier.testTag("composer_anon_switch")
                )
            }

            AnimatedVisibility(visible = draft.anonymousMode) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ON-DEVICE VOICE MASKING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StudioViolet
                            )
                            Text(
                                text = draft.voiceDisguise.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = TitaniumMuted
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StudioViolet.copy(alpha = 0.24f),
                            modifier = Modifier
                                .clickable {
                                    haptics(HapticCue.CAPTION_TOGGLE)
                                    onRollBurner()
                                }
                                .testTag("roll_burner_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = "New burner alias",
                                    tint = StudioWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "New Burner",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioWhite
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DisguiseChipRow(
                        selected = draft.voiceDisguise,
                        onSelect = onSelectDisguise,
                        allowNatural = draft.selectedRoom?.anonymousRoom != true
                    )
                }
            }
        }
    }
}
