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
        val ctx: Context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), androidx.appcompat.R.style.Theme_AppCompat)
        view = TesseraEditText(ctx, style)
        view.layoutParams = ViewGroup.LayoutParams(width, height)
        view.onMarkdownChange = { emitted += it }
        view.onFormatStateChanged = { states += it }
        view.setMarkdown(md)
        layout()
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
