package app.tessera.ui.settings

import android.os.SystemClock
import android.view.MotionEvent
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.inject
import app.tessera.ui.lift
import app.tessera.ui.notes.AppearanceAppTest
import app.tessera.ui.notes.UiNode
import app.tessera.ui.reveal
import app.tessera.ui.sleepUntil
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings → Appearance and Settings → About → Open source licenses (spec 2026-11-13-appearance-screen-and-motion §2, §4) in the real
 * app: the way there and back, the rows and their switches and sliders, the saved values from the first frame, the licenses' list and
 * text. Driven like [AppearanceAppTest] (no Compose rule, real touches, the real clocks).
 */
@HiltAndroidTest
class AppearanceAndLicensesAppTest : AppearanceAppTest() {

    @After
    fun backToDefaults() = runBlocking {
        prefs.setGradientEnabled(true)
        prefs.setBackgroundMotionEnabled(true)
        prefs.setGrainEnabled(true)
        prefs.setGrainIntensity(0.5f)
        prefs.setGrainSize(1)
    }

    private fun appearance() = d.byTag(UiTestTags.AppearanceRoot).firstOrNull()
    private fun settings() = d.byTag(UiTestTags.SettingsRoot).firstOrNull()

    /** The texts of the Appearance screen on the screen now. */
    private fun appearanceTexts() = d.nodes().filter { UiTestTags.AppearanceRoot in it.tags && it.text.isNotEmpty() && d.onScreen(it) }.map { it.text }

    /** The Switch named [title] (its content description) on the Appearance screen: on, off, or `null` when it is not there. */
    private fun switch(title: Int, nodes: List<UiNode> = d.nodes()): Boolean? =
        nodes.firstOrNull { UiTestTags.AppearanceRoot in it.tags && it.desc == s(title) && it.toggled != null }?.toggled

    // ------------------------------------------------------------------ the way there and back

    /**
     * The main screen has "General" with "Appearance" and "Language" and no appearance rows of its own; "Appearance" opens its screen
     * with the theme, gradient, animation and grain rows; the system Back and the top bar's arrow both return to Settings.
     */
    @Test
    fun appearanceOpensFromGeneralAndBothBacksReturnToSettings() {
        theme(ThemeMode.LIGHT, grain = true, gradient = true)
        d.launch()
        openSettings()
        val main = d.visibleTexts()
        d.log("settings: $main")
        // a group's label is drawn in capitals
        assertTrue("\"General\" on the main screen: $main", d.texts(s(R.string.settings_section_general).uppercase()).any { d.onScreen(it) })
        assertTrue("\"Appearance\" row: $main", d.byTag(UiTestTags.SettingsAppearanceRow).any { d.onScreen(it) })
        assertTrue("\"Language\" row: $main", d.texts(s(R.string.settings_section_language)).any { d.onScreen(it) })
        for (gone in listOf(R.string.settings_theme_sheet_title, R.string.settings_gradient_title, R.string.settings_grain_title)) {
            assertTrue("\"${s(gone)}\" is no longer on the main screen: $main", d.texts(s(gone)).none { d.onScreen(it) })
        }

        openAppearance()
        val rows = appearanceTexts()
        d.log("appearance: $rows")
        for (title in listOf(
            R.string.settings_appearance, R.string.settings_theme_sheet_title, R.string.settings_theme_light,
            R.string.settings_gradient_title, R.string.settings_background_motion_title, R.string.settings_background_motion_subtitle,
            R.string.settings_grain_title, R.string.settings_grain_intensity,
        )) assertTrue("\"${s(title)}\" on the Appearance screen: $rows", s(title) in rows)
        assertEquals("the gradient's switch is on", true, switch(R.string.settings_gradient_title))
        assertEquals("the animation's switch is on", true, switch(R.string.settings_background_motion_title))
        assertEquals("the grain's switch is on", true, switch(R.string.settings_grain_title))

        d.back()
        d.await("Settings after the system Back") { settings() }
        d.awaitTrue("the Appearance screen gone") { appearance() == null }

        openAppearance()
        d.tap(d.byTag(UiTestTags.AppearanceBack).first { d.onScreen(it) })
        d.await("Settings after the top bar's arrow") { settings() }
        d.awaitTrue("the Appearance screen gone") { appearance() == null }
        d.back()
        d.await("the list after Back from Settings") { d.byTag(UiTestTags.NotesListRoot).firstOrNull() }
    }

