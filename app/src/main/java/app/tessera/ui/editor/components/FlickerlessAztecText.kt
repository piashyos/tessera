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

    override fun refreshText(stealEditorFocus: Boolean) {
        if (stealEditorFocus) {
            super.refreshText(stealEditorFocus)
        } else {
            invalidate()
            onLightweightRefresh?.invoke()
        }
    }
}
