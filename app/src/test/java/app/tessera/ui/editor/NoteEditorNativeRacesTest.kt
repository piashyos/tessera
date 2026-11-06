package app.tessera.ui.editor

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.NoteId
import app.tessera.test.fakes.FakeUserPreferences
import app.tessera.ui.editor.engine.TesseraEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.wordpress.aztec.AztecText
import java.time.Duration

/**
 * The races of the whole screen with the native engine on: `NoteEditorScreen` + the real `NoteEditorViewModel` over fake
 * repositories, time driven through the main looper (the VM debounce 500 ms, the engine's 300 ms).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteEditorNativeRacesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val prefs = FakeUserPreferences().apply { useNativeEditor.value = true }
    private lateinit var repo: FakeEditorRepository
    private lateinit var vm: NoteEditorViewModel
    private val store = ViewModelStore()
    private var shown by mutableStateOf(true)

    private fun open(content: String) {
        repo = FakeEditorRepository(notes = listOf(sampleNote("n", content = content)))
        val created = repo.createViewModel("n", prefs = prefs)
        vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        })[NoteEditorViewModel::class.java]
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                if (shown) {
                    val state by vm.state.collectAsState()
                    NoteEditorScreen(
                        state = state,
                        onContentChange = vm::onContentChange,
                        onFlush = vm::flush,
                        onAddTag = {},
                        onRemoveTag = {},
                        onTogglePin = {},
                        onBack = {},
                        onDelete = {},
                    )
                }
            }
        }
        pass(50)
    }

    private fun pass(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        rule.waitForIdle()
    }

    private fun <V : View> find(type: Class<V>, v: View = rule.activity.window.decorView): V? =
        if (type.isInstance(v)) type.cast(v)
        else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(type, g.getChildAt(it)) } }

    private fun editor() = find(TesseraEditText::class.java) ?: error("no TesseraEditText on the screen")

    /** A user edit as the IME makes it: text typed into the live Editable. */
    private fun type(at: Int, s: String) = rule.runOnUiThread { editor().text!!.insert(at, s) }

    private fun stored() = repo.updateCalls.lastOrNull()?.content

    // ------------------------------------------------------------------ the flag (blocker 1)

    @Test fun no_body_is_drawn_until_the_engine_is_known() {
        var changes = 0
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteEditorScreen(
                    state = NoteEditorState(), // loading, flag unknown
                    onContentChange = { changes++ },
                    onAddTag = {}, onRemoveTag = {}, onTogglePin = {}, onBack = {}, onDelete = {},
                )
            }
        }
        rule.waitForIdle()
        assertNull(find(TesseraEditText::class.java))
        assertNull(find(AztecText::class.java))
        assertEquals(0, changes)
    }

    @Test fun flag_on_the_aztec_body_is_never_there_and_a_legacy_note_is_not_rewritten() {
        open("* legacy\n1. 1.")
        assertNotNull(find(TesseraEditText::class.java))
        assertNull(find(AztecText::class.java))
        pass(3_000)
        assertTrue("opened and untouched: nothing is saved", repo.updateCalls.isEmpty())
        assertEquals("* legacy\n1. 1.", vm.state.value.content)
        assertEquals(1L, vm.state.value.updatedAt)
    }

    @Test fun flag_switched_while_the_note_is_open_changes_neither_the_editor_nor_the_content() {
        open("* legacy")
        prefs.useNativeEditor.value = false
        pass(3_000)
        assertNotNull(find(TesseraEditText::class.java))
        assertNull(find(AztecText::class.java))
        assertTrue(repo.updateCalls.isEmpty())
        assertEquals("* legacy", vm.state.value.content)
    }

    // ------------------------------------------------------------------ remote updates (item 8)

    @Test fun a_remote_update_without_a_pending_edit_is_shown_and_the_caret_stays_inside() {
        open("abc def ghi")
        rule.runOnUiThread { editor().setSelection(11) }
        repo.pushRemote(sampleNote("n", content = "ab").copy(updatedAt = 2L))
        pass(100)
        val e = editor()
        assertEquals("ab", e.markdown())
        assertEquals("ab", vm.state.value.content)
        assertTrue("caret ${e.selectionStart} inside ${e.text!!.length}", e.selectionStart in 0..e.text!!.length)
        pass(3_000)
        assertTrue("an update from outside is not an edit of ours", repo.updateCalls.isEmpty())
    }

    @Test fun a_remote_update_inside_the_debounce_does_not_lose_the_pending_edit() {
        open("abc")
        type(3, "!") // pending in the engine (300 ms)
        pass(100)
        repo.pushRemote(sampleNote("n", content = "remote text").copy(updatedAt = 2L)) // arrives inside the window
        pass(100)
        assertEquals("the pending edit stays on screen", "abc!", editor().markdown())
        pass(3_000)
        assertEquals("abc!", editor().markdown())
        assertEquals("abc!", vm.state.value.content)
        assertEquals("the emit went out and was saved", "abc!", stored())
    }

    // ------------------------------------------------------------------ typing and undo during autosave (item 9)

    @Test fun fast_typing_and_undo_inside_the_autosave_windows_end_with_the_screen_text_saved() {
        open("abc")
        type(3, "1")
        pass(200)
        type(4, "2")
        pass(450) // the engine emitted "abc12", the VM autosave (500 ms) is running
        pass(100)
        rule.runOnUiThread { editor().undo() }
        pass(150)
        type(editor().text!!.length, "Z")
        pass(3_000)
        val onScreen = editor().markdown()
        assertEquals(onScreen, vm.state.value.content)
        assertEquals(onScreen, stored())
    }

    @Test fun undo_back_to_the_saved_text_still_ends_with_the_screen_text_saved() {
        open("abc")
        type(3, "1")
        pass(3_000) // saved: "abc1"
        assertEquals("abc1", stored())
        rule.runOnUiThread { editor().undo() }
        pass(3_000)
        assertEquals("abc", editor().markdown())
        assertEquals("abc", vm.state.value.content)
        assertEquals("abc", stored())
    }

    // ------------------------------------------------------------------ leaving the screen (item 10)

    @Test fun leaving_mid_debounce_saves_the_edit_while_the_view_model_lives() {
        open("abc")
        type(3, "!")
        pass(50)
        shown = false // back: the host leaves the composition inside the engine's 300 ms
        rule.waitForIdle()
        pass(1_000)
        assertEquals("abc!", vm.state.value.content)
        assertEquals("abc!", stored())
    }

    @Test fun leaving_mid_debounce_saves_the_edit_even_when_the_view_model_goes_with_the_screen() {
        open("abc")
        type(3, "!")
        pass(50)
        shown = false
        rule.waitForIdle()
        store.clear() // the back stack entry is gone before the 500 ms autosave
        pass(1_000)
        assertFalse("saved", stored() == null)
        assertEquals("abc!", stored())
    }

    // ------------------------------------------------------------------ the app goes to the background (ON_STOP)

    private fun stop() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()
    }

    @Test fun stop_inside_both_debounces_saves_exactly_what_is_on_screen() {
        open("abc")
        type(3, "!")
        pass(50) // far inside the engine's 300 ms and the VM's 500 ms
        assertTrue("nothing is out yet", repo.updateCalls.isEmpty())
        stop()
        pass(10) // no debounce has run out
        assertEquals(editor().markdown(), stored())
        assertEquals("abc!", stored())
        assertEquals(1, repo.updateCalls.size)
    }

    @Test fun stop_inside_the_vm_window_saves_what_the_engine_already_handed_out() {
        open("abc")
        type(3, "!")
        pass(350) // emitted, the VM autosave is waiting
        stop()
        pass(10)
        assertEquals("abc!", stored())
        pass(3_000)
        assertEquals("the autosave that was cancelled does not write again", 1, repo.updateCalls.size)
    }

    @Test fun stop_without_an_edit_writes_nothing() {
        open("* legacy")
        pass(100)
        stop()
        pass(3_000)
        assertTrue(repo.updateCalls.isEmpty())
        assertEquals(1L, vm.state.value.updatedAt)
        assertEquals("* legacy", vm.state.value.content)
    }

    @Test fun a_note_deleted_right_after_a_key_is_not_brought_back_by_the_late_save_or_the_flush() {
        open("abc")
        type(3, "!")
        pass(50)
        vm.delete() // the trash button
        shown = false // the screen leaves: the editor hands over its last key
        rule.waitForIdle()
        store.clear() // the entry is gone: onCleared must not save either
        pass(3_000)
        assertEquals(listOf(NoteId("n")), repo.deleteCalls)
        assertTrue("no write after the delete", repo.updateCalls.isEmpty())
    }

    @Test fun stop_after_a_delete_does_not_save_the_note_again() {
        open("abc")
        type(3, "!")
        pass(50)
        vm.delete()
        stop()
        pass(3_000)
        assertTrue(repo.updateCalls.isEmpty())
    }
}
