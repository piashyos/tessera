package app.tessera.core.ui.theme

import kotlin.random.Random

/** Cells per side of the noise grid. Small enough to stay cheap, large enough to hide tiling — at any grain size. */
private const val NoiseGridCells = 160

/** One repeating noise tile: [side] × [side] ARGB pixels, row-major. Not a data class — compare [pixels] with `contentEquals`. */
class GrainTile(val side: Int, val pixels: IntArray)

/**
 * Builds the deterministic grain tile for [grainSize] (1..4): always a 160 × 160 grid of gray cells, each cell an
 * `s × s` px square of one value, so the side is `160 × s` and the pattern repeats no more often at ×4 than at ×1.
 * Same seed and read order as the original 1 px tile — at `grainSize = 1` the result is bit-identical to it.
 */
fun grainTile(grainSize: Int): GrainTile {
    val s = grainSize.coerceIn(1, 4)
    val n = NoiseGridCells
    val side = n * s
    val rnd = Random(seed = 0)
    val cells = IntArray(n * n) {
        val g = rnd.nextInt(256)
        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    val pixels = IntArray(side * side) { i -> cells[(i / side / s) * n + (i % side) / s] }
    return GrainTile(side, pixels)
}
