package app.tessera.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() +
            expandVertically(expandFrom = Alignment.Bottom) +
            scaleIn(transformOrigin = TransformOrigin(0f, 1f), initialScale = 0.85f),
        exit = fadeOut() +
            shrinkVertically(shrinkTowards = Alignment.Bottom) +
            scaleOut(transformOrigin = TransformOrigin(0f, 1f), targetScale = 0.85f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
