package app.tessera.ui.notes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.components.FloatingCapsule
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterBar(
    expanded: Boolean,
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded && availableTags.isNotEmpty(),
        enter = fadeIn() +
            expandVertically(expandFrom = Alignment.Top) +
            scaleIn(transformOrigin = TransformOrigin(1f, 0f), initialScale = 0.85f),
        exit = fadeOut() +
            shrinkVertically(shrinkTowards = Alignment.Top) +
            scaleOut(transformOrigin = TransformOrigin(1f, 0f), targetScale = 0.85f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableTags.forEach { tag ->
                val selected = tag in selectedTags
                val chipModifier = Modifier
                    .clip(CircleShape)
                    .clickable { onTagToggle(tag) }
                    .testTag(UiTestTags.tagFilterChip(tag))
                val chipContent: @Composable BoxScope.() -> Unit = {
                    Text("#$tag", style = MaterialTheme.typography.labelMedium)
                }
                if (selected) {
                    // Selected chips use a theme-aware primary tint.
                    FloatingCapsule(
                        modifier = chipModifier,
                        shape = CircleShape,
                        hazeState = hazeState,
                        blurRadius = 18.dp,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        content = chipContent,
                    )
                } else {
                    // Unselected chips omit backgroundColor so FloatingCapsule
                    // applies its theme-aware default tint (dark vs light).
                    FloatingCapsule(
                        modifier = chipModifier,
                        shape = CircleShape,
                        hazeState = hazeState,
                        blurRadius = 18.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        content = chipContent,
                    )
                }
            }
        }
    }
}
