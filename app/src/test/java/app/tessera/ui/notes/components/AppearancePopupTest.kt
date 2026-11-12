package app.tessera.ui.notes.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AppearancePopupTest {
    @get:Rule
    val rule = createComposeRule()

    private val saved = mutableListOf<Triple<String, String?, String?>>()
    private var dismissed = 0

    private fun show(
        title: String = "Folder",
        name: String? = "",
        initialColor: String? = null,
        initialIcon: String? = null,
        showReset: Boolean = false,
        nameTakenOnStart: Boolean = false,
    ) {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Surface {
                    var taken by remember { mutableStateOf(nameTakenOnStart) }
                    AppearancePopup(
                        visible = true,
                        title = title,
                        name = name,
                        initialColor = initialColor,
                        initialIcon = initialIcon,
                        onSave = { n, c, i -> saved += Triple(n, c, i) },
                        onDismiss = { dismissed++ },
                        hazeState = HazeState(),
                        showReset = showReset,
                        nameTaken = taken,
                        onNameEdited = { taken = false },
                    )
                }
            }
        }
    }

    // The footer buttons and the title row are never scrolled away, so a plain click finds them.
    private fun click(text: String) = rule.onNodeWithText(text).performClick()

    private fun pick(description: String) = rule.onNodeWithContentDescription(description).performScrollTo().performClick()

    @Test
    fun `save of a folder is off while the name is blank`() {
        show()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("   ")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Work")
        rule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `a taken name shows an error that goes away when the name is edited`() {
        show(name = "Work", nameTakenOnStart = true)
        rule.onNodeWithText("A folder with this name already exists").assertExists()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("2")
        rule.onNodeWithText("A folder with this name already exists").assertDoesNotExist()
    }

    @Test
    fun `a tag has no name field and saves colour and icon`() {
        show(title = "#work", name = null, showReset = true)
        // The emoji field is the only text field.
        assertEquals(1, rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        rule.onNodeWithText("Save").assertIsEnabled()
        pick("Blue")
        pick("Star")
        click("Save")
        assertEquals(listOf(Triple("", "blue", "lucide:star")), saved)
    }

    @Test
    fun `a folder saves its trimmed name with colour and icon`() {
        show()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("  Work ")
        pick("Teal")
        pick("Work")
        click("Save")
        assertEquals(listOf(Triple("Work", "teal", "lucide:briefcase")), saved)
    }

    @Test
    fun `an emoji is accepted and a letter is not`() {
        show(title = "#work", name = null)
        val emojiField = rule.onAllNodes(hasSetTextAction())[0]
        emojiField.performTextInput("a")
        click("Save")
        assertEquals(listOf(Triple("", null, null)), saved)

        emojiField.performTextInput("🏠")
        click("Save")
        assertEquals(Triple("", null, "emoji:🏠"), saved.last())
    }

    @Test
    fun `of several emoji typed at once the last one wins`() {
        show(title = "#work", name = null)
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("🏠⭐")
        click("Save")
        assertEquals(listOf(Triple("", null, "emoji:⭐")), saved)
    }

    @Test
    fun `a letter typed after a picked icon leaves the icon`() {
        show(title = "#work", name = null)
        pick("Star")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("a")
        click("Save")
        assertEquals(listOf(Triple("", null, "lucide:star")), saved)
    }

    @Test
    fun `an untouched key of a newer version is saved back as it was`() {
        show(initialColor = "neon", initialIcon = "lucide:no-such-icon", name = "Work")
        click("Save")
        assertEquals(listOf(Triple("Work", "neon", "lucide:no-such-icon")), saved)
    }

    @Test
    fun `touching the colour replaces only the colour`() {
        show(initialColor = "neon", initialIcon = "lucide:no-such-icon", name = "Work")
        pick("Red")
        click("Save")
        assertEquals(listOf(Triple("Work", "red", "lucide:no-such-icon")), saved)
    }

    @Test
    fun `the footer buttons are on screen without scrolling`() {
        show(title = "#work", name = null)
        rule.onNodeWithText("Save").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `reset clears colour and icon`() {
        show(title = "#work", name = null, initialColor = "red", initialIcon = "lucide:heart", showReset = true)
        click("Reset")
        click("Save")
        assertEquals(listOf(Triple("", null, null)), saved)
    }

    @Test
    fun `cancel closes without saving`() {
        show()
        click("Cancel")
        assertEquals(1, dismissed)
        assertTrue(saved.isEmpty())
    }
}
