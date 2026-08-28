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

/**
 * Step-0 spike (Chunk 1 / Task 1) — de-risks the two Aztec unknowns the whole
 * persistence/render approach rests on. Kept as a regression guard.
 *
 * VERDICT (recorded after running against Aztec v2.1.7):
 *
 *   (a) SIZING — the on-screen span width tracks the DELIVERED DRAWABLE's INTRINSIC width:
 *       Aztec's `AztecDynamicImageSpan.getSize` returns `adjustBounds(w).width()`, and (once the
 *       editor is measured) `adjustBounds` sets `width = min(drawable.intrinsicWidth, paragraphWidth)`
 *       — it does NOT use the full editor content width, and it IGNORES any prior `setBounds`
 *       (`getMaxWidth(w)` is the identity, verified from the v2.1.7 bytecode).
 *       => Apply `w%` by SCALING THE DECODED BITMAP (Task 3), no custom span needed.
 *
 *       DENSITY TRAP (measured here — [currentDecodePathShrinksByDensity_documented]):
 *       `BitmapDrawable(null, bitmap).intrinsicWidth == bitmap.width * 160 / bitmap.density`.
 *       A `BitmapFactory.decodeFile` bitmap comes back at the DEVICE density (480 on xxhdpi),
 *       so a 200px source renders at 200*160/480 ≈ 67px — 1/3 size. The CURRENT loader's
 *       `BitmapDrawable(null, bitmap)` therefore mis-sizes on hidpi.
 *       => Task 3 MUST set the (scaled) bitmap's density to `Bitmap.DENSITY_NONE` so
 *       `getScaledWidth` returns the raw pixel width and `intrinsicWidth == targetWidth` on
 *       every device. Proven by [densityNoneBitmapWidthDrivesSpanWidth] (~200px).
 *
 *   (b) QUERY SURVIVAL — `editor.fromHtml(...)` → `editor.toHtml()` preserves the id and the
 *       `?w=&rot=` query, re-serializing the raw `&` as `&amp;` in the HTML attribute (see
 *       [queryRoundTrip]). => Task 4 must emit `&amp;` in markdownToHtml and decode `&amp;`→`&`
 *       on the way back; parseImageRef must decode `&amp;`→`&` before splitting (matches
 *       TesseraImageLoader.parseImageRef as written).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AztecImageParamSpikeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun writeWebp(id: String, w: Int, h: Int) {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        @Suppress("DEPRECATION")
        FileOutputStream(File(dir, "$id.webp")).use { bmp.compress(Bitmap.CompressFormat.WEBP, 80, it) }
    }

    private fun realLoader() = TesseraImageLoader(
        noteImageDao = NoDbDao(),
        filesDir = context.filesDir,
        cacheDir = context.cacheDir,
        downloader = ImageDownloader { null },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    /**
     * A getter that delivers a 200px-wide bitmap whose density is [Bitmap.DENSITY_NONE], mimicking
     * exactly what Task 3's decode must produce so `intrinsicWidth == raw px == targetWidth`.
     */
    private fun densityNone200Getter() = object : org.wordpress.aztec.Html.ImageGetter {
        override fun loadImage(source: String, callbacks: org.wordpress.aztec.Html.ImageGetter.Callbacks, maxWidth: Int) {
            val bmp = Bitmap.createBitmap(200, 333, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.GREEN)
                density = Bitmap.DENSITY_NONE
            }
            callbacks.onImageLoaded(android.graphics.drawable.BitmapDrawable(null, bmp))
        }

        override fun loadImage(
            source: String,
            callbacks: org.wordpress.aztec.Html.ImageGetter.Callbacks,
            maxWidth: Int,
            minWidth: Int,
        ) = loadImage(source, callbacks, maxWidth)
    }

    /**
     * (a) A 200px-wide bitmap with DENSITY_NONE, in an ~1200px editor, renders at ~200px on
     * screen — the width is driven by the drawable's intrinsic size, NOT the paragraph width, and
     * DENSITY_NONE makes intrinsicWidth == raw px (no device-density division). This is the
     * mechanism Task 3 relies on.
     */
    @Test
    fun densityNoneBitmapWidthDrivesSpanWidth() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = "<img src=\"tessera-img://sizetest\" />",
                    markdownKey = "spike-a",
                    htmlToMarkdown = { it },
                    onMarkdownChange = {},
                    onActionsReady = {},
                    imageGetter = densityNone200Getter(),
                )
            }
        }
        rule.waitForIdle()

        val editor = rule.activity.findViewById<AztecText>(R.id.aztec)
        val editable = editor.editableText
        val span = editable.getSpans(0, editable.length, AztecImageSpan::class.java).firstOrNull()
        assertTrue("expected an AztecImageSpan after fromHtml", span != null)

        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        val size = span!!.getSize(editor.paint, editable, start, end, null)

        assertTrue(
            "expected span width ~200 (DENSITY_NONE bitmap), got $size; editorWidth=${editor.width}",
            size in 180..220,
        )
        assertTrue(
            "span width must be well below the editor content width; size=$size editorWidth=${editor.width}",
            size < editor.width - 200,
        )
    }

    /**
     * (a-caveat) DOCUMENTS the density trap Task 3 must avoid: the CURRENT loader wraps a
     * `decodeFile` bitmap in `BitmapDrawable(null, bitmap)` WITHOUT resetting density, so on an
     * xxhdpi (480) editor a 200px source renders at 200*160/480 ≈ 67px — 1/3 size. Task 3 fixes
     * this by setting the scaled bitmap's density to DENSITY_NONE (proven above).
     */
    @Test
    fun currentDecodePathShrinksByDensity_documented() {
        writeWebp("sizetest", 200, 333)
        val loader = realLoader()
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = "<img src=\"tessera-img://sizetest\" />",
                    markdownKey = "spike-a2",
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
        val span = editable.getSpans(0, editable.length, AztecImageSpan::class.java).firstOrNull()!!
        val size = span.getSize(
            editor.paint, editable,
            editable.getSpanStart(span), editable.getSpanEnd(span), null,
        )
        // xxhdpi qualifier → density 480 → 200*160/480 ≈ 67. Locks the observed pre-fix behavior.
        assertTrue("expected density-shrunk width (~67), got $size", size in 60..75)
    }

    /** (b) Query survives byte-for-byte through Aztec's fromHtml→toHtml. Capture the literal form. */
    @Test
    fun queryRoundTrip() {
        val loader = realLoader()
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                RichNoteBody(
                    html = "<p>placeholder</p>",
                    markdownKey = "spike-b",
                    htmlToMarkdown = { it },
                    onMarkdownChange = {},
                    onActionsReady = {},
                    imageGetter = loader,
                )
            }
        }
        rule.waitForIdle()

        val editor = rule.activity.findViewById<AztecText>(R.id.aztec)
        var out = ""
        rule.runOnUiThread {
            editor.fromHtml("<img src=\"tessera-img://abc-1?w=50&rot=90\" />")
            out = editor.toHtml()
        }

        assertTrue("id must survive round-trip; out=$out", out.contains("tessera-img://abc-1"))
        assertTrue("w param must survive; out=$out", out.contains("w=50"))
        assertTrue("rot param must survive; out=$out", out.contains("rot=90"))
        // Aztec HTML-escapes the raw `&` → `&amp;` in the attribute. If this fails with a raw `&`,
        // flip Task 4 to emit/parse a raw `&` instead.
        assertTrue(
            "Aztec is expected to emit `&amp;` in the attribute; out=$out",
            out.contains("w=50&amp;rot=90"),
        )
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
