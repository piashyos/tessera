package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import app.tessera.ui.editor.InviteUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for [MembersSheet] (Phase 4, spec §6.3): owner-view (role menus + invite),
 * viewer-view (read-only roster), and the "user not found" invite feedback.
 *
 * Captures the [MembersSheetContent] directly rather than the wrapping ModalBottomSheet, which
 * doesn't lay out under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MembersSheetScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val me = UserId("me")

    private fun members() = listOf(
        ShareMember(me, "You", "you@example.com", null, ShareRole.OWNER, 1L),
        ShareMember(UserId("bob"), "Bob Lee", "bob@example.com", null, ShareRole.EDITOR, 2L),
        ShareMember(UserId("ann"), "Ann Park", "ann@example.com", null, ShareRole.VIEWER, 3L),
    )

    private fun capture(
        name: String,
        darkTheme: Boolean,
        myRole: ShareRole,
        inviteState: InviteUiState = InviteUiState.Idle,
    ) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MembersSheetContent(
                            members = members(),
                            myRole = myRole,
                            currentUserId = me,
                            inviteState = inviteState,
                            onChangeRole = { _, _ -> },
                            onRemoveMember = {},
                            onInvite = { _, _ -> },
                            onInviteInputChanged = {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/members/$name.png")
    }

    @Test fun ownerLight() = capture("members_owner_light", darkTheme = false, myRole = ShareRole.OWNER)

    @Test fun ownerDark() = capture("members_owner_dark", darkTheme = true, myRole = ShareRole.OWNER)

    @Test fun viewerLight() = capture("members_viewer_light", darkTheme = false, myRole = ShareRole.VIEWER)

    @Test fun viewerDark() = capture("members_viewer_dark", darkTheme = true, myRole = ShareRole.VIEWER)

    @Test
    fun notFoundLight() = capture(
        "members_not_found_light",
        darkTheme = false,
        myRole = ShareRole.OWNER,
        inviteState = InviteUiState.UserNotFound,
    )
}
