package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.tessera.core.text.notedoc.NoteMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the spans actually paint: a [StaticLayout] drawn on a bitmap (native graphics). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BlockSpanDrawTest {
    private val style = EngineTestStyle.create()
    private val width = 400

    private class Drawn(val layout: Layout, val bitmap: Bitmap) {
        fun px(x: Int, y: Int) = bitmap.getPixel(x, y)
    }

    private fun draw(md: String): Drawn {
        val text = DocBinder.build(NoteMarkdown.parse(md), style)
        DocBinder.setObjectWidth(text, width)
        val size = style.bodyTextSizePx
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size; color = EngineTestStyle.TEXT }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
        val bitmap = Bitmap.createBitmap(width, layout.height + 4, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)
        return Drawn(layout, bitmap)
    }

    @Test
    fun monoSeriesIsOneBlockRoundedOnlyAtItsEnds() {
        val d = draw("above\n```\none\ntwo\nthree\n```\nbelow")
        val l = d.layout
        val first = 1; val mid = 2; val last = 3
        // first line: top corners cut, bottom edge joins the next line
        assertEquals(Color.WHITE, d.px(1, l.getLineTop(first) + 1))
        assertEquals(EngineTestStyle.MONO_BG, d.px(width - 40, l.getLineTop(first) + 1))
        // no gap between lines of the series, and no rounding inside it
        for (line in listOf(first to mid, mid to last)) {
            val seam = l.getLineTop(line.second)
            assertEquals(EngineTestStyle.MONO_BG, d.px(width - 40, seam - 1))
            assertEquals(EngineTestStyle.MONO_BG, d.px(width - 40, seam))
            assertEquals("left edge at the seam of ${line.first}/${line.second}", EngineTestStyle.MONO_BG, d.px(1, seam))
            assertEquals(EngineTestStyle.MONO_BG, d.px(1, seam - 1))
        }
        // last line: bottom corners cut
        assertEquals(Color.WHITE, d.px(1, l.getLineBottom(last) - 1))
        assertEquals(EngineTestStyle.MONO_BG, d.px(width - 40, l.getLineBottom(last) - 1))
        // the neighbours outside the series are untouched
        assertEquals(Color.WHITE, d.px(width - 40, l.getLineBottom(0) - 1))
        assertEquals(Color.WHITE, d.px(width - 40, l.getLineTop(4) + 1))
    }

    @Test
    fun aSingleMonoLineIsRoundedOnAllCorners() {
        val d = draw("```\nonly\n```")
        val l = d.layout
        assertEquals(Color.WHITE, d.px(1, l.getLineTop(0) + 1))
        assertEquals(Color.WHITE, d.px(1, l.getLineBottom(0) - 1))
        assertEquals(Color.WHITE, d.px(width - 2, l.getLineTop(0) + 1))
        assertEquals(EngineTestStyle.MONO_BG, d.px(width / 2 + 60, l.getLineBottom(0) / 2))
    }

    @Test
    fun quoteBarAndFillAreContinuousAcrossQuoteParagraphs() {
        val d = draw("above\n> one\n> two\nbelow")
        val l = d.layout
        for (y in listOf(l.getLineTop(1) + 1, l.getLineBottom(1) - 1, l.getLineTop(2), l.getLineBottom(2) - 1)) {
            assertEquals("bar at y=$y", EngineTestStyle.QUOTE_BAR, d.px(1, y))
            assertEquals("fill at y=$y", EngineTestStyle.QUOTE_FILL, d.px(width - 5, y))
        }
        assertEquals(Color.WHITE, d.px(1, l.getLineTop(0) + 1))
        assertEquals(Color.WHITE, d.px(1, l.getLineTop(3) + 1))
    }

    @Test
    fun everyListMarkerIsDrawnInItsMargin() {
        val d = draw("- bullet\n+ dash\n1. number\n- [ ] open\n- [x] done\n    - nested")
        val l = d.layout
        for (line in 0 until 6) {
            val margin = if (line == 5) 30 + 24 else 30
            val (top, bottom) = l.getLineTop(line) to l.getLineBottom(line)
            var painted = 0
            for (x in 0 until margin) for (y in top until bottom) if (d.px(x, y) != Color.WHITE) painted++
            assertTrue("no marker on line $line", painted > 0)
            // nothing leaks to the left of the marker column of that level
            if (line == 5) for (x in 0 until 24) for (y in top until bottom) assertEquals(Color.WHITE, d.px(x, y))
        }
    }

    @Test
    fun checkedBoxIsFilledAndUncheckedIsOutlined() {
        val d = draw("- [ ] open\n- [x] done")
        val l = d.layout
        fun center(line: Int) = d.px(9, (l.getLineTop(line) + l.getLineBottom(line)) / 2 - 5)
        assertEquals("filled box of a checked item", EngineTestStyle.ACCENT, center(1))
        assertNotEquals("an unchecked box is hollow", EngineTestStyle.ACCENT, center(0))
    }

    @Test
    fun marksInsideAHeadingKeepTheirLookWhateverTheOrderOfTheSpans() {
        fun pixels(md: String) = draw(md).let { d -> IntArray(d.bitmap.width * d.bitmap.height).also { d.bitmap.getPixels(it, 0, d.bitmap.width, 0, 0, d.bitmap.width, d.bitmap.height) } }
        for ((marked, plain) in listOf("# **Ab** cd" to "# Ab cd", "# *Ab* cd" to "# Ab cd", "# a `c` d" to "# a c d")) {
            assertTrue("$marked looks like $plain", !pixels(marked).contentEquals(pixels(plain)))
        }
        // the other order: the block span set first, the marks after it
        val doc = NoteMarkdown.parse("# **Ab** cd")
        val built = DocBinder.build(doc, style)
        val reversed = android.text.SpannableStringBuilder(built.toString())
        built.getSpans(0, built.length, Any::class.java).filter { DocBinder.isEngineSpan(it) }
            .sortedByDescending { it is app.tessera.ui.editor.engine.spans.BlockSpan }
            .forEach { reversed.setSpan(it, built.getSpanStart(it), built.getSpanEnd(it), built.getSpanFlags(it)) }
        val size = style.bodyTextSizePx
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size; color = EngineTestStyle.TEXT }
        fun render(t: CharSequence): IntArray {
            val l = StaticLayout.Builder.obtain(t, 0, t.length, paint, width).build()
            val b = Bitmap.createBitmap(width, l.height + 4, Bitmap.Config.ARGB_8888)
            Canvas(b).also { it.drawColor(Color.WHITE); l.draw(it) }
            return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        }
        assertTrue("block span first, marks after: same picture", render(built).contentEquals(render(reversed)))
    }

    /** The most painted pixels in one pixel row of the text area: a struck line has a row across the whole text. */
    private fun maxRowInk(d: Drawn): Int = (0 until d.bitmap.height).maxOf { y -> (30 until width).count { d.px(it, y) != Color.WHITE && d.px(it, y) != 0xFFEEEEEE.toInt() } }

    private fun count(d: Drawn, color: Int, xFrom: Int = 30): Int {
        var n = 0
        for (x in xFrom until width) for (y in 0 until d.bitmap.height) if (d.px(x, y) == color) n++
        return n
    }

    @Test
    fun checkedItemIsStruckAndMutedWhateverIsMarkedInsideIt() {
        for (md in listOf("- [x] aaaa bbbb", "- [x] `aaaa` bbbb", "- [x] **aaaa** bbbb")) {
            val d = draw(md)
            assertTrue("$md: muted text", count(d, EngineTestStyle.MUTED) > 20)
            assertEquals("$md: no body-colored text in a checked item", 0, count(d, EngineTestStyle.TEXT))
            val inkOpen = maxRowInk(draw("- [ ] " + md.drop(6)))
            assertTrue("$md: strike line ${maxRowInk(d)} vs $inkOpen", maxRowInk(d) > inkOpen + 10)
        }
        val open = draw("- [ ] aaaa bbbb")
        assertTrue(count(open, EngineTestStyle.TEXT) > 20)
        // (an antialiased edge of dark text can hit the muted gray by chance: compare, do not expect zero)
        assertTrue(count(open, EngineTestStyle.MUTED) * 4 < count(draw("- [x] aaaa bbbb"), EngineTestStyle.MUTED))
    }

    @Test
    fun dividerAndPlaceholdersAreDrawnOnTheirLine() {
        val d = draw("a\n---\n![](tessera-img://p-1)\n| A |\n| --- |\n| 1 |\nb")
        val l = d.layout
        val mid = (l.getLineTop(1) + l.getLineBottom(1)) / 2
        assertTrue((mid - 2..mid + 2).any { d.px(40, it) == EngineTestStyle.DIVIDER })
        val imageTop = l.getLineTop(2)
        assertEquals("frame of the image placeholder", 0xFF999999.toInt(), d.px(30, imageTop + 1))
    }
}
