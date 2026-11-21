package com.unmuted.app.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.unmuted.app.data.VoiceDisguise
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
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

private const val TAG = "AudioRecorderController"

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
        initAudioContext(context)
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
        com.unmuted.app.core.audio.bakeVoiceDisguise(context, inputFile, disguise)

    fun startRecording(maxDurationMs: Long = 60_000L, onAutoStopped: (File, Long, List<Float>) -> Unit = { _, _, _ -> }) {
        stopAndReleaseInternal()
        maxDurationCapMs = maxDurationMs
        accumulatedMs = 0L
        val audioDir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(audioDir, "unmuted_${System.currentTimeMillis()}.m4a")
        currentFile = file

        try {
            val audioSourceToTry = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )
            var startedRecorder: MediaRecorder? = null
            var lastErr: Exception? = null

            for (src in audioSourceToTry) {
                val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                try {
                    mediaRecorder.apply {
                        setAudioSource(src)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(22050)
                        setAudioEncodingBitRate(32000)
                        setAudioChannels(1)
                        setOutputFile(file.absolutePath)
                        prepare()
                        start()
                    }
                    startedRecorder = mediaRecorder
                    break
                } catch (e: Exception) {
                    lastErr = e
                    runCatching { mediaRecorder.reset() }
                    runCatching { mediaRecorder.release() }
                }
            }

            if (startedRecorder == null) {
                throw lastErr ?: IllegalStateException("Unable to start audio recording.")
            }

            recorder = startedRecorder
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
