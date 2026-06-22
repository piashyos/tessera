package app.tessera.ui.notes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.notes.components.TagFilterBar
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TagFilterBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chipDisplaysAndTogglesTag() {
        var toggled: String? = null
        composeRule.setContent {
            TagFilterBar(
                expanded = true,
                availableTags = listOf("work", "ideas"),
                selectedTags = emptySet(),
                onTagToggle = { toggled = it },
                hazeState = HazeState(),
            )
        }

        composeRule.onNodeWithTag(UiTestTags.tagFilterChip("work"))
            .assertIsDisplayed()
            .performClick()

        assertEquals("work", toggled)
    }
}
