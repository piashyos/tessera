package app.tessera.ui.editor.components

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.formatting.BlockFormatter
import org.wordpress.aztec.spans.AztecOrderedListSpan

/**
 * Ordered-list span that renders the "1." markers in Inter Medium (variable-font weight 500) so the
 * numbers read as part of the app's type rather than the editor's regular body weight. Color and
 * layout are inherited from [AztecOrderedListSpan], which paints the number with
 * `listStyle.indicatorColor` — we re-style that to the live palette before constructing this span.
 */
class TesseraOrderedListSpan(
    nestingLevel: Int,
    attributes: AztecAttributes,
    listStyle: BlockFormatter.ListStyle,
) : AztecOrderedListSpan(nestingLevel, attributes, listStyle) {

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
        // Bump the variable font to medium just for the number, then restore so the body text the
        // shared paint draws afterwards keeps its regular weight. setFontVariationSettings is API 26+.
        val saved = p.fontVariationSettings
        p.fontVariationSettings = "'wght' 500"
        super.drawLeadingMargin(c, p, x, dir, top, baseline, bottom, text, start, end, first, l)
        p.fontVariationSettings = saved
    }
}
