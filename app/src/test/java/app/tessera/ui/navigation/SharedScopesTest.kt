package app.tessera.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedScopesTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `SharedTransitionLayout provides a scope via CompositionLocal`() {
        var captured: Any? = null
        composeRule.setContent {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    captured = LocalSharedTransitionScope.current
                }
            }
        }
        composeRule.onRoot().assertExists()
        assertNotNull(captured)
    }
}
