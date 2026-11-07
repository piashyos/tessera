package app.tessera.ui.editor.components

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.util.Size
import app.tessera.data.images.NoteImageDecoder
import app.tessera.ui.editor.engine.ImageResult
import app.tessera.ui.editor.engine.ObjectImageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Builds a [NoteObjectImageSource] bound to a coroutine scope (the owning ViewModel's). */
class NoteObjectImageSourceFactory @Inject constructor(private val decoder: NoteImageDecoder) {
    fun create(scope: CoroutineScope): NoteObjectImageSource = NoteObjectImageSource(decoder, scope)
}

/**
 * [ObjectImageSource] over [NoteImageDecoder] (spec §3.1): all lookup and decoding lives in the decoder, this adds the memory cache and the
 * contract of the engine. The cache is an `LruCache` of drawables by `src + maxWidthPx`, bounded by bytes ([maxCacheBytes], by default an
 * eighth of the heap: a note of 20 screen-wide images). Loads run in [scope]; results are delivered on the main thread, once, never after a
 * cancel. The drawable is a `BitmapDrawable` of a `DENSITY_NONE` bitmap, so its intrinsic size is the pixel size on every screen density.
 */
class NoteObjectImageSource(
    private val decoder: NoteImageDecoder,
    private val scope: CoroutineScope,
    maxCacheBytes: Int = (Runtime.getRuntime().maxMemory() / 8).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
) : ObjectImageSource {

    private val cache = object : LruCache<String, BitmapDrawable>(maxCacheBytes) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int = value.bitmap.byteCount
    }

    override fun peek(src: String, maxWidthPx: Int): Drawable? = cache.get(key(src, maxWidthPx))

    override fun size(src: String, maxWidthPx: Int): Size? = decoder.cachedSize(src, maxWidthPx)

    override fun load(src: String, maxWidthPx: Int, onResult: (ImageResult) -> Unit): () -> Unit {
        var cancelled = false
        val job = scope.launch(Dispatchers.Main) {
            val result = when (val r = decoder.decode(src, maxWidthPx)) {
                is NoteImageDecoder.Result.Loaded -> {
                    r.bitmap.density = android.graphics.Bitmap.DENSITY_NONE
                    val d = BitmapDrawable(null, r.bitmap).apply { setBounds(0, 0, r.bitmap.width, r.bitmap.height) }
                    cache.put(key(src, maxWidthPx), d)
                    ImageResult.Loaded(d)
                }
                NoteImageDecoder.Result.Unavailable -> ImageResult.Unavailable
            }
            if (!cancelled) onResult(result)
        }
        return { cancelled = true; job.cancel() }
    }

    /** Raw bounds of the images of a note, read before the note is shown so the placeholders get their exact size ([size]). */
    suspend fun prefetchSizes(srcs: List<String>) = decoder.prefetchSizes(srcs)

    private fun key(src: String, maxWidthPx: Int) = "$src@$maxWidthPx"
}
