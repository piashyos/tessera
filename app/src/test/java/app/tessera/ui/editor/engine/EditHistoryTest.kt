package app.tessera.ui.editor.engine

import app.tessera.ui.editor.engine.EditHistory.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §6.7. Pure state, so plain JUnit: the clock is a parameter. */
class EditHistoryTest {
    private fun s(md: String) = Snapshot(md, 0, 0)

    @Test
    fun typingWithPausesUnderHalfASecondIsOneStep() {
        val h = EditHistory()
        h.typing(0) { s("") }
        h.typing(400) { error("same step") }
        h.typing(800) { error("same step") } // 400 ms after the previous edit: the pause counts from the last edit
        assertEquals(s(""), h.undo(s("abc")))
        assertFalse(h.canUndo)
    }

    @Test
    fun aPauseOverHalfASecondStartsANewStep() {
        val h = EditHistory()
        h.typing(0) { s("") }
        h.typing(501) { s("a") }
        assertEquals(s("a"), h.undo(s("ab")))
        assertEquals(s(""), h.undo(s("a")))
        assertNull(h.undo(s("")))
    }

    @Test
    fun aCommandIsAStepOfItsOwnAndEndsTheRun() {
        val h = EditHistory()
        h.typing(0) { s("") }
        h.step(s("a")) // a button, right after typing
        h.typing(10) { s("a!") } // typing right after the command is a new step
        assertEquals(s("a!"), h.undo(s("a!b")))
        assertEquals(s("a"), h.undo(s("a!")))
        assertEquals(s(""), h.undo(s("a")))
    }

    @Test
    fun theLimitDropsTheOldestStep() {
        val h = EditHistory(limit = 100)
        for (i in 0 until 130) h.step(s("$i"))
        val popped = generateSequence { h.undo(s("x")) }.toList()
        assertEquals(100, popped.size)
        assertEquals(s("129"), popped.first())
        assertEquals(s("30"), popped.last()) // 0..29 were dropped
    }

    @Test
    fun undoThenRedoWalksTheStatesAndANewEditDropsTheRedo() {
        val h = EditHistory()
        h.step(s("1"))
        h.step(s("2"))
        assertEquals(s("2"), h.undo(s("3")))
        assertTrue(h.canRedo)
        assertEquals(s("3"), h.redo(s("2")))
        assertEquals(s("2"), h.undo(s("3")))
        h.step(s("2")) // a new edit after an undo
        assertFalse(h.canRedo)
    }

    @Test
    fun clearForgetsEverything() {
        val h = EditHistory()
        h.step(s("1"))
        h.undo(s("2"))
        h.clear()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        h.typing(5) { s("fresh") } // a new run starts after a clear even at the same time
        assertEquals(s("fresh"), h.undo(s("x")))
    }

    @Test
    fun anUndoEndsTheTypingRun() {
        val h = EditHistory()
        h.typing(0) { s("") }
        h.undo(s("a"))
        h.typing(100) { s("") } // within 500 ms of the old run, yet a new step
        assertTrue(h.canUndo)
    }
}
