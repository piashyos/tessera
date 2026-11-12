package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.Tag
import app.tessera.domain.repository.FolderRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.ui.editor.engine.DocBinder
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Screenshots of the real app with the native editor for the comparison with the mockup (plan Chunk 6): frames 01–04 with the
 * notes of the frames (same text, tags, folder), the constructions the mockup does not have, and the note of frame 01 in every
 * palette × light / dark. Shots go to Downloads/native-editor-qa (`6b-<name>-fs<font scale>.png`). The vertical geometry of the
 * frame-01 note is logged in dp (`adb logcat -s NativeEditorShots`) for the table of `native-editor-design-diff.md`.
 * Run at the mockup parity (`wm size 1125x2436`, `wm density 480`).
 */
@HiltAndroidTest
class NativeEditorShotsTest : NativeAppTest() {
    @Inject lateinit var notes: NotesRepository
    @Inject lateinit var folders: FolderRepository

    private val fontScale get() = app.instrumentation.targetContext.resources.configuration.fontScale

    private fun shot(name: String) {
        app.settle(800)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(app.instrumentation.targetContext, "6b-$name-fs$fontScale", b)
        Log.i(TAG, "shot $name ${b.width}x${b.height}")
    }

    private fun note(content: String, tags: List<String>, pinned: Boolean, path: List<String>): String = runBlocking {
        val saved = notes.updateNote(notes.createNote().copy(content = content, tags = tags.map(::Tag), isPinned = pinned))
        // Folder names are unique among live folders now: reuse the one from an earlier call.
        var folder: Folder? = null
        for (name in path) {
            val parent = folder
            folder = folders.observeFolders().first().firstOrNull { it.name == name && it.parentId == parent?.id }
                ?: ((if (parent == null) folders.createFolder(name, null, null)
                else folders.createSubfolder(parent.id, name, null, null)) as FolderSaveResult.Saved).folder
        }
        folders.assignNote(saved.id, folder!!.id)
        saved.id.value
    }

    private fun theme(mode: ThemeMode, palette: ThemePalette = ThemePalette.WARM) = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemePalette(palette)
        prefs.setThemeMode(mode)
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
        runCatching { Gboard(app).awaitWindow() }.onFailure {
            shot("focus-failed")
            Log.i(TAG, "keyboard not shown; tapped at $x,$y; focused=${app.ui { e.isFocused }}; windows=${app.instrumentation.uiAutomation.windows.map { w -> w.type to w.title }}")
            throw it
        }
        app.settle(1_200)
    }

    /** Where each paragraph of the note is, in dp from the top of the text: the block spacing of the table. */
    private fun logGeometry(label: String, e: TesseraEditText) = app.ui {
        val d = e.resources.displayMetrics.density
        val l = e.layout
        val t = e.text!!
        var ps = 0
        for (b in DocBinder.read(t).blocks) {
            val first = l.getLineForOffset(ps)
            val end = t.indexOf('\n', ps).let { if (it < 0) t.length else it }
            val last = l.getLineForOffset(end)
            val kind = (b as? NoteBlock.Text)?.kind ?: b
            Log.i(TAG, "$label | $kind | top %.1f dp | height %.1f dp | lines ${last - first + 1} | first line %.1f dp | baseline %.1f dp".format(
                l.getLineTop(first) / d, (l.getLineBottom(last) - l.getLineTop(first)) / d,
                (l.getLineBottom(first) - l.getLineTop(first)) / d, l.getLineBaseline(first) / d,
            ))
            ps = end + 1
        }
        val loc = IntArray(2).also { e.getLocationOnScreen(it) }
        Log.i(TAG, "$label | editor at x %.1f dp, y %.1f dp, padding top %.1f dp, text size %.1f px".format(loc[0] / d, loc[1] / d, e.totalPaddingTop / d, e.textSize))
    }

    @Test
    fun mockupFramesAndConstructionsWithoutAMockup() {
        runBlocking { prefs.setLanguageTag("ru") }
        theme(ThemeMode.LIGHT)
        // the first start applies the language and recreates the activity (the app keeps the locale): start again on a settled one
        app.launch()
        app.settle(3_000)
        app.close()
        app.launch()
        app.settle(1_500)

        // frames 01 / 02: the note of editor-content-light / -dark
        val q3 = note(Q3, listOf("срочно", "встреча"), pinned = true, path = listOf("Работа", "Проекты"))
        app.open(q3)
        app.settle(1_500) // past the shared-element transition from the card
        logGeometry("frame 01", app.editorOrNull()!!)
        shot("01-content-light")
        theme(ThemeMode.DARK)
        shot("02-content-dark")
        theme(ThemeMode.LIGHT)
        app.back()

        // frame 04: editor-format-bar — a note being written, the keyboard and the bar
        val ideas = note(IDEAS, listOf("срочно"), pinned = false, path = listOf("Работа", "Проекты"))
        Log.i(TAG, "step: frame 04")
        app.open(ideas)
        app.settle(1_500)
        focusEnd(app.editorOrNull()!!)
        shot("04-format-bar")
        Log.i(TAG, "step: frame 04 back")
        app.back()

        // frame 03 (editor-empty, a new note from the + button) is shot over adb, as a user does it: shots-frame03.sh
        // (here the test clock of Compose leaves the new destination un-composed after the + button)

        // without a mockup: Subheading, dash, levels 0–4, numbers, checks, placeholders
        val rest = note(NO_MOCKUP, emptyList(), pinned = false, path = listOf("Работа"))
        app.open(rest)
        app.settle(1_500)
        logGeometry("no mockup", app.editorOrNull()!!)
        shot("05-no-mockup-light")
        theme(ThemeMode.DARK)
        shot("05-no-mockup-dark")
        theme(ThemeMode.LIGHT)
        app.back()

        // the frame-01 note in every palette, light and dark
        app.open(q3)
        for (p in ThemePalette.entries) for (m in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(m, p)
            shot("palette-${p.name.lowercase()}-${m.name.lowercase()}")
        }
        theme(ThemeMode.LIGHT)
        app.back()
        runBlocking { prefs.setLanguageTag("") }
    }

    private companion object {
        const val TAG = "NativeEditorShots"

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

        const val NO_MOCKUP = "# Без макета\n" +
            "### Подзаголовок\n" +
            "Основной текст между блоками.\n" +
            "+ тире раз\n" +
            "+ тире два\n" +
            "- уровень 0\n" +
            "    - уровень 1\n" +
            "        - уровень 2\n" +
            "            - уровень 3\n" +
            "                - уровень 4\n" +
            "1. номер\n" +
            "    1. номер, уровень 1\n" +
            "        1. номер, уровень 2\n" +
            "- [ ] чек\n" +
            "    - [x] чек, уровень 1\n" +
            "---\n" +
            "![](tessera-img://qa-image)\n" +
            "| A | B |\n" +
            "| --- | --- |\n" +
            "| 1 | 2 |\n" +
            "Текст после заглушек."
    }
}
