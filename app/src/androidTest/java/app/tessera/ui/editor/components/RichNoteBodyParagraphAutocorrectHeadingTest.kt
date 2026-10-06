package app.tessera.ui.editor.components

import android.text.Spannable
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.spans.AztecHeadingSpan

/**
 * Feature invariant, from the user's point of view (the "разметка сходит с ума" bug in SVID_2026…):
 *
 *   "My note is a plain paragraph followed by a checklist — there is NO heading anywhere. When I
 *    tap into the paragraph and the keyboard autocorrects a word I'm on (an active IME composing
 *    region), the paragraph and the checklist below it must NOT suddenly become a giant H1. No
 *    heading span may appear, and nothing may serialize as `# …`."
 *
 * This is a DIFFERENT trigger from [RichNoteBodyBackspaceChecklistTest] (Backspace at a list-item
 * head under an existing heading, already fixed). Here the note has no heading at all; the video
 * shows the paragraph + the first checklist items collapsing into one oversized serif H1 the instant
 * the keyboard commits an autocorrect while a composing region is live, and staying that way for
 * seconds until the debounced re-serialize normalizes it.
 *
 * We drive the editor's real InputConnection the way a soft keyboard does when it corrects a word
 * that is already on screen: grab the existing word as a composing region ([setComposingRegion]),
 * then replace it with the correction ([setComposingText]) — leaving the region ACTIVE (underlined),
 * exactly the state on screen when the markup blows up. We assert on BOTH the live editor spans (the
 * render-level defect: a heading span covering non-heading content) and the serialized Markdown.
 *
 * [committedAutocorrectInParagraphKeepsStructure] is the control: the identical correction COMMITTED
 * (no composing region left) must stay clean. If the committed case passes and the composing case
 * fails, the active composing region is the isolated trigger.
 */
