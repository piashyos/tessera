package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecText

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class BlankLineAfterBlockTypingTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Test
    fun blankLineTypedAfterBlockIsPreserved() {
        var editor: AztecText? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = "", markdownKey = "seed", htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        editor = rule.activity.findViewById(R.id.aztec)

        // Simulate the USER action: load a block, then TYPE a blank line + more content, then flush.
        // (initialMarkdown, textToInsertAtEnd, expected)
        val cases = listOf(
            Triple("# H", "\n\ntext", "# H\n\ntext"),
            Triple("# H", "\n\n# H2", "# H\n\n# H2"),
            Triple("- item", "\n\ntext", "- item\n\ntext"),
            Triple("> quote", "\n\ntext", "> quote\n\ntext"),
        )
        for ((initial, insert, expected) in cases) {
            var flushed = ""
            rule.runOnUiThread {
                editor!!.fromHtml(converter.markdownToHtml(initial))
                editor!!.setSelection(editor!!.length())
                // Insert char-by-char to mimic keystrokes (Aztec attaches paragraph structure).
                insert.forEach { ch -> editor!!.text.insert(editor!!.selectionStart, ch.toString()) }
                flushed = converter.htmlToMarkdown(editor!!.toHtml())
            }
            assertEquals("typing a blank line after [$initial] must keep it", expected, flushed)
        }
    }

    @Test
    fun blankLineTypedBetweenTwoBlocksIsPreserved() {
        var editor: AztecText? = null
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(html = "", markdownKey = "seed", htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = {}, onActionsReady = {})
            }
        }
        rule.waitForIdle()
        editor = rule.activity.findViewById(R.id.aztec)

        // The reported bug: place the caret at the end of a block that is FOLLOWED by another
        // block and press Enter — the typed blank line must survive the flush.
        // (initialMarkdown, caret anchor text, expected)
        val cases = listOf(
            Triple("# H\n- [ ] task", "H", "# H\n\n- [ ] task"),
            Triple("# A\n# B", "A", "# A\n\n# B"),
            Triple("# A\n> quote", "A", "# A\n\n> quote"),
        )
        for ((initial, anchor, expected) in cases) {
            var flushed = ""
            rule.runOnUiThread {
                editor!!.fromHtml(converter.markdownToHtml(initial))
                val caret = editor!!.editableText.toString().let { it.indexOf(anchor) + anchor.length }
                editor!!.setSelection(caret)
                editor!!.text.insert(editor!!.selectionStart, "\n")
                flushed = converter.htmlToMarkdown(editor!!.toHtml())
            }
            assertEquals("typing a blank line inside [$initial] must keep it", expected, flushed)
        }
    }
}
