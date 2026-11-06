package app.tessera.ui.editor.engine

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
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
import androidx.core.view.ViewCompat
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.EditHistory.Snapshot
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.ObjectSpan
import kotlin.math.abs

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
        }

    private val normalizer = Normalizer(style)
    private val rules = EditRules(style)
    private val typing = TypingAttributes(style)
    private val autoReplace = AutoReplace(rules)
    private val history = EditHistory()
    private val emitHandler = Handler(Looper.getMainLooper())
    private val emitRunnable = Runnable { flushPendingEdit() }
    private val a11y = CheckboxAccessibilityHelper(this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var ready = false
    private var guarded = false
    private var inCommand = false // the history step of what the watcher sees is taken by the caller
    private var pasting = false
    private var editKeyListener: KeyListener? = null
    private var originKind: ParagraphKind = ParagraphKind.Body
    private var changeStart = 0
    private var changeEnd = 0

    private var lastLoadedMarkdown: String? = null
    private var hasPendingUserEdit = false
    private var lastFormatState: FormatState? = null

    private var tapTarget: Int? = null
    private var downX = 0f
    private var downY = 0f

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (guarded || !ready) return
            val e = s as Editable
            originKind = normalizer.originKind(e, start)
            typing.beforeChange(e, start, start + count)
            if (!inCommand) history.typing(now()) { snapshot(e) }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
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
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        background = null
        gravity = Gravity.TOP or Gravity.START
        editKeyListener = keyListener
        applyViewStyle()
        addTextChangedListener(watcher)
        ViewCompat.setAccessibilityDelegate(this, a11y)
        ready = true
    }

    // ------------------------------------------------------------------ load and emit (§6.9)

    /**
     * The Markdown the note holds, from the first load and from every later update from outside. Equal to what was last loaded or
     * emitted: ignored (the echo of our own emit). A user edit not emitted yet: skipped, the pending emit reconciles. Otherwise the text
     * is rebuilt, the selection kept by offset, the history cleared. Never emits. The live document is not compared with [markdown]:
     * a legacy note in a non-canonical form would never match.
     */
    fun setMarkdown(markdown: String) {
        if (markdown == lastLoadedMarkdown || hasPendingUserEdit) return
        val s = selectionStart.coerceAtLeast(0)
        val t = selectionEnd.coerceAtLeast(0)
        rebuild(NoteMarkdown.parse(markdown))
        setSelection(s.coerceAtMost(text?.length ?: 0), t.coerceAtMost(text?.length ?: 0))
        history.clear()
        typing.reset()
        lastLoadedMarkdown = markdown
        a11y.invalidateRoot()
        notifyFormatState()
    }

    /** The Markdown of the document as it is now. */
    fun markdown(): String {
        val t = text ?: return ""
        return NoteMarkdown.serialize(DocBinder.read(t))
    }

    /** Emits a pending user edit now (leaving the screen, saving). */
    fun flushPendingEdit() {
        emitHandler.removeCallbacks(emitRunnable)
        if (!hasPendingUserEdit) return
        hasPendingUserEdit = false
        val md = markdown()
        if (md == lastLoadedMarkdown) return // edited and put back: nothing to say
        lastLoadedMarkdown = md
        onMarkdownChange?.invoke(md)
    }

    /** The host composable leaves: a pending user edit is emitted, nothing else is; no callback fires after this. */
    fun dispose() {
        flushPendingEdit()
        emitHandler.removeCallbacksAndMessages(null)
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
    fun insertHorizontalRule() { command { e, _, t -> rules.insertHorizontalRule(e, t) } }

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

    internal fun performBackspace(): Boolean {
        if (!editable) return false
        val e = text ?: return false
        val s = selectionStart.coerceAtLeast(0)
        val t = selectionEnd.coerceAtLeast(0)
        val ps = paragraphStart(e, s)
        if (s == t && s > contentStart(e, ps) && !isObjectParagraph(e, ps)) {
            // a character of the paragraph: it belongs to the typing run, as it would through the IME
            history.typing(now()) { snapshot(e, s, t) }
            guard { rules.backspace(e, s, t).let { setSelection(it.start.coerceAtMost(e.length), it.end.coerceAtMost(e.length)) } }
            commitUserEdit()
        } else {
            command { ed, a, b -> rules.backspace(ed, a, b) }
        }
        return true
    }

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
    private fun command(op: (Editable, Int, Int) -> Sel?): Boolean {
        val e = text ?: return false
        if (!editable) return false
        val s = selectionStart.coerceAtLeast(0)
        val t = selectionEnd.coerceAtLeast(0)
        val before = snapshot(e, s, t)
        var sel: Sel? = null
        guard {
            inCommand = true
            try { sel = op(e, s, t) } finally { inCommand = false }
            applyObjectWidth()
        }
        sel?.let { setSelection(it.start.coerceAtMost(e.length), it.end.coerceAtMost(e.length)) }
        val changed = sel != null && snapshot(e).markdown != before.markdown
        if (changed) {
            history.step(before)
            commitUserEdit()
        } else {
            notifyFormatState()
        }
        return changed
    }

    private fun snapshot(e: Editable, s: Int = selectionStart.coerceAtLeast(0), t: Int = selectionEnd.coerceAtLeast(0)) =
        Snapshot(NoteMarkdown.serialize(DocBinder.read(e)), s, t)

    private fun now() = SystemClock.uptimeMillis()

    // ------------------------------------------------------------------ keys

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (ready && editable) {
            val plain = !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (plain) return performEnter()
                KeyEvent.KEYCODE_DEL -> if (plain) return performBackspace()
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
            android.R.id.paste, android.R.id.pasteAsPlainText -> if (editable) paste()
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

    private fun paste() {
        val fragment = ClipboardBridge.fragment(context)
        if (fragment != null) {
            command { e, s, t -> rules.insertDoc(e, s, t, fragment) }
            return
        }
        val e = text ?: return
        val before = snapshot(e)
        pasting = true
        inCommand = true
        try { super.onTextContextMenuItem(android.R.id.pasteAsPlainText) } finally { pasting = false; inCommand = false }
        if (snapshot(e).markdown != before.markdown) history.step(before)
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
            val span = t.getSpans(ps, minOf(ps + 1, t.length), ListItemSpan::class.java).firstOrNull { t.getSpanStart(it) == ps } ?: continue
            if (span.kind.type != ListType.Check) continue
            val cy = (l.getLineTop(line) + l.getLineBottom(line)) / 2 + offY
            val h = maxOf(l.getLineBottom(line) - l.getLineTop(line), minPx)
            val left = totalPaddingLeft - scrollX
            val rect = Rect(left, cy - h / 2, left + maxOf(span.getLeadingMargin(true), minPx), cy + (h + 1) / 2)
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
                    downX = event.x
                    downY = event.y
                }
                MotionEvent.ACTION_MOVE -> if (tapTarget != null && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) tapTarget = null
                MotionEvent.ACTION_CANCEL -> tapTarget = null
                MotionEvent.ACTION_UP -> {
                    val ps = tapTarget
                    tapTarget = null
                    if (ps != null) { // a tap on a check box: the text under the finger gets no caret, no keyboard
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

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = (ready && a11y.dispatchHoverEvent(event)) || super.dispatchHoverEvent(event)

    // ------------------------------------------------------------------ view

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return // the TextView constructor gets here before this class is built
        typing.onSelectionChanged(selStart, selEnd)
        val t = text
        if (!guarded && t != null && selStart == selEnd && selStart >= 0) {
            val cs = contentStart(t, paragraphStart(t, selStart))
            if (selStart < cs) setSelection(cs) // a caret never stands before a sentinel (§6.1)
        }
        notifyFormatState()
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
     * Objects are as wide as the text area. The width is given to the spans, then they are set again so a live layout measures them
     * anew (the size of a replacement span is cached by the layout).
     */
    private fun applyObjectWidth() {
        val w = (width - totalPaddingLeft - totalPaddingRight).coerceAtLeast(0)
        rules.objectWidthPx = w
        val t = text ?: return
        val spans = t.getSpans(0, t.length, ObjectSpan::class.java)
        if (spans.none { it.widthPx != w }) return
        DocBinder.setObjectWidth(t, w)
        for (o in spans) t.setSpan(o, t.getSpanStart(o), t.getSpanEnd(o), t.getSpanFlags(o))
    }

    private fun applyViewStyle() {
        setTextColor(editorStyle.textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, editorStyle.bodyTextSizePx)
    }

    private inline fun guard(block: () -> Unit) {
        if (guarded) return block()
        guarded = true
        try { block() } finally { guarded = false }
    }

    private companion object {
        const val EMIT_DEBOUNCE_MS = 300L
        const val MIN_TOUCH_DP = 48
    }
}
