package app.tessera.ui.editor.components

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.TesseraEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** `NoteBody`'s own wiring: load, external update, emit, dispose, read-only (spec §6.9, §7). The engine itself is tested apart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteBodyTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var markdown by mutableStateOf("abc")
    private var shown by mutableStateOf(true)
    private var editable by mutableStateOf(true)
    private val emitted = mutableListOf<String>()
    private val states = mutableListOf<FormatState>()
    private var actions: RichBodyActions? = null

    private fun setContent() = rule.setContent {
        TesseraTheme(darkTheme = false, dynamicColor = false) {
            if (shown) {
                NoteBody(
                    markdown = markdown,
                    onMarkdownChange = { emitted += it },
                    onActionsReady = { actions = it },
                    onFormatStateChanged = { states += it },
                    editable = editable,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun editor(): TesseraEditText {
        fun find(v: View): TesseraEditText? =
            if (v is TesseraEditText) v else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        return find(rule.activity.window.decorView) ?: error("no TesseraEditText in the window")
    }

    private fun pass(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        rule.waitForIdle()
    }

    @Test fun loads_the_markdown_and_hands_out_actions_and_the_state() {
        markdown = "# Title\n- a"
        setContent()
        rule.waitForIdle()
        assertEquals("# Title\n- a", editor().markdown())
        assertNotNull(actions)
        assertTrue("the format state is delivered when the callback is set", states.isNotEmpty())
        assertTrue("loading emits nothing", emitted.isEmpty())
    }

    @Test fun a_command_is_emitted_after_the_debounce() {
        setContent()
        rule.waitForIdle()
        rule.runOnUiThread { editor().setSelection(1); actions!!.toggleHeading1() }
        assertTrue("not yet", emitted.isEmpty())
        pass(400)
        assertEquals(listOf("# abc"), emitted)
        assertTrue("the bar learns the history", states.last().canUndo)
    }

    @Test fun an_external_update_is_applied_and_emits_nothing() {
        setContent()
        rule.waitForIdle()
        markdown = "changed by a collaborator"
        rule.waitForIdle()
        assertEquals("changed by a collaborator", editor().markdown())
        pass(400)
        assertTrue(emitted.isEmpty())
    }

    @Test fun an_update_during_a_pending_user_edit_is_skipped_and_the_echo_is_ignored() {
        setContent()
        rule.waitForIdle()
        rule.runOnUiThread { editor().setSelection(1); actions!!.toggleHeading1() }
        markdown = "old state from the store" // arrives inside the debounce window
        rule.waitForIdle()
        assertEquals("the pending edit stays", "# abc", editor().markdown())
        pass(400)
        assertEquals(listOf("# abc"), emitted)
        markdown = "# abc" // the screen echoes our own emit back
        rule.waitForIdle()
        assertEquals(listOf("# abc"), emitted)
        assertEquals("# abc", editor().markdown())
    }

    @Test fun leaving_the_composition_emits_a_pending_edit() {
        setContent()
        rule.waitForIdle()
        rule.runOnUiThread { editor().setSelection(1); actions!!.toggleHeading1() }
        shown = false
        rule.waitForIdle()
        assertEquals(listOf("# abc"), emitted)
    }

    @Test fun leaving_the_composition_without_an_edit_emits_nothing() {
        markdown = "* legacy"
        setContent()
        rule.waitForIdle()
        shown = false
        rule.waitForIdle()
        assertTrue(emitted.isEmpty())
    }

    @Test fun read_only_takes_no_input_and_focusBody_does_not_focus() {
        editable = false
        setContent()
        rule.waitForIdle()
        val e = editor()
        assertFalse(e.editable)
        rule.runOnUiThread { actions!!.toggleHeading1(); actions!!.focusBody() }
        pass(400)
        assertFalse(e.isFocused)
        assertEquals("abc", e.markdown())
        assertTrue(emitted.isEmpty())
    }

    @Test fun switching_editable_follows_the_parameter() {
        setContent()
        rule.waitForIdle()
        assertTrue(editor().editable)
        editable = false
        rule.waitForIdle()
        assertFalse(editor().editable)
        editable = true
        rule.waitForIdle()
        assertTrue(editor().editable)
    }
}
