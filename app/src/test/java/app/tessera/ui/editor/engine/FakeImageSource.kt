package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Size

/**
 * An [ObjectImageSource] a test drives by hand: what is in memory ([cache], by `src@width`), the sizes known before the decode ([sizes], by `src`),
 * and every load that was asked for, held until the test delivers a result with [deliver]. A result can also be delivered to a load that was
 * cancelled or to one that was answered already: the engine must throw such a result away, whatever the contract says about cancelling.
 */
internal class FakeImageSource(private val remembersResults: Boolean = false) : ObjectImageSource {
    class Request(val src: String, val maxWidthPx: Int, val onResult: (ImageResult) -> Unit) {
        var cancelled = false
        var answered = false
    }

    val cache = HashMap<String, Drawable>()
    val sizes = HashMap<String, Size>()
    val requests = ArrayList<Request>()
    val peeked = ArrayList<String>()

    /** Loads that are alive: neither cancelled nor answered. */
    val pending: List<Request> get() = requests.filter { !it.cancelled && !it.answered }

    override fun peek(src: String, maxWidthPx: Int): Drawable? { peeked += "$src@$maxWidthPx"; return cache["$src@$maxWidthPx"] }
    override fun size(src: String, maxWidthPx: Int): Size? = sizes[src]
    override fun load(src: String, maxWidthPx: Int, onResult: (ImageResult) -> Unit): () -> Unit {
        val r = Request(src, maxWidthPx, onResult)
        requests += r
        return { r.cancelled = true }
    }

    fun deliver(r: Request, result: ImageResult) {
        r.answered = true
        if (remembersResults && result is ImageResult.Loaded) cache["${r.src}@${r.maxWidthPx}"] = result.drawable // as the real source's memory cache does
        r.onResult(result)
    }

    companion object {
        /** A picture of [w]×[h] px, `DENSITY_NONE`: its intrinsic size is the pixel size. */
        fun drawable(w: Int, h: Int): Drawable =
            BitmapDrawable(null, Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE })
    }
}
