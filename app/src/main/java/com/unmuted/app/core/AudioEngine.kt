package com.unmuted.app.core

import android.content.ComponentName
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.util.concurrent.ListenableFuture
import com.unmuted.app.data.VoiceDisguise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val TAG = "AudioEngine"

@Volatile
private var activeAppContext: Context? = null

@OptIn(UnstableApi::class)
class VoiceDisguiseDspAudioProcessor(private val disguise: VoiceDisguise) : BaseAudioProcessor() {
    private var prevSample: Float = 0f
    private var hpPrevSample: Float = 0f
    private var hpPrevInput: Float = 0f
    private var sampleIndex: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return if (disguise == VoiceDisguise.NONE) {
            AudioProcessor.AudioFormat.NOT_SET
        } else {
            inputAudioFormat
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val outputBuffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {
            val rawSample = inputBuffer.short
            val floatSample = rawSample.toFloat() / 32768.0f
            var processed = floatSample

            when (disguise) {
                VoiceDisguise.DEEP -> {
                    val alpha = 0.65f
                    prevSample = alpha * prevSample + (1f - alpha) * floatSample
                    processed = (prevSample * 1.25f).coerceIn(-1.0f, 1.0f)
                }
                VoiceDisguise.STUDIO_RADIO -> {
                    val hpAlpha = 0.88f
                    val hpOut = hpAlpha * (hpPrevSample + floatSample - hpPrevInput)
                    hpPrevInput = floatSample
                    hpPrevSample = hpOut

                    val lpAlpha = 0.45f
                    prevSample = lpAlpha * prevSample + (1f - lpAlpha) * hpOut
                    processed = (prevSample * 1.4f).coerceIn(-0.95f, 0.95f)
                }
                VoiceDisguise.GLITCH -> {
                    sampleIndex++
                    val modPhase = (sampleIndex % 640) / 640.0f
                    val modGain = if (modPhase < 0.5f) 0.65f else 1.25f
                    val jitter = (kotlin.math.sin(sampleIndex * 0.08) * 0.15f).toFloat()
                    processed = ((floatSample * modGain) + jitter).coerceIn(-1.0f, 1.0f)
                }
                VoiceDisguise.NEUTRAL_SHIFT -> {
                    val alpha = 0.25f
                    prevSample = alpha * prevSample + (1f - alpha) * floatSample
                    processed = (0.7f * prevSample + 0.3f * floatSample).coerceIn(-1.0f, 1.0f)
                }
                VoiceDisguise.NONE -> {
                    processed = floatSample
                }
            }

            val outShort = (processed * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            outputBuffer.putShort(outShort)
        }
        outputBuffer.flip()
    }

    override fun onReset() {
        prevSample = 0f
        hpPrevSample = 0f
        hpPrevInput = 0f
        sampleIndex = 0L
    }

    override fun onFlush() {
        prevSample = 0f
        hpPrevSample = 0f
        hpPrevInput = 0f
        sampleIndex = 0L
    }
}

@OptIn(UnstableApi::class)
suspend fun bakeVoiceDisguise(
    context: Context,
    inputFile: File,
    disguise: VoiceDisguise
): File {
    if (disguise == VoiceDisguise.NONE || !inputFile.exists() || inputFile.length() == 0L) {
        return inputFile
    }
    if (inputFile.name.startsWith("masked_${disguise.name}_")) {
        return inputFile
    }

    val audioDir = File(context.cacheDir, "recordings").apply { mkdirs() }
    val outputFile = File(audioDir, "masked_${disguise.name}_${System.currentTimeMillis()}.m4a")

    return withContext(Dispatchers.Main) {
        val sonic = SonicAudioProcessor().apply {
            setPitch(disguise.pitchFactor)
            setSpeed(1.0f)
        }
        val dsp = VoiceDisguiseDspAudioProcessor(disguise)
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
            .setEffects(Effects(listOf(sonic, dsp), emptyList()))
            .build()

        val transformer = Transformer.Builder(context.applicationContext)
            .build()

        suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }
            transformer.addListener(listener)
            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }
            transformer.start(editedMediaItem, outputFile.absolutePath)
        }

        if (outputFile.exists() && outputFile.length() > 0L) {
            if (inputFile.absolutePath != outputFile.absolutePath) {
                runCatching { inputFile.delete() }
            }
            outputFile
        } else {
            inputFile
        }
    }
}

