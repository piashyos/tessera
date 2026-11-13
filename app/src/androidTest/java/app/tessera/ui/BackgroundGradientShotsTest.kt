package app.tessera.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.notes.AppearanceAppTest
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Screens of the live background for the user (there is no mockup; spec §3, §6) — not assertions:
 * - the list and the editor in light and dark × WARM, SLATE, SAGE (gradient on, grain at its default);
 * - AMOLED dark (no gradient), the gradient off, grain ×1 and ×4, a flat reference (no gradient, no grain);
 * - Settings → Appearance in English and Russian, grain on ("Intensity" and "Grain size" shown) and off.
 *
 * Every shot waits past the last touch (a tap "breathes" the blobs): the background is at rest. Shots go to [ShotDir] (`adb pull`).
 */
@HiltAndroidTest
class BackgroundGradientShotsTest : AppearanceAppTest() {

    @After
    fun backToDefaults() = runBlocking {
        prefs.setGradientEnabled(true)
        prefs.setAmoled(false)
        prefs.setGrainSize(1)
        prefs.setLanguageTag("")
    }

    private fun look(
        mode: ThemeMode,
        palette: ThemePalette = ThemePalette.Default,
        gradient: Boolean = true,
        grain: Boolean = true,
        size: Int = 1,
        amoled: Boolean = false,
    ) {
        theme(mode, palette, grain, gradient)
        runBlocking {
            prefs.setAmoled(amoled)
            prefs.setGrainSize(size)
        }
        d.shell("mkdir -p $ShotDir")
    }

    private fun seed() {
        note("n-3", "Groceries", body = "Milk, bread, apples")
        note("n-2", "Weekend", body = "Hike if the weather holds")
        note("n-1", "Reading", body = "Two chapters a day")
    }

    private fun shot(name: String) = d.shell("screencap -p $ShotDir/$name.png")

    private fun list(name: String) {
        d.launch()
        d.settle(1_500)
        shot(name)
    }

    @Test
    fun listAndEditorInThePalettes() {
        seed()
        for (palette in listOf(ThemePalette.WARM, ThemePalette.SLATE, ThemePalette.SAGE)) {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                val tag = "${palette.name.lowercase()}-${mode.name.lowercase()}"
                look(mode, palette)
                list("list-$tag")
                d.tap(d.card("n-1") ?: error("no card"))
                d.await("the editor", 60_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() }
                Thread.sleep(2_500)
                shot("editor-$tag")
                d.close()
            }
        }
    }

    /**
     * A tap on a note card (the press pulse, spec §4.1–§4.2): frames ~0 / 150 / 400 / 800 ms after the press and the editor at rest 3 s
     * later; logcat gives how far the lower empty part of each frame is from that rest frame. Frames go to Download/native-editor-qa.
     */
    @Test
    fun aTapOnACardPulsesBehindTheEditor() {
        seed()
        look(ThemeMode.LIGHT)
        list("tap-card-list-rest")
        Thread.sleep(2_500)
        val card = d.card("n-2") ?: error("no card")
        val (x, y) = card.bounds.centerX() to card.bounds.centerY()
        val frames = ArrayList<Pair<Long, Bitmap>>()
        val t0 = SystemClock.uptimeMillis()
        d.inject(t0, MotionEvent.ACTION_DOWN, x, y)
        frames += (SystemClock.uptimeMillis() - t0) to d.screenshot()
        sleepUntil(t0 + 60)
        d.inject(t0, MotionEvent.ACTION_UP, x, y)
        for (ms in listOf(150L, 400L, 800L)) {
            sleepUntil(t0 + ms)
            frames += (SystemClock.uptimeMillis() - t0) to d.screenshot()
        }
        d.await("the editor", 60_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() }
        Thread.sleep(3_000)
        val rest = d.screenshot()
        val band = Rect(d.dp(16f), d.screenHeight() * 55 / 100, d.screenWidth() - d.dp(16f), d.screenHeight() * 85 / 100)
        for ((at, b) in frames) {
            d.log("tap on a card, $at ms after the press: band $band vs the editor at rest: ${diff(rest, b, band)}")
            savePng(ctx, "ab-tap-card-${at}ms", b)
        }
        savePng(ctx, "ab-tap-card-editor-rest", rest)
    }

    /** A tap on the empty list: the pulse frame by frame against the frame at rest (logcat), light and dark; frames as above. */
    @Test
    fun aTapOnTheEmptyListPulses() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val t = mode.name.lowercase()
            look(mode)
            list("tap-empty-$t-rest")
            Thread.sleep(2_500)
            val band = Rect(d.dp(16f), d.dp(100f), d.screenWidth() - d.dp(16f), d.screenHeight() * 48 / 100)
            val rest = d.screenshot()
            val (x, y) = d.screenWidth() / 2 to band.centerY()
            val frames = ArrayList<Pair<Long, Bitmap>>()
            val t0 = SystemClock.uptimeMillis()
            d.inject(t0, MotionEvent.ACTION_DOWN, x, y)
            sleepUntil(t0 + 60)
            d.inject(t0, MotionEvent.ACTION_UP, x, y)
            for (ms in listOf(100L, 200L, 350L, 500L, 800L, 1_200L, 1_800L, 2_500L)) {
                sleepUntil(t0 + ms)
                frames += (SystemClock.uptimeMillis() - t0) to d.screenshot()
            }
            for ((at, b) in frames) {
                d.log("tap on the empty list ($t), $at ms after the press: band $band vs rest: ${diff(rest, b, band)}")
                savePng(ctx, "ab-tap-empty-$t-${at}ms", b)
            }
            savePng(ctx, "ab-tap-empty-$t-rest", rest)
            d.close()
        }
    }

    @Test
    fun amoledGradientOffAndGrainSizes() {
        seed()
        look(ThemeMode.DARK, amoled = true)
        list("list-amoled-dark")
        d.close()
        look(ThemeMode.LIGHT, gradient = false)
        list("list-light-gradient-off")
        d.close()
        look(ThemeMode.DARK, gradient = false)
        list("list-dark-gradient-off")
        d.close()
        look(ThemeMode.LIGHT, gradient = false, grain = false)
        list("list-light-flat")
        d.close()
        look(ThemeMode.LIGHT, size = 1)
        list("list-light-grain-x1")
        d.close()
        look(ThemeMode.LIGHT, size = 4)
        list("list-light-grain-x4")
        d.close()
        look(ThemeMode.DARK, size = 4)
        list("list-dark-grain-x4")
    }

    @Test
    fun settingsInEnglishAndRussian() {
        for (lang in listOf("en", "ru")) {
            look(ThemeMode.LIGHT)
            runBlocking { prefs.setLanguageTag(if (lang == "en") "" else lang) }
            val res = ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) })
            fun str(id: Int) = res.getString(id)
            d.launch()
            d.tap(d.await("the drawer button") { d.descs(str(R.string.folders_open_drawer)).firstOrNull { d.onScreen(it) } })
            d.tap(d.await("Settings in the drawer") { d.texts(str(R.string.settings_title)).firstOrNull { d.onScreen(it) } })
            d.await("the settings", 15_000) { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
            d.settle(800)
            shot("settings-$lang-top")
            d.reveal(str(R.string.settings_gradient_title))
            d.reveal(str(R.string.settings_grain_size))
            d.settle(600)
            shot("settings-$lang-appearance-grain-on")
            d.tap(d.reveal(str(R.string.settings_grain_title)))
            d.settle(800)
            shot("settings-$lang-appearance-grain-off")
            d.tap(d.reveal(str(R.string.settings_grain_title)))
            d.settle(600)
            d.close()
        }
    }
}
