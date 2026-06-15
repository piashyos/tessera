package app.tessera.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.model.UserId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingSignInInvokesCallback() {
        var signInInvoked = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(isSignedIn = false),
                onSignIn = { signInInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.SettingsSignIn).performClick()

        assertTrue(signInInvoked)
    }

    @Test
    fun signedInStateShowsSignOut() {
        var signOutInvoked = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(isSignedIn = true, userId = "user@example.com"),
                onSignOut = { signOutInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.SettingsSignOut).performClick()

        assertTrue(signOutInvoked)
    }

    @Test
    fun tappingBackInvokesCallback() {
        var backInvoked = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(),
                onBack = { backInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.SettingsBack).performClick()

        assertTrue(backInvoked)
    }

    @Test
    fun accountSwitchDialogRendersAndMoveInvokesCallback() {
        var moveInvoked = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(
                    isSignedIn = true,
                    userId = "new@example.com",
                    pendingSwitch = PendingAccountSwitch(
                        existingUid = UserId("old"),
                        newUid = UserId("new"),
                    ),
                ),
                onConfirmMove = { moveInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.AccountSwitchDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.AccountSwitchMove).performClick()

        assertTrue(moveInvoked)
    }

    @Test
    fun accountSwitchDialogDeleteInvokesCallback() {
        var deleteInvoked = false
        composeRule.setContent {
            SettingsScreen(
                state = SettingsState(
                    isSignedIn = true,
                    userId = "new@example.com",
                    pendingSwitch = PendingAccountSwitch(
                        existingUid = UserId("old"),
                        newUid = UserId("new"),
                    ),
                ),
                onDeleteLocal = { deleteInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.AccountSwitchDelete).performClick()

        assertTrue(deleteInvoked)
    }
}
