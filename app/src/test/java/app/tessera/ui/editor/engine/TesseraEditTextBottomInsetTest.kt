package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `bottomInset` is the strip a floating (glass) panel covers: it is real bottom padding, so the caret of the last line and the end of the note
 * scroll clear of the panel, yet the text is drawn into it (`TextView` would clip it away) so the panel can blur it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TesseraEditTextBottomInsetTest {
    private val inset = 150
    private fun long() = (1..60).joinToString("\n") { "Line $it with some words" }

    private fun fixture(withInset: Boolean): HostFixture {
        val h = HostFixture(long())
        h.view.setPadding(0, 8, 0, 16)
        if (withInset) h.view.bottomInset = inset
        h.layout()
        return h
    }

    @Test
    fun theInsetIsRealPadding() {
        val h = fixture(true)
        assertEquals(16 + inset, h.view.paddingBottom)
        h.view.bottomInset = 0
        assertEquals(16, h.view.paddingBottom)
    }

    @Test
    fun theLastLineEndsAboveTheInsetAtTheEndOfTheNote() {
        val h = fixture(true)
        h.view.setSelection(h.view.text!!.length)
        h.layout()
        val l = h.view.layout
        h.view.scrollTo(0, l.height + h.view.totalPaddingTop + h.view.totalPaddingBottom - h.view.height) // the end of the note
        val lineBottom = l.getLineBottom(l.lineCount - 1) + h.view.totalPaddingTop - h.view.scrollY
        assertTrue("last line ends at $lineBottom, inset starts at ${h.view.height - inset}", lineBottom <= h.view.height - inset)
    }

    // Robolectric's canvas does not apply TextView's clip to the padding, so the lack of the draw-under cannot be shown here: on the device
    // (GlassBarShotsTest) the text under the bar vanishes without it.
    /** Ink in the strip the panel covers, mid-note (text continues below the view). */
    private fun inkInStrip(drawUnder: Boolean): Int {
        val h = HostFixture(long())
        h.view.setPadding(0, 8, 0, 16)
        if (drawUnder) h.view.bottomInset = inset else h.view.setPadding(0, 8, 0, 16 + inset)
        h.layout()
        h.view.scrollTo(0, 300)
        val b = Bitmap.createBitmap(h.width, h.height, Bitmap.Config.ARGB_8888)
        Canvas(b).also { it.drawColor(Color.WHITE); h.view.draw(it) }
        var ink = 0
        for (y in h.height - inset until h.height - 16) for (x in 0 until h.width) if (b.getPixel(x, y) != Color.WHITE) ink++
        return ink
    }

    @Test
    fun textIsDrawnUnderTheInset() = assertTrue("text runs under the strip", inkInStrip(drawUnder = true) > 0)
}
