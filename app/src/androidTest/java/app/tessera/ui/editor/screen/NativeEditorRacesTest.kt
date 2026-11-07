package app.tessera.ui.editor.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.performClick
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.editor.engine.ClipboardBridge
import app.tessera.ui.editor.engine.DocBinder
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Races of the real app around the native editor (plan, Chunk 6 "Гонки и варианты на устройстве"): a rotation in the middle of
 * a composed word and of the 300 ms emit, the flag switched under an open note, a sync landing with and without an edit in flight,
 * fast typing from the input system mixed with undo, a paste while a word is composed. After each: nothing typed is lost or
 * doubled, the editor and the database agree, the document is canonical.
 */
@HiltAndroidTest
class NativeEditorRacesTest : NativeAppTest() {

    private fun AppDriver.rotate(orientation: Int) {
        ui { activity().requestedOrientation = orientation }
        compose.waitUntil(15_000) { runCatching { ui { activity().resources.configuration.orientation } }.getOrNull() == expectedConfig(orientation) }
        awaitEditor()
    }

    private fun expectedConfig(o: Int) =
        if (o == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT

    /** The document the editor shows is what a fresh load of its Markdown shows, and its Markdown is a fixed point. */
    private fun assertCanonical(e: TesseraEditText, label: String) {
        val md = app.md(e)
        assertEquals("$label: fixed point", md, NoteMarkdown.serialize(NoteMarkdown.parse(md)))
        assertEquals("$label: text as a fresh load", app.ui { DocBinder.build(NoteMarkdown.parse(md), e.editorStyle).toString() }, app.text(e))
    }

    // ------------------------------------------------------------------ rotation

    @Test
    fun rotationInTheMiddleOfAComposedWordKeepsTheWordTheCaretAndTheSave() {
        app.launch()
        app.seed("qa-rot-1", "Rotate\nword", BASE)
        try {
            val e = app.open("qa-rot-1")
            app.ui { e.setSelection(e.text!!.length) }
            app.ime(e) { setComposingText("abc", 1) } // a word in composition, not emitted yet
            app.rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val e2 = app.awaitEditor()
            assertEquals("the composed word is in the text after the rotation", "Rotate\nwordabc", app.md(e2))
            val (focused, ime) = app.ui {
                e2.isFocused to (ViewCompat.getRootWindowInsets(e2)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
            }
            assertEquals("the caret is where it was (after the rotation the editor is focused: $focused, the keyboard is up: $ime)", e2.text!!.length, app.ui { e2.selectionStart })
            app.settle()
            assertEquals("and it is saved", "Rotate\nwordabc", app.entity("qa-rot-1")!!.content)
        } finally {
            app.rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
        app.back()
        assertEquals("Rotate\nwordabc", app.entity("qa-rot-1")!!.content)
    }

    @Test
    fun rotationInTheMiddleOfTheEmitKeepsTheEditOnce() {
        app.launch()
        app.seed("qa-rot-2", "Rotate\n- item", BASE)
        try {
            val e = app.open("qa-rot-2")
            app.ui { e.setSelection(e.text!!.length) }
            app.ime(e) { commitText("X", 1) }
            app.ime(e) { commitText("\n", 1) } // a new item, its emit 300 ms away
            app.ime(e) { commitText("Y", 1) }
            app.rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val e2 = app.awaitEditor()
            assertEquals("Rotate\n- itemX\n- Y", app.md(e2))
            assertCanonical(e2, "after the rotation")
            app.ime(e2) { commitText("Z", 1) } // typing goes on in the new editor
            app.rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            val e3 = app.awaitEditor()
            assertEquals("Rotate\n- itemX\n- YZ", app.md(e3))
        } finally {
            app.rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        }
        app.back()
        assertEquals("Rotate\n- itemX\n- YZ", app.entity("qa-rot-2")!!.content)
    }

    // ------------------------------------------------------------------ the flag under an open note

    @Test
    fun switchingTheFlagUnderAnOpenNoteChangesNeitherTheEditorNorTheEdit() {
        app.launch()
        app.seed("qa-flag", "Flag\n- [ ] task", BASE)
        val e = app.open("qa-flag")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("!", 1) }
        runBlocking { prefs.setUseNativeEditor(false) } // Settings, while the note is open (another window, a second activity)
        app.settle()
        assertNotNull("still the native editor", app.editorOrNull())
        assertNull("no Aztec appeared", app.aztecOrNull())
        app.ime(app.editorOrNull()!!) { commitText("?", 1) }
        app.back()
        assertEquals("both edits saved", "Flag\n- [ ] task!?", app.entity("qa-flag")!!.content)

        // the next note opened takes the new value: Aztec, with the text as saved
        app.tapCard("qa-flag")
        compose.waitUntil(15_000) { app.aztecOrNull() != null }
        val aztecText = app.ui { app.aztecOrNull()!!.text.toString() }
        assertTrue("Aztec shows the saved edit: \"$aztecText\"", aztecText.contains("task!?"))
        runBlocking { prefs.setUseNativeEditor(true) } // and back while Aztec is open: Aztec stays
        app.settle()
        assertNull("Aztec stays until the note is left", app.editorOrNull())
        app.back()
    }

    // ------------------------------------------------------------------ a sync landing while the note is open

    @Test
    fun aRemoteUpdateWithoutAnEditInFlightIsShown() {
        app.launch()
        app.seed("qa-sync-1", "Sync\nold", BASE)
        val e = app.open("qa-sync-1")
        app.seed("qa-sync-1", "Sync\nnew from a collaborator\n- [x] done", BASE + 60_000)
        compose.waitUntil(10_000) { app.md(e) == "Sync\nnew from a collaborator\n- [x] done" }
        assertCanonical(e, "after the remote update")
        app.ui { e.undo() }
        assertEquals("undo does not go back through a collaborator's change (§6.7)", "Sync\nnew from a collaborator\n- [x] done", app.md(e))
        app.back()
        val saved = app.entity("qa-sync-1")!!
        assertEquals("nothing written back", "Sync\nnew from a collaborator\n- [x] done", saved.content)
        assertEquals("nothing written back: updatedAt", BASE + 60_000, saved.updatedAt)
    }

    @Test
    fun aRemoteUpdateWhileAnEditIsWaitingForAutosaveKeepsTheEdit() {
        app.launch()
        app.seed("qa-sync-2", "Sync\nmine", BASE)
        val e = app.open("qa-sync-2")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("L", 1) }
        Thread.sleep(400) // emitted to the screen, the 500 ms autosave not run yet
        app.seed("qa-sync-2", "Sync\ntheirs", BASE + 60_000)
        app.settle()
        assertEquals("the typed character stays", "Sync\nmineL", app.md(e))
        app.back()
        assertEquals("Sync\nmineL", app.entity("qa-sync-2")!!.content)
    }

    @Test
    fun aRemoteUpdateInsideTheEmitDebounceDoesNotEatTheKeystroke() {
        app.launch()
        app.seed("qa-sync-3", "Sync\nmine", BASE)
        val e = app.open("qa-sync-3")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("K", 1) } // not emitted yet (300 ms): the screen still holds the old note
        app.seed("qa-sync-3", "Sync\ntheirs", BASE + 60_000)
        app.settle()
        val shown = app.md(e)
        assertTrue("the keystroke is not lost: \"$shown\"", shown.contains("K"))
        assertCanonical(e, "after the race")
        app.back()
        assertEquals("the editor and the database agree", shown, app.entity("qa-sync-3")!!.content)
    }

