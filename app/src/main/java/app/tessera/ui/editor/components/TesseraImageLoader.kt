package app.tessera.ui.editor.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.NoteImageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.aztec.Html
import java.io.File
import javax.inject.Inject

/**
 * The downloader seam Aztec's image getter delegates to when an image has no local file yet.
 * Chunk 4 / Task 13 supplies the real Drive-backed downloader; until then the DI-provided
 * default returns `null`, which surfaces the "unavailable" placeholder.
 */
fun interface ImageDownloader {
    /** Fetch [imageId] to a local file, or return `null` when it can't be retrieved. */
    suspend fun download(imageId: String): File?
}

/** Builds a [TesseraImageLoader] bound to a coroutine scope (the owning ViewModel's). */
interface NoteImageLoaderFactory {
    fun create(scope: CoroutineScope): TesseraImageLoader
}

class DefaultNoteImageLoaderFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteImageDao: NoteImageDao,
    private val downloader: ImageDownloader,
) : NoteImageLoaderFactory {
    override fun create(scope: CoroutineScope): TesseraImageLoader =
        TesseraImageLoader(
            noteImageDao = noteImageDao,
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            downloader = downloader,
            scope = scope,
        )
}

/**
 * Aztec [Html.ImageGetter] for `tessera-img://<id>` sources. Resolves a drawable off the main
 * thread and delivers it on the supplied [Callbacks]:
 *
 *  1. Non-matching source → [Callbacks.onImageFailed].
 *  2. Room row's `localPath` file exists → decode downsampled to `maxWidth`.
 *  3. Else `cacheDir/images/<id>.webp` exists → same.
 *  4. Else loading placeholder + delegate to [downloader] (null until Chunk 4 → "unavailable").
 *  5. Failure → "unavailable" placeholder.
 *
 * The persistent "not synced" badge is deferred to v1.1; placeholders here are plain tinted tiles.
 */
class TesseraImageLoader(
    private val noteImageDao: NoteImageDao,
    private val filesDir: File,
    private val cacheDir: File,
    private val downloader: ImageDownloader,
    private val scope: CoroutineScope,
) : Html.ImageGetter {

    override fun loadImage(source: String, callbacks: Html.ImageGetter.Callbacks, maxWidth: Int) {
        loadImage(source, callbacks, maxWidth, 0)
    }

    override fun loadImage(
        source: String,
        callbacks: Html.ImageGetter.Callbacks,
        maxWidth: Int,
        minWidth: Int,
    ) {
        val imageId = parseImageId(source)
        if (imageId == null) {
            callbacks.onImageFailed()
            return
        }
        val width = maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH
        scope.launch {
            val row = runCatching { noteImageDao.byId(imageId) }.getOrNull()
            // Local file already on disk → decode and deliver.
            val local = localFileFor(imageId, row)
            if (local != null) {
                val drawable = withContext(Dispatchers.IO) { decode(local, width, row) }
                if (drawable != null) {
                    callbacks.onImageLoaded(drawable)
                    return@launch
                }
            }
            // Not local yet → show a loading tile and ask the downloader seam.
            callbacks.onImageLoading(placeholder(width, row, PLACEHOLDER_LOADING))
            val fetched = runCatching { downloader.download(imageId) }.getOrNull()
            val drawable = fetched?.let { f -> withContext(Dispatchers.IO) { decode(f, width, row) } }
            if (drawable != null) {
                callbacks.onImageLoaded(drawable)
            } else {
                callbacks.onImageLoaded(placeholder(width, row, PLACEHOLDER_UNAVAILABLE))
            }
        }
    }

    /**
     * Resolve [imageId] to a decoded local drawable for the immediate insert-at-caret path, or
     * `null` if no local file is available. Runs its disk work off the main thread.
     */
    suspend fun resolve(imageId: String, maxWidth: Int): Drawable? {
        val width = maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH
        val row = runCatching { noteImageDao.byId(imageId) }.getOrNull()
        val local = localFileFor(imageId, row) ?: return null
        return withContext(Dispatchers.IO) { decode(local, width, row) }
    }

    /** A tinted tile matching the insert path's placeholder, sized from [maxWidth]/[row]. */
    fun loadingPlaceholder(maxWidth: Int, row: NoteImageEntity?): Drawable =
        placeholder(maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH, row, PLACEHOLDER_LOADING)

    private fun localFileFor(imageId: String, row: NoteImageEntity?): File? {
        row?.localPath?.let { path ->
            val f = File(path)
            if (f.exists()) return f
        }
        val cached = File(File(cacheDir, IMAGES_DIR), "$imageId.webp")
        if (cached.exists()) return cached
        // Also honour the canonical filesDir/images/<id>.webp the repository writes to.
        val stored = File(File(filesDir, IMAGES_DIR), "$imageId.webp")
        return stored.takeIf { it.exists() }
    }

    private fun decode(file: File, maxWidth: Int, row: NoteImageEntity?): Drawable? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth.takeIf { it > 0 } ?: row?.width ?: return null
        val srcH = bounds.outHeight.takeIf { it > 0 } ?: row?.height ?: srcW
        var sample = 1
        while (srcW / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val drawable = BitmapDrawable(null, bitmap)
        setDisplayBounds(drawable, srcW, srcH, maxWidth)
        return drawable
    }

    private fun placeholder(maxWidth: Int, row: NoteImageEntity?, tint: Int): Drawable {
        val drawable = ColorDrawable(tint)
        val srcW = row?.width?.takeIf { it > 0 } ?: maxWidth
        val srcH = row?.height?.takeIf { it > 0 } ?: (maxWidth * 9 / 16)
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

    private fun parseImageId(source: String): String? {
        if (!source.startsWith(SCHEME)) return null
        val id = source.removePrefix(SCHEME)
        return id.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val SCHEME = "tessera-img://"
        const val IMAGES_DIR = "images"
        const val DEFAULT_MAX_WIDTH = 1080

        // Simple tinted tiles for v1 (no theme access here). Loading = faint grey scrim;
        // unavailable = faint red scrim. The persistent "not synced" badge is deferred to v1.1.
        val PLACEHOLDER_LOADING: Int = Color.argb(20, 128, 128, 128)
        val PLACEHOLDER_UNAVAILABLE: Int = Color.argb(30, 200, 80, 80)
    }
}
