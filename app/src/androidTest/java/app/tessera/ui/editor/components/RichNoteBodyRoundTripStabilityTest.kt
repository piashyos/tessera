package app.tessera.ui.editor.components

import android.view.View
import android.view.ViewGroup
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
 * Feature invariant, phrased as the user experiences it:
 *
 *   "If I open a note and don't touch it, saving it changes nothing. The heading stays a heading,
 *    the checklist stays a checklist — the document is stable."
 *
 * The editor stores Markdown but edits an Aztec spanned document, so every autosave is really
 * `markdown -> fromHtml -> (edit) -> toHtml -> markdown`. If that loop is NOT a fixed point for a
 * given structure, each debounced flush/reload flips the note between two shapes — which is exactly
 * the "разметка сходит с ума" flicker (a checklist line re-reading as a heading, and back).
 *
 * These cases mirror the note from the bug report: a heading directly followed by a checklist. We
 * load each fixture into the REAL Aztec editor on-device and assert that serializing it straight
 * back yields byte-identical Markdown. Any difference is the instability.
 */
class RichNoteBodyRoundTripStabilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val converter = MarkdownHtmlConverter()

    @Test
    fun h1ThenChecklist_isStable() = assertStable("# Что нужна добавить\n- [ ] Эффект бумаги на фон")

    @Test
    fun h2ThenChecklist_isStable() = assertStable("## Что нужна добавить\n- [ ] Эффект бумаги на фон")

    @Test
    fun h1ThenTwoChecklistItems_isStable() =
        assertStable("# Заголовок\n- [ ] Эффект бумаги на фон\n- [ ] Исправить разметку")

    @Test
    fun h1BlankChecklist_isStable() =
        assertStable("# Заголовок\n\n- [ ] Эффект бумаги на фон")

    @Test
    fun checklistThenTrailingEmptyItem_isStable() =
        assertStable("- [ ] Эффект бумаги на фон\n- [ ] ")

    /**
     * Load [md] into the on-screen Aztec editor, serialize it straight back to Markdown with no
     * edits, and assert the result is identical. The message prints both sides so a failure shows
     * exactly how the structure drifted.
     */
    private fun assertStable(md: String) {
        val html = converter.markdownToHtml(md)
        composeRule.setContent {
            MaterialTheme {
                RichNoteBody(
                    html = html,
                    markdownKey = md,
                    htmlToMarkdown = { converter.htmlToMarkdown(it) },
                    onMarkdownChange = {},
                    onActionsReady = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitForIdle()

        val editor = findAztec(composeRule.activity.window.decorView)
            ?: error("AztecText not found in the composed view tree")

        val roundTripped = arrayOf("")
        composeRule.runOnUiThread {
            roundTripped[0] = converter.htmlToMarkdown(editor.toHtml())
        }

        assertEquals(
            "round-trip changed the note (load then save with no edits must be a no-op)",
            md,
            roundTripped[0],
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
