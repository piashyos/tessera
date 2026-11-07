package app.tessera.ui.editor.engine

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every text of [EngineTextCorpus] through the same walks: load, Backspace from the end to nothing (by every path), typing and Enter at spread
 * positions, select all + delete, copy all + paste, undo of a few edits and redo of them. After each step the invariants of
 * [EngineFuzz.Session.check] hold. The default run takes every third document (about 600 of them, a few minutes in all would be too long
 * for a unit run); the deep mode (`ENGINE_FUZZ_DEEP=1`) takes all of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineCorpusTest {
    private val docs: List<String> by lazy { EngineTextCorpus.markdowns.let { all -> if (EngineFuzz.deep) all else all.filterIndexed { i, _ -> i % 3 == 0 } } }

    private fun positions(len: Int, n: Int) = (0..n).map { it * len / n }.distinct()

    private fun session(md: String) = EngineFuzz.Session(md)

    @Test
    fun everyDocumentLoadsToItsCanonicalForm() {
        for ((i, md) in docs.withIndex()) session(md).use { s ->
            s.check("doc $i load")
            assertEquals("doc $i: the editor holds the canonical form", NoteMarkdown.serialize(NoteMarkdown.parse(md)), s.h.md())
        }
    }

    /** Backspace from the end of the text until nothing is left, by `deleteSurroundingText(1, 0)`, the DEL key event and the hardware key. */
    @Test
    fun backspaceFromTheEndEmptiesEveryDocumentByEveryPath() {
        for ((i, md) in docs.withIndex()) for (path in 0..2) session(md).use { s ->
            val h = s.h
            h.caret(h.text.length)
            val ic = h.view.onCreateInputConnection(EditorInfo())!!
            val limit = 3 * h.text.length + 60
            var n = 0
            while (h.text.isNotEmpty() && n < limit) {
                when (path) {
                    0 -> { // as Gboard: a selection (a selected object) is collapsed to its end first, then one character is deleted
                        if (h.view.selectionStart != h.view.selectionEnd) ic.setSelection(h.view.selectionEnd, h.view.selectionEnd)
                        ic.deleteSurroundingText(1, 0)
                    }
                    1 -> { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)); ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)) }
                    else -> h.key(KeyEvent.KEYCODE_DEL)
                }
                if (n % 7 == 0 || h.text.length < 4) s.check("doc $i path $path backspace #$n")
                n++
            }
            s.check("doc $i path $path end")
            assertTrue("doc $i path $path: Backspace made no progress (${h.text.length} chars left after $n presses): ${md.take(80)}", h.text.isEmpty() || (h.text.length == 1 && h.text[0] == '​'))
        }
    }

    @Test
    fun typingAndEnterAtSpreadPositionsKeepEveryInvariant() {
        for ((i, md) in docs.withIndex()) {
            val len = session(md).use { it.h.text.length }
            for (p in positions(len, 6)) for (g in listOf("x", "👍🏽", "\n")) session(md).use { s ->
                s.h.caret(p)
                s.h.view.onCreateInputConnection(EditorInfo())!!.commitText(g, 1)
                s.check("doc $i at $p type ${g.map { it.code }}")
            }
        }
    }

    @Test
    fun selectAllThenDeleteOrReplaceLeavesAValidDocument() {
        for ((i, md) in docs.withIndex()) for (how in 0..2) session(md).use { s ->
            val h = s.h
            h.select(0, h.text.length)
            val ic = h.view.onCreateInputConnection(EditorInfo())!!
            when (how) {
                0 -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                1 -> ic.commitText("я", 1)
                else -> ic.commitText("\n", 1)
            }
            s.check("doc $i select all, how $how")
        }
    }

    @Test
    fun copyAllAndPasteAtTheEndLosesNothing() {
        for ((i, md) in docs.withIndex()) {
            if (md.length > 3000) continue
            session(md).use { s ->
                val h = s.h
                val before = h.text.length
                h.select(0, h.text.length)
                h.view.onTextContextMenuItem(android.R.id.copy)
                h.caret(h.text.length)
                h.view.onTextContextMenuItem(android.R.id.paste)
                s.check("doc $i copy all and paste")
                assertTrue("doc $i: the text did not grow by a paste of itself (${h.text.length} vs $before)", h.text.length >= before)
            }
        }
    }

    @Test
    fun undoGivesBackEveryEditByteForByteAndRedoRepeatsIt() {
        for ((i, md) in docs.withIndex()) session(md).use { s ->
            val h = s.h
            val states = arrayListOf(h.md())
            val ic = h.view.onCreateInputConnection(EditorInfo())!!
            val len = h.text.length
            for ((k, p) in positions(len, 4).take(4).withIndex()) {
                h.pass(600)
                h.caret(minOf(p, h.text.length))
                when (k % 3) {
                    0 -> ic.commitText("ab", 1)
                    1 -> ic.commitText("\n", 1)
                    else -> h.view.toggleKind(ParagraphKind.Quote)
                }
                if (h.md() != states.last()) states += h.md()
            }
            s.check("doc $i after edits")
            for (k in states.size - 2 downTo 0) { h.view.undo(); assertEquals("doc $i undo to state $k", states[k], h.md()) }
            for (k in 1 until states.size) { h.view.redo(); assertEquals("doc $i redo to state $k", states[k], h.md()) }
            s.check("doc $i after redo")
        }
    }

    @Test
    fun bigDocumentsKeepTheInvariantsToo() {
        for ((i, md) in EngineTextCorpus.bigMarkdowns.withIndex()) session(md).use { s ->
            s.check("big $i load")
            val h = s.h
            val ic = h.view.onCreateInputConnection(EditorInfo())!!
            for (p in positions(h.text.length, 5)) { h.caret(p); ic.commitText("x", 1); ic.commitText("\n", 1); ic.deleteSurroundingText(1, 0) }
            s.check("big $i edited")
            h.caret(h.text.length)
            repeat(200) { ic.deleteSurroundingText(1, 0) }
            s.check("big $i backspaced")
        }
    }
}
