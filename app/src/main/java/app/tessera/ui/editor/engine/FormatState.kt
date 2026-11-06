package app.tessera.ui.editor.engine

import android.text.Spanned
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind

/** The kind of the paragraphs under the selection: one, or [Mixed] when they differ (list items of one type are one kind). */
sealed interface ParagraphFormat {
    data class Single(val kind: ParagraphKind) : ParagraphFormat
    data object Mixed : ParagraphFormat
}

/** What the format bar shows (spec §7). `canUndo` / `canRedo` come from the history, the host fills them in. */
data class FormatState(
    val paragraph: ParagraphFormat = ParagraphFormat.Single(ParagraphKind.Body),
    val marks: Set<Mark> = emptySet(),
    val canIndent: Boolean = false,
    val canOutdent: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    companion object {
        val Empty = FormatState()

        /**
         * The state at [selStart]..[selEnd]. A mark is active when it covers the whole selection (line breaks, objects, sentinels
         * and mono lines do not count); without a selection it is the typing attributes at the caret.
         */
        fun of(text: Spanned, selStart: Int, selEnd: Int, typing: TypingAttributes): FormatState {
            val starts = touchedParagraphs(text, selStart, selEnd).filter { !isObjectParagraph(text, it) }
            val kinds = starts.map { kindOf(text, it) }
            val paragraph = when {
                kinds.isEmpty() -> ParagraphFormat.Single(ParagraphKind.Body)
                kinds.all { sameFamily(it, kinds.first()) } -> ParagraphFormat.Single(kinds.first())
                else -> ParagraphFormat.Mixed
            }
            val marks = if (selEnd > selStart) {
                val ranges = MarkOps.eligibleRanges(text, selStart, selEnd)
                MarkOps.ALL.filter { m ->
                    val rs = ranges.flatMap { MarkOps.effectiveRanges(text, it.first, it.last + 1, m) }
                    rs.isNotEmpty() && rs.all { MarkOps.covers(text, it.first, it.last + 1, m) }
                }.toSet()
            } else {
                typing.active(text, selStart)
            }
            val items = kinds.filterIsInstance<ParagraphKind.ListItem>()
            return FormatState(
                paragraph = paragraph,
                marks = marks,
                canIndent = items.any { it.level < MAX_LEVEL },
                canOutdent = items.any { it.level > 0 },
            )
        }
    }
}
