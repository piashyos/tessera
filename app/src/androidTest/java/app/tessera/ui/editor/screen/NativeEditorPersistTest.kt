package app.tessera.ui.editor.screen

import android.view.KeyEvent
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec §9 item 3: every construction of stage 1 survives "edit → leave the note → open it again" in the real app. The edit is made
 * the way the screen makes it — the toolbar's commands on the editor, typing through its `InputConnection` — then Back; the saved
 * `content` must be the Markdown the editor showed, and the reopened editor must show the same document (Markdown, kinds, text).
 */
@HiltAndroidTest
class NativeEditorPersistTest : NativeAppTest() {

    private class Case(val name: String, val seed: String, val edit: AppDriver.(TesseraEditText) -> Unit)

    private fun item(t: ListType, level: Int = 0) = ParagraphKind.ListItem(t, level)

    /** The caret at the end of "target" — the last paragraph of every seed. */
    private fun AppDriver.atTarget(e: TesseraEditText) = ui { e.setSelection(e.text!!.length) }

    private fun AppDriver.selectWord(e: TesseraEditText, word: String) = ui {
        val at = e.text.toString().lastIndexOf(word)
        e.setSelection(at, at + word.length)
    }

    private fun kind(name: String, k: ParagraphKind, seed: String = SEED) = Case(name, seed) { e -> atTarget(e); ui { e.toggleKind(k) } }

    /** Typing as a keyboard starting at the caret: a word in composition elsewhere is finished first. */
    private fun AppDriver.type(e: TesseraEditText, s: String) {
        ime(e) { finishComposingText() }
        s.forEach { ch -> ime(e) { commitText(ch.toString(), 1) } }
    }

