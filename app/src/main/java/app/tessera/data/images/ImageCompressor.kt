package app.tessera.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes an image from [Uri], applies EXIF rotation, caps the long side at [MAX_LONG_SIDE]
 * (never upscaling), and encodes the result as WebP at [target].
 */
@Singleton
class ImageCompressor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        data class Compressed(val file: File, val width: Int, val height: Int)

        suspend fun compress(
            source: Uri,
            target: File,
        ): Compressed =
            withContext(Dispatchers.Default) {
                val bounds = readBounds(source)
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_LONG_SIDE)

                val rawBitmap = decodeSampled(source, sampleSize)
                val rotationDegrees = readRotationDegrees(source)
                val orientedBitmap = applyRotation(rawBitmap, rotationDegrees)
                val finalBitmap = downscaleIfNeeded(orientedBitmap, MAX_LONG_SIDE)

                writeWebp(finalBitmap, target)

                Compressed(target, finalBitmap.width, finalBitmap.height)
            }

        private fun readBounds(source: Uri): BitmapFactory.Options {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // NOTE: with inJustDecodeBounds=true, decodeStream ALWAYS returns null and only fills
            // `options` — so we must null-check the STREAM itself, not the decode result. Folding the
            // two together (`openInputStream(...)?.use { decodeStream(...) } ?: throw`) throws on every
            // real device even when the stream opened fine. (Robolectric's shadow returns a non-null
            // bitmap in bounds mode, which is why this slipped past the unit tests.)
            val stream = context.contentResolver.openInputStream(source)
                ?: throw IOException("Unable to open input stream for $source")
            stream.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                throw IOException("Unable to decode image bounds for $source")
            }
            return options
        }

        private fun calculateInSampleSize(
            width: Int,
            height: Int,
            maxLongSide: Int,
        ): Int {
            var sampleSize = 1
            var longSide = maxOf(width, height)
            while (longSide / 2 >= maxLongSide) {
                sampleSize *= 2
                longSide /= 2
            }
            return sampleSize
        }

        private fun decodeSampled(
            source: Uri,
            sampleSize: Int,
        ): Bitmap {
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap =
                context.contentResolver.openInputStream(source)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                } ?: throw IOException("Unable to open input stream for $source")
            return bitmap ?: throw IOException("Unable to decode image for $source")
        }

        private fun readRotationDegrees(source: Uri): Int =
            context.contentResolver.openInputStream(source)?.use { input ->
                ExifInterface(input).rotationDegrees
            } ?: 0

        private fun applyRotation(
            bitmap: Bitmap,
            degrees: Int,
        ): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        }

        private fun downscaleIfNeeded(
            bitmap: Bitmap,
            maxLongSide: Int,
        ): Bitmap {
            val longSide = maxOf(bitmap.width, bitmap.height)
            if (longSide <= maxLongSide) return bitmap

            val scale = maxLongSide.toFloat() / longSide
            val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaled !== bitmap) bitmap.recycle()
            return scaled
        }

        @Suppress("DEPRECATION")
        private fun writeWebp(
            bitmap: Bitmap,
            target: File,
        ) {
            target.parentFile?.mkdirs()
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            FileOutputStream(target).use { output ->
                bitmap.compress(format, WEBP_QUALITY, output)
            }
        }

        companion object {
            private const val MAX_LONG_SIDE = 2048
            private const val WEBP_QUALITY = 80
        }
    }
