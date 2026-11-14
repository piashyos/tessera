package app.tessera.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.BackdropGains
import app.tessera.core.ui.theme.BackdropReturnStiffness
import app.tessera.core.ui.theme.FlingFriction
import app.tessera.core.ui.theme.FlingVelocityThreshold
import app.tessera.core.ui.theme.PulseFallDamping
import app.tessera.core.ui.theme.PulseFallStiffness
import app.tessera.ui.notes.AppearanceAppTest
import app.tessera.ui.notes.AppearanceDevice
import app.tessera.ui.notes.UiNode
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live background (spec 2026-11-13-appearance-screen-and-motion §2.3, §3) in the real app, with real touches and the device's real
 * keyboard, on the real clocks (no Compose rule: under one, Compose animations run only while the test's clock is advanced).
 *
 * Measured on pixels of the whole screen (`UiAutomation.takeScreenshot`) inside an area where only the background shows: on the empty
 * list, the band between the top bar and the "No notes yet" text; in the editor, the band between the last line and the format bar.
 * - At rest three frames 400 ms apart are equal pixel for pixel: the noise floor is 0 (grain is static; nothing animates).
 * - A smooth gradient moves by a few colour levels a channel, so "changed" is any pixel that differs at all; "moved" is at least
 *   [MovedShare] of the band changed (see the QA doc for the measured shares — many times more, at a noise floor of 0).
 * - Back at rest means the frame from before the touch again: 0 changed pixels (the springs end exactly on rest). It must come within a
 *   window from the animation's constants (the same sums as `GrainOverlayGestureTest`) plus [DeviceSlackMs] for the emulator's frames.
 *
 * Needs `animator_duration_scale` ≠ 0 (checked) and, for the keyboard, Gboard's full panel: on an AVD with a hardware keyboard
 * `settings put secure show_ime_with_hard_keyboard 1` and `settings put secure stylus_handwriting_enabled 0`. Frames of the moments are
 * kept in [ShotDir] (`adb pull`).
 */
@HiltAndroidTest
class BackgroundGradientTest : AppearanceAppTest() {

    @After
    fun backToDefaults() = runBlocking {
        prefs.setGradientEnabled(true)
        prefs.setBackgroundMotionEnabled(true)
        prefs.setAmoled(false)
        prefs.setGrainSize(1)
    }

    private fun background(mode: ThemeMode, gradient: Boolean, grain: Boolean = true, amoled: Boolean = false, motion: Boolean = true) {
        theme(mode, grain = grain, gradient = gradient, motion = motion)
        runBlocking { prefs.setAmoled(amoled) }
        d.shell("mkdir -p $ShotDir")
        val scale = d.shell("settings get global animator_duration_scale").trim()
        d.log("animator_duration_scale = $scale")
        check(scale != "0" && scale != "0.0") { "animator_duration_scale is $scale: animations are off on the device, the blobs never move" }
    }

    // ------------------------------------------------------------------ a tap: the breath, on release

    /**
     * A tap (spec §3.1–§3.2): the press alone leaves the background still, the release starts the breath, and the band is the frame at
     * rest again within the breath's window.
     */
    @Test
    fun aTapBreathesOnReleaseAndTheBackgroundIsBackAtRestWithinTheBreathsWindow() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        val band = emptyListBand()
        val rest = d.restFrame(band, "before the tap")
        val (x, y) = d.screenWidth() / 2 to band.centerY()
        val down = SystemClock.uptimeMillis()
        d.inject(down, MotionEvent.ACTION_DOWN, x, y)
        sleepUntil(down + 80)
        val pressed = diff(rest, d.screenshot(), band)
        val up = d.lift(down, x, y)
        d.log("tap: the finger was down ${up - down} ms (a tap is ≤ ${ViewConfiguration.getLongPressTimeout()} ms)")
        check(up - down < ViewConfiguration.getLongPressTimeout()) {
            "the screenshot while pressed took the finger past the long-press timeout (${up - down} ms): the host is too slow, rerun"
        }
        val peak = d.peakOf(d.series(rest, band, up, 800), "tap, light")
        val back = d.awaitBack(rest, band, up, breathMs, "tap, light")
        d.log("tap: band $band; while pressed: $pressed; peak: $peak; back at rest ${back.first} ms after the up (window ${breathMs + DeviceSlackMs} ms)")
        assertEquals("the press alone leaves the background still — $pressed", 0, pressed.changed)
        assertTrue("the release breathes the background — peak $peak", peak.second.share >= MovedShare)
        assertTrue("back at rest within the breath's window ${breathMs + DeviceSlackMs} ms: ${back.first} ms", back.second)
    }

    /** A finger held still past the long-press timeout (spec §3.1): no breath, no glide — the band stays the frame at rest. */
    @Test
    fun aLongPressWithoutMovingLeavesTheBackgroundStill() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        val band = emptyListBand()
        val rest = d.restFrame(band, "before the long press")
        val (x, y) = d.screenWidth() / 2 to band.centerY()
        val down = SystemClock.uptimeMillis()
        d.inject(down, MotionEvent.ACTION_DOWN, x, y)
        sleepUntil(down + 1_000)
        val held = diff(rest, d.screenshot(), band)
        val up = d.lift(down, x, y)
        val after = d.series(rest, band, up, 1_200).maxByOrNull { it.second.changed }!!
        d.log("long press: band $band; held: $held; the most changed frame after the up: $after")
        assertEquals("a long press held still leaves the background still — $held", 0, held.changed)
        assertEquals("after a long press there is no breath — ${after.first} ms after the up: ${after.second}", 0, after.second.changed)
    }

    // ------------------------------------------------------------------ a drag: a small shift, less than the breath

    @Test
    fun aHeldDragShiftsTheBackgroundLessThanATapBreathesAndItSettles_light() = dragShiftsLessThanATap(ThemeMode.LIGHT)

    @Test
    fun aHeldDragShiftsTheBackgroundLessThanATapBreathesAndItSettles_dark() = dragShiftsLessThanATap(ThemeMode.DARK)

    /** Spec §3.3, §7: a drag moves the colours — and less than a tap's breath ("noticeable, never in your face"); then back at rest. */
    private fun dragShiftsLessThanATap(mode: ThemeMode) {
        val t = mode.name.lowercase()
        background(mode, gradient = true)
        d.launch()
        val band = emptyListBand()
        var rest = d.restFrame(band, "before the tap")
        shot("list-$t-rest")
        val (cx, cy) = d.screenWidth() / 2 to band.centerY()
        val tapDown = SystemClock.uptimeMillis()
        d.inject(tapDown, MotionEvent.ACTION_DOWN, cx, cy)
        sleepUntil(tapDown + 60)
        val tapUp = d.lift(tapDown, cx, cy)
        val breath = d.peakOf(d.series(rest, band, tapUp, 800), "tap, $t").second

        rest = d.restFrame(band, "before the drag")
        val x = d.screenWidth() * 2 / 3
        val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
        Thread.sleep(500) // held still: the blobs have followed the pull
        val held = diff(rest, d.screenshot(), band)
        shot("list-$t-held")
        val up = d.lift(down, x, band.bottom - d.dp(8f))
        val back = d.awaitBack(rest, band, up, returnMs, "held drag, $t")
        d.log("$t: band $band; tap's breath at its peak: $breath; held drag: $held; back at rest ${back.first} ms after the up")
        assertTrue("$t: a held drag moves the background — $held", held.share >= MovedShare)
        assertTrue("$t: a held drag changes the colours less than a tap's breath — drag $held, breath $breath", held.mean < breath.mean)
        assertTrue("$t: back at rest within the return's window ${returnMs + DeviceSlackMs} ms: ${back.first} ms", back.second)
    }

    // ------------------------------------------------------------------ a swipe: the colours glide on after the finger is up

    /**
     * A short quick swipe (spec §3.3): released at speed, the colours glide on after the finger is up — further than after the very same
     * swipe held still before the up, which only returns (both are inside the soft limit: the swipe's travel × the gains stays well under
     * it, the glide has room); then the band is at rest within the fling and return windows.
     */
    @Test
    fun aQuickSwipeGlidesFurtherThanTheSameSwipeHeldStillAndSettlesWithinTheWindow() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        val band = emptyListBand()
        val x = d.screenWidth() / 2
        val (y0, y1) = band.top + d.dp(24f) to band.top + d.dp(24f) + d.dp(110f)

        var rest = d.restFrame(band, "before the held swipe")
        val heldUp = d.swipe(x, y0, y1, holdMs = 300)
        val held = d.peakOf(d.series(rest, band, heldUp, 1_200), "the swipe held still before the up")
        d.awaitBack(rest, band, heldUp, returnMs, "the held swipe")

        rest = d.restFrame(band, "before the swipe")
        val up = d.swipe(x, y0, y1)
        val frames = d.series(rest, band, up, 1_200)
        val glide = d.peakOf(frames, "the swipe released at speed")
        val back = d.awaitBack(rest, band, up, flingMs + returnMs, "the swipe")
        d.log("swipe: band $band; frames after the up: ${frames.joinToString { "${it.first} ms: %.2f".format(it.second.mean) }}")
        d.log("swipe: held still before the up — peak $held; released at speed — peak $glide; back at rest ${back.first} ms after the up")
        assertTrue("a swipe moves the background — peak $glide", glide.second.share >= MovedShare)
        assertTrue(
            "released at speed the colours glide further than after the same swipe held still: %.2f vs %.2f levels on average (≥ ×%.2f)"
                .format(glide.second.mean, held.second.mean, GlideGain),
            glide.second.mean >= held.second.mean * GlideGain,
        )
        assertTrue("back at rest within the fling + return window ${flingMs + returnMs + DeviceSlackMs} ms: ${back.first} ms", back.second)
    }

    // ------------------------------------------------------------------ nothing moves

    /**
     * "Background animation" off (spec §2.3): the gradient is drawn but stands still under a tap and a drag; turned on again (the app
     * running on), the same tap breathes it — so the zero is the setting, not a background that cannot move.
     */
    @Test
    fun withTheAnimationOffATapAndADragLeaveTheBackgroundStillAndOnAgainATapBreathesIt() {
        background(ThemeMode.LIGHT, gradient = true, motion = false)
        d.launch()
        val band = emptyListBand()
        val rest = d.restFrame(band, "animation off, before the touches")
        assertStillUnderATapAndADrag(rest, band, "animation off")

        runBlocking { prefs.setBackgroundMotionEnabled(true) }
        d.settle(800)
        val (x, y) = d.screenWidth() / 2 to band.centerY()
        val down = SystemClock.uptimeMillis()
        d.inject(down, MotionEvent.ACTION_DOWN, x, y)
        sleepUntil(down + 60)
        val peak = d.peakOf(d.series(rest, band, d.lift(down, x, y), 800), "animation on again")
        d.log("animation on again: the tap's peak $peak")
        assertTrue("the animation on again: a tap breathes the background — $peak", peak.second.share >= MovedShare)
    }

    @Test
    fun withTheGradientOffATapAndADragLeaveTheEmptyListStill() {
        background(ThemeMode.LIGHT, gradient = false)
        d.launch()
        val band = emptyListBand()
        val rest = d.restFrame(band, "gradient off, before the touches")
        assertStillUnderATapAndADrag(rest, band, "gradient off")
    }

    /** AMOLED + dark: true black, no gradient; grain off so the band must be flat black, and it stays so under a tap and a drag. */
    @Test
    fun amoledDarkHasNoGradientAndATapAndADragLeaveItBlack() {
        background(ThemeMode.DARK, gradient = true, grain = false, amoled = true)
        d.launch()
        val band = emptyListBand()
        val rest = d.restFrame(band, "AMOLED, before the touches")
        shot("list-amoled-dark-rest")
        val notBlack = rest.count(band) { (it and 0xFFFFFF) != 0 }
        assertEquals("AMOLED dark: the empty band is pure black (no blobs)", 0, notBlack)
        assertStillUnderATapAndADrag(rest, band, "AMOLED dark")
    }

    /** System "Remove animations" (spec §3.4): no breath, no shift — the blobs stay at rest. */
    @Test
    fun withAnimationsRemovedATapAndADragLeaveTheBackgroundAtRest() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        val band = emptyListBand()
        val was = d.shell("settings get global animator_duration_scale").trim()
        try {
            d.shell("settings put global animator_duration_scale 0")
            d.settle(1_000)
            val rest = d.restFrame(band, "animations removed, before the touches")
            assertStillUnderATapAndADrag(rest, band, "animations removed")
        } finally {
            d.shell("settings put global animator_duration_scale ${was.ifEmpty { "1" }.let { if (it == "null") "1" else it }}")
        }
    }

    /** A tap (frames for 800 ms after the up), then a held drag and 600 ms after it: every frame of [band] is [rest]. */
    private fun assertStillUnderATapAndADrag(rest: Bitmap, band: Rect, what: String) {
        val (cx, cy) = d.screenWidth() / 2 to band.centerY()
        val tapDown = SystemClock.uptimeMillis()
        d.inject(tapDown, MotionEvent.ACTION_DOWN, cx, cy)
        sleepUntil(tapDown + 60)
        val tap = d.series(rest, band, d.lift(tapDown, cx, cy), 800).maxByOrNull { it.second.changed }!!

        val x = d.screenWidth() * 2 / 3
        val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
        Thread.sleep(500)
        val held = diff(rest, d.screenshot(), band)
        val up = d.lift(down, x, band.bottom - d.dp(8f))
        sleepUntil(up + 600)
        val later = diff(rest, d.screenshot(), band)
        d.log("$what: band $band; the tap's most changed frame (${tap.first} ms after the up): ${tap.second}; held drag: $held; 600 ms after: $later")
        assertEquals("$what: a tap leaves the band as it was — ${tap.first} ms after the up: ${tap.second}", 0, tap.second.changed)
        assertEquals("$what: a held drag leaves the band as it was — $held", 0, held.changed)
        assertEquals("$what: after the drag the band is as it was — $later", 0, later.changed)
    }

    // ------------------------------------------------------------------ the switches in Settings → Appearance act on the whole app

    /**
     * Settings → Appearance (spec §2.2–§2.3) in the real app: "Background animation" off stops the list's background under a tap and a
     * drag, on again brings the breath back; "Background gradient" off leaves the list flat and still.
     */
    @Test
    fun theAppearanceSwitchesStopTheBackgroundForTheWholeApp() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        openSettings()
        openAppearance()
        d.tap(d.byTag(UiTestTags.AppearanceMotionRow).first { d.onScreen(it) })
        d.awaitTrue("the animation off") { runBlocking { !prefs.backgroundMotionEnabled.first() } }
        d.back()
        d.await("Settings") { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.back()
        var band = emptyListBand()
        var rest = d.restFrame(band, "the list after the animation was turned off in Appearance")
        assertStillUnderATapAndADrag(rest, band, "the animation turned off in Appearance")

        openSettings()
        openAppearance()
        d.tap(d.byTag(UiTestTags.AppearanceMotionRow).first { d.onScreen(it) })
        d.awaitTrue("the animation on") { runBlocking { prefs.backgroundMotionEnabled.first() } }
        d.back()
        d.await("Settings") { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.back()
        band = emptyListBand()
        rest = d.restFrame(band, "the list after the animation was turned on in Appearance")
        val (x, y) = d.screenWidth() / 2 to band.centerY()
        val down = SystemClock.uptimeMillis()
        d.inject(down, MotionEvent.ACTION_DOWN, x, y)
        sleepUntil(down + 60)
        val peak = d.peakOf(d.series(rest, band, d.lift(down, x, y), 800), "the animation turned on in Appearance")
        assertTrue("the animation turned on in Appearance: a tap breathes the list's background — $peak", peak.second.share >= MovedShare)

        openSettings()
        openAppearance()
        d.tap(d.byTag(UiTestTags.AppearanceGradientRow).first { d.onScreen(it) })
        d.awaitTrue("the gradient off") { runBlocking { !prefs.gradientEnabled.first() } }
        d.back()
        d.await("Settings") { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.back()
        band = emptyListBand()
        rest = d.restFrame(band, "the list after the gradient was turned off in Appearance")
        assertStillUnderATapAndADrag(rest, band, "the gradient turned off in Appearance")
    }

    // ------------------------------------------------------------------ the background takes nothing from the screens

    /**
     * The background's listener takes nothing from the screens (spec §3.1: it never consumes) — in the real app, with the gradient and
     * its animation on: the list scrolls under a drag, a card's swipe reveals its action, a tap opens the note. (`NotesListScreenTest`,
     * `TesseraSwipeableNoteCardTest` and `TesseraPopupSwipeTest` set their own content, without the background.)
     */
    @Test
    fun withTheGradientOnTheListScrollsACardSwipesOpenAndATapOpensTheNote() {
        background(ThemeMode.LIGHT, gradient = true)
        repeat(30) { note("n-$it", "Note $it") }
        d.launch()
        val top = d.await("the newest card") { d.card("n-29") }.bounds.top
        d.drag(d.screenWidth() / 2, d.screenHeight() * 3 / 4, d.screenHeight() / 4)
        d.awaitTrue("the list scrolled under the drag") { (d.card("n-29")?.bounds?.top ?: Int.MIN_VALUE) < top - d.dp(100f) }

        val card = d.nodes().filter { n -> n.tag?.startsWith("note_card_") == true && d.onScreen(n) }
            .firstOrNull { it.bounds.top > d.dp(120f) && it.bounds.bottom < d.screenHeight() - d.dp(160f) } ?: error("no card in the middle")
        val id = card.tag!!.removePrefix("note_card_")
        val y = card.bounds.centerY()
        val t0 = SystemClock.uptimeMillis()
        d.inject(t0, MotionEvent.ACTION_DOWN, d.screenWidth() * 4 / 5, y)
        for (i in 1..20) {
            Thread.sleep(16)
            d.inject(t0, MotionEvent.ACTION_MOVE, d.screenWidth() * 4 / 5 - d.screenWidth() * 3 / 5 * i / 20, y)
        }
        Thread.sleep(150)
        d.inject(t0, MotionEvent.ACTION_UP, d.screenWidth() / 5, y)
        d.awaitTrue("a swipe to the left reveals the card's delete action") {
            d.byTag(UiTestTags.swipeAction(id, "delete")).any { d.onScreen(it) && it.bounds.height() > 0 }
        }
        assertTrue("the swipe alone deletes nothing", runBlocking { noteDao.getById(id) }?.deletedAt == null)

        val other = d.nodes().filter { n -> n.tag?.startsWith("note_card_") == true && n.tag != UiTestTags.noteCard(id) && d.onScreen(n) }
            .firstOrNull { it.bounds.top > d.dp(120f) && it.bounds.bottom < d.screenHeight() - d.dp(160f) } ?: error("no other card")
        d.tap(other)
        // the first tap may only close the open card (single-open list): then a second one opens the note
        val opened = runCatching { d.await("the editor", 8_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() } }.isSuccess
        if (!opened) d.tap(d.card(other.tag!!.removePrefix("note_card_")) ?: error("the card is gone"))
        d.await("a tap opens the note", 30_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() }
    }

    /** The band of the empty list between the top bar (its "Folders" button) and the "No notes yet" text, inset from the sides. */
    private fun emptyListBand(): Rect {
        val title = d.await("the empty list") { d.texts(s(R.string.notes_empty_title)).firstOrNull { d.onScreen(it) } }
        val bar = d.descs(s(R.string.folders_open_drawer)).filter { d.onScreen(it) }.minByOrNull { it.bounds.top } ?: error("no top bar")
        val band = Rect(d.dp(16f), bar.bounds.bottom + d.dp(24f), d.screenWidth() - d.dp(16f), title.bounds.top - d.dp(24f))
        check(band.height() >= d.dp(80f)) { "the empty band is too small: $band" }
        return band
    }

    // ------------------------------------------------------------------ the editor

    @Test
    fun typingWithTheKeyboardsKeysLeavesTheEditorsBackgroundStillAndADragThereMovesIt() {
        background(ThemeMode.LIGHT, gradient = true)
        note("n-type", "Gradient", body = "Short")
        d.launch()
        d.tap(d.card("n-type") ?: error("no card"))
        d.await("the editor", 60_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() }
        d.awaitTrue("the list left") { d.byTag(UiTestTags.NotesListRoot).isEmpty() }
        val e = d.await("the text view", 15_000) { editText() }
        d.settle(800)

        // a tap on the end of the last line: the caret, the keyboard (the tap itself is a touch and breathes the background)
        val line = d.ui { lastLine(e) }
        d.tap(line.endX - d.dp(2f), line.mid)
        val imeTop = d.awaitIme()
        d.await("Gboard's letters", 15_000) { key("q") }
        Thread.sleep(breathMs + DeviceSlackMs) // past the tap's breath and the keyboard's slide
        val textBottom = d.ui { lastLine(e) }.bottom
        val firstBelow = d.nodes().filter { d.onScreen(it) && it.bounds.top > textBottom && it.bounds.height() < d.screenHeight() / 2 }
            .minOfOrNull { it.bounds.top } ?: imeTop
        val band = Rect(d.dp(16f), textBottom + d.dp(32f), d.screenWidth() - d.dp(16f), minOf(firstBelow, imeTop) - d.dp(24f))
        check(band.height() >= d.dp(60f)) { "the editor's empty band is too small: $band (text bottom $textBottom, below $firstBelow, ime $imeTop)" }
        val rest = d.restFrame(band, "the editor with the keyboard up")
        shot("editor-light-keyboard-rest")

        var worst = Diff(0, band.width() * band.height(), 0, 0)
        for (ch in "hello world") {
            pressKey(if (ch == ' ') "Space" else ch.toString())
            val now = diff(rest, d.screenshot(), band)
            if (now.changed > worst.changed) worst = now
        }
        d.settle(800)
        val typed = d.ui { e.text.toString() }
        val after = diff(rest, d.screenshot(), band)
        shot("editor-light-typed")
        d.log("editor: band $band; typed \"$typed\"; worst frame while typing: $worst; after typing: $after")
        assertTrue("the keys reached the note: \"$typed\"", "hello world" in typed)
        assertEquals("typing leaves the editor's background still — worst frame: $worst", 0, worst.changed)
        assertEquals("after typing the editor's background is as before — $after", 0, after.changed)

        // the same band does move under a finger (it is the background, not a surface over it)
        val y = band.centerY()
        val t0 = SystemClock.uptimeMillis()
        d.inject(t0, MotionEvent.ACTION_DOWN, d.screenWidth() / 5, y)
        for (i in 1..20) {
            Thread.sleep(16)
            d.inject(t0, MotionEvent.ACTION_MOVE, d.screenWidth() / 5 + d.screenWidth() * 3 / 5 * i / 20, y)
        }
        Thread.sleep(500)
        val held = d.screenshot()
        shot("editor-light-held")
        d.inject(t0, MotionEvent.ACTION_UP, d.screenWidth() * 4 / 5, y)
        val moved = diff(rest, held, band)
        d.log("editor: held drag vs rest: $moved")
        assertTrue("a held drag moves the editor's background in $band — $moved", moved.share >= MovedShare)
    }

    private fun editText(): EditText? {
        val a = d.activity() ?: return null
        return d.ui { find(a.window.decorView)?.takeIf { it.isAttachedToWindow && it.layout != null } }
    }

    private fun find(v: View): EditText? {
        if (v is EditText) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }

    private class Line(val mid: Int, val endX: Int, val bottom: Int)

    /** The last line of [e] on the screen. Main thread. */
    private fun lastLine(e: EditText): Line {
        val loc = IntArray(2).also(e::getLocationOnScreen)
        val l = e.layout
        val last = l.lineCount - 1
        val top = loc[1] + e.totalPaddingTop - e.scrollY
        return Line(top + (l.getLineTop(last) + l.getLineBottom(last)) / 2, loc[0] + e.totalPaddingLeft + l.getLineRight(last).toInt(), top + l.getLineBottom(last))
    }

    // ------------------------------------------------------------------ Gboard

    private fun imeNodes(): List<AccessibilityNodeInfo> = buildList {
        fun walk(n: AccessibilityNodeInfo) { add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::walk) }
        d.ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root?.let(::walk)
    }

    private fun key(desc: String): Rect? = imeNodes().firstOrNull { it.contentDescription?.toString().equals(desc, ignoreCase = desc.length == 1) }
        ?.let { n -> Rect().also(n::getBoundsInScreen) }

    /** A key of the keyboard pressed the way a finger does (a tap in the keyboard's window). */
    private fun pressKey(desc: String) {
        val r = key(desc) ?: error("no key \"$desc\" on the keyboard")
        d.shell("input tap ${r.centerX()} ${r.centerY()}")
        d.settle(150)
    }

    // ------------------------------------------------------------------ touches, frames

    /** A finger down at ([x], [y0]) and moved slowly (a move a frame) to ([x], [y1]), still down. Returns the down time. */
    private fun press(x: Int, y0: Int, y1: Int, steps: Int = 20): Long {
        val t0 = SystemClock.uptimeMillis()
        d.inject(t0, MotionEvent.ACTION_DOWN, x, y0)
        for (i in 1..steps) {
            Thread.sleep(16)
            d.inject(t0, MotionEvent.ACTION_MOVE, x, y0 + (y1 - y0) * i / steps)
        }
        return t0
    }

    private fun shot(name: String) = d.shell("screencap -p $ShotDir/$name.png")

    private fun Bitmap.count(r: Rect, pred: (Int) -> Boolean): Int {
        var n = 0
        for (y in r.top until r.bottom) for (x in r.left until r.right) if (pred(getPixel(x, y))) n++
        return n
    }

    // Windows until the background is at rest again, from the animation's constants — the sums of GrainOverlayGestureTest: the
    // breath rises in 0.4 s and falls within 6 / (damping × √stiffness) s; a fling lasts ln(v0 / threshold) / (4.2 × friction) s
    // (v0 ≤ the device's fastest fling × the largest gain); the critically damped return gets under the visibility threshold within
    // 10 / √stiffness s.
    private val breathMs = 400 + (6f / (PulseFallDamping * sqrt(PulseFallStiffness)) * 1000).toLong()
    private val returnMs = (10f / sqrt(BackdropReturnStiffness) * 1000).toLong()
    private val flingMs get() = (ln(ViewConfiguration.get(ctx).scaledMaximumFlingVelocity * BackdropGains[0] / FlingVelocityThreshold) /
        (4.2f * FlingFriction) * 1000).toLong() + 100

    private companion object {
        /** The share of the band a moving background must change (measured many times more at a noise floor of 0; see the QA doc). */
        const val MovedShare = 0.05

        /**
         * How much further a swipe released at speed carries the colours than the same swipe held still. On paper, for 110 dp (220 px
         * here) in 96 ms the held shift of blob A is 0.12·720·tanh(0.2·220 / 86) ≈ 40 px and the glide (v ≈ 2300 px/s × 0.2, end
         * v / 6.72 ≈ +68 px, cut to the 86 px circle) about doubles it. The moves are stamped 16 ms apart whatever the app's frames
         * (a touchscreen's clock; the injection itself waits for the app and takes longer on emulator-5556 — see the swipe's log), so
         * the glide gets the swipe's own velocity. After review fixes A.3–A.4 this test's peaks gave ×1.45 (1.46 vs 1.01 levels; the
         * strength series, before A.4, ×1.73 for the same travel); without a glide the ratio would be ~1.0 (the same travel), and
         * before fix A.1 (a stale sample at the release) the series gave ×1.04–1.22. ×1.25 sits between.
         */
        const val GlideGain = 1.25
    }
}

