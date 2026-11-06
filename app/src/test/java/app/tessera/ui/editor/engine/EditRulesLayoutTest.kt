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
import app.tessera.ui.editor.engine.spans.ListItemSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A live [DynamicLayout] re-measures only the changed paragraphs: after a command the neighbours (the edges of a mono series,
 * the numbers of a list run) must look as a fresh layout of the same text. Run this class on its own if `RenderNodeNatives` flakes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EditRulesLayoutTest {
    private val style = EngineTestStyle.create()
    private val rules = EditRules(style)
    private val width = 400
    private val size = style.bodyTextSizePx
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = size; color = EngineTestStyle.TEXT }

    private fun live(md: String): Pair<Editable, DynamicLayout> {
        val e = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), style))
        return e to DynamicLayout.Builder.obtain(e, paint, width).build()
    }

    private fun render(l: Layout): IntArray {
        val b = Bitmap.createBitmap(width, l.height + 4, Bitmap.Config.ARGB_8888)
        Canvas(b).also { it.drawColor(Color.WHITE); l.draw(it) }
        return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    }

    private fun assertLooksFresh(label: String, e: Editable, layout: DynamicLayout) {
        EngineAssertions.assertInvariant1(e, label)
        val fresh = DocBinder.build(DocBinder.read(e), style)
        DocBinder.setObjectWidth(fresh, width)
        val staticLayout = StaticLayout.Builder.obtain(fresh, 0, fresh.length, paint, width).build()
        assertEquals("$label: text", fresh.toString(), e.toString())
        assertEquals("$label: line count", staticLayout.lineCount, layout.lineCount)
        for (i in 0..staticLayout.lineCount) assertEquals("$label: top of line $i", staticLayout.getLineTop(i), layout.getLineTop(i))
        val live = e.getSpans(0, e.length, ListItemSpan::class.java).sortedBy { e.getSpanStart(it) }.map { it.number }
        val want = fresh.getSpans(0, fresh.length, ListItemSpan::class.java).sortedBy { fresh.getSpanStart(it) }.map { it.number }
        assertEquals("$label: numbers", want, live)
        assertTrue("$label: picture", render(layout).contentEquals(render(staticLayout)))
    }

    private fun at(e: Editable, needle: String) = e.toString().indexOf(needle)

    @Test
    fun monoOnALineBetweenTwoMonoSeriesJoinsThem() {
        val (e, layout) = live("```\na\n```\nx\n```\nc\n```")
        assertLooksFresh("start", e, layout)
        val p = at(e, "x")
        rules.toggleKind(e, p, p, ParagraphKind.Mono)
        assertLooksFresh("x became mono", e, layout)
    }

    @Test
    fun backspaceAtTheStartOfTheFirstMonoLineOfASeries() {
        val (e, layout) = live("```\na\nb\nc\n```")
        rules.backspace(e, 0, 0)
        assertLooksFresh("first line reset to Body", e, layout)
        assertEquals(ParagraphKind.Body, kindOf(e, 0))
    }

    @Test
    fun monoOffOnTheMiddleLineOfASeries() {
        val (e, layout) = live("```\na\nb\nc\n```")
        val p = at(e, "b")
        rules.toggleKind(e, p, p, ParagraphKind.Mono)
        assertLooksFresh("middle line is Body", e, layout)
    }

    @Test
    fun enterAtTheEndOfMonoAddsALineToTheSeries() {
        val (e, layout) = live("above\n```\none\ntwo\n```\nbelow")
        val p = at(e, "two") + 3
        rules.enter(e, p, p)
        assertLooksFresh("enter in mono", e, layout)
    }

    @Test
    fun numbersFollowEnterIndentKindAndBackspaceInTheMiddleOfAList() {
        val (e, layout) = live("1. a\n2. b\n3. c\n4. d")
        var p = at(e, "b") + 1
        rules.enter(e, p, p)
        assertLooksFresh("enter", e, layout)
        p = at(e, "c")
        rules.changeLevel(e, p, p, +1)
        assertLooksFresh("indent", e, layout)
        rules.toggleKind(e, p, p, ParagraphKind.ListItem(ListType.Bullet, 0))
        assertLooksFresh("number -> bullet", e, layout)
        p = at(e, "d")
        rules.backspace(e, paragraphStartOf(e, p), paragraphStartOf(e, p))
        assertLooksFresh("backspace at the start of an item", e, layout)
    }

    @Test
    fun theDividerRestartsTheNumbersBelowIt() {
        rules.objectWidthPx = width // the host gives new objects the width of the text area
        val (e, layout) = live("1. a\n2. b\n3. c")
        val p = at(e, "a") + 1
        rules.insertHorizontalRule(e, p)
        assertLooksFresh("divider", e, layout)
    }

    @Test
    fun theDividerAfterAMonoLine() {
        rules.objectWidthPx = width
        val (e, layout) = live("```\na\nb\n```\nbelow")
        val p = at(e, "b") + 1
        rules.insertHorizontalRule(e, p)
        assertLooksFresh("divider after the last mono line", e, layout)
    }

    @Test
    fun theDividerAfterAnEmptyMonoLine() {
        rules.objectWidthPx = width
        val (e, layout) = live("```\na\n```\nbelow")
        val p = at(e, "a") + 1
        val sel = rules.enter(e, p, p)
        assertLooksFresh("enter in mono", e, layout)
        rules.insertHorizontalRule(e, sel.start)
        assertLooksFresh("divider after an empty mono line", e, layout)
    }

    private fun paragraphStartOf(e: Editable, p: Int) = paragraphStart(e, p)
}
