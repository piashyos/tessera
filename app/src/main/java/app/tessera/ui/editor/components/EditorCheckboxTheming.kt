package app.tessera.ui.editor.components

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable

/**
 * Live, palette-driven colors for the task-list checkbox. Held mutable so a single drawable
 * instance can be built once yet always paint with the *current* theme palette — the editor just
 * needs an `invalidate()` when these change (see [RichNoteBody]).
 */
class CheckboxPalette {
    @Volatile var boxColor: Int = 0
    @Volatile var tickColor: Int = 0
}

/**
 * Self-drawn checkbox: an outlined box when unchecked, a filled box with a tick when checked.
 * Reads [palette] at draw time so palette changes show up on the next invalidate. Bounds/state are
 * set by Aztec's task-list span exactly as for its stock drawable.
 */
internal class TesseraCheckboxDrawable(
    private val checked: Boolean,
    private val palette: CheckboxPalette,
    private val cornerPx: Float,
    private val strokePx: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = palette.boxColor
            rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
            canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.color = palette.tickColor
            val w = rect.width()
            val h = rect.height()
            canvas.drawLine(rect.left + w * 0.24f, rect.top + h * 0.52f, rect.left + w * 0.43f, rect.top + h * 0.70f, paint)
            canvas.drawLine(rect.left + w * 0.43f, rect.top + h * 0.70f, rect.left + w * 0.76f, rect.top + h * 0.30f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.color = palette.boxColor
            val inset = strokePx / 2f
            rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun checkboxSelector(palette: CheckboxPalette, cornerPx: Float, strokePx: Float): StateListDrawable =
    StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_checked), TesseraCheckboxDrawable(true, palette, cornerPx, strokePx))
        addState(intArrayOf(), TesseraCheckboxDrawable(false, palette, cornerPx, strokePx))
    }

/**
 * [Resources] that returns our palette-driven checkbox for Aztec's `ic_checkbox` id and delegates
 * everything else to the platform. Aztec loads the checkbox via `context.resources.getDrawable(id,
 * null)` with a null theme (so it can only read static colors) and recreates the task-list span on
 * every tap — intercepting here is the one place the live palette can reach the checkbox.
 */
private class CheckboxResources(
    base: Resources,
    private val checkboxId: Int,
    private val selector: StateListDrawable,
) : Resources(base.assets, base.displayMetrics, base.configuration) {
    @Deprecated("Deprecated in Java")
    override fun getDrawable(id: Int, theme: Theme?): Drawable =
        if (id == checkboxId) selector else super.getDrawable(id, theme)
}

/**
 * Wraps [base] so the inflated editor (and the task-list spans Aztec creates from its context)
 * resolve `ic_checkbox` to our palette-driven drawable. Returns the wrapped context; mutate
 * [palette] and invalidate the editor to recolor the checkbox live.
 */
fun checkboxThemedContext(
    base: Context,
    palette: CheckboxPalette,
    cornerPx: Float,
    strokePx: Float,
): Context {
    // Match the exact id the task-list span requests (its compiled R constant) — resolving via
    // getIdentifier can yield a different value and silently never match.
    val checkboxId = org.wordpress.aztec.R.drawable.ic_checkbox
    val wrapped = CheckboxResources(base.resources, checkboxId, checkboxSelector(palette, cornerPx, strokePx))
    return object : ContextWrapper(base) {
        override fun getResources(): Resources = wrapped
    }
}
