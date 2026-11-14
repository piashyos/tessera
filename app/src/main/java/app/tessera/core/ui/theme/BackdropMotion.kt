package app.tessera.core.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.minDimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tanh

/** How far each of the three blobs follows a gesture (A, B, C): different strength spreads them into a "wave". */
internal val BackdropGains = floatArrayOf(0.2f, 0.15f, 0.1f)

/** Direction of each blob's shift relative to the gesture, in degrees: the blobs fan out instead of moving as one. */
internal val BackdropAngles = floatArrayOf(0f, 120f, -120f)

/**
 * Max blob shift, as a fraction of the shorter screen side. The shift saturates smoothly ([softLimit]) towards it, so an
 * ordinary scroll stays well inside and leaves room for the fling.
 */
internal const val BackdropLimitFraction = 0.12f

/** The blobs' return to rest after a gesture: critically damped (no overshoot), ~2.3 s from the limit. */
internal const val BackdropReturnDamping = 1f
internal const val BackdropReturnStiffness = 12f

/** Fling after a swipe: exponential decay of the blob's velocity, over in about a second. */
internal const val FlingFriction = 1.6f
internal const val FlingVelocityThreshold = 2f
internal val FlingDecay: DecayAnimationSpec<Offset> = exponentialDecay(FlingFriction, FlingVelocityThreshold)

/** Radius scale of the blobs at the top of the tap "breath". */
internal const val PulseScale = 1.12f

/** Stiffness of the pulse's fall back to rest: soft enough that a tap on a note still shows it behind the editor. */
internal const val PulseFallStiffness = 20f

/**
 * Damping of the pulse's fall: nearly no overshoot — with the glow a ringing tail stays visible for long — and it ends
 * at [PulseRestThreshold] (a 0.02 pulse is under half a colour level), so a tap is over in ~1.4 s.
 */
internal const val PulseFallDamping = 0.8f
internal const val PulseRestThreshold = 0.02f

/**
 * How much stronger the blobs' tint is at the top of the tap breath. The resting tint is faint by design (10–12 colour
 * levels from the plain background), so scaling the blobs alone is invisible; the breath also brightens them.
 */
internal const val PulseGlow = 1.25f

/** Draw alpha of a blob brush built at [PulseGlow] × its resting alpha: 1 / [PulseGlow] at rest, 1 at the top of the pulse. */
internal fun pulseGlowAlpha(pulse: Float): Float = (1f + (PulseGlow - 1f) * pulse.coerceIn(0f, 1f)) / PulseGlow

/** [v] turned by [degrees]. */
internal fun rotate(v: Offset, degrees: Float): Offset {
    val r = Math.toRadians(degrees.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
}

/** [raw] squeezed into a circle of radius [limitPx]: `limit × tanh(|raw| / limit)` by length, the direction is kept. */
internal fun softLimit(raw: Offset, limitPx: Float): Offset {
    val length = raw.getDistance()
    return if (length > 0f) raw * (limitPx * tanh(length / limitPx) / length) else raw
}

/** The [raw] gesture shift that [softLimit]s to [shift] (a blob caught mid-fling continues from where it is, without a jump). */
internal fun softLimitInverse(shift: Offset, limitPx: Float): Offset {
    val length = shift.getDistance()
    if (length == 0f) return shift
    val r = (length / limitPx).coerceAtMost(0.999f)
    return shift * (limitPx * 0.5f * ln((1f + r) / (1f - r)) / length)
}

private val FlingDecayVector = FlingDecay.vectorize(Offset.VectorConverter)

/** Where a blob at [start] flung at [velocity] stops, by [FlingDecay]. */
internal fun flingEnd(start: Offset, velocity: Offset): Offset = Offset.VectorConverter.run {
    convertFromVector(FlingDecayVector.getTargetValue(convertToVector(start), convertToVector(velocity)))
}

/**
 * [velocity] cut down so that the fling's end point ([endFor]) is inside the circle of radius [limitPx]: when the end
 * of the exponential decay (linear in the velocity, a straight path) is outside, the velocity is scaled to land it on
 * the circle. `updateBounds` is not an option: it is a square and cuts the decay on both axes.
 */
internal fun fitFlingToCircle(velocity: Offset, limitPx: Float, endFor: (Offset) -> Offset): Offset {
    if (endFor(velocity).getDistance() <= limitPx) return velocity
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2
        if (endFor(velocity * mid).getDistance() <= limitPx) lo = mid else hi = mid
    }
    return velocity * lo
}

/**
 * Motion of the background gradient blobs. A finger drags the three blobs in different directions ([onDrag]); a swipe
 * lets them glide on and back to rest ([onFling]); a tap makes them "breathe" ([onTap]); a new press catches them
 * where they are ([onPress]). Everything runs on [scope]'s animations; at rest nothing is animating, so nothing redraws.
 *
 * Read [offset], [pulse] and [scale] from a draw lambda — then motion only invalidates drawing, not composition.
 */
