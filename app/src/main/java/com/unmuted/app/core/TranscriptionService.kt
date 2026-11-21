package com.unmuted.app.core

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.unmuted.app.data.CaptionSegment
import com.unmuted.app.data.CaptionWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

private const val TAG = "TranscriptionService"
private const val STRUCTURED_TRANSCRIBE_MODEL = "gemini-3.8-flash"

data class TranscriptionResult(
    val transcript: String,
    val segments: List<CaptionSegment>
)

/**
 * Generates accurate verbatim transcripts with real phrase-level AND word-level timestamps
 * via Firebase AI Logic structured JSON output so Spotify-style synced lyrics highlight
 * each spoken phrase and word in real time.
 */
class TranscriptionService {
    private val captionResponseSchema: Schema = Schema.obj(
        mapOf(
            "transcript" to Schema.string("Full verbatim spoken transcript with natural punctuation."),
            "segments" to Schema.array(
                items = Schema.obj(
                    mapOf(
                        "startMs" to Schema.integer("Spoken phrase start timestamp in milliseconds from 0."),
                        "endMs" to Schema.integer("Spoken phrase end timestamp in milliseconds."),
                        "text" to Schema.string("Spoken phrase text (3 to 9 words per lyric line)."),
                        "words" to Schema.array(
                            items = Schema.obj(
                                mapOf(
                                    "startMs" to Schema.integer("Word start timestamp in milliseconds."),
                                    "endMs" to Schema.integer("Word end timestamp in milliseconds."),
                                    "word" to Schema.string("Individual spoken word including punctuation.")
                                )
                            ),
                            description = "Word-level timestamps within this phrase."
                        )
                    )
                ),
                description = "Ordered list of spoken caption phrases with millisecond timestamps."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = STRUCTURED_TRANSCRIBE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = captionResponseSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun transcribeAudioFile(
        audioFile: File,
        durationMs: Long
    ): Result<TranscriptionResult> {
        return try {
            val bytes = audioFile.readBytes()
            if (bytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("Recorded audio file is empty."))
            }
            val safeDuration = max(durationMs, 1000L)
            val prompt = content {
                inlineData(bytes = bytes, mimeType = "audio/mp4")
                text(
                    "Transcribe this spoken audio clip (total duration ${safeDuration}ms) verbatim with natural punctuation. " +
                        "Split into concise Spotify-style lyric phrases (3-8 words per phrase) with accurate spoken startMs and endMs timestamps " +
                        "bounded within [0, ${safeDuration}]. For every phrase, also return accurate word-level timestamps in the words array. " +
                        "Ensure timestamps are monotonically non-decreasing."
                )
            }
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text?.trim().orEmpty()
            if (rawJson.isBlank()) {
                return Result.failure(
                    IllegalStateException("Transcription returned empty output. Try speaking closer to the mic.")
                )
            }
            parseStructuredTranscription(rawJson, safeDuration)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            Result.failure(Exception("Transcription timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI timestamped transcription failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Audio transcription failed."))
        }
    }

    private fun parseStructuredTranscription(
        rawJson: String,
        durationMs: Long
    ): Result<TranscriptionResult> {
        return try {
            val cleanJson = rawJson
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val root = JSONObject(cleanJson)
            val transcript = root.optString("transcript", "").trim()
            val segmentsJson = root.optJSONArray("segments") ?: JSONArray()
            val parsedSegments = mutableListOf<CaptionSegment>()
            var lastEndMs = 0L

            for (i in 0 until segmentsJson.length()) {
                val segObj = segmentsJson.optJSONObject(i) ?: continue
                val segText = segObj.optString("text", "").trim()
                if (segText.isBlank()) continue

                val rawStart = segObj.optLong("startMs", lastEndMs).coerceIn(0L, durationMs)
                val segStart = max(rawStart, lastEndMs).coerceAtMost((durationMs - 100L).coerceAtLeast(0L))
                val rawEnd = segObj.optLong("endMs", segStart + 800L).coerceIn(segStart + 120L, durationMs)
                val segEnd = max(rawEnd, segStart + 120L).coerceAtMost(durationMs)

                val wordsJson = segObj.optJSONArray("words")
                val parsedWords = parseWordsForSegment(
                    wordsJson = wordsJson,
                    segmentText = segText,
                    segStartMs = segStart,
                    segEndMs = segEnd
                )

                parsedSegments += CaptionSegment(
                    startMs = segStart,
                    endMs = segEnd,
                    text = segText,
                    words = parsedWords
                )
                lastEndMs = segEnd
            }

            val finalTranscript = transcript.ifBlank {
                parsedSegments.joinToString(" ") { it.text }.trim()
            }
            if (finalTranscript.isBlank()) {
                return Result.failure(
                    IllegalStateException("No speech detected. Try speaking closer to the microphone.")
                )
            }

            val finalSegments = if (parsedSegments.isNotEmpty()) {
                parsedSegments.take(80)
            } else {
                buildSyncedCaptionSegments(finalTranscript, durationMs)
            }
            Result.success(TranscriptionResult(finalTranscript, finalSegments))
        } catch (e: Exception) {
            Log.w(TAG, "Structured JSON parse fallback: ${e.message}")
            val fallbackText = rawJson.trim()
            if (fallbackText.isBlank() || fallbackText.startsWith("{")) {
                Result.failure(Exception("Could not parse timestamped captions. Please retry transcription."))
            } else {
                Result.success(
                    TranscriptionResult(
                        transcript = fallbackText,
                        segments = buildSyncedCaptionSegments(fallbackText, durationMs)
                    )
                )
            }
        }
    }

    private fun parseWordsForSegment(
        wordsJson: JSONArray?,
        segmentText: String,
        segStartMs: Long,
        segEndMs: Long
    ): List<CaptionWord> {
        val result = mutableListOf<CaptionWord>()
        if (wordsJson != null && wordsJson.length() > 0) {
            var wordCursor = segStartMs
            for (j in 0 until wordsJson.length()) {
                val wObj = wordsJson.optJSONObject(j) ?: continue
                val wordText = wObj.optString("word", "").trim()
                if (wordText.isBlank()) continue
                val rawWStart = wObj.optLong("startMs", wordCursor).coerceIn(segStartMs, segEndMs)
                val wStart = max(rawWStart, wordCursor).coerceAtMost(segEndMs)
                val rawWEnd = wObj.optLong("endMs", wStart + 180L).coerceIn(wStart + 40L, segEndMs)
                val wEnd = max(rawWEnd, wStart + 40L).coerceAtMost(segEndMs)
                result += CaptionWord(startMs = wStart, endMs = wEnd, word = wordText)
                wordCursor = wEnd
            }
        }
        return if (result.isNotEmpty()) result else buildWordsForPhrase(segmentText, segStartMs, segEndMs)
    }
}

/**
 * Splits a transcript into expressive Spotify-style lyric phrases AND word-level timing spans
 * (used when the user manually edits transcript text after AI transcription or as a safe fallback).
 */
fun buildSyncedCaptionSegments(transcript: String, durationMs: Long): List<CaptionSegment> {
    val clean = transcript.replace(Regex("\\s+"), " ").trim()
    if (clean.isBlank()) return emptyList()

    val rawClauses = clean
        .split(Regex("(?<=[.!?])\\s+|(?<=,)\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val lyricLines = mutableListOf<String>()
    for (clause in rawClauses) {
        val words = clause.split(" ").filter { it.isNotBlank() }
        if (words.size <= 8) {
            lyricLines.add(clause)
        } else {
            var idx = 0
            while (idx < words.size) {
                val end = (idx + 7).coerceAtMost(words.size)
                lyricLines.add(words.subList(idx, end).joinToString(" "))
                idx = end
            }
        }
    }

    if (lyricLines.isEmpty()) return emptyList()

    val totalWeight = lyricLines.sumOf { max(it.length, 4) }.toDouble()
    val safeDuration = max(durationMs, 1500L)
    var cursorMs = 0L

    return lyricLines.mapIndexed { index, line ->
        val weight = max(line.length, 4).toDouble() / totalWeight
        val start = cursorMs
        val end = if (index == lyricLines.lastIndex) {
            safeDuration
        } else {
            (start + (safeDuration * weight).toLong()).coerceAtMost(safeDuration)
        }
        val clampedEnd = max(end, start + 300L).coerceAtMost(safeDuration)
        cursorMs = clampedEnd
        CaptionSegment(
            startMs = start,
            endMs = clampedEnd,
            text = line,
            words = buildWordsForPhrase(line, start, clampedEnd)
        )
    }
}

private fun buildWordsForPhrase(phrase: String, startMs: Long, endMs: Long): List<CaptionWord> {
    val tokens = phrase.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val span = (endMs - startMs).coerceAtLeast(120L)
    val totalChars = tokens.sumOf { max(it.length, 2) }.toDouble()
    var cursor = startMs
    return tokens.mapIndexed { idx, token ->
        val wStart = cursor
        val wEnd = if (idx == tokens.lastIndex) {
            endMs
        } else {
            val weight = max(token.length, 2).toDouble() / totalChars
            (wStart + (span * weight).toLong()).coerceIn(wStart + 40L, endMs)
        }
        cursor = wEnd
        CaptionWord(startMs = wStart, endMs = wEnd, word = token)
    }
}
