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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.rememberHaptics
import app.tessera.core.ui.tessera.rememberTagChipColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close

@Composable
fun TagChipsExpandable(
    expanded: Boolean,
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
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
            expandHorizontally(animationSpec = springExpand, expandFrom = Alignment.Start) +
            scaleIn(transformOrigin = TransformOrigin(0f, 0.5f), initialScale = 0.85f, animationSpec = springScale),
        exit = fadeOut() +
            shrinkHorizontally(animationSpec = springExpand, shrinkTowards = Alignment.Start) +
            scaleOut(transformOrigin = TransformOrigin(0f, 0.5f), targetScale = 0.85f, animationSpec = springScale),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(Alignment.Start)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, alignment = Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag ->
                // Compact pill chip in the tag's own hue (rememberTagChipColors) — same color the
                // tag shows on the list card and in the filter row. labelSmall text, tight padding,
                // and a modest inline ✕.
                val tagColors = rememberTagChipColors(tag)
                Surface(
                    shape = CircleShape,
                    color = tagColors.container,
                    contentColor = tagColors.content,
                    modifier = Modifier.clip(CircleShape),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(start = 10.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelSmall)
                        Surface(
                            onClick = { haptics.tick(); onRemoveTag(tag) },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(20.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.editor_remove_tag, tag),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                onClick = { haptics.tick(); onAddTagClick() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.editor_add_tag),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
