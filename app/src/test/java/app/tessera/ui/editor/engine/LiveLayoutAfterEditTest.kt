package app.tessera.ui.editor.engine

import app.tessera.core.text.notedoc.Mark
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the layout shows right after an edit whose spans are set from `afterTextChanged` (the normalizer, the auto-replace, the typing
 * attributes) is what a fresh build of the same note shows: line tops, numbers, the spacing the spans measured with. A neighbour below the
 * edit keeps its old measures when the layout is not told about the span that was set there (review of Chunk 4c).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveLayoutAfterEditTest {
    @Test
    fun anAutoReplaceRemeasuresTheNeighbourBelow() {
        val s = EngineFuzz.Session("milk\n## Heading")
        s.h.caret(0)
        val ic = s.h.ic()
        ic.commitText("-", 1)
        ic.commitText(" ", 1) // "- " becomes a list item: the gap above the heading is the list's, not the Body's
        s.check("auto-replace")
        s.checkLayout("auto-replace")
    }

    @Test
    fun aSelectionAcrossParagraphsReplacedByTextRemeasuresTheItemBelow() {
        val s = EngineFuzz.Session("- a\nbody\n- b")
        s.h.select(1, 6)
        s.h.ic().commitText("z", 1)
        s.check("replace across")
        s.checkLayout("replace across")
    }

    @Test
    fun aTypedCharacterWithABoldTypingAttributeKeepsTheLayout() {
        val s = EngineFuzz.Session("title\n> quote\n> more\n- item\n```\ncode\n```\nend")
        for (p in listOf(2, 8, 14, 22, 28)) {
            s.h.caret(p)
            s.h.view.toggleMark(Mark.Bold)
            s.h.ic().commitText("x", 1) // the MarkSpan is set in afterTextChanged
            s.checkLayout("bold typed at $p")
        }
        s.check("bold typed")
    }

    @Test
    fun deletingAndSplittingAroundSeriesKeepsTheLayout() {
        val s = EngineFuzz.Session("a\n- b\n- c\n> q1\n> q2\n```\nm1\nm2\n```\nz")
        val ic = s.h.ic()
        for (p in listOf(3, 6, 11, 14, 20)) {
            s.h.caret(p)
            ic.commitText("\n", 1)
            s.checkLayout("enter at $p")
            ic.deleteSurroundingText(1, 0)
            s.checkLayout("back at $p")
        }
    }
}
