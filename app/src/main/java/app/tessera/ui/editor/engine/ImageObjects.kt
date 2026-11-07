package app.tessera.ui.editor.engine

import android.graphics.drawable.Drawable
import android.text.Spanned
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.domain.model.parseImageRef
import app.tessera.ui.editor.engine.spans.ImageState
import app.tessera.ui.editor.engine.spans.ObjectSpan

/**
 * The life of the pictures of a text (phase 2 spec §3.2): what each image span shows, and the loads behind it. The host calls [sync] whenever
 * the text or its width changes; it is cheap when nothing is new.
 *
 * - A span that has no picture for its `src` and the text width first asks the memory ([ObjectImageSource.peek], or the drawables of the spans
 *   the text had before a rebuild, [carryOver]); then it gets the size the decode will give ([ObjectImageSource.size]) and the load starts.
 * - A result goes to the span only if the span is still in the text and still asks for that `src` and width; anything else is thrown away.
 *   [deliver] then sets the span again, quietly, so the layout measures it.
 * - A load is cancelled when its span leaves the text (a deletion, an undo), when the span wants another `src`, and in [cancelAll].
 */
internal class ImageObjects(private val deliver: (ObjectSpan) -> Unit) {
    private var source: ObjectImageSource? = null
    private val inflight = LinkedHashSet<ImageState>()
    private var carry: Map<String, Drawable>? = null

    /** The new source: what was asked of the old one is asked again. */
    fun setSource(value: ObjectImageSource?) {
        if (source === value) return
        cancelAll()
        source = value
    }

    /** The pictures the spans of [old] show, for the spans of the text that replaces it (undo, an update from outside). Cancels its loads. */
    fun carryOver(old: Spanned?) {
        val kept = HashMap<String, Drawable>()
        if (old != null) for (o in old.getSpans(0, old.length, ObjectSpan::class.java)) {
            val st = o.image ?: continue
            val d = st.drawable ?: continue
            val b = o.block as? NoteBlock.Image ?: continue
            if (!st.failed && st.bakedPercent == parseImageRef(b.src)?.wPercent && o.widthPx > 0) kept["${b.src}@${o.widthPx}"] = d
        }
        cancelAll()
        carry = kept.takeIf { it.isNotEmpty() }
    }

    fun cancelAll() {
        for (st in inflight) st.cancel?.invoke()
        inflight.clear()
        carry = null
    }

    /** Brings the pictures up to date with [spans] at [widthPx]; returns the spans whose size or picture changed (they must be measured again). */
    fun sync(text: Spanned, spans: Array<ObjectSpan>, widthPx: Int): List<ObjectSpan> {
        if (inflight.isNotEmpty()) {
            val gone = inflight.filter { st -> st.span?.let { text.getSpanStart(it) < 0 } ?: true }
            for (st in gone) { st.cancel?.invoke(); st.cancel = null; st.requested = null; inflight.remove(st) }
        }
        var changed: ArrayList<ObjectSpan>? = null
        for (o in spans) {
            val st = o.image ?: continue
            val b = o.block as? NoteBlock.Image ?: continue
            val w = o.widthPx.takeIf { it > 0 } ?: widthPx
            if (w <= 0) continue
            val src = source ?: continue // without a source nothing is asked: the spans are asked again when one is set
            val key = "${b.src}@$w"
            if (st.requested == key) continue
            st.cancel?.invoke(); st.cancel = null; inflight.remove(st)
            st.requested = key
            st.failed = false
            val ref = parseImageRef(b.src)
            val known = src.peek(b.src, w) ?: carry?.get(key)
            if (known != null) {
                st.drawable = known
                st.bakedPercent = ref?.wPercent ?: 100
                st.planned = null
            } else {
                val planned = src.size(b.src, w)
                if (planned != st.planned) st.planned = planned
                start(st, src, b.src, w, key)
            }
            (changed ?: ArrayList<ObjectSpan>().also { changed = it }) += o
        }
        if (inflight.isEmpty()) carry = null
        return changed ?: emptyList()
    }

    private fun start(st: ImageState, src: ObjectImageSource, url: String, w: Int, key: String) {
        inflight += st
        st.cancel = src.load(url, w) { result ->
            inflight.remove(st)
            st.cancel = null
            if (st.requested != key) return@load // another picture was asked for since
            val span = st.span ?: return@load
            when (result) {
                is ImageResult.Loaded -> {
                    st.drawable = result.drawable
                    st.bakedPercent = parseImageRef(url)?.wPercent ?: 100
                    st.failed = false
                }
                ImageResult.Unavailable -> { st.failed = true; st.drawable = null }
            }
            deliver(span)
        }
    }
}
