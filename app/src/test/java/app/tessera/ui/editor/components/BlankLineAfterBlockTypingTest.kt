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
}
