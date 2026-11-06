package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.ui.editor.engine.spans.MarkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §4 rule 3 / §6.6: a mark that partly crosses `Code` is clipped at the code already in the `Editable`, so the screen shows what `read` returns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkCodeClipTest {
    private val style = EngineTestStyle.create()
    private val rules = EditRules(style)

    private fun ed(md: String): Editable = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), style))
    private fun md(e: Editable) = NoteMarkdown.serialize(DocBinder.read(e))
    private fun bold(e: Editable) = e.getSpans(0, e.length, MarkSpan::class.java).filter { it.mark == Mark.Bold }
        .map { e.getSpanStart(it) to e.getSpanEnd(it) }.sortedBy { it.first }

    /** What `read` makes of the marks must be what the editable holds (no spaces in these texts, so rule 2 does not interfere). */
    private fun assertScreenIsRead(e: Editable) {
        val screen = e.getSpans(0, e.length, MarkSpan::class.java).map { Triple(e.getSpanStart(it), e.getSpanEnd(it), it.mark.toString()) }.toSet()
        val read = (DocBinder.read(e).blocks.single() as app.tessera.core.text.notedoc.NoteBlock.Text).content.marks
            .map { Triple(it.start, it.end, it.mark.toString()) }.toSet()
        assertEquals(read, screen)
    }

    @Test
    fun boldOverASelectionThatEndsInsideTheCodeStopsBeforeIt() {
        val e = ed("ab`cd`ef") // text abcdef, code 2..4
        rules.toggleMark(e, 1, 3, Mark.Bold)
        assertEquals(listOf(1 to 2), bold(e))
        assertScreenIsRead(e)
    }

    @Test
    fun boldOverASelectionThatStartsInsideTheCodeStartsAfterIt() {
        val e = ed("ab`cd`ef")
        rules.toggleMark(e, 3, 6, Mark.Bold)
        assertEquals(listOf(4 to 6), bold(e))
        assertScreenIsRead(e)
    }

    @Test
    fun boldOverASelectionThatCoversTheCodeWholeStaysOverIt() {
        val e = ed("ab`cd`ef")
        rules.toggleMark(e, 1, 5, Mark.Bold)
        assertEquals(listOf(1 to 5), bold(e))
        assertScreenIsRead(e)
    }

    @Test
    fun boldInsideTheCodeIsNotApplied() {
        val e = ed("ab`cdef`gh")
        rules.toggleMark(e, 3, 5, Mark.Bold)
        assertTrue(bold(e).isEmpty())
        assertEquals("ab`cdef`gh", md(e))
    }

    @Test
    fun theSecondPressOverTheSameSelectionTakesTheBoldOff() {
        val e = ed("ab`cd`ef")
        rules.toggleMark(e, 1, 3, Mark.Bold)
        rules.toggleMark(e, 1, 3, Mark.Bold)
        assertTrue(bold(e).isEmpty())
        val tp = TypingAttributes(style)
        rules.toggleMark(e, 1, 3, Mark.Bold)
        assertTrue(Mark.Bold in FormatState.of(e, 1, 3, tp).marks)
    }
}
