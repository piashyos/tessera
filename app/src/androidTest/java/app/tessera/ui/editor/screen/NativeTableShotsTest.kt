package app.tessera.ui.editor.screen

import android.os.ParcelFileDescriptor
import android.util.Log
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.text.markdownblocks.Block
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.markdownblocks.joinBlocksIntoMarkdown
import app.tessera.core.text.markdownblocks.serializeGfmTable
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * The same notes with tables in the native editor and in Aztec, light and dark, at the font scale 1.0 and 1.3, for the comparison of phase 2
 * (plan P2-3). Shots go to Downloads/native-editor-qa (`p2t-<note>-<engine>-<theme>-fs<scale>.png`); `stitch.sh` puts them side by side.
 * The theme and the font scale are set before a note is opened (an open Aztec note does not take a new theme).
 */
@HiltAndroidTest
class NativeTableShotsTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private val ctx get() = app.instrumentation.targetContext

    private fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(app.instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }

    @After
    fun restore() {
        shell("settings put system font_scale 1.0")
        runBlocking { prefs.setUseNativeEditor(true); prefs.setThemeMode(ThemeMode.AUTO) }
    }

    private fun shot(name: String) {
        app.settle(1_000)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, name, b)
        Log.i(TAG, "shot $name ${b.width}x${b.height}")
    }

    @Test
    fun tablesInTheNativeEditorAndInAztec() {
        val budget = TableGrid(
            listOf("Статья", "Сумма", "Заметка"),
            listOf(listOf("Аренда", "1200", "ежемесячно"), listOf("Еда", "300", "примерно"), listOf("Транспорт", "80", "")),
        )
        val wide = TableGrid(
            listOf("Name", "Qty", "Price", "Shop", "Aisle", "Note"),
            listOf(listOf("apple", "3", "1.20", "market", "4", "red"), listOf("watermelon", "1", "3.10", "corner", "1", "big and heavy")),
            colWidths = listOf(120f, 60f, 80f, 120f, 70f, 160f),
        )
        val notes = listOf(
            "a" to "# Бюджет\nРасходы за месяц:\n" + serializeTable(budget) + "\nИтого считаем вручную.",
            "b" to joinBlocksIntoMarkdown(listOf(Block.Text("Широкая таблица со своими ширинами:"), Block.Table(wide), Block.Text("Текст после таблицы."))),
        )
        for (scale in listOf("1.0", "1.3")) {
            app.close()
            shell("settings put system font_scale $scale")
            Thread.sleep(1_500)
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                runBlocking { prefs.setDynamicColor(false); prefs.setThemePalette(ThemePalette.WARM); prefs.setThemeMode(mode) }
                app.launch()
                app.settle(1_500)
                notes.forEachIndexed { i, (key, md) -> if (app.entity("qa-tshot-$key") == null) app.seed("qa-tshot-$key", md, 1_700_000_000_000L + i * 1_000L) }
                for (engine in listOf("native", "aztec")) {
                    runBlocking { prefs.setUseNativeEditor(engine == "native") }
                    for ((key, _) in notes) {
                        app.tapCard("qa-tshot-$key")
                        compose.waitUntil(15_000) { app.editorOrNull() != null || app.aztecOrNull() != null }
                        app.settle(2_000)
                        shot("p2t-$key-$engine-${mode.name.lowercase()}-fs$scale")
                        app.back()
                    }
                }
                app.close()
            }
        }
    }

    private fun serializeTable(g: TableGrid) = serializeGfmTable(g).trimEnd('\n')

    private companion object {
        const val TAG = "NativeTableShots"
    }
}
