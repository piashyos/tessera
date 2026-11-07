package app.tessera.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.domain.model.ImageRef
import app.tessera.domain.model.parseImageRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Decodes a note image (`tessera-img://<id>?w=..&rot=..`) into a ready-to-draw [Bitmap]. No UI types.
 *
 * Sources, in order: Room row's `localPath` → `cacheDir/images/<id>.webp` → `filesDir/images/<id>.webp`
 * → Drive via [downloader]. `w`/`rot` are baked into the bitmap: rotate first, then
 * width = `min(rotated width, maxWidth) × w%`. Aztec's span width is `min(drawable.intrinsicWidth,
 * paragraphWidth)` and ignores `setBounds`, hence scaling the pixels; the bitmap density is
 * [Bitmap.DENSITY_NONE] so the intrinsic width is the raw pixel width on every device density.
 */
class NoteImageDecoder(
    private val noteImageDao: NoteImageDao,
    private val filesDir: File,
    private val cacheDir: File,
    private val downloader: ImageDownloader,
) {
    /** Raw file bounds by image id (w/rot/maxWidth are applied on read). */
    private val rawSizes = ConcurrentHashMap<String, Size>()

    sealed interface Result {
        data class Loaded(val bitmap: Bitmap) : Result
        data object Unavailable : Result
    }

    /**
     * Decode [src]; [Result.Unavailable] when the ref is invalid, no source yields a file, or the
     * file can't be decoded. [download] = false skips the Drive fallback (local files only).
     * Disk work runs on IO; cancellation propagates.
     */
    suspend fun decode(src: String, maxWidthPx: Int, download: Boolean = true): Result {
        val ref = parseImageRef(src) ?: return Result.Unavailable
        val row = attempt { noteImageDao.byId(ref.id) }
        suspend fun decodeIo(file: File) = withContext(Dispatchers.IO) { fileDecoder(file, maxWidthPx, row, ref) }
        // A local file that exists but won't decode (e.g. a truncated download) must not pin the image
        // to "unavailable": fall through to Drive, whose download rewrites the cache file.
        var bitmap = localFile(ref.id, row)?.let { decodeIo(it) }
        if (bitmap == null && download) {
            bitmap = attempt { downloader.download(ref.id) }?.let { decodeIo(it) }
        }
        return bitmap?.let { Result.Loaded(it) } ?: Result.Unavailable
    }

    /**
     * Partial-image rejection via [ImageDecoder] (API 28+). Test seam: Robolectric's ImageDecoder shadow
     * can't decode (returns nothing), so JVM tests turn this off and exercise the BitmapFactory path;
     * the ImageDecoder path itself is verified on device (NativeImageSizeTest).
     */
    internal var strictDecoding: Boolean = Build.VERSION.SDK_INT >= 28

    /** Decode seam (file, maxWidth, row, ref → baked bitmap or null); tests swap it to simulate a corrupt file. */
    internal var fileDecoder: (File, Int, NoteImageEntity?, ImageRef) -> Bitmap? = ::decodeFile

    /**
     * Reads ONLY the bounds (`inJustDecodeBounds`) of the local/cached files behind [srcs] and stores
     * the RAW (outWidth, outHeight) by image id, so no layout width is needed yet; `w`/`rot` and
     * maxWidth are applied by [cachedSize]. Never touches Drive; sources without a local file or
     * readable bounds get no entry. Runs on IO.
     */
    suspend fun prefetchSizes(srcs: List<String>) {
        for (src in srcs) {
            val ref = parseImageRef(src) ?: continue
            val row = attempt { noteImageDao.byId(ref.id) }
            val file = localFile(ref.id, row) ?: continue
            val raw = withContext(Dispatchers.IO) { rawBounds(file) } ?: continue
            rawSizes[ref.id] = raw
        }
    }

    /**
     * Baked size of [src] at [maxWidthPx] from raw bounds left by [prefetchSizes] or a previous
     * decode (same formula as [decode], via [finalSize]); null without an entry. No IO.
     */
    fun cachedSize(src: String, maxWidthPx: Int): Size? {
        val ref = parseImageRef(src) ?: return null
        val raw = rawSizes[ref.id] ?: return null
        return finalSize(raw.width, raw.height, ref, effectiveWidth(maxWidthPx))
    }

    private fun effectiveWidth(maxWidth: Int) = maxWidth.takeIf { it > 0 } ?: DEFAULT_MAX_WIDTH

    private fun rawBounds(file: File): Size? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) Size(bounds.outWidth, bounds.outHeight) else null
    }

    /**
     * Blocking decode of the conventional local file only (no Room, no Drive), or null. For callers
     * that must deliver synchronously (Aztec measures its image span once, right after fromHtml).
     */
    fun decodeLocal(src: String, maxWidthPx: Int): Bitmap? {
        val ref = parseImageRef(src) ?: return null
        val file = localFile(ref.id, null) ?: return null
        return fileDecoder(file, maxWidthPx, null, ref)
    }

    /** Stored (width, height) of [src]'s Room row, for sizing placeholders; null when unknown. */
    suspend fun storedSize(src: String): Pair<Int, Int>? {
        val ref = parseImageRef(src) ?: return null
        val row = attempt { noteImageDao.byId(ref.id) } ?: return null
        return row.width to row.height
    }

    /** Run a Room/Drive call; any failure but cancellation means "no result". */
    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun localFile(imageId: String, row: NoteImageEntity?): File? {
        row?.localPath?.let { path ->
            val f = File(path)
            if (f.exists()) return f
        }
        val cached = File(File(cacheDir, IMAGES_DIR), "$imageId.webp")
        if (cached.exists()) return cached
        // Also honour the canonical filesDir/images/<id>.webp the repository writes to.
        return File(File(filesDir, IMAGES_DIR), "$imageId.webp").takeIf { it.exists() }
    }

    private fun decodeFile(file: File, maxWidth: Int, row: NoteImageEntity?, ref: ImageRef): Bitmap? {
        val width = effectiveWidth(maxWidth)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val hasBounds = bounds.outWidth > 0 && bounds.outHeight > 0
        if (hasBounds) rawSizes[ref.id] = Size(bounds.outWidth, bounds.outHeight)
        // Bounds unreadable → fall back to the Room row's dimensions (sizing only; the size below is then
        // taken from the decoded bitmap).
        val rawW = if (hasBounds) bounds.outWidth else row?.width ?: return null
        val rawH = if (hasBounds) bounds.outHeight else row?.height ?: return null
        var bitmap = decodeSampled(file, sampleSize(rawW, rawH, ref, width)) ?: return null

        // Rotate first so w% applies to the rotated orientation.
        if (ref.rotDegrees != 0) {
            val matrix = Matrix().apply { postRotate(ref.rotDegrees.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        // Size by construction: with bounds, land exactly on finalSize (what prefetch/cachedSize promised)
        // however the codec rounded the sampled dimensions.
        val target = if (hasBounds) finalSize(rawW, rawH, ref, width) else bakedSize(bitmap.width, bitmap.height, width, ref.wPercent)
        if (target.width != bitmap.width || target.height != bitmap.height) {
            bitmap = Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
        }
        bitmap.density = Bitmap.DENSITY_NONE
        return bitmap
    }

    /**
     * Decode [file] downsampled by [sample]; null when it can't be decoded. On API 28+ via
     * [ImageDecoder] with partial results rejected: a truncated file (BitmapFactory would hand back a
     * half-painted bitmap) counts as undecodable, so the caller falls through to Drive.
     */
    private fun decodeSampled(file: File, sample: Int): Bitmap? {
        if (strictDecoding) return decodeSampledStrict(file, sample)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    @RequiresApi(28)
    private fun decodeSampledStrict(file: File, sample: Int): Bitmap? =
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                // Software: the bitmap is rotated and scaled afterwards, which a HARDWARE bitmap can't be.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sample)
                decoder.setOnPartialImageListener { false }
            }
        } catch (_: Exception) {
            null
        }

    internal companion object {
        private const val IMAGES_DIR = "images"
        private const val DEFAULT_MAX_WIDTH = 1080

        /** Power-of-two downsample so the ROTATED width stays >= [width] (rot 90/270 swap raw w/h). */
        fun sampleSize(rawW: Int, rawH: Int, ref: ImageRef, width: Int): Int {
            val rotatedW = if (ref.rotDegrees == 90 || ref.rotDegrees == 270) rawH else rawW
            var sample = 1
            while (rotatedW / (sample * 2) >= width) sample *= 2
            return sample
        }

        /**
         * THE size formula: raw file bounds + [ref] + [maxWidth] → baked size. decodeFile scales its
         * bitmap to exactly this: sample, rotate (sampled dimensions rounded up), then
         * width = min(rotated width, maxWidth) × w%, height keeps the rotated aspect.
         */
        fun finalSize(rawW: Int, rawH: Int, ref: ImageRef, maxWidth: Int): Size {
            val sample = sampleSize(rawW, rawH, ref, maxWidth)
            val w = (rawW + sample - 1) / sample
            val h = (rawH + sample - 1) / sample
            val swap = ref.rotDegrees == 90 || ref.rotDegrees == 270
            return bakedSize(if (swap) h else w, if (swap) w else h, maxWidth, ref.wPercent)
        }

        /** Width = min(rotated width, maxWidth) × w%, height keeps the rotated aspect. */
        fun bakedSize(rotatedW: Int, rotatedH: Int, maxWidth: Int, wPercent: Int): Size {
            val targetWidth = Math.round(wPercent / 100f * minOf(rotatedW, maxWidth)).coerceAtLeast(1)
            val targetHeight = Math.round(targetWidth * rotatedH.toFloat() / rotatedW).coerceAtLeast(1)
            return Size(targetWidth, targetHeight)
        }
    }
}
