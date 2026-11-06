package app.tessera.ui.editor.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Real-looking sizes (Inter 15sp body, as the editor) and colors that are easy to find in pixels. */
internal object DeviceStyle {
    const val TEXT = 0xFF111111.toInt()
    const val MUTED = 0xFF777777.toInt()
    const val ACCENT = 0xFFCC3300.toInt()
    const val TICK = 0xFFFFFFFF.toInt()

    fun create(ctx: Context): EditorStyle {
        val dm = ctx.resources.displayMetrics
        fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)
        fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, dm)
        return EditorStyle(
            textColor = TEXT,
            mutedTextColor = MUTED,
            bodyTextSizePx = sp(15f),
            titleTextSizePx = sp(26f),
            headingTextSizePx = sp(20f),
            subheadingTextSizePx = sp(18f),
            accentColor = ACCENT,
            checkboxTickColor = TICK,
            bulletRadiusPx = dp(3f),
            checkboxSizePx = dp(18f),
            checkboxCornerPx = dp(4f),
            checkboxStrokePx = dp(2f),
            listMarkerAreaPx = dp(28f),
            listLevelStepPx = dp(24f),
            quoteBarColor = 0xFF00AA00.toInt(),
            quoteTextColor = 0xFF333333.toInt(),
            quoteFillColor = 0xFFCCFFCC.toInt(),
            quoteBarWidthPx = dp(3f),
            quoteGapPx = dp(12f),
            monoBackgroundColor = 0xFFDDDDFF.toInt(),
            monoTextColor = 0xFF222222.toInt(),
            monoCornerPx = dp(8f),
            monoPaddingHorizontalPx = dp(12f),
            monoPaddingVerticalPx = dp(8f),
            // the block spacing of spec §6.11
            titleSpaceTopPx = dp(12f),
            titleSpaceBottomPx = dp(8f),
            headingSpaceTopPx = dp(12f),
            headingSpaceBottomPx = dp(6f),
            blockEdgePx = dp(10f),
            listItemPaddingPx = dp(3f),
            quotePaddingVerticalPx = dp(4f),
            codeBackgroundColor = 0xFFEEEEEE.toInt(),
            dividerColor = 0xFF0000FF.toInt(),
            dividerThicknessPx = dp(1f),
            placeholderBorderColor = 0xFF999999.toInt(),
            placeholderTextColor = MUTED,
            placeholderCornerPx = dp(8f),
            placeholderPaddingPx = dp(8f),
            imageLabel = "Изображение",
            tableLabel = "Таблица",
        )
    }
}

/**
 * A [TesseraEditText] in a real, hardware-accelerated window of a [ComponentActivity] on the device. The editor is driven the way a
 * keyboard drives it — through the [InputConnection] the view hands out — and read the way a user sees it: the document, the layout,
 * the pixels of the window. No focus by default: a real keyboard attached to the view would send its own calls between ours.
 */
internal class DeviceEditor(md: String, private val focus: Boolean = false) : AutoCloseable {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val scenario = ActivityScenario.launch(ComponentActivity::class.java)
    lateinit var activity: ComponentActivity
    lateinit var container: FrameLayout
    lateinit var view: TesseraEditText
    lateinit var style: EditorStyle
    val emitted = CopyOnWriteArrayList<String>()

    init {
        scenario.onActivity { a ->
            activity = a
            a.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            // the container takes the initial focus, so the editor (focusable in touch mode) does not get it by itself
            // inside the system bars (the window is edge-to-edge: a tap under the status bar would go to System UI)
            container = FrameLayout(a).apply { setBackgroundColor(Color.WHITE); isFocusableInTouchMode = true; fitsSystemWindows = true }
            a.setContentView(container)
            style = DeviceStyle.create(a)
            view = newEditor(md)
            view.onMarkdownChange = { emitted += it }
            container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            if (focus) view.requestFocus() else container.requestFocus()
        }
        idle()
    }

    /** A new editor, with [md] loaded when given, as `NoteBody` makes it (not attached). Main thread only. */
    fun newEditor(md: String?): TesseraEditText = TesseraEditText(ContextThemeWrapper(activity, androidx.appcompat.R.style.Theme_AppCompat_Light), style).apply {
        isVerticalScrollBarEnabled = false
        if (md != null) setMarkdown(md)
    }

    fun <T> ui(block: () -> T): T {
        var r: Result<T>? = null
        instrumentation.runOnMainSync { r = runCatching(block) }
        return r!!.getOrThrow()
    }

    fun idle() = instrumentation.waitForIdleSync()

