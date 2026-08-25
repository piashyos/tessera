package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color
import app.tessera.core.preferences.ThemeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicStylesTest {
    /** Terracotta-ish seed, close to the WARM brand primary. */
    private val seed = 0xFFC96442.toInt()

    @Test
    fun eachStyleProducesADistinctScheme() {
        val primaries = ThemeStyle.entries.map { schemeForSeed(seed, it, dark = false).primary }
        assertEquals("styles must not collapse to the same primary", primaries.size, primaries.toSet().size)
    }

    @Test
    fun lightAndDarkVariantsDiffer() {
        for (style in ThemeStyle.entries) {
            val light = schemeForSeed(seed, style, dark = false)
            val dark = schemeForSeed(seed, style, dark = true)
            assertNotEquals("light/dark background must differ for $style", light.background, dark.background)
            // Light surfaces are bright, dark surfaces are dim.
            assertTrue("$style light background should be bright", light.background.luminance() > 0.5f)
            assertTrue("$style dark background should be dim", dark.background.luminance() < 0.5f)
        }
    }

    @Test
    fun trueBlackForcesBlackBackgroundAndLowestSurfaces() {
        val dark = schemeForSeed(seed, ThemeStyle.TONAL_SPOT, dark = true).withTrueBlack()
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.Black, dark.surface)
        assertEquals(Color.Black, dark.surfaceDim)
        assertEquals(Color.Black, dark.surfaceContainerLowest)
        // The container ladder above "lowest" must survive so grouped rows stay distinguishable.
        assertNotEquals(Color.Black, dark.surfaceContainer)
    }

    private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)
}