    private val cases = listOf(
        kind("Title", ParagraphKind.Title),
        kind("Heading", ParagraphKind.Heading),
        kind("Subheading", ParagraphKind.Subheading),
        kind("Quote", ParagraphKind.Quote),
        kind("Mono", ParagraphKind.Mono),
        kind("Bullet", item(ListType.Bullet)),
        kind("Dash", item(ListType.Dash)),
        kind("Number", item(ListType.Number)),
        kind("Check", item(ListType.Check)),
        kind("Title back to Body", ParagraphKind.Title, seed = "Persist\n# target"),
        kind("Mono back to Body", ParagraphKind.Mono, seed = "Persist\n```\ntarget\n```"),
        Case("Check, checked", SEED) { e -> atTarget(e); ui { e.toggleKind(item(ListType.Check)); e.toggleCheckAt(e.text.toString().lastIndexOf("target")) } },
        Case("checked -> unchecked", "Persist\n- [x] target") { e -> ui { e.toggleCheckAt(e.text.toString().lastIndexOf("target")) } },
        *(1..4).map { level ->
            Case("Bullet level $level", SEED) { e -> atTarget(e); ui { e.toggleKind(item(ListType.Bullet)); repeat(level) { e.indent() } } }
        }.toTypedArray(),
        Case("Number level 2 under level 0", "Persist\n1. one\ntarget") { e -> atTarget(e); ui { e.toggleKind(item(ListType.Number)); e.indent(); e.indent() } },
        Case("Check level 3", SEED) { e -> atTarget(e); ui { e.toggleKind(item(ListType.Check)); repeat(3) { e.indent() } } },
        Case("outdent level 2 -> 1", "Persist\n        - target") { e -> atTarget(e); ui { e.outdent() } },
        Case("Bold", SEED) { e -> selectWord(e, "target"); ui { e.toggleMark(Mark.Bold) } },
        Case("Italic", SEED) { e -> selectWord(e, "target"); ui { e.toggleMark(Mark.Italic) } },
        Case("Underline", SEED) { e -> selectWord(e, "target"); ui { e.toggleMark(Mark.Underline) } },
        Case("Strike", SEED) { e -> selectWord(e, "target"); ui { e.toggleMark(Mark.Strike) } },
        Case("Bold + Italic + Underline + Strike", SEED) { e ->
            selectWord(e, "target"); ui { e.toggleMark(Mark.Bold); e.toggleMark(Mark.Italic); e.toggleMark(Mark.Underline); e.toggleMark(Mark.Strike) }
        },
        Case("Bold inside a word", SEED) { e -> selectWord(e, "rge"); ui { e.toggleMark(Mark.Bold) } },
        Case("Bold typing attribute", SEED) { e -> atTarget(e); ui { e.toggleMark(Mark.Bold) }; type(e, " bold") },
        Case("Bold removed", "Persist\n**target**") { e -> selectWord(e, "target"); ui { e.toggleMark(Mark.Bold) } },
        Case("marks in a heading and a list item", "Persist\n# head\n- target") { e ->
            selectWord(e, "head"); ui { e.toggleMark(Mark.Italic) }; selectWord(e, "target"); ui { e.toggleMark(Mark.Bold) }
        },
        Case("Divider", SEED) { e -> atTarget(e); ui { e.insertHorizontalRule() } },
        Case("typed line", SEED) { e -> atTarget(e); ime(e) { commitText("\n", 1) }; type(e, "typed line") },
        Case("typed list via auto-replace", SEED) { e -> atTarget(e); ime(e) { commitText("\n", 1) }; type(e, "- dash item") },
        Case("typed checklist via auto-replace", SEED) { e -> atTarget(e); ime(e) { commitText("\n", 1) }; type(e, "[] task") },
        Case("Enter at the end of a checklist", "Persist\n- [ ] target") { e -> atTarget(e); ime(e) { commitText("\n", 1) } },
        Case("Backspace joins paragraphs", SEED) { e ->
            ui { e.setSelection(e.text.toString().lastIndexOf("target")) }
            ime(e) { sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)); sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)) }
        },
        Case("a word replaced by composing (autocorrect)", SEED) { e ->
            atTarget(e); ime(e) { val end = e.text!!.length; setComposingRegion(end - 6, end); setComposingText("targets", 1); finishComposingText() }
        },
        Case("legacy note, then one character", "Persist\n* star\n1. 1. one\n#### four\n<b>b</b>\ntarget") { e -> atTarget(e); type(e, "!") },
        Case("every kind at once", "Persist\n# T\n## H\n### S\n> q\n```\nm\n```\n- b\n+ d\n1. n\n- [ ] c\n- [x] x\n---\ntarget") { e ->
            atTarget(e); type(e, "?")
        },
    )

    @Test
    fun everyConstructionSurvivesEditCloseAndReopen() {
        app.launch()
        val broken = ArrayList<String>()
        cases.forEachIndexed { i, c ->
            val id = "qa-persist-$i"
            app.seed(id, c.seed, System.currentTimeMillis()) // above the notes edited before it: its card is on screen
            val e = app.open(id)
            c.edit(app, e)
            app.settle(400)
            val shown = app.md(e)
            val kinds = app.kinds(e)
            val text = app.text(e)
            app.back()
            val saved = app.entity(id)?.content
            val again = app.open(id)
            val reopened = Triple(app.md(again), app.kinds(again), app.text(again))
            app.back()
            val problems = buildList {
                if (shown == c.seed) add("the edit changed nothing: ${q(shown)}")
                if (saved != shown) add("saved ${q(saved)} != shown ${q(shown)}")
                if (reopened.first != shown) add("reopened ${q(reopened.first)} != shown ${q(shown)}")
                if (reopened.second != kinds) add("kinds ${reopened.second} != $kinds")
                if (reopened.third != text) add("text ${q(reopened.third)} != ${q(text)}")
            }
            if (problems.isNotEmpty()) broken += "${c.name}: ${problems.joinToString("; ")}"
        }
        assertEquals("constructions that did not survive (${broken.size} of ${cases.size})", emptyList<String>(), broken)
    }

    @Test
    fun anEditUndoneBeforeLeavingSavesNothing() {
        app.launch()
        val ts = BASE
        app.seed("qa-persist-undo", SEED, ts)
        val e = app.open("qa-persist-undo")
        app.ui { e.setSelection(e.text!!.length); e.toggleKind(ParagraphKind.Title) }
        app.type(e, "x")
        app.ui { e.undo(); e.undo() }
        assertEquals(SEED, app.md(e))
        app.back()
        val after = app.entity("qa-persist-undo")!!
        assertEquals("edited and undone: the same content", SEED, after.content)
        assertEquals("edited and undone: nothing was written", ts, after.updatedAt)
    }

    private fun q(s: String?) = s?.let { "\"" + it.replace("\n", "\\n") + "\"" } ?: "null"

    private companion object {
        const val SEED = "Persist\ntarget"
        const val BASE = 1_700_000_000_000L
    }
}
