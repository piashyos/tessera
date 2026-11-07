package app.tessera.ui.editor.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.MetricAffectingSpan
import android.text.method.ArrowKeyMovementMethod
import android.text.method.KeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.EditHistory.Snapshot
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.ObjectSpan
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** A selected image: its paragraph, its `src`, and where it is in the view (the host draws the resize and rotate handles there). */
data class ImageSelection(val ps: Int, val src: String, val rect: Rect)

/** A tap target of a check box: where it is in the view, and what TalkBack says about it. */
internal class CheckboxZone(val ps: Int, val rect: Rect, val checked: Boolean, val label: String)

/**
 * The native editor (spec §6, D13): an `AppCompatEditText` over an `Editable` with the engine's spans. It owns the whole edit loop:
 * `TextWatcher` (history run, typing attributes, [Normalizer], auto-replace), the commands of [EditRules] behind the panel buttons, Enter
 * and Backspace from every path (hardware keys, [TesseraInputConnection]), copy / paste, check box taps, and the emitting of Markdown
 * (§6.9): [onMarkdownChange] only after a user edit, 300 ms after it; load, [setMarkdown] from outside, [editorStyle], [editable] and
 * [dispose] without a pending edit emit nothing.
 *
 * Every edit the host makes itself runs under one recursion guard, so its own changes do not come back through the watcher.
 * The state a screen needs is [formatState], pushed through [onFormatStateChanged] whenever it changes.
 */
