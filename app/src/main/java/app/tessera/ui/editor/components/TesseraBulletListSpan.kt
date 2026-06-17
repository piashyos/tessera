package app.tessera.ui.editor.components

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.formatting.BlockFormatter
import org.wordpress.aztec.spans.AztecUnorderedListSpan

/**
 * Bullet span that paints a filled circle instead of the hardcoded "•" glyph Aztec
 * draws by default. Lets us size the dot independently from the editor's text size.
 */
class TesseraBulletListSpan(
    nestingLevel: Int,
    attributes: AztecAttributes,
    listStyle: BlockFormatter.ListStyle,
    private val bulletRadiusPx: Float,
) : AztecUnorderedListSpan(nestingLevel, attributes, listStyle) {

    override fun drawLeadingMargin(
        c: Canvas,
        p: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        l: Layout,
    ) {
        if (!first) return
        // Mirror Aztec's behavior: only paint on the first line of each list item.
        val processedLineIndex = getIndexOfProcessedLine(text, end) ?: return
        if (processedLineIndex < 0) return
        // Skip when the current draw range falls outside this span — guards against
        // nested-list edge cases identical to the upstream implementation.
        val spanned = text as? Spanned ?: return
        val spanStart = spanned.getSpanStart(this)
        val spanEnd = spanned.getSpanEnd(this)
        if (start < spanStart || end > spanEnd) return

        val style = listStyle
        val savedColor = p.color
        val savedStyle = p.style
        p.color = style.indicatorColor
        p.style = Paint.Style.FILL
        val cx = x + dir * (style.indicatorMargin + bulletRadiusPx)
        val cy = (top + bottom) / 2f
        c.drawCircle(cx, cy, bulletRadiusPx, p)
        p.color = savedColor
        p.style = savedStyle
    }
}
