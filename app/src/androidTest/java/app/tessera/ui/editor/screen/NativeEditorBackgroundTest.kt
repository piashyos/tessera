package app.tessera.ui.editor.screen

import android.view.inputmethod.BaseInputConnection
import androidx.lifecycle.Lifecycle
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The open note goes to the background (`ON_STOP`, as Home or another app on top) and comes back. While it is away the edit in
 * flight is saved (feature-5: flush on `ON_STOP` — the app may be killed there); when it is back the editor shows it exactly once,
 * typing goes on, and what is saved in the end has nothing doubled or lost.
 */
@HiltAndroidTest
class NativeEditorBackgroundTest : NativeAppTest() {

    private fun background() {
        app.scenario!!.moveToState(Lifecycle.State.CREATED)
        app.settle(300)
    }

    private fun foreground(): TesseraEditText {
        app.scenario!!.moveToState(Lifecycle.State.RESUMED)
        return app.awaitEditor()
    }

    @Test
    fun anEditInFlightIsSavedWhileAwayAndShownOnceWhenBack() {
        app.launch()
        app.seed("qa-bg-1", "Away\none", BASE)
        val e = app.open("qa-bg-1")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("XYZ", 1) } // the 300 ms emit and the 500 ms autosave have not run
        background()
        assertEquals("saved while in the background (the process may die here)", "Away\noneXYZ", app.entity("qa-bg-1")!!.content)
        val back = foreground()
        assertEquals("shown once when back", "Away\noneXYZ", app.md(back))
        app.ui { back.setSelection(back.text!!.length) }
        app.ime(back) { commitText("!", 1) }
        app.back()
        assertEquals("saved once", "Away\noneXYZ!", app.entity("qa-bg-1")!!.content)
    }

    @Test
    fun aComposedWordGoesAwayAndComesBackOnce() {
        app.launch()
        app.seed("qa-bg-2", "Away\n- [ ] item ", BASE)
        val e = app.open("qa-bg-2")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { setComposingText("wor", 1) }
        background()
        assertEquals("the composed word is saved while away", "Away\n- [ ] item wor", app.entity("qa-bg-2")!!.content)
        val back = foreground()
        assertEquals("shown once when back", "Away\n- [ ] item wor", app.md(back))
        // a keyboard that comes back goes on with its word if the region is still there, or starts the next one if not
        app.ime(back) {
            if (BaseInputConnection.getComposingSpanStart(back.text!!) >= 0) setComposingText("word", 1) else commitText("d", 1)
            finishComposingText()
        }
        assertEquals("the word is finished, not doubled", "Away\n- [ ] item word", app.md(back))
        app.back()
        assertEquals("Away\n- [ ] item word", app.entity("qa-bg-2")!!.content)
    }

    @Test
    fun goingAwayAndBackAgainAndAgainWhileTypingLosesAndDoublesNothing() {
        app.launch()
        app.seed("qa-bg-3", "Away\n", BASE)
        var e = app.open("qa-bg-3")
        val typed = StringBuilder()
        repeat(6) { round ->
            app.ui { e.setSelection(e.text!!.length) }
            val piece = "r$round "
            piece.forEach { ch -> app.ime(e) { commitText(ch.toString(), 1) } }
            typed.append(piece)
            if (round % 2 == 0) {
                app.ime(e) { commitText("\n", 1) }
                typed.append('\n')
            }
            background()
            assertEquals("round $round: saved while away", "Away\n$typed", app.entity("qa-bg-3")!!.content)
            e = foreground()
            assertEquals("round $round: shown once when back", "Away\n$typed", app.md(e))
        }
        val md = app.md(e)
        assertEquals("fixed point", md, NoteMarkdown.serialize(NoteMarkdown.parse(md)))
        app.back()
        assertEquals("saved once", "Away\n$typed", app.entity("qa-bg-3")!!.content)
    }

    @Test
    fun undoAfterComingBackUndoesTheLastEditNotTheSave() {
        app.launch()
        app.seed("qa-bg-4", "Away\none", BASE)
        val e = app.open("qa-bg-4")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText(" two", 1) }
        background()
        val back = foreground()
        app.ui { back.undo() }
        assertEquals("undo after the trip takes the typing back (the save on ON_STOP is not a step of its own)", "Away\none", app.md(back))
        app.ui { back.redo() }
        assertEquals("Away\none two", app.md(back))
        app.back()
        assertEquals("Away\none two", app.entity("qa-bg-4")!!.content)
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
