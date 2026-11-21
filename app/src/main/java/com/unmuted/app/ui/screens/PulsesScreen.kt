package com.unmuted.app.ui.screens

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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.core.PlaybackState
import com.unmuted.app.data.PostKind
import com.unmuted.app.data.SafetySettings
import com.unmuted.app.data.UserPostReaction
import com.unmuted.app.data.VoicePost
import com.unmuted.app.data.VoteType
import com.unmuted.app.data.formatDurationMs
import com.unmuted.app.ui.components.InteractiveWaveform
import com.unmuted.app.ui.components.ReactiveOrbBackdrop
import com.unmuted.app.ui.components.SyncedLyricsView
import com.unmuted.app.ui.components.UserAvatarBadge
import com.unmuted.app.ui.theme.AcousticError
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianBackground
import com.unmuted.app.ui.theme.ObsidianSurface
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import com.unmuted.app.ui.viewmodel.DataUiState

@Composable
fun PulsesScreen(
    postsState: DataUiState<List<VoicePost>>,
    reactions: Map<String, UserPostReaction>,
    playback: PlaybackState,
    filteredAuthorUids: Set<String> = emptySet(),
    safetySettings: SafetySettings = SafetySettings(),
    onAutoPlayPost: (VoicePost) -> Unit,
    onTogglePlayPost: (VoicePost) -> Unit,
    onSeekToMs: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onVote: (VoicePost, VoteType) -> Unit,
    onOpenComments: (VoicePost) -> Unit,
    onEcho: (VoicePost) -> Unit,
    onRecordPulse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allPosts = remember(postsState, filteredAuthorUids, safetySettings) {
        (postsState as? DataUiState.Success)?.data.orEmpty()
            .filterNot { it.authorUid in filteredAuthorUids }
            .filter { safetySettings.showUnfilteredControversial || !it.isUnfilteredOrControversial() }
    }
    val pulseItems = remember(allPosts) {
        val shorts = allPosts.filter { it.kindEnum() == PostKind.PULSE_SHORT && !it.audioMediaId.isNullOrBlank() }
        val others = allPosts.filter { it.kindEnum() == PostKind.FEED_VOICE && !it.audioMediaId.isNullOrBlank() }
        shorts + others
    }

    var lyricStageMode by rememberSaveable { mutableStateOf(true) }

    if (pulseItems.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCoral.copy(0.25f), StudioViolet.copy(0.25f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.GraphicEq, null, tint = ElectricCoral, modifier = Modifier.size(38.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Voice Pulses", style = MaterialTheme.typography.headlineMedium, color = StudioWhite)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Short-form voice takes with live Spotify-style scrolling captions. Record the first Pulse now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TitaniumMuted
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onRecordPulse,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCoral),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("record_first_pulse_btn")
                ) {
                    Icon(Icons.Filled.Mic, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Record a Pulse")
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { pulseItems.size })

    LaunchedEffect(pagerState.settledPage, pulseItems.size) {
        val item = pulseItems.getOrNull(pagerState.settledPage)
        if (item != null) {
            onAutoPlayPost(item)
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .testTag("pulses_vertical_pager")
    ) { page ->
        val post = pulseItems[page]
        val isThisActive = playback.activeMediaId == post.audioMediaId
        val isPlaying = isThisActive && playback.playing
        val durationMs = if (isThisActive && playback.durationMs > 0L) playback.durationMs else post.durationMs.coerceAtLeast(1L)
        val posMs = if (isThisActive) playback.positionMs else 0L
        val progress = (posMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val palette = ProfileAccentPalette.fromKey(post.authorAccentKey)
        val accent = if (post.anonymousMode) StudioViolet else palette.primary
        val reaction = reactions[post.id]
        val vote = reaction?.voteEnum() ?: VoteType.NONE
        val echoed = reaction?.echoed == true
        val mediaBitmap = remember(post.attachmentImageBase64) {
            ImageCompressor.decodeBase64ToBitmap(post.attachmentImageBase64)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.22f),
                            ObsidianBackground,
                            Color(0xFF07080C)
                        )
                    )
                )
        ) {
            if (!lyricStageMode && mediaBitmap != null) {
                Image(
                    bitmap = mediaBitmap.asImageBitmap(),
                    contentDescription = "Pulse visual backdrop",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.36f)
                )
            }

            ReactiveOrbBackdrop(
                active = isPlaying,
                primaryColor = accent,
                secondaryColor = palette.secondary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("PULSES", style = MaterialTheme.typography.titleMedium, color = StudioWhite)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurface.copy(alpha = 0.72f),
                        modifier = Modifier.clickable(onClick = onCycleSpeed)
                    ) {
                        Text(
                            text = when (playback.speed) {
                                1.5f -> "1.5x"
                                2.0f -> "2x"
                                else -> "1x"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioWhite,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (lyricStageMode) accent.copy(alpha = 0.28f) else ObsidianSurface.copy(alpha = 0.72f),
                        modifier = Modifier
                            .clickable { lyricStageMode = !lyricStageMode }
                            .testTag("pulse_stage_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (lyricStageMode) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                                contentDescription = "Toggle Lyrics Stage",
                                tint = StudioWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (lyricStageMode) "Lyric Stage" else "Visual Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioWhite
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 68.dp, bottom = 152.dp, start = 18.dp, end = 78.dp),
                contentAlignment = Alignment.Center
            ) {
                if (lyricStageMode) {
                    SyncedLyricsView(
                        segments = post.captionSegments,
                        fallbackTranscript = post.transcript,
                        positionMs = posMs,
                        isActiveAudio = isThisActive,
                        isFullStage = true,
                        accentColor = accent,
                        onSeekToMs = onSeekToMs
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.22f))
                                .clickable { onTogglePlayPost(post) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Toggle playback",
                                tint = StudioWhite,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        val activeLine = remember(post.captionSegments, posMs) {
                            post.captionSegments.firstOrNull { posMs in it.startMs..it.endMs }?.text
                                ?: post.transcript.take(120)
                        }
                        if (activeLine.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ObsidianSurface.copy(alpha = 0.78f)
                            ) {
                                Text(
                                    text = activeLine,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StudioWhite,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PulseActionIcon(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    label = if (isPlaying) "Pause" else "Play",
                    tint = accent,
                    onClick = { onTogglePlayPost(post) }
                )
                PulseActionIcon(
                    icon = if (vote == VoteType.LIKE) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    label = post.likeCount.toString(),
                    tint = if (vote == VoteType.LIKE) ElectricCoral else StudioWhite,
                    onClick = { onVote(post, VoteType.LIKE) }
                )
                PulseActionIcon(
                    icon = if (vote == VoteType.DISLIKE) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    label = post.dislikeCount.toString(),
                    tint = if (vote == VoteType.DISLIKE) AcousticError else StudioWhite,
                    onClick = { onVote(post, VoteType.DISLIKE) }
                )
                PulseActionIcon(
                    icon = Icons.Filled.ChatBubbleOutline,
                    label = post.commentCount.toString(),
                    tint = StudioWhite,
                    onClick = { onOpenComments(post) }
                )
                PulseActionIcon(
                    icon = Icons.Filled.Repeat,
                    label = post.echoCount.toString(),
                    tint = if (echoed) SignalMint else StudioWhite,
                    onClick = { onEcho(post) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatarBadge(
                        name = post.displaySpeakerName(),
                        avatarBase64 = if (post.anonymousMode) null else post.authorAvatarBase64,
                        accentKey = post.authorAccentKey,
                        anonymousMode = post.anonymousMode,
                        size = 38.dp,
                        showRing = true
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(post.displaySpeakerName(), style = MaterialTheme.typography.titleMedium, color = StudioWhite)
                            if (post.anonymousMode) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.Shield, null, tint = StudioViolet, modifier = Modifier.size(14.dp))
                            }
                        }
                        Text(
                            text = buildString {
                                append(post.displaySpeakerHandle())
                                if (!post.roomSlug.isNullOrBlank()) append(" · r/${post.roomSlug}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TitaniumMuted
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                InteractiveWaveform(
                    peaks = post.waveformPeaks.map { it.toFloat() },
                    progress = progress,
                    playing = isPlaying,
                    accentColor = accent,
                    secondaryColor = palette.secondary,
                    onSeekFraction = { frac -> onSeekToMs((durationMs * frac).toLong()) },
                    modifier = Modifier.height(34.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDurationMs(posMs), style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
                    Text(formatDurationMs(durationMs), style = MaterialTheme.typography.labelSmall, color = TitaniumMuted)
                }
            }
        }
    }
}

@Composable
private fun PulseActionIcon(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ObsidianSurface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioWhite)
    }
}
