package app.tessera.ui.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.TagPickerPopup
import app.tessera.ui.editor.engine.ImageResult
import app.tessera.ui.editor.engine.ObjectImageSource
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for [NoteEditorScreen] (Plan 07, Task 2).
 *
 * The body is the real native editor View (not the inspection-mode placeholder): it does
 * render deterministically under Robolectric's NATIVE graphics mode, so these baselines exercise
 * the genuine editor surface — top bar, tag chips, `NoteBody`, and the format bar — rather than a stand-in. The format bar renders because the IME insets read as
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
        composeRule.onAllNodes(isRoot()).onFirst().captureRoboImage("ui/editor/$name.png")
    }

    @Composable
    private fun editor(state: NoteEditorState, imageSource: ObjectImageSource? = null) {
        NoteEditorScreen(
            state = state,
            imageSource = imageSource,
            onContentChange = {},
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }

    /**
     * Synchronous stand-in for `NoteObjectImageSource`: the picture is in memory at once, so the baseline renders a real
     * image object (not a placeholder) without touching disk or coroutines.
     */
    private fun solidImageSource(): ObjectImageSource = object : ObjectImageSource {
        private val tile: Drawable = BitmapDrawable(
            null,
            Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.rgb(0x6A, 0x8E, 0xAE)); density = Bitmap.DENSITY_NONE },
        )
        override fun peek(src: String, maxWidthPx: Int): Drawable = tile
        override fun size(src: String, maxWidthPx: Int): Size = Size(600, 300)
        override fun load(src: String, maxWidthPx: Int, onResult: (ImageResult) -> Unit): () -> Unit = {}
    }

    @Test fun contentLight() = capture("editor_content_light", darkTheme = false) { editor(contentState()) }

    @Test fun contentDark() = capture("editor_content_dark", darkTheme = true) { editor(contentState()) }

    @Test fun emptyLight() = capture("editor_empty_light", darkTheme = false) { editor(emptyState()) }

    @Test fun emptyDark() = capture("editor_empty_dark", darkTheme = true) { editor(emptyState()) }

    @Test fun tableLight() = capture("editor_table_light", darkTheme = false) { editor(tableState()) }

    @Test fun tableDark() = capture("editor_table_dark", darkTheme = true) { editor(tableState()) }

    @Test fun imageLight() =
        capture("editor_image_light", darkTheme = false) { editor(imageState(), solidImageSource()) }

    @Test fun imageDark() =
        capture("editor_image_dark", darkTheme = true) { editor(imageState(), solidImageSource()) }

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

// Noon UTC of a past year: the label is "12 Jun 2024" whatever "today" and the time zone (±11 h) are.
private val headerDate = java.time.Instant.parse("2024-06-12T12:00:00Z").toEpochMilli()

private fun contentState() =
    NoteEditorState(
        folderBreadcrumb = listOf("Work", "Projects"),
        updatedAt = headerDate,
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
        // three levels: the path loses its start ("… › Lisbon 2026") and stays clear of the buttons
        folderBreadcrumb = listOf("Personal", "Travel plans and ideas", "Lisbon 2026"),
        updatedAt = headerDate,
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
