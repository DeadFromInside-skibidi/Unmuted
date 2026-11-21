package com.unmuted.app.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.view.Surface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.unmuted.app.R
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.VoiceDisguise
import com.unmuted.app.data.VoicePost
import com.unmuted.app.ui.theme.ProfileAccentPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.sin

private const val VIDEO_WIDTH = 720
private const val VIDEO_HEIGHT = 1280
private const val VIDEO_FPS = 30
private const val VIDEO_BITRATE = 3_500_000
private const val I_FRAME_INTERVAL = 2
private const val MAX_DURATION_US = 60_000_000L // 60 seconds max

private data class PendingVideoFrame(
    val buffer: ByteBuffer,
    val offset: Int,
    val size: Int,
    val flags: Int
)

/**
 * High-performance on-device 9:16 vertical video exporter (720x1280 @ 30fps MP4)
 * with synced karaoke lyrics, live animated waveform, speaker branding, and zero-re-encoding audio.
 */
suspend fun exportAudiogram(
    context: Context,
    post: VoicePost,
    onProgress: (Float) -> Unit
): File = withContext(Dispatchers.Default) {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    pruneOldExports(exportDir)

    val resolvedAudioFile = resolveAudioSourceFile(context, post)
    val isTempAudioFile = resolvedAudioFile.name.startsWith("temp_source_")
    val outputFile = File(exportDir, "unmuted_${post.id}_${System.currentTimeMillis()}.mp4")

    var extractor: MediaExtractor? = null
    var videoCodec: MediaCodec? = null
    var inputSurface: Surface? = null
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    var exportSuccess = false

    try {
        // 1. Setup Audio Track Extraction
        extractor = MediaExtractor().apply {
            setDataSource(resolvedAudioFile.absolutePath)
        }
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        if (audioTrackIndex == -1 || audioFormat == null) {
            throw IllegalStateException("No valid audio track found in recording.")
        }
        extractor.selectTrack(audioTrackIndex)

        val audioDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
            audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            post.durationMs * 1000L
        }
        val exportDurationUs = minOf(audioDurationUs, MAX_DURATION_US).coerceAtLeast(1_000_000L)
        val totalFrames = ((exportDurationUs * VIDEO_FPS.toLong()) / 1_000_000L).toInt().coerceAtLeast(30)
        val durationMs = exportDurationUs / 1000L

        // 2. Setup Video MediaCodec Encoder (H.264 COLOR_FormatSurface)
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
        }
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoCodec.createInputSurface()
        videoCodec.start()

        // 3. Setup MediaMuxer
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerAudioTrack = muxer.addTrack(audioFormat)
        var muxerVideoTrack = -1

        // 4. Preload Fonts & Graphic Assets
        val spaceGrotesk = ResourcesCompat.getFont(context, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD
        val plusJakartaSans = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
        val jetbrainsMono = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE

        val accentPalette = ProfileAccentPalette.fromKey(post.authorAccentKey)
        val accentColor = if (post.anonymousMode) 0xFF8B5CF6.toInt() else accentPalette.primary.run {
            Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
        }

        val renderer = AudiogramFrameRenderer(
            spaceGrotesk = spaceGrotesk,
            plusJakartaSans = plusJakartaSans,
            jetbrainsMono = jetbrainsMono,
            accentColor = accentColor,
            post = post,
            durationMs = durationMs
        )

        val videoBufferInfo = MediaCodec.BufferInfo()
        val audioBuffer = ByteBuffer.allocateDirect(128 * 1024)
        val audioBufferInfo = MediaCodec.BufferInfo()
        var encodedVideoFrames = 0L
        val pendingVideoFrames = mutableListOf<PendingVideoFrame>()

        fun drainVideoCodec(endOfStream: Boolean) {
            val timeoutUs = if (endOfStream) 10_000L else 0L
            while (true) {
                val status = videoCodec.dequeueOutputBuffer(videoBufferInfo, timeoutUs)
                if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break else break
                } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw IllegalStateException("Video format changed after muxer started")
                    val newFormat = videoCodec.outputFormat
                    muxerVideoTrack = muxer.addTrack(newFormat)
                    if (muxerAudioTrack != -1 && muxerVideoTrack != -1) {
                        muxer.start()
                        muxerStarted = true
                        for (pending in pendingVideoFrames) {
                            val pInfo = MediaCodec.BufferInfo().apply {
                                set(
                                    pending.offset,
                                    pending.size,
                                    (encodedVideoFrames++ * 1_000_000L) / VIDEO_FPS.toLong(),
                                    pending.flags
                                )
                            }
                            muxer.writeSampleData(muxerVideoTrack, pending.buffer, pInfo)
                        }
                        pendingVideoFrames.clear()
                    }
                } else if (status >= 0) {
                    val encodedData = videoCodec.getOutputBuffer(status)
                        ?: throw IllegalStateException("OutputBuffer $status was null")

                    if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        videoBufferInfo.size = 0
                    }

                    if (videoBufferInfo.size > 0) {
                        if (muxerStarted) {
                            encodedData.position(videoBufferInfo.offset)
                            encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                            videoBufferInfo.presentationTimeUs = (encodedVideoFrames++ * 1_000_000L) / VIDEO_FPS.toLong()
                            muxer.writeSampleData(muxerVideoTrack, encodedData, videoBufferInfo)
                        } else {
                            val copy = ByteBuffer.allocateDirect(videoBufferInfo.size)
                            encodedData.position(videoBufferInfo.offset)
                            encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                            copy.put(encodedData)
                            copy.flip()
                            pendingVideoFrames.add(PendingVideoFrame(copy, 0, videoBufferInfo.size, videoBufferInfo.flags))
                        }
                    }

                    videoCodec.releaseOutputBuffer(status, false)
                    if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }

        fun pumpAudioUpTo(targetTimeUs: Long) {
            if (!muxerStarted) return
            while (true) {
                val sampleSize = extractor.readSampleData(audioBuffer, 0)
                if (sampleSize < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime > exportDurationUs) break
                if (sampleTime > targetTimeUs) break

                val flags = extractor.sampleFlags
                audioBufferInfo.set(0, sampleSize, sampleTime, flags)
                muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                extractor.advance()
            }
        }

        // 5. Render Video Frames
        for (frameIndex in 0 until totalFrames) {
            coroutineContext.ensureActive()

            val presentationTimeUs = (frameIndex * 1_000_000L) / VIDEO_FPS.toLong()
            val currentPosMs = presentationTimeUs / 1000L

            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    inputSurface.lockHardwareCanvas()
                } catch (_: Exception) {
                    inputSurface.lockCanvas(null)
                }
            } else {
                inputSurface.lockCanvas(null)
            }

            try {
                renderer.renderFrame(canvas, currentPosMs, frameIndex)
            } finally {
                inputSurface.unlockCanvasAndPost(canvas)
            }

            drainVideoCodec(endOfStream = false)
            pumpAudioUpTo(presentationTimeUs)

            if (frameIndex % 3 == 0 || frameIndex == totalFrames - 1) {
                onProgress(frameIndex.toFloat() / totalFrames)
            }
        }

        // 6. Signal End-of-Stream & Final Drain
        videoCodec.signalEndOfInputStream()
        var eosPolls = 0
        while ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0 && eosPolls < 60) {
            drainVideoCodec(endOfStream = true)
            eosPolls++
        }
        pumpAudioUpTo(exportDurationUs)

        onProgress(1.0f)
        exportSuccess = true
        outputFile
    } finally {
        runCatching { inputSurface?.release() }
        runCatching { videoCodec?.stop() }
        runCatching { videoCodec?.release() }
        runCatching { extractor?.release() }
        if (muxerStarted) {
            runCatching { muxer?.stop() }
        }
        runCatching { muxer?.release() }

        if (isTempAudioFile) {
            runCatching { resolvedAudioFile.delete() }
        }
        if (!exportSuccess && outputFile.exists()) {
            runCatching { outputFile.delete() }
        }
    }
}

