package app.tessera.core.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Lightweight haptic helper backed by the host [View] so taps and long-presses get a
 * consistent, subtle physical response. Uses [HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING]
 * so the feedback fires even when the view's own haptic flag is off.
 */
class Haptics(private val view: View) {
    /** Light tick for ordinary taps (buttons, toggles, chips). */
    fun tick() {
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    /** Slightly stronger pulse for long-press / mode entry. */
    fun longPress() {
        view.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
