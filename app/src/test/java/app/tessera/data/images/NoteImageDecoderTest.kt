package app.tessera.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.domain.model.parseImageRef
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException

/**
 * Decoder tests run on Robolectric's BitmapFactory shadow, which is lenient (it returns a non-null
 * bitmap in `inJustDecodeBounds` mode and for undecodable bytes — memory
 * `robolectric_bitmapfactory_bounds_mode`). They cover source order, w/rot/maxWidth maths, refusal
 * and cancellation. Real-codec behaviour is listed for on-device QA in the P2-1a report.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteImageDecoderTest {

    private lateinit var context: Context
    private var row: NoteImageEntity? = null
    private var downloader: ImageDownloader = ImageDownloader { null }
    private var downloads = 0

    private val dao = object : NoteImageDao {
        override suspend fun byId(imageId: String): NoteImageEntity? = row
        override suspend fun upsert(image: NoteImageEntity) = Unit
        override suspend fun byNote(noteId: String): List<NoteImageEntity> = emptyList()
        override suspend fun byOwnerAndStatus(uid: String, status: ImageStatus): List<NoteImageEntity> = emptyList()
        override suspend fun byOwner(uid: String): List<NoteImageEntity> = emptyList()
        override suspend fun all(): List<NoteImageEntity> = emptyList()
        override suspend fun backfillOwner(uid: String) = Unit
        override suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus) = Unit
        override suspend fun delete(imageId: String) = Unit
    }

    private fun decoder() = NoteImageDecoder(
        noteImageDao = dao,
        filesDir = context.filesDir,
        cacheDir = context.cacheDir,
        downloader = { id -> downloads++; downloader.download(id) },
    ).apply { strictDecoding = false } // Robolectric can't run ImageDecoder; see NoteImageDecoder.strictDecoding

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "images").deleteRecursively()
        File(context.cacheDir, "images").deleteRecursively()
        row = null
        downloader = ImageDownloader { null }
        downloads = 0
    }

    private fun writeImage(file: File, w: Int, h: Int): File {
        file.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        @Suppress("DEPRECATION")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.WEBP, 80, it) }
        return file
    }

    private fun stored(id: String, w: Int = 120, h: Int = 200) =
        writeImage(File(File(context.filesDir, "images"), "$id.webp"), w, h)

    private fun entity(id: String, localPath: String?) = NoteImageEntity(
        imageId = id, noteId = "n", ownerUid = null, localPath = localPath, width = 120, height = 200,
    )

    private suspend fun NoteImageDecoder.bitmap(src: String, maxWidth: Int = 800): Bitmap =
        (decode(src, maxWidth) as NoteImageDecoder.Result.Loaded).bitmap

    // --- decode maths (moved from TesseraImageLoaderTest) ---

    @Test
    fun `rot 90 swaps orientation`() = runTest {
        stored("rot-1")
        val b = decoder().bitmap("tessera-img://rot-1?rot=90")
        assertTrue("rotated should be landscape: ${b.width}x${b.height}", b.width > b.height)
    }

    @Test
    fun `w 50 halves width of the w 100 case`() = runTest {
        stored("w-full")
        stored("w-half")
        val full = decoder().bitmap("tessera-img://w-full?w=100")
        val half = decoder().bitmap("tessera-img://w-half?w=50")
        assertEquals(full.width / 2, half.width)
    }

    @Test
    fun `w 100 keeps native width`() = runTest {
        stored("w-native")
        assertEquals(120, decoder().bitmap("tessera-img://w-native?w=100").width)
    }

    @Test
    fun `result is baked at DENSITY_NONE`() = runTest {
        stored("dens")
        assertEquals(Bitmap.DENSITY_NONE, decoder().bitmap("tessera-img://dens").density)
    }

    @Test
    fun `width is capped at maxWidth then scaled by w percent`() = runTest {
        stored("wide", w = 1000, h = 500)
        val capped = decoder().bitmap("tessera-img://wide", maxWidth = 400)
        assertEquals(400, capped.width)
        assertEquals(200, capped.height)
        val half = decoder().bitmap("tessera-img://wide?w=50", maxWidth = 400)
        assertEquals(200, half.width)
    }

    @Test
    fun `w applies to the rotated width`() = runTest {
        stored("rw", w = 100, h = 300) // rotated: 300x100, maxWidth 200 → min(300,200)=200 → w=50 → 100
        val b = decoder().bitmap("tessera-img://rw?w=50&rot=90", maxWidth = 200)
        assertEquals(100, b.width)
    }

    // --- sources ---

    @Test
    fun `row localPath wins over cache and stored files`() = runTest {
        stored("src-1", w = 50, h = 50)
        writeImage(File(File(context.cacheDir, "images"), "src-1.webp"), 60, 60)
        val custom = writeImage(File(context.cacheDir, "custom/elsewhere.webp"), 70, 70)
        row = entity("src-1", custom.absolutePath)
        assertEquals(70, decoder().bitmap("tessera-img://src-1").width)
        assertEquals(0, downloads)
    }

    @Test
    fun `cache is used when localPath is missing or dangling`() = runTest {
        writeImage(File(File(context.cacheDir, "images"), "src-2.webp"), 60, 60)
        row = entity("src-2", File(context.cacheDir, "gone.webp").absolutePath)
        assertEquals(60, decoder().bitmap("tessera-img://src-2").width)
        assertEquals(0, downloads)
    }

    @Test
    fun `filesDir convention file is used`() = runTest {
        stored("src-3", w = 50, h = 50)
        assertEquals(50, decoder().bitmap("tessera-img://src-3").width)
        assertEquals(0, downloads)
    }

    @Test
    fun `drive download is the last resort`() = runTest {
        val fetched = writeImage(File(context.cacheDir, "dl/fetched.webp"), 80, 80)
        downloader = ImageDownloader { fetched }
        assertEquals(80, decoder().bitmap("tessera-img://src-4").width)
        assertEquals(1, downloads)
    }

    @Test
    fun `row dimensions do not change the baked size`() = runTest {
        val fetched = writeImage(File(context.cacheDir, "dl/fetched2.webp"), 80, 80)
        downloader = ImageDownloader { fetched }
        row = entity("src-5", null) // row says 120x200; the bitmap is what counts
        assertEquals(80, decoder().bitmap("tessera-img://src-5").width)
    }

    @Test
    fun `decodeLocal reads only the convention files and never downloads`() {
        assertNull(decoder().decodeLocal("tessera-img://nope", 800))
        stored("loc-1")
        assertEquals(120, decoder().decodeLocal("tessera-img://loc-1", 800)!!.width)
        assertEquals(0, downloads)
    }

    // --- refusal ---

    @Test
    fun `no file and Drive returns null is Unavailable`() = runTest {
        assertEquals(NoteImageDecoder.Result.Unavailable, decoder().decode("tessera-img://missing", 800))
        assertEquals(1, downloads)
    }

    @Test
    fun `Drive failure is Unavailable not a crash`() = runTest {
        downloader = ImageDownloader { throw java.io.IOException("drive down") }
        assertEquals(NoteImageDecoder.Result.Unavailable, decoder().decode("tessera-img://missing", 800))
    }

    @Test
    fun `Room failure does not block the file lookup`() = runTest {
        stored("room-bad")
        val failing = object : NoteImageDao by dao {
            override suspend fun byId(imageId: String): NoteImageEntity? = error("db closed")
        }
        val d = NoteImageDecoder(failing, context.filesDir, context.cacheDir) { null }.apply { strictDecoding = false }
        assertTrue(d.decode("tessera-img://room-bad", 800) is NoteImageDecoder.Result.Loaded)
    }

    @Test
    fun `invalid ref is Unavailable without touching Drive`() = runTest {
        assertEquals(NoteImageDecoder.Result.Unavailable, decoder().decode("https://x/y.png", 800))
        assertEquals(NoteImageDecoder.Result.Unavailable, decoder().decode("tessera-img://", 800))
        assertEquals(0, downloads)
    }

    @Test
    fun `download=false skips Drive`() = runTest {
        assertEquals(NoteImageDecoder.Result.Unavailable, decoder().decode("tessera-img://missing", 800, download = false))
        assertEquals(0, downloads)
    }

    @Test
    fun `storedSize reads the row and is null without one`() = runTest {
        assertNull(decoder().storedSize("tessera-img://sz"))
        row = entity("sz", null)
        assertEquals(120 to 200, decoder().storedSize("tessera-img://sz"))
    }

    // --- corrupt local file ---
    // Robolectric's BitmapFactory shadow returns a bitmap even for garbage (memory
    // robolectric_bitmapfactory_bounds_mode), so "undecodable" is simulated through the decoder's
    // fileDecoder seam: files holding CORRUPT fail, everything else uses the real decode path.
    // That the real codec returns null for a truncated webp is a QA case on device.

    private fun NoteImageDecoder.failingOnCorruptFiles() {
        val real = fileDecoder
        fileDecoder = { f, w, r, ref -> if (f.readText(Charsets.ISO_8859_1).startsWith("CORRUPT")) null else real(f, w, r, ref) }
    }

    private fun corruptCacheFile(id: String): File =
        File(File(context.cacheDir, "images"), "$id.webp").apply {
            parentFile?.mkdirs()
            writeText("CORRUPT")
        }

    @Test
    fun `corrupt cache file falls through to Drive which rewrites it`() = runTest {
        val broken = corruptCacheFile("bad-1")
        downloader = ImageDownloader { _ ->
            writeImage(broken, 80, 80) // the real downloader overwrites cacheDir/images/<id>.webp
        }
        val d = decoder().apply { failingOnCorruptFiles() }
        val result = d.decode("tessera-img://bad-1", 800)
        assertEquals(80, (result as NoteImageDecoder.Result.Loaded).bitmap.width)
        assertEquals(1, downloads)
    }

    @Test
    fun `corrupt file and Drive failure is Unavailable`() = runTest {
        corruptCacheFile("bad-2")
        val d = decoder().apply { failingOnCorruptFiles() }
        assertEquals(NoteImageDecoder.Result.Unavailable, d.decode("tessera-img://bad-2", 800))
        assertEquals(1, downloads)
    }

    @Test
    fun `corrupt file and download=false is Unavailable without calling Drive`() = runTest {
        corruptCacheFile("bad-3")
        val d = decoder().apply { failingOnCorruptFiles() }
        assertEquals(NoteImageDecoder.Result.Unavailable, d.decode("tessera-img://bad-3", 800, download = false))
        assertEquals(0, downloads)
    }

    // --- prefetchSizes / cachedSize ---
    // Robolectric's bounds-mode shadow is not a real codec: that prefetch reads real file bounds
    // (no pixel decode) and matches the on-device decode size is a QA case, see the P2-1a report.

    @Test
    fun `no entry gives null`() {
        stored("ps-0")
        assertNull(decoder().cachedSize("tessera-img://ps-0", 800))
        assertNull(decoder().cachedSize("https://x/y.png", 800))
    }

    @Test
    fun `prefetched size equals the decoded size for w rot and maxWidth`() = runTest {
        val cases = listOf(
            Triple("tessera-img://ps-a", 800, 120 to 200),
            Triple("tessera-img://ps-b?w=50", 800, 120 to 200),
            Triple("tessera-img://ps-c?rot=90", 800, 120 to 200),
            Triple("tessera-img://ps-d?w=30&rot=270", 800, 120 to 200),
            Triple("tessera-img://ps-e?rot=180", 800, 120 to 200),
            Triple("tessera-img://ps-f", 400, 1000 to 500),
            Triple("tessera-img://ps-g?w=50&rot=90", 200, 100 to 300),
            Triple("tessera-img://ps-h", 640, 2989 to 1601), // QA Н1: sample 4, codec rounding
            Triple("tessera-img://ps-i?rot=90", 640, 2047 to 1153), // QA Н2: sample by the turned width
        )
        for ((src, max, dim) in cases) {
            stored(src.substringAfter("://").substringBefore('?'), dim.first, dim.second)
            val d = decoder()
            d.prefetchSizes(listOf(src))
            val predicted = d.cachedSize(src, max)
            assertNotNull("no size for $src", predicted)
            val ref = parseImageRef(src)!!
            assertEquals(
                "$src: cachedSize uses the shared formula",
                NoteImageDecoder.finalSize(dim.first, dim.second, ref, max), predicted,
            )
            val bmp = decoder().bitmap(src, max)
            assertEquals("$src width", bmp.width, predicted!!.width)
            assertEquals("$src height", bmp.height, predicted.height)
        }
    }

    @Test
    fun `sample is chosen by the width after rotation`() {
        val plain = parseImageRef("tessera-img://x")!!
        val turned = parseImageRef("tessera-img://x?rot=90")!!
        assertEquals(1, NoteImageDecoder.sampleSize(2047, 1153, turned, 640)) // turned width 1153 → no halving
        assertEquals(2, NoteImageDecoder.sampleSize(2047, 1153, plain, 640))
        assertEquals(4, NoteImageDecoder.sampleSize(2989, 1601, plain, 640))
        assertEquals(2, NoteImageDecoder.sampleSize(2989, 1601, turned, 640)) // turned width 1601
        assertEquals(NoteImageDecoder.sampleSize(10, 20, turned, 5), NoteImageDecoder.sampleSize(20, 10, plain, 5))
    }

    @Test
    fun `a turned photo is as wide as the text allows`() {
        val turned = parseImageRef("tessera-img://x?rot=90")!!
        val size = NoteImageDecoder.finalSize(2047, 1153, turned, 640)
        assertEquals(640, size.width) // was 577 when the sample followed the unturned width
        assertEquals(1136, size.height)
    }

    @Test
    fun `one prefetch serves any maxWidth`() = runTest {
        stored("ps-k", 1000, 500)
        val d = decoder()
        d.prefetchSizes(listOf("tessera-img://ps-k"))
        assertEquals(Size(400, 200), d.cachedSize("tessera-img://ps-k", 400))
        assertEquals(Size(300, 150), d.cachedSize("tessera-img://ps-k", 300))
        assertEquals(Size(1000, 500), d.cachedSize("tessera-img://ps-k", 1080))
        assertEquals(Size(1000, 500), d.cachedSize("tessera-img://ps-k", 0)) // 0 → default 1080
    }

    @Test
    fun `w and rot from src change the result without a new prefetch`() = runTest {
        stored("ps-v", 120, 200)
        val d = decoder()
        d.prefetchSizes(listOf("tessera-img://ps-v"))
        assertEquals(Size(120, 200), d.cachedSize("tessera-img://ps-v", 800))
        assertEquals(Size(60, 100), d.cachedSize("tessera-img://ps-v?w=50", 800))
        assertEquals(Size(200, 120), d.cachedSize("tessera-img://ps-v?rot=90", 800))
        assertEquals(Size(100, 60), d.cachedSize("tessera-img://ps-v?w=50&rot=270", 800))
    }

    @Test
    fun `prefetch skips Drive-only invalid and missing sources and never downloads`() = runTest {
        val d = decoder()
        d.prefetchSizes(listOf("tessera-img://on-drive", "https://x/y.png", "tessera-img://"))
        assertNull(d.cachedSize("tessera-img://on-drive", 800))
        assertEquals(0, downloads)
    }

    @Test
    fun `prefetch reads localPath and cache sources`() = runTest {
        val custom = writeImage(File(context.cacheDir, "custom/p.webp"), 70, 70)
        row = entity("ps-lp", custom.absolutePath)
        val d = decoder()
        d.prefetchSizes(listOf("tessera-img://ps-lp"))
        assertEquals(70, d.cachedSize("tessera-img://ps-lp", 800)!!.width)
    }

    @Test
    fun `a finished decode leaves raw bounds for any later src and width`() = runTest {
        stored("ps-dec")
        val d = decoder()
        d.decode("tessera-img://ps-dec?w=50", 800)
        assertEquals(Size(60, 100), d.cachedSize("tessera-img://ps-dec?w=50", 800))
        assertEquals(Size(120, 200), d.cachedSize("tessera-img://ps-dec", 800)) // other w, no new decode
    }

    // --- cancellation ---

    @Test
    fun `cancelling decode cancels the Drive call and delivers no result`() = runTest {
        val gate = CompletableDeferred<File?>()
        var cancelledInside = false
        downloader = ImageDownloader {
            try {
                gate.await()
            } catch (e: CancellationException) {
                cancelledInside = true
                throw e
            }
        }
        var result: NoteImageDecoder.Result? = null
        val job = launch { result = decoder().decode("tessera-img://slow", 800) }
        runCurrent()
        assertEquals("download must be in flight", 1, downloads)
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertTrue("cancellation must reach the downloader", cancelledInside)
        assertNull("a cancelled decode must not report Unavailable", result)
    }

    @Test
    fun `a CancellationException from Drive is rethrown not swallowed`() = runTest {
        downloader = ImageDownloader { throw CancellationException("cancelled") }
        var result: NoteImageDecoder.Result? = null
        val job = launch { result = decoder().decode("tessera-img://c", 800) }
        runCurrent()
        assertTrue(job.isCancelled)
        assertNull(result)
    }
}
