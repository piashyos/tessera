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
 * - A load is cancelled when its span leaves the text (a deletion), when the span wants another `src`, and in [cancelAll]. A rebuild of the text
 *   (undo, an update from outside) does not cancel the loads of the old spans: the new spans of the same `src` and width take them over
 *   ([carryOver], [sync]); what no new span took is cancelled at the end of the first [sync].
 */
internal class ImageObjects(private val deliver: (ObjectSpan) -> Unit) {
    private var source: ObjectImageSource? = null
    private val inflight = LinkedHashSet<ImageState>()
    private var carry: Map<String, Drawable>? = null

    /** Loads of the spans of the text that was replaced, by `src@width`, waiting for a span of the new text to take them over. */
    private val carriedLoads = HashMap<String, ImageState>()

    /** The new source: what was asked of the old one is asked again. */
    fun setSource(value: ObjectImageSource?) {
        if (source === value) return
        cancelAll()
        source = value
    }

    /**
     * The text is replaced (undo, an update from outside): the pictures its spans show, and the loads they wait for, are offered to the spans of the
     * new text. Nothing is cancelled yet.
     */
    fun carryOver(old: Spanned?) {
        val kept = HashMap<String, Drawable>()
        if (old != null) for (o in old.getSpans(0, old.length, ObjectSpan::class.java)) {
            val st = o.image ?: continue
            val d = st.drawable ?: continue
            val b = o.block as? NoteBlock.Image ?: continue
            if (!st.failed && st.bakedPercent == parseImageRef(b.src)?.wPercent && o.widthPx > 0) kept["${b.src}@${o.widthPx}"] = d
        }
        for (key in carriedLoads.keys.toList()) carriedLoads.remove(key)?.let { it.cancel?.invoke(); it.cancel = null; it.requested = null }
        for (st in inflight) st.requested?.let { carriedLoads.putIfAbsent(it, st) }
        for (st in carriedLoads.values) inflight.remove(st)
        carry = kept.takeIf { it.isNotEmpty() }
    }

    fun cancelAll() {
        for (st in inflight) { st.cancel?.invoke(); st.cancel = null; st.requested = null }
        for (st in carriedLoads.values) { st.cancel?.invoke(); st.cancel = null; st.requested = null }
        inflight.clear()
        carriedLoads.clear()
        carry = null
    }

    /** Brings the pictures up to date with [spans] at [widthPx]; returns the spans whose size or picture changed (they must be measured again). */
    fun sync(text: Spanned, spans: Array<ObjectSpan>, widthPx: Int): List<ObjectSpan> {
        if (inflight.isNotEmpty()) {
            val gone = inflight.filter { st -> st.span?.let { text.getSpanStart(it) < 0 } ?: true }
            for (st in gone) { st.cancel?.invoke(); st.cancel = null; st.requested = null; inflight.remove(st) }
        }
        var changed: ArrayList<ObjectSpan>? = null
        val asked = source != null && widthPx > 0 // a sync that could ask for the pictures of the text: what was carried over and not taken is dropped
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
                val taken = carriedLoads.remove(key)
                if (taken != null) { // the same image at the same width was loading in the old text: that load is this span's
                    taken.forward = st
                    st.cancel = taken.cancel
                    taken.cancel = null
                    inflight += st
                } else {
                    start(st, src, b.src, w, key)
                }
            }
            (changed ?: ArrayList<ObjectSpan>().also { changed = it }) += o
        }
        if (asked) { // what no span of the new text took over is not wanted any more
            for (st in carriedLoads.values) { st.cancel?.invoke(); st.cancel = null; st.requested = null }
            carriedLoads.clear()
            if (inflight.isEmpty()) carry = null
        }
        return changed ?: emptyList()
    }

    private fun start(st: ImageState, src: ObjectImageSource, url: String, w: Int, key: String) {
        inflight += st
        st.cancel = src.load(url, w) { result ->
            val dest = st.resolve() // the state that waits for this load now (a rebuild may have passed it on)
            inflight.remove(dest)
            dest.cancel = null
            if (dest.requested != key) return@load // another picture was asked for since
            val span = dest.span ?: return@load
            when (result) {
                is ImageResult.Loaded -> {
                    dest.drawable = result.drawable
                    dest.bakedPercent = parseImageRef(url)?.wPercent ?: 100
                    dest.failed = false
                }
                ImageResult.Unavailable -> { dest.failed = true; dest.drawable = null }
            }
            deliver(span)
        }
    }
}
