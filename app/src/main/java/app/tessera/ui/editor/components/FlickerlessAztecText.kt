package app.tessera.ui.editor.components

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.spans.AztecListItemSpan
import org.wordpress.aztec.spans.AztecListSpan
import org.wordpress.aztec.spans.AztecOrderedListSpan
import org.wordpress.aztec.spans.AztecTaskListSpan
import org.wordpress.aztec.spans.AztecUnorderedListSpan
import java.io.File
import java.util.UUID

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
            val needsGrant =
                flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
            if (needsGrant) {
                try {
                    info.requestPermission()
                } catch (_: Exception) {
                    return@OnCommitContentListener false
                }
            }
            // The insert pipeline reads the URI LATER (compression runs off the main thread), but a
            // keyboard's grant is often transient — so copy the bytes into an app-owned cache file
            // WHILE the grant is held, then hand the downstream a file-backed URI. The release is in
            // `finally` so it can never be skipped if the copy throws.
            val localUri: Uri? = try {
                copyToCache(info.contentUri)
            } finally {
                if (needsGrant) runCatching { info.releasePermission() }
            }
            if (localUri != null) handler(localUri) else false
        }
        val wrapped = InputConnectionCompat.createWrapper(connection, editorInfo, callback)
        return BackspaceInterceptingConnection(wrapped)
    }

    /**
     * The list container span (task / ordered / unordered) covering [offset], or null if that
     * position isn't inside a list. Detected via span CLASS so the app's custom
     * [TesseraBulletListSpan] / [TesseraOrderedListSpan] subclasses are recognized too. Task is
     * checked first because [AztecTaskListSpan] is itself an unordered-list span.
     */
    private fun listContainerAt(offset: Int): AztecListSpan? {
        val e = editableText ?: return null
        val end = (offset + 1).coerceAtMost(e.length)
        e.getSpans(offset, end, AztecTaskListSpan::class.java).firstOrNull()?.let { return it }
        e.getSpans(offset, end, AztecOrderedListSpan::class.java).firstOrNull()?.let { return it }
        e.getSpans(offset, end, AztecUnorderedListSpan::class.java).firstOrNull()?.let { return it }
        return null
    }

    /**
     * Market-standard Backspace behavior (Apple Notes / Google Docs / Notion): pressing Backspace at
     * the START of the FIRST item of a list removes just that item's list formatting — it becomes a
     * plain paragraph on its own line — instead of Android's default, which joins the line into the
     * block above and lets it inherit that block's style. Without this, a checklist item sitting
     * under a heading was swallowed into the heading and re-rendered as an oversized H1 (the
     * "разметка сходит с ума" bug).
     *
     * We delist by editing spans directly rather than via [toggleFormatting]: Aztec's toggle only
     * recognizes its own span classes (so it corrupts the app's custom bullet/ordered spans) and,
     * even on stock spans, leaves a degenerate empty `<ul>`/`<ol>` wrapper that then drops the text.
     *
     * Only the FIRST item is special — a Backspace at a later item's head just merges it into the
     * item above (normal), so that falls through to the default. Returns true when it consumed the
     * Backspace.
     */
    private fun handleBackspaceAtListItemStart(): Boolean {
        val e = editableText ?: return false
        if (selectionStart != selectionEnd) return false
        val caret = selectionStart
        val atLineStart = caret == 0 || (caret <= e.length && e[caret - 1] == '\n')
        if (!atLineStart) return false
        val container = listContainerAt(caret) ?: return false
        // Fire only for the FIRST item: if the line above is itself in a list, this is a mid-list
        // item and the default in-list merge is correct. (Checked by content position, not the
        // container's span start, which may or may not include the separating newline.)
        val prevLineInList = caret >= 2 && listContainerAt(caret - 2) != null
        if (prevLineInList) return false
        val cEnd = e.getSpanEnd(container)
        val flags = e.getSpanFlags(container)
        val lineEnd = e.indexOf('\n', caret).let { if (it == -1) e.length else it }
        // Drop this item's list-item marker span(s) so it stops rendering as a bullet/checkbox/number.
        e.getSpans(caret, lineEnd, AztecListItemSpan::class.java).forEach { e.removeSpan(it) }
        e.removeSpan(container)
        if (cEnd > lineEnd) {
            // Multi-item list: keep the items below as a list; restart the container after this item.
            e.setSpan(container, (lineEnd + 1).coerceAtMost(e.length), cEnd, flags)
        }
        // Block-level change shifts line metrics; rebuild the layout and persist (a span-only edit
        // does not fire the TextWatcher, so the debounced flush is driven via onLightweightRefresh).
        rebuildLayout()
        onLightweightRefresh?.invoke()
        return true
    }

    /**
     * Wraps the platform InputConnection so a soft-keyboard Backspace at a list-item head is
     * redirected to [handleBackspaceAtListItemStart]. Keyboards deliver Backspace either as a
     * KEYCODE_DEL key event or (more commonly) as `deleteSurroundingText(1, 0)`, so both are
     * intercepted. Hardware Backspace is covered by [onKeyDown].
     */
    private inner class BackspaceInterceptingConnection(target: InputConnection) :
        InputConnectionWrapper(target, false) {

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_DEL &&
                handleBackspaceAtListItemStart()
            ) {
                return true
            }
            return super.sendKeyEvent(event)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength == 1 && afterLength == 0 && handleBackspaceAtListItemStart()) {
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && handleBackspaceAtListItemStart()) return true
        return super.onKeyDown(keyCode, event)
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
                    // Copy synchronously here (clip access is valid during the paste action) so the
                    // later off-main read doesn't depend on a URI grant that may have lapsed.
                    val localUri = copyToCache(uri)
                    if (localUri != null && handler(localUri)) return true
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }

    /**
     * Copy [source]'s bytes into an app-owned cache file under `cacheDir/camera/` and return a
     * file-backed URI (readable later by the compressor via `contentResolver.openInputStream`,
     * which handles the `file` scheme). Must be called while any read grant on [source] is still
     * valid. Returns null if the stream can't be opened or the copy fails.
     */
    private fun copyToCache(source: Uri): Uri? =
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "clip-${UUID.randomUUID()}")
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (copied) Uri.fromFile(file) else null
        } catch (_: Exception) {
            null
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
