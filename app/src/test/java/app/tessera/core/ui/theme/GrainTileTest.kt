package app.tessera.core.ui.theme

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GrainTileTest {
    /** The tile exactly as `GrainOverlay` built it before grain size existed: 160×160, Random(0), 1 px per speck. */
    private fun originalTile(): IntArray {
        val side = 160
        val rnd = Random(seed = 0)
        return IntArray(side * side) {
            val g = rnd.nextInt(256)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    @Test
    fun `size one is bit-identical to the original tile`() {
        val tile = grainTile(1)
        assertEquals(160, tile.side)
        assertTrue(originalTile().contentEquals(tile.pixels))
    }

    @Test
    fun `every s by s square is uniform and the side is 160 times s`() {
        for (s in 1..4) {
            val tile = grainTile(s)
            assertEquals(160 * s, tile.side, "side is 160 * $s")
            assertEquals(tile.side * tile.side, tile.pixels.size)
            for (y in 0 until tile.side) for (x in 0 until tile.side) {
                val corner = tile.pixels[(y / s * s) * tile.side + (x / s * s)]
                assertEquals(corner, tile.pixels[y * tile.side + x], "s=$s at ($x,$y)")
            }
        }
    }

    @Test
    fun `result is deterministic`() {
        for (s in 1..4) assertArrayEquals(grainTile(s).pixels, grainTile(s).pixels)
    }

    @Test
    fun `grid is always 160 cells and every cell equals the size one speck`() {
        val base = grainTile(1)
        for (s in 2..4) {
            val tile = grainTile(s)
            for (cy in 0 until 160) for (cx in 0 until 160) {
                assertEquals(base.pixels[cy * 160 + cx], tile.pixels[cy * s * tile.side + cx * s], "s=$s cell ($cx,$cy)")
            }
        }
    }
}
