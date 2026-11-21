package com.unmuted.app.core.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.unmuted.app.data.VoiceDisguise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "AudioDspProcessors"

@Volatile
internal var activeAppContext: Context? = null

fun initAudioContext(context: Context) {
    activeAppContext = context.applicationContext
}

@OptIn(UnstableApi::class)
class StudioVoiceAudioProcessor : BaseAudioProcessor() {
    private var hpPrevSample: Float = 0f
    private var hpPrevInput: Float = 0f
    private var hpAlpha: Float = 0.98f
    private var runningRmsSq: Float = 0.025f
    private var currentGain: Float = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 22050
        val rc = 1.0 / (2.0 * Math.PI * 80.0)
        val dt = 1.0 / sampleRate.toDouble()
        hpAlpha = (rc / (rc + dt)).toFloat().coerceIn(0.85f, 0.999f)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val outputBuffer = replaceOutputBuffer(remaining)

        val targetRms = 0.16f // Conversational target: ~ -16 dBFS RMS
        val maxGain = 3.5f
        val minGain = 0.7f
        val ceiling = 0.891f // Hard ceiling at -1 dBFS

        while (inputBuffer.hasRemaining()) {
            val rawSample = inputBuffer.short
            val floatSample = rawSample.toFloat() / 32768.0f

            // 1. First-order 80 Hz high-pass filter
            val hpOut = hpAlpha * (hpPrevSample + floatSample - hpPrevInput)
            hpPrevInput = floatSample
            hpPrevSample = hpOut

            // 2. Running RMS envelope follower
            val sampleSq = hpOut * hpOut
            runningRmsSq = 0.998f * runningRmsSq + 0.002f * sampleSq
            val estRms = kotlin.math.sqrt(runningRmsSq.toDouble()).toFloat().coerceAtLeast(0.001f)

            // 3. Smooth automatic gain targeting -16 dBFS RMS
            val targetGain = (targetRms / estRms).coerceIn(minGain, maxGain)
            currentGain = 0.9992f * currentGain + 0.0008f * targetGain

            val gained = hpOut * currentGain

            // 4. Soft-knee compression + peak limiter
            val absGained = kotlin.math.abs(gained)
            val processed = if (absGained <= 0.65f) {
                gained
            } else {
                val excess = absGained - 0.65f
                val compressed = 0.65f + 0.241f * kotlin.math.tanh((excess / 0.35f).toDouble()).toFloat()
                (if (gained < 0f) -compressed else compressed).coerceIn(-ceiling, ceiling)
            }

            val outShort = (processed * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            outputBuffer.putShort(outShort)
        }
        outputBuffer.flip()
    }

    override fun onReset() {
        hpPrevSample = 0f
        hpPrevInput = 0f
        runningRmsSq = 0.025f
        currentGain = 1.0f
    }

    override fun onFlush() {
        hpPrevSample = 0f
        hpPrevInput = 0f
        runningRmsSq = 0.025f
        currentGain = 1.0f
    }
}

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
    if (!inputFile.exists() || inputFile.length() == 0L) {
        return inputFile
    }
    val prefix = if (disguise == VoiceDisguise.NONE) "studio_clean_" else "masked_${disguise.name}_"
    if (inputFile.name.startsWith(prefix)) {
        return inputFile
    }

    val audioDir = File(context.cacheDir, "recordings").apply { mkdirs() }
    val outputFile = File(audioDir, "${prefix}${System.currentTimeMillis()}.m4a")

    return withContext(Dispatchers.Main) {
        var bakeSuccess = false
        try {
            val processors = mutableListOf<AudioProcessor>()
            if (disguise != VoiceDisguise.NONE) {
                val sonic = SonicAudioProcessor().apply {
                    setPitch(disguise.pitchFactor)
                    setSpeed(1.0f)
                }
                val dsp = VoiceDisguiseDspAudioProcessor(disguise)
                processors.add(sonic)
                processors.add(dsp)
            }
            // Studio voice cleanup (80Hz HPF + AGC + soft-knee compressor + -1 dBFS peak limiter) runs on ALL takes
            processors.add(StudioVoiceAudioProcessor())

            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
                .setEffects(Effects(processors, emptyList()))
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
                bakeSuccess = true
                if (inputFile.absolutePath != outputFile.absolutePath) {
                    runCatching { inputFile.delete() }
                }
                outputFile
            } else {
                throw IllegalStateException("Baking voice audio produced an empty output file.")
            }
        } catch (e: Exception) {
            if (disguise == VoiceDisguise.NONE) {
                Log.w(TAG, "StudioVoice audio polish failed on device codec, falling back to raw recording", e)
                if (outputFile.exists()) runCatching { outputFile.delete() }
                return@withContext inputFile
            } else {
                throw e
            }
        } finally {
            if (!bakeSuccess) {
                if (outputFile.exists()) {
                    runCatching { outputFile.delete() }
                }
                if (disguise != VoiceDisguise.NONE && inputFile.exists()) {
                    runCatching { inputFile.delete() }
                }
            }
        }
    }
}

suspend fun bakeVoiceDisguise(
    inputFile: File,
    disguise: VoiceDisguise
): File {
    val ctx = activeAppContext ?: throw IllegalStateException("Application context unavailable for voice processing.")
    return bakeVoiceDisguise(ctx, inputFile, disguise)
}
