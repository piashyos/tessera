package app.tessera.ui.editor.components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Live, palette-driven colors for the task-list checkbox. Held mutable so a single drawable
 * instance can be built once yet always paint with the *current* theme palette — the editor just
 * needs an `invalidate()` when these change.
 */
class CheckboxPalette {
    @Volatile var boxColor: Int = 0
    @Volatile var tickColor: Int = 0
}

/**
 * Self-drawn checkbox: an outlined box when unchecked, a filled box with a tick when checked.
 * Reads [palette] at draw time so palette changes show up on the next invalidate.
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
