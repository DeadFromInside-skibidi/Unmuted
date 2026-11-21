package com.unmuted.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class HapticCue {
    SCRUB_TICK,
    CAPTION_TOGGLE,
    RECORD_START,
    RECORD_STOP,
    REACTION
}

class StudioHaptics(private val view: View) {
    operator fun invoke(cue: HapticCue) = trigger(cue)

    fun trigger(cue: HapticCue) {
        val flag = when (cue) {
            HapticCue.SCRUB_TICK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            }
            HapticCue.CAPTION_TOGGLE -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.RECORD_START -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
            HapticCue.RECORD_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            HapticCue.REACTION -> HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(flag)
    }
}

@Composable
fun rememberStudioHaptics(): StudioHaptics {
    val view = LocalView.current
    return remember(view) { StudioHaptics(view) }
}
