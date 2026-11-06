package app.tessera.ui.editor.engine

import android.text.Editable
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.ParagraphKind

/**
 * Auto-replace of spec §6.5: a space typed right after a marker at the start of a Body paragraph turns the marker into a
 * paragraph kind. A separate step, not hidden in the normalizer: the host calls [tryApply] after the normalizer for an edit that
 * came from the keyboard and records its own history step between the typing and the replacement, so that undo right after
 * the replacement returns the typed characters ([Result.typed] is what was removed).
 */
class AutoReplace(private val rules: EditRules) {
    /** [typed] is the marker and the space that were removed; [caret] is the new caret. */
    data class Result(val kind: ParagraphKind, val typed: String, val caret: Int)

    /**
     * [inserted] is what the edit really put in: the middle that [TypingAttributes.afterChange] returns, not the whole replaced region
     * (an IME that re-commits "- " over an existing "- " inserts nothing). [typed] is false for a paste: nothing happens then.
     * Fires only when a space is the last inserted character, right after a marker that is the whole text before it.
     */
    fun tryApply(e: Editable, inserted: IntRange, typed: Boolean): Result? {
        val m = match(e, inserted, typed) ?: return null
        val caret = rules.replaceMarker(e, m.ps, m.marker.length + 1, m.kind)
        return Result(m.kind, "${m.marker} ", caret)
    }

    /** Would [tryApply] fire. The host asks first, so that it takes its history snapshot only when something is about to be replaced. */
    fun wouldApply(e: Editable, inserted: IntRange, typed: Boolean): Boolean = match(e, inserted, typed) != null

    private class Match(val ps: Int, val marker: String, val kind: ParagraphKind)

    private fun match(e: Editable, inserted: IntRange, typed: Boolean): Match? {
        if (!typed || inserted.isEmpty()) return null
        val space = inserted.last
        if (space >= e.length || e[space] != ' ') return null
        val ps = paragraphStart(e, space)
        if (space - ps > MAX_MARKER) return null // no copy of a long line on every space
        val marker = e.substring(ps, space)
        val kind = MARKERS[marker] ?: return null
        if (kindOf(e, ps) != ParagraphKind.Body || isObjectParagraph(e, ps)) return null
        return Match(ps, marker, kind)
    }

    private companion object {
        const val MAX_MARKER = 3
        fun item(t: ListType) = ParagraphKind.ListItem(t, 0)
        val MARKERS = mapOf(
            "*" to item(ListType.Bullet),
            "-" to item(ListType.Dash),
            "1." to item(ListType.Number),
            "1)" to item(ListType.Number),
            "[]" to item(ListType.Check),
            "[ ]" to item(ListType.Check),
            "#" to ParagraphKind.Title,
            "##" to ParagraphKind.Heading,
            "###" to ParagraphKind.Subheading,
            ">" to ParagraphKind.Quote,
            "```" to ParagraphKind.Mono,
        )
    }
}
