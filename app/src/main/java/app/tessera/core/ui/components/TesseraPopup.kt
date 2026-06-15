package app.tessera.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@Composable
fun TesseraPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val frostColor = MaterialTheme.colorScheme.surface
    val keyboardVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)
    val cardBottomGap = if (keyboardVisible) 12.dp else 16.dp
    val dismiss: () -> Unit = {
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BackHandler(enabled = visible, onBack = dismiss)

        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred + dimmed backdrop; tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeEffect(state = hazeState) {
                        backgroundColor = frostColor
                    }
                    .background(Color.Black.copy(alpha = 0.25f))
                    .testTag(UiTestTags.PopupScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    ),
            )

            // Bottom-anchored card; slides up while the scrim fades.
            // Sits above the keyboard (12dp gap) or, when the keyboard is hidden,
            // above the nav bar with a doubled 24dp gap.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(spring()) { it },
                        exit = slideOutVertically(spring()) { it },
                    )
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = cardBottomGap)
                    .testTag(UiTestTags.PopupCard)
                    // card taps must not reach the scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxCardHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    content = content,
                )
            }
        }
    }
}
