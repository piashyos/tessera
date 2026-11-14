package app.tessera.core.ui.theme

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackdropMotionTest {
    private fun angleBetween(a: Offset, b: Offset): Float =
        Math.toDegrees(atan2((a.x * b.y - a.y * b.x).toDouble(), (a.x * b.x + a.y * b.y).toDouble())).toFloat()

    @Test
    fun `blobs fan out at 0 and plus minus 120 degrees to the gesture`() {
        val gesture = Offset(0f, 10f)
        for ((i, expected) in listOf(0f, 120f, -120f).withIndex()) {
            val turned = rotate(gesture, BackdropAngles[i])
            assertEquals(gesture.getDistance(), turned.getDistance(), 0.001f)
            assertEquals(expected, angleBetween(gesture, turned), 0.01f, "blob $i")
        }
    }

    @Test
    fun `soft limit stays inside the circle, keeps the direction and grows monotonically`() {
        val limit = 100f
        var previous = 0f
        for (length in listOf(1f, 10f, 50f, 100f, 300f, 1000f)) {
            val shift = softLimit(Offset(0.6f, 0.8f) * length, limit)
            assertTrue(shift.getDistance() < limit + 0.0001f, "$length -> ${shift.getDistance()}")
            assertTrue(shift.getDistance() > previous)
            assertEquals(0.6f / 0.8f, shift.x / shift.y, 0.0001f)
            previous = shift.getDistance()
        }
        // small shifts pass almost unchanged: an ordinary scroll does not feel the limit
        assertEquals(1f, softLimit(Offset(1f, 0f), limit).x, 0.001f)
        assertEquals(Offset.Zero, softLimit(Offset.Zero, limit))
    }

    @Test
    fun `soft limit inverse gives back the raw shift of a caught blob`() {
        val limit = 100f
        for (shift in listOf(Offset(3f, -4f), Offset(60f, 0f), Offset(0f, -95f))) {
            val back = softLimit(softLimitInverse(shift, limit), limit)
            assertEquals(shift.x, back.x, 0.01f)
            assertEquals(shift.y, back.y, 0.01f)
        }
        assertTrue(softLimitInverse(Offset(100f, 0f), limit).getDistance().isFinite())
    }

    @Test
    fun `a fast fling is cut so that it ends exactly on the circle`() {
        val limit = 100f
        for (start in listOf(Offset.Zero, Offset(40f, -30f))) {
            for (velocity in listOf(Offset(0f, 5000f), Offset(-3000f, 4000f))) {
                assertTrue(flingEnd(start, velocity).getDistance() > limit, "unlimited fling leaves the circle")
                val fit = fitFlingToCircle(velocity, limit) { flingEnd(start, it) }
                assertEquals(limit, flingEnd(start, fit).getDistance(), 0.01f)
                // same direction, only slower
                assertTrue(fit.getDistance() < velocity.getDistance())
                assertEquals(0f, angleBetween(velocity, fit), 0.001f)
            }
        }
    }

    @Test
    fun `a short fling is left alone`() {
        val v = Offset(0f, 200f)
        assertEquals(v, fitFlingToCircle(v, 100f) { flingEnd(Offset.Zero, it) })
        assertTrue(flingEnd(Offset.Zero, v).getDistance() < 100f)
    }

    @Test
    fun theGlowKeepsTheRestingTintAndReachesPulseGlowTimesItAtThePeak() {
        assertEquals(1.25f, PulseGlow, 0f)
        assertEquals(1.12f, PulseScale, 0f)
        // The brush is built at PulseGlow × the resting alpha, so the draw alpha at rest must undo exactly that.
        assertEquals(1f, pulseGlowAlpha(0f) * PulseGlow, 0.0001f)
        assertEquals(PulseGlow, pulseGlowAlpha(1f) * PulseGlow, 0.0001f)
        assertTrue(pulseGlowAlpha(0.5f) > pulseGlowAlpha(0f) && pulseGlowAlpha(0.5f) < pulseGlowAlpha(1f))
        assertEquals(pulseGlowAlpha(1f), pulseGlowAlpha(1.4f), 0.0001f) // the spring overshoot is clamped
        assertTrue(abs(pulseGlowAlpha(-1f) - pulseGlowAlpha(0f)) < 0.0001f)
    }

    /** Frames only when told to, so a test controls what runs between a move and a release. */
    private class ManualClock : MonotonicFrameClock {
        private val waiting = ArrayList<(Long) -> Unit>()
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            suspendCancellableCoroutine { cont -> waiting += { t -> cont.resume(onFrame(t)) } }

        fun frame(nanos: Long) {
            val now = waiting.toList()
            waiting.clear()
            now.forEach { it(nanos) }
        }
    }

    /**
     * A fast drag, then one more move and the release in the same main-thread pass (a device delivers a batch of MOVEs
     * together with UP / CANCEL): the last move's coroutine has not started, the previous drag spring is alive and fast.
     * The return to rest must not inherit that speed: no growth outwards, no crossing zero.
     */
    private fun TestScope.releaseAfterFastDrag(release: (BackdropMotionState, Size) -> Unit) {
        val clock = ManualClock()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + clock)
        val state = BackdropMotionState(scope)
        val size = Size(1000f, 2000f)
        var t = 0L
        fun frame() {
            testScheduler.runCurrent()
            t += 16_000_000L
            clock.frame(t)
            testScheduler.runCurrent()
        }
        repeat(6) { state.onDrag(Offset(0f, 60f), size); frame(); frame() }
        state.onDrag(Offset(0f, 60f), size)
        release(state, size) // no scheduler run in between
        val atRelease = state.offset(0).y
        assertTrue(atRelease > 3f, "A moved with the drag: $atRelease")
        var peak = atRelease
        var low = atRelease
        repeat(400) { frame(); peak = maxOf(peak, state.offset(0).y); low = minOf(low, state.offset(0).y) }
        assertTrue(peak <= atRelease + 0.5f, "no glide outwards after the release: $atRelease -> $peak")
        assertTrue(low >= -0.5f, "no overshoot across zero: $low")
        assertEquals(0f, state.offset(0).getDistance(), 0.5f)
    }

    @Test
    fun `a cancel right after a fast drag move returns without gliding on`() = runTest {
        releaseAfterFastDrag { state, _ -> state.onCancel() }
    }

    @Test
    fun `a motionless release right after a fast drag move returns without gliding on`() = runTest {
        releaseAfterFastDrag { state, size -> state.onFling(Offset.Zero, size) }
    }
}
