package app.tessera.ui.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §6.9 / D13: what goes out and when. Every "nothing is emitted" check passes the whole debounce first, so it cannot pass by being early. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraEditTextEmitTest {

    @Test
    fun loadingEmitsNothing() {
        val h = HostFixture("# Title\n\n- a\n- b")
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun aLegacyNoteOpenedAndClosedWithoutAnEditEmitsNothing() {
        val h = HostFixture("* x\n1. 1. y")
        h.pass(1000)
        h.view.dispose()
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun reapplyingTheThemeEmitsNothing() {
        val h = HostFixture("# T\ntext")
        h.view.editorStyle = EngineTestStyle.create(imageLabel = "Other").copy(textColor = 0xFF123456.toInt())
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
        assertEquals("# T\ntext", h.md())
    }

    @Test
    fun switchingEditableEmitsNothingAndKeepsTextAndSelection() {
        val h = HostFixture("- **a**\n- b")
        h.select(1, 3)
        h.view.editable = false
        h.view.editable = true
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
        assertEquals("- **a**\n- b", h.md())
        assertEquals(1 to 3, h.view.selectionStart to h.view.selectionEnd)
    }

    @Test
    fun anExternalRebuildEmitsNothing() {
        val h = HostFixture("a")
        h.view.setMarkdown("b")
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
        assertEquals("b", h.md())
    }

    @Test
    fun disposeWithoutAnEditEmitsNothing() {
        val h = HostFixture("a")
        h.view.dispose()
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun anEditIsEmittedOnceAfterTheDebounce() {
        val h = HostFixture("a")
        h.caret(1)
        h.text.insert(1, "b")
        h.pass(299)
        assertTrue("not yet at 299 ms", h.emitted.isEmpty())
        h.pass(1)
        assertEquals(listOf("ab"), h.emitted)
        h.pass(2000)
        assertEquals("only once", 1, h.emitted.size)
    }

    @Test
    fun editsInsideTheDebounceMakeOneEmitWithTheLastText() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.pass(200)
        h.text.insert(2, "c")
        h.pass(200)
        assertTrue(h.emitted.isEmpty())
        h.pass(100)
        assertEquals(listOf("abc"), h.emitted)
    }

    @Test
    fun disposeEmitsAPendingEditAtOnceAndNothingAfter() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.view.dispose()
        assertEquals(listOf("ab"), h.emitted)
        h.pass(2000)
        assertEquals(1, h.emitted.size)
    }

    @Test
    fun aCommandIsAnEditToo() {
        val h = HostFixture("- a")
        h.caret(1)
        h.view.indent()
        h.pass(300)
        assertEquals(listOf("    - a"), h.emitted)
    }

    @Test
    fun undoIsEmittedAsAUserEdit() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
        h.view.undo()
        h.pass(300)
        assertEquals(listOf("ab", "a"), h.emitted)
        h.view.redo()
        h.pass(300)
        assertEquals(listOf("ab", "a", "ab"), h.emitted)
    }

    @Test
    fun anExternalMarkdownIsSkippedWhileAnEditIsPending() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.view.setMarkdown("remote")
        assertEquals("ab", h.md())
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
    }

    @Test
    fun anExternalMarkdownWithoutAPendingEditIsAppliedTheSelectionKeptAndTheHistoryCleared() {
        val h = HostFixture("hello world")
        h.text.insert(0, "x")
        h.pass(300) // emitted, nothing pending
        assertTrue(h.view.formatState().canUndo)
        h.caret(5)
        h.view.setMarkdown("hello there, world")
        assertEquals("hello there, world", h.md())
        assertEquals(5, h.view.selectionStart)
        assertFalse("history cleared", h.view.formatState().canUndo)
        h.pass(1000)
        assertEquals("the rebuild emitted nothing", 1, h.emitted.size)
    }

    @Test
    fun theSelectionIsClampedWhenTheNewTextIsShorter() {
        val h = HostFixture("hello world")
        h.caret(11)
        h.view.setMarkdown("hi")
        assertEquals(2, h.view.selectionStart)
    }

    @Test
    fun theEchoOfOurOwnEmitIsIgnored() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.pass(300)
        val before = h.view.text
        h.view.setMarkdown(h.emitted.last()) // the echo comes back through the note
        assertTrue("not rebuilt", before === h.view.text)
        assertTrue("history kept", h.view.formatState().canUndo)
    }

    @Test
    fun aLegacyNoteOpensAndTakesAnExternalUpdate() {
        val h = HostFixture("* x")
        assertEquals("- x", h.md()) // the canonical form of the open note; the raw string was not emitted
        h.view.setMarkdown("* y") // a collaborator's edit: the live document is not compared with the raw string
        assertEquals("- y", h.md())
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun anEditAndItsUndoInsideTheDebounceEmitsNothingNew() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.view.undo()
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun anOldIncomingValueRepeatedAfterOurEmitDoesNotRollTheTextBack() {
        val h = HostFixture("a")
        h.text.insert(1, "b")
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
        h.view.setMarkdown("a") // a recomposition before the note caught up: the old value again
        assertEquals("ab", h.md())
        h.view.setMarkdown("ab") // the echo of our emit
        assertEquals("ab", h.md())
        h.view.setMarkdown("c") // really new, nothing pending
        assertEquals("c", h.md())
        h.pass(1000)
        assertEquals(1, h.emitted.size)
    }

    @Test
    fun anEditAfterDisposeEmitsNothing() {
        val h = HostFixture("a")
        h.view.dispose()
        h.text.insert(1, "b")
        h.pass(1000)
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun theFormatStateKnowsAboutAPlainPasteAtOnce() {
        val h = HostFixture("")
        h.view.setClip("pasted")
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("pasted", h.md())
        assertTrue(h.states.last().canUndo)
        assertFalse(h.states.last().canRedo)
    }

    @Test
    fun aPasteAfterAnUndoClearsTheRedoInTheState() {
        val h = HostFixture("")
        h.text.insert(0, "a")
        h.view.undo()
        assertTrue(h.states.last().canRedo)
        h.view.setClip("p")
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertTrue(h.states.last().canUndo)
        assertFalse(h.states.last().canRedo)
    }

    // --- a value from outside that came while an edit was pending (§6.9: the pending emit reconciles)

    @Test
    fun anEditThatWasPutBackAppliesTheRemoteValueThatWasSkipped_typedAndErased() {
        val h = HostFixture("a")
        h.caret(1)
        h.ic().commitText("b", 1)
        h.view.setMarkdown("remote") // a co-author's edit arrives while ours is pending: skipped
        assertEquals("ab", h.md())
        h.ic().deleteSurroundingText(1, 0) // and our edit is erased
        h.pass(300)
        assertEquals("remote", h.md())
        assertTrue("nothing of ours to emit", h.emitted.isEmpty())
    }

    @Test
    fun anEditThatWasPutBackAppliesTheRemoteValueThatWasSkipped_undone() {
        val h = HostFixture("a")
        h.caret(1)
        h.ic().commitText("b", 1)
        h.view.setMarkdown("remote")
        h.view.undo()
        h.pass(300)
        assertEquals("remote", h.md())
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun anEditThatWasPutBackAppliesTheRemoteValueThatWasSkipped_checkboxTappedTwice() {
        val h = HostFixture("- [ ] a")
        h.view.toggleCheckAt(0)
        h.view.setMarkdown("remote")
        h.view.toggleCheckAt(0)
        h.pass(300)
        assertEquals("remote", h.md())
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun theSameThroughTheBackgroundWriter() {
        val h = HostFixture("a")
        val jobs = ArrayList<Runnable>()
        h.view.emitExecutor = java.util.concurrent.Executor { jobs += it }
        h.caret(1)
        h.ic().commitText("b", 1)
        h.view.setMarkdown("remote")
        h.ic().deleteSurroundingText(1, 0)
        h.pass(300)
        jobs.forEach { it.run() }
        h.pass(10)
        assertEquals("remote", h.md())
        assertTrue(h.emitted.isEmpty())
    }

    @Test
    fun anEditThatGoesOutIsNotRolledBackByTheRemoteValueThatWasSkippedBehindIt() {
        val h = HostFixture("a")
        h.caret(1)
        h.ic().commitText("b", 1)
        h.view.setMarkdown("remote")
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
        assertEquals("ab", h.md())
        h.pass(1000)
        assertEquals("the skipped value is not applied behind a real edit", "ab", h.md())
        h.view.setMarkdown("remote") // the same value again is a repeat
        assertEquals("ab", h.md())
    }

    @Test
    fun aStaleValueOfTheNoteIsNotTakenForNewsWhenTheEditIsPutBack() {
        val h = HostFixture("a")
        h.caret(1)
        h.ic().commitText("b", 1)
        h.pass(300) // "ab" is out; the note has not caught up yet
        h.ic().commitText("c", 1)
        h.view.setMarkdown("a") // the stale value of the recomposition, skipped while "abc" is pending
        h.ic().deleteSurroundingText(1, 0) // "abc" is erased back to what was emitted
        h.pass(300)
        assertEquals("the stale value is not applied", "ab", h.md())
        assertEquals(listOf("ab"), h.emitted)
    }

    @Test
    fun theLastOfSeveralRemoteValuesIsTheOneApplied() {
        val h = HostFixture("a")
        h.caret(1)
        h.ic().commitText("b", 1)
        h.view.setMarkdown("remote 1")
        h.view.setMarkdown("remote 2")
        h.ic().deleteSurroundingText(1, 0)
        h.pass(300)
        assertEquals("remote 2", h.md())
    }
}
