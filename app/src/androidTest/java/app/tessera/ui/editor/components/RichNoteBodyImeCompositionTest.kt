package app.tessera.ui.editor.components

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.core.text.MarkdownHtmlConverter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.wordpress.aztec.AztecText

/**
 * Feature-level invariant, phrased the way a user experiences it (NOT mirrored from the editor's
 * implementation):
 *
 *   "While I type into a checklist item — including while the on-screen keyboard is still showing
 *    an autocorrect suggestion for the word I'm on (an active IME composing region) — the item
 *    stays a checklist item. It never turns into a heading and its checkbox never disappears."
 *
 * A soft keyboard delivers an in-progress word as `setComposingText` (an underlined, not-yet-
 * committed region marked with SPAN_COMPOSING), and only turns it into ordinary text on
 * `commitText`. So we drive the editor's own InputConnection exactly like a keyboard does — that
 * reproduces the composition state deterministically, with no physical keyboard and no flaky
 * timing. The editor persists edits by serializing itself to Markdown on a 300ms debounce; the bug
 * is that a serialization taken WHILE a word is mid-composition inside a checklist item comes back
 * out as a heading (`# …`), which the next reload then paints as a real H1 (checkboxes vanish).
 *
 * [committedTypingKeepsChecklistItem] is the control: the identical edit COMMITTED (no composing
 * region) must round-trip cleanly. If the committed case passes and the composing case fails, the
 * active composing region is the isolated trigger.
 */
class RichNoteBodyImeCompositionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Volatile
    private var latestMarkdown: String? = null

    /** Note that opens with a heading followed by a checklist item missing its first word. */
    private val initialMd = "# Что нужна добавить\n- [ ]  бумаги на фон"

    @Test
    fun composingAWordInsideChecklistItemKeepsItAChecklistItem() {
        val editor = mountEditorAndFocusTaskItem()

        composeRule.runOnUiThread {
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.setComposingText("Эффект", 1) // leaves an ACTIVE composing region (SPAN_COMPOSING)
            ic.endBatchEdit()
        }

        val md = awaitFlush()
        assertChecklistStillAChecklist(md)
    }

    @Test
    fun committedTypingKeepsChecklistItem() {
        val editor = mountEditorAndFocusTaskItem()

        composeRule.runOnUiThread {
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.commitText("Эффект", 1) // committed text — no composing region left behind
            ic.endBatchEdit()
        }

        val md = awaitFlush()
        assertChecklistStillAChecklist(md)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun mountEditorAndFocusTaskItem(): AztecText {
        val initialHtml = converter.markdownToHtml(initialMd)
        composeRule.setContent {
            MaterialTheme {
                RichNoteBody(
                    html = initialHtml,
                    markdownKey = initialMd,
                    htmlToMarkdown = { converter.htmlToMarkdown(it) },
                    onMarkdownChange = { latestMarkdown = it },
                    onActionsReady = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()

        val editor = findAztec(composeRule.activity.window.decorView)
            ?: error("AztecText not found in the composed view tree")

        composeRule.runOnUiThread {
            editor.requestFocus()
            // Place the caret at the very start of the task item's text (before " бумаги на фон").
            val caret = editor.text.indexOf(" бумаги на фон").coerceAtLeast(0)
            editor.setSelection(caret)
        }
        return editor
    }

    /** Wait past the editor's 300ms serialization debounce and return the emitted Markdown. */
    private fun awaitFlush(): String {
        Thread.sleep(600)
        composeRule.waitForIdle()
        return latestMarkdown ?: error("editor never flushed any markdown")
    }

    private fun assertChecklistStillAChecklist(md: String) {
        assertTrue(
            "checklist item must remain a checklist item, but the editor serialized:\n$md",
            md.contains("- [ ] Эффект бумаги на фон"),
        )
        assertFalse(
            "checklist text must never be reclassified as a heading, but got:\n$md",
            Regex("(?m)^#\\s.*Эффект").containsMatchIn(md),
        )
    }

    private fun findAztec(v: View): AztecText? {
        if (v is AztecText) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findAztec(v.getChildAt(i))?.let { return it }
        }
        return null
    }
}