/** Where the live background's QA shots go on the device (`adb pull`). */
internal const val ShotDir = "/data/local/tmp/appearance-motion-qa"

/** The emulator draws a frame and takes a screenshot late (SwiftShader, a loaded host): added to every window from the constants. */
internal const val DeviceSlackMs = 700L

/**
 * A scrolling screen (Settings) dragged by a finger until [text] is in view, clear of the top bar and [bottomDp] above the bottom edge
 * (room for a slider under a label; the last row of a list can only come up to the list's end: pass less).
 */
internal fun AppearanceDevice.reveal(text: String, bottomDp: Float = 120f): UiNode {
    repeat(10) {
        val n = texts(text).firstOrNull { onScreen(it) }
        if (n != null && n.bounds.top >= dp(140f) && n.bounds.bottom <= screenHeight() - dp(bottomDp)) return n
        if (n != null && n.bounds.top < dp(140f)) {
            drag(screenWidth() / 2, screenHeight() * 2 / 5, screenHeight() * 3 / 5) // above: back down a little
        } else {
            drag(screenWidth() / 2, screenHeight() * 3 / 4, screenHeight() * 2 / 5)
        }
        log("reveal \"$text\": ${n?.bounds}; after a drag the lowest texts: " +
            nodes().filter { it.text.isNotEmpty() && onScreen(it) }.sortedBy { it.bounds.bottom }.takeLast(3).joinToString { "${it.text} ${it.bounds}" })
    }
    error("\"$text\" is not scrolled into view; on screen: ${visibleTexts()}")
}

