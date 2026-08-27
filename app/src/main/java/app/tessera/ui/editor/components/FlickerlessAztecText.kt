package app.tessera.ui.editor.components

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.wordpress.aztec.AztecText

/**
 * AztecText subclass that suppresses the relayout flicker on task-list checkbox taps.
 *
 * Stock Aztec calls `refreshText(false)` from `onTouchEvent` after every task-list checkbox
 * tap. That implementation does `setText(getEditableText())`, which forces a full
 * `TextView` layout rebuild (`requestLayout()` + `invalidate()`) even though the editable
 * has not changed. Hosted inside Compose's `AndroidView`, that propagates as a visible
 * one-frame jump — what the user perceives as line-height flicker.
 *
 * The checkbox itself is drawn via the `LeadingMarginSpan2` drawable in
 * `AztecTaskListSpan.drawLeadingMargin` — its state-list drawable's `setState(...)`
 * already calls `invalidateSelf()`, which propagates to the view. So an `invalidate()`
 * here is sufficient to re-paint the new checked state without rebuilding the layout.
 *
 * The `refreshText(true)` path is left untouched — it's only used for the full no-arg
 * refresh (e.g. after async image/video loads) where the relayout is genuinely needed.
 */
class FlickerlessAztecText : AztecText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    /**
     * Notified after every lightweight `refreshText(false)` — the path Aztec uses for
     * task-list checkbox taps. Because we no longer do `setText(editable)` here, the
     * regular `TextWatcher.afterTextChanged` does not fire for these toggles. Hosting
     * code should listen via this hook to persist the new state.
     */
    var onLightweightRefresh: (() -> Unit)? = null

    /**
     * Notified on every caret/selection move. Aztec recomputes the active block/inline
     * formats per selection, but Compose has no way to observe that — without this hook the
     * toolbar's active-format highlight only refreshes on an unrelated recomposition, so a
     * heading button stays lit long after the caret left the heading. Hosting code listens
     * here to re-read [contains] and update the toolbar state live.
     */
    var onCaretSelectionChanged: ((Int, Int) -> Unit)? = null

    /**
     * Notified when an image reaches the editor via the IME rich-content path
     * ([InputConnectionCompat.createWrapper]) or an image clipboard paste. Hosting code
     * routes the content [Uri] through the same insert pipeline the picker/camera use and
     * returns `true` when it will handle it (so the platform doesn't fall back to a text paste).
     */
    var onImageCommitted: ((Uri) -> Boolean)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCaretSelectionChanged?.invoke(selStart, selEnd)
    }

    /**
     * Advertise image MIME support to the keyboard and wrap the connection so GBoard-style
     * rich-content commits (e.g. pasted/inserted images, GIFs) are delivered to [onImageCommitted].
     * Some keyboards only grant a transient read permission, so request it before reading the URI
     * and release it after — otherwise reading the content later throws [SecurityException].
     */
    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val connection = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))
        val callback = InputConnectionCompat.OnCommitContentListener { info: InputContentInfoCompat, flags: Int, _ ->
            val handler = onImageCommitted ?: return@OnCommitContentListener false
            if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                try {
                    info.requestPermission()
                } catch (_: Exception) {
                    return@OnCommitContentListener false
                }
            }
            val handled = handler(info.contentUri)
            if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                runCatching { info.releasePermission() }
            }
            handled
        }
        return InputConnectionCompat.createWrapper(connection, editorInfo, callback)
    }

    /**
     * Intercept a paste whose clipboard primary clip carries an image URI and route it to
     * [onImageCommitted] instead of Android's default (text-only) paste, which would drop it.
     */
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val handler = onImageCommitted
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (handler != null && clip != null && clip.itemCount > 0) {
                val uri = clip.getItemAt(0).uri
                val mime = uri?.let { context.contentResolver.getType(it) }
                if (uri != null && mime != null && mime.startsWith("image/")) {
                    if (handler(uri)) return true
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }

    override fun refreshText(stealEditorFocus: Boolean) {
        if (stealEditorFocus) {
            super.refreshText(stealEditorFocus)
        } else {
            invalidate()
            onLightweightRefresh?.invoke()
        }
    }

    /**
     * Force Aztec's full text-layout rebuild (`setText(editableText)`), which re-measures every
     * line. Needed after a BLOCK-level format change (heading/list/quote/code/paragraph) where the
     * line height changes: the lightweight invalidate-only [refreshText] above leaves the lines
     * below at stale Y positions, so they render overlapping. Aztec's path here preserves the
     * selection and does NOT steal focus or fire the TextWatcher (stealEditorFocus = false), so it
     * is safe to call straight after a toolbar toggle. Inline toggles (bold/italic/…) don't change
     * line metrics and keep using the lightweight refresh, so checkbox taps stay flicker-free.
     */
    fun rebuildLayout() = super.refreshText(false)
}
