package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import app.tessera.ui.editor.engine.spans.BlockSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The caret the editor draws itself: the text of the line, not the margins of its block (task: native-editor-polish #2). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CaretTest {
    private val style = EngineTestStyle.create()

    private fun host(md: String): HostFixture = HostFixture(md, width = 300, style = style).also {
        it.view.includeFontPadding = false
        it.layout()
    }

    private fun focused(md: String) = host(md).also { assertTrue(it.view.requestFocus()); it.caret(0) }

    private val doc = "intro\n\n> quote one\n> quote two\n\n# Title\n\n## Heading\n\n- a\n- b\n- c\n\n```\ncode\n```\n\n" +
        "a long paragraph that wraps over several lines of the narrow editor so that the middle lines have no block around them at all"

    @Test fun bandOfEveryLineIsTheTextWithoutTheMarginsOfItsBlock() {
        val h = host(doc)
        val l = h.view.layout
        val t = h.text
        var withMargin = 0
        for (line in 0 until l.lineCount) {
            val (top, bottom) = h.view.textBand(line)
            val ps = l.getLineStart(line)
            val span = t.getSpans(ps, minOf(ps + 1, t.length), BlockSpan::class.java).firstOrNull()
            val sp = span?.baked
            val extraTop = if (sp != null && ps <= t.getSpanStart(span)) sp.extraTop else 0
            val extraBottom = if (sp != null && l.getLineEnd(line) >= t.getSpanEnd(span)) sp.extraBottom else 0
            assertEquals("top of line $line", l.getLineTop(line) + extraTop, top)
            assertEquals("bottom of line $line", l.getLineBottom(line) - extraBottom, bottom)
            if (extraTop + extraBottom > 0) withMargin++
        }
        assertTrue("the document has lines with margins", withMargin >= 8)
    }

    @Test fun aHeadingBandIsAsHighAsItsOwnFont() {
        val h = host("# Title\nbody")
        val title = TextPaint(h.view.paint).apply { textSize = this@CaretTest.style.titleTextSizePx; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }.fontMetricsInt
        val body = h.view.paint.fontMetricsInt
        val (tt, tb) = h.view.textBand(0)
        val (bt, bb) = h.view.textBand(1)
        assertEquals(title.descent - title.ascent, tb - tt)
        assertEquals(body.descent - body.ascent, bb - bt)
        assertTrue(tb - tt > bb - bt)
    }

    @Test fun lineSpacingExtraIsNotInTheBand() {
        val h = host(doc)
        h.view.setLineSpacing(6f, 1f)
        h.layout()
        val l = h.view.layout
        val last = l.lineCount - 2 // a middle line of the wrapped paragraph
        val (top, bottom) = h.view.textBand(last)
        assertEquals(l.getLineTop(last), top)
        assertEquals(l.getLineBottom(last) - 6, bottom)
    }

    @Test fun noCaretWithoutFocusWithASelectionOrInAViewer() {
        val h = host("hello world")
        h.caret(3)
        assertNull("no focus", h.view.caretRect())
        assertTrue(h.view.requestFocus())
        h.caret(3)
        assertNotNull(h.view.caretRect())
        h.select(1, 4)
        assertNull("a selection", h.view.caretRect())
        h.caret(3)
        h.view.editable = false
        assertNull("read-only", h.view.caretRect())
    }

    @Test fun caretStandsOnTheBandOfItsLineAtTheCaretX() {
        val h = focused("intro\n\n> quote")
        h.caret(h.at("quote", 2))
        val r = h.view.caretRect()!!
        val line = h.view.layout.getLineForOffset(h.at("quote", 2))
        val (top, bottom) = h.view.textBand(line)
        assertEquals((h.view.totalPaddingTop + top).toFloat(), r.top, 0f)
        assertEquals((h.view.totalPaddingTop + bottom).toFloat(), r.bottom, 0f)
        assertEquals(2 * h.view.resources.displayMetrics.density, r.width(), 0.01f)
        assertTrue(r.top > h.view.layout.getLineTop(line) + h.view.totalPaddingTop)
    }

    @Test fun blinksEvery500msAndRestartsOnAnEditAndOnAMove() {
        val h = focused("hello")
        assertTrue(h.view.caretOn)
        h.pass(499); assertTrue(h.view.caretOn)
        h.pass(1); assertFalse(h.view.caretOn)
        h.pass(500); assertTrue(h.view.caretOn)
        h.pass(500); assertFalse(h.view.caretOn)
        h.caret(2)
        assertTrue("a move shows the caret", h.view.caretOn)
        h.pass(500); assertFalse(h.view.caretOn)
        h.text.insert(2, "x")
        assertTrue("an edit shows the caret", h.view.caretOn)
        h.pass(499); assertTrue("and the phase starts over", h.view.caretOn)
        h.pass(1); assertFalse(h.view.caretOn)
    }

    @Test fun blinkStopsWithTheFocus() {
        val h = focused("hello")
        h.view.clearFocus()
        h.pass(1500)
        assertTrue(h.view.caretOn)
        assertNull(h.view.caretRect())
    }

    @Test fun caretIsPaintedOverTheTextOfTheQuoteLineOnly() {
        val h = focused("intro\n\n> quote")
        h.caret(h.at("quote", 2))
        val r = h.view.caretRect()!!
        val line = h.view.layout.getLineForOffset(h.at("quote", 2))
        val bmp = Bitmap.createBitmap(h.width, h.height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).also { it.drawColor(Color.WHITE); h.view.draw(it) }
        val x = (r.left + 1).toInt()
        fun accent(y: Int) = bmp.getPixel(x, y) == EngineTestStyle.ACCENT
        val lineTop = h.view.totalPaddingTop + h.view.layout.getLineTop(line)
        val lineBottom = h.view.totalPaddingTop + h.view.layout.getLineBottom(line)
        assertTrue("middle of the text", accent(((r.top + r.bottom) / 2).toInt()))
        assertTrue("first row of the band", accent(r.top.toInt()))
        assertTrue("last row of the band", accent(r.bottom.toInt() - 1))
        for (y in lineTop until r.top.toInt()) assertFalse("margin above at $y", accent(y))
        for (y in r.bottom.toInt() until lineBottom) assertFalse("margin below at $y", accent(y))
        h.pass(500)
        Canvas(bmp).also { it.drawColor(Color.WHITE); h.view.draw(it) }
        assertFalse("the off phase", accent(((r.top + r.bottom) / 2).toInt()))
    }
}
