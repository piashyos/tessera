package app.tessera.ui.editor.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.ImageResult
import app.tessera.ui.editor.engine.ObjectImageSource
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The image object in `NoteBody` in each of its states (phase 2 spec §3.1), light and dark: loaded (the picture, in its own size), loading (the
 * tile, at the size known before the decode) and unavailable (the tile with its caption). The pictures come from a fake [ObjectImageSource];
 * the golden files are written by the orchestrator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NoteBodyImageScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val md = "Текст над картинкой.\n![](tessera-img://pic)\nТекст под картинкой."

    private enum class State { Loaded, Loading, Unavailable }

    private fun picture(): Drawable {
        val bmp = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        val c = Canvas(bmp)
        val p = Paint()
        p.color = Color.rgb(70, 130, 180); c.drawRect(0f, 0f, 600f, 300f, p)
        p.color = Color.rgb(250, 200, 80); c.drawCircle(450f, 90f, 50f, p)
        p.color = Color.rgb(60, 110, 70); c.drawRect(0f, 220f, 600f, 300f, p)
        return BitmapDrawable(null, bmp)
    }

    private fun source(state: State) = object : ObjectImageSource {
        private val drawable = picture()
        override fun peek(src: String, maxWidthPx: Int): Drawable? = if (state == State.Loaded) drawable else null
        override fun size(src: String, maxWidthPx: Int): Size? = if (state == State.Loading) Size(600, 300) else null
        override fun load(src: String, maxWidthPx: Int, onResult: (ImageResult) -> Unit): () -> Unit {
            if (state == State.Unavailable) Handler(Looper.getMainLooper()).post { onResult(ImageResult.Unavailable) }
            return {} // Loading: the result never comes
        }
    }

    private fun capture(name: String, state: State, dark: Boolean) {
        val src = source(state)
        rule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteBody(
                        markdown = md,
                        onMarkdownChange = {},
                        onActionsReady = {},
                        onFormatStateChanged = {},
                        editable = true,
                        imageSource = src,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    )
                }
            }
        }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("ui/editor/note_body_image_$name.png")
    }

    @Test fun loadedLight() = capture("loaded_light", State.Loaded, dark = false)
    @Test fun loadedDark() = capture("loaded_dark", State.Loaded, dark = true)
    @Test fun loadingLight() = capture("loading_light", State.Loading, dark = false)
    @Test fun loadingDark() = capture("loading_dark", State.Loading, dark = true)
    @Test fun unavailableLight() = capture("unavailable_light", State.Unavailable, dark = false)
    @Test fun unavailableDark() = capture("unavailable_dark", State.Unavailable, dark = true)
}
