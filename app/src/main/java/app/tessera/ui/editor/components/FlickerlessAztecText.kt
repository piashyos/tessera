package app.tessera.ui.editor.components

import android.content.Context
import android.util.AttributeSet
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

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCaretSelectionChanged?.invoke(selStart, selEnd)
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