    @Test
    fun aRemoteUpdateWhileAWordIsComposedKeepsTheWord() {
        app.launch()
        app.seed("qa-sync-4", "Sync\nmine ", BASE)
        val e = app.open("qa-sync-4")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { setComposingText("wor", 1) }
        Thread.sleep(400) // emitted: the screen has the edit, the word is still in composition
        app.seed("qa-sync-4", "Sync\ntheirs", BASE + 60_000)
        app.settle()
        assertEquals("the composed word stays", "Sync\nmine wor", app.md(e))
        app.ime(e) { setComposingText("word", 1); finishComposingText() }
        assertEquals("and the keyboard goes on with it", "Sync\nmine word", app.md(e))
        app.back()
        assertEquals("Sync\nmine word", app.entity("qa-sync-4")!!.content)
    }

    @Test
    fun aRemoteUpdateAfterASavedEditIsShownAndTypingGoesOnAtACaretInsideTheText() {
        app.launch()
        app.seed("qa-sync-5", "Sync\nmine", BASE)
        val e = app.open("qa-sync-5")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("!", 1) }
        app.settle() // emitted and saved
        val savedAt = app.entity("qa-sync-5")!!.updatedAt
        app.seed("qa-sync-5", "Sync", savedAt + 60_000) // a collaborator cut the note shorter than the caret
        compose.waitUntil(10_000) { app.md(e) == "Sync" }
        val caret = app.ui { e.selectionStart to e.selectionEnd }
        assertTrue("the caret is inside the new text: $caret", caret.first in 0..4 && caret.second in 0..4)
        app.ime(e) { commitText("?", 1) }
        assertCanonical(e, "typing after the rebuild")
        val md = app.md(e)
        app.back()
        assertEquals(md, app.entity("qa-sync-5")!!.content)
    }

    // ------------------------------------------------------------------ leaving the note right after a key

    @Test
    fun backRightAfterAKeySavesTheKey() {
        app.launch()
        app.seed("qa-back-1", "Back\none", BASE)
        val e = app.open("qa-back-1")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("X", 1) } // the 300 ms emit has not run: Back must hand the edit over
        app.back()
        assertEquals("the Back arrow", "Back\noneX", app.entity("qa-back-1")!!.content)

        val e2 = app.open("qa-back-1")
        app.ui { e2.setSelection(e2.text!!.length) }
        app.ime(e2) { commitText("Y", 1) }
        app.systemBack()
        assertEquals("the system Back", "Back\noneXY", app.entity("qa-back-1")!!.content)
    }

    @Test
    fun deletingTheNoteRightAfterAKeyDoesNotBringItBack() {
        app.launch()
        app.seed("qa-del-1", "Delete\nme", BASE)
        val e = app.open("qa-del-1")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { commitText("Z", 1) } // pending in the editor when the trash is tapped
        compose.onNodeWithTag(UiTestTags.EditorDelete).performClick()
        compose.waitUntil(15_000) { app.editorOrNull() == null }
        app.settle(2_000) // the editor's last emit and the screen's autosave would land here
        val after = app.entity("qa-del-1")
        assertTrue("the note stays deleted (deletedAt ${after?.deletedAt}), not saved back to the list", after == null || after.deletedAt != null)
    }

    // ------------------------------------------------------------------ fast input from the input system, with undo

    @Test
    fun fastTypingFromTheInputSystemMixedWithUndoLeavesAConsistentNote() {
        app.launch()
        app.seed("qa-fast", "Fast\n", BASE)
        val e = app.open("qa-fast")
        app.ui { e.requestFocus(); e.setSelection(e.text!!.length) }
        app.settle(500)
        val typing = Thread {
            val pfd = app.instrumentation.uiAutomation.executeShellCommand("input text the_quick_brown_fox_jumps_over_the_lazy_dog_0123456789")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }.apply { start() }
        val started = SystemClock.uptimeMillis()
        while (typing.isAlive && SystemClock.uptimeMillis() - started < 20_000) {
            Thread.sleep(150)
            app.ui { e.undo() }
        }
        typing.join()
        app.settle()
        assertCanonical(e, "after the typing and the undos")
        val shown = app.md(e)
        app.ui { repeat(50) { e.redo() } }
        assertCanonical(e, "after redo")
        val redone = app.md(e)
        app.back()
        assertEquals("the editor and the database agree (before redo: \"$shown\")", redone, app.entity("qa-fast")!!.content)
    }

    // ------------------------------------------------------------------ a paste while a word is composed

    @Test
    fun aPasteWhileAWordIsComposedInsertsAtTheCaretAndLosesNothing() {
        app.launch()
        app.seed("qa-paste", "Paste\nstart ", BASE)
        val e = app.open("qa-paste")
        app.ui { e.setSelection(e.text!!.length) }
        app.ime(e) { setComposingText("wor", 1) }
        ClipboardBridge.forget()
        app.ui { app.activity().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(null, "XYZ")) }
        app.ui { e.onTextContextMenuItem(android.R.id.paste) }
        assertEquals("Paste\nstart worXYZ", app.md(e))
        app.ime(e) { finishComposingText(); commitText("!", 1) } // the keyboard sees the caret moved: it ends the word, typing goes on
        assertEquals("Paste\nstart worXYZ!", app.md(e))
        assertCanonical(e, "after the paste")
        app.back()
        assertEquals("Paste\nstart worXYZ!", app.entity("qa-paste")!!.content)
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
