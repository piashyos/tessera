package app.tessera.ui.notes.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            if (onClick == null) {
                modifier
            } else {
                modifier.clickable(onClick = onClick)
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color =
                    if (note.isPinned) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text(
                        text = note.title.firstOrNull()?.uppercase() ?: "N",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (note.isPinned) {
                    Text(
                        text = stringResource(R.string.note_pinned),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = note.title.ifBlank { stringResource(R.string.note_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = note.previewText() ?: stringResource(R.string.note_empty_preview),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (note.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        note.tags.take(6).forEach { tag ->
                            TagChip(name = tag.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(name: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = "#$name",
            modifier = Modifier,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun Note.previewText(): String? =
    content
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun NoteCardLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Surface {
            NoteCard(
                note =
                    previewNote(
                        title = "Weekend sketchpad",
                        content = "Refine the staggered grid spacing.\nRemember to validate swipe thresholds.",
                        tags = listOf("android", "compose", "design"),
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NoteCardDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            NoteCard(
                note =
                    previewNote(
                        title = "Pinned roadmap",
                        content = "Ship list route first.\nSwipe actions and undo can layer on next.",
                        pinned = true,
                        tags = listOf("pinned", "roadmap"),
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun previewNote(
    title: String,
    content: String,
    pinned: Boolean = false,
    tags: List<String> = emptyList(),
): Note =
    Note(
        id = NoteId("preview"),
        title = title,
        content = content,
        tags = tags.map(::Tag),
        isPinned = pinned,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )
