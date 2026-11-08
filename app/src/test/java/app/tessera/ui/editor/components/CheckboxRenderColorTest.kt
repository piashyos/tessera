package app.tessera.ui.editor.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.TesseraEditText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class CheckboxRenderColorTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun checkedBoxUsesPaletteColor() {
        val md = "- [x] done"
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false, palette = ThemePalette.SLATE) {
                NoteBody(
                    markdown = md,
                    onMarkdownChange = {},
                    onActionsReady = {},
                    onFormatStateChanged = {},
                    editable = true,
                )
            }
        }
        rule.waitForIdle()
        fun find(v: View): TesseraEditText? =
            if (v is TesseraEditText) v else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        val ed = find(rule.activity.window.decorView) ?: error("no TesseraEditText in the window")
        var bluish = false
        var isBrandOrange = true
        rule.runOnUiThread {
            val w = 400
            ed.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val h = ed.measuredHeight.coerceAtLeast(60)
            ed.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            ed.draw(Canvas(bmp))
            // Scan the left margin (where the checkbox sits) for the most-saturated pixel.
            var best = 0
            var bestScore = -1
            for (y in 0 until minOf(h, 80)) {
                for (x in 0 until 70) {
                    val px = bmp.getPixel(x, y)
                    val a = (px ushr 24) and 0xFF
                    if (a < 200) continue
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val score = maxOf(r, g, b) - minOf(r, g, b) // colorfulness
                    if (score > bestScore) { bestScore = score; best = px }
                }
            }
            val r = (best shr 16) and 0xFF
            val g = (best shr 8) and 0xFF
            val b = best and 0xFF
            // Slate primary (#475569) is clearly blue-dominant; the brand orange (#C96442) is
            // red-dominant. So under the Slate palette the checkbox must render bluish, not orange.
            bluish = b > r
            isBrandOrange = r > 150 && b < 100
        }
        assertTrue("checkbox should render in the Slate palette (blue-dominant), was orange", bluish)
        assertFalse("checkbox must not be the brand orange", isBrandOrange)
    }
}
