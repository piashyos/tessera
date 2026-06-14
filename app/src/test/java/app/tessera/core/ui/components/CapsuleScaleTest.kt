package app.tessera.core.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CapsuleScaleTest {
    @Test
    fun `progress below zero clamps to resting scale`() {
        assertEquals(1f, capsuleScaleFactor(scrollProgress = -0.4f))
    }

    @Test
    fun `progress above one clamps to collapsed scale`() {
        assertEquals(0.92f, capsuleScaleFactor(scrollProgress = 1.4f))
    }

    @Test
    fun `midpoint progress interpolates between full and collapsed scale`() {
        assertEquals(0.96f, capsuleScaleFactor(scrollProgress = 0.5f), 0.0001f)
    }
}
