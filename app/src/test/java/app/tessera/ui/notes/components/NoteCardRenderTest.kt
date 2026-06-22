package app.tessera.ui.notes.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NoteCardRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun nowMs() =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun showsRelativeDate() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteCard(
                    note =
                        Note(
                            id = NoteId("n1"),
                            title = "Title",
                            content = "Body",
                            tags = listOf(Tag("work")),
                            isPinned = false,
                            createdAt = nowMs(),
                            updatedAt = nowMs(),
                            deletedAt = null,
                            syncedAt = null,
                            ownerId = null,
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }
}
