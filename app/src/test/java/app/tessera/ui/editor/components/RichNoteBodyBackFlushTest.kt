package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.NoteId
import app.tessera.ui.editor.FakeEditorRepository
import app.tessera.ui.editor.NoteEditorViewModel
import app.tessera.ui.editor.sampleNote
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.wordpress.aztec.AztecText

/**
 * Aztec holds an edit back for 300 ms; Back must hand it over BEFORE `flushAndLeave`, or the save sees the text 300 ms late:
 * a note emptied, then retyped within the window, would be deleted as blank (Т6-2). Wired as the screen does it:
 * `handOverEdit()` = `actions.flushPendingEdit()`, then `flushAndLeave()`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class RichNoteBodyBackFlushTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val repo = FakeEditorRepository(notes = listOf(sampleNote("n", content = "old content")))
    private lateinit var vm: NoteEditorViewModel
    private var actions: RichBodyActions? = null

    private fun idle(ms: Long) {
        rule.runOnUiThread { ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS) }
        rule.waitForIdle()
    }

    private fun open(): AztecText {
        rule.runOnUiThread { vm = repo.createViewModel("n", currentUser = "me") } // Aztec: the flag is off
        idle(0)
        val converter = MarkdownHtmlConverter()
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = converter.markdownToHtml("old content"),
                    markdownKey = "old content",
                    htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = { vm.onContentChange(it) },
                    onActionsReady = { actions = it },
                )
            }
        }
        rule.waitForIdle()
        return rule.activity.findViewById(R.id.aztec)
    }

    private fun type(editor: AztecText, text: String) = rule.runOnUiThread {
        editor.editableText.replace(0, editor.editableText.length, text)
    }

    private fun back() {
        rule.runOnUiThread {
            actions?.flushPendingEdit()
            vm.flushAndLeave()
        }
        idle(50)
    }

    @Test
    fun emptiedThenRetypedInsideTheDebounceWindowKeepsTheNewText() {
        val editor = open()
        type(editor, "")
        idle(350) // the empty text reached the VM
        assertEquals("", vm.state.value.content)

        type(editor, "new content")
        idle(100) // Back before the 300 ms are over
        back()

        assertEquals(listOf("new content"), repo.updateCalls.map { it.content })
        assertEquals(emptyList<NoteId>(), repo.permanentDeleteEverywhereCalls)
    }

    @Test
    fun backWithoutEditsWritesAndDeletesNothing() {
        open()
        back()

        assertEquals(emptyList<String>(), repo.updateCalls.map { it.content })
        assertEquals(emptyList<NoteId>(), repo.permanentDeleteEverywhereCalls)
        assertEquals(emptyList<NoteId>(), repo.deleteCalls)
    }
}
