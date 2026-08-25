package app.tessera.ui.editor.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Spanned
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.formatting.BlockFormatter
import org.wordpress.aztec.spans.AztecPreformatSpan

/**
 * Code-block span that paints a fully rounded background.
 *
 * Aztec's stock [AztecPreformatSpan] draws the block background once *per line* and rounds
 * corners with a [android.graphics.CornerPathEffect] path: the first line gets an open-bottom
 * "∩" path (top corners rounded only) and the last line an open-top "∪" path. A single-line
 * block is therefore both first AND last line, but the upstream first-line branch wins — so its
 * bottom corners are never rounded (the visible "rounded only at the top" artifact).
 *
 * We override [drawBackground] and paint the rect ourselves with explicit per-corner radii:
 * top corners rounded on the first line, bottom corners on the last line. A single line rounds
 * all four. Leading margin, vertical padding and line-height are left to the superclass.
 */
class TesseraPreformatSpan(
    nestingLevel: Int,
    attributes: AztecAttributes,
    preformatStyle: BlockFormatter.PreformatStyle,
) : AztecPreformatSpan(nestingLevel, attributes, preformatStyle) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()
    private val path = Path()

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        val isFirstLine = spanStart == start
        val isLastLine = spanEnd == end

        val style = preformatStyle
        val alpha = (style.preformatBackgroundAlpha * 255f).toInt().coerceIn(0, 255)
        val bg = style.preformatBackground
        fillPaint.color = Color.argb(alpha, Color.red(bg), Color.green(bg), Color.blue(bg))

        val radius = style.preformatBorderRadius.toFloat()
        val topRadius = if (isFirstLine) radius else 0f
        val bottomRadius = if (isLastLine) radius else 0f
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.reset()
        path.addRoundRect(
            rect,
            floatArrayOf(
                topRadius, topRadius, // top-left
                topRadius, topRadius, // top-right
                bottomRadius, bottomRadius, // bottom-right
                bottomRadius, bottomRadius, // bottom-left
            ),
            Path.Direction.CW,
        )
        canvas.drawPath(path, fillPaint)
    }
}
