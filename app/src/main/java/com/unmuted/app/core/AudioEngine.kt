package com.unmuted.app.core

import android.content.Context
import com.unmuted.app.data.VoiceDisguise
import java.io.File

// Backward compatibility typealiases pointing to com.unmuted.app.core.audio.*
typealias AudioRecorderController = com.unmuted.app.core.audio.AudioRecorderController
typealias AudioPlayerManager = com.unmuted.app.core.audio.AudioPlayerManager
typealias RecordingState = com.unmuted.app.core.audio.RecordingState
typealias PlaybackState = com.unmuted.app.core.audio.PlaybackState
typealias NowPlayingMeta = com.unmuted.app.core.audio.NowPlayingMeta
typealias PlaybackPhase = com.unmuted.app.core.audio.PlaybackPhase
typealias StudioVoiceAudioProcessor = com.unmuted.app.core.audio.StudioVoiceAudioProcessor
typealias VoiceDisguiseDspAudioProcessor = com.unmuted.app.core.audio.VoiceDisguiseDspAudioProcessor

suspend fun bakeVoiceDisguise(
    context: Context? = null,
    inputFile: File,
    disguise: VoiceDisguise
): File = if (context != null) {
    com.unmuted.app.core.audio.bakeVoiceDisguise(context, inputFile, disguise)
} else {
    com.unmuted.app.core.audio.bakeVoiceDisguise(inputFile, disguise)
}

suspend fun bakeVoiceDisguise(
    inputFile: File,
    disguise: VoiceDisguise
): File = com.unmuted.app.core.audio.bakeVoiceDisguise(inputFile, disguise)

fun normalizeWaveform(samples: List<Float>, targetCount: Int = 42): List<Float> =
    com.unmuted.app.core.audio.normalizeWaveform(samples, targetCount)