    /**
     * The Appearance screen shares the Settings screen's ViewModel (spec §2.2): it shows the saved values from its first frame. With
     * everything off and a dark theme — the opposite of a fresh state's defaults (all on, system theme) — no snapshot of the screen
     * from the first one on may show a slider, the animation's row, a switch on or another theme.
     */
    @Test
    fun appearanceShowsTheSavedValuesFromItsFirstFrame() {
        theme(ThemeMode.DARK, grain = false, gradient = false, motion = false)
        d.launch()
        openSettings()
        d.settle(1_000) // Settings has its state

        // the tap injected straight (no wait for the main thread to idle, which could let the first frames pass unseen)
        val row = d.byTag(UiTestTags.SettingsAppearanceRow).first { d.onScreen(it) }.bounds
        val down = SystemClock.uptimeMillis()
        d.inject(down, MotionEvent.ACTION_DOWN, row.centerX(), row.centerY())
        sleepUntil(down + 60)
        val tapped = d.lift(down, row.centerX(), row.centerY())
        // polled back to back (no pause): the first snapshot with the screen is as near to its first frame as the main thread allows
        var first: List<UiNode>? = null
        while (first == null) {
            check(SystemClock.uptimeMillis() < tapped + 10_000) { "no Appearance screen 10 s after the tap: ${d.visibleTexts()}" }
            first = d.nodes().takeIf { n -> n.any { it.tag == UiTestTags.AppearanceRoot } }
        }
        val firstAt = SystemClock.uptimeMillis() - tapped
        val snapshots = mutableListOf(first)
        while (SystemClock.uptimeMillis() < tapped + 2_000) snapshots += d.nodes()
        d.screenshot().also { savePng(ctx, "am-appearance-after-2s", it) }
        // Settings still in the first snapshot: it was taken while the two screens cross-fade, i.e. on the new screen's first frames
        val settingsToo = first.any { it.tag == UiTestTags.SettingsRoot }
        val settingsGoneAt = snapshots.indexOfFirst { n -> n.none { it.tag == UiTestTags.SettingsRoot } }
        d.log("appearance: Settings still in the first snapshot: $settingsToo (gone from snapshot $settingsGoneAt)")
        d.log("appearance: first seen $firstAt ms after the tap; ${snapshots.size} snapshots in 2 s; the first: " +
            first.filter { UiTestTags.AppearanceRoot in it.tags && it.text.isNotEmpty() }.joinToString(" | ") { it.text })

        val forbidden = listOf(R.string.settings_background_motion_title, R.string.settings_grain_intensity, R.string.settings_grain_size)
        snapshots.forEachIndexed { i, nodes ->
            val mine = nodes.filter { UiTestTags.AppearanceRoot in it.tags }
            for (f in forbidden) assertTrue("snapshot $i: no \"${s(f)}\" (it is off)", mine.none { it.text == s(f) })
            assertTrue("snapshot $i: no other theme than Dark", mine.none { it.text == s(R.string.settings_theme_system) || it.text == s(R.string.settings_theme_light) })
            assertTrue("snapshot $i: the gradient's switch never on", switch(R.string.settings_gradient_title, nodes) != true)
            assertTrue("snapshot $i: the grain's switch never on", switch(R.string.settings_grain_title, nodes) != true)
        }
        // the snapshots did see the screen with its rows (else the checks above prove nothing)
        val seen = snapshots.count { n -> switch(R.string.settings_gradient_title, n) == false && n.any { UiTestTags.AppearanceRoot in it.tags && it.text == s(R.string.settings_theme_dark) } }
        d.log("appearance: $seen of ${snapshots.size} snapshots show the rows with the saved values")
        assertTrue("the snapshots saw the rows: the gradient's switch off and the theme Dark", seen > 0)
        assertEquals("the very first snapshot already has the rows", false, switch(R.string.settings_gradient_title, first))

        // for the eye: the pixels of a second entry, frame after frame while the screens cross-fade (a screenshot takes ~100 ms, so
        // these are taken apart from the snapshots above, which they would thin out)
        d.back()
        d.await("Settings") { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.settle(1_000)
        val again = d.byTag(UiTestTags.SettingsAppearanceRow).first { d.onScreen(it) }.bounds
        val down2 = SystemClock.uptimeMillis()
        d.inject(down2, MotionEvent.ACTION_DOWN, again.centerX(), again.centerY())
        sleepUntil(down2 + 60)
        val tapped2 = d.lift(down2, again.centerX(), again.centerY())
        val frames = buildList { while (SystemClock.uptimeMillis() < tapped2 + 900) add((SystemClock.uptimeMillis() - tapped2) to d.screenshot()) }
        frames.forEach { (ms, b) -> savePng(ctx, "am-appearance-enter-${ms}ms", b) }
        d.log("appearance: the second entry, frames at ${frames.map { it.first }} ms after the tap")
    }

    // ------------------------------------------------------------------ the rows

    /** "Background animation" shows only with the gradient on (spec §2.2); hidden and shown again, it keeps its value. */
    @Test
    fun theAnimationRowShowsOnlyWithTheGradientAndKeepsItsValue() {
        theme(ThemeMode.LIGHT, grain = true, gradient = true, motion = true)
        d.launch()
        openSettings()
        openAppearance()
        d.tap(d.byTag(UiTestTags.AppearanceMotionRow).first { d.onScreen(it) })
        d.awaitTrue("the animation off") { runBlocking { !prefs.backgroundMotionEnabled.first() } && switch(R.string.settings_background_motion_title) == false }

        d.tap(d.byTag(UiTestTags.AppearanceGradientRow).first { d.onScreen(it) })
        d.awaitTrue("the gradient off") { runBlocking { !prefs.gradientEnabled.first() } && switch(R.string.settings_gradient_title) == false }
        assertTrue("gradient off: no \"${s(R.string.settings_background_motion_title)}\"", d.texts(s(R.string.settings_background_motion_title)).none { d.onScreen(it) })
        assertTrue("gradient off: the grain's row stays", d.texts(s(R.string.settings_grain_title)).any { d.onScreen(it) })

        d.tap(d.byTag(UiTestTags.AppearanceGradientRow).first { d.onScreen(it) })
        d.awaitTrue("the gradient on") { runBlocking { prefs.gradientEnabled.first() } }
        d.await("the animation's row back") { d.byTag(UiTestTags.AppearanceMotionRow).firstOrNull { d.onScreen(it) } }
        assertEquals("the animation's row is back with its value (off)", false, switch(R.string.settings_background_motion_title))

        d.tap(d.byTag(UiTestTags.AppearanceMotionRow).first { d.onScreen(it) })
        d.awaitTrue("the animation on") { runBlocking { prefs.backgroundMotionEnabled.first() } && switch(R.string.settings_background_motion_title) == true }
    }

    /** The grain's sliders show only with grain on; a tap at the ends of their tracks saves 1 and 0 (intensity), 4 and 1 (size). */
    @Test
    fun theGrainSlidersShowOnlyWithGrainAndSaveTheirEnds() {
        theme(ThemeMode.LIGHT, grain = true, gradient = true)
        d.launch()
        openSettings()
        openAppearance()

        fun slider(label: Int): UiNode {
            val l = d.reveal(s(label))
            // the slider right under its label (past the track's end a tap does nothing)
            return d.nodes().filter { it.bounds.top in l.bounds.bottom..l.bounds.bottom + d.dp(16f) && it.bounds.width() > d.screenWidth() / 2 }
                .minByOrNull { it.bounds.height() } ?: error("no slider under \"${s(label)}\"")
        }
        var bar = slider(R.string.settings_grain_intensity)
        d.tap(bar.bounds.right - d.dp(14f), bar.bounds.centerY())
        d.awaitTrue("the intensity's right end saves 1") { runBlocking { prefs.grainIntensity.first() } > 0.95f }
        bar = slider(R.string.settings_grain_intensity)
        d.tap(bar.bounds.left + d.dp(14f), bar.bounds.centerY())
        d.awaitTrue("the intensity's left end saves 0") { runBlocking { prefs.grainIntensity.first() } < 0.05f }

        bar = slider(R.string.settings_grain_size)
        d.tap(bar.bounds.right - d.dp(14f), bar.bounds.centerY())
        d.awaitTrue("the size's right end saves 4") { runBlocking { prefs.grainSize.first() } == 4 }
        bar = slider(R.string.settings_grain_size)
        d.tap(bar.bounds.left + d.dp(14f), bar.bounds.centerY())
        d.awaitTrue("the size's left end saves 1") { runBlocking { prefs.grainSize.first() } == 1 }

        d.tap(d.reveal(s(R.string.settings_grain_title)))
        d.awaitTrue("grain off") { runBlocking { !prefs.grainEnabled.first() } }
        assertTrue("grain off: no \"Grain size\"", d.texts(s(R.string.settings_grain_size)).none { d.onScreen(it) })
        assertTrue("grain off: no \"Intensity\"", d.texts(s(R.string.settings_grain_intensity)).none { d.onScreen(it) })
        d.tap(d.reveal(s(R.string.settings_grain_title)))
        d.awaitTrue("grain on again") { runBlocking { prefs.grainEnabled.first() } }
        d.reveal(s(R.string.settings_grain_size))
        d.reveal(s(R.string.settings_grain_intensity))
    }

    // ------------------------------------------------------------------ licenses

    /**
     * About → "Open source licenses" (spec §4): its screen lists the plugin's entries — in a debug build the plugin's one "Debug License
     * Info" — a tap shows the entry's text, Back closes the text, the top bar's arrow returns to Settings.
     */
    @Test
    fun licensesOpenFromAboutListTheDebugEntryAndShowItsText() {
        theme(ThemeMode.LIGHT)
        d.launch()
        openSettings()
        d.tap(d.reveal(s(R.string.settings_licenses), bottomDp = 48f))
        d.await("the licenses", 15_000) { d.byTag(UiTestTags.LicensesRoot).firstOrNull() }
        d.await("an entry") { d.nodes().firstOrNull { UiTestTags.LicensesRoot in it.tags && it.text == DebugEntry && d.onScreen(it) } }
        d.settle(600)
        val texts = d.nodes().filter { UiTestTags.LicensesRoot in it.tags && it.text.isNotEmpty() && d.onScreen(it) }.map { it.text }
        d.log("licenses: $texts")
        assertTrue("the title \"${s(R.string.settings_licenses)}\": $texts", s(R.string.settings_licenses) in texts)
        assertEquals("debug: the plugin's one entry", listOf(DebugEntry), texts.filter { it != s(R.string.settings_licenses) })

        d.tap(d.texts(DebugEntry).first { d.onScreen(it) && UiTestTags.LicensesRoot in it.tags })
        val text = d.await("the license's text") { d.byTag(UiTestTags.LicensesText).firstOrNull { it.text.isNotBlank() } }
        d.save("licenses-text")
        d.log("license text (${text.text.length} chars): ${text.text.take(120)}")
        assertTrue("the entry's text is shown: \"${text.text.take(80)}\"", text.text.length > 10)

        d.back()
        d.awaitTrue("Back closes the text") { d.byTag(UiTestTags.LicensesText).isEmpty() }
        assertTrue("the licenses' screen stays", d.byTag(UiTestTags.LicensesRoot).isNotEmpty())
        d.tap(d.byTag(UiTestTags.LicensesBack).first { d.onScreen(it) })
        d.await("Settings after the top bar's arrow") { settings() }
        d.awaitTrue("the licenses' screen gone") { d.byTag(UiTestTags.LicensesRoot).isEmpty() }
    }

    private companion object {
        /** The one entry the oss-licenses plugin writes into a debug build. */
        const val DebugEntry = "Debug License Info"
    }
}

