package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.SpanWatcher
import android.text.SpannableStringBuilder

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
 */
internal class EngineEditable(source: CharSequence) : SpannableStringBuilder(source) {
    var version = 0
        private set

    /** End of the edited range in the new text while a `replace` delivers its notifications, else -1. */
    private var editEnd = -1

    override fun replace(start: Int, end: Int, tb: CharSequence, tbstart: Int, tbend: Int): SpannableStringBuilder {
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

    override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
        if (what != null && DocBinder.isEngineSpan(what)) version++
        super.setSpan(what, start, end, flags)
    }

    override fun removeSpan(what: Any?) {
        if (what != null && DocBinder.isEngineSpan(what)) version++
        super.removeSpan(what)
    }

    override fun <T : Any?> getSpans(queryStart: Int, queryEnd: Int, kind: Class<T>?): Array<T> {
        val all = super.getSpans(queryStart, queryEnd, kind)
        if (kind !== SpanWatcher::class.java || editEnd < 0 || queryStart <= editEnd) return all
        // asked for the watchers of a span that starts after the edit: it only moved
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

    private companion object {
        const val LAYOUT_WATCHER = "android.text.DynamicLayout\$ChangeWatcher"
    }
}
