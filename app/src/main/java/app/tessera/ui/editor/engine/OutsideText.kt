package app.tessera.ui.editor.engine

import android.text.Layout

/** Where a touch landed when it is not on the text itself (spec §3). */
sealed interface Outside {
    /** Under the last line of the note: the caret goes to the end of the note. */
    data object BelowText : Outside

    /** On a line but off its text (right of a short line, an empty line, left of an indented line): the caret goes to [offset]. */
    data class OffLine(val offset: Int) : Outside
}

/** [x], [y] are layout coordinates (padding taken off, scroll added). `null`: the point is on the text. */
fun outsideTextAt(layout: Layout, x: Float, y: Float): Outside? {
    if (layout.lineCount == 0 || y > layout.getLineBottom(layout.lineCount - 1)) return Outside.BelowText
    val line = layout.getLineForVertical(y.toInt().coerceAtLeast(0))
    // getLineLeft ignores the leading margin (quote bar, list marker); getParagraphLeft has it
    if (x >= maxOf(layout.getLineLeft(line), layout.getParagraphLeft(line).toFloat()) && x <= layout.getLineRight(line)) return null
    return Outside.OffLine(layout.getOffsetForHorizontal(line, x))
}
