package app.tessera.ui.editor.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.data.images.ImageDownloader
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.spans.AztecImageSpan
import java.io.File
import java.io.FileOutputStream

/** Verifies [imageSpanRect] computes a plausible on-screen rect for an inserted image span. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ImageSpanRectTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `imageSpanRect matches the drawable size and is on-screen`() {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        @Suppress("DEPRECATION")
        FileOutputStream(File(dir, "rect-1.webp")).use { bmp.compress(Bitmap.CompressFormat.WEBP, 80, it) }
        val loader = TesseraImageLoader(
            noteImageDao = NoDbDao(),
            filesDir = context.filesDir,
            cacheDir = context.cacheDir,
            downloader = ImageDownloader { null },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = "<img src=\"tessera-img://rect-1\" />",
                    markdownKey = "rect",
                    htmlToMarkdown = { it },
                    onMarkdownChange = {},
                    onActionsReady = {},
                    imageGetter = loader,
                )
            }
        }
        rule.waitForIdle()

        val editor = rule.activity.findViewById<AztecText>(R.id.aztec)
        val editable = editor.editableText
        val span = editable.getSpans(0, editable.length, AztecImageSpan::class.java).first()
        val spanStart = editable.getSpanStart(span)
        val drawableWidth = span.drawable!!.bounds.width()

        val rect = imageSpanRect(editor, spanStart)
        assertTrue("rect must be non-null", rect != null)
        assertTrue("rect width ${rect!!.width} must match drawable width $drawableWidth", rect.width.toInt() == drawableWidth)
        assertTrue("rect top ${rect.top} must be >= 0", rect.top >= 0f)
        assertTrue("rect height ${rect.height} must be positive", rect.height > 0f)
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
