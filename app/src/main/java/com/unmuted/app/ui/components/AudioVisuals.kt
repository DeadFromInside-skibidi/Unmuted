package com.unmuted.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unmuted.app.core.ImageCompressor
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.WaveformStyle
import com.unmuted.app.ui.theme.AmberVinyl
import com.unmuted.app.ui.theme.BodyFontFamily
import com.unmuted.app.ui.theme.ElectricCoral
import com.unmuted.app.ui.theme.ObsidianSurfaceElevated
import com.unmuted.app.ui.theme.ProfileAccentPalette
import com.unmuted.app.ui.theme.SignalCyan
import com.unmuted.app.ui.theme.SignalMint
import com.unmuted.app.ui.theme.StudioViolet
import com.unmuted.app.ui.theme.StudioWhite
import com.unmuted.app.ui.theme.TitaniumMuted
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun InteractiveWaveform(
    peaks: List<Float>,
    progress: Float,
    playing: Boolean,
    style: WaveformStyle = WaveformStyle.BARS,
    accentColor: Color = ElectricCoral,
    secondaryColor: Color = StudioViolet,
    speakerSplitFraction: Float? = null,
    quotedAccentColor: Color = SignalCyan,
    onSeekFraction: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val safePeaks = remember(peaks) {
        if (peaks.size >= 8) peaks else List(36) { idx ->
            val wave = abs(sin(idx * 0.45f)) * 0.65f + 0.2f
            wave.coerceIn(0.15f, 0.95f)
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveform_phase"
    )
    val clampedProgress = progress.coerceIn(0f, 1f)
    val validSplit = speakerSplitFraction?.takeIf { it in 0.05f..0.92f }
    val haptics = rememberStudioHaptics()
    val currentOnSeek by rememberUpdatedState(onSeekFraction)
    var lastHapticFrac by remember { mutableFloatStateOf(-1f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .let { base ->
                if (onSeekFraction != null) {
                    base
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (size.width > 0) {
                                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    haptics(HapticCue.SCRUB_TICK)
                                    lastHapticFrac = frac
                                    currentOnSeek?.invoke(frac)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    if (size.width > 0) {
                                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        haptics(HapticCue.SCRUB_TICK)
                                        lastHapticFrac = frac
                                    }
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    if (size.width > 0) {
                                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        if (lastHapticFrac < 0f || abs(frac - lastHapticFrac) >= 0.055f) {
                                            haptics(HapticCue.SCRUB_TICK)
                                            lastHapticFrac = frac
                                        }
                                        currentOnSeek?.invoke(frac)
                                    }
                                }
                            )
                        }
                } else base
            }
    ) {
        val count = safePeaks.size
        val w = size.width
        val h = size.height
        val midY = h / 2f
        val activeBrush = Brush.horizontalGradient(listOf(accentColor, secondaryColor))
        val quotedActiveBrush = Brush.horizontalGradient(listOf(quotedAccentColor, AmberVinyl))
        val inactiveColor = Color.White.copy(alpha = 0.22f)
        val quotedInactiveColor = quotedAccentColor.copy(alpha = 0.30f)

        when (style) {
            WaveformStyle.BARS, WaveformStyle.VINYL -> {
                val gap = if (style == WaveformStyle.VINYL) 3.dp.toPx() else 2.5.dp.toPx()
                val barW = ((w - gap * (count - 1)) / count).coerceAtLeast(2.dp.toPx())
                safePeaks.forEachIndexed { idx, rawPeak ->
                    val frac = idx.toFloat() / count.coerceAtLeast(1)
                    val animBoost = if (playing) (sin(phase + idx * 0.55f) * 0.14f) else 0f
                    val amp = (rawPeak + animBoost).coerceIn(0.14f, 1f)
                    val barH = (h * amp).coerceAtLeast(5.dp.toPx())
                    val x = idx * (barW + gap)
                    val top = midY - barH / 2f
                    val isQuotedSide = validSplit != null && frac < validSplit
                    val isFilled = frac <= clampedProgress
                    if (isFilled) {
                        drawRoundRect(
                            brush = if (isQuotedSide) quotedActiveBrush else activeBrush,
                            topLeft = Offset(x, top),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    } else {
                        drawRoundRect(
                            color = if (isQuotedSide) quotedInactiveColor else inactiveColor,
                            topLeft = Offset(x, top),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            WaveformStyle.DOTS -> {
                val step = w / count.coerceAtLeast(1)
                safePeaks.forEachIndexed { idx, rawPeak ->
                    val frac = idx.toFloat() / count.coerceAtLeast(1)
                    val animBoost = if (playing) sin(phase + idx * 0.6f) * 0.14f else 0f
                    val amp = (rawPeak + animBoost).coerceIn(0.15f, 1f)
                    val radius = (3.dp.toPx() + amp * 4.5.dp.toPx()).coerceAtMost(h * 0.34f)
                    val cx = idx * step + step / 2f
                    val cy = midY + sin(idx * 0.5f) * (h * 0.16f * amp)
                    val isFilled = frac <= clampedProgress
                    drawCircle(
                        color = if (isFilled) accentColor else inactiveColor,
                        radius = radius,
                        center = Offset(cx, cy)
                    )
                }
            }

            WaveformStyle.RIBBON -> {
                val step = w / (count - 1).coerceAtLeast(1)
                val fullPath = Path()
                safePeaks.forEachIndexed { idx, rawPeak ->
                    val animBoost = if (playing) sin(phase + idx * 0.45f) * 0.15f else 0f
                    val amp = (rawPeak + animBoost).coerceIn(0.15f, 1f)
                    val x = idx * step
                    val y = midY + (if (idx % 2 == 0) -1f else 1f) * (h * 0.4f * amp)
                    if (idx == 0) fullPath.moveTo(x, y) else fullPath.lineTo(x, y)
                }
                drawPath(
                    path = fullPath,
                    color = inactiveColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                val activeCount = (count * clampedProgress).toInt().coerceIn(1, count)
                if (activeCount > 1) {
                    val activePath = Path()
                    for (idx in 0 until activeCount) {
                        val animBoost = if (playing) sin(phase + idx * 0.45f) * 0.15f else 0f
                        val amp = (safePeaks[idx] + animBoost).coerceIn(0.15f, 1f)
                        val x = idx * step
                        val y = midY + (if (idx % 2 == 0) -1f else 1f) * (h * 0.4f * amp)
                        if (idx == 0) activePath.moveTo(x, y) else activePath.lineTo(x, y)
                    }
                    drawPath(
                        path = activePath,
                        brush = activeBrush,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (validSplit != null) {
            val splitX = w * validSplit
            drawLine(
                color = StudioWhite.copy(alpha = 0.92f),
                start = Offset(splitX, 2.dp.toPx()),
                end = Offset(splitX, h - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = if (clampedProgress >= validSplit) accentColor else quotedAccentColor,
                radius = 4.5.dp.toPx(),
                center = Offset(splitX, 4.5.dp.toPx())
            )
        }
    }
}

/**
 * Spotify-style synchronized transcript box with word-level karaoke highlighting when
 * real word timestamps (`CaptionSegment.words`) are present, plus tap-to-seek on any line.
 */
@Composable
fun SyncedLyricsView(
    segments: List<CaptionSegment>,
    fallbackTranscript: String,
    positionMs: Long,
    isActiveAudio: Boolean,
    isFullStage: Boolean = false,
    accentColor: Color = ElectricCoral,
    onSeekToMs: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveSegments = remember(segments, fallbackTranscript) {
        if (segments.isNotEmpty()) segments
        else if (fallbackTranscript.isNotBlank()) listOf(CaptionSegment(0L, 60_000L, fallbackTranscript))
        else emptyList()
    }

    if (effectiveSegments.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "No spoken captions available for this voice.",
                style = MaterialTheme.typography.bodySmall,
                color = TitaniumMuted
            )
        }
        return
    }

    val activeIndex = remember(effectiveSegments, positionMs, isActiveAudio) {
        if (!isActiveAudio) 0
        else {
            val found = effectiveSegments.indexOfFirst { positionMs in it.startMs..it.endMs }
            if (found >= 0) found
            else effectiveSegments.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex, isActiveAudio) {
        if (isActiveAudio && activeIndex in effectiveSegments.indices) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    val haptics = rememberStudioHaptics()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = if (isFullStage) 24.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (isFullStage) 18.dp else 8.dp)
    ) {
        itemsIndexed(effectiveSegments) { index, segment ->
            val isCurrent = isActiveAudio && index == activeIndex
            val isPast = isActiveAudio && index < activeIndex
            val targetScale = if (isCurrent) 1.03f else 0.98f
            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "lyric_scale"
            )
            val textColor by animateColorAsState(
                targetValue = when {
                    isCurrent -> StudioWhite
                    isPast -> StudioWhite.copy(alpha = 0.56f)
                    !isActiveAudio && index == 0 -> StudioWhite.copy(alpha = 0.92f)
                    else -> TitaniumMuted.copy(alpha = 0.58f)
                },
                animationSpec = tween(220),
                label = "lyric_color"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isCurrent) accentColor.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable {
                        haptics(HapticCue.SCRUB_TICK)
                        onSeekToMs(segment.startMs)
                    }
                    .padding(horizontal = if (isFullStage) 14.dp else 10.dp, vertical = if (isFullStage) 8.dp else 5.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (isFullStage) 28.dp else 18.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(Modifier.width(10.dp))
                }

                if (isCurrent && segment.words.isNotEmpty()) {
                    val wordHighlighted = buildAnnotatedString {
                        segment.words.forEachIndexed { wordIdx, cw ->
                            val isActiveWord = positionMs in cw.startMs..cw.endMs
                            val isSpoken = positionMs > cw.endMs
                            val spanColor = when {
                                isActiveWord -> accentColor
                                isSpoken -> StudioWhite
                                else -> StudioWhite.copy(alpha = 0.45f)
                            }
                            withStyle(
                                SpanStyle(
                                    color = spanColor,
                                    fontWeight = if (isActiveWord) FontWeight.ExtraBold else FontWeight.Bold
                                )
                            ) {
                                append(cw.word)
                            }
                            if (wordIdx < segment.words.lastIndex) append(" ")
                        }
                    }
                    Text(
                        text = wordHighlighted,
                        fontFamily = BodyFontFamily,
                        fontSize = if (isFullStage) 24.sp else 15.sp,
                        lineHeight = if (isFullStage) 32.sp else 20.sp
                    )
                } else {
                    Text(
                        text = segment.text,
                        fontFamily = BodyFontFamily,
                        fontSize = if (isFullStage) (if (isCurrent) 24.sp else 20.sp) else (if (isCurrent) 15.sp else 13.5.sp),
                        lineHeight = if (isFullStage) 32.sp else 20.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun UserAvatarBadge(
    name: String,
    avatarBase64: String? = null,
    accentKey: String = "CORAL",
    anonymousMode: Boolean = false,
    size: Dp = 42.dp,
    showRing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = ProfileAccentPalette.fromKey(accentKey)
    val bitmap = remember(avatarBase64) { ImageCompressor.decodeBase64ToBitmap(avatarBase64) }
    val ringBrush = Brush.linearGradient(
        if (anonymousMode) listOf(StudioViolet, Color(0xFF38BDF8))
        else listOf(palette.primary, palette.secondary)
    )

    Box(
        modifier = modifier
            .size(size)
            .let { m ->
                if (showRing) m.border(2.dp, ringBrush, CircleShape).padding(3.dp)
                else m.border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            }
            .clip(CircleShape)
            .background(
                if (anonymousMode) Brush.linearGradient(listOf(Color(0xFF2B1C4E), Color(0xFF13151D)))
                else Brush.linearGradient(listOf(palette.primary.copy(alpha = 0.35f), ObsidianSurfaceElevated))
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            anonymousMode -> {
                GeometricBurnerAvatar(
                    seed = name.ifBlank { "unmuted_burner" },
                    modifier = Modifier.fillMaxSize()
                )
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "$name avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val initials = name.trim()
                    .split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .take(2)
                    .joinToString("")
                    .ifBlank { "U" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StudioWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
fun GeometricBurnerAvatar(
    seed: String,
    modifier: Modifier = Modifier
) {
    val palettes = remember {
        listOf(
            StudioViolet to SignalCyan,
            ElectricCoral to AmberVinyl,
            SignalMint to StudioViolet,
            SignalCyan to ElectricCoral,
            AmberVinyl to StudioViolet
        )
    }
    val hash = remember(seed) { abs(seed.hashCode()) }
    val (c1, c2) = palettes[hash % palettes.size]

    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF161327), Color(0xFF0E1019))
            )
        )
        val grid = 5
        val pad = size.minDimension * 0.16f
        val cell = (size.minDimension - pad * 2f) / grid
        var bits = hash
        for (row in 0 until grid) {
            for (col in 0..2) {
                val active = (bits and 1) == 1
                bits = (bits ushr 1) xor (row * 31 + col * 17)
                if (active) {
                    val color = if ((row + col) % 2 == 0) c1 else c2
                    val leftX = pad + col * cell
                    val topY = pad + row * cell
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(leftX, topY),
                        size = Size(cell * 0.86f, cell * 0.86f),
                        cornerRadius = CornerRadius(cell * 0.24f, cell * 0.24f)
                    )
                    val mirrorCol = grid - 1 - col
                    if (mirrorCol != col) {
                        val mirrorX = pad + mirrorCol * cell
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(mirrorX, topY),
                            size = Size(cell * 0.86f, cell * 0.86f),
                            cornerRadius = CornerRadius(cell * 0.24f, cell * 0.24f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DisguiseChipRow(
    selected: VoiceDisguise,
    onSelect: (VoiceDisguise) -> Unit,
    allowNatural: Boolean = true,
    modifier: Modifier = Modifier
) {
    val options = remember(allowNatural) {
        if (allowNatural) VoiceDisguise.entries else VoiceDisguise.maskingPresets
    }
    val haptics = rememberStudioHaptics()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.name }) { preset ->
            val active = preset == selected
            FilterChip(
                selected = active,
                onClick = {
                    haptics(HapticCue.CAPTION_TOGGLE)
                    onSelect(preset)
                },
                modifier = Modifier.testTag("disguise_chip_${preset.name.lowercase()}"),
                label = {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = if (active) {
                    {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StudioViolet.copy(alpha = 0.28f),
                    selectedLabelColor = StudioWhite,
                    selectedLeadingIconColor = StudioViolet
                )
            )
        }
    }
}

@Composable
fun DebateRatioBar(
    agreeCount: Long,
    disagreeCount: Long,
    onAgreeClick: (() -> Unit)? = null,
    onDisagreeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val total = (agreeCount + disagreeCount).coerceAtLeast(0L)
    val targetAgreeFrac = if (total == 0L) 0.5f else (agreeCount.toFloat() / total.toFloat()).coerceIn(0.08f, 0.92f)
    val animatedAgreeFrac by animateFloatAsState(
        targetValue = targetAgreeFrac,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "debate_ratio_anim"
    )
    val agreePct = if (total == 0L) 50 else ((agreeCount * 100L) / total).toInt()
    val disagreePct = 100 - agreePct

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceElevated)
            .padding(10.dp)
            .testTag("debate_ratio_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AGREE · $agreePct% ($agreeCount)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SignalMint
            )
            Text(
                text = if (total == 0L) "LIVE DEBATE RATIO" else "$total TAKES",
                style = MaterialTheme.typography.labelSmall,
                color = TitaniumMuted
            )
            Text(
                text = "DISAGREE · $disagreePct% ($disagreeCount)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ElectricCoral
            )
        }

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(animatedAgreeFrac.coerceAtLeast(0.05f))
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(SignalMint, SignalCyan)))
            )
            Box(
                modifier = Modifier
                    .weight((1f - animatedAgreeFrac).coerceAtLeast(0.05f))
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(AmberVinyl, ElectricCoral)))
            )
        }

        if (onAgreeClick != null && onDisagreeClick != null) {
            val haptics = rememberStudioHaptics()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SignalMint.copy(alpha = 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptics(HapticCue.REACTION)
                            onAgreeClick()
                        }
                        .testTag("debate_quick_agree_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ThumbUp, null, tint = SignalMint, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Join Agree Side", style = MaterialTheme.typography.labelSmall, color = SignalMint, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElectricCoral.copy(alpha = 0.14f),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptics(HapticCue.REACTION)
                            onDisagreeClick()
                        }
                        .testTag("debate_quick_disagree_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ThumbDown, null, tint = ElectricCoral, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Join Disagree Side", style = MaterialTheme.typography.labelSmall, color = ElectricCoral, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun ReactiveOrbBackdrop(
    active: Boolean,
    primaryColor: Color = ElectricCoral,
    secondaryColor: Color = StudioViolet,
    amplitude: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orb_transition")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )
    val scale = if (active) pulse * (0.9f + amplitude * 0.25f) else 0.92f

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.42f)
        val baseRadius = size.minDimension * 0.24f * scale
        for (i in 4 downTo 1) {
            val r = baseRadius * (0.65f + i * 0.38f)
            val color = if (i % 2 == 0) primaryColor else secondaryColor
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.16f / i), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
            drawCircle(
                color = color.copy(alpha = if (active) 0.24f / i else 0.08f / i),
                radius = r * 0.85f,
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )
        }
    }
}
