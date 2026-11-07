package app.tessera.ui.editor.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.ui.editor.engine.ImageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * The contract of [NoteObjectImageSource] (phase 2 spec §3.1): the result on the main thread, once, never after a cancel; the memory cache by
 * `src + maxWidthPx`, bounded by bytes. The decoder runs on Robolectric's lenient `BitmapFactory` (memory `robolectric_bitmapfactory_bounds_mode`):
 * what a real codec returns is for the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteObjectImageSourceTest {
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope

    private val dao = object : NoteImageDao {
        override suspend fun byId(imageId: String): NoteImageEntity? = null
        override suspend fun upsert(image: NoteImageEntity) = Unit
        override suspend fun byNote(noteId: String): List<NoteImageEntity> = emptyList()
        override suspend fun byOwnerAndStatus(uid: String, status: ImageStatus): List<NoteImageEntity> = emptyList()
        override suspend fun byOwner(uid: String): List<NoteImageEntity> = emptyList()
        override suspend fun all(): List<NoteImageEntity> = emptyList()
        override suspend fun backfillOwner(uid: String) = Unit
        override suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus) = Unit
        override suspend fun delete(imageId: String) = Unit
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "images").deleteRecursively()
        File(context.cacheDir, "images").deleteRecursively()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun source(maxBytes: Int = 8 * 1024 * 1024) =
        // Robolectric cannot decode through ImageDecoder: the BitmapFactory path (the device path is NativeImageSizeTest's).
        NoteObjectImageSource(NoteImageDecoder(dao, context.filesDir, context.cacheDir) { null }.apply { strictDecoding = false }, scope, maxBytes)

    private fun write(id: String, w: Int = 40, h: Int = 20) {
        val f = File(File(context.filesDir, "images"), "$id.webp").apply { parentFile?.mkdirs() }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        @Suppress("DEPRECATION")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.WEBP, 80, it) }
    }

    /** The decode runs on IO and comes back through the main Looper. */
    private fun await(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun loadDeliversADensityNoneDrawableOnTheMainThreadOnceAndCachesIt() {
        write("a")
        val s = source()
        val results = ArrayList<ImageResult>()
        var thread: Thread? = null
        assertNull("nothing in memory before the load", s.peek("tessera-img://a", 300))
        s.load("tessera-img://a", 300) { thread = Thread.currentThread(); results += it }
        await("the result") { results.isNotEmpty() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, results.size)
        assertSame(Looper.getMainLooper().thread, thread)
        val d = (results.single() as ImageResult.Loaded).drawable as BitmapDrawable
        assertEquals(Bitmap.DENSITY_NONE, d.bitmap.density)
        assertSame("the cache has what was delivered", d, s.peek("tessera-img://a", 300))
        assertNull("another width is another entry", s.peek("tessera-img://a", 200))
    }

    @Test
    fun noResultAfterCancel() {
        write("a")
        val s = source()
        var calls = 0
        val cancel = s.load("tessera-img://a", 300) { calls++ }
        cancel()
        repeat(30) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertEquals(0, calls)
    }

    @Test
    fun aMissingImageIsUnavailableAndNotCached() {
        val s = source()
        val results = ArrayList<ImageResult>()
        s.load("tessera-img://nope", 300) { results += it }
        await("the result") { results.isNotEmpty() }
        assertEquals(listOf<ImageResult>(ImageResult.Unavailable), results)
        assertNull(s.peek("tessera-img://nope", 300))
        val foreign = ArrayList<ImageResult>()
        s.load("https://example.com/x.png", 300) { foreign += it }
        await("the result of a foreign source") { foreign.isNotEmpty() }
        assertEquals(listOf<ImageResult>(ImageResult.Unavailable), foreign)
    }

    @Test
    fun aResultIsNeverDeliveredInsideLoadItself() {
        val s = source()
        var returned = false
        var insideLoad = false
        val results = ArrayList<ImageResult>()
        s.load("https://example.com/x.png", 300) { insideLoad = !returned; results += it } // a refusal needs no decode: the quickest result there is
        returned = true
        await("the result") { results.isNotEmpty() }
        assertEquals("the caller had its cancel function first", false, insideLoad)
    }

    @Test
    fun theCacheIsBoundedByBytesAndDropsTheOldest() {
        write("a"); write("b")
        val one = 40 * 20 * 4 // an ARGB_8888 bitmap of 40×20 (a small image keeps its size)
        val s = source(maxBytes = one + one / 2) // room for one
        val done = ArrayList<ImageResult>()
        s.load("tessera-img://a", 300) { done += it }
        await("a") { done.size == 1 }
        s.load("tessera-img://b", 300) { done += it }
        await("b") { done.size == 2 }
        assertNotNull(s.peek("tessera-img://b", 300))
        assertNull("the older one went", s.peek("tessera-img://a", 300))
    }

    @Test
    fun sizeIsNullWithoutPrefetchedBounds() {
        val s = source()
        assertNull(s.size("tessera-img://a", 300))
        assertNull(s.size("not-an-image", 300))
    }
}
