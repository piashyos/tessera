package app.tessera.ui.editor.screen

import android.util.Log
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.repository.FolderRepository
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** The popups over the notes list: their backdrop is the whole screen (header, list, search, the glass bar), blurred. Shots `glass-popup-*`. */
@HiltAndroidTest
class GlassPopupShotsTest : NativeAppTest() {
    @Inject lateinit var folders: FolderRepository

    private val ctx get() = app.instrumentation.targetContext
    private fun s(id: Int) = ctx.getString(id)

    private fun shot(name: String) {
        app.settle(900)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, "glass-popup-$name", b)
        Log.i(TAG, "shot $name")
    }

    @Test
    fun popupsOverTheList() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val m = mode.name.lowercase()
            runBlocking { prefs.setDynamicColor(false); prefs.setThemeMode(mode) }
            db.clearAllTables()
            val now = System.currentTimeMillis()
            (1..8).forEach { app.seed("gp-$it", "# Заметка $it\nТекст заметки номер $it, несколько слов для карточки.", now - it * 1000L) }
            runBlocking { folders.createFolder("Работа", null, null) }
            fun fresh() { app.close(); app.launch(); app.awaitCard("gp-1"); app.settle(800) }

            // new folder: from the drawer
            fresh()
            runCatching {
                compose.onAllNodesWithContentDescription(s(R.string.folders_open_drawer))[1].performClick() // the bar's button (the top bar has one too)
                app.settle(900)
                shot("drawer-$m")
                compose.onAllNodesWithContentDescription(s(R.string.folders_create))[0].performClick()
                app.settle(900)
                shot("new-folder-$m")
            }.onFailure { Log.i(TAG, "new folder: $it") }

            // move a note: long press on a card, "Move to folder"
            fresh()
            runCatching {
                compose.onNodeWithTag(UiTestTags.noteCard("gp-2")).performTouchInput { longClick() }
                app.settle(900)
                compose.onNodeWithText(s(R.string.note_action_move)).performClick()
                app.settle(900)
                shot("move-note-$m")
            }.onFailure { Log.i(TAG, "move: $it") }

            // search -> In folder
            fresh()
            runCatching {
                compose.onNodeWithText(s(R.string.search_hint)).performClick()
                app.settle(900)
                shot("search-$m")
                compose.onNodeWithText(s(R.string.search_filter_folder)).performClick()
                app.settle(900)
                shot("search-in-folder-$m")
            }.onFailure { Log.i(TAG, "search: $it") }
            app.close()
        }
    }

    private companion object { const val TAG = "GlassPopupShots" }
}
