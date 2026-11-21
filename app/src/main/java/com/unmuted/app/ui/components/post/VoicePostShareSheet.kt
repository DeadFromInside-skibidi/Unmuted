package com.unmuted.app.ui.components.post

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.exportAudiogram
import com.unmuted.app.core.shareAudiogramVideo
import com.unmuted.app.core.sharePostText
import com.unmuted.app.data.VoicePost
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.ObsidianHairline
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun VoicePostShareSheet(
    post: VoicePost,
    primaryAccent: Color,
    showShareMenu: Boolean,
    onDismissShareMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportJob by remember { mutableStateOf<Job?>(null) }

    if (showShareMenu) {
        AlertDialog(
            onDismissRequest = onDismissShareMenu,
            title = {
                Text(
                    text = "Share Voice Take",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StudioWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Export as vertical 9:16 video or share quote link:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TitaniumMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissShareMenu()
                                isExporting = true
                                exportProgress = 0f
                                exportJob = coroutineScope.launch {
                                    try {
                                        val file = exportAudiogram(context, post) { prog ->
                                            exportProgress = prog
                                        }
                                        isExporting = false
                                        shareAudiogramVideo(context, file, post)
                                    } catch (e: CancellationException) {
                                        isExporting = false
                                    } catch (e: Exception) {
                                        isExporting = false
                                        Toast.makeText(
                                            context,
                                            "Export failed: ${e.localizedMessage ?: "Unknown error"}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(primaryAccent.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Share Video (9:16 Audiogram)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioWhite
                                )
                                Text(
                                    text = "Stories, WhatsApp, Shorts & Reels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianHairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismissShareMenu()
                                sharePostText(context, post)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(SignalMint.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = SignalMint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Share Text & Quote",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StudioWhite
                                )
                                Text(
                                    text = "Copy quote and post link",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TitaniumMuted
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissShareMenu) {
                    Text("Close", color = TitaniumMuted)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = modifier
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = {
                exportJob?.cancel()
                isExporting = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Rendering 9:16 Audiogram",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = StudioWhite
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rendering synced lyrics and live waveform...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TitaniumMuted
                    )
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = primaryAccent,
                        trackColor = ObsidianSurfaceElevated
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${(exportProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportJob?.cancel()
                        isExporting = false
                    }
                ) {
                    Text("Cancel", color = AcousticError)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = modifier
        )
    }
}
