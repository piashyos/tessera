package app.tessera.ui.editor.engine

import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** A [TesseraEditText] in a Robolectric context, measured and laid out, plus what the host tests need to drive it. */
internal class HostFixture(md: String, val width: Int = 400, val height: Int = 800, style: EditorStyle = EngineTestStyle.create()) {
    val view: TesseraEditText
    val emitted = ArrayList<String>()
    val states = ArrayList<FormatState>()

    init {
        releaseNativesSometimes()
        val ctx: Context = themedContext()
        view = TesseraEditText(ctx, style)
        view.layoutParams = ViewGroup.LayoutParams(width, height)
        view.onMarkdownChange = { emitted += it }
        view.onFormatStateChanged = { states += it }
        view.setMarkdown(md)
        layout()
    }

    companion object {
        private var created = 0

        /**
         * Robolectric's `ShadowLineBreaker` keeps the result of every line-break computation in a static registry for ever (one per reflow: 400 000
         * of them were ~60 MB after 8 000 sessions, and a long fuzz run ran out of memory). A result is read at once by the `StaticLayout` that asked
         * for it, so the registry is emptied between fixtures; objects of the fixture in use are not touched while it is being laid out.
         */
        fun releaseNativesSometimes() {
            if (++created % 10 != 0) return
            releaseNatives()
        }

        /** Also called by a long walk inside one fixture ([EngineFuzz.Session.check]): a reflow of a paragraph of 11 000 characters leaves ~20 KB. */
        fun releaseNatives() {
            runCatching {
                val registry = Class.forName("org.robolectric.shadows.ShadowLineBreaker").getDeclaredField("nativeObjectRegistry").apply { isAccessible = true }.get(null)
                registry.javaClass.getMethod("clear").invoke(registry)
            }
        }

        private var app: android.app.Application? = null
        private var themed: Context? = null

        /** One themed context per application: a new one for each of thousands of fixtures kept ~0.35 MB each (a theme with its resources) in a long fuzz run. */
        fun themedContext(): Context {
            val a = RuntimeEnvironment.getApplication()
            if (app !== a) { app = a; themed = ContextThemeWrapper(a, androidx.appcompat.R.style.Theme_AppCompat) }
            return themed!!
        }
    }

    fun layout() {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    val text get() = view.text!!
    fun md() = view.markdown()
    fun at(needle: String, shift: Int = 0) = text.toString().indexOf(needle) + shift
    fun caret(p: Int) = view.setSelection(p)
    fun select(a: Int, b: Int) = view.setSelection(a, b)

    fun ic(): InputConnection = view.onCreateInputConnection(EditorInfo())!!

    fun key(code: Int, meta: Int = 0): Boolean = view.onKeyDown(code, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
    fun shortcut(code: Int, meta: Int): Boolean = view.onKeyShortcut(code, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))

    /** Time passes on the main looper (the debounce of the emit, the pause of a typing run). */
    fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
}

/** A clip from another app: plain text on the system clipboard. */
internal fun TesseraEditText.setClip(text: String) {
    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText(null, text))
}
