package app.tessera.ui.editor.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure resize/rotate geometry + canonical src writers. */
class ImageSelectionGeometryTest {

    @Test fun `outward drag grows width`() {
        // start=50% of a 200px default (=100px), +100px outward → 200px → 100%.
        assertEquals(100, dragToWidthPercent(startPercent = 50, signedDx = 100, defaultWidthPx = 200))
    }

    @Test fun `inward drag shrinks width`() {
        // start=100% (200px), -100px → 100px → 50%.
        assertEquals(50, dragToWidthPercent(startPercent = 100, signedDx = -100, defaultWidthPx = 200))
    }

    @Test fun `would-exceed-100 is clamped to 100`() {
        assertEquals(100, dragToWidthPercent(startPercent = 100, signedDx = 300, defaultWidthPx = 200))
    }

    @Test fun `below-10 is clamped to 10`() {
        assertEquals(10, dragToWidthPercent(startPercent = 20, signedDx = -200, defaultWidthPx = 200))
    }

    @Test fun `withWidthPercent drops default w but keeps rot`() {
        assertEquals("tessera-img://x?rot=90", withWidthPercent("tessera-img://x?rot=90", 100))
    }

    @Test fun `withWidthPercent sets non-default w before rot`() {
        assertEquals("tessera-img://x?w=50&rot=90", withWidthPercent("tessera-img://x?rot=90", 50))
    }

    @Test fun `withRotation drops default rot but keeps w`() {
        assertEquals("tessera-img://x?w=50", withRotation("tessera-img://x?w=50", 0))
    }

    @Test fun `withRotation sets non-default rot after w`() {
        assertEquals("tessera-img://x?w=50&rot=180", withRotation("tessera-img://x?w=50", 180))
    }

    @Test fun `both defaults serialize bare`() {
        assertEquals("tessera-img://x", withWidthPercent("tessera-img://x?rot=0", 100))
        assertEquals("tessera-img://x", withRotation("tessera-img://x?w=100", 0))
    }
}
