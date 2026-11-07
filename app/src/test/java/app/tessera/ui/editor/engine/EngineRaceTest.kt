package app.tessera.ui.editor.engine

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import app.tessera.core.text.notedoc.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Races, deterministic: the main `Looper` is driven by hand (`HostFixture.pass`), so the 300 ms debounce of the emit, the 500 ms of a typing
 * run and the order of the host's calls are exactly what each test says (spec §6.7, §6.9). After each scenario the invariants of
 * [EngineFuzz.Session.check] hold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineRaceTest {
    private fun session(md: String) = EngineFuzz.Session(md)
    private fun EngineFuzz.Session.ic() = h.view.onCreateInputConnection(EditorInfo())!!

    @Test
    fun theDebounceAgainstAnExternalSetMarkdown() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        s.ic().commitText("b", 1) // pending: not emitted yet
        h.pass(100)
        assertEquals(emptyList<String>(), h.emitted)
        h.view.setMarkdown("external") // arrives inside the debounce: skipped, the pending emit reconciles
        assertEquals("ab", h.md())
        h.pass(250)
        assertEquals(listOf("ab"), h.emitted)
        h.view.setMarkdown("ab") // the echo of our emit: ignored
        assertEquals("ab", h.md())
        h.view.setMarkdown("external 2") // a new value with nothing pending: applied
        assertEquals("external 2", h.md())
        assertEquals("nothing is emitted for a load", listOf("ab"), h.emitted)
        s.check("race 1")
    }

    @Test
    fun anExternalUpdateThenAnEditEmitsBoth() {
        val s = session("a")
        val h = s.h
        h.view.setMarkdown("external")
        assertEquals("external", h.md())
        h.caret(h.text.length)
        s.ic().commitText("!", 1)
        h.pass(300)
        assertEquals(listOf("external!"), h.emitted)
        s.check("race 1b")
    }

    @Test
    fun theStaleValueOfTheNoteAfterOurEmitDoesNotRollTheTextBack() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        s.ic().commitText("b", 1)
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
        s.ic().commitText("c", 1)
        h.view.setMarkdown("a") // the recomposition still carries the value the note had before our emit
        assertEquals("abc", h.md())
        h.pass(300)
        assertEquals(listOf("ab", "abc"), h.emitted)
        h.view.setMarkdown("a") // and again after the second emit: still the old one
        assertEquals("abc", h.md())
        s.check("race 2")
    }

    @Test
    fun undoAndRedoInsideAPendingDebounce() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        s.ic().commitText("b", 1)
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
        h.pass(600) // a new typing run
        s.ic().commitText("c", 1)
        assertEquals("abc", h.md())
        h.view.undo() // back to the emitted state before the debounce fires
        assertEquals("ab", h.md())
        h.pass(300)
        assertEquals("the document is what was emitted already: nothing to say", listOf("ab"), h.emitted)
        h.view.redo()
        assertEquals("abc", h.md())
        h.view.undo()
        h.view.redo()
        h.pass(300)
        assertEquals(listOf("ab", "abc"), h.emitted)
        h.view.undo()
        h.view.undo() // second step back: the state before "b"
        assertEquals("a", h.md())
        h.pass(300)
        assertEquals(listOf("ab", "abc", "a"), h.emitted)
        s.check("race 3")
    }

    @Test
    fun undoWhileTheDebounceIsPendingDoesNotLoseTheEdit() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        s.ic().commitText("b", 1)
        h.pass(200)
        h.view.undo()
        h.pass(50)
        assertEquals("a", h.md())
        assertEquals("edited and put back before the emit: nothing is emitted", emptyList<String>(), h.emitted)
        h.view.redo()
        h.pass(300)
        assertEquals(listOf("ab"), h.emitted)
    }

    @Test
    fun pasteDuringComposing() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        val ic = s.ic()
        ic.setComposingText("при", 1)
        assertTrue(BaseInputConnection.getComposingSpanStart(h.text) >= 0)
        h.view.setClip("XY")
        h.view.onTextContextMenuItem(android.R.id.paste)
        s.check("race 4 after paste")
        assertTrue("both the composing text and the pasted one are in the note: ${h.text}", h.text.toString().contains("XY") && h.text.toString().contains("при"))
        ic.setComposingText("прив", 1) // the IME goes on with its word
        s.check("race 4 after the IME went on")
        ic.finishComposingText()
        s.check("race 4 end")
        h.pass(300)
        assertEquals(h.md(), h.emitted.last())
    }

    @Test
    fun disposeInTheMiddleOfTheDebounce() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        s.ic().commitText("b", 1)
        h.pass(100)
        h.view.dispose()
        assertEquals("dispose emits the pending edit at once", listOf("ab"), h.emitted)
        h.pass(2000)
        assertEquals("and nothing after", listOf("ab"), h.emitted)
        s.check("race 5")
    }

    @Test
    fun disposeWithNothingPendingEmitsNothing() {
        val s = session("a")
        s.h.view.dispose()
        s.h.pass(2000)
        assertEquals(emptyList<String>(), s.h.emitted)
    }

    @Test
    fun anEditAfterDisposeWorksAndEmitsNothing() {
        val s = session("a")
        val h = s.h
        h.view.dispose()
        h.caret(1)
        s.ic().commitText("b", 1)
        h.view.toggleMark(Mark.Bold)
        h.key(KeyEvent.KEYCODE_ENTER)
        h.view.undo()
        h.pass(2000)
        h.view.flushPendingEdit()
        assertEquals(emptyList<String>(), h.emitted)
        s.check("race 6")
    }

    @Test
    fun anExternalUpdateDuringComposing() {
        val s = session("a")
        val h = s.h
        h.caret(1)
        val ic = s.ic()
        ic.setComposingText("при", 1) // a composing edit is a user edit: pending
        h.view.setMarkdown("external")
        assertEquals("skipped while an edit is pending", "aпри", h.md())
        ic.commitText("привет", 1)
        h.pass(300)
        assertEquals(listOf("aпривет"), h.emitted)
        s.check("race 7a")

        // the pending edit is out (flushed) while the IME still composes: the external update applies and the composition goes on
        val t = session("a")
        val g = t.h
        g.caret(1)
        val ic2 = t.ic()
        ic2.setComposingText("при", 1)
        g.view.flushPendingEdit()
        g.view.setMarkdown("external")
        assertEquals("external", g.md())
        t.check("race 7b after the external update")
        ic2.setComposingText("приве", 1)
        t.check("race 7b the IME goes on")
        ic2.commitText("привет", 1)
        t.check("race 7b committed")
        assertTrue("the word is in the note: ${g.text}", g.text.toString().contains("привет"))
        g.pass(300)
        assertEquals(g.md(), g.emitted.last())
    }

    @Test
    fun anAutoReplaceDuringComposing() {
        // Gboard keeps the marker as a composing word and commits it with the space
        for ((marker, typed) in listOf("-" to "- ", "1." to "1. ", "#" to "# ", ">" to "> ", "[]" to "[] ")) {
            val s = session("")
            val h = s.h
            val ic = s.ic()
            ic.setComposingText(marker, 1)
            s.check("race 8 composing '$marker'")
            ic.commitText(typed, 1)
            s.check("race 8 committed '$typed'")
            assertTrue("'$typed' turned into a paragraph kind, not text: ${h.text}", h.text.toString().replace("​", "").isEmpty())
            h.view.undo()
            assertEquals("undo right after the replacement gives the typed characters back", typed, h.text.toString())
            s.check("race 8 undone '$typed'")
        }
    }

    @Test
    fun anAutoReplaceDoesNotFireOnAPaste() {
        val s = session("")
        s.h.view.setClip("- ")
        s.h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals("- ", s.h.text.toString())
        s.check("race 8b")
    }

    @Test
    fun aTypedMarkerAndSpaceBecomeAKindAndUndoGivesTheTypedCharactersBack() {
        val s = session("")
        val h = s.h
        val ic = s.ic()
        ic.commitText("-", 1)
        h.pass(600) // the pause that ends a typing run
        ic.commitText(" ", 1)
        assertEquals("an empty dash item", "+ ", h.md())
        assertTrue(h.text.toString().replace("\u200B", "").isEmpty())
        h.view.undo()
        assertEquals("the typed characters, not the marker alone", "- ", h.text.toString())
        h.view.undo()
        assertEquals("-", h.text.toString())
        h.view.undo()
        assertEquals("", h.text.toString())
        s.check("race 8c")
    }
}
