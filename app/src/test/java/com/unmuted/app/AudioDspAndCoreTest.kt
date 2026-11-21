package com.unmuted.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.unmuted.app.core.audio.StudioVoiceAudioProcessor
import com.unmuted.app.core.buildSyncedCaptionSegments
import com.unmuted.app.data.buildPostSearchTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioDspAndCoreTest {

    @Test
    fun studioVoiceAudioProcessor_pcmOutputStaysStrictlyWithinMinusOneDbFs() {
        val processor = StudioVoiceAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Generate 1000 samples of a heavily clipped high-amplitude square/sine wave
        val sampleCount = 1000
        val inputBuffer = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until sampleCount) {
            val sample = if (i % 2 == 0) 32767.toShort() else (-32768).toShort()
            inputBuffer.putShort(sample)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output
        assertTrue("Output buffer should contain processed samples", outputBuffer.hasRemaining())

        val maxAllowedSample = 29200 // -1 dBFS ceiling (~0.891 * 32767 = 29195)
        var maxObserved = 0
        while (outputBuffer.hasRemaining()) {
            val outSample = abs(outputBuffer.short.toInt())
            if (outSample > maxObserved) {
                maxObserved = outSample
            }
            assertTrue(
                "Peak sample $outSample exceeded -1 dBFS limit ($maxAllowedSample)",
                outSample <= maxAllowedSample
            )
        }
        assertTrue("Processor produced non-zero output", maxObserved > 0)
    }

    @Test
    fun buildSyncedCaptionSegments_producesMonotonicNonOverlappingTimestampsBoundedByDuration() {
        val transcript = "Unmuted brings raw unfiltered voice to social discourse. Every take is preserved with verbatim precision. Listen without borders."
        val durationMs = 12000L

        val segments = buildSyncedCaptionSegments(transcript, durationMs)
        assertFalse("Segments should not be empty", segments.isEmpty())

        var lastEndMs = 0L
        for ((index, segment) in segments.withIndex()) {
            assertTrue("Segment $index startMs (${segment.startMs}) should be >= 0", segment.startMs >= 0L)
            assertTrue("Segment $index startMs (${segment.startMs}) <= endMs (${segment.endMs})", segment.startMs <= segment.endMs)
            assertTrue("Segment $index startMs (${segment.startMs}) should be >= previous endMs ($lastEndMs)", segment.startMs >= lastEndMs)
            assertTrue("Segment $index endMs (${segment.endMs}) should be <= durationMs ($durationMs)", segment.endMs <= durationMs)

            assertFalse("Segment $index words should not be empty", segment.words.isEmpty())
            var wordLastEnd = segment.startMs
            for ((wIdx, word) in segment.words.withIndex()) {
                assertTrue("Word $wIdx in seg $index startMs (${word.startMs}) <= endMs (${word.endMs})", word.startMs <= word.endMs)
                assertTrue("Word $wIdx startMs (${word.startMs}) should be >= wordLastEnd ($wordLastEnd)", word.startMs >= wordLastEnd)
                assertTrue("Word $wIdx endMs (${word.endMs}) should be <= seg endMs (${segment.endMs})", word.endMs <= segment.endMs)
                wordLastEnd = word.endMs
            }
            lastEndMs = segment.endMs
        }
    }

    @Test
    fun buildPostSearchTokens_stripsPunctuationLowercasesDeduplicatesAndCapsAt30() {
        val tokens = buildPostSearchTokens(
            headlineText = "Breaking: Revolutionary Audio Engine 2.0 Released! #audio #unmuted",
            hashtags = listOf("#voice", "#Unmuted", "#trending"),
            roomName = "Tech & AI Debates",
            roomSlug = "tech-ai",
            transcript = "The new transformer pipeline ensures lossless audio processing with studio loudness normalization and peak limiting. Every single voice take stays authentic and unfiltered without noise."
        )

        assertFalse("Tokens should not be empty", tokens.isEmpty())
        assertTrue("Tokens count must be capped at 30", tokens.size <= 30)

        // Verify deduplication
        val tokenSet = tokens.toSet()
        assertEquals("Tokens must be unique (deduplicated)", tokenSet.size, tokens.size)

        // Verify lowercase and stripped punctuation
        for (token in tokens) {
            assertEquals("Token '$token' must be lowercase", token.lowercase(), token)
            assertTrue("Token '$token' must not start with punctuation", token.first().isLetterOrDigit())
            assertTrue("Token '$token' must not end with punctuation", token.last().isLetterOrDigit())
            assertFalse("Token '$token' must not be a stop word", token in setOf("the", "and", "with", "this", "that"))
        }

        // Verify expected tokens are present
        assertTrue("Should contain 'revolutionary'", "revolutionary" in tokens)
        assertTrue("Should contain 'unmuted'", "unmuted" in tokens)
        assertTrue("Should contain 'debates'", "debates" in tokens)
    }
}
