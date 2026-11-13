package app.tessera.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.notes.AppearanceAppTest
import app.tessera.ui.notes.AppearanceDevice
import app.tessera.ui.notes.UiNode
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live background (spec 2026-11-13-animated-background §1, §3, §4) in the real app, with real touches and the device's real
 * keyboard, on the real clocks (no Compose rule: under one, Compose animations run only while the test's clock is advanced).
 *
 * Measured on pixels of the whole screen (`UiAutomation.takeScreenshot`) inside an area where only the background shows: on the empty
 * list, the band between the top bar and the "No notes yet" text; in the editor, the band between the last line and the format bar.
 * - At rest two frames 400 ms apart are equal pixel for pixel: the noise floor is 0 (grain is static; nothing animates).
 * - A drag shifts the blobs by up to 9 % of the short side (65 px here) against a radius of 0.7–0.85 of the width, so a smooth gradient
 *   moves by a level or two a channel: "changed" is any pixel that differs at all. Measured on emulator-5556 (720×1280), a held drag
 *   changes 89.7 % (light) / 92.7 % (dark) of the list's band and 71.4 % of the editor's, max step 2; rest against rest — 0 px. A held
 *   drag must change at least [MovedShare] = 5 %: 14× under the smallest measurement, any change at all is above the noise floor.
 *   The press pulse of a tap peaks 350–500 ms after the press: 70–77 % of the band, max step 3–4.
 * - Back at rest means the same frame as before the touch: 0 changed pixels (the springs end exactly on rest).
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
        prefs.setAmoled(false)
        prefs.setGrainSize(1)
    }

    private fun background(mode: ThemeMode, gradient: Boolean, grain: Boolean = true, amoled: Boolean = false) {
        theme(mode, grain = grain, gradient = gradient)
        runBlocking { prefs.setAmoled(amoled) }
        d.shell("mkdir -p $ShotDir")
        val scale = d.shell("settings get global animator_duration_scale").trim()
        d.log("animator_duration_scale = $scale")
        check(scale != "0" && scale != "0.0") { "animator_duration_scale is $scale: animations are off on the device, the blobs never move" }
    }

    // ------------------------------------------------------------------ the empty list

    @Test
    fun aDragOverTheEmptyListMovesTheBackgroundAndItIsBackAtRest2_5sAfterTheFingerIsUp_light() = dragMovesAndSettles(ThemeMode.LIGHT)

    @Test
    fun aDragOverTheEmptyListMovesTheBackgroundAndItIsBackAtRest2_5sAfterTheFingerIsUp_dark() = dragMovesAndSettles(ThemeMode.DARK)

    private fun dragMovesAndSettles(mode: ThemeMode) {
        val t = mode.name.lowercase()
        background(mode, gradient = true)
        d.launch()
        val band = emptyListBand()
        val rest = restFrame(band, "before the drag")
        shot("list-$t-rest")

        val x = d.screenWidth() * 2 / 3
        val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
        Thread.sleep(500) // held still: the blobs have followed the pull
        val held = d.screenshot()
        shot("list-$t-held")
        val up = lift(down, x, band.bottom - d.dp(8f))
        sleepUntil(up + 2_500)
        val after = d.screenshot()
        shot("list-$t-after-2_5s")

        val moved = diff(rest, held, band)
        val back = diff(rest, after, band)
        d.log("$t: band $band; held vs rest: $moved; 2.5 s after up vs rest: $back")
        assertTrue("$t: a held drag moves the background in the empty band $band — $moved", moved.share >= MovedShare)
        assertEquals("$t: 2.5 s after the finger is up the band is the frame at rest again — $back", 0, back.changed)
    }

    /**
     * The background's listener takes nothing from the screens (spec §4.1) — in the real app, with the gradient on: the list scrolls under a
     * drag, a card's swipe reveals its action, a tap opens the note. (`NotesListScreenTest`, `TesseraSwipeableNoteCardTest` and
     * `TesseraPopupSwipeTest` set their own content, without the background.)
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

    /** Every press pulses the blobs away from the finger (spec §4.1–§4.2): a tap moves the empty list's background, at rest 2.5 s later. */
    @Test
    fun aTapOnTheEmptyListPulsesTheBackgroundAndItIsBackAtRest2_5sAfterThePress() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        val band = emptyListBand()
        val rest = restFrame(band, "before the tap")
        val (x, y) = d.screenWidth() / 2 to band.centerY()
        val t0 = SystemClock.uptimeMillis()
        d.inject(t0, MotionEvent.ACTION_DOWN, x, y)
        sleepUntil(t0 + 60)
        d.inject(t0, MotionEvent.ACTION_UP, x, y)
        sleepUntil(t0 + 400)
        val peak = diff(rest, d.screenshot(), band)
        sleepUntil(t0 + 2_500)
        val back = diff(rest, d.screenshot(), band)
        d.log("tap: band $band; 400 ms after the press vs rest: $peak; 2.5 s after vs rest: $back")
        assertTrue("400 ms after a tap the background has moved — $peak", peak.share >= MovedShare)
        assertEquals("2.5 s after the press the band is the frame at rest again — $back", 0, back.changed)
    }

    @Test
    fun withTheGradientOffADragLeavesTheEmptyListStill() {
        background(ThemeMode.LIGHT, gradient = false)
        d.launch()
        val band = emptyListBand()
        val rest = restFrame(band, "before the drag")
        val x = d.screenWidth() * 2 / 3
        val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
        Thread.sleep(500)
        val held = d.screenshot()
        shot("list-light-gradient-off-held")
        val up = lift(down, x, band.bottom - d.dp(8f))
        sleepUntil(up + 500)
        val after = d.screenshot()

        val moved = diff(rest, held, band)
        val later = diff(rest, after, band)
        d.log("gradient off: band $band; held vs rest: $moved; after up vs rest: $later")
        assertEquals("gradient off: a held drag leaves the band as it was — $moved", 0, moved.changed)
        assertEquals("gradient off: after the drag the band is as it was — $later", 0, later.changed)
    }

    /** AMOLED + dark: true black, no gradient (spec §3); grain off so the band must be flat black. */
    @Test
    fun amoledDarkHasNoGradientAndADragLeavesItBlack() {
        background(ThemeMode.DARK, gradient = true, grain = false, amoled = true)
        d.launch()
        val band = emptyListBand()
        val rest = restFrame(band, "before the drag")
        shot("list-amoled-dark-rest")
        val notBlack = rest.count(band) { (it and 0xFFFFFF) != 0 }
        assertEquals("AMOLED dark: the empty band is pure black (no blobs)", 0, notBlack)

        val x = d.screenWidth() * 2 / 3
        val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
        Thread.sleep(500)
        val held = d.screenshot()
        lift(down, x, band.bottom - d.dp(8f))
        val moved = diff(rest, held, band)
        d.log("amoled dark: band $band; held vs rest: $moved")
        assertEquals("AMOLED dark: a held drag leaves the band black — $moved", 0, moved.changed)
    }

    /** System "Remove animations" (spec §4.2): the blobs must not jump to the finger — they stay at rest. */
    @Test
    fun withAnimationsRemovedADragLeavesTheBackgroundAtRest() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        val band = emptyListBand()
        val was = d.shell("settings get global animator_duration_scale").trim()
        try {
            d.shell("settings put global animator_duration_scale 0")
            d.settle(1_000)
            val rest = restFrame(band, "animations removed, before the drag")
            val x = d.screenWidth() * 2 / 3
            val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
            Thread.sleep(500)
            val held = d.screenshot()
            val up = lift(down, x, band.bottom - d.dp(8f))
            sleepUntil(up + 500)
            val after = d.screenshot()
            val moved = diff(rest, held, band)
            val later = diff(rest, after, band)
            d.log("animations removed: band $band; held vs rest: $moved; after up vs rest: $later")
            assertEquals("animations removed: a held drag leaves the background at rest — $moved", 0, moved.changed)
            assertEquals("animations removed: after the drag the background is at rest — $later", 0, later.changed)
        } finally {
            d.shell("settings put global animator_duration_scale ${was.ifEmpty { "1" }.let { if (it == "null") "1" else it }}")
        }
    }

    /**
     * Settings → Appearance (spec §6) in the real app: "Grain size" (and "Intensity") show only while grain is on; the size slider saves
     * 4 and 1 from its ends; the gradient's row turns the live background off for the whole app — a drag on the list no longer moves it.
     */
    @Test
    fun theSettingsRowsSaveTheGrainSizeShowItOnlyWithGrainAndTurnTheGradientOff() {
        background(ThemeMode.LIGHT, gradient = true)
        d.launch()
        openDrawer()
        d.tap(d.texts(s(R.string.settings_title)).first { d.onScreen(it) })
        d.await("the settings", 15_000) { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.settle(600)

        d.reveal(s(R.string.settings_gradient_title))
        assertTrue("grain on: \"Intensity\" is shown", d.texts(s(R.string.settings_grain_intensity)).any { d.onScreen(it) })
        val size = d.reveal(s(R.string.settings_grain_size))
        shot("settings-en-grain-on")
        // the slider right under its label: a tap at the ends of its track saves 4 and 1 (past the track's end a tap does nothing)
        val slider = d.nodes().filter { it.bounds.top in size.bounds.bottom..size.bounds.bottom + d.dp(16f) && it.bounds.width() > d.screenWidth() / 2 }
            .minByOrNull { it.bounds.height() } ?: error("no slider under \"Grain size\"")
        d.tap(slider.bounds.right - d.dp(14f), slider.bounds.centerY())
        d.awaitTrue("the slider's right end saves size 4") { runBlocking { prefs.grainSize.first() } == 4 }
        d.tap(slider.bounds.left + d.dp(14f), slider.bounds.centerY())
        d.awaitTrue("the slider's left end saves size 1") { runBlocking { prefs.grainSize.first() } == 1 }

        d.tap(d.reveal(s(R.string.settings_grain_title)))
        d.awaitTrue("grain off") { runBlocking { !prefs.grainEnabled.first() } }
        assertTrue("grain off: no \"Grain size\"", d.texts(s(R.string.settings_grain_size)).none { d.onScreen(it) })
        assertTrue("grain off: no \"Intensity\"", d.texts(s(R.string.settings_grain_intensity)).none { d.onScreen(it) })
        assertTrue("grain off: the gradient's row stays", d.texts(s(R.string.settings_gradient_title)).any { d.onScreen(it) })
        shot("settings-en-grain-off")
        d.tap(d.reveal(s(R.string.settings_grain_title)))
        d.awaitTrue("grain on again") { runBlocking { prefs.grainEnabled.first() } }
        d.reveal(s(R.string.settings_grain_size))

        d.tap(d.reveal(s(R.string.settings_gradient_title)))
        d.awaitTrue("the gradient off") { runBlocking { !prefs.gradientEnabled.first() } }
        d.back()
        val band = emptyListBand()
        val rest = restFrame(band, "the list after the gradient was turned off in Settings")
        val x = d.screenWidth() * 2 / 3
        val down = press(x, band.top + d.dp(8f), band.bottom - d.dp(8f))
        Thread.sleep(500)
        val held = d.screenshot()
        lift(down, x, band.bottom - d.dp(8f))
        val moved = diff(rest, held, band)
        d.log("gradient off in Settings: held vs rest: $moved")
        assertEquals("the gradient turned off in Settings: a held drag leaves the list's background still — $moved", 0, moved.changed)
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

        // a tap on the end of the last line: the caret, the keyboard (the tap itself is a touch and moves the background)
        val line = d.ui { lastLine(e) }
        d.tap(line.endX - d.dp(2f), line.mid)
        val imeTop = d.awaitIme()
        d.await("Gboard's letters", 15_000) { key("q") }
        Thread.sleep(3_000) // past the tap's "breath" and the keyboard's slide
        val textBottom = d.ui { lastLine(e) }.bottom
        val firstBelow = d.nodes().filter { d.onScreen(it) && it.bounds.top > textBottom && it.bounds.height() < d.screenHeight() / 2 }
            .minOfOrNull { it.bounds.top } ?: imeTop
        val band = Rect(d.dp(16f), textBottom + d.dp(32f), d.screenWidth() - d.dp(16f), minOf(firstBelow, imeTop) - d.dp(24f))
        check(band.height() >= d.dp(60f)) { "the editor's empty band is too small: $band (text bottom $textBottom, below $firstBelow, ime $imeTop)" }
        val rest = restFrame(band, "the editor with the keyboard up")
        shot("editor-light-keyboard-rest")

        var worst = Diff(0, band.width() * band.height(), 0)
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

    /** The finger of [press] up; returns the time it went up. */
    private fun lift(down: Long, x: Int, y: Int): Long {
        d.inject(down, MotionEvent.ACTION_UP, x, y)
        return SystemClock.uptimeMillis()
    }

    /** The background at rest in [r]: the second of two frames 400 ms apart that agree pixel for pixel. */
    private fun restFrame(r: Rect, what: String): Bitmap {
        var last = d.screenshot()
        val until = SystemClock.uptimeMillis() + 15_000
        var tries = 0
        while (true) {
            Thread.sleep(400)
            val next = d.screenshot()
            val dd = diff(last, next, r)
            tries++
            if (dd.changed == 0) {
                d.log("$what: at rest after $tries pairs of frames")
                return next
            }
            check(SystemClock.uptimeMillis() < until) { "$what: the background never came to rest in $r; the last two frames: $dd" }
            last = next
        }
    }

    private fun shot(name: String) = d.shell("screencap -p $ShotDir/$name.png")

    private fun Bitmap.count(r: Rect, pred: (Int) -> Boolean): Int {
        var n = 0
        for (y in r.top until r.bottom) for (x in r.left until r.right) if (pred(getPixel(x, y))) n++
        return n
    }

    private companion object {
        /** The share of the band a held drag must change (measured 71–93 %, noise floor 0; see the class doc). */
        const val MovedShare = 0.05
    }
}

/** Where the live background's QA shots go on the device (`adb pull`). */
internal const val ShotDir = "/data/local/tmp/animated-background-qa"

/** A scrolling screen (Settings) dragged by a finger until [text] is in view, clear of the top bar and the bottom edge. */
internal fun AppearanceDevice.reveal(text: String): UiNode {
    repeat(10) {
        val n = texts(text).firstOrNull { onScreen(it) }
        if (n != null && n.bounds.top >= dp(140f) && n.bounds.bottom <= screenHeight() - dp(120f)) return n
        if (n != null && n.bounds.top < dp(140f)) {
            drag(screenWidth() / 2, screenHeight() * 2 / 5, screenHeight() * 3 / 5) // above: back down a little
        } else {
            drag(screenWidth() / 2, screenHeight() * 3 / 4, screenHeight() * 2 / 5)
        }
    }
    error("\"$text\" is not scrolled into view; on screen: ${visibleTexts()}")
}

/** How [b] differs from [a] inside [r]: the pixels with any channel off, and the largest step of a channel. */
internal class Diff(val changed: Int, val total: Int, val maxStep: Int) {
    val share get() = changed.toDouble() / total
    override fun toString() = "%d of %d px changed (%.1f %%), max step %d".format(changed, total, share * 100, maxStep)
}

internal fun diff(a: Bitmap, b: Bitmap, r: Rect): Diff {
    val w = r.width()
    val h = r.height()
    val pa = IntArray(w * h).also { a.getPixels(it, 0, w, r.left, r.top, w, h) }
    val pb = IntArray(w * h).also { b.getPixels(it, 0, w, r.left, r.top, w, h) }
    var changed = 0
    var max = 0
    for (i in pa.indices) {
        val p = pa[i]
        val q = pb[i]
        if (p == q) continue
        changed++
        val step = maxOf(abs(Color.red(p) - Color.red(q)), abs(Color.green(p) - Color.green(q)), abs(Color.blue(p) - Color.blue(q)))
        if (step > max) max = step
    }
    return Diff(changed, w * h, max)
}

/** A touch event injected into the window, as a finger makes it. */
internal fun AppearanceDevice.inject(down: Long, action: Int, x: Int, y: Int) {
    val ev = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x.toFloat(), y.toFloat(), 0)
    ev.source = InputDevice.SOURCE_TOUCHSCREEN
    ua.injectInputEvent(ev, true)
    ev.recycle()
}

internal fun sleepUntil(t: Long) {
    while (SystemClock.uptimeMillis() < t) Thread.sleep(5)
}
