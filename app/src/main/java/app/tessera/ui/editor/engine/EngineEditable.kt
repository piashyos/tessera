package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.SpanWatcher
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan

/**
 * The `Editable` of [TesseraEditText]. Two jobs, both for a note of a thousand paragraphs:
 *
 * - **No reflow for a span that only moved.** An insertion moves every span after it, and `SpannableStringBuilder` tells each of them to
 *   the `DynamicLayout`, which reflows the paragraph of each `UpdateLayout` span twice (a block span of a heading, a bold mark): one key
 *   cost O(spans) reflows. A span that starts after the edited range changes nothing in the layout (the layout shifts its own line starts
 *   with the text change), so while a `replace` runs that notification is not delivered to the layout's watcher. The spans in or around the
 *   range, and every other watcher, are told as usual. If a platform version asks its watchers another way, this does nothing.
 * - **[version]** counts the changes of the document (text and engine spans, not the selection or the IME's spans), so what is derived from
 *   the whole document (its Markdown) is computed again only after a change.
 * - **[quietly]** — the one change that is not a change of the document: a span set again as it is, so the layout measures it anew (an
 *   image that has arrived, spec §3.2).
 */
internal class EngineEditable(source: CharSequence) : SpannableStringBuilder(source) {
    var version = 0
        private set

    private var quiet = 0

    /**
     * Runs [block] without a new [version]: for [setSpan] of a span that is set again on its own range with its own flags, which tells the layout
     * to measure it anew and is no change of the document. Anything else inside (a text change, another range, a new span, a removal) breaks the
     * promise that nothing was changed: it throws when [strict] (the host turns it off in a release build, where the version just grows).
     */
    fun quietly(block: () -> Unit) {
        quiet++
        try { block() } finally { quiet-- }
    }

    private fun breach(what: String) {
        if (strict) throw IllegalStateException("quietly: $what")
    }

    /** End of the edited range in the new text while a `replace` delivers its notifications, else -1. */
    private var editEnd = -1

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
        if (quiet > 0) breach("text changed")
        version++
        val outer = editEnd
        editEnd = start + (tbend - tbstart)
        try {
            return super.replace(start, end, tb, tbstart, tbend)
        } finally {
            editEnd = outer
            version++ // the watchers run inside super.replace: what they computed from the text before the change is out of date now
        }
    }

    // An explicit change of a span (the normalizer, a command, the auto-replace, all inside the afterTextChanged of a replace) is always told to
    // the layout: only the shifts that `replace` itself sends are quiet.
    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        if (quiet > 0) {
            if (what == null || getSpanStart(what) != start || getSpanEnd(what) != end || getSpanFlags(what) != flags) breach("a span that is not set again as it is")
        } else if (what != null && DocBinder.isEngineSpan(what)) version++
        val outer = editEnd
        editEnd = -1
        try { super.setSpan(what, start, end, flags) } finally { editEnd = outer }
    }

    override fun removeSpan(what: Any?) {
        if (quiet > 0) breach("a span removed")
        if (what != null && DocBinder.isEngineSpan(what)) version++
        val outer = editEnd
        editEnd = -1
        try { super.removeSpan(what) } finally { editEnd = outer }
    }

    override fun <T : Any?> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> {
        val all = super.getSpans(queryStart, queryEnd, kind)
        if (kind !== SpanWatcher::class.java || editEnd < 0 || queryStart <= editEnd) return all
        // asked for the watchers of a span that starts after the edit: it only moved. A replacement span (an object) is always told: the
        // layout keeps the size of one and a placeholder moved next to a full-width line broke the line after it (found by the layout fuzzer)
        if (super.getSpans(queryStart, queryEnd, ReplacementSpan::class.java).isNotEmpty()) return all
        @Suppress("UNCHECKED_CAST")
        val quiet = all.filter { it?.javaClass?.name != LAYOUT_WATCHER } as List<T>
        return if (quiet.size == all.size) all else quiet.toTypedArray(all)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> List<T>.toTypedArray(like: Array<T>): Array<T> {
        val out = java.lang.reflect.Array.newInstance(like.javaClass.componentType!!, size) as Array<T>
        forEachIndexed { i, v -> out[i] = v }
        return out
    }

    companion object {
        /** See [quietly]. */
        @Volatile var strict = true

        private const val LAYOUT_WATCHER = "android.text.DynamicLayout\$ChangeWatcher"
    }
}
