package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.DynamicLayout
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.BLOCK_SPAN_FLAGS
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.ListItemSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A live [DynamicLayout] re-measures only the changed paragraphs. After edits the neighbours of the changed paragraph
 * (mono padding of the first / last line of a series, the numbers of a list) must look as a fresh layout of the same text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NeighbourLayoutTest {
    private val style = EngineTestStyle.create()
    private val normalizer = Normalizer(style)
    private val width = 400
    private val size = style.bodyTextSizePx
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size; color = EngineTestStyle.TEXT }

    private fun live(md: String): Pair<Editable, DynamicLayout> {
        val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), style))
        return e to DynamicLayout.Builder.obtain(e, paint, width).build()
    }

    private fun edit(e: Editable, start: Int, end: Int, replacement: CharSequence) {
        val origin = normalizer.originKind(e, start)
        e.replace(start, end, replacement)
        normalizer.normalize(e, start, start + replacement.length, origin)
    }

    private fun render(l: Layout): IntArray {
        val b = Bitmap.createBitmap(width, l.height + 4, Bitmap.Config.ARGB_8888)
        Canvas(b).also { it.drawColor(Color.WHITE); l.draw(it) }
        return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    }

    /** The live layout and the numbers of the editable against a fresh build of what the editable holds. */
    private fun assertLooksFresh(label: String, e: Editable, layout: DynamicLayout) {
        EngineAssertions.assertInvariant1(e, label)
        val fresh = DocBinder.build(DocBinder.read(e), style)
        DocBinder.setObjectWidth(fresh, width)
        val staticLayout = StaticLayout.Builder.obtain(fresh, 0, fresh.length, paint, width).build()
        assertEquals("$label: text", fresh.toString(), e.toString())
        assertEquals("$label: line count", staticLayout.lineCount, layout.lineCount)
        for (i in 0..staticLayout.lineCount) {
            assertEquals("$label: top of line $i", staticLayout.getLineTop(i), layout.getLineTop(i))
        }
        val live = e.getSpans(0, e.length, ListItemSpan::class.java).sortedBy { e.getSpanStart(it) }.map { it.number }
        val want = fresh.getSpans(0, fresh.length, ListItemSpan::class.java).sortedBy { fresh.getSpanStart(it) }.map { it.number }
        assertEquals("$label: numbers", want, live)
        assertTrue("$label: picture", render(layout).contentEquals(render(staticLayout)))
    }

    @Test
    fun enterAtTheEndOfMonoAndDeletingAParagraphBetweenMonoLines() {
        val (e, layout) = live("above\n```\none\ntwo\n```\nbelow")
        assertLooksFresh("start", e, layout)
        val endOfTwo = e.toString().indexOf("two") + 3
        edit(e, endOfTwo, endOfTwo, "\n") // Enter at the end of the series
        assertLooksFresh("enter at the end of mono", e, layout)
        edit(e, endOfTwo + 1, endOfTwo + 1, "three")
        assertLooksFresh("typed in the new line", e, layout)
        val oneStart = e.toString().indexOf("one")
        edit(e, oneStart, oneStart + 4, "") // "one\n": the first line of the series goes
        assertLooksFresh("first line deleted", e, layout)
        val twoStart = e.toString().indexOf("two")
        edit(e, twoStart + 3, twoStart + 4, "") // "\n" between "two" and "three": the two lines join
        assertLooksFresh("lines joined", e, layout)
    }

    @Test
    fun deletingTheMiddleLineOfAMonoSeries() {
        val (e, layout) = live("```\na\nb\nc\n```")
        edit(e, 2, 4, "") // "b\n"
        assertLooksFresh("middle line deleted", e, layout)
        edit(e, 2, 3, "") // "c"
        assertLooksFresh("last line emptied", e, layout)
    }

    @Test
    fun insertingAnItemInTheMiddleOfANumberedList() {
        val (e, layout) = live("1. a\n2. b\n3. c\nx")
        assertLooksFresh("start", e, layout)
        edit(e, 1, 1, "\nz") // a new item after "a"
        assertLooksFresh("item inserted", e, layout)
        assertEquals(listOf(1, 2, 3, 4), e.getSpans(0, e.length, ListItemSpan::class.java).sortedBy { e.getSpanStart(it) }.map { it.number })
        edit(e, 0, 2, "") // the first item goes ("a\n")
        assertLooksFresh("first item deleted", e, layout)
    }

    @Test
    fun changingTheTypeOfAnItemRenumbersTheRunsAround() {
        val (e, layout) = live("1. a\n2. b\n3. c\n4. d")
        // what EditRules does: swap the span of paragraph 2 for another kind, then refresh the neighbours
        val b = e.toString().indexOf("b")
        val old = e.getSpans(b, b + 1, BlockSpan::class.java).single()
        val start = e.getSpanStart(old)
        val end = e.getSpanEnd(old)
        e.removeSpan(old)
        e.setSpan(BlockSpan.create(ParagraphKind.ListItem(ListType.Bullet, 0), style)!!, start, end, BLOCK_SPAN_FLAGS)
        val w = Normalizer.affectedRange(e, start, end)
        Normalizer.refreshNeighbours(e, w.start, w.end)
        assertLooksFresh("number -> bullet", e, layout)
        // the bullet breaks the run: "c" starts again at 1
        assertEquals(listOf(1, 1, 1, 2), e.getSpans(0, e.length, ListItemSpan::class.java).sortedBy { e.getSpanStart(it) }.map { it.number })
    }
}
