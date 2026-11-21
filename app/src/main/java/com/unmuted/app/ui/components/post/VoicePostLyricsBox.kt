package com.unmuted.app.ui.components.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.VoicePost
import com.unmuted.app.ui.components.SyncedLyricsView
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted

@Composable
fun VoicePostLyricsBox(
    post: VoicePost,
    playback: PlaybackState,
    isThisActive: Boolean,
    isQuotedPhase: Boolean,
    isClapback: Boolean,
    captionsOpen: Boolean,
    primaryAccent: Color,
    onPlayPause: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onExpandLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = captionsOpen,
        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianSurface.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (isQuotedPhase) SignalCyan else SignalMint,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isQuotedPhase) {
                            "QUOTED CLIP PLAYING · SWITCHING SOON"
                        } else {
                            "LIVE LYRICS · TAP ANY LINE TO SEEK"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isQuotedPhase) SignalCyan else SignalMint
                    )
                }
                IconButton(
                    onClick = onExpandLyrics,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Expand full lyrics stage",
                        tint = TitaniumMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (isClapback && !post.quotedTranscript.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SignalCyan.copy(alpha = if (isQuotedPhase) 0.22f else 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 6.dp)
                        .clickable {
                            if (!isThisActive) onPlayPause()
                            onSeekToMs(0L)
                        }
                ) {
                    Text(
                        text = "${post.quotedAuthorName ?: "Quoted"}: “${post.quotedTranscript}”",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isQuotedPhase) FontWeight.Bold else FontWeight.Normal,
                        color = if (isQuotedPhase) StudioWhite else SignalCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            SyncedLyricsView(
                segments = post.captionSegments,
                fallbackTranscript = post.transcript,
                positionMs = if (isThisActive && !isQuotedPhase) playback.positionMs else 0L,
                isActiveAudio = isThisActive && !isQuotedPhase,
                isFullStage = false,
                accentColor = primaryAccent,
                onSeekToMs = { target ->
                    if (!isThisActive) onPlayPause()
                    val seekTarget = if (isClapback) post.quotedDurationMs() + target else target
                    onSeekToMs(seekTarget)
                },
                modifier = Modifier.heightIn(max = 172.dp)
            )
        }
    }
}
