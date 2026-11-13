package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.Tag
import app.tessera.domain.repository.FolderRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Screenshots of the editor's top bar for the comparison with the mockup (spec 2026-11-12-editor-header-and-popup-gestures §2, plan
 * Chunk 3b.2): frames 01 / 02 / 04 with the notes of the frames, and the states the mockup does not have — no folder (date only),
 * a long folder path, the "⋯" menu open (light and dark). The bounds of the bar's parts are logged in dp (`adb logcat -s EditorHeaderShots`).
 * Shots go to Downloads/native-editor-qa (`hdr-<name>-fs<font scale>.png`). Run at the mockup parity (`wm size 1125x2436`,
 * `wm density 480`), with an on-screen keyboard (`show_ime_with_hard_keyboard 1`) for frame 04.
 */
@HiltAndroidTest
class EditorHeaderShotsTest : NativeAppTest() {
    @Inject lateinit var notes: NotesRepository
    @Inject lateinit var folders: FolderRepository

    private val fontScale get() = app.instrumentation.targetContext.resources.configuration.fontScale

    private fun shot(name: String) {
        app.settle(800)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(app.instrumentation.targetContext, "hdr-$name-fs$fontScale", b)
        Log.i(TAG, "shot $name ${b.width}x${b.height}")
    }

    private fun note(content: String, tags: List<String>, pinned: Boolean, path: List<String>): String = runBlocking {
        val saved = notes.updateNote(notes.createNote().copy(content = content, tags = tags.map(::Tag), isPinned = pinned))
        var folder: Folder? = null
        for (name in path) {
            val parent = folder
            folder = folders.observeFolders().first().firstOrNull { it.name == name && it.parentId == parent?.id }
                ?: ((if (parent == null) folders.createFolder(name, null, null)
                else folders.createSubfolder(parent.id, name, null, null)) as FolderSaveResult.Saved).folder
        }
        folder?.let { folders.assignNote(saved.id, it.id) }
        saved.id.value
    }

