package app.tessera.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Runs on a real device so it uses the real [android.graphics.BitmapFactory] (Robolectric's shadow
 * returns a non-null bitmap in `inJustDecodeBounds` mode, which hid the original defect where
 * [ImageCompressor.readBounds] folded the bounds-mode null bitmap into the stream null-check and
 * threw "Unable to open input stream" on EVERY image insert). This is the regression guard for
 * "image does not insert into the editor".
 */
@RunWith(AndroidJUnit4::class)
class ImageCompressorInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sourceJpeg(width: Int, height: Int): Uri {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val file = File(context.cacheDir, "src_${width}x${height}.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun compress_realDevice_producesWebpWithoutThrowing() = runBlocking {
        val compressor = ImageCompressor(context)
        val target = File(context.cacheDir, "out.webp")

        val result = compressor.compress(sourceJpeg(1200, 800), target)

        assertTrue("target webp must exist and be non-empty", target.exists() && target.length() > 0)
        assertEquals(1200, result.width)
        assertEquals(800, result.height)
    }

    @Test
    fun compress_capsLongSideAt2048() = runBlocking {
        val compressor = ImageCompressor(context)
        val target = File(context.cacheDir, "out_big.webp")

        val result = compressor.compress(sourceJpeg(4096, 2048), target)

        assertEquals(2048, result.width)
        assertEquals(1024, result.height)
    }
}