class TesseraEditText(context: Context, style: EditorStyle) :
    AppCompatEditText(context, null, androidx.appcompat.R.attr.editTextStyle) {

    /** The colors and sizes the spans paint with. Setting it repaints the text; nothing is reported as an edit. */
    var editorStyle: EditorStyle = style
        set(value) {
            field = value
            normalizer.style = value
            rules.style = value
            typing.style = value
            applyViewStyle()
            val t = text ?: return
            guard {
                DocBinder.reapplyTheme(t, value)
                Normalizer.refreshNeighbours(t, 0, t.length)
            }
        }

    /** Called with the Markdown of the document, 300 ms after a user edit (and at once by [flushPendingEdit] / [dispose]). */
    var onMarkdownChange: ((String) -> Unit)? = null

    /** Called when what the format bar shows changes; the current state is delivered when the callback is set. */
    var onFormatStateChanged: ((FormatState) -> Unit)? = null
        set(value) {
            field = value
            lastFormatState = null
            notifyFormatState()
        }

    /** `false` for a viewer of a shared note: no input, check boxes stay as they are, the text can be selected and copied (§6.10). */
    var editable: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            val s = selectionStart.coerceAtLeast(0)
            val t = selectionEnd.coerceAtLeast(0)
            guard {
                if (value) {
                    setTextIsSelectable(false) // sets the text again: a new Editable, no movement method, not focusable
                    keyListener = editKeyListener
                    movementMethod = ArrowKeyMovementMethod.getInstance()
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    isLongClickable = true
                } else {
                    keyListener = null
                    setTextIsSelectable(true)
                }
            }
            val len = text?.length ?: 0
            setSelection(s.coerceAtMost(len), t.coerceAtMost(len))
            typing.reset()
            applyObjectWidth()
            a11y.invalidateRoot()
            notifyFormatState()
            restartBlink()
        }

    /** Where the pictures of the image objects come from (phase 2 §3.1); without it they are tiles. */
    var imageSource: ObjectImageSource? = null
        set(value) {
            field = value
            images.setSource(value)
            if (ready) guard { applyObjectWidth() }
        }

    /**
     * An image that arrived through the keyboard (rich content) or the clipboard: a `file:` URI of a copy in the app's cache, made while the system's
     * grant is held. `true` when the host takes it (the editor's insert command is `insertImage`, called when the image is stored).
     */
    var onImageCommitted: ((android.net.Uri) -> Boolean)? = null

    /** The selected image, `null` when the selection is not exactly one image or the editor is read-only. Delivered on the main thread after a draw. */
    var onImageSelectionChanged: ((ImageSelection?) -> Unit)? = null
    private var lastImageSelection: ImageSelection? = null

    /**
     * Images of the keyboard's rich content and of a paste go to [onImageCommitted]; whatever else came with them (text) goes on to the default
     * handling. Here, on the main thread, only the URIs are taken (and the keyboard's grant, if it gave one). The bytes are copied into a file of the
     * app on [copyExecutor] — a big picture must not stop the screen — and the grant is released after the copy. The host gets the file on the main
     * thread; a file the host did not take is deleted.
     */
    private val receiveImages =
        OnReceiveContentListener { _, payload ->
            if (onImageCommitted == null || !editable) return@OnReceiveContentListener payload
            val clipHasImage = payload.clip.description.hasMimeType("image/*")
            val split = payload.partition { item -> clipHasImage && item.uri != null }
            val taken = split.first
            if (taken != null) {
                val uris = (0 until taken.clip.itemCount).mapNotNull { taken.clip.getItemAt(it).uri }
                val grant = keyboardGrant(taken)
                copyExecutor.execute {
                    val files = try { uris.mapNotNull { copyToCache(it) } } finally { grant?.let { runCatching { it.releasePermission() } } }
                    emitHandler.post { for (f in files) if (disposed || onImageCommitted?.invoke(android.net.Uri.fromFile(f)) != true) f.delete() }
                }
            }
            split.second
        }

    /** Where the bytes of an image are copied (not the main thread). */
    internal var copyExecutor: java.util.concurrent.Executor = copyPool

    private val images = ImageObjects { span -> deliverImage(span) }
    private val normalizer = Normalizer(style)
    private val rules = EditRules(style)
    private val typing = TypingAttributes(style)
    private val autoReplace = AutoReplace(rules)
    private val history = EditHistory()
    private val emitHandler = Handler(Looper.getMainLooper())
    private val emitRunnable = Runnable { emitDebounced() }

    /** Where the Markdown of a pending edit is written when the debounce fires: not on the main thread (a note of a thousand paragraphs takes tens of ms). */
    internal var emitExecutor: java.util.concurrent.Executor = emitPool
    private var emitToken = 0
    private var disposed = false
    private val a11y = CheckboxAccessibilityHelper(this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // the caret is ours (see [caretRect]): the platform one stretches over the whole line, margins of a block included
    private val caretPaint = Paint().apply { this.style = Paint.Style.FILL }
    private val caretBox = RectF()
    private val bandPaint = TextPaint()
    private val blink: Runnable = Runnable {
        caretOn = !caretOn
        invalidate()
        if (caretShown()) emitHandler.postDelayed(blink, CARET_BLINK_MS)
    }

    /** The blink phase: `true` right after an edit or a move, then every [CARET_BLINK_MS]. */
    internal var caretOn = true
        private set

    private var ready = false
    private var guarded = false
    private var inCommand = false // the history step of what the watcher sees is taken by the caller
    private var pasting = false
    private var editKeyListener: KeyListener? = null
    private var originKind: ParagraphKind = ParagraphKind.Body
    private var changeStart = 0
    private var changeEnd = 0

    private var lastLoadedMarkdown: String? = null
    private var lastIncoming: String? = null
    private var hasPendingUserEdit = false
    private var lastFormatState: FormatState? = null

    /**
     * The object paragraph that was selected whole (and the version of the document then): the next Backspace deletes it. An IME moves the caret
     * to the end of the selection first (AOSP LatinIME, Gboard) and that keeps the mark; any other move or edit drops it. A caret that
     * got after an object by itself (a tap, an arrow) has no mark: Backspace selects the object first.
     */
    private var selectedObject = -1
    private var selectedObjectVersion = -1
    private var selectedObjectIn: Editable? = null

    private fun trackSelectedObject(s: Int, e: Int) {
        val t = text as? EngineEditable ?: return
        if (e - s == 1 && isObjectParagraph(t, s)) {
            selectedObject = s
            selectedObjectVersion = t.version
            selectedObjectIn = t
        } else if (!(s == e && s == selectedObject + 1 && selectedObjectVersion == t.version && selectedObjectIn === t)) {
            selectedObject = -1
        }
    }

    private fun caretAfterSelectedObject(at: Int): Boolean {
        val t = text as? EngineEditable ?: return false
        return selectedObject >= 0 && selectedObjectVersion == t.version && selectedObjectIn === t && at == selectedObject + 1 && selectionStart == selectionEnd
    }

    private var tapTarget: Int? = null
    private var imageTapTarget: Int? = null
    private var tableTapTarget: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressMs = 0L

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (guarded || !ready) return
            val e = s as Editable
            originKind = normalizer.originKind(e, start)
            typing.beforeChange(e, start, start + count)
            if (!inCommand) history.typing(now()) { snapshot(e) }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            markdownCache = null // the version of the editable changes when the replace is over; a step taken in afterTextChanged needs the text as it is now
            restartBlink()
            if (guarded || !ready) return
            changeStart = start
            changeEnd = start + count
        }

        override fun afterTextChanged(s: Editable) {
            if (guarded || !ready) return
            guard {
                val inserted = typing.afterChange(s, changeEnd)
                normalizer.normalize(s, changeStart, changeEnd, originKind)
                val typed = !pasting
                if (autoReplace.wouldApply(s, inserted, typed)) {
                    if (!inCommand) history.step(snapshot(s, changeEnd, changeEnd)) // undo right after the replacement gives the typed characters back
                    autoReplace.tryApply(s, inserted, typed)?.let { setSelection(it.caret.coerceAtMost(s.length)) }
                }
                applyObjectWidth()
            }
            commitUserEdit()
        }
    }

    init {
        setEditableFactory(object : Editable.Factory() {
            // a debug build (and the unit tests) refuse a misuse of `quietly`, a release build lets the version grow
            override fun newEditable(source: CharSequence): Editable =
                EngineEditable(source, strict = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
        })
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        background = null
        gravity = Gravity.TOP or Gravity.START
        editKeyListener = keyListener
        applyViewStyle()
        hidePlatformCaret()
        addTextChangedListener(watcher)
        ViewCompat.setAccessibilityDelegate(this, a11y)
        ViewCompat.setOnReceiveContentListener(this, arrayOf("image/*"), receiveImages)
        ready = true
    }

    // ------------------------------------------------------------------ load and emit (§6.9)

    /**
     * The Markdown the note holds, from the first load and from every later update from outside. Equal to what was last loaded or
     * emitted, or equal to the previous incoming value: ignored (the echo of our own emit, a repeat). A user edit not emitted yet: skipped, the pending emit reconciles. Otherwise the text
     * is rebuilt, the selection kept by offset, the history cleared. Never emits. The live document is not compared with [markdown]:
     * a legacy note in a non-canonical form would never match.
     */
    fun setMarkdown(markdown: String) {
        // the host calls this on every recomposition with whatever value the note holds: only a value that is new is an update. The old value
        // coming again (the note has not caught up with our emit yet) must not roll the text back.
        if (markdown == lastIncoming) return
        lastIncoming = markdown
        if (markdown == lastLoadedMarkdown) return
        if (hasPendingUserEdit) { skippedIncoming = markdown; return } // the pending emit reconciles; if it has nothing to say, this value is applied then
        val s = selectionStart.coerceAtLeast(0)
        val t = selectionEnd.coerceAtLeast(0)
        rebuild(NoteMarkdown.parse(markdown))
        setSelection(s.coerceAtMost(text?.length ?: 0), t.coerceAtMost(text?.length ?: 0))
        history.clear()
        typing.reset()
        lastLoadedMarkdown = markdown
        known(markdown)
        skippedIncoming = null
        a11y.invalidateRoot()
        notifyFormatState()
    }

    /** The Markdown of the document as it is now. */
    fun markdown(): String {
        val t = text ?: return ""
        return currentMarkdown(t)
    }

    /** The Markdown of [e], serialized again only after the document changed (a history step and an emit would each walk a long note). */
    private fun currentMarkdown(e: Editable): String {
        val en = e as? EngineEditable ?: return NoteMarkdown.serialize(DocBinder.read(e))
        val c = markdownCache
        if (c != null && c.editable === en && c.version == en.version) return c.markdown
        val md = NoteMarkdown.serialize(DocBinder.read(e))
        markdownCache = MarkdownCache(en, en.version, md)
        return md
    }

    private class MarkdownCache(val editable: EngineEditable, val version: Int, val markdown: String)

    private var markdownCache: MarkdownCache? = null

    /**
     * The debounce fired. A light copy of the document is taken here (it costs a fraction of the serialization), the Markdown is written from it on
     * [emitExecutor], and the result comes back to the main thread: if the note is still the one that was copied, it is cached and emitted (§6.9: the
     * same Markdown, the same moment of the contract); if the user went on, a newer debounce has been started and this result is dropped.
     */
    private fun emitDebounced() {
        if (!hasPendingUserEdit) return
        val t = text as? EngineEditable
        val c = markdownCache
        if (t == null || (c != null && c.editable === t && c.version == t.version)) { flushPendingEdit(); return }
        val token = ++emitToken
        val version = t.version
        val copy = SpannableStringBuilder(t)
        emitExecutor.execute {
            val md = NoteMarkdown.serialize(DocBinder.read(copy))
            emitHandler.post { deliverEmit(token, t, version, md) }
        }
    }

    private fun deliverEmit(token: Int, t: EngineEditable, version: Int, md: String) {
        if (disposed || token != emitToken) return
        if (text !== t || t.version != version) { // the note changed while the Markdown was written: write it again after a pause
            if (hasPendingUserEdit) { emitHandler.removeCallbacks(emitRunnable); emitHandler.postDelayed(emitRunnable, EMIT_DEBOUNCE_MS) }
            return
        }
        markdownCache = MarkdownCache(t, version, md)
        flushPendingEdit()
    }

    /** Emits a pending user edit now (leaving the screen, saving). */
    fun flushPendingEdit() {
        emitHandler.removeCallbacks(emitRunnable)
        emitToken++ // a Markdown that is being written in the background is not needed any more
        if (!hasPendingUserEdit) return
        hasPendingUserEdit = false
        val md = markdown()
        if (md == lastLoadedMarkdown) {
            // edited and put back: nothing to say. A value from outside that came while the edit was pending is the news then: apply it (§6.9), unless it
            // is one this editor has loaded or emitted already (the stale value of a recomposition that has not caught up).
            val skipped = skippedIncoming
            skippedIncoming = null
            if (skipped != null && skipped != md && skipped !in knownValues) { lastIncoming = null; setMarkdown(skipped) }
            return
        }
        lastLoadedMarkdown = md
        known(md)
        skippedIncoming = null // the edit goes out: the owner of the note takes it, a value that was skipped behind it is older
        onMarkdownChange?.invoke(md)
    }

    /** Markdown values this editor has loaded or emitted (the last 32): what comes back as a stale value is not news. */
    private val knownValues = LinkedHashSet<String>()
    private fun known(md: String) { knownValues.remove(md); knownValues.add(md); if (knownValues.size > 32) knownValues.remove(knownValues.first()) }
    private var skippedIncoming: String? = null

    /** The host composable leaves: a pending user edit is emitted, nothing else is; no callback fires after this. */
    fun dispose() {
        flushPendingEdit()
        disposed = true
        images.cancelAll()
        emitHandler.removeCallbacksAndMessages(null)
        onImageSelectionChanged = null
        onImageCommitted = null
        onMarkdownChange = null
        onFormatStateChanged = null
    }

    private fun commitUserEdit() {
        hasPendingUserEdit = true
        emitHandler.removeCallbacks(emitRunnable)
        emitHandler.postDelayed(emitRunnable, EMIT_DEBOUNCE_MS)
        a11y.invalidateRoot()
        notifyFormatState()
    }

    /** Replaces the whole text with [doc]; the engine's spans, the width of the objects and the IME state follow. */
    private fun rebuild(doc: app.tessera.core.text.notedoc.NoteDoc) {
        images.carryOver(text)
        guard { setText(DocBinder.build(doc, editorStyle), TextView.BufferType.EDITABLE) }
        applyObjectWidth()
    }

    // ------------------------------------------------------------------ state for the panel

    fun formatState(): FormatState {
        val t = text ?: return FormatState.Empty
        return FormatState.of(t, selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0), typing)
            .copy(canUndo = history.canUndo, canRedo = history.canRedo)
    }

    private fun notifyFormatState() {
        val cb = onFormatStateChanged ?: return
        if (!ready || guarded) return
        val fs = formatState()
        if (fs != lastFormatState) {
            lastFormatState = fs
            cb(fs)
        }
    }

    // ------------------------------------------------------------------ commands (the panel, the keys)

    fun toggleKind(kind: ParagraphKind) { command { e, s, t -> rules.toggleKind(e, s, t, kind) } }

    /** A selection: the mark over it. A caret: the mark of the text typed next (not an edit of the document). */
    fun toggleMark(mark: Mark) {
        if (!editable) return
        val s = selectionStart.coerceAtLeast(0)
        val t = selectionEnd.coerceAtLeast(0)
        if (s == t) {
            typing.toggle(text ?: return, s, mark)
            lastFormatState = null
            notifyFormatState()
        } else {
            command { e, a, b -> rules.toggleMark(e, a, b, mark) }
        }
    }

    fun indent() { command { e, s, t -> rules.changeLevel(e, s, t, +1) } }
    fun outdent() { command { e, s, t -> rules.changeLevel(e, s, t, -1) } }
    fun insertHorizontalRule() { command { e, s, t -> rules.insertHorizontalRule(e, s, t) } }

    /** [block] as a paragraph of its own after the caret's (phase 2 §2). A selection is deleted first. */
    fun insertObject(block: NoteBlock) { command { e, s, t -> rules.insertObject(e, s, t, block) } }

    /** An empty 2×2 table as a paragraph of its own after the caret's (phase 2 §4). */
    fun insertTable() = insertObject(NoteBlock.Table(TableGrid.empty(columns = 2, rows = 2)))

    /**
     * The table of the paragraph [ps] gets [grid] (a cell committed, a row added): one history step, the selection stays where it was (the table is
     * not selected by an edit of its cell). `false` when nothing changed.
     */
    fun replaceTable(ps: Int, grid: TableGrid): Boolean {
        val old = (tableSpanAt(ps)?.block as? NoteBlock.Table)?.grid ?: return false
        if (old == grid) return false
        return command { e, s, t -> if (rules.replaceObject(e, ps, NoteBlock.Table(grid)) != null) Sel(s, t) else null }
    }

    /** The object of the paragraph [ps] goes, its paragraph with it (the table's delete button): one step. */
    fun deleteObjectAt(ps: Int): Boolean = command { e, _, _ -> rules.forwardDelete(e, ps, ps + 1) }

    /** The table of the paragraph [ps] swaps places with the paragraph before it ([delta] −1) or after it (+1): one step. */
    fun moveTable(ps: Int, delta: Int): Boolean = command { e, _, _ -> rules.moveObject(e, ps, delta) }

    /** The caret goes to the paragraph after the table at [ps] (Enter in its last cell) and the editor takes the focus and the keyboard. */
    fun focusAfterTable(ps: Int) {
        val e = text ?: return
        if (!editable || ps !in 0 until e.length) return
        val next = (paragraphEnd(e, ps) + 1).coerceAtMost(e.length)
        requestFocus()
        setSelection(next)
        context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(this, 0)
    }

    private fun tableSpanAt(ps: Int): ObjectSpan? {
        val t = text ?: return null
        if (ps !in 0 until t.length) return null
        return t.getSpans(ps, ps + 1, ObjectSpan::class.java).firstOrNull { t.getSpanStart(it) == ps && it.block is NoteBlock.Table }
    }

    /**
     * The overlay of the [index]-th table (document order) measured [heightPx]: the span reserves that much from now on (the same layout pass: the
     * overlay and the text under it never disagree for a frame). Remembered for the grid, so a table that is rebuilt starts at its height.
     */
    internal fun reportTableHeight(index: Int, heightPx: Int) {
        val t = text as? EngineEditable ?: return
        if (disposed || heightPx <= 0) return
        val span = t.getSpans(0, t.length, ObjectSpan::class.java).filter { it.block is NoteBlock.Table }.sortedBy { t.getSpanStart(it) }.getOrNull(index) ?: return
        tableHeights[(span.block as NoteBlock.Table).grid] = heightPx
        if (span.tableHeightPx == heightPx) return
        span.tableHeightPx = heightPx
        deliverImage(span)
    }

    private val tableHeights = HashMap<TableGrid, Int>()

    /** What a table takes before its overlay has measured it: the height this grid had, else 40dp for each line and the overlay's padding. */
    private fun plannedTableHeight(grid: TableGrid): Int =
        tableHeights[grid] ?: (((grid.rowCount + 1) * 40 + 8) * resources.displayMetrics.density).toInt()

    /** The image `tessera-img://<imageId>` at the caret, once the host has stored it. */
    fun insertImage(imageId: String) = insertObject(NoteBlock.Image("tessera-img://$imageId"))

    /** The image of the paragraph [ps] gets [src] (a resize, a rotation): one history step; the old picture stays on screen until the new one comes. */
    fun replaceImageSrc(ps: Int, src: String, expected: String? = null) {
        val current = (imageSpanAt(ps)?.block as? NoteBlock.Image)?.src ?: return
        if (expected != null && current != expected) return // the handles are of an image that is not here any more: they must not touch what stands there now
        if (current == src) { previewImageWidth(ps, null); return }
        command { e, _, _ -> rules.replaceObject(e, ps, NoteBlock.Image(src)) }
    }

    /** While a handle is dragged: the image of [ps] is shown at [percent] of its width (`null` ends the preview). Not an edit. */
    fun previewImageWidth(ps: Int, percent: Int?) {
        val span = imageSpanAt(ps) ?: return
        val st = span.image ?: return
        if (st.previewPercent == percent) return
        st.previewPercent = percent
        deliverImage(span)
    }

    private fun imageSpanAt(ps: Int): ObjectSpan? {
        val t = text ?: return null
        if (ps !in 0 until t.length) return null
        return t.getSpans(ps, ps + 1, ObjectSpan::class.java).firstOrNull { t.getSpanStart(it) == ps && it.block is NoteBlock.Image }
    }

    /** The span was changed in size or picture: the layout measures it again; nothing of the document changed. */
    private fun deliverImage(span: ObjectSpan) {
        val t = text as? EngineEditable ?: return
        if (disposed) return
        val s = t.getSpanStart(span)
        if (s < 0) return
        guard { t.quietly { t.setSpan(span, s, t.getSpanEnd(span), t.getSpanFlags(span)) } }
        a11y.invalidateRoot()
        invalidate()
    }

    fun undo() { travel { cur -> history.undo(cur) } }
    fun redo() { travel { cur -> history.redo(cur) } }

    private fun travel(pick: (Snapshot) -> Snapshot?) {
        val e = text ?: return
        if (!editable) return
        val target = pick(snapshot(e)) ?: return
        rebuild(NoteMarkdown.parse(target.markdown))
        val len = text?.length ?: 0
        setSelection(target.selStart.coerceAtMost(len), target.selEnd.coerceAtMost(len))
        typing.reset()
        commitUserEdit() // an undo is a user edit: it goes out
    }

    internal fun performEnter(): Boolean {
        if (!editable) return false
        command { e, s, t -> rules.enter(e, s, t) }
        return true
    }

    /**
     * Backspace at [at] (default: the caret). An IME with a composing region deletes before the start of the region, not before the
     * caret ([TesseraInputConnection]): [at] is then the start of the region and the caret keeps its place relative to the text after it.
     */
    internal fun performBackspace(at: Int = -1): Boolean {
        if (!editable) return false
        val e = text ?: return false
        val s = selectionStart.coerceAtLeast(0)
        val t = selectionEnd.coerceAtLeast(0)
        val pos = if (at >= 0) at.coerceAtMost(e.length) else s
        val ps = paragraphStart(e, pos)
        if ((at >= 0 || s == t) && pos > contentStart(e, ps) && !isObjectParagraph(e, ps)) {
            // a character of the paragraph: it belongs to the typing run, as it would through the IME
            history.typing(now()) { snapshot(e, s, t) }
            guard {
                val len = e.length
                val r = rules.backspace(e, pos, pos)
                if (at >= 0 && at != s) { // the caret was after the composing text: it moves back by what went
                    val d = len - e.length
                    setSelection((s - d).coerceIn(0, e.length), (t - d).coerceIn(0, e.length))
                } else {
                    setSelection(r.start.coerceAtMost(e.length), r.end.coerceAtMost(e.length))
                }
            }
            commitUserEdit()
        } else {
            val afterSelectedObject = caretAfterSelectedObject(pos)
            command(at = if (at >= 0) at else null) { ed, a, b -> rules.backspace(ed, a, b, afterSelectedObject) }
        }
        return true
    }

    /** Delete over an object: it is selected (the caret before it) or deleted (it is selected). `true` when the rule handled the key, edit or not. */
    private fun performForwardDelete(): Boolean {
        var handled = false
        command { e, s, t -> rules.forwardDelete(e, s, t)?.also { handled = true } }
        return handled
    }

    /** Delete with the caret at the end of the text deletes nothing; the platform's key listener does not take that well (a range before 0). */
    private fun atEndOfText() = selectionStart == selectionEnd && selectionEnd == length()

    internal fun hasCollapsedSelection() = selectionStart == selectionEnd

    /** The caret right after the sentinel of an empty last paragraph is the start of that paragraph (autocapitalization, §6.1). */
    internal fun capsModeAfterSentinel(reqModes: Int): Int? {
        val e = text ?: return null
        val s = selectionStart
        if (s <= 0 || s != selectionEnd) return null
        val ps = paragraphStart(e, s)
        return if (s > ps && contentStart(e, ps) == s) TextUtils.getCapsMode(e, ps, reqModes) else null
    }

    internal fun toggleCheckAt(ps: Int): Boolean {
        if (!editable) return false
        return command { e, s, t -> if (rules.toggleChecked(e, ps)) Sel(s, t) else null }
    }

    /**
     * Runs [op] over the selection under the guard as one history step. [op] returns the new selection, or `null` for a command that
     * did nothing. Whatever changed the document (text, a kind, a mark, a check mark) is a user edit. True when something changed.
     */
    private fun command(at: Int? = null, op: (Editable, Int, Int) -> Sel?): Boolean {
        val e = text ?: return false
        if (!editable) return false
        val s = at ?: selectionStart.coerceAtLeast(0)
        val t = at ?: selectionEnd.coerceAtLeast(0)
        val before = snapshot(e, s, t)
        val v0 = versionOf(e)
        var sel: Sel? = null
        guard {
            inCommand = true
            try { sel = op(e, s, t) } finally { inCommand = false }
            applyObjectWidth()
        }
        sel?.let { setSelection(it.start.coerceAtMost(e.length), it.end.coerceAtMost(e.length)) }
        val changed = sel != null && changedSince(e, v0)
        if (changed) {
            history.step(before)
            commitUserEdit()
        } else {
            notifyFormatState()
        }
        return changed
    }

    /**
     * The state of the note for the history. When the Markdown of the current version is known it is the snapshot; otherwise the snapshot is a light
     * copy of the document and the Markdown is written when a step is used (undo, redo): taking it costs nothing like a serialization of the note.
     */
    private fun snapshot(e: Editable, s: Int = selectionStart.coerceAtLeast(0), t: Int = selectionEnd.coerceAtLeast(0)): Snapshot {
        val en = e as? EngineEditable
        val c = markdownCache
        if (en != null && c != null && c.editable === en && c.version == en.version) return Snapshot(c.markdown, s, t)
        val copy = SpannableStringBuilder(e)
        return Snapshot.lazy(s, t) { NoteMarkdown.serialize(DocBinder.read(copy)) }
    }

    /** Did [op] change the document since [version] (text or the spans of the engine)? */
    private fun changedSince(e: Editable, version: Int?) = version == null || (e as? EngineEditable)?.version != version
    private fun versionOf(e: Editable) = (e as? EngineEditable)?.version

    private fun now() = SystemClock.uptimeMillis()

    // ------------------------------------------------------------------ images from the keyboard and the clipboard

    /** The keyboard's grant on the URI, which it hands over in the extras of the content; it is released when the bytes are copied. */
    private fun keyboardGrant(content: ContentInfoCompat): androidx.core.view.inputmethod.InputContentInfoCompat? =
        runCatching {
            val info = content.extras?.getParcelable<android.view.inputmethod.InputContentInfo>("androidx.core.view.extra.INPUT_CONTENT_INFO")
            info?.let { androidx.core.view.inputmethod.InputContentInfoCompat.wrap(it) }
        }.getOrNull()

    private fun copyToCache(source: android.net.Uri): File? =
        try {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "clip-${UUID.randomUUID()}")
            val copied = context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) }; true } ?: false
            if (copied) file else null
        } catch (_: Exception) {
            null
        }

    // ------------------------------------------------------------------ keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ready && editable) {
            val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (plain) return performEnter()
                KeyEvent.KEYCODE_DEL -> if (plain) return performBackspace()
                KeyEvent.KEYCODE_FORWARD_DEL -> if (plain && (performForwardDelete() || atEndOfText())) return true
                KeyEvent.KEYCODE_TAB -> if (plain && indentByKey(if (event.isShiftPressed) -1 else +1)) return true
            }
            if (handleShortcut(keyCode, event)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Ctrl+B/I/U/Z go through `onKeyShortcut` on a device, before `onKeyDown`; TextView would take Ctrl+Z for its own undo stack. */
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (ready && editable && handleShortcut(keyCode, event)) return true
        return super.onKeyShortcut(keyCode, event)
    }

    private fun handleShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (!(event.isCtrlPressed || event.isMetaPressed) || event.isAltPressed) return false
        when (keyCode) {
            KeyEvent.KEYCODE_B -> toggleMark(Mark.Bold)
            KeyEvent.KEYCODE_I -> toggleMark(Mark.Italic)
            KeyEvent.KEYCODE_U -> toggleMark(Mark.Underline)
            KeyEvent.KEYCODE_Z -> if (event.isShiftPressed) redo() else undo()
            else -> return false
        }
        return true
    }

    /** Tab / Shift+Tab move list items; on anything else the key keeps its default (focus). */
    private fun indentByKey(delta: Int): Boolean {
        val e = text ?: return false
        val touchesList = touchedParagraphs(e, selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0)).any { kindOf(e, it) is ParagraphKind.ListItem }
        if (!touchesList) return false
        command { ed, s, t -> rules.changeLevel(ed, s, t, delta) }
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
        super.onCreateInputConnection(outAttrs)?.let { TesseraInputConnection(it, this) }

    // ------------------------------------------------------------------ clipboard (§6.8)

    override fun onTextContextMenuItem(id: Int): Boolean {
        when (id) {
            android.R.id.undo -> if (editable) undo()
            android.R.id.redo -> if (editable) redo()
            android.R.id.copy -> copySelection()
            android.R.id.cut -> if (editable) cutSelection()
            android.R.id.paste -> if (editable) paste(plainOnly = false)
            android.R.id.pasteAsPlainText -> if (editable) paste(plainOnly = true) // Ctrl+Shift+V: never the fragment
            else -> return super.onTextContextMenuItem(id)
        }
        return true
    }

    private fun copySelection(): Boolean {
        val e = text ?: return false
        val s = selectionStart
        val t = selectionEnd
        if (!ClipboardBridge.copy(context, e, minOf(s, t), maxOf(s, t))) return false
        setSelection(maxOf(s, t)) // as TextView does: the selection is done with
        return true
    }

    private fun cutSelection() {
        val s = selectionStart
        val t = selectionEnd
        if (s == t) return
        if (ClipboardBridge.copy(context, text ?: return, minOf(s, t), maxOf(s, t))) command { e, a, b -> rules.backspace(e, a, b) }
    }

    private fun paste(plainOnly: Boolean) {
        val fragment = if (plainOnly) null else ClipboardBridge.fragment(context)
        if (fragment != null) {
            command { e, s, t -> rules.insertDoc(e, s, t, fragment) }
            return
        }
        val e = text ?: return
        val before = snapshot(e)
        val v0 = versionOf(e)
        pasting = true
        inCommand = true
        try { super.onTextContextMenuItem(android.R.id.pasteAsPlainText) } finally { pasting = false; inCommand = false }
        if (changedSince(e, v0)) {
            history.step(before)
            notifyFormatState() // the watcher notified before the step: canUndo / canRedo were stale
        }
    }

    // ------------------------------------------------------------------ check boxes (§6.10)

    internal fun checkboxZones(): List<CheckboxZone> {
        val l = layout ?: return emptyList()
        val t = text ?: return emptyList()
        if (t.isEmpty()) return emptyList()
        val minPx = (MIN_TOUCH_DP * resources.displayMetrics.density).toInt()
        val offY = totalPaddingTop - scrollY
        val firstLine = l.getLineForVertical((-offY - minPx).coerceAtLeast(0))
        val lastLine = l.getLineForVertical((height - offY + minPx).coerceAtLeast(0))
        val visible = Rect(0, 0, width, height)
        val out = ArrayList<CheckboxZone>()
        for (line in firstLine..lastLine) {
            val ps = l.getLineStart(line)
            if (ps > 0 && t[ps - 1] != '\n') continue // the marker is on the first line of the item
            val span = blockSpanAt(t, ps) as? ListItemSpan ?: continue
            if (span.kind.type != ListType.Check) continue
            val fm = paint.fontMetrics
            val cy = (l.getLineBaseline(line) + ((fm.ascent + fm.descent) / 2).toInt()) + offY // the middle of the text line, not of its margins
            val h = maxOf(l.getLineBottom(line) - l.getLineTop(line), minPx)
            val left = totalPaddingLeft - scrollX
            val rect = Rect(left, cy - h / 2, left + span.getLeadingMargin(true), cy + (h + 1) / 2) // the width of the marker area; the text after it is a caret place
            if (!Rect.intersects(rect, visible)) continue
            out += CheckboxZone(ps, rect, span.kind.checked, t.subSequence(contentStart(t, ps), paragraphEnd(t, ps)).toString())
        }
        return out
    }

    /** The zone [x], [y] (in the view) is in; of overlapping ones the item whose line is nearest. */
    internal fun checkboxZoneAt(x: Float, y: Float): CheckboxZone? =
        checkboxZones().filter { it.rect.contains(x.toInt(), y.toInt()) }.minByOrNull { abs(it.rect.centerY() - y) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (ready && editable) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tapTarget = checkboxZoneAt(event.x, event.y)?.ps
                    imageTapTarget = if (tapTarget == null) imageZones().firstOrNull { it.rect.contains(event.x.toInt(), event.y.toInt()) }?.ps else null
                    // what reaches the editor on a table's line is its margin (the overlay takes the table itself): not a place for the caret
                    tableTapTarget = if (tapTarget == null && imageTapTarget == null) tableLineAt(event.y) else null
                    longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
                    downX = event.x
                    downY = event.y
                }
                MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) { tapTarget = null; imageTapTarget = null; tableTapTarget = null }
                MotionEvent.ACTION_CANCEL -> { tapTarget = null; imageTapTarget = null; tableTapTarget = null }
                MotionEvent.ACTION_UP -> {
                    val ps = tapTarget
                    tapTarget = null
                    val image = imageTapTarget
                    imageTapTarget = null
                    val table = tableTapTarget
                    tableTapTarget = null
                    if (table != null && event.eventTime - event.downTime < longPressMs) { // a tap in the margin of a table: the caret goes to the paragraph next to it
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        caretNextToTable(table, above = event.y < (objectRect(table)?.top ?: 0))
                        return true
                    }
                    if (image != null && event.eventTime - event.downTime < longPressMs) { // a tap on an image selects it: no caret, no keyboard
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        selectImage(image)
                        return true
                    }
                    if (ps != null && event.eventTime - event.downTime < longPressMs) { // a long press is a selection of the word, not a tap // a tap on a check box: the text under the finger gets no caret, no keyboard
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        toggleCheckAt(ps)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /** The table paragraph whose line is at [y] (view coordinates), else `null`. */
    private fun tableLineAt(y: Float): Int? {
        val l = layout ?: return null
        val ly = (y - totalPaddingTop + scrollY).toInt()
        if (ly < 0 || ly > l.height) return null
        val ps = l.getLineStart(l.getLineForVertical(ly))
        return if (tableSpanAt(ps) != null) ps else null
    }

    /** A tap above a table puts the caret at the end of the paragraph before it, below it at the start of the one after (the only one there is: at the other side). */
    private fun caretNextToTable(ps: Int, above: Boolean) {
        val e = text ?: return
        val pe = paragraphEnd(e, ps)
        val at = when {
            above && ps > 0 -> ps - 1
            pe < e.length -> pe + 1
            ps > 0 -> ps - 1
            else -> return
        }
        requestFocus()
        setSelection(at)
        context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(this, 0)
    }

    /** The image of the paragraph [ps] is the selection; the keyboard goes away (a tap on an image is not a place to type). */
    private fun selectImage(ps: Int) {
        requestFocus() // the hardware keys (Backspace, Delete) reach the selected object
        setSelection(ps, ps + 1)
        context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = (ready && a11y.dispatchHoverEvent(event)) || super.dispatchHoverEvent(event)

    // ------------------------------------------------------------------ view

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return // the TextView constructor gets here before this class is built
        typing.onSelectionChanged(selStart, selEnd)
        trackSelectedObject(selStart, selEnd)
        val t = text
        if (!guarded && t != null && selStart == selEnd && selStart >= 0) {
            val cs = contentStart(t, paragraphStart(t, selStart))
            if (selStart < cs) setSelection(cs) // a caret never stands before a sentinel (§6.1)
        }
        notifyFormatState()
        restartBlink()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (ready) restartBlink()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ready) restartBlink()
    }

    override fun onDetachedFromWindow() {
        emitHandler.removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ caret

    /**
     * The platform caret is as tall as the line, and a line carries the margin of its block (quote padding, the gap above a heading, the
     * padding of a list item) in its height. A caret that draws nothing stays in its place, so the insertion handle (it needs a visible cursor)
     * keeps working; before API 29 there is no hook for that and the cursor is hidden whole.
     */
    private fun hidePlatformCaret() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) textCursorDrawable = NoCaret else isCursorVisible = false
    }

    private fun caretShown() = ready && !disposed && editable && isFocused && selectionStart >= 0 && selectionStart == selectionEnd

    private fun restartBlink() {
        emitHandler.removeCallbacks(blink)
        caretOn = true
        invalidate()
        if (caretShown()) emitHandler.postDelayed(blink, CARET_BLINK_MS)
    }

    /**
     * The text of [line] without what the block adds around it: from the top of its glyphs (baseline + ascent of the font the caret's
     * neighbour is set in) to their bottom (baseline + descent), in layout coordinates. Margins, paddings and `lineSpacingExtra` are outside.
     */
    internal fun textBand(line: Int): Pair<Int, Int> {
        val l = layout ?: return 0 to 0
        val t = text ?: return l.getLineTop(line) to l.getLineBottom(line)
        val start = l.getLineStart(line)
        val at = if (selectionStart in start..l.getLineEnd(line) && selectionStart > start) selectionStart - 1 else start
        bandPaint.set(paint)
        for (span in t.getSpans(at, minOf(at + 1, t.length), MetricAffectingSpan::class.java)) span.updateMeasureState(bandPaint)
        val baseline = l.getLineBaseline(line)
        val fm = bandPaint.fontMetricsInt
        return (baseline + fm.ascent) to (baseline + fm.descent)
    }

    /** Where the caret is drawn (view content coordinates), `null` when it is not: no focus, a selection, a read-only note. Ignores the blink phase. */
    internal fun caretRect(): RectF? {
        if (!caretShown()) return null
        val l = layout ?: return null
        val sel = selectionStart
        if (sel > (text?.length ?: 0)) return null
        val (top, bottom) = textBand(l.getLineForOffset(sel))
        val w = CARET_WIDTH_DP * resources.displayMetrics.density
        val x = (totalPaddingLeft + l.getPrimaryHorizontal(sel) - 0.5f).coerceIn(0f, (width - w).coerceAtLeast(0f))
        return caretBox.apply { set(x, (totalPaddingTop + top).toFloat(), x + w, (totalPaddingTop + bottom).toFloat()) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (caretOn) caretRect()?.let { canvas.drawRect(it, caretPaint) }
        pushImageSelection()
    }

    /**
     * Where the handles of the selected image go: read after each draw, so the scroll, a new layout and a picture that has arrived are all
     * in it. Told to the host (posted: a draw is no place for it to change its state) only when it differs from what it was told last.
     */
    private fun pushImageSelection() {
        val now = currentImageSelection()
        if (now == lastImageSelection) return
        lastImageSelection = now
        val cb = onImageSelectionChanged ?: return
        emitHandler.post { if (!disposed) cb(now) }
    }

    private fun currentImageSelection(): ImageSelection? {
        if (!editable || !isFocused) return null // an editor that is not in use (the keyboard went, another field has the focus) has no handles
        val s = selectionStart
        if (selectionEnd - s != 1) return null
        val span = imageSpanAt(s) ?: return null
        val rect = objectRect(s) ?: return null
        if (!Rect.intersects(rect, Rect(0, 0, width, height))) return null // scrolled out: no frame over the title bar or the tags
        return ImageSelection(s, (span.block as NoteBlock.Image).src, rect)
    }

    private object NoCaret : Drawable() {
        override fun draw(canvas: Canvas) = Unit
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSPARENT
        override fun getIntrinsicWidth() = 0
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (ready) applyObjectWidth() // also when only the padding changed
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (ready) applyObjectWidth()
    }

    /**
     * Objects are as wide as the text area. The width is given to the spans, the pictures are brought up to date ([ImageObjects.sync]), then the
     * spans that changed are set again, quietly (nothing of the document changed), so a live layout measures them anew (the size of a
     * replacement span is cached by the layout).
     */
    private fun applyObjectWidth() {
        val w = (width - totalPaddingLeft - totalPaddingRight).coerceAtLeast(0)
        rules.objectWidthPx = w
        val t = text ?: return
        val spans = t.getSpans(0, t.length, ObjectSpan::class.java)
        val again = ArrayList<ObjectSpan>()
        for (o in spans) if (o.widthPx != w) { o.widthPx = w; again += o }
        for (o in spans) {
            val table = o.block as? NoteBlock.Table ?: continue
            if (o.tableHeightPx == 0) { o.tableHeightPx = plannedTableHeight(table.grid); if (o !in again) again += o }
        }
        for (o in images.sync(t, spans, w)) if (o !in again) again += o
        if (again.isEmpty()) return
        val reset = { for (o in again) t.setSpan(o, t.getSpanStart(o), t.getSpanEnd(o), t.getSpanFlags(o)) }
        if (t is EngineEditable) t.quietly(reset) else reset()
    }

    private fun applyViewStyle() {
        setTextColor(editorStyle.textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, editorStyle.bodyTextSizePx)
        caretPaint.color = editorStyle.accentColor
        invalidate()
    }

    private inline fun guard(block: () -> Unit) {
        if (guarded) return block()
        guarded = true
        try { block() } finally { guarded = false }
    }

    private companion object {
        /** One thread for all editors: the Markdown of the last pending edit is what counts. */
        /** One thread for the copies of images that arrive from the keyboard and the clipboard. */
        val copyPool: java.util.concurrent.Executor by lazy {
            java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "tessera-image-copy").apply { isDaemon = true } }
        }

        val emitPool: java.util.concurrent.Executor by lazy {
            java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "tessera-emit").apply { isDaemon = true } }
        }
        const val EMIT_DEBOUNCE_MS = 300L
        const val MIN_TOUCH_DP = 48
        const val CARET_BLINK_MS = 500L
        const val CARET_WIDTH_DP = 2
    }
}