/**
 * Handles Canvas drawing operations for vertical 9:16 audiogram video frames.
 */
private class AudiogramFrameRenderer(
    private val spaceGrotesk: Typeface,
    private val plusJakartaSans: Typeface,
    private val jetbrainsMono: Typeface,
    private val accentColor: Int,
    private val post: VoicePost,
    private val durationMs: Long
) {
    private val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, VIDEO_HEIGHT.toFloat(),
            intArrayOf(0xFF090B0F.toInt(), 0xFF141722.toInt(), 0xFF090B0F.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val glowColor = Color.argb(45, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        shader = RadialGradient(
            VIDEO_WIDTH / 2f, 480f, 460f,
            intArrayOf(glowColor, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B1E28.toInt()
    }

    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x33FFFFFF
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = spaceGrotesk
        textSize = 19f
        color = accentColor
    }

    private val maskBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x338B5CF6
    }

    private val maskTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = spaceGrotesk
        textSize = 18f
        color = 0xFFC084FC.toInt()
    }

    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
    }

    private val avatarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = spaceGrotesk
        textSize = 28f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val speakerNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = plusJakartaSans
        textSize = 28f
        color = 0xFFF8FAFC.toInt()
        isFakeBoldText = true
    }

    private val speakerHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = plusJakartaSans
        textSize = 18f
        color = 0xFF94A3B8.toInt()
    }

    private val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = spaceGrotesk
        textSize = 26f
        color = 0xFFFFFFFF.toInt()
        isFakeBoldText = true
    }

    private val activeCaptionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = plusJakartaSans
        textSize = 34f
        color = 0xFFFFFFFF.toInt()
        isFakeBoldText = true
    }

    private val inactiveCaptionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = plusJakartaSans
        textSize = 22f
        color = 0x66CBD5E1
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = jetbrainsMono
        textSize = 22f
        color = 0xFF9BA3B4.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f
        color = 0x22FFFFFF
    }

    private val brandTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = spaceGrotesk
        textSize = 26f
        color = 0xFFF1F5F9.toInt()
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.18f
    }

    private val brandSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = plusJakartaSans
        textSize = 15f
        color = 0xFF64748B.toInt()
        textAlign = Paint.Align.CENTER
    }

    private val resampledPeaks = if (post.waveformPeaks.isNotEmpty()) {
        resamplePeaks(post.waveformPeaks.map { it.toFloat() }, 44)
    } else {
        List(44) { 0.35f }
    }

    fun renderFrame(canvas: Canvas, currentPosMs: Long, frameIndex: Int) {
        // 1. Studio Backdrop & Glow
        canvas.drawRect(0f, 0f, VIDEO_WIDTH.toFloat(), VIDEO_HEIGHT.toFloat(), bgPaint)
        canvas.drawCircle(VIDEO_WIDTH / 2f, 480f, 460f, glowPaint)

        // 2. Top Pill Row
        val roomTag = if (!post.roomSlug.isNullOrBlank()) "r/${post.roomSlug}" else "UNMUTED"
        val roomPillWidth = pillTextPaint.measureText(roomTag) + 36f
        val roomRect = RectF(48f, 72f, 48f + roomPillWidth, 120f)
        canvas.drawRoundRect(roomRect, 24f, 24f, pillBgPaint)
        canvas.drawRoundRect(roomRect, 24f, 24f, pillBorderPaint)
        canvas.drawText(roomTag, 66f, 103f, pillTextPaint)

        if (post.anonymousMode || post.voiceDisguise != VoiceDisguise.NONE.name) {
            val maskTag = "🎭 Masked Voice"
            val maskWidth = maskTextPaint.measureText(maskTag) + 32f
            val maskRect = RectF(VIDEO_WIDTH - 48f - maskWidth, 72f, VIDEO_WIDTH - 48f, 120f)
            canvas.drawRoundRect(maskRect, 24f, 24f, maskBadgePaint)
            canvas.drawText(maskTag, VIDEO_WIDTH - 48f - maskWidth + 16f, 103f, maskTextPaint)
        }

        // 3. Speaker Profile Header
        val avatarCenterX = 82f
        val avatarCenterY = 196f
        canvas.drawCircle(avatarCenterX, avatarCenterY, 34f, avatarPaint)
        val initial = post.displaySpeakerName().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        canvas.drawText(initial, avatarCenterX, avatarCenterY + 10f, avatarTextPaint)

        canvas.drawText(post.displaySpeakerName(), 134f, 192f, speakerNamePaint)
        canvas.drawText(post.displaySpeakerHandle(), 134f, 218f, speakerHandlePaint)

        var headerBottomY = 246f
        val headline = post.headlineText?.trim()
        if (!headline.isNullOrEmpty()) {
            val headlineLayout = StaticLayout.Builder.obtain(headline, 0, headline.length, headlinePaint, 624)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(48f, 252f)
            headlineLayout.draw(canvas)
            canvas.restore()
            headerBottomY = 252f + headlineLayout.height + 16f
        }

        // 4. Center Karaoke Lyrics / Captions
        val segments = post.captionSegments
        val captionAreaTop = maxOf(headerBottomY + 36f, 420f)

        if (segments.isNotEmpty()) {
            val activeIndex = segments.indexOfFirst { currentPosMs in it.startMs..it.endMs }
            val effectiveIndex = if (activeIndex != -1) {
                activeIndex
            } else {
                segments.indexOfLast { currentPosMs >= it.endMs }.coerceAtLeast(0)
            }

            var currentDrawY = captionAreaTop

            // Previous Segment (Dimmed)
            if (effectiveIndex > 0) {
                val prevSeg = segments[effectiveIndex - 1]
                val prevLayout = StaticLayout.Builder.obtain(prevSeg.text, 0, prevSeg.text.length, inactiveCaptionPaint, 624)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()
                canvas.save()
                canvas.translate(48f, currentDrawY)
                prevLayout.draw(canvas)
                canvas.restore()
                currentDrawY += prevLayout.height + 20f
            } else {
                currentDrawY += 40f
            }

            // Active Segment (Bold + Word Highlight)
            val activeSeg = segments.getOrNull(effectiveIndex)
            if (activeSeg != null) {
                val ssb = SpannableStringBuilder()
                if (activeSeg.words.isNotEmpty()) {
                    for (wordObj in activeSeg.words) {
                        val start = ssb.length
                        ssb.append(wordObj.word).append(" ")
                        val end = ssb.length - 1
                        val isSpoken = currentPosMs >= wordObj.startMs
                        val spanColor = if (isSpoken) accentColor else Color.WHITE
                        ssb.setSpan(ForegroundColorSpan(spanColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else {
                    ssb.append(activeSeg.text)
                    ssb.setSpan(ForegroundColorSpan(Color.WHITE), 0, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val activeLayout = StaticLayout.Builder.obtain(ssb, 0, ssb.length, activeCaptionPaint, 624)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()
                canvas.save()
                canvas.translate(48f, currentDrawY)
                activeLayout.draw(canvas)
                canvas.restore()
                currentDrawY += activeLayout.height + 20f
            }

            // Next Segment (Dimmed)
            if (effectiveIndex + 1 < segments.size) {
                val nextSeg = segments[effectiveIndex + 1]
                val nextLayout = StaticLayout.Builder.obtain(nextSeg.text, 0, nextSeg.text.length, inactiveCaptionPaint, 624)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()
                canvas.save()
                canvas.translate(48f, currentDrawY)
                nextLayout.draw(canvas)
                canvas.restore()
            }
        } else {
            // Fallback cleanly wrapped transcript
            val transcriptFallback = post.transcript.trim().ifEmpty { "Voice take on Unmuted." }
            val fallbackLayout = StaticLayout.Builder.obtain(transcriptFallback.take(240), 0, minOf(transcriptFallback.length, 240), activeCaptionPaint, 624)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            canvas.save()
            canvas.translate(48f, captionAreaTop + 60f)
            fallbackLayout.draw(canvas)
            canvas.restore()
        }

        // 5. Bottom Animated Waveform
        val barCount = 44
        val barWidth = 8f
        val barSpacing = 6f
        val waveformTotalWidth = barCount * (barWidth + barSpacing) - barSpacing
        val waveformStartX = (VIDEO_WIDTH - waveformTotalWidth) / 2f
        val waveformCenterY = 940f
        val progress = (currentPosMs.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        val activeBarIndex = (progress * barCount).toInt().coerceIn(0, barCount - 1)

        for (i in 0 until barCount) {
            val peak = resampledPeaks.getOrElse(i) { 0.35f }
            var barHeight = (peak * 115f).coerceIn(16f, 120f)
            val barX = waveformStartX + i * (barWidth + barSpacing)

            if (i == activeBarIndex) {
                barHeight *= (1.0f + 0.22f * sin(frameIndex * 0.45f))
            }

            val top = waveformCenterY - barHeight / 2f
            val bottom = waveformCenterY + barHeight / 2f

            if (i <= activeBarIndex) {
                barPaint.color = accentColor
            } else {
                barPaint.color = 0x33CBD5E1
            }

            canvas.drawRoundRect(RectF(barX, top, barX + barWidth, bottom), 4f, 4f, barPaint)
        }

        // Timer Display (MM:SS / MM:SS in JetBrains Mono)
        val timerString = "${formatTime(currentPosMs)} / ${formatTime(durationMs)}"
        canvas.drawText(timerString, VIDEO_WIDTH / 2f, 1038f, timerPaint)

        // 6. Footer Watermark Branding
        canvas.drawLine(200f, 1120f, 520f, 1120f, dividerPaint)
        canvas.drawText("UNMUTED", VIDEO_WIDTH / 2f, 1165f, brandTitlePaint)
        canvas.drawText("Hear the full take on Unmuted", VIDEO_WIDTH / 2f, 1198f, brandSubtitlePaint)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        return String.format("%02d:%02d", mins, secs)
    }

    private fun resamplePeaks(source: List<Float>, targetCount: Int): List<Float> {
        if (source.isEmpty() || targetCount <= 0) return emptyList()
        if (source.size == targetCount) return source
        return List(targetCount) { idx ->
            val pos = idx.toFloat() / targetCount.coerceAtLeast(1) * source.size
            source[pos.toInt().coerceIn(0, source.lastIndex)].coerceIn(0.12f, 1f)
        }
    }
}

/**
 * Resolves the audio source locally or downloads from network into cacheDir/exports/.
 */
private suspend fun resolveAudioSourceFile(context: Context, post: VoicePost): File = withContext(Dispatchers.IO) {
    val audioUrl = post.effectiveAudioUrl()
    if (audioUrl.isNullOrBlank()) {
        throw IllegalArgumentException("Voice post has no valid audio URL.")
    }

    if (audioUrl.startsWith("file://")) {
        val f = File(audioUrl.removePrefix("file://"))
        if (f.exists() && f.length() > 0L) return@withContext f
    }
    val directFile = File(audioUrl)
    if (directFile.exists() && directFile.length() > 0L) {
        return@withContext directFile
    }

    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val tempFile = File(exportDir, "temp_source_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.m4a")

    val url = URL(audioUrl)
    val conn = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }
    conn.connect()
    if (conn.responseCode !in 200..299) {
        throw IOException("Failed to download audio file: HTTP ${conn.responseCode}")
    }
    conn.inputStream.use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    if (!tempFile.exists() || tempFile.length() == 0L) {
        throw IOException("Downloaded audio track is empty.")
    }
    tempFile
}

/**
 * Prunes exported videos older than 1 hour to prevent cache bloat.
 */
private fun pruneOldExports(exportDir: File) {
    runCatching {
        val oneHourAgo = System.currentTimeMillis() - 60L * 60L * 1000L
        exportDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".mp4") && file.lastModified() < oneHourAgo) {
                file.delete()
            }
        }
    }
}

/**
 * Triggers safe Android sharing of the generated 9:16 vertical MP4 video via FileProvider.
 */
fun shareAudiogramVideo(context: Context, mp4File: File, post: VoicePost) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        mp4File
    )
    val speaker = post.displaySpeakerName()
    val headline = post.headlineText?.takeIf { it.isNotBlank() } ?: post.transcript.take(80)
    val shareText = "Listen to \"$headline\" by $speaker on Unmuted\n#Unmuted #VoiceSocial"

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Audiogram Video", uri)
    }
    val chooser = Intent.createChooser(intent, "Share Voice Audiogram").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

/**
 * Triggers quick text and link sharing for a voice post.
 */
fun sharePostText(context: Context, post: VoicePost) {
    val speaker = post.displaySpeakerName()
    val headline = post.headlineText?.takeIf { it.isNotBlank() } ?: post.transcript.take(120)
    val text = "“$headline” — $speaker on Unmuted\nJoin the voice conversation: https://unmuted.app/p/${post.id}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, "Share Voice Post").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
