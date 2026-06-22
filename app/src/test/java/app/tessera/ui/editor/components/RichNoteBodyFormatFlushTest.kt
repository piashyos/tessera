package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecText

/**
 * Regression test for the "format changes are not saved" bug: applying a character format
 * (bold/italic/…) toggles AztecText spans without changing the text, so the TextWatcher never
 * fires. The editor must still propagate the new markdown via [onMarkdownChange] so the autosave
 * path can persist it — otherwise the formatting is silently lost on exit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class RichNoteBodyFormatFlushTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun togglingBoldOnSelectionPropagatesMarkdown() {
        val converter = MarkdownHtmlConverter()
        var actions: RichBodyActions? = null
        val recorded = mutableListOf<String>()

        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = converter.markdownToHtml("hello world"),
                    markdownKey = "hello world",
                    htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = { recorded += it },
                    onActionsReady = { actions = it },
                )
            }
        }
        rule.waitForIdle()

        val editor = rule.activity.findViewById<AztecText>(R.id.aztec)
        rule.runOnUiThread {
            editor.requestFocus()
            editor.setSelection(0, editor.length())
            actions!!.toggleBold()
        }
        rule.waitForIdle()

        assertTrue(
            "toggleBold must propagate bolded markdown via onMarkdownChange; recorded=$recorded",
            recorded.any { it.contains("**") },
        )
    }
}
