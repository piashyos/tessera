package app.tessera.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageCompressorTest {
    private lateinit var compressor: ImageCompressor
    private lateinit var workDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compressor = ImageCompressor(context)
        workDir = File(context.cacheDir, "image-compressor-test").apply { mkdirs() }
    }

    private fun writeSourceJpeg(name: String, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val file = File(workDir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    private fun targetFile(name: String): File = File(workDir, name)

    @Test
    fun `caps long side at 2048 for landscape source`() = runTest {
        val source = writeSourceJpeg("landscape.jpg", 3000, 1500)
        val target = targetFile("landscape-out.webp")

        val result = compressor.compress(Uri.fromFile(source), target)

        assertEquals(2048, result.width)
        assertEquals(1024, result.height)
    }

    @Test
    fun `caps long side at 2048 for portrait source`() = runTest {
        val source = writeSourceJpeg("portrait.jpg", 1500, 3000)
        val target = targetFile("portrait-out.webp")

        val result = compressor.compress(Uri.fromFile(source), target)

        assertEquals(1024, result.width)
        assertEquals(2048, result.height)
    }

    @Test
    fun `does not upscale small images`() = runTest {
        val source = writeSourceJpeg("small.jpg", 100, 100)
        val target = targetFile("small-out.webp")

        val result = compressor.compress(Uri.fromFile(source), target)

        assertEquals(100, result.width)
        assertEquals(100, result.height)
    }

    @Test
    fun `applies EXIF rotation of 90 degrees`() = runTest {
        val source = writeSourceJpeg("rotated.jpg", 200, 100)
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val target = targetFile("rotated-out.webp")

        val result = compressor.compress(Uri.fromFile(source), target)

        assertEquals(100, result.width)
        assertEquals(200, result.height)
    }

    @Test
    fun `writes a decodable non-empty webp file`() = runTest {
        val source = writeSourceJpeg("basic.jpg", 400, 300)
        val target = targetFile("basic-out.webp")

        compressor.compress(Uri.fromFile(source), target)

        assertTrue(target.exists())
        assertTrue(target.length() > 0)
        val decoded = BitmapFactory.decodeFile(target.path)
        assertNotNull(decoded)
    }
}
