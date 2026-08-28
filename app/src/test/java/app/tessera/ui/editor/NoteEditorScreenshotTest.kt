package app.tessera.ui.editor

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.TagPickerPopup
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.wordpress.aztec.Html

/**
 * Screenshot baselines for [NoteEditorScreen] (Plan 07, Task 2).
 *
 * The body is the real AztecText Android View (not the inspection-mode placeholder): it does
 * inflate and render deterministically under Robolectric's NATIVE graphics mode, so these
 * baselines exercise the genuine rich-editor surface — top bar, tag chips, AztecText body, and
 * the format bar — rather than a stand-in. The format bar renders because the IME insets read as
 * visible under Robolectric, which conveniently matches the plan's "IME up" state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NoteEditorScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/editor/$name.png")
    }

    @Composable
    private fun editor(state: NoteEditorState, imageGetter: Html.ImageGetter? = null) {
        NoteEditorScreen(
            state = state,
            imageGetter = imageGetter,
            onContentChange = {},
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }

    /**
     * Synchronous stand-in for [TesseraImageLoader]: delivers a solid-color tile immediately so the
     * baseline renders a real image span (not a placeholder) without touching disk or coroutines.
     */
    private fun solidImageGetter(): Html.ImageGetter = object : Html.ImageGetter {
        override fun loadImage(source: String, callbacks: Html.ImageGetter.Callbacks, maxWidth: Int) {
            val w = maxWidth.takeIf { it > 0 } ?: 600
            val drawable = ColorDrawable(Color.rgb(0x6A, 0x8E, 0xAE))
            drawable.setBounds(0, 0, w, (w * 0.5f).toInt())
            callbacks.onImageLoaded(drawable)
        }

        override fun loadImage(
            source: String,
            callbacks: Html.ImageGetter.Callbacks,
            maxWidth: Int,
            minWidth: Int,
        ) = loadImage(source, callbacks, maxWidth)
    }

    @Test fun contentLight() = capture("editor_content_light", darkTheme = false) { editor(contentState()) }

    @Test fun contentDark() = capture("editor_content_dark", darkTheme = true) { editor(contentState()) }

    @Test fun emptyLight() = capture("editor_empty_light", darkTheme = false) { editor(emptyState()) }

    @Test fun emptyDark() = capture("editor_empty_dark", darkTheme = true) { editor(emptyState()) }

    @Test fun tableLight() = capture("editor_table_light", darkTheme = false) { editor(tableState()) }

    @Test fun tableDark() = capture("editor_table_dark", darkTheme = true) { editor(tableState()) }

    @Test fun imageLight() =
        capture("editor_image_light", darkTheme = false) { editor(imageState(), solidImageGetter()) }

    @Test fun imageDark() =
        capture("editor_image_dark", darkTheme = true) { editor(imageState(), solidImageGetter()) }

    @Test fun tagPickerLight() = capture("editor_tag_picker_light", darkTheme = false) { tagPicker() }

    @Test fun tagPickerDark() = capture("editor_tag_picker_dark", darkTheme = true) { tagPicker() }

    @Composable
    private fun tagPicker() {
        Box(modifier = Modifier.fillMaxSize()) {
            TagPickerPopup(
                visible = true,
                availableTags = listOf("ideas", "kotlin", "work", "personal"),
                onAddTag = {},
                onDismiss = {},
                hazeState = remember { HazeState() },
            )
        }
    }
}

private fun contentState() =
    NoteEditorState(
        content = "Material You redesign\n\nFinal QA pass for the Tessera editor surface.",
        tags = listOf("android", "compose"),
        isPinned = true,
        availableTags = listOf("ideas", "kotlin"),
        isLoading = false,
    )

private fun emptyState() =
    NoteEditorState(
        content = "",
        tags = emptyList(),
        isPinned = false,
        availableTags = listOf("ideas", "kotlin"),
        isLoading = false,
    )

private fun imageState() =
    NoteEditorState(
        content = "Trip photos\n\n![](tessera-img://demo-1)\n\nSunset over the bay.",
        tags = listOf("travel"),
        isPinned = false,
        availableTags = listOf("ideas", "kotlin"),
        isLoading = false,
    )

private fun tableState() =
    NoteEditorState(
        content = "Sprint plan\n\n| Task | Owner |\n| --- | --- |\n| Design | Ann |\n| Build | Lee |",
        tags = listOf("planning"),
        isPinned = false,
        availableTags = listOf("ideas", "kotlin"),
        isLoading = false,
    )
