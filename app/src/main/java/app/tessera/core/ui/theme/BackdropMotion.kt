package app.tessera.core.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.minDimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How far each of the three blobs follows a gesture (A, B, C): different strength spreads them into a "wave". */
internal val BackdropGains = floatArrayOf(0.35f, 0.25f, 0.15f)

/** Spring stiffness of each blob's return to rest (A, B, C): they settle one after another, ~1.4–1.8 s. */
internal val BackdropReturnStiffness = floatArrayOf(25f, 32f, 40f)

/**
 * Max blob shift, as a fraction of the shorter screen side. Tuned on device: at 0.09 the blobs (faint by design)
 * moved by 2–4 colour levels — below what the eye sees.
 */
internal const val BackdropLimitFraction = 0.2f

/** Damping of the return: below 1 so the blob overshoots slightly and settles. */
internal const val BackdropReturnDamping = 0.6f

/** How far the press pulse pushes each blob (A, B, C) away from the touch point, as a fraction of the limit. */
internal val PulseAmplitude = floatArrayOf(1.0f, 0.8f, 0.6f)

/** Radius scale of the blobs at the top of the press pulse. */
internal const val PulseScale = 1.3f

/** Stiffness of the pulse's fall back to rest: soft enough that a tap on a note still shows it behind the editor. */
internal const val PulseFallStiffness = 20f

/**
 * Damping of the pulse's fall: nearly no overshoot — with the glow a ringing tail stays visible for long — and it ends
 * at [PulseRestThreshold] (a 0.02 pulse is under half a colour level), so a tap is over in ~1.4 s.
 */
internal const val PulseFallDamping = 0.8f
internal const val PulseRestThreshold = 0.02f

/**
 * How much stronger the blobs' tint is at the top of the press pulse. The resting tint is faint by design (10–12 colour
 * levels from the plain background), so moving the blobs alone changes the picture by 4–5 levels at most — invisible;
 * the pulse also brightens them.
 */
internal const val PulseGlow = 2f

/** Draw alpha of a blob brush built at [PulseGlow] × its resting alpha: 1 / [PulseGlow] at rest, 1 at the top of the pulse. */
internal fun pulseGlowAlpha(pulse: Float): Float = (1f + (PulseGlow - 1f) * pulse.coerceIn(0f, 1f)) / PulseGlow

/**
 * [current] moved by [delta] × [gain], clamped to a circle of radius [limitPx] (by vector length, so the
 * direction is kept). A long drag therefore reaches the limit and stays there.
 */
internal fun nudge(current: Offset, delta: Offset, gain: Float, limitPx: Float): Offset {
    val next = current + delta * gain
    val length = next.getDistance()
    return if (length > limitPx && length > 0f) next * (limitPx / length) else next
}

/**
 * Extra shift of a blob resting at [center] while the press pulse is at [pulse]: away from [origin] (the touch
 * point), [pulse] × [amplitude] × [limitPx] long. A blob exactly under the finger does not move.
 */
internal fun pulsePush(center: Offset, origin: Offset, pulse: Float, amplitude: Float, limitPx: Float): Offset {
    val away = center - origin
    val dist = away.getDistance()
    return if (dist > 0f) away * (pulse * amplitude * limitPx / dist) else Offset.Zero
}

/**
 * Motion of the background gradient blobs. Touches push the three blobs along the gesture ([onDrag]),
 * releasing returns them to rest at different speeds ([onRelease] / [onCancel]), and every first press
 * plays a "pulse" away from the touch point ([onPress]). Everything runs on [scope]'s animations; at rest
 * nothing is animating, so nothing redraws.
 *
 * Read [offset], [pulse], [pulseOrigin] and [scale] from a draw lambda — then motion only invalidates drawing, not composition.
 */
class BackdropMotionState internal constructor(private val scope: CoroutineScope) {
    private val offsets = List(3) { Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold) }
    private val pulseAnimatable = Animatable(0f)

    // Accumulated goal of each blob. Synchronous on purpose: Animatable.value lags behind the spring and
    // Animatable.targetValue is only updated after the animation takes the mutex.
    private val targets = Array(3) { Offset.Zero }

    /** Current shift of blob [index] (0 = A, 1 = B, 2 = C) from its resting place, in px. */
    fun offset(index: Int): Offset = offsets[index].value

    /** Press pulse, 0 at rest → 1 → 0. Independent of the gesture offsets; the draw code adds the two. */
    val pulse: Float get() = pulseAnimatable.value

    /** Where the last press landed, in px of the overlay; the pulse pushes the blobs away from it. */
    var pulseOrigin by mutableStateOf(Offset.Zero)
        private set

    /** Current radius scale of all blobs, 1 at rest. */
    val scale: Float get() = 1f + (PulseScale - 1f) * pulse

    /** Total shift of blob [index] resting at [center]: the gesture offset plus the press pulse away from the finger. */
    fun blobShift(index: Int, center: Offset, limitPx: Float): Offset =
        offset(index) + pulsePush(center, pulseOrigin, pulse, PulseAmplitude[index], limitPx)

    // "Remove animations" (durationScale 0) would make every animateTo jump to its goal in one frame —
    // the blobs would twitch. With the scale at 0 the blobs just stay at rest.
    private fun motionAllowed() = (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) != 0f

    /** The pointers moved by [delta] px (mean of their shifts) on a surface of [size] px. */
    fun onDrag(delta: Offset, size: Size) {
        if (!motionAllowed()) return
        val limitPx = BackdropLimitFraction * size.minDimension
        for (i in 0 until 3) {
            targets[i] = nudge(targets[i], delta, BackdropGains[i], limitPx)
            val goal = targets[i]
            scope.launch {
                offsets[i].animateTo(
                    goal,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                        visibilityThreshold = Offset.VisibilityThreshold,
                    ),
                )
            }
        }
    }

    /** All pointers are up: the blobs go back to rest, each at its own pace. */
    fun onRelease() {
        if (!motionAllowed()) return
        for (i in 0 until 3) {
            targets[i] = Offset.Zero
            scope.launch {
                offsets[i].animateTo(
                    Offset.Zero,
                    spring(
                        dampingRatio = BackdropReturnDamping,
                        stiffness = BackdropReturnStiffness[i],
                        visibilityThreshold = Offset.VisibilityThreshold,
                    ),
                )
            }
        }
    }

    /** The system cancelled the gesture: same return to rest. */
    fun onCancel() = onRelease()

    /** The first pointer of a gesture went down at [position]: one pulse away from it (a new press restarts it smoothly). */
    fun onPress(position: Offset) {
        if (!motionAllowed()) return
        pulseOrigin = position
        scope.launch {
            pulseAnimatable.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            pulseAnimatable.animateTo(
                0f,
                spring(dampingRatio = PulseFallDamping, stiffness = PulseFallStiffness, visibilityThreshold = PulseRestThreshold),
            )
        }
    }
}

@Composable
fun rememberBackdropMotionState(): BackdropMotionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackdropMotionState(scope) }
}
