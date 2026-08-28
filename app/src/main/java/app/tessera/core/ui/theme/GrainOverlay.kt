package app.tessera.core.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/** Grain alpha at [intensity] = 1f. Deliberately low so the texture reads as a subtle film grain. */
private const val MaxGrainAlpha = 0.12f

/** Side of the repeating noise tile, in px. Small enough to stay cheap, large enough to hide tiling. */
private const val NoiseTileSizePx = 160

/**
 * Provides the app's base background — the theme [background][androidx.compose.material3.ColorScheme.background]
 * color plus, when [enabled], a tiled monochrome film-grain texture (in the spirit of Arc) — and
 * lays [content] on top of it.
 *
 * The grain is drawn BEHIND the content, so it only shows on the background: opaque elements (cards,
 * bars, sheets) paint over it and stay crisp, while the empty background regions carry the grain.
 * For this to work the screens above must NOT paint their own opaque full-screen background (their
 * Scaffolds / root Surfaces are transparent), so the background comes from here.
 *
 * [intensity] (0f..1f) maps linearly to the grain alpha, capped at [MaxGrainAlpha]. The overlay is
 * draw-only and adds no pointer-input node, so it never intercepts touches.
 */
@Composable
fun GrainOverlay(
    enabled: Boolean,
    intensity: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val alpha = intensity.coerceIn(0f, 1f) * MaxGrainAlpha
    val showGrain = enabled && alpha > 0f
    // One deterministic noise tile, built once while grain is on and repeated across the viewport.
    // A fixed seed keeps the pattern stable across recompositions/config changes so it never
    // visibly "reshuffles"; keying on [showGrain] avoids allocating the bitmap while grain is off.
    val brush = remember(showGrain) {
        if (!showGrain) return@remember null
        val bitmap = Bitmap.createBitmap(NoiseTileSizePx, NoiseTileSizePx, Bitmap.Config.ARGB_8888)
        val rnd = Random(seed = 0)
        val pixels = IntArray(NoiseTileSizePx * NoiseTileSizePx) {
            val g = rnd.nextInt(256)
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        bitmap.setPixels(pixels, 0, NoiseTileSizePx, 0, 0, NoiseTileSizePx, NoiseTileSizePx)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(background)
                if (brush != null) drawRect(brush = brush, alpha = alpha)
            },
    ) {
        content()
    }
}
