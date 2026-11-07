package app.tessera.ui.editor.screen

import android.view.inputmethod.EditorInfo
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke of the old path with the flag off (spec §9 item 6): a note opens in Aztec, not in the native editor, shows its text, an
 * edit is saved, and a note opened and closed without an edit keeps its `content` and `updatedAt` as before.
 */
@HiltAndroidTest
class AztecPathSmokeTest : NativeAppTest() {

    @Test
    fun withTheFlagOffNotesOpenInAztecAndEditsAreSaved() {
        runBlocking { prefs.setUseNativeEditor(false) }
        app.launch()
        app.seed("qa-aztec-1", "# Aztec\n- [ ] task\nbody", BASE)
        app.tapCard("qa-aztec-1")
        compose.waitUntil(15_000) { app.aztecOrNull() != null }
        app.settle()
        assertNull("not the native editor", app.editorOrNull())
        val shown = app.ui { app.aztecOrNull()!!.text.toString() }
        assertTrue("Aztec shows the note: \"$shown\"", shown.contains("Aztec") && shown.contains("task") && shown.contains("body"))
        app.settle(2_000)
        val whileOpen = app.entity("qa-aztec-1")!!
        assertEquals("open in Aztec, 3 s, no edit: content (a rewrite on load)", "# Aztec\n- [ ] task\nbody", whileOpen.content)
        app.back()
        val untouched = app.entity("qa-aztec-1")!!
        assertEquals("opened and closed in Aztec: content", "# Aztec\n- [ ] task\nbody", untouched.content)
        assertEquals("opened and closed in Aztec: updatedAt", BASE, untouched.updatedAt)

        app.tapCard("qa-aztec-1")
        compose.waitUntil(15_000) { app.aztecOrNull() != null }
        app.settle()
        app.ui {
            val a = app.aztecOrNull()!!
            a.setSelection(a.text.toString().indexOf("body") + 4)
            a.onCreateInputConnection(EditorInfo())!!.commitText("!", 1)
        }
        app.settle()
        app.back()
        assertEquals("an Aztec edit is saved", "# Aztec\n- [ ] task\nbody!", app.entity("qa-aztec-1")!!.content)
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
