package app.tessera.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.FloatingCapsule
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

@Composable
fun TagChipsExpandable(
    expanded: Boolean,
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val springExpand = spring<IntSize>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val springScale = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow,
    )

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() +
            expandHorizontally(animationSpec = springExpand, expandFrom = Alignment.End) +
            scaleIn(transformOrigin = TransformOrigin(1f, 0.5f), initialScale = 0.85f, animationSpec = springScale),
        exit = fadeOut() +
            shrinkHorizontally(animationSpec = springExpand, shrinkTowards = Alignment.End) +
            scaleOut(transformOrigin = TransformOrigin(1f, 0.5f), targetScale = 0.85f, animationSpec = springScale),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), reverseScrolling = true),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag ->
                FloatingCapsule(
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.clip(CircleShape),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium)
                        Surface(
                            onClick = { onRemoveTag(tag) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = onAddTagClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Lucide.Plus,
                    contentDescription = stringResource(R.string.editor_add_tag),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
