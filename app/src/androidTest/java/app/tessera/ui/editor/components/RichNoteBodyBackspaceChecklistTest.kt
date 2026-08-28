package app.tessera.ui.editor.components

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.core.text.MarkdownHtmlConverter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.wordpress.aztec.AztecText

/**
 * Feature invariant, from the user's point of view:
 *
 *   "When my caret is at the start of a checklist item under a heading and I press Backspace, the
 *    item does NOT turn into a heading. Backspace either joins it to the line above or leaves it
 *    plain text — it never inherits the heading's H1 size."
 *
 * Matches the bug video: the caret sits at the very start of the checklist line ("|Эффект …"), the
 * checkbox is gone, and the line renders as an oversized H1 for ~2s. Backspace at the head of a
 * list item strips the list block in Aztec, and the line can adopt the format of the block above
 * it — the heading. We drive the real editor's Backspace key and assert on the stored Markdown.
 */
class RichNoteBodyBackspaceChecklistTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Volatile private var latestMarkdown: String? = null

    private val heading = "# Что нужна добавить в приложение или исправить"

    @Test
    fun backspaceAtStartOfChecklistItemDelistsInPlace() {
        // Market standard (Apple Notes / Google Docs / Notion): the item is delisted into a plain
        // paragraph on its own line — the heading is untouched and never swallows the text.
        assertBackspaceAtHeadDelists(
            item = "- [ ] Эффект бумаги на фон",
            expectedPlainLine = "Эффект бумаги на фон",
        )
    }

    @Test
    fun backspaceAtStartOfBulletItemDelistsInPlace() {
        assertBackspaceAtHeadDelists(
            item = "- Эффект бумаги на фон",
            expectedPlainLine = "Эффект бумаги на фон",
        )
    }

    @Test
    fun backspaceAtHeadOfFirstItemKeepsRemainingItems() {
        // Multi-item list: delisting the first item must NOT drop the items below it — they stay a
        // checklist. This guards the container-span shrink against data loss.
        val stored = backspaceAtHeadOf(
            "$heading\n- [ ] Эффект бумаги на фон\n- [ ] Исправить разметку",
        )
        assertEquals(
            "first item delisted in place; the rest stays a checklist",
            "$heading\nЭффект бумаги на фон\n- [ ] Исправить разметку",
            stored,
        )
    }

    private fun assertBackspaceAtHeadDelists(item: String, expectedPlainLine: String) {
        val stored = backspaceAtHeadOf("$heading\n$item")
        assertEquals(
            "Backspace at a list-item head must delist it in place, leaving the heading intact",
            "$heading\n$expectedPlainLine",
            stored,
        )
    }

    /** Mount [md], press Backspace at the start of the first "Эффект…" line, return stored markdown. */
    private fun backspaceAtHeadOf(md: String): String {
        val editor = mount(md)
        composeRule.runOnUiThread {
            editor.requestFocus()
            val caret = editor.text.indexOf("Эффект").coerceAtLeast(0)
            editor.setSelection(caret)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        Thread.sleep(600) // let the 300ms persist-debounce flush
        composeRule.waitForIdle()
        return latestMarkdown ?: error("editor never flushed any markdown")
    }

    private fun mount(initialMd: String): AztecText {
        val html = converter.markdownToHtml(initialMd)
        composeRule.setContent {
            MaterialTheme {
                RichNoteBody(
                    html = html,
                    markdownKey = initialMd,
                    htmlToMarkdown = { converter.htmlToMarkdown(it) },
                    onMarkdownChange = { latestMarkdown = it },
                    onActionsReady = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()
        return findAztec(composeRule.activity.window.decorView)
            ?: error("AztecText not found in the composed view tree")
    }

    private fun findAztec(v: View): AztecText? {
        if (v is AztecText) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) findAztec(v.getChildAt(i))?.let { return it }
        }
        return null
    }
}
