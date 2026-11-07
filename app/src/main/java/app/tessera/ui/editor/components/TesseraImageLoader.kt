package app.tessera.ui.editor.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import app.tessera.data.images.ImageDownloader
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.domain.model.parseImageRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.wordpress.aztec.Html
import java.io.File
import javax.inject.Inject

/** Builds a [TesseraImageLoader] bound to a coroutine scope (the owning ViewModel's). */
interface NoteImageLoaderFactory {
    fun create(scope: CoroutineScope): TesseraImageLoader
}

class DefaultNoteImageLoaderFactory @Inject constructor(
    private val decoder: NoteImageDecoder,
) : NoteImageLoaderFactory {
    override fun create(scope: CoroutineScope): TesseraImageLoader = TesseraImageLoader(decoder, scope)
}

/**
 * Aztec [Html.ImageGetter] for `tessera-img://<id>` sources: a thin wrapper over [NoteImageDecoder]
 * (all source lookup and decoding lives there) that adds the Aztec callbacks and placeholder tiles.
 *
 *  1. Non-matching source → [Callbacks.onImageFailed].
 *  2. Local file present → decoded drawable, delivered synchronously.
 *  3. Else loading placeholder, then async decode (Room `localPath` / cache / Drive).
 *  4. Failure → "unavailable" placeholder.
 *
 * The persistent "not synced" badge is deferred to v1.1; placeholders here are plain tinted tiles.
 */
class TesseraImageLoader(
    private val decoder: NoteImageDecoder,
    private val scope: CoroutineScope,
) : Html.ImageGetter {

    constructor(
        noteImageDao: NoteImageDao,
        filesDir: File,
        cacheDir: File,
        downloader: ImageDownloader,
        scope: CoroutineScope,
    ) : this(NoteImageDecoder(noteImageDao, filesDir, cacheDir, downloader), scope)

    override fun loadImage(source: String, callbacks: Html.ImageGetter.Callbacks, maxWidth: Int) {
        loadImage(source, callbacks, maxWidth, 0)
    }

    override fun loadImage(
        source: String,
        callbacks: Html.ImageGetter.Callbacks,
        maxWidth: Int,
        minWidth: Int,
    ) {
        if (parseImageRef(source) == null) {
            callbacks.onImageFailed()
            return
        }
        val width = maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH

        // SYNCHRONOUS local path. Aztec calls loadImage() from fromHtml/setText BEFORE the view is
        // measured, then measures the image span once. A drawable delivered LATER (async) only calls
        // setDrawable + post{invalidate} — it does NOT re-measure the text — so the span keeps the
        // stale placeholder size and the photo renders as a small tile. Delivering the local drawable
        // synchronously here makes the span carry the real drawable at first measure — exactly like
        // the insert-at-caret path that already renders correctly.
        decoder.decodeLocal(source, width)?.let {
            callbacks.onImageLoaded(drawableOf(it))
            return
        }

        // No local file (recipient before download) → loading tile, then fetch. A downloaded image
        // renders at the placeholder size until the note is reopened (which then hits the synchronous
        // path above); acceptable for v1.
        callbacks.onImageLoading(placeholder(width, null, PLACEHOLDER_LOADING))
        scope.launch {
            when (val result = decoder.decode(source, width)) {
                is NoteImageDecoder.Result.Loaded -> callbacks.onImageLoaded(drawableOf(result.bitmap))
                NoteImageDecoder.Result.Unavailable ->
                    callbacks.onImageLoaded(placeholder(width, decoder.storedSize(source), PLACEHOLDER_UNAVAILABLE))
            }
        }
    }

    /**
     * Resolve a `tessera-img://<id>?w=..&rot=..` [source] to a decoded local drawable for the
     * immediate insert-at-caret and resize/rotate re-decode paths, or `null` if the ref is invalid
     * or no local file is available (never downloads). Applies the source's `w`/`rot`.
     */
    suspend fun resolve(source: String, maxWidth: Int): Drawable? {
        val result = decoder.decode(source, maxWidth, download = false)
        return (result as? NoteImageDecoder.Result.Loaded)?.let { drawableOf(it.bitmap) }
    }

    /** A tinted tile matching the insert path's placeholder, sized from [maxWidth]/[row]. */
    fun loadingPlaceholder(maxWidth: Int, row: NoteImageEntity?): Drawable =
        placeholder(
            maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH,
            row?.let { it.width to it.height },
            PLACEHOLDER_LOADING,
        )

    private fun drawableOf(bitmap: Bitmap): Drawable =
        BitmapDrawable(null, bitmap).apply {
            // setBounds is harmless (and correct for the brief pre-measurement window); the intrinsic
            // size is what sticks post-measure.
            setBounds(0, 0, bitmap.width, bitmap.height)
        }

    private fun placeholder(maxWidth: Int, size: Pair<Int, Int>?, tint: Int): Drawable {
        val drawable = ColorDrawable(tint)
        val srcW = size?.first?.takeIf { it > 0 } ?: maxWidth
        val srcH = size?.second?.takeIf { it > 0 } ?: (maxWidth * 9 / 16)
        setDisplayBounds(drawable, srcW, srcH, maxWidth)
        return drawable
    }

    /** Scale (w,h) down to fit [maxWidth] and stamp the result onto [drawable]'s bounds. */
    private fun setDisplayBounds(drawable: Drawable, srcW: Int, srcH: Int, maxWidth: Int) {
        val w: Int
        val h: Int
        if (srcW > maxWidth) {
            w = maxWidth
            h = (srcH.toLong() * maxWidth / srcW).toInt().coerceAtLeast(1)
        } else {
            w = srcW.coerceAtLeast(1)
            h = srcH.coerceAtLeast(1)
        }
        drawable.setBounds(0, 0, w, h)
    }

    private companion object {
        const val DEFAULT_MAX_WIDTH = 1080

        // Simple tinted tiles for v1 (no theme access here). Loading = faint grey scrim;
        // unavailable = faint red scrim. The persistent "not synced" badge is deferred to v1.1.
        val PLACEHOLDER_LOADING: Int = Color.argb(20, 128, 128, 128)
        val PLACEHOLDER_UNAVAILABLE: Int = Color.argb(30, 200, 80, 80)
    }
}
