package app.tessera.core.ui.theme

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackdropMotionTest {
    @Test
    fun `shift goes the way of the gesture`() {
        val r = nudge(Offset.Zero, Offset(3f, -4f), gain = 0.5f, limitPx = 100f)
        assertEquals(Offset(1.5f, -2f), r)
    }

    @Test
    fun `length never exceeds the limit and direction is kept`() {
        val r = nudge(Offset(50f, 0f), Offset(0f, 1000f), gain = 1f, limitPx = 20f)
        assertEquals(20f, r.getDistance(), 0.001f)
        assertTrue(r.x > 0f && r.y > 0f)
        assertEquals(50f / 1000f, r.x / r.y, 0.0001f)
    }

    @Test
    fun `zero gesture does not move`() {
        assertEquals(Offset.Zero, nudge(Offset.Zero, Offset.Zero, gain = 0.35f, limitPx = 10f))
        assertEquals(Offset(2f, 3f), nudge(Offset(2f, 3f), Offset.Zero, gain = 0.35f, limitPx = 10f))
    }

    @Test
    fun `repeated nudges grow monotonically and stop at the limit`() {
        var current = Offset.Zero
        var previous = 0f
        repeat(100) {
            current = nudge(current, Offset(0f, 10f), gain = 0.25f, limitPx = 30f)
            val length = current.getDistance()
            assertTrue(length >= previous - 0.0001f)
            assertTrue(length <= 30f + 0.0001f)
            previous = length
        }
        assertEquals(30f, previous, 0.001f)
    }

    @Test
    fun pulsePushGoesAwayFromTheTouchPoint() {
        val push = pulsePush(center = Offset(100f, 100f), origin = Offset(100f, 0f), pulse = 1f, amplitude = 0.5f, limitPx = 40f)
        assertEquals(0f, push.x, 0.001f)
        assertEquals(20f, push.y, 0.001f)
    }

    @Test
    fun pulsePushIsZeroAtRestAndUnderTheFinger() {
        assertEquals(Offset.Zero, pulsePush(Offset(5f, 5f), Offset(0f, 0f), pulse = 0f, amplitude = 0.6f, limitPx = 40f))
        assertEquals(Offset.Zero, pulsePush(Offset(5f, 5f), Offset(5f, 5f), pulse = 1f, amplitude = 0.6f, limitPx = 40f))
    }

    @Test
    fun theGlowKeepsTheRestingTintAndReachesPulseGlowTimesItAtThePeak() {
        // The brush is built at PulseGlow × the resting alpha, so the draw alpha at rest must undo exactly that.
        assertEquals(1f, pulseGlowAlpha(0f) * PulseGlow, 0.0001f)
        assertEquals(PulseGlow, pulseGlowAlpha(1f) * PulseGlow, 0.0001f)
        assertTrue(pulseGlowAlpha(0.5f) > pulseGlowAlpha(0f) && pulseGlowAlpha(0.5f) < pulseGlowAlpha(1f))
        assertEquals(pulseGlowAlpha(1f), pulseGlowAlpha(1.4f), 0.0001f) // the spring overshoot is clamped
    }
}
