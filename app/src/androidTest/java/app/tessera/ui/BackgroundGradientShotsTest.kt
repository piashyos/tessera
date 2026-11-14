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
 * Screens of the live background and of Settings → Appearance for the user (there is no mockup; spec 2026-11-13-appearance-screen-and-
 * motion §2–§4) — not assertions:
 * - the list and the editor in light and dark × WARM, SLATE, SAGE (gradient on, grain at its default);
 * - AMOLED dark (no gradient), the gradient off, grain ×1 and ×4, a flat reference (no gradient, no grain);
 * - Settings and Appearance in English and Russian, light and dark; the licenses' list and text;
 * - the motion's strength: series of frames of a tap's breath, a held drag and swipes, with the colour levels in logcat.
 *
 * Every still shot waits past the last touch (a tap "breathes" the blobs): the background is at rest. Shots go to [ShotDir] (`adb pull`),
 * series to Download/native-editor-qa.
 */
@HiltAndroidTest
class BackgroundGradientShotsTest : AppearanceAppTest() {

    @After
    fun backToDefaults() = runBlocking {
        prefs.setGradientEnabled(true)
        prefs.setBackgroundMotionEnabled(true)
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
     * A tap on a note card (the breath starts on the release, spec §3.2): frames ~0 / 150 / 400 / 800 ms after the up and the editor at
     * rest 3 s later; logcat gives how far the lower empty part of each frame is from that rest frame. Frames go to
     * Download/native-editor-qa.
     */
    @Test
    fun aTapOnACardBreathesBehindTheEditor() {
        seed()
        look(ThemeMode.LIGHT)
        list("tap-card-list-rest")
        Thread.sleep(2_500)
        val card = d.card("n-2") ?: error("no card")
        val (x, y) = card.bounds.centerX() to card.bounds.centerY()
        val frames = ArrayList<Pair<Long, Bitmap>>()
        val down = SystemClock.uptimeMillis()
        d.inject(down, MotionEvent.ACTION_DOWN, x, y)
        sleepUntil(down + 60)
        val up = d.lift(down, x, y)
        for (ms in listOf(0L, 150L, 400L, 800L)) {
            sleepUntil(up + ms)
            frames += (SystemClock.uptimeMillis() - up) to d.screenshot()
        }
        d.await("the editor", 60_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() }
        Thread.sleep(3_000)
        val rest = d.screenshot()
        val band = Rect(d.dp(16f), d.screenHeight() * 55 / 100, d.screenWidth() - d.dp(16f), d.screenHeight() * 85 / 100)
        for ((at, b) in frames) {
            d.log("tap on a card, $at ms after the up: band $band vs the editor at rest: ${diff(rest, b, band)}")
            savePng(ctx, "am-tap-card-${at}ms", b)
        }
        savePng(ctx, "am-tap-card-editor-rest", rest)
    }

    /**
     * The strength of the motion on the empty list (spec §3.4: "noticeable, never in your face"; ~4–6 colour levels at the breath's peak,
     * ~2–4 for a scroll's shift), light and dark, each frame against the frame at rest, in logcat (`-s TagFolderAppearance`, `MOTION`):
     * - a tap: frames from the up (the breath starts on the release) until rest;
     * - a held drag (the finger still down 500 ms after the slow pull), then its return;
     * - a short and a long quick swipe, each twice: released at speed (a glide) and held still 300 ms before the up (no glide) — the same
     *   travel, so a glide shows as the released one going further; frames ~100 / 300 / 600 / 1000 ms after the up, then until rest.
     * Frames are kept in memory and saved after each series (a PNG takes ~0.4 s); the frame times of the app (`dumpsys gfxinfo`) are logged
     * for each swipe: the velocity of a swipe is read from the touches the app gets per frame.
     */
    @Test
    fun theMotionsStrengthOnTheEmptyList() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val t = mode.name.lowercase()
            look(mode)
            list("am-empty-$t-rest")
            val title = d.await("the empty list") { d.texts(s(R.string.notes_empty_title)).firstOrNull { d.onScreen(it) } }
            val bar = d.descs(s(R.string.folders_open_drawer)).filter { d.onScreen(it) }.minByOrNull { it.bounds.top } ?: error("no top bar")
            val band = Rect(d.dp(16f), bar.bounds.bottom + d.dp(24f), d.screenWidth() - d.dp(16f), title.bounds.top - d.dp(24f))
            val x = d.screenWidth() / 2

            var rest = d.restFrame(band, "$t: before the tap")
            savePng(ctx, "am-$t-rest", rest)
            val down = SystemClock.uptimeMillis()
            d.inject(down, MotionEvent.ACTION_DOWN, x, band.centerY())
            sleepUntil(down + 60)
            var up = d.lift(down, x, band.centerY())
            report("$t tap", rest, band, up, listOf(100L, 200L, 300L, 400L, 600L, 900L, 1_300L, 1_800L, 2_500L))

            rest = d.restFrame(band, "$t: before the held drag")
            val d0 = SystemClock.uptimeMillis()
            d.inject(d0, MotionEvent.ACTION_DOWN, x, band.top + d.dp(8f))
            for (i in 1..20) {
                Thread.sleep(16)
                d.inject(d0, MotionEvent.ACTION_MOVE, x, band.top + d.dp(8f) + (band.height() - d.dp(16f)) * i / 20)
            }
            Thread.sleep(500)
            val held = d.screenshot()
            d.log("MOTION $t held drag (${band.height() - d.dp(16f)} px pulled): ${diff(rest, held, band)}")
            savePng(ctx, "am-$t-drag-held", held)
            up = d.lift(d0, x, band.bottom - d.dp(8f))
            d.awaitBack(rest, band, up, 3_000, "MOTION $t held drag, the return")

            for ((name, travel) in listOf("short" to d.dp(110f), "long" to band.height() - d.dp(32f))) {
                for (hold in listOf(0L, 300L)) {
                    val what = "$t $name swipe ($travel px), " + if (hold == 0L) "released at speed" else "held $hold ms before the up"
                    rest = d.restFrame(band, "$what: before")
                    d.shell("dumpsys gfxinfo app.tessera reset")
                    val y0 = band.top + d.dp(16f)
                    val t0 = SystemClock.uptimeMillis()
                    d.inject(t0, MotionEvent.ACTION_DOWN, x, y0, t0)
                    for (i in 1..6) {
                        sleepUntil(t0 + i * 16L)
                        d.inject(t0, MotionEvent.ACTION_MOVE, x, y0 + travel * i / 6, t0 + i * 16L)
                    }
                    Thread.sleep(hold)
                    up = d.lift(t0, x, y0 + travel, if (hold > 0) SystemClock.uptimeMillis() else t0 + 7 * 16L)
                    report(what, rest, band, up, listOf(0L, 100L, 300L, 600L, 1_000L, 1_500L, 2_200L), "am-$t-swipe-$name-hold$hold")
                    val gfx = d.shell("dumpsys gfxinfo app.tessera").lines()
                        .filter { it.contains("Total frames") || it.contains("percentile") }.joinToString("; ") { it.trim() }
                    d.log("MOTION $what: the app's frames: $gfx")
                    d.awaitBack(rest, band, up, 4_200, "MOTION $what, the return")
                }
            }
            d.close()
        }
    }

    /** Frames of [band] at [at] ms after [up] (as near as screenshots allow), logged against [rest] and then saved as [prefix]-<ms>. */
    private fun report(what: String, rest: Bitmap, band: Rect, up: Long, at: List<Long>, prefix: String = "am-${what.replace(' ', '-')}") {
        val frames = ArrayList<Pair<Long, Bitmap>>()
        for (ms in at) {
            sleepUntil(up + ms)
            frames += (SystemClock.uptimeMillis() - up) to d.screenshot()
        }
        for ((ms, b) in frames) d.log("MOTION $what, $ms ms after the up: ${diff(rest, b, band)}")
        frames.forEachIndexed { i, (ms, b) -> if (i in listOf(0, 2, 4)) savePng(ctx, "$prefix-${ms}ms", b) }
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

    /**
     * Settings in English and Russian, light and dark (spec §2.1–§2.2): the main screen ("General": Appearance, Language) and the
     * Appearance screen; in light also with grain off (no sliders) and with the gradient off (no "Background animation"). Every shot
     * waits until the background is at rest (each tap breathes it).
     */
    @Test
    fun settingsAndAppearanceInEnglishAndRussian() {
        for (lang in listOf("en", "ru")) {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                val tag = "$lang-${mode.name.lowercase()}"
                look(mode)
                runBlocking { prefs.setLanguageTag(if (lang == "en") "" else lang) }
                val res = ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) })
                fun str(id: Int) = res.getString(id)
                d.launch()
                openSettings(::str)
                atRest()
                shot("settings-$tag-top")
                openAppearance()
                atRest()
                shot("appearance-$tag")
                if (mode == ThemeMode.LIGHT) {
                    d.tap(d.byTag(UiTestTags.AppearanceGrainRow).first { d.onScreen(it) })
                    d.awaitTrue("grain off") { d.texts(str(R.string.settings_grain_size)).none { d.onScreen(it) } }
                    atRest()
                    shot("appearance-$tag-grain-off")
                    d.tap(d.byTag(UiTestTags.AppearanceGradientRow).first { d.onScreen(it) })
                    d.awaitTrue("gradient off") { d.byTag(UiTestTags.AppearanceMotionRow).isEmpty() }
                    atRest()
                    shot("appearance-$tag-gradient-off")
                }
                d.close()
            }
        }
    }

    /** The licenses' list (debug: the plugin's one "Debug License Info" entry) in light and dark, and its text in light. */
    @Test
    fun licensesListAndText() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val t = mode.name.lowercase()
            look(mode)
            d.launch()
            openSettings()
            d.tap(d.reveal(s(R.string.settings_licenses), bottomDp = 48f))
            d.await("the licenses", 15_000) { d.byTag(UiTestTags.LicensesRoot).firstOrNull() }
            d.await("an entry") { d.texts("Debug License Info").firstOrNull { d.onScreen(it) } }
            atRest()
            shot("licenses-$t")
            if (mode == ThemeMode.LIGHT) {
                d.tap(d.texts("Debug License Info").first { d.onScreen(it) })
                d.await("the license text") { d.byTag(UiTestTags.LicensesText).firstOrNull() }
                atRest()
                shot("licenses-text-$t")
            }
            d.close()
        }
    }

    /** The screen under the status bar still: three frames 400 ms apart equal (each tap breathes the background for ~2 s). */
    private fun atRest() {
        d.restFrame(Rect(0, d.statusBarBottom(), d.screenWidth(), d.screenHeight()), "the screen for a shot")
    }
}
