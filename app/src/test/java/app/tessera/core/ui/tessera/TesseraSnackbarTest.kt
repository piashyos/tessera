package app.tessera.core.ui.tessera

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraSnackbarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun shows_message_when_emitted() {
    rule.setContent {
      val host = remember { SnackbarHostState() }
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        LaunchedEffect(Unit) { host.showSnackbar("Can't move folder here") }
        TesseraSnackbarHost(hostState = host)
      }
    }
    rule.onNodeWithText("Can't move folder here").assertIsDisplayed()
  }
}
