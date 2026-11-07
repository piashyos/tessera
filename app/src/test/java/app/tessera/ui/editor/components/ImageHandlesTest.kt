package app.tessera.ui.editor.components

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.viewinterop.AndroidView
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.parseImageRef
import app.tessera.ui.editor.engine.EngineTestStyle
import app.tessera.ui.editor.engine.FakeImageSource
import app.tessera.ui.editor.engine.ImageResult
import app.tessera.ui.editor.engine.ImageSelection
import app.tessera.ui.editor.engine.TesseraEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The handles of a selected image (`ImageHandles` over `ImageSelectionOverlay`) with a real editor under them, driven by real touches: a
 * gesture block outlives recompositions, so it has to use the image, the src and the size of the moment (a rotation twice is 180°, a resize
 * keeps the rotation and a rotation keeps the width).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ImageHandlesTest {
    @get:Rule val rule = createComposeRule()

    private val source = FakeImageSource()
    private lateinit var editor: TesseraEditText
    private val selection = mutableStateOf<ImageSelection?>(null)

    /** A frame: the editor tells the handles where the image is after a draw, and Compose does not draw a View of a Robolectric host by itself. */
    private fun settle() {
        repeat(2) {
            rule.runOnUiThread { editor.measure(android.view.View.MeasureSpec.makeMeasureSpec(editor.width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(editor.height, android.view.View.MeasureSpec.EXACTLY)); editor.layout(0, 0, editor.width, editor.height); editor.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(maxOf(editor.width, 1), maxOf(editor.height, 1), android.graphics.Bitmap.Config.ARGB_8888))) }
            rule.waitForIdle()
        }
    }

    private fun open(md: String = "a\n![](tessera-img://x)\nb") {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TesseraEditText(ContextThemeWrapper(ctx, R.style.Theme_Tessera), EngineTestStyle.create()).also { e ->
                                editor = e
                                e.imageSource = source
                                e.onImageSelectionChanged = { selection.value = it }
                                e.setMarkdown(md)
                            }
                        },
                    )
                    selection.value?.let { ImageHandles(it, editor) }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { source.deliver(source.requests.single(), ImageResult.Loaded(FakeImageSource.drawable(300, 100))) }
        settle()
        rule.runOnUiThread {
            val ps = editor.text!!.indexOf('￼')
            editor.requestFocus()
            editor.setSelection(ps, ps + 1)
        }
        settle()
        assertNotNull("the handles are up (focus ${editor.isFocused}, size ${editor.width}x${editor.height}, selection ${editor.selectionStart}..${editor.selectionEnd})", selection.value)
    }

    private fun rotate() {
        rule.onNodeWithContentDescription("Rotate image 90 degrees").performTouchInput { click() }
        rule.waitForIdle()
        settle()
    }

    private fun resizeTo(deltaPx: Float) {
        rule.onAllNodesWithContentDescription("Resize image")[3].performTouchInput { swipe(center, center + Offset(deltaPx, 0f), 200) }
        rule.waitForIdle()
        settle()
    }

    private fun src() = Regex("tessera-img://[^)]*").find(editor.markdown())!!.value
    private fun ref() = parseImageRef(src())!!

    @Test
    fun rotatingTwiceTurnsTheImageByHalfACircle() {
        open()
        rotate()
        assertEquals(90, ref().rotDegrees)
        rotate()
        assertEquals("the second turn is made from the first, not from the image as it was selected", 180, ref().rotDegrees)
        rotate()
        assertEquals(270, ref().rotDegrees)
    }

    @Test
    fun aRotationAfterAResizeKeepsTheWidth() {
        open()
        resizeTo(-120f)
        val w = ref().wPercent
        assertTrue("the image got narrower: $w", w < 100)
        rotate()
        assertEquals(90, ref().rotDegrees)
        assertEquals("the width is kept", w, ref().wPercent)
    }

    @Test
    fun aResizeAfterARotationKeepsTheRotationAndCountsFromTheCurrentWidth() {
        open()
        rotate()
        resizeTo(-120f)
        val first = ref()
        assertEquals(90, first.rotDegrees)
        assertTrue(first.wPercent < 100)
        resizeTo(-60f)
        val second = ref()
        assertEquals(90, second.rotDegrees)
        assertTrue("the second drag starts from the first one's width: ${first.wPercent} -> ${second.wPercent}", second.wPercent < first.wPercent)
    }

    @Test
    fun theHandlesFollowAnImageThatArrivedAfterTheSelection() {
        // the tile is as wide as the text; the drag is counted from the width of the picture the handles are over, not of the tile they were drawn on
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            TesseraEditText(ContextThemeWrapper(ctx, R.style.Theme_Tessera), EngineTestStyle.create()).also { e ->
                                editor = e
                                e.imageSource = source
                                e.onImageSelectionChanged = { selection.value = it }
                                e.setMarkdown("![](tessera-img://x)")
                            }
                        },
                    )
                    selection.value?.let { ImageHandles(it, editor) }
                }
            }
        }
        rule.waitForIdle()
        rule.runOnUiThread { editor.requestFocus(); editor.setSelection(0, 1) }
        settle()
        val tile = selection.value!!.rect.width()
        rule.runOnUiThread { source.deliver(source.requests.single(), ImageResult.Loaded(FakeImageSource.drawable(200, 100))) }
        settle()
        assertEquals("the picture is narrower than the tile was", 200, selection.value!!.rect.width())
        assertTrue(tile > 200)
        resizeTo(-100f)
        // the drag loses the touch slop at its start, so about -50px of the 100 arrive: from 200px that is ~74%, from the tile's 392px it would be ~87%
        val w = ref().wPercent
        assertTrue("counted from the picture's 200px, not from the tile: $w%", w in 60..80)
    }
}
