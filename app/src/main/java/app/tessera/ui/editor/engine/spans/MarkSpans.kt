package app.tessera.ui.editor.engine.spans

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import app.tessera.core.text.notedoc.Mark
import app.tessera.ui.editor.engine.EditorStyle

/** Inline mark spans of the engine (spec §6.1). Set with `SPAN_EXCLUSIVE_EXCLUSIVE`; typing attributes continue them. */
interface MarkSpan {
    val mark: Mark
}

class BoldMarkSpan : StyleSpan(Typeface.BOLD), MarkSpan {
    override val mark: Mark get() = Mark.Bold
}

class ItalicMarkSpan : StyleSpan(Typeface.ITALIC), MarkSpan {
    override val mark: Mark get() = Mark.Italic
}

class UnderlineMarkSpan : UnderlineSpan(), MarkSpan {
    override val mark: Mark get() = Mark.Underline
}

class StrikeMarkSpan : StrikethroughSpan(), MarkSpan {
    override val mark: Mark get() = Mark.Strike
}

/** Inline code: monospace on a tinted background. */
class CodeMarkSpan(val style: EditorStyle) : MetricAffectingSpan(), MarkSpan {
    override val mark: Mark get() = Mark.Code
    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = Typeface.MONOSPACE
    }

    override fun updateDrawState(tp: TextPaint) {
        tp.typeface = Typeface.MONOSPACE
        tp.bgColor = style.codeBackgroundColor
    }
}

fun markSpanOf(mark: Mark, style: EditorStyle): Any = when (mark) {
    Mark.Bold -> BoldMarkSpan()
    Mark.Italic -> ItalicMarkSpan()
    Mark.Underline -> UnderlineMarkSpan()
    Mark.Strike -> StrikeMarkSpan()
    Mark.Code -> CodeMarkSpan(style)
}