    /** What a keyboard gets when it binds to the editor. */
    fun ic(): InputConnection = ui { view.onCreateInputConnection(EditorInfo()) ?: error("no input connection") }

    /** Runs IME calls on the main thread, as the framework delivers them, then lets the frame settle. */
    fun ime(block: InputConnection.() -> Unit) {
        ui { view.onCreateInputConnection(EditorInfo())!!.block() }
        idle()
    }

    fun text(): String = ui { view.text.toString() }
    fun md(): String = ui { view.markdown() }
    fun blocks(): List<NoteBlock> = ui { DocBinder.read(view.text!!).blocks }
    fun kinds(): List<ParagraphKind> = blocks().map { (it as? NoteBlock.Text)?.kind ?: error("object paragraph: $it") }
    fun caret(p: Int) = ui { view.setSelection(p) }
    fun select(a: Int, b: Int) = ui { view.setSelection(a, b) }
    fun at(needle: String, shift: Int = 0): Int = text().indexOf(needle).also { check(it >= 0) { "\"$needle\" not in \"${text()}\"" } } + shift

    /** Past the 300 ms debounce of the emit: the last emitted Markdown, `null` when nothing went out. */
    fun awaitEmit(): String? {
        Thread.sleep(450)
        idle()
        return emitted.lastOrNull()
    }

    /** Lets the window draw what the views hold now (the main looper settles, then a few frames pass). */
    fun settleFrames() {
        idle()
        Thread.sleep(250)
        idle()
    }

    /** The pixels the window shows over [v] — the hardware pipeline, with the `RenderNode` caches the views keep. */
    fun capture(v: android.view.View = view): Bitmap {
        settleFrames()
        val rect = ui {
            val loc = IntArray(2)
            v.getLocationInWindow(loc)
            Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
        }
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val thread = HandlerThread("pixel-copy").apply { start() }
        try {
            val done = CountDownLatch(1)
            var result = -1
            PixelCopy.request(activity.window, rect, bitmap, { result = it; done.countDown() }, Handler(thread.looper))
            check(done.await(5, TimeUnit.SECONDS)) { "PixelCopy timed out" }
            check(result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
        } finally {
            thread.quitSafely()
        }
        return bitmap
    }

    override fun close() {
        ui { view.dispose() }
        scenario.close()
    }
}

/** Pixels of [b] inside [r] within [tolerance] per channel of [argb] (anti-aliased edges are near, not equal). */
internal fun countNear(b: Bitmap, r: Rect, argb: Int, tolerance: Int = 40): Int {
    var n = 0
    for (y in r.top.coerceAtLeast(0) until r.bottom.coerceAtMost(b.height)) {
        for (x in r.left.coerceAtLeast(0) until r.right.coerceAtMost(b.width)) {
            val p = b.getPixel(x, y)
            if (abs(Color.red(p) - Color.red(argb)) <= tolerance && abs(Color.green(p) - Color.green(argb)) <= tolerance &&
                abs(Color.blue(p) - Color.blue(argb)) <= tolerance
            ) n++
        }
    }
    return n
}

/** The bounding box of the pixels [countNear] counts; `null` when there are none. */
internal fun boundsNear(b: Bitmap, r: Rect, argb: Int, tolerance: Int = 40): Rect? {
    var box: Rect? = null
    for (y in r.top.coerceAtLeast(0) until r.bottom.coerceAtMost(b.height)) {
        for (x in r.left.coerceAtLeast(0) until r.right.coerceAtMost(b.width)) {
            if (countNear(b, Rect(x, y, x + 1, y + 1), argb, tolerance) == 1) box = (box ?: Rect(x, y, x + 1, y + 1)).apply { union(x, y) }
        }
    }
    return box
}

/** `null` when equal; otherwise how many pixels differ and their bounding box. */
internal fun pixelDiff(a: Bitmap, b: Bitmap): String? {
    if (a.width != b.width || a.height != b.height) return "size ${a.width}x${a.height} vs ${b.width}x${b.height}"
    val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
    val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    var n = 0
    var box: Rect? = null
    for (i in pa.indices) {
        if (pa[i] != pb[i]) {
            val x = i % a.width
            val y = i / a.width
            n++
            box = (box ?: Rect(x, y, x + 1, y + 1)).apply { union(x, y) }
        }
    }
    return if (n == 0) null else "$n pixels differ in $box"
}

/** Keeps [b] in Downloads/native-editor-qa (shared storage outlives the uninstall after the run) for `adb pull`. */
internal fun savePng(ctx: Context, name: String, b: Bitmap) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
        put(MediaStore.Downloads.MIME_TYPE, "image/png")
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/native-editor-qa")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
    ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