class RichNoteBodyParagraphAutocorrectHeadingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Volatile private var latestMarkdown: String? = null

    /** Plain paragraph (NO heading) followed by a checklist — the note from the bug video. */
    private val initialMd =
        "Что нужна добавить в приложение или исправить\n" +
            "- [ ] Эффект бумаги на фон\n" +
            "- [ ] Исправить сумашествие разметки"

    /**
     * The REAL note from the video: a serif H1 title line ("# Tessera"), then the description
     * paragraph, then the checklist. The leading heading is the ingredient the plain-paragraph
     * cases below omit — the "разметка сходит с ума" family is a heading span swallowing the
     * blocks that follow it, so a heading must be present for it to act.
     */
    private val notedMd =
        "# Tessera\n" +
            "Что нужна добавить в приложение или исправить\n" +
            "- [ ] Эффект бумаги на фон\n" +
            "- [ ] Исправить сумашествие разметки"

    /** The word the user was editing in the paragraph when the markup blew up. */
    private val word = "приложение"

    @Test
    fun composingAutocorrectInParagraphDoesNotTurnBodyIntoHeading() {
        val editor = mount(initialMd)

        composeRule.runOnUiThread {
            editor.requestFocus()
            val text = editor.text.toString()
            val start = text.indexOf(word)
            val end = start + word.length
            editor.setSelection(end)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.setComposingRegion(start, end)     // keyboard grabs the on-screen word as composing
            ic.setComposingText(word, 1)          // autocorrect suggestion — region STAYS active
            ic.endBatchEdit()
        }

        // Inspect the live editor BEFORE the debounce flush — the render defect is a heading span,
        // not necessarily a serialized `#` yet.
        assertNoHeadingSpan(editor, "immediately after composing autocorrect")

        val md = awaitFlush()
        assertBodyStructurePreserved(md)
        assertNoHeadingSpan(editor, "after the debounced serialize/reload")
    }

    @Test
    fun committedAutocorrectInParagraphKeepsStructure() {
        val editor = mount(initialMd)

        composeRule.runOnUiThread {
            editor.requestFocus()
            val text = editor.text.toString()
            val start = text.indexOf(word)
            val end = start + word.length
            editor.setSelection(end)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.setComposingRegion(start, end)
            ic.commitText(word, 1)                // correction COMMITTED — no composing region left
            ic.endBatchEdit()
        }

        assertNoHeadingSpan(editor, "immediately after committed autocorrect")
        val md = awaitFlush()
        assertBodyStructurePreserved(md)
        assertNoHeadingSpan(editor, "after the debounced serialize/reload")
    }

    @Test
    fun composingAutocorrectUnderHeadingDoesNotSpreadTheHeading() {
        val editor = mount(notedMd)

        composeRule.runOnUiThread {
            editor.requestFocus()
            val text = editor.text.toString()
            val start = text.indexOf(word)
            val end = start + word.length
            editor.setSelection(end)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.setComposingRegion(start, end)
            ic.setComposingText(word, 1)
            ic.endBatchEdit()
        }

        assertOnlyTitleIsHeading(editor, "immediately after composing autocorrect under heading")
        val md = awaitFlush()
        assertOnlyTitleHeadingInMarkdown(md)
        assertTrue(
            "the checklist must survive intact under the heading, but got:\n$md",
            md.contains("- [ ] Эффект бумаги на фон") &&
                md.contains("- [ ] Исправить сумашествие разметки"),
        )
        assertOnlyTitleIsHeading(editor, "after the debounced serialize/reload")
    }

    @Test
    fun backspaceAtHeadOfParagraphUnderHeadingDoesNotSpreadTheHeading() {
        // Backspace at the very start of the description paragraph (directly under "# Tessera").
        // The list-item-head fix does NOT cover a paragraph head, so this is the exposed path.
        val editor = mount(notedMd)

        composeRule.runOnUiThread {
            editor.requestFocus()
            val caret = editor.text.indexOf("Что нужна").coerceAtLeast(0)
            editor.setSelection(caret)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }

        val md = awaitFlush()
        // Merging the paragraph up into "Tessera" is acceptable; what must NEVER happen is the
        // checklist below being pulled into a heading.
        assertTrue(
            "the checklist must remain a checklist, never absorbed into a heading:\n$md",
            md.contains("- [ ] Эффект бумаги на фон") &&
                md.contains("- [ ] Исправить сумашествие разметки"),
        )
        assertChecklistItemsAreNotHeadings(editor, "after Backspace at the paragraph head")
    }

    @Test
    fun selectionSpanningHeadingAndListThenCommitDoesNotHeadingTheList() {
        // Mirrors the multi-line selection on screen when it blew up: a range covering the heading,
        // the paragraph, and the first list items, then a keyboard commit lands on it.
        val editor = mount(notedMd)

        composeRule.runOnUiThread {
            editor.requestFocus()
            val text = editor.text.toString()
            val selStart = text.indexOf(word)
            val selEnd = text.indexOf("Исправить сумашествие").let { it + "Исправить сумашествие".length }
            editor.setSelection(selStart, selEnd)
            val ic = editor.onCreateInputConnection(EditorInfo()) ?: error("no input connection")
            ic.beginBatchEdit()
            ic.setComposingRegion(selStart, selEnd)
            ic.commitText(word, 1)
            ic.endBatchEdit()
        }

        val md = awaitFlush()
        // The commit legitimately replaces the selected range, so text may shrink — the invariant
        // that isolates the bug is that NO new heading is minted below the title.
        assertOnlyTitleHeadingInMarkdown(md)
    }

    // --- assertions ----------------------------------------------------------------------------

    /** The serialized note may contain exactly the one legit heading ("# Tessera") and no other. */
    private fun assertOnlyTitleHeadingInMarkdown(md: String) {
        val headingLines = Regex("(?m)^#{1,6}\\s.*$").findAll(md).map { it.value }.toList()
        assertTrue(
            "only the '# Tessera' title may be a heading; found ${headingLines.size}: " +
                "$headingLines\nfull markdown:\n$md",
            headingLines.size == 1 && headingLines[0].startsWith("# Tessera"),
        )
    }

    /** Exactly one heading span may exist — the "Tessera" title — and it must cover only that word. */
    private fun assertOnlyTitleIsHeading(editor: AztecText, whenLabel: String) {
        composeRule.runOnUiThread {
            val spannable = editor.text as Spannable
            val headings = spannable.getSpans(0, spannable.length, AztecHeadingSpan::class.java)
            val ranges = headings.joinToString {
                "[${spannable.getSpanStart(it)},${spannable.getSpanEnd(it)}]=${it.heading}"
            }
            assertTrue(
                "the heading must stay confined to the title line $whenLabel, but spans were $ranges" +
                    "\neditor text: \"${spannable}\"",
                headings.all { spannable.getSpanEnd(it) <= "Tessera".length + 1 },
            )
        }
    }

    /** No heading span may cover any checklist item. */
    private fun assertChecklistItemsAreNotHeadings(editor: AztecText, whenLabel: String) {
        composeRule.runOnUiThread {
            val spannable = editor.text as Spannable
            val itemStart = spannable.toString().indexOf("Эффект бумаги на фон").coerceAtLeast(0)
            val headings = spannable.getSpans(itemStart, spannable.length, AztecHeadingSpan::class.java)
            assertTrue(
                "no heading span may cover the checklist $whenLabel, but found " +
                    headings.joinToString {
                        "[${spannable.getSpanStart(it)},${spannable.getSpanEnd(it)}]=${it.heading}"
                    } +
                    "\neditor text: \"${spannable}\"",
                headings.isEmpty(),
            )
        }
    }


    /** No AztecHeadingSpan may exist anywhere — the note has no heading and editing must not add one. */
    private fun assertNoHeadingSpan(editor: AztecText, whenLabel: String) {
        composeRule.runOnUiThread {
            val spannable = editor.text as Spannable
            val headings = spannable.getSpans(0, spannable.length, AztecHeadingSpan::class.java)
            assertTrue(
                "no heading span may cover the paragraph/checklist $whenLabel, but found " +
                    "${headings.size}: " +
                    headings.joinToString {
                        "[${spannable.getSpanStart(it)},${spannable.getSpanEnd(it)}]=${it.heading}"
                    } +
                    "\neditor text: \"${spannable}\"",
                headings.isEmpty(),
            )
        }
    }

    private fun assertBodyStructurePreserved(md: String) {
        assertFalse(
            "body must never be reclassified as a heading, but got:\n$md",
            Regex("(?m)^#\\s").containsMatchIn(md),
        )
        assertTrue(
            "the checklist must survive intact, but got:\n$md",
            md.contains("- [ ] Эффект бумаги на фон") &&
                md.contains("- [ ] Исправить сумашествие разметки"),
        )
    }

    // --- harness -------------------------------------------------------------------------------

    private fun awaitFlush(): String {
        Thread.sleep(600) // past the 300ms serialize debounce
        composeRule.waitForIdle()
        return latestMarkdown ?: error("editor never flushed any markdown")
    }

    private fun mount(md: String): AztecText {
        val html = converter.markdownToHtml(md)
        composeRule.setContent {
            MaterialTheme {
                RichNoteBody(
                    html = html,
                    markdownKey = md,
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
