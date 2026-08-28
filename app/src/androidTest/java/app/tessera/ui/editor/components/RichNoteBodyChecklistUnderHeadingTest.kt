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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.wordpress.aztec.AztecText

/**
 * Feature invariant, from the user's point of view:
 *
 *   "Under a heading I make a checklist and type an item. The heading stays a heading; the item I
 *    type is a checklist item. It does not turn into a second heading."
 *
 * This is the exact gesture from the bug video: a note that opens with an H1, then the user starts
 * a checklist beneath it and types into the first item. We reproduce it on the real Aztec editor by
 * pressing Enter and toggling the task list the same way the checklist toolbar button does
 * (RichBodyActions.toggleTaskList), then committing text — and assert on the Markdown the editor
 * actually persists.
 */
class RichNoteBodyChecklistUnderHeadingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Volatile private var latestMarkdown: String? = null
    @Volatile private var actions: RichBodyActions? = null

    private val heading = "# Что нужна добавить в приложение или исправить"

    @Test
    fun typingAChecklistItemUnderAHeadingStaysAChecklistItem() {
        val editor = mount(heading)

        composeRule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(editor.text.length) // caret at end of the heading
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.commitText("\n", 1) // Enter -> new line under the heading
            ic.endBatchEdit()
        }
        composeRule.waitForIdle()

        // Turn the new line into a checklist item, exactly like tapping the checklist toolbar button.
        composeRule.runOnUiThread { actions!!.toggleTaskList() }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.commitText("Эффект бумаги на фон", 1)
            ic.endBatchEdit()
        }

        Thread.sleep(600) // let the 300ms persist-debounce flush
        composeRule.waitForIdle()

        val md = latestMarkdown ?: error("editor never flushed any markdown")
        assertFalse(
            "the checklist line must not be serialized as a heading, but the editor stored:\n$md",
            Regex("(?m)^#+\\s.*Эффект").containsMatchIn(md),
        )
        assertEquals(
            "note must be [heading] + [one checklist item], but stored:\n$md",
            "$heading\n- [ ] Эффект бумаги на фон",
            md,
        )
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
                    onActionsReady = { actions = it },
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
