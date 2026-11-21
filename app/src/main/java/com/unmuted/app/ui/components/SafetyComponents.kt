package com.unmuted.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmuted.app.data.ReportCategory
import com.unmuted.app.data.SafetyRelation
import com.unmuted.app.data.SafetyRelationType
import com.unmuted.app.data.SafetyReportTarget
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

/**
 * Mandatory structured reporting flow for illegal & harmful content (App Store / Google Play UGC compliance).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyReportBottomSheet(
    target: SafetyReportTarget,
    currentUid: String,
    onDismiss: () -> Unit,
    onSubmit: (ReportCategory, String, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember(target.targetId) { mutableStateOf<ReportCategory?>(null) }
    var details by remember(target.targetId) { mutableStateOf("") }
    var alsoBlock by remember(target.targetId) { mutableStateOf(false) }
    val canBlockAuthor = !target.targetAuthorUid.isNullOrBlank() && target.targetAuthorUid != currentUid

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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("safety_report_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = AcousticError,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Report Illegal or Harmful Content",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioWhite
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Unmuted protects open debate, while enforcing strict zero-tolerance rules against illegal content, credible violence, and exploitation.",
                style = MaterialTheme.typography.bodySmall,
                color = TitaniumMuted
            )

            Spacer(Modifier.height(14.dp))

            ReportCategory.entries.forEach { category ->
                val isSelected = selectedCategory == category
                val accent = if (category.urgentLegalReview) AcousticError else ElectricCoral
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) accent.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                    border = BorderStroke(
                        width = if (isSelected) 1.3.dp else 1.dp,
                        color = if (isSelected) accent else ObsidianHairline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedCategory = category }
                        .testTag("report_cat_${category.code.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            colors = RadioButtonDefaults.colors(selectedColor = accent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioWhite,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (category.urgentLegalReview) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AcousticError.copy(alpha = 0.24f)
                                    ) {
                                        Text(
                                            text = "URGENT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = AcousticError,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = category.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TitaniumMuted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = details,
                onValueChange = { details = it.take(500) },
                label = { Text("Optional context (timestamps, details)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_details_input")
            )

            if (canBlockAuthor) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Also block ${target.targetDisplayName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioWhite
                            )
                            Text(
                                text = "Immediately hides all voices, replies, and DMs from this speaker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TitaniumMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = alsoBlock,
                            onCheckedChange = { alsoBlock = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = AcousticError),
                            modifier = Modifier.testTag("report_also_block_switch")
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val chosen = selectedCategory ?: return@Button
                        onSubmit(chosen, details.trim(), alsoBlock)
                    },
                    enabled = selectedCategory != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedCategory?.urgentLegalReview == true) AcousticError else ElectricCoral
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("submit_safety_report_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (selectedCategory?.urgentLegalReview == true) "Escalate Report" else "Submit Report",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * User-controlled Safety & Open Debate Controls sheet:
 * - Toggle unfiltered/controversial rooms & anonymous hot takes
 * - Configure community downvote auto-collapse threshold
 * - Manage Muted and Blocked speakers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyControlsBottomSheet(
    settings: SafetySettings,
    relations: List<SafetyRelation>,
    onUpdateSettings: (SafetySettings) -> Unit,
    onRemoveRelation: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberStudioHaptics()

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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("safety_controls_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GppGood,
                    contentDescription = null,
                    tint = SignalMint,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Safety & Open Debate Controls",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioWhite
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "You decide what enters your feed. Mute speakers, tune community downvote filters, or toggle unfiltered debate rooms anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = TitaniumMuted
            )

            Spacer(Modifier.height(16.dp))

            // 1. User-controlled content sensitivity toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show unfiltered / controversial rooms",
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioWhite
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Display anonymous debate rooms, confessions, and spicy Hot Take threads in Feed, Discover, and Rooms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TitaniumMuted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = settings.showUnfilteredControversial,
                        onCheckedChange = { enabled ->
                            haptics(HapticCue.CAPTION_TOGGLE)
                            onUpdateSettings(settings.copy(showUnfilteredControversial = enabled))
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = StudioViolet),
                        modifier = Modifier.testTag("toggle_unfiltered_controversial")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. Community downvote visibility controls
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Collapse heavily downvoted voices",
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioWhite
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Place a tap-to-reveal veil over community-downvoted takes instead of deleting them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TitaniumMuted
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = settings.collapseHeavilyDownvoted,
                            onCheckedChange = { enabled ->
                                haptics(HapticCue.CAPTION_TOGGLE)
                                onUpdateSettings(settings.copy(collapseHeavilyDownvoted = enabled))
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = ElectricCoral),
                            modifier = Modifier.testTag("toggle_collapse_downvoted")
                        )
                    }

                    if (settings.collapseHeavilyDownvoted) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Collapse threshold (net downvotes):",
                            style = MaterialTheme.typography.labelMedium,
                            color = TitaniumMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 10, 20).forEach { threshold ->
                                val selected = settings.downvoteThreshold == threshold
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptics(HapticCue.CAPTION_TOGGLE)
                                        onUpdateSettings(settings.copy(downvoteThreshold = threshold))
                                    },
                                    label = { Text("-$threshold net") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCoral.copy(alpha = 0.24f),
                                        selectedLabelColor = StudioWhite
                                    ),
                                    modifier = Modifier.testTag("downvote_threshold_$threshold")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ObsidianHairline)
            Spacer(Modifier.height(14.dp))

            // 3. Per-user Mute & Block list
            Text(
                text = "Muted & Blocked Speakers (${relations.size})",
                style = MaterialTheme.typography.titleMedium,
                color = StudioWhite
            )
            Spacer(Modifier.height(6.dp))

            if (relations.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SignalMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "No speakers muted or blocked. Use the ⋯ menu on any post or profile to mute or block.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TitaniumMuted
                        )
                    }
                }
            } else {
                relations.forEach { rel ->
                    val isBlocked = rel.typeEnum() == SafetyRelationType.BLOCK
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianHairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isBlocked) Icons.Filled.Block else Icons.Filled.VolumeOff,
                                contentDescription = null,
                                tint = if (isBlocked) AcousticError else AmberVinyl,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rel.targetName.ifBlank { "Speaker" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioWhite
                                )
                                Text(
                                    text = if (isBlocked) "Blocked · Hidden everywhere & DMs blocked" else "Muted · Hidden from your feeds",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TitaniumMuted
                                )
                            }
                            TextButton(
                                onClick = {
                                    haptics(HapticCue.REACTION)
                                    onRemoveRelation(rel.targetUid)
                                },
                                modifier = Modifier.testTag("unmute_unblock_${rel.targetUid}")
                            ) {
                                Text(
                                    text = if (isBlocked) "Unblock" else "Unmute",
                                    color = ElectricCoral,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/**
 * Shown when user has turned OFF "Show unfiltered / controversial rooms", giving a clear 1-tap way to opt back in.
 */
@Composable
fun UnfilteredFilterNoticeBanner(
    hiddenCount: Int,
    onEnableUnfiltered: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StudioViolet.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, StudioViolet.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("unfiltered_notice_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = StudioViolet,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unfiltered & controversial takes hidden ($hiddenCount)",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioWhite
                )
                Text(
                    text = "Your sensitivity filter is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TitaniumMuted
                )
            }
            TextButton(onClick = onEnableUnfiltered) {
                Text("Show", color = ElectricCoral, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DownvotedPostVeil(
    netDownvotes: Long,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurfaceElevated,
        border = BorderStroke(1.dp, AmberVinyl.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onReveal() }
            .testTag("downvoted_post_veil")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ReportProblem,
                contentDescription = "Collapsed by community downvotes",
                tint = AmberVinyl,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Collapsed by community downvotes (-$netDownvotes net)",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioWhite
                )
                Text(
                    text = "Open debate preserved · Tap to reveal and listen anyway",
                    style = MaterialTheme.typography.bodySmall,
                    color = TitaniumMuted
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Reveal",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = ElectricCoral
            )
        }
    }
}