    private fun theme(mode: ThemeMode) = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemePalette(ThemePalette.WARM)
        prefs.setThemeMode(mode)
    }

    /** Bounds in dp of the bar's buttons and of the title lines ([texts]), for the table of the design diff. */
    private fun logBar(label: String, vararg texts: String) {
        val d = app.instrumentation.targetContext.resources.displayMetrics.density
        fun line(name: String, b: androidx.compose.ui.geometry.Rect) = Log.i(
            TAG, "$label | $name | left %.1f top %.1f right %.1f bottom %.1f | w %.1f h %.1f | centre x %.1f y %.1f dp".format(
                b.left / d, b.top / d, b.right / d, b.bottom / d, b.width / d, b.height / d, b.center.x / d, b.center.y / d,
            ),
        )
        for (tag in listOf(UiTestTags.EditorBack, UiTestTags.EditorPin, UiTestTags.EditorOverflow)) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { line(tag, it.boundsInRoot) }
        }
        for (t in texts) {
            compose.onAllNodes(hasText(t), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { line("\"$t\"", it.boundsInRoot) }
                ?: Log.i(TAG, "$label | \"$t\" | not found")
        }
        val w = app.ui { app.activity().window.decorView.width } / d
        Log.i(TAG, "$label | window width %.1f dp, font scale $fontScale".format(w))
    }

    /** A finger at the end of the text: the caret there, the keyboard and the format bar up. */
    private fun focusEnd(e: TesseraEditText) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            val l = e.layout
            val line = l.lineCount - 1
            loc[0] + (e.totalPaddingLeft + l.getLineRight(line) + 30f).coerceAtMost(e.width - 10f) to
                loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
        }
        val t = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
        Gboard(app).awaitWindow()
        app.settle(1_200)
    }

    private fun openMenuAndShoot(name: String) {
        compose.onNodeWithTag(UiTestTags.EditorOverflow).performClick()
        app.settle(800)
        logBar("$name menu", "Удалить заметку", "Сделать общей")
        shot(name)
        app.instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK) // closes the menu only
        app.settle(500)
    }

    @Test
    fun headerFramesAndStatesWithoutAMockup() {
        runBlocking { prefs.setLanguageTag("ru") }
        theme(ThemeMode.LIGHT)
        // the first start applies the language and recreates the activity: start again on a settled one
        app.launch()
        app.settle(3_000)
        app.close()
        app.launch()
        app.settle(1_500)

        // frames 01 / 02: editor-content-light / -dark
        val q3 = note(Q3, listOf("срочно", "встреча"), pinned = true, path = listOf("Работа", "Проекты"))
        app.open(q3)
        app.settle(1_500)
        logBar("frame 01", "Работа › Проекты", "Сегодня", "Q3 roadmap — приоритеты")
        shot("01-content-light")
        openMenuAndShoot("menu-light")
        theme(ThemeMode.DARK)
        shot("02-content-dark")
        openMenuAndShoot("menu-dark")
        theme(ThemeMode.LIGHT)
        app.back()

        // no folder: the date alone
        val loose = note(IDEAS, emptyList(), pinned = false, path = emptyList())
        app.open(loose)
        app.settle(1_500)
        logBar("no folder", "Сегодня")
        shot("no-folder-light")
        app.back()

        // a long path: the start is cut ("… › Проекты 2026"), the title keeps clear of the buttons (folder names are unique)
        val deep = note(IDEAS, listOf("срочно"), pinned = false, path = listOf(LONG_1, LONG_2, LONG_3))
        app.open(deep)
        app.settle(1_500)
        logBar("long path", "$LONG_1 › $LONG_2 › $LONG_3", "Сегодня")
        shot("long-path-light")
        theme(ThemeMode.DARK)
        shot("long-path-dark")
        theme(ThemeMode.LIGHT)
        app.back()

        // frame 03 (editor-empty, a new note from the + button) is shot over adb: an empty note has no card to open here

        // frame 04: editor-format-bar — the keyboard and the bar
        val ideas = note(IDEAS, listOf("срочно"), pinned = false, path = listOf("Работа", "Проекты"))
        app.open(ideas)
        app.settle(1_500)
        focusEnd(app.editorOrNull()!!)
        logBar("frame 04", "Работа › Проекты", "Сегодня")
        shot("04-format-bar")
        app.back()
        runBlocking { prefs.setLanguageTag("") }
    }

    private companion object {
        const val TAG = "EditorHeaderShots"
        const val LONG_1 = "Очень длинное имя папки верхнего уровня"
        const val LONG_2 = "Вложенная папка с длинным именем"
        const val LONG_3 = "Проекты 2026"

        const val Q3 = "# Q3 roadmap — приоритеты\n" +
            "Список приоритетов для третьего квартала. Обсудили на ретро 11 июня, договорились вернуться через две недели.\n" +
            "## Что точно делаем\n" +
            "- [x] Закрыть онбординг для новых пользователей\n" +
            "- [ ] Доделать invisible sync (текущая ветка)\n" +
            "- [ ] Разобраться с CRDT для офлайн-конфликтов\n" +
            "- [ ] Material You редизайн\n" +
            "## Под вопросом\n" +
            "Голосовой ввод и шифрование заметок — отдельным треком, не блокируем релиз.\n" +
            "> «Сначала закрываем долги по UX, потом расширяемся.»\n" +
            "## Связанное\n" +
            "```\n" +
            "git log --oneline feature/sync\n" +
            "baae2d4 fix(prefs): avoid loop\n" +
            "10cf6f3 feat(settings): account block\n" +
            "```"

        const val IDEAS = "# Идеи для редизайна\n" +
            "Material You + терракотовая айдентика, нижний поиск, FAB на той же линии.\n" +
            "## Ключевые решения\n" +
            "- [ ] Папки 2 уровня вложенности\n" +
            "- [ ] Карточки фиксированной высоты"
    }
}
