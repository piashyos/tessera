package app.tessera.ui.editor.engine

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.data.images.ImageDownloader
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.ui.editor.components.NoteObjectImageSource
import app.tessera.ui.editor.screen.ImageFixtures
import app.tessera.ui.editor.screen.shownImages
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2 spec §7 (perf): a note with 20 pictures as wide as the screen. Opening = what the ViewModel does before the note reaches the
 * screen (`prefetchSizes`: a Room row and the bounds of a file per picture) plus `setMarkdown` up to the first layout; 5 runs, each a cold
 * decoder (a new process knows no size), the median is checked against 300 ms (stage 1: 144 ms for 1000 paragraphs). Then the pictures
 * arrive: the frames drawn meanwhile, the layout height before and after (no jump). A scroll through the note: memory before and after, no
 * OOM. A key with the pictures and without. `adb logcat -s NativeImagePerf` has every number. No `uiAutomation` here: it would keep
 * accessibility on and skew the timings.
 */
@HiltAndroidTest
class NativeImagePerfTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var imageDao: NoteImageDao
    @Inject lateinit var db: NotesDatabase

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ids = (1..20).map { "qa-perf-$it" }

    @Before
    fun setUp() {
        hilt.inject()
        db.clearAllTables()
    }

    @After
    fun clean() = ImageFixtures.removeApp(ctx, ids)

    private fun ms(ns: Long) = ns / 1_000_000.0
    private fun median(xs: List<Double>) = xs.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
    private fun fmt(xs: List<Double>) = xs.joinToString(", ") { "%.1f".format(it) }
    private fun log(msg: String) = Log.i(TAG, msg)

    private fun memory(label: String): String {
        Runtime.getRuntime().gc()
        Thread.sleep(200)
        val r = Runtime.getRuntime()
        return "$label: java used %.1f MB of max %.0f MB, native allocated %.1f MB".format(
            (r.totalMemory() - r.freeMemory()) / 1e6, r.maxMemory() / 1e6, Debug.getNativeHeapAllocatedSize() / 1e6,
        ).also(::log)
    }

    /** Frame durations of the window while [block] runs (FrameMetrics TOTAL_DURATION, ms). */
    private fun <T> frames(window: Window, block: () -> T): Pair<T, List<Double>> {
        val out = CopyOnWriteArrayList<Double>()
        val thread = HandlerThread("frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, m, _ -> out += m.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6 }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
        try {
            val r = block()
            Thread.sleep(300)
            return r to out.toList()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }

    private fun frameLine(fs: List<Double>) = "${fs.size} frames, over 16.7 ms: ${fs.count { it > 16.7 }}, over 33 ms: ${fs.count { it > 33.4 }}, max %.1f ms, median %.1f ms".format(fs.maxOrNull() ?: 0.0, if (fs.isEmpty()) 0.0 else median(fs))

    @Test
    fun aNoteWith20ScreenWidePicturesOpensFastAndScrollsWithoutOom() {
        // a phone photo as the app stores it: the compressor caps the long side at 2048
        for (id in ids) {
            val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, id), 2048, 1536)
            runBlocking { imageDao.upsert(ImageFixtures.row(id, f, 2048, 1536)) }
        }
        val md = "# Trip\n" + ids.mapIndexed { i, id -> "Day ${i + 1}: some text about the place, long enough to wrap onto a second line of a phone.\n![](tessera-img://$id)" }.joinToString("\n") + "\nthe end"
        val noDrive = ImageDownloader { null }
        val scopes = ArrayList<CoroutineScope>()
        DeviceEditor("").use { e ->
            memory("before")
            val prefetch = ArrayList<Double>()
            val toLayout = ArrayList<Double>()
            val total = ArrayList<Double>()
            val toFrame = ArrayList<Double>()
            val toAll = ArrayList<Double>()
            var current: TesseraEditText = e.view
            repeat(RUNS) { run ->
                val decoder = NoteImageDecoder(imageDao, ctx.filesDir, ctx.cacheDir, noDrive)
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scopes += it }
                val source = NoteObjectImageSource(decoder, scope)
                // what the ViewModel does before the note reaches the state, on IO
                val t0 = System.nanoTime()
                runBlocking(Dispatchers.IO) { source.prefetchSizes(NoteMarkdown.parse(md).blocks.filterIsInstance<NoteBlock.Image>().map { it.src }) }
                val t1 = System.nanoTime()
                val laidOut = CountDownLatch(1)
                val drawn = CountDownLatch(1)
                var tLayout = 0L
                var tFrame = 0L
                var heightAtLayout = 0
                val (t2, loadFrames) = frames(e.activity.window) {
                    val t2 = e.ui {
                        e.container.removeView(current)
                        val v = e.newEditor(null)
                        val t = System.nanoTime()
                        v.imageSource = source
                        v.setMarkdown(md)
                        e.container.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        v.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                if (v.layout == null) return
                                tLayout = System.nanoTime()
                                heightAtLayout = v.layout.height
                                v.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                laidOut.countDown()
                            }
                        })
                        v.viewTreeObserver.registerFrameCommitCallback { tFrame = System.nanoTime(); drawn.countDown() }
                        current = v
                        t
                    }
                    check(laidOut.await(10, TimeUnit.SECONDS)) { "no layout" }
                    check(drawn.await(10, TimeUnit.SECONDS)) { "no frame" }
                    val until = System.currentTimeMillis() + 20_000
                    while (e.ui { shownImages(current).count { it.loaded } } < ids.size && System.currentTimeMillis() < until) Thread.sleep(10)
                    toAll += ms(System.nanoTime() - t2)
                    t2
                }
                log("run ${run + 1}: while the pictures came: ${frameLine(loadFrames)}")
                prefetch += ms(t1 - t0)
                toLayout += ms(tLayout - t2)
                total += ms(tLayout - t0)
                toFrame += ms(tFrame - t0)
                val shown = e.ui { shownImages(current) }
                val heightAfter = e.ui { current.layout.height }
                log("run ${run + 1}: prefetch %.1f ms, setMarkdown -> first layout %.1f ms, open total %.1f ms, first frame %.1f ms, all 20 pictures %.1f ms; layout height %d -> %d; loaded %d, picture %s".format(
                    prefetch.last(), toLayout.last(), total.last(), toFrame.last(), toAll.last(), heightAtLayout, heightAfter, shown.count { it.loaded }, shown.first().let { "${it.pictureW}x${it.pictureH}" },
                ))
                assertEquals("run ${run + 1}: every picture came", ids.size, shown.count { it.loaded })
                assertEquals("run ${run + 1}: the note did not change its height when the pictures came", heightAtLayout, heightAfter)
            }
            log("open, $RUNS runs: prefetch [${fmt(prefetch)}] median %.1f ms".format(median(prefetch)))
            log("open, $RUNS runs: setMarkdown -> first layout [${fmt(toLayout)}] median %.1f ms".format(median(toLayout)))
            log("open, $RUNS runs: prefetch + first layout [${fmt(total)}] median %.1f ms".format(median(total)))
            log("open, $RUNS runs: to the first frame [${fmt(toFrame)}] median %.1f ms".format(median(toFrame)))
            log("open, $RUNS runs: until all 20 pictures [${fmt(toAll)}] median %.1f ms".format(median(toAll)))
            memory("after $RUNS opens, the last one shown")

            // a scroll through the 20 pictures, top to bottom and back
            val v = current
            val (_, scrollFrames) = frames(e.activity.window) {
                val max = e.ui { v.layout.height + v.totalPaddingTop + v.totalPaddingBottom - v.height }
                for (y in (0..max step 120) + (max downTo 0 step 120)) {
                    e.ui { v.scrollTo(0, y) }
                    Thread.sleep(16)
                }
                e.idle()
            }
            log("scroll through 20 pictures: ${frameLine(scrollFrames)}")
            memory("after the scroll")

            // a key with the pictures and the same text without them
            fun keys(target: TesseraEditText): List<Double> {
                e.ui {
                    val t = target.text.toString()
                    target.setSelection(t.indexOf("Day 10:") + 4)
                }
                return (1..KEYS).map {
                    Thread.sleep(600) // each one starts a typing run (the history takes a snapshot)
                    e.ui {
                        val ic = target.onCreateInputConnection(EditorInfo())!!
                        val a = System.nanoTime()
                        ic.commitText("a", 1)
                        ms(System.nanoTime() - a)
                    }
                }
            }
            // the same text without the pictures first (a run that goes second is warmer), then the note with them; each also scrolled
            fun scroll(target: TesseraEditText): List<Double> = frames(e.activity.window) {
                val max = e.ui { target.layout.height + target.totalPaddingTop + target.totalPaddingBottom - target.height }
                for (y in (0..max step 120) + (max downTo 0 step 120)) {
                    e.ui { target.scrollTo(0, y) }
                    Thread.sleep(16)
                }
                e.idle()
            }.second
            val plain = e.ui {
                e.container.removeView(v)
                e.newEditor(md.replace(Regex("!\\[]\\(tessera-img://[^)]*\\)"), "a picture was here")).also {
                    e.container.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            e.idle()
            log("scroll through the same text without pictures: ${frameLine(scroll(plain))}")
            val withoutPictures = keys(plain)
            e.ui { e.container.removeView(plain); e.container.addView(v) }
            e.idle()
            val withPictures = keys(v)
            log("key, first of a run (x$KEYS), same text without pictures: [${fmt(withoutPictures)}] median %.1f ms, max %.1f ms".format(median(withoutPictures), withoutPictures.max()))
            log("key, first of a run (x$KEYS), 20 pictures: [${fmt(withPictures)}] median %.1f ms, max %.1f ms".format(median(withPictures), withPictures.max()))
            scopes.forEach { it.cancel() }

            assertTrue("a note with 20 pictures opens (prefetch + first layout) in ≤ 300 ms: median %.1f ms of [${fmt(total)}]".format(median(total)), median(total) <= 300.0)
            // the plan's criterion (§7): typing is not slower than stage 1 (the first key after a pause, plan of phase 1); the cost the pictures add
            // over the same text without them is logged above (finding N6 of the QA)
            assertTrue("a key with 20 pictures is not slower than stage 1 (%.1f ms): %.1f ms (without pictures %.1f ms)".format(STAGE1_FIRST_KEY_MS, median(withPictures), median(withoutPictures)), median(withPictures) <= STAGE1_FIRST_KEY_MS)
        }
    }

    /**
     * The decoder of API 28+ (`ImageDecoder`, 78a9868) against the decode it replaced (`BitmapFactory` with the same sample, then the same
     * scale): the peak of the native heap while one phone photo (2048×1536) is decoded for the text width. 7 runs each, in turns.
     */
    @Test
    fun aPhotoDecodesWithNoMoreMemoryThanBitmapFactoryTook() {
        val root = java.io.File(ctx.cacheDir, "qa-perf-peak").apply { deleteRecursively() }
        val file = ImageFixtures.webp(java.io.File(root, "images/qa-peak.webp"), 2048, 1536)
        val decoder = NoteImageDecoder(imageDao, root, root, ImageDownloader { null })
        val ref = app.tessera.domain.model.parseImageRef("tessera-img://qa-peak")!!
        val width = 704
        fun before(): android.graphics.Bitmap {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { android.graphics.BitmapFactory.decodeFile(file.absolutePath, it) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width) sample *= 2
            val b = android.graphics.BitmapFactory.decodeFile(file.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })!!
            val w = minOf(b.width, width)
            return android.graphics.Bitmap.createScaledBitmap(b, w, Math.round(w * b.height.toFloat() / b.width), true).also { if (it !== b) b.recycle() }
        }
        /** Peak of the native heap over [block], MB above where it started; and the time. */
        fun peak(block: () -> android.graphics.Bitmap): Pair<Double, Double> {
            Runtime.getRuntime().gc()
            Thread.sleep(300)
            val base = Debug.getNativeHeapAllocatedSize()
            val max = java.util.concurrent.atomic.AtomicLong(base)
            val running = java.util.concurrent.atomic.AtomicBoolean(true)
            val sampler = Thread { while (running.get()) { max.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf); Thread.sleep(1) } }.apply { start() }
            val t0 = System.nanoTime()
            val b = block()
            val t = ms(System.nanoTime() - t0)
            running.set(false)
            sampler.join()
            max.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
            check(b.width == width) { "decoded ${b.width}x${b.height}" }
            b.recycle()
            return (max.get() - base) / 1e6 to t
        }
        val now = ArrayList<Pair<Double, Double>>()
        val old = ArrayList<Pair<Double, Double>>()
        repeat(7) {
            now += peak { decoder.fileDecoder(file, width, null, ref)!! }
            old += peak { before() }
        }
        log("decode 2048x1536 for $width px, ImageDecoder: peak [${fmt(now.map { it.first })}] MB median %.1f MB, time median %.1f ms".format(median(now.map { it.first }), median(now.map { it.second })))
        log("decode 2048x1536 for $width px, BitmapFactory: peak [${fmt(old.map { it.first })}] MB median %.1f MB, time median %.1f ms".format(median(old.map { it.first }), median(old.map { it.second })))
        root.deleteRecursively()
        assertTrue(
            "the peak of a decode did not grow: ImageDecoder %.1f MB vs BitmapFactory %.1f MB".format(median(now.map { it.first }), median(old.map { it.first })),
            median(now.map { it.first }) <= median(old.map { it.first }) + 0.5,
        )
    }

    private companion object {
        const val TAG = "NativeImagePerf"
        const val RUNS = 5
        const val KEYS = 20

        /** Stage 1, the first key after a pause in a note of 1000 paragraphs (plan of phase 1, perf). */
        const val STAGE1_FIRST_KEY_MS = 30.5
    }
}
