package app.tessera.ui.editor.engine

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * The images of the native editor (phase 2 spec §3): the tile and its size, the life of a load (peek, quiet measure, cancel, discarded results,
 * carry over a rebuild), resize and rotate with the old picture on screen, the tap, the keys, the keyboard's and the clipboard's images.
 * Robolectric masks the real decode (memory `robolectric_bitmapfactory_bounds_mode`): the pictures here are made by [FakeImageSource].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraEditTextImageTest {
    private val md = "a\n![](tessera-img://x)\nb"
    private val x = "tessera-img://x"

    /** The width of the text area of a [HostFixture]: 400 less the padding of the editor's theme. */
    private val TEXT = 392

    private fun host(markdown: String = md, source: FakeImageSource = FakeImageSource(), before: (FakeImageSource) -> Unit = {}): Pair<HostFixture, FakeImageSource> {
        before(source)
        val h = HostFixture(markdown)
        h.view.imageSource = source
        h.layout()
        return h to source
    }

    private fun HostFixture.ps() = at("￼")
    private fun HostFixture.rect() = view.objectRect(ps())!!
    private fun HostFixture.version() = (text as EngineEditable).version
    private fun HostFixture.tap(px: Float, py: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, px, py, 0))
    }

    private val loaded = ImageResult.Loaded(FakeImageSource.drawable(300, 100))

    // ------------------------------------------------------------ the tile and its size

    @Test
    fun anImageWithoutAKnownSizeIsATileAsWideAsTheTextAndAsHighAsThePlaceholder() {
        val (h, src) = host()
        assertEquals(1, src.requests.size)
        assertEquals(x, src.requests.single().src)
        assertEquals(TEXT, src.requests.single().maxWidthPx)
        val r = h.rect()
        assertEquals(TEXT to 160, r.width() to r.height())
    }

    @Test
    fun aSizeKnownBeforeTheDecodeGivesTheTileItsExactSize() {
        val (h, _) = host { it.sizes[x] = Size(200, 50) }
        assertEquals(200 to 50, h.rect().let { it.width() to it.height() })
    }

    @Test
    fun theLineIsAsHighAsThePictureNotHigherAndAtTheLeftEdge() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        val r = h.rect()
        assertEquals(300 to 100, r.width() to r.height())
        assertEquals(h.view.totalPaddingLeft, r.left)
        val l = h.view.layout
        val line = l.getLineForOffset(h.ps())
        val gaps = h.view.let { v -> (h.text.getSpans(0, h.text.length, app.tessera.ui.editor.engine.spans.ObjectSpan::class.java).single().baked) }!!
        val height = l.getLineBottom(line) - l.getLineTop(line)
        val below = h.view.paint.fontMetricsInt.bottom // the line break after the picture keeps the descent of the font under it
        assertTrue("the line is the picture, the margins and at most the descent of the font: $height", height in 100 + gaps.gapTop + gaps.gapBottom..100 + gaps.gapTop + gaps.gapBottom + below)
    }

    @Test
    fun anUnreadableImageIsATileWithoutAPictureAndKeepsItsSize() {
        val (h, src) = host { it.sizes[x] = Size(200, 50) }
        src.deliver(src.requests.single(), ImageResult.Unavailable)
        h.layout()
        assertEquals(200 to 50, h.rect().let { it.width() to it.height() })
        val span = h.text.getSpans(0, h.text.length, app.tessera.ui.editor.engine.spans.ObjectSpan::class.java).single()
        assertTrue(span.image!!.failed)
        assertNull(span.image!!.drawable)
    }

    // ------------------------------------------------------------ the life of a load (spec §3.2)

    @Test
    fun openLoadCloseSaysNothingAndChangesNothing() {
        val (h, src) = host()
        val v = h.version()
        val before = h.md()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        assertEquals("the picture is measured", 100, h.rect().height())
        assertEquals("no new version of the document", v, h.version())
        assertEquals(before, h.md())
        assertFalse("no history step", h.view.formatState().canUndo)
        h.view.flushPendingEdit()
        h.pass(2000)
        h.view.dispose()
        assertEquals("nothing was handed out", emptyList<String>(), h.emitted)
    }

    @Test
    fun aPictureInMemoryNeedsNoLoad() {
        val (h, src) = host { it.cache["$x@$TEXT"] = FakeImageSource.drawable(300, 100) }
        assertTrue("no load asked for", src.requests.isEmpty())
        assertEquals(300 to 100, h.rect().let { it.width() to it.height() })
    }

    @Test
    fun theSelectionOfAnObjectSurvivesAnArrivingPicture() {
        val (h, src) = host()
        val ps = h.ps()
        h.select(ps, ps + 1)
        src.deliver(src.requests.single(), loaded)
        h.layout()
        assertEquals(ps to ps + 1, h.view.selectionStart to h.view.selectionEnd)
        h.key(KeyEvent.KEYCODE_DEL)
        assertEquals("the mark 'selected' was not lost: Backspace deletes the object", "a\nb", h.md())
    }

    @Test
    fun aResultForADeletedImageIsThrownAwayAndItsLoadIsCancelled() {
        val (h, src) = host()
        val req = src.requests.single()
        val ps = h.ps()
        h.select(ps, ps + 1)
        h.key(KeyEvent.KEYCODE_DEL)
        assertEquals("a\nb", h.md())
        assertTrue("the load was cancelled", req.cancelled)
        val v = h.version()
        src.deliver(req, loaded) // even if the source were to answer
        h.layout()
        assertEquals(v, h.version())
        assertEquals("a\nb", h.md())
    }

    @Test
    fun aResizeCancelsTheLoadOfTheOldSrcAndItsLateResultIsNotUsed() {
        val (h, src) = host()
        val old = src.requests.single()
        h.view.replaceImageSrc(h.ps(), "$x?w=50")
        assertTrue(old.cancelled)
        assertEquals(2, src.requests.size)
        src.deliver(old, loaded)
        h.layout()
        val span = h.text.getSpans(0, h.text.length, app.tessera.ui.editor.engine.spans.ObjectSpan::class.java).single()
        assertNull("the late picture of the old src is not shown", span.image!!.drawable)
    }

    @Test
    fun disposeCancelsEveryLoad() {
        val (h, src) = host("![](tessera-img://x)\n![](tessera-img://y)")
        assertEquals(2, src.pending.size)
        h.view.dispose()
        assertEquals(0, src.pending.size)
    }

    @Test
    fun aRebuildKeepsThePicturesAndAsksForNothingAgain() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        h.view.setMarkdown("a\n![](tessera-img://x)\nb\nc") // an update from outside
        h.layout()
        assertEquals("no second load", 1, src.requests.size)
        assertEquals(300 to 100, h.rect().let { it.width() to it.height() })
    }

    @Test
    fun undoAfterADeletionBringsTheImageBackFromTheMemoryCache() {
        val (h, src) = host(source = FakeImageSource(remembersResults = true))
        src.deliver(src.requests.single(), loaded)
        h.layout()
        val ps = h.ps()
        h.select(ps, ps + 1)
        h.key(KeyEvent.KEYCODE_DEL)
        h.view.undo()
        h.layout()
        assertEquals("a\n![](tessera-img://x)\nb", h.md())
        assertEquals("the picture of the image that was deleted, from the source's memory: no tile, no new load", 300 to 100, h.rect().let { it.width() to it.height() })
        assertEquals(1, src.requests.size)
    }

    @Test
    fun aChangeOfTheThemeKeepsThePictureAndALoadInFlight() {
        val (h, src) = host()
        h.view.editorStyle = h.view.editorStyle.copy(accentColor = 0xFF123456.toInt())
        h.layout()
        assertEquals(1, src.requests.size)
        src.deliver(src.requests.single(), loaded)
        h.layout()
        assertEquals("the result reached the restyled span", 100, h.rect().height())
        h.view.editorStyle = h.view.editorStyle.copy(accentColor = 0xFF654321.toInt())
        h.layout()
        assertEquals("and the picture stays across the next restyle", 100, h.rect().height())
        assertEquals(1, src.requests.size)
    }

    @Test
    fun aNarrowerTextAsksForTheImageAgainAtItsWidth() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.view.setPadding(50, 0, 50, 0)
        h.layout()
        assertEquals(listOf(TEXT, 300), src.requests.map { it.maxWidthPx })
    }

    // ------------------------------------------------------------ resize and rotate

    @Test
    fun aResizeShowsTheOldPictureScaledAtOnceAndIsOneStepOfTheHistory() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        val ps = h.ps()
        h.select(ps, ps + 1)
        h.view.replaceImageSrc(ps, "$x?w=50")
        h.layout()
        assertEquals("the old picture, 50% of its width, not stretched", 150 to 50, h.rect().let { it.width() to it.height() })
        assertEquals("a\n![](tessera-img://x?w=50)\nb", h.md())
        assertEquals("still selected", ps to ps + 1, h.view.selectionStart to h.view.selectionEnd)
        src.deliver(src.requests.last(), ImageResult.Loaded(FakeImageSource.drawable(150, 50)))
        h.layout()
        assertEquals("the new picture is as big as the scaled one: nothing moves", 150 to 50, h.rect().let { it.width() to it.height() })
        h.view.undo()
        assertEquals("a\n![](tessera-img://x)\nb", h.md())
        assertFalse("one step", h.view.formatState().canUndo)
    }

    @Test
    fun aRotationShowsTheOldPictureAsItWas() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        h.view.replaceImageSrc(h.ps(), "$x?rot=90")
        h.layout()
        assertEquals(300 to 100, h.rect().let { it.width() to it.height() })
        assertEquals("a\n![](tessera-img://x?rot=90)\nb", h.md())
    }

    @Test
    fun theDragPreviewIsNotAnEdit() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        val v = h.version()
        h.view.previewImageWidth(h.ps(), 40)
        h.layout()
        assertEquals(120 to 40, h.rect().let { it.width() to it.height() })
        assertEquals(v, h.version())
        assertFalse(h.view.formatState().canUndo)
        h.view.previewImageWidth(h.ps(), null)
        h.layout()
        assertEquals(300 to 100, h.rect().let { it.width() to it.height() })
        assertEquals(emptyList<String>(), h.emitted)
    }

    // ------------------------------------------------------------ tap, selection, keys

    @Test
    fun aTapOnAnImageSelectsItAndMovesNoCaretIntoTheText() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        h.caret(0)
        val r = h.rect()
        h.tap(r.exactCenterX(), r.exactCenterY())
        val ps = h.ps()
        assertEquals(ps to ps + 1, h.view.selectionStart to h.view.selectionEnd)
        assertEquals("selecting is no edit", md, h.md())
    }

    @Test
    fun theTalkBackNodeCoversThePictureNotTheWholeTextWidth() {
        val (h, src) = host("![](tessera-img://x?w=50)")
        src.deliver(src.requests.single(), ImageResult.Loaded(FakeImageSource.drawable(150, 50)))
        h.layout()
        val zone = h.view.imageZones().single()
        assertEquals(150 to 50, zone.rect.width() to zone.rect.height())
        val helper = androidx.core.view.ViewCompat.getAccessibilityDelegate(h.view) as androidx.customview.widget.ExploreByTouchHelper
        val provider = helper.getAccessibilityNodeProvider(h.view)!!
        val node = provider.createAccessibilityNodeInfo(zone.ps)!!
        val bounds = android.graphics.Rect()
        node.getBoundsInParent(bounds)
        assertEquals(zone.rect, bounds)
        assertEquals("a point beside the picture hits no node", androidx.customview.widget.ExploreByTouchHelper.INVALID_ID,
            helper.javaClass.getDeclaredMethod("getVirtualViewAt", Float::class.java, Float::class.java).apply { isAccessible = true }.invoke(helper, zone.rect.right + 30f, zone.rect.exactCenterY()))
    }

    @Test
    fun aTapNextToTheImageDoesNotSelectIt() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        val r = h.rect()
        h.tap(r.right + 20f, r.exactCenterY()) // beside a picture narrower than the text
        assertFalse(h.view.selectionEnd - h.view.selectionStart == 1)
    }

    @Test
    fun aReadOnlyNoteShowsImagesButDoesNotSelectThem() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.view.editable = false
        h.layout()
        val r = h.rect()
        h.tap(r.exactCenterX(), r.exactCenterY())
        assertFalse(h.view.selectionEnd - h.view.selectionStart == 1 && h.view.selectionStart == h.ps())
        assertEquals(300 to 100, r.width() to r.height())
        h.view.insertImage("zz")
        assertEquals("nothing is inserted", md, h.md())
    }

    @Test
    fun theSelectionOfAnImageIsToldAfterADrawWithItsRect() {
        val (h, src) = host()
        src.deliver(src.requests.single(), loaded)
        h.layout()
        val told = ArrayList<ImageSelection?>()
        h.view.onImageSelectionChanged = { told += it }
        val ps = h.ps()
        h.select(ps, ps + 1)
        val canvas = Canvas(Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888))
        h.view.draw(canvas)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val sel = told.last()!!
        assertEquals(ps, sel.ps)
        assertEquals(x, sel.src)
        assertEquals(h.rect(), sel.rect)
        h.caret(0)
        h.view.draw(canvas)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNull("no image is selected any more", told.last())
    }

    @Test
    fun theDeleteKeyOverASelectedObjectDeletesItsParagraph() {
        val (h, _) = host()
        val ps = h.ps()
        h.select(ps, ps + 1)
        assertTrue(h.key(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals("a\nb", h.md())
        h.view.undo()
        assertEquals(md, h.md())
    }

    @Test
    fun insertImageAddsAParagraphAfterTheCaretsOneAsOneStep() {
        val (h, src) = host("ab\ncd")
        h.caret(1)
        h.view.insertImage("new")
        assertEquals("a\n![](tessera-img://new)\nb\ncd", h.md())
        assertEquals("a load for the new image", "tessera-img://new", src.requests.last().src)
        assertEquals("the caret is in the paragraph after the image", h.at("b"), h.view.selectionStart)
        h.view.undo()
        assertEquals("ab\ncd", h.md())
    }

    // ------------------------------------------------------------ images from the keyboard and the clipboard

    class TypeProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    private fun imageContent(h: HostFixture, source: Int): Pair<Uri, ContentInfoCompat> {
        Robolectric.setupContentProvider(TypeProvider::class.java, "tessera.test")
        val uri = Uri.parse("content://tessera.test/pic")
        shadowOf(h.view.context.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val clip = ClipData.newUri(h.view.context.contentResolver, "pic", uri)
        return uri to ContentInfoCompat.Builder(clip, source).build()
    }

    @Test
    fun anImageFromTheKeyboardIsCopiedAndHandedToTheHost() {
        val (h, _) = host("ab")
        val got = ArrayList<Uri>()
        h.view.onImageCommitted = { got += it; true }
        val (_, payload) = imageContent(h, ContentInfoCompat.SOURCE_INPUT_METHOD)
        assertNull("all of it was taken", ViewCompat.performReceiveContent(h.view, payload))
        val copy = got.single()
        assertEquals("file", copy.scheme)
        assertEquals(listOf<Byte>(1, 2, 3), java.io.File(copy.path!!).readBytes().toList())
        assertEquals("the editor changed nothing yet: the host inserts when the image is stored", "ab", h.md())
    }

    @Test
    fun anImageOnTheClipboardIsPastedThroughTheSameDoor() {
        val (h, _) = host("ab")
        val got = ArrayList<Uri>()
        h.view.onImageCommitted = { got += it; true }
        val (uri, _) = imageContent(h, ContentInfoCompat.SOURCE_CLIPBOARD)
        h.view.context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newUri(h.view.context.contentResolver, "pic", uri))
        h.view.onTextContextMenuItem(android.R.id.paste)
        assertEquals(1, got.size)
        assertEquals("ab", h.md())
    }

    @Test
    fun aReadOnlyEditorTakesNoImages() {
        val (h, _) = host("ab")
        val got = ArrayList<Uri>()
        h.view.onImageCommitted = { got += it; true }
        h.view.editable = false
        val (_, payload) = imageContent(h, ContentInfoCompat.SOURCE_INPUT_METHOD)
        assertNotNull(ViewCompat.performReceiveContent(h.view, payload))
        assertTrue(got.isEmpty())
    }
}
