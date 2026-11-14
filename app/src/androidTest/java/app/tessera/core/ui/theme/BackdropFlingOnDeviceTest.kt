package app.tessera.core.ui.theme

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The glide after a swipe on the device's real input (spec 2026-11-13-appearance-screen-and-motion §3.3: "the blob's initial velocity
 * is the gesture's velocity, turned and scaled"), read straight from [BackdropMotionState] — no pixels. A [GrainOverlay] of [SideDp]
 * in a bare activity takes a quick vertical swipe injected through `UiAutomation` (6 moves stamped 16 ms apart, up a frame later — the
 * touchscreen's clock, however late the slow app takes them); the end of blob A's glide gives the velocity the overlay used:
 * v = (end − start) × 4.2 × [FlingFriction] / gain A — against the swipe's own velocity. The start is read while the up is handled
 * (a parent's Final pass, after the overlay's `onFling` froze the drag): a poll from the test thread lands a slow frame later, inside
 * the glide.
 *
 * Twice: with the overlay's cheap frames, and with every frame of the blobs costing [SlowFrameMs] on the main thread — as the whole app
 * draws on emulator-5556 (`dumpsys gfxinfo` p50 53–85 ms while the blobs move): the glide must not depend on the frame rate. The swipe's
 * travel keeps the glide's end inside the limit circle (no cut). Measured 117 % of the swipe's mean velocity on both, three runs out of
 * three (the tracker gives the moves' own 13.3 px per 16 ms; the mean also counts the up's frame); ≥ 50 % is the check.
 */
@RunWith(AndroidJUnit4::class)
class BackdropFlingOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<ComponentActivity>? = null
    private lateinit var motion: BackdropMotionState
    @Volatile private var atUp = Float.NaN

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun theGlideTakesTheSwipesVelocity_cheapFrames() = glide(slowFrameMs = 0)

    @Test
    fun theGlideTakesTheSwipesVelocity_framesOf70ms() = glide(slowFrameMs = SlowFrameMs)

    private fun glide(slowFrameMs: Long) {
        scenario = ActivityScenario.launch(ComponentActivity::class.java).also { s ->
            s.onActivity { a ->
                a.setContent {
                    motion = rememberBackdropMotionState()
                    TesseraTheme(darkTheme = false, dynamicColor = false) {
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val e = awaitPointerEvent(PointerEventPass.Final)
                                        if (e.changes.any { it.changedToUpIgnoreConsumed() }) atUp = motion.offset(0).y
                                    }
                                }
                            },
                        ) {
                            GrainOverlay(
                                enabled = false, intensity = 0f, gradientEnabled = true, motion = motion,
                                modifier = Modifier.align(Alignment.Center).size(SideDp.dp),
                            ) {
                                // a heavy screen: each frame of the moving blobs costs slowFrameMs on the main thread
                                if (slowFrameMs > 0) Box(Modifier.fillMaxSize().drawBehind { motion.offset(0); Thread.sleep(slowFrameMs) })
                            }
                        }
                    }
                }
            }
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(800)
        var cx = 0
        var cy = 0
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            val loc = IntArray(2).also(v::getLocationOnScreen)
            cx = loc[0] + v.width / 2
            cy = loc[1] + v.height / 2
        }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val side = SideDp * density
        val limit = BackdropLimitFraction * side
        val travel = (side * 0.2f).toInt() // A: 0.2 × travel, softly limited, + the glide stays inside the limit
        val y0 = cy - travel / 2

        val frames = mutableListOf<Long>()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(t: Long) {
                frames += t / 1_000_000
                if (frames.size < 400) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback(callback) }

        val ua = instrumentation.uiAutomation
        // Stamped with the planned times, as a touchscreen does: the injection waits for the app, so on slow frames it falls behind.
        fun inject(down: Long, at: Long, action: Int, y: Int) {
            val ev = MotionEvent.obtain(down, at, action, cx.toFloat(), y.toFloat(), 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            ua.injectInputEvent(ev, true)
            ev.recycle()
        }
        val t0 = SystemClock.uptimeMillis()
        inject(t0, t0, MotionEvent.ACTION_DOWN, y0)
        for (i in 1..6) {
            while (SystemClock.uptimeMillis() < t0 + i * 16L) Thread.sleep(1)
            inject(t0, t0 + i * 16L, MotionEvent.ACTION_MOVE, y0 + travel * i / 6)
        }
        inject(t0, t0 + 7 * 16L, MotionEvent.ACTION_UP, y0 + travel)
        val up = SystemClock.uptimeMillis()
        val swipeVelocity = travel * 1000f / (7 * 16L)

        var end = 0f
        while (SystemClock.uptimeMillis() < up + 1_500) {
            var a = 0f
            instrumentation.runOnMainSync { a = motion.offset(0).y }
            end = maxOf(end, a)
            Thread.sleep(8)
        }
        val start = atUp
        assertTrue("the up reached the overlay", !start.isNaN())
        val used = (end - start) * 4.2f * FlingFriction / BackdropGains[0]
        val gaps = frames.zipWithNext { p, n -> n - p }.filter { it > 0 }.sorted()
        val p50 = gaps.getOrElse(gaps.size / 2) { 0L }
        Log.i(Tag, "frames ${slowFrameMs}ms extra: side %.0f px, limit %.0f px, travel $travel px in ${7 * 16} ms planned (injected in ${up - t0} ms) = %.0f px/s; blob A at the up %.1f px, the glide's end %.1f px → the velocity used %.0f px/s (%.0f %% of the swipe's); frame gaps p50 $p50 ms, max ${gaps.lastOrNull()} ms"
            .format(side, limit, swipeVelocity, start, end, used, used / swipeVelocity * 100))
        assertTrue("the glide's end stays inside the limit circle (no cut): %.1f of %.1f px".format(end, limit), end < limit - 0.5f)
        assertTrue(
            "the glide takes the swipe's velocity: %.0f px/s used of %.0f px/s (≥ 50 %%), frames +$slowFrameMs ms (p50 gap $p50 ms)"
                .format(used, swipeVelocity),
            abs(used) >= swipeVelocity * 0.5f,
        )
    }

    private companion object {
        const val SideDp = 200
        const val SlowFrameMs = 70L
        const val Tag = "BackdropFling"
    }
}
