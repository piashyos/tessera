package app.tessera.ui.editor.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.images.ImageDownloader
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.wordpress.aztec.Html
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraImageLoaderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Regression guard: a locally-present image MUST be delivered synchronously within loadImage().
     * Aztec measures the image span once right after fromHtml; a drawable delivered later (via a
     * coroutine) leaves the span at the stale placeholder size, so the photo renders as a tiny tile.
     * This asserts onImageLoaded fires BEFORE loadImage returns (no dispatcher advanced) with a real
     * decoded BitmapDrawable — not a placeholder ColorDrawable.
     */
    @Test
    fun `local image is delivered synchronously with a decoded drawable`() {
        val imageId = "local-1"
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        @Suppress("DEPRECATION")
        FileOutputStream(File(dir, "$imageId.webp")).use { bmp.compress(Bitmap.CompressFormat.WEBP, 80, it) }

        val loader = TesseraImageLoader(
            noteImageDao = NoDbDao(),
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            downloader = ImageDownloader { null },
            // A scope whose dispatcher is NEVER pumped by this test: if delivery were async, the
            // callback would not fire and `delivered` would stay null.
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        var delivered: Drawable? = null
        val callbacks = object : Html.ImageGetter.Callbacks {
            override fun onImageLoaded(drawable: Drawable?) { delivered = drawable }
            override fun onImageLoading(drawable: Drawable?) {}
            override fun onImageFailed() {}
        }

        loader.loadImage("tessera-img://$imageId", callbacks, 800)

        assertNotNull("local image must be delivered synchronously", delivered)
        assertTrue("must be a decoded bitmap, not a placeholder tile", delivered is BitmapDrawable)
        assertTrue("drawable must have real bounds", delivered!!.bounds.width() > 0 && delivered!!.bounds.height() > 0)
    }

    private class NoDbDao : NoteImageDao {
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
}
