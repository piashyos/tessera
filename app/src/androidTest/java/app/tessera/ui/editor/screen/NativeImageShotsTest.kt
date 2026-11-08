package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Notes with pictures in the native editor, light and dark (plan P2-3). Shots go to Downloads/native-editor-qa
 * (`p2-<note>-native-<theme>.png`). The pictures are the app's files of this device.
 */
@HiltAndroidTest
class NativeImageShotsTest : NativeAppTest() {
    @Inject lateinit var imageDao: NoteImageDao

    private val ctx get() = app.instrumentation.targetContext
    private val pictures = listOf(
        Triple("shot-wide", 1600, 900), Triple("shot-narrow", 301, 401), Triple("shot-w50", 1001, 563), Triple("shot-rot", 1001, 563),
    )

    @After
    fun clean() {
        ImageFixtures.removeApp(ctx, pictures.map { it.first })
        runBlocking { prefs.setThemeMode(ThemeMode.AUTO) }
    }

    private fun shot(name: String) {
        app.settle(1_000)
        val b = app.instrumentation.uiAutomation.takeScreenshot() ?: error("no screenshot")
        savePng(ctx, name, b)
        Log.i(TAG, "shot $name ${b.width}x${b.height}")
    }

    private fun theme(mode: ThemeMode) = runBlocking {
        prefs.setDynamicColor(false)
        prefs.setThemePalette(ThemePalette.WARM)
        prefs.setThemeMode(mode)
    }

    private fun tapCenterOfFirstPicture() {
        val e = app.editorOrNull() ?: return
        val (x, y) = app.ui {
            val r = shownImages(e).first().rect
            val loc = IntArray(2).also { e.getLocationInWindow(it) }
            loc[0] + r.exactCenterX() to loc[1] + r.exactCenterY()
        }
        val t = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
    }

    @Test
    fun picturesInTheNativeEditor() {
        for ((id, w, h) in pictures) {
            val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, id), w, h)
            runBlocking { imageDao.upsert(ImageFixtures.row(id, f, w, h)) }
        }
        val notes = listOf(
            "a" to "# Поездка\nПервый день: море и скалы, широкая картинка.\n![](tessera-img://shot-wide)\nУзкая картинка не растягивается:\n![](tessera-img://shot-narrow)\nТекст под картинками.",
            "b" to "# Ресайз и поворот\nПоловина ширины:\n![](tessera-img://shot-w50?w=50)\nПовёрнута на 90°:\n![](tessera-img://shot-rot?rot=90)\nКонец.",
            "c" to "# Недоступна\nКартинки нет на устройстве:\n![](tessera-img://shot-missing)\nТекст после неё.",
        )
        theme(ThemeMode.LIGHT)
        app.launch()
        notes.forEachIndexed { i, (key, md) -> app.seed("qa-shot-$key", md, 1_700_000_000_000L + i * 1_000L) }
        // the theme is chosen before a note is opened
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            app.settle(1_000)
            val t = mode.name.lowercase()
            for ((key, _) in notes) {
                app.tapCard("qa-shot-$key")
                compose.waitUntil(15_000) { app.editorOrNull() != null }
                app.settle(2_000) // past the transition from the card and the pictures
                shot("p2-$key-native-$t")
                if (key == "a") {
                    tapCenterOfFirstPicture()
                    shot("p2-$key-native-selected-$t")
                }
                app.back()
            }
        }
    }

    private companion object {
        const val TAG = "NativeImageShots"
    }
}
