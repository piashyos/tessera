package app.tessera.ui.notes

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchClearTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clearButtonAppearsWithQueryAndClearsIt() {
        var query = "kotlin"
        composeRule.setContent {
            NotesListScreen(
                state = NotesListState.Content(
                    notes = emptyList(),
                    pinnedCount = 0,
                    query = query,
                    selection = emptySet(),
                    availableTags = emptyList(),
                    selectedTags = emptySet(),
                    viewMode = NotesViewMode.Tiles,
                ),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = { query = it },
                onTagToggle = {},
                onToggleViewMode = {},
                onCreateNote = {},
            )
        }
        composeRule.onNodeWithTag(UiTestTags.SearchClear)
            .assertIsDisplayed()
            .performClick()
        assertEquals("", query)
    }
}