suspend fun bakeVoiceDisguise(
    inputFile: File,
    disguise: VoiceDisguise
): File {
    val ctx = activeAppContext
    return if (ctx != null) {
        bakeVoiceDisguise(ctx, inputFile, disguise)
    } else {
        inputFile
    }
}

data class RecordingState(
    val recording: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0L,
    val liveAmplitudes: List<Float> = emptyList(),
    val outputFile: File? = null,
    val error: String? = null
)

class AudioRecorderController(private val context: Context) {
    init {
        activeAppContext = context.applicationContext
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recorder: MediaRecorder? = null
    private var tickerJob: Job? = null
    private var currentFile: File? = null
    private var accumulatedMs: Long = 0L
    private var segmentStartMs: Long = 0L
    private var maxDurationCapMs: Long = 60_000L

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    suspend fun bakeVoiceDisguise(inputFile: File, disguise: VoiceDisguise): File =
        com.unmuted.app.core.bakeVoiceDisguise(context, inputFile, disguise)

    fun startRecording(maxDurationMs: Long = 60_000L, onAutoStopped: (File, Long, List<Float>) -> Unit = { _, _, _ -> }) {
        stopAndReleaseInternal()
        maxDurationCapMs = maxDurationMs
        accumulatedMs = 0L
        val audioDir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(audioDir, "unmuted_${System.currentTimeMillis()}.m4a")
        currentFile = file

        try {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(22050)
                setAudioEncodingBitRate(24000)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            segmentStartMs = System.currentTimeMillis()
            _state.value = RecordingState(
                recording = true,
                paused = false,
                elapsedMs = 0L,
                liveAmplitudes = listOf(0.15f),
                outputFile = file
            )
            startTicker(onAutoStopped)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopAndReleaseInternal()
            _state.value = RecordingState(
                recording = false,
                error = e.localizedMessage ?: "Microphone could not start. Check audio permissions."
            )
        }
    }

    fun togglePause() {
        val current = _state.value
        val mr = recorder ?: return
        if (!current.recording) return
        try {
            if (!current.paused) {
                mr.pause()
                accumulatedMs += (System.currentTimeMillis() - segmentStartMs)
                _state.value = current.copy(paused = true, elapsedMs = accumulatedMs)
            } else {
                mr.resume()
                segmentStartMs = System.currentTimeMillis()
                _state.value = current.copy(paused = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pause/resume failed", e)
        }
    }

    fun stopRecording(): Triple<File, Long, List<Float>>? {
        val current = _state.value
        val file = currentFile
        val elapsed = if (current.recording && !current.paused) {
            accumulatedMs + (System.currentTimeMillis() - segmentStartMs)
        } else {
            current.elapsedMs
        }
        val peaks = normalizeWaveform(current.liveAmplitudes, 42)
        stopAndReleaseInternal()
        return if (file != null && file.exists() && file.length() > 0L) {
            _state.value = RecordingState(
                recording = false,
                paused = false,
                elapsedMs = max(elapsed, 400L),
                liveAmplitudes = peaks,
                outputFile = file
            )
            Triple(file, max(elapsed, 400L), peaks)
        } else {
            _state.value = RecordingState(error = "Recording was too short. Hold and speak again.")
            null
        }
    }

    fun reset() {
        stopAndReleaseInternal()
        currentFile?.delete()
        currentFile = null
        _state.value = RecordingState()
    }

    private fun startTicker(onAutoStopped: (File, Long, List<Float>) -> Unit) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(65L)
                val st = _state.value
                if (!st.recording) break
                if (st.paused) continue
                val elapsed = accumulatedMs + (System.currentTimeMillis() - segmentStartMs)
                val rawAmp = try {
                    recorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                val normalized = if (rawAmp <= 1) {
                    0.08f
                } else {
                    min(1f, max(0.1f, (ln(rawAmp.toDouble()) / ln(32767.0)).toFloat()))
                }
                val updatedAmps = (st.liveAmplitudes + normalized).takeLast(96)
                _state.value = st.copy(elapsedMs = elapsed, liveAmplitudes = updatedAmps)
                if (elapsed >= maxDurationCapMs) {
                    val result = stopRecording()
                    if (result != null) {
                        onAutoStopped(result.first, result.second, result.third)
                    }
                    break
                }
            }
        }
    }

    private fun stopAndReleaseInternal() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    fun release() {
        stopAndReleaseInternal()
    }
}

fun normalizeWaveform(samples: List<Float>, targetCount: Int = 42): List<Float> {
    if (samples.isEmpty()) return List(targetCount) { 0.2f }
    if (samples.size <= targetCount) {
        return List(targetCount) { idx ->
            val mapped = (idx.toFloat() / targetCount * samples.size).toInt().coerceIn(0, samples.lastIndex)
            samples[mapped].coerceIn(0.12f, 1.0f)
        }
    }
    val step = samples.size.toFloat() / targetCount
    return List(targetCount) { i ->
        val start = (i * step).toInt().coerceIn(0, samples.lastIndex)
        val end = ((i + 1) * step).toInt().coerceIn(start + 1, samples.size)
        val slice = samples.subList(start, end)
        (slice.maxOrNull() ?: 0.2f).coerceIn(0.12f, 1.0f)
    }
}

enum class PlaybackPhase {
    QUOTED_CLIP,
    MAIN
}

data class NowPlayingMeta(
    val title: String = "Unmuted Voice",
    val speakerName: String = "Unmuted Speaker",
    val subtitle: String = "Voice Take",
    val accentKey: String = "CORAL",
    val anonymousMode: Boolean = false,
    val avatarBase64: String? = null,
    val sourcePostId: String? = null,
    val isClapback: Boolean = false,
    val quotedAuthorName: String? = null
)

data class PlaybackState(
    val activeMediaId: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val voiceDisguise: VoiceDisguise = VoiceDisguise.NONE,
    val activePhase: PlaybackPhase = PlaybackPhase.MAIN,
    val quoteSplitFraction: Float = 0f,
    val combinedPositionMs: Long = 0L,
    val combinedDurationMs: Long = 0L,
    val nowPlaying: NowPlayingMeta? = null,
    val error: String? = null
)

private data class ClapbackSessionMeta(
    val sessionId: String,
    val quoteSpanMs: Long,
    val mainDurationMs: Long,
    val quoteDisguise: VoiceDisguise,
    val mainDisguise: VoiceDisguise
) {
    val totalDurationMs: Long get() = (quoteSpanMs + mainDurationMs).coerceAtLeast(1L)
    val splitFraction: Float get() = (quoteSpanMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0.08f, 0.85f)
}

class AudioPlayerManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerDeferred = CompletableDeferred<MediaController>()
    private var mediaController: MediaController? = null

    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var activeClapback: ClapbackSessionMeta? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val ctrl = mediaController ?: return
            _state.value = _state.value.copy(
                playing = isPlaying,
                buffering = ctrl.playbackState == Player.STATE_BUFFERING
            )
            if (isPlaying) startProgressTicker() else progressJob?.cancel()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = mediaController ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(buffering = true)
                }
                Player.STATE_READY -> {
                    syncStateFromController(ctrl)
                }
                Player.STATE_ENDED -> {
                    progressJob?.cancel()
                    val cb = activeClapback
                    _state.value = _state.value.copy(
                        playing = false,
                        buffering = false,
                        positionMs = 0L,
                        combinedPositionMs = 0L,
                        activePhase = if (cb != null) PlaybackPhase.QUOTED_CLIP else PlaybackPhase.MAIN
                    )
                }
                Player.STATE_IDLE -> {
                    progressJob?.cancel()
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = mediaController ?: return
            syncStateFromController(ctrl)
        }
    }

    init {
        activeAppContext = context.applicationContext
        connectToPlaybackService()
    }

    private fun connectToPlaybackService() {
        try {
            val token = SessionToken(appContext, ComponentName(appContext, UnmutedPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        val ctrl = future.get()
                        mediaController = ctrl
                        ctrl.addListener(playerListener)
                        if (!controllerDeferred.isCompleted) {
                            controllerDeferred.complete(ctrl)
                        }
                        if (ctrl.mediaItemCount > 0) {
                            syncStateFromController(ctrl)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed connecting to UnmutedPlaybackService", e)
                        if (!controllerDeferred.isCompleted) {
                            controllerDeferred.completeExceptionally(e)
                        }
                    }
                },
                ContextCompat.getMainExecutor(appContext)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaController", e)
        }
    }

    private suspend fun awaitController(): MediaController? {
        mediaController?.let { return it }
        return try {
            controllerDeferred.await()
        } catch (_: Exception) {
            controllerDeferred = CompletableDeferred()
            connectToPlaybackService()
            runCatching { controllerDeferred.await() }.getOrNull()
        }
    }

    fun isCached(mediaId: String): Boolean = false

    fun preloadAudioClips(mediaIds: List<String> = emptyList()) {
        // Handled natively by ExoPlayer SimpleCache
    }

    fun togglePlayLocalFile(
        mediaId: String,
        file: File,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        metadata: NowPlayingMeta = NowPlayingMeta(title = "Studio Preview", speakerName = "You", subtitle = disguise.badge)
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == mediaId && ctrl != null && ctrl.mediaItemCount > 0) {
            if (ctrl.isPlaying) pause() else resume()
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            val controller = awaitController() ?: return@launch
            playSingleUriOnController(
                controller = controller,
                mediaId = mediaId,
                uri = Uri.fromFile(file),
                disguise = disguise,
                speed = _state.value.speed,
                metadata = metadata
            )
        }
    }

    fun togglePlayRemote(
        mediaId: String,
        audioUrl: String,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        autoPlay: Boolean = false,
        metadata: NowPlayingMeta = NowPlayingMeta()
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == mediaId && ctrl != null && ctrl.mediaItemCount > 0 && activeClapback == null) {
            if (!autoPlay) {
                if (ctrl.isPlaying) pause() else resume()
            } else if (!ctrl.isPlaying) {
                resume()
            }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            _state.value = current.copy(
                activeMediaId = mediaId,
                buffering = true,
                playing = false,
                positionMs = 0L,
                combinedPositionMs = 0L,
                combinedDurationMs = 0L,
                quoteSplitFraction = 0f,
                activePhase = PlaybackPhase.MAIN,
                voiceDisguise = disguise,
                nowPlaying = metadata,
                error = null
            )
            try {
                val controller = awaitController() ?: return@launch
                val uri = Uri.parse(audioUrl)
                playSingleUriOnController(controller, mediaId, uri, disguise, _state.value.speed, metadata)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remote audio play failed", e)
                _state.value = PlaybackState(speed = _state.value.speed, error = e.localizedMessage ?: "Audio clip could not be played.")
            }
        }
    }

    fun playRangePreview(
        previewId: String,
        audioUrl: String,
        startMs: Long,
        endMs: Long,
        disguise: VoiceDisguise = VoiceDisguise.NONE,
        metadata: NowPlayingMeta = NowPlayingMeta(title = "Clapback Clip Preview", subtitle = "Quoted Slice")
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == previewId && ctrl != null && ctrl.isPlaying) {
            pause()
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            activeClapback = null
            _state.value = current.copy(
                activeMediaId = previewId,
                buffering = true,
                playing = false,
                activePhase = PlaybackPhase.QUOTED_CLIP,
                quoteSplitFraction = 0f,
                voiceDisguise = disguise,
                nowPlaying = metadata,
                error = null
            )
            try {
                val controller = awaitController() ?: return@launch
                val clippedItem = buildMediaItem(
                    mediaId = previewId,
                    uri = Uri.parse(audioUrl),
                    disguise = disguise,
                    speed = _state.value.speed,
                    meta = metadata,
                    clipStartMs = startMs.coerceAtLeast(0L),
                    clipEndMs = endMs.coerceAtLeast(startMs + 500L)
                )
                controller.setMediaItem(clippedItem)
                controller.prepare()
                sendAudioParams(controller, _state.value.speed, disguise)
                controller.play()
                startProgressTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Range preview failed", e)
                _state.value = PlaybackState(speed = _state.value.speed, error = "Could not preview clip range.")
            }
        }
    }

    fun togglePlayClapback(
        sessionId: String,
        quoteAudioUrl: String,
        quoteStartMs: Long,
        quoteEndMs: Long,
        quoteDisguise: VoiceDisguise,
        mainAudioUrl: String,
        mainDurationHintMs: Long,
        mainDisguise: VoiceDisguise,
        autoPlay: Boolean = false,
        metadata: NowPlayingMeta = NowPlayingMeta(isClapback = true)
    ) {
        val current = _state.value
        val ctrl = mediaController
        if (current.activeMediaId == sessionId && ctrl != null && activeClapback?.sessionId == sessionId && ctrl.mediaItemCount >= 2) {
            if (!autoPlay) {
                if (ctrl.isPlaying) pause() else resume()
            } else if (!ctrl.isPlaying) {
                resume()
            }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.value = current.copy(
                activeMediaId = sessionId,
                buffering = true,
                playing = false,
                activePhase = PlaybackPhase.QUOTED_CLIP,
                nowPlaying = metadata,
                error = null
            )
            try {
                val controller = awaitController() ?: return@launch
                val safeStart = quoteStartMs.coerceAtLeast(0L)
                val safeEnd = quoteEndMs.coerceAtLeast(safeStart + 500L)
                val quoteSpan = (safeEnd - safeStart).coerceAtLeast(500L)
                val session = ClapbackSessionMeta(
                    sessionId = sessionId,
                    quoteSpanMs = quoteSpan,
                    mainDurationMs = mainDurationHintMs.coerceAtLeast(1000L),
                    quoteDisguise = quoteDisguise,
                    mainDisguise = mainDisguise
                )
                activeClapback = session

                val quoteMeta = metadata.copy(
                    title = "Quoted: ${metadata.quotedAuthorName ?: "Speaker"}",
                    speakerName = metadata.quotedAuthorName ?: "Quoted Speaker",
                    subtitle = "Clapback Clip"
                )
                val quoteItem = buildMediaItem(
                    mediaId = "${sessionId}_quote",
                    uri = Uri.parse(quoteAudioUrl),
                    disguise = quoteDisguise,
                    speed = _state.value.speed,
                    meta = quoteMeta,
                    clipStartMs = safeStart,
                    clipEndMs = safeEnd
                )
                val mainItem = buildMediaItem(
                    mediaId = "${sessionId}_main",
                    uri = Uri.parse(mainAudioUrl),
                    disguise = mainDisguise,
                    speed = _state.value.speed,
                    meta = metadata
                )

                _state.value = _state.value.copy(
                    activeMediaId = sessionId,
                    activePhase = PlaybackPhase.QUOTED_CLIP,
                    quoteSplitFraction = session.splitFraction,
                    combinedPositionMs = 0L,
                    combinedDurationMs = session.totalDurationMs,
                    voiceDisguise = quoteDisguise,
                    nowPlaying = metadata
                )
                controller.setMediaItems(listOf(quoteItem, mainItem), 0, 0L)
                controller.prepare()
                sendAudioParams(controller, _state.value.speed, quoteDisguise)
                controller.play()
                startProgressTicker()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clapback playback failed", e)
                activeClapback = null
                _state.value = PlaybackState(speed = _state.value.speed, error = "Clapback playback failed.")
            }
        }
    }

    private fun playSingleUriOnController(
        controller: MediaController,
        mediaId: String,
        uri: Uri,
        disguise: VoiceDisguise,
        speed: Float,
        metadata: NowPlayingMeta
    ) {
        val item = buildMediaItem(
            mediaId = mediaId,
            uri = uri,
            disguise = disguise,
            speed = speed,
            meta = metadata
        )
        _state.value = _state.value.copy(
            activeMediaId = mediaId,
            activePhase = PlaybackPhase.MAIN,
            quoteSplitFraction = 0f,
            voiceDisguise = disguise,
            nowPlaying = metadata,
            error = null
        )
        controller.setMediaItem(item)
        controller.prepare()
        sendAudioParams(controller, speed, disguise)
        controller.play()
        startProgressTicker()
    }

    private fun buildMediaItem(
        mediaId: String,
        uri: Uri,
        disguise: VoiceDisguise,
        speed: Float,
        meta: NowPlayingMeta,
        clipStartMs: Long? = null,
        clipEndMs: Long? = null
    ): MediaItem {
        val extras = Bundle().apply {
            putString(PlaybackSessionContract.EXTRA_VOICE_DISGUISE, disguise.name)
            putFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, speed)
            putString(PlaybackSessionContract.EXTRA_SESSION_MEDIA_ID, mediaId)
        }
        val artworkBytes = if (!meta.anonymousMode && !meta.avatarBase64.isNullOrBlank()) {
            runCatching { Base64.decode(meta.avatarBase64, Base64.DEFAULT) }.getOrNull()
        } else null

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(meta.title.ifBlank { "Unmuted Voice" })
            .setArtist(meta.speakerName.ifBlank { "Unmuted Speaker" })
            .setAlbumTitle(meta.subtitle.ifBlank { "Unmuted" })
            .setExtras(extras)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val itemBuilder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())

        if (clipStartMs != null && clipEndMs != null && clipEndMs > clipStartMs) {
            itemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs)
                    .setEndPositionMs(clipEndMs)
                    .build()
            )
        }
        return itemBuilder.build()
    }

    private fun sendAudioParams(controller: MediaController, speed: Float, disguise: VoiceDisguise) {
        val args = Bundle().apply {
            putFloat(PlaybackSessionContract.EXTRA_BASE_SPEED, speed)
            putString(PlaybackSessionContract.EXTRA_VOICE_DISGUISE, disguise.name)
        }
        val cmd = SessionCommand(PlaybackSessionContract.ACTION_APPLY_AUDIO_PARAMS, Bundle.EMPTY)
        try {
            controller.sendCustomCommand(cmd, args)
        } catch (e: Exception) {
            Log.w(TAG, "Custom audio params command failed: ${e.message}")
        }
    }

    fun updateDisguisePreview(disguise: VoiceDisguise) {
        _state.value = _state.value.copy(voiceDisguise = disguise)
        mediaController?.let { ctrl ->
            sendAudioParams(ctrl, _state.value.speed, disguise)
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_state.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _state.value = _state.value.copy(speed = nextSpeed)
        mediaController?.let { ctrl ->
            sendAudioParams(ctrl, nextSpeed, _state.value.voiceDisguise)
        }
    }

    fun seekTo(positionMs: Long) {
        if (activeClapback != null) {
            seekToCombined(positionMs)
            return
        }
        val ctrl = mediaController ?: return
        val dur = ctrl.duration.takeIf { it > 0L } ?: _state.value.durationMs
        val clamped = positionMs.coerceIn(0L, dur.coerceAtLeast(0L))
        ctrl.seekTo(clamped)
        _state.value = _state.value.copy(positionMs = clamped, combinedPositionMs = clamped)
    }

    fun seekToCombined(combinedMs: Long) {
        val cb = activeClapback ?: run {
            seekTo(combinedMs)
            return
        }
        val ctrl = mediaController ?: return
        val clamped = combinedMs.coerceIn(0L, cb.totalDurationMs)
        if (clamped < cb.quoteSpanMs) {
            ctrl.seekTo(0, clamped)
            _state.value = _state.value.copy(
                activePhase = PlaybackPhase.QUOTED_CLIP,
                positionMs = clamped,
                combinedPositionMs = clamped,
                voiceDisguise = cb.quoteDisguise
            )
            sendAudioParams(ctrl, _state.value.speed, cb.quoteDisguise)
        } else {
            val replyOffset = (clamped - cb.quoteSpanMs).coerceAtLeast(0L)
            ctrl.seekTo(1, replyOffset)
            _state.value = _state.value.copy(
                activePhase = PlaybackPhase.MAIN,
                positionMs = replyOffset,
                combinedPositionMs = clamped,
                voiceDisguise = cb.mainDisguise
            )
            sendAudioParams(ctrl, _state.value.speed, cb.mainDisguise)
        }
    }

    fun pause() {
        mediaController?.pause()
        progressJob?.cancel()
        _state.value = _state.value.copy(playing = false)
    }

    fun resume() {
        val ctrl = mediaController ?: return
        val cb = activeClapback
        if (ctrl.playbackState == Player.STATE_ENDED) {
            if (cb != null) {
                ctrl.seekTo(0, 0L)
            } else {
                ctrl.seekTo(0L)
            }
        }
        ctrl.play()
        _state.value = _state.value.copy(playing = true)
        startProgressTicker()
    }

    fun stop() {
        loadJob?.cancel()
        progressJob?.cancel()
        activeClapback = null
        mediaController?.let { ctrl ->
            ctrl.stop()
            ctrl.clearMediaItems()
        }
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val ctrl = mediaController ?: break
                syncStateFromController(ctrl)
                if (!ctrl.isPlaying && ctrl.playbackState != Player.STATE_BUFFERING) break
                delay(70L)
            }
        }
    }

    private fun syncStateFromController(ctrl: MediaController) {
        val pos = ctrl.currentPosition.coerceAtLeast(0L)
        val rawDur = ctrl.duration.takeIf { it > 0L } ?: _state.value.durationMs.coerceAtLeast(1L)
        val cb = activeClapback
        if (cb != null) {
            val idx = ctrl.currentMediaItemIndex
            val phase = if (idx == 0) PlaybackPhase.QUOTED_CLIP else PlaybackPhase.MAIN
            val actualMainDur = if (idx == 1 && ctrl.duration > 0L) ctrl.duration else cb.mainDurationMs
            val totalDur = (cb.quoteSpanMs + actualMainDur).coerceAtLeast(1L)
            val combinedPos = if (idx == 0) {
                pos.coerceIn(0L, cb.quoteSpanMs)
            } else {
                (cb.quoteSpanMs + pos).coerceIn(cb.quoteSpanMs, totalDur)
            }
            val split = (cb.quoteSpanMs.toFloat() / totalDur.toFloat()).coerceIn(0.08f, 0.85f)
            val phaseDisguise = if (idx == 0) cb.quoteDisguise else cb.mainDisguise
            _state.value = _state.value.copy(
                playing = ctrl.isPlaying,
                buffering = ctrl.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = rawDur,
                activePhase = phase,
                quoteSplitFraction = split,
                combinedPositionMs = combinedPos,
                combinedDurationMs = totalDur,
                voiceDisguise = phaseDisguise
            )
        } else {
            _state.value = _state.value.copy(
                playing = ctrl.isPlaying,
                buffering = ctrl.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = rawDur,
                combinedPositionMs = pos,
                combinedDurationMs = rawDur,
                activePhase = PlaybackPhase.MAIN
            )
        }
    }

    fun release() {
        loadJob?.cancel()
        progressJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
