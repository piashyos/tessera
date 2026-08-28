package app.tessera.ui.editor.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import app.tessera.data.images.ImageDownloader
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

/** Parsed `tessera-img` reference: bare id plus optional display params. */
data class ImageRef(val id: String, val wPercent: Int, val rotDegrees: Int)

private const val TESSERA_IMG_SCHEME = "tessera-img://"
private val VALID_ROT = setOf(0, 90, 180, 270)

/**
 * Parse `tessera-img://<id>?w=..&rot=..`; null when [source] isn't a tessera-img ref or has a
 * blank id. Defaults `w=100`, `rot=0`. `w` is clamped to 10..100; `rot` must be one of
 * {0,90,180,270} else it falls back to 0. Tolerates a malformed query without crashing.
 */
fun parseImageRef(source: String): ImageRef? {
    if (!source.startsWith(TESSERA_IMG_SCHEME)) return null
    val rest = source.removePrefix(TESSERA_IMG_SCHEME)
    val id = rest.substringBefore('?')
    if (id.isBlank()) return null
    // Aztec's toHtml hands the query back with `&amp;` (HTML-escaped); decode so splitting works
    // whether the source came from stored markdown (raw `&`) or from editor.toHtml (`&amp;`).
    val query = rest.substringAfter('?', "").replace("&amp;", "&")
    val params = query.split('&').mapNotNull {
        val k = it.substringBefore('=', "")
        val v = it.substringAfter('=', "")
        if (k.isEmpty()) null else k to v
    }.toMap()
    val w = params["w"]?.toIntOrNull()?.coerceIn(10, 100) ?: 100
    val rot = params["rot"]?.toIntOrNull()?.takeIf { it in VALID_ROT } ?: 0
    return ImageRef(id, w, rot)
}

/** The bare id of a tessera-img ref, or null when [source] isn't one. */
fun parseImageId(source: String): String? = parseImageRef(source)?.id

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
        val ref = parseImageRef(source)
        if (ref == null) {
            callbacks.onImageFailed()
            return
        }
        val imageId = ref.id
        val width = maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH

        // SYNCHRONOUS local path. Aztec calls loadImage() from fromHtml/setText BEFORE the view is
        // measured, then measures the image span once. A drawable delivered LATER (async) only calls
        // setDrawable + post{invalidate} — it does NOT re-measure the text — so the span keeps the
        // stale placeholder size and the photo renders as a small tile. Delivering the local drawable
        // synchronously here (the file lives at filesDir|cacheDir/images/<id>.webp by convention, so
        // no suspend DB read is needed) makes the span carry the real drawable at first measure —
        // exactly like the insert-at-caret path that already renders correctly.
        val localFile = conventionLocalFile(imageId)
        if (localFile != null) {
            val drawable = decode(localFile, width, null, ref)
            if (drawable != null) {
                callbacks.onImageLoaded(drawable)
                return
            }
        }

        // No local file (recipient before download) → loading tile, then fetch via the downloader.
        // A downloaded image renders at the placeholder size until the note is reopened (which then
        // hits the synchronous path above); acceptable for v1.
        callbacks.onImageLoading(placeholder(width, null, PLACEHOLDER_LOADING))
        scope.launch {
            val row = runCatching { noteImageDao.byId(imageId) }.getOrNull()
            val fetched = runCatching { downloader.download(imageId) }.getOrNull()
            val drawable = fetched?.let { f -> withContext(Dispatchers.IO) { decode(f, width, row, ref) } }
            if (drawable != null) {
                callbacks.onImageLoaded(drawable)
            } else {
                callbacks.onImageLoaded(placeholder(width, row, PLACEHOLDER_UNAVAILABLE))
            }
        }
    }

    /** The local `<id>.webp` under filesDir (repository writes) or cacheDir (downloader writes), if present. */
    private fun conventionLocalFile(imageId: String): File? {
        val stored = File(File(filesDir, IMAGES_DIR), "$imageId.webp")
        if (stored.exists()) return stored
        val cached = File(File(cacheDir, IMAGES_DIR), "$imageId.webp")
        return cached.takeIf { it.exists() }
    }

    /**
     * Resolve a `tessera-img://<id>?w=..&rot=..` [source] to a decoded local drawable for the
     * immediate insert-at-caret and resize/rotate re-decode paths, or `null` if the ref is invalid
     * or no local file is available. Applies the source's `w`/`rot`. Runs disk work off the main thread.
     */
    suspend fun resolve(source: String, maxWidth: Int): Drawable? {
        val ref = parseImageRef(source) ?: return null
        val width = maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH
        val row = runCatching { noteImageDao.byId(ref.id) }.getOrNull()
        val local = localFileFor(ref.id, row) ?: return null
        return withContext(Dispatchers.IO) { decode(local, width, row, ref) }
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

    private fun decode(file: File, maxWidth: Int, row: NoteImageEntity?, ref: ImageRef): Drawable? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val srcW = bounds.outWidth.takeIf { it > 0 } ?: row?.width ?: return null
        val srcH = bounds.outHeight.takeIf { it > 0 } ?: row?.height ?: srcW
        var sample = 1
        while (srcW / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

        // Rotate first so w% applies to the rotated orientation.
        if (ref.rotDegrees != 0) {
            val matrix = Matrix().apply { postRotate(ref.rotDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val rotatedW = bitmap.width
        val rotatedH = bitmap.height

        // Size by SCALING THE BITMAP — Aztec's adjustBounds recomputes span width from the drawable's
        // intrinsic size after measurement (min(intrinsicWidth, paragraphWidth)), IGNORING setBounds
        // (verified by AztecImageParamSpikeTest). w=100 maps to the width the image would render at
        // untreated (min(native, maxWidth)); w<100 scales down from there.
        val defaultWidth = minOf(rotatedW, maxWidth)
        val targetWidth = Math.round(ref.wPercent / 100f * defaultWidth).coerceAtLeast(1)
        val targetHeight = Math.round(targetWidth * rotatedH.toFloat() / rotatedW).coerceAtLeast(1)
        if (targetWidth != rotatedW) {
            bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }
        // DENSITY_NONE makes BitmapDrawable(null, bitmap).intrinsicWidth == the raw pixel width, so
        // the on-screen span width == targetWidth on EVERY device density (a decoded bitmap otherwise
        // carries the device density and Aztec would render it at width*160/density — 1/3 on xxhdpi;
        // see AztecImageParamSpikeTest.currentDecodePathShrinksByDensity_documented).
        bitmap.density = Bitmap.DENSITY_NONE
        val drawable = BitmapDrawable(null, bitmap)
        // setBounds is harmless (and correct for the brief pre-measurement window); the intrinsic
        // size is what sticks post-measure.
        drawable.setBounds(0, 0, targetWidth, targetHeight)
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

    private companion object {
        const val IMAGES_DIR = "images"
        const val DEFAULT_MAX_WIDTH = 1080

        // Simple tinted tiles for v1 (no theme access here). Loading = faint grey scrim;
        // unavailable = faint red scrim. The persistent "not synced" badge is deferred to v1.1.
        val PLACEHOLDER_LOADING: Int = Color.argb(20, 128, 128, 128)
        val PLACEHOLDER_UNAVAILABLE: Int = Color.argb(30, 200, 80, 80)
    }
}