/**
 * How [b] differs from [a] inside [r]: the pixels with any channel off, the largest step of a channel, and the sum of the per-pixel
 * largest steps ([mean] over the band: how much the colours changed overall, in levels).
 */
internal class Diff(val changed: Int, val total: Int, val maxStep: Int, val sumStep: Long) {
    val share get() = changed.toDouble() / total
    val mean get() = sumStep.toDouble() / total
    override fun toString() = "%d of %d px changed (%.1f %%), max step %d, mean %.2f".format(changed, total, share * 100, maxStep, mean)
}

internal fun diff(a: Bitmap, b: Bitmap, r: Rect): Diff {
    val w = r.width()
    val h = r.height()
    val pa = IntArray(w * h).also { a.getPixels(it, 0, w, r.left, r.top, w, h) }
    val pb = IntArray(w * h).also { b.getPixels(it, 0, w, r.left, r.top, w, h) }
    var changed = 0
    var max = 0
    var sum = 0L
    for (i in pa.indices) {
        val p = pa[i]
        val q = pb[i]
        if (p == q) continue
        changed++
        val step = maxOf(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
        sum += step
        if (step > max) max = step
    }
    return Diff(changed, w * h, max, sum)
}

/**
 * A touch event injected into the window, as a finger makes it, stamped [at]. The injection waits until the app has handled the
 * event, so on slow frames the injections fall behind; a finger's samples keep the touchscreen's clock — pass the planned time.
 */
internal fun AppearanceDevice.inject(down: Long, action: Int, x: Int, y: Int, at: Long = SystemClock.uptimeMillis()) {
    val ev = MotionEvent.obtain(down, at, action, x.toFloat(), y.toFloat(), 0)
    ev.source = InputDevice.SOURCE_TOUCHSCREEN
    ua.injectInputEvent(ev, true)
    ev.recycle()
}

/** The finger of a gesture that went down at [down] up at ([x], [y]), stamped [at]; returns the time it went up. */
internal fun AppearanceDevice.lift(down: Long, x: Int, y: Int, at: Long = SystemClock.uptimeMillis()): Long {
    inject(down, MotionEvent.ACTION_UP, x, y, at)
    return SystemClock.uptimeMillis()
}

/**
 * A quick swipe from ([x], [y0]) to ([x], [y1]) in [steps] moves 16 ms apart, then the finger held still [holdMs] (0: up a frame
 * later, the velocity stays). The events carry the planned times, as a touchscreen stamps them, however slow the app's frames are.
 * Returns the up time.
 */
internal fun AppearanceDevice.swipe(x: Int, y0: Int, y1: Int, steps: Int = 6, holdMs: Long = 0): Long {
    val t0 = SystemClock.uptimeMillis()
    inject(t0, MotionEvent.ACTION_DOWN, x, y0, t0)
    for (i in 1..steps) {
        sleepUntil(t0 + i * 16L)
        inject(t0, MotionEvent.ACTION_MOVE, x, y0 + (y1 - y0) * i / steps, t0 + i * 16L)
    }
    val moved = SystemClock.uptimeMillis() - t0
    if (holdMs > 0) Thread.sleep(holdMs)
    val at = if (holdMs > 0) SystemClock.uptimeMillis() else t0 + (steps + 1) * 16L
    return lift(t0, x, y1, at).also { log("swipe ${y1 - y0} px: moves done in $moved ms (planned ${steps * 16} ms), up ${it - t0} ms after the down") }
}

/** The background at rest in [r]: the last of three frames 400 ms apart that agree pixel for pixel. */
internal fun AppearanceDevice.restFrame(r: Rect, what: String): Bitmap {
    var last = screenshot()
    var same = 0
    val until = SystemClock.uptimeMillis() + 15_000
    var tries = 0
    while (true) {
        Thread.sleep(400)
        val next = screenshot()
        val dd = diff(last, next, r)
        tries++
        same = if (dd.changed == 0) same + 1 else 0
        if (same >= 2) {
            log("$what: at rest after $tries pairs of frames")
            return next
        }
        check(SystemClock.uptimeMillis() < until) { "$what: the background never came to rest in $r; the last two frames: $dd" }
        last = next
    }
}

/** Frames of [band] from [since] for [ms], as fast as screenshots come, each against [rest]: (ms after [since] the frame was asked, its diff). */
internal fun AppearanceDevice.series(rest: Bitmap, band: Rect, since: Long, ms: Long): List<Pair<Long, Diff>> = buildList {
    while (SystemClock.uptimeMillis() < since + ms) {
        val at = SystemClock.uptimeMillis() - since
        add(at to diff(rest, screenshot(), band))
    }
}

/** The frame of [frames] where the colours moved most (by [Diff.mean]); logged as [what]. */
internal fun AppearanceDevice.peakOf(frames: List<Pair<Long, Diff>>, what: String): Pair<Long, Diff> =
    frames.maxByOrNull { it.second.mean }!!.also { log("$what: ${frames.size} frames, the peak ${it.first} ms after: ${it.second}") }

/**
 * Frames of [band] from [since] until one is [rest] again (0 changed pixels) or [windowMs] + [DeviceSlackMs] + 3 s have passed.
 * Returns (ms after [since] of the first frame at rest — or of the last one tried, whether it came within [windowMs] + [DeviceSlackMs]).
 */
internal fun AppearanceDevice.awaitBack(rest: Bitmap, band: Rect, since: Long, windowMs: Long, what: String): Pair<Long, Boolean> {
    val limit = windowMs + DeviceSlackMs
    var last: Diff? = null
    while (true) {
        val at = SystemClock.uptimeMillis() - since
        val now = diff(rest, screenshot(), band)
        if (now.changed == 0) return (at to (at <= limit)).also { log("$what: back at rest $at ms after (window $limit ms)") }
        last = now
        if (at > limit + 3_000) return (at to false).also { log("$what: NOT at rest $at ms after (window $limit ms): $last") }
        Thread.sleep(100)
    }
}

internal fun sleepUntil(t: Long) {
    while (SystemClock.uptimeMillis() < t) Thread.sleep(5)
}