class BackdropMotionState internal constructor(private val scope: CoroutineScope) {
    private val offsets = List(3) { Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold) }
    private val pulseAnimatable = Animatable(0f)
    private val jobs = arrayOfNulls<Job>(3)
    private var pulseJob: Job? = null

    // Accumulated, unsaturated gesture shift of each blob. Synchronous on purpose: Animatable.value lags behind the
    // spring and Animatable.targetValue is only updated after the animation takes the mutex.
    private val raw = Array(3) { Offset.Zero }

    /** Current shift of blob [index] (0 = A, 1 = B, 2 = C) from its resting place, in px. */
    fun offset(index: Int): Offset = offsets[index].value

    /** Tap breath, 0 at rest → 1 → 0. Independent of the gesture offsets. */
    val pulse: Float get() = pulseAnimatable.value

    /** Current radius scale of all blobs, 1 at rest. */
    val scale: Float get() = 1f + (PulseScale - 1f) * pulse

    // "Remove animations" (durationScale 0) would make every animateTo jump to its goal in one frame —
    // the blobs would twitch. With the scale at 0 the blobs just stay at rest.
    private fun motionAllowed() = (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) != 0f

    private fun limitPx(size: Size) = BackdropLimitFraction * size.minDimension

    // No cancel() here: cancelling an Animatable zeroes its velocity and frame time, so a move every frame would restart the
    // spring from standstill each time. A new animation on the same Animatable takes over through its mutex, with the velocity.
    private fun run(i: Int, block: suspend () -> Unit) {
        jobs[i] = scope.launch { block() }
    }

    /** A gesture starts: the blobs stop where they are (a press during a fling catches it) and continue from there. */
    fun onPress(size: Size) {
        if (!motionAllowed()) return
        for (i in 0 until 3) {
            jobs[i]?.cancel()
            raw[i] = softLimitInverse(offsets[i].value, limitPx(size))
        }
    }

    /** The pointers moved by [delta] px (mean of their shifts) on a surface of [size] px. */
    fun onDrag(delta: Offset, size: Size) {
        if (!motionAllowed()) return
        val limit = limitPx(size)
        for (i in 0 until 3) {
            raw[i] += rotate(delta, BackdropAngles[i]) * BackdropGains[i]
            val goal = softLimit(raw[i], limit)
            run(i) {
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

    /** A swipe ended at [velocity] px/s (of the gesture): the blobs glide on, inside the limit circle, then return to rest. */
    fun onFling(velocity: Offset, size: Size) {
        if (!motionAllowed()) return
        val limit = limitPx(size)
        for (i in 0 until 3) {
            val v = rotate(velocity, BackdropAngles[i]) * BackdropGains[i]
            raw[i] = Offset.Zero
            jobs[i]?.cancel() // drops a drag move that has not started yet (a batch comes together with the release)
            run(i) {
                val start = offsets[i].value
                val fit = fitFlingToCircle(v, limit) { flingEnd(start, it) }
                if (fit != Offset.Zero) offsets[i].animateDecay(fit, FlingDecay)
                offsets[i].animateTo(Offset.Zero, returnSpring(), initialVelocity = Offset.Zero)
            }
        }
    }

    /** The system cancelled the gesture: back to rest, no glide, no breath. */
    fun onCancel() = onSettle()

    // The return starts with zero velocity (initialVelocity): a drag spring that is still alive when the release comes would
    // otherwise hand its speed to it, and the blob would glide on outwards or swing across zero.

    /** The gesture ended without a swipe (tap, long press): back to rest, no glide. */
    fun onSettle() {
        if (!motionAllowed()) return
        for (i in 0 until 3) {
            raw[i] = Offset.Zero
            jobs[i]?.cancel() // drops a drag move that has not started yet (a batch comes together with the release)
            run(i) { offsets[i].animateTo(Offset.Zero, returnSpring(), initialVelocity = Offset.Zero) }
        }
    }

    /** A tap: the blobs settle and breathe once (a new tap restarts the breath smoothly). */
    fun onTap() {
        if (!motionAllowed()) return
        onSettle()
        pulseJob?.cancel()
        pulseJob = scope.launch {
            pulseAnimatable.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            pulseAnimatable.animateTo(
                0f,
                spring(dampingRatio = PulseFallDamping, stiffness = PulseFallStiffness, visibilityThreshold = PulseRestThreshold),
            )
        }
    }

    /** The listener is gone (the setting was switched off): no breath carries on. */
    fun endPulse() {
        pulseJob?.cancel()
        scope.launch { pulseAnimatable.snapTo(0f) }
    }

    private fun returnSpring() = spring<Offset>(
        dampingRatio = BackdropReturnDamping,
        stiffness = BackdropReturnStiffness,
        visibilityThreshold = Offset.VisibilityThreshold,
    )
}

@Composable
fun rememberBackdropMotionState(): BackdropMotionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackdropMotionState(scope) }
}
