package app.tessera.ui.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.text.markdownBodyPreviewPlainText
import app.tessera.core.ui.components.TesseraInputField
import app.tessera.core.ui.components.TesseraTextField
import app.tessera.domain.model.Note

/**
 * Bottom-sheet content for the Android share-target flow: shows the incoming text,
 * lets the user pick a destination (new note by default, or append to an existing one),
 * attach tags, and confirm with the check button in the top corner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    state: ShareUiState,
    pickerNotes: List<Note>,
    pickerQuery: String,
    onTextChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectNewNote: () -> Unit,
    onSelectNote: (Note) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picking by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AnimatedContent(
            targetState = picking,
            transitionSpec = {
                val towards = if (targetState) -1 else 1
                (slideInHorizontally { it / 4 * -towards } + fadeIn())
                    .togetherWith(slideOutHorizontally { it / 4 * towards } + fadeOut())
            },
            label = "sharePane",
        ) { showPicker ->
            if (showPicker) {
                NotePickerPane(
                    notes = pickerNotes,
                    query = pickerQuery,
                    selected = state.target,
                    onQueryChange = onQueryChange,
                    onSelectNewNote = { onSelectNewNote(); picking = false },
                    onSelectNote = { onSelectNote(it); picking = false },
                    onBack = { picking = false },
                )
            } else {
                ComposePane(
                    state = state,
                    onTextChange = onTextChange,
                    onPickTarget = { picking = true },
                    onAddTag = onAddTag,
                    onRemoveTag = onRemoveTag,
                    onSave = onSave,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ComposePane(
    state: ShareUiState,
    onTextChange: (String) -> Unit,
    onPickTarget: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            // Scroll the whole pane so the keyboard shrinking the sheet scrolls the content instead
            // of compressing the last child (the tags input) to a flattened sliver.
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.share_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            FilledIconButton(
                onClick = onSave,
                enabled = state.canSave,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.share_save),
                    )
                }
            }
        }

        TargetRow(target = state.target, onClick = onPickTarget)

        // Grows with its content (min 120dp) — the pane's own scroll handles overflow, so no
        // nested same-axis scroll and the text stays top-aligned.
        TesseraInputField(
            value = state.text,
            onValueChange = onTextChange,
            placeholder = stringResource(R.string.share_text_placeholder),
            singleLine = false,
            minHeight = 120.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        TagsEditor(tags = state.tags, onAddTag = onAddTag, onRemoveTag = onRemoveTag)
    }
}

@Composable
private fun TargetRow(target: ShareTarget, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (target) {
                            is ShareTarget.NewNote -> Icons.Rounded.NoteAdd
                            is ShareTarget.ExistingNote -> Icons.Rounded.Description
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.share_target_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when (target) {
                        is ShareTarget.NewNote -> stringResource(R.string.share_target_new_note)
                        is ShareTarget.ExistingNote ->
                            target.title.ifBlank { stringResource(R.string.note_untitled) }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (input.isNotBlank()) {
            onAddTag(input)
            input = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.share_tags_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium)
                        Surface(
                            onClick = { onRemoveTag(tag) },
                            shape = CircleShape,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.editor_remove_tag, tag),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    TesseraTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = stringResource(R.string.editor_add_tag),
                        imeAction = ImeAction.Done,
                        onImeAction = submit,
                        modifier = Modifier.widthIn(min = 96.dp, max = 160.dp),
                    )
                    Surface(
                        onClick = submit,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.editor_add_tag),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotePickerPane(
    notes: List<Note>,
    query: String,
    selected: ShareTarget,
    onQueryChange: (String) -> Unit,
    onSelectNewNote: () -> Unit,
    onSelectNote: (Note) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.share_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.share_pick_note_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TesseraTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.share_search_notes),
                imeAction = ImeAction.Search,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            item(key = "new-note") {
                PickerRow(
                    title = stringResource(R.string.share_target_new_note),
                    preview = null,
                    icon = Icons.Rounded.NoteAdd,
                    selected = selected is ShareTarget.NewNote,
                    onClick = onSelectNewNote,
                )
            }
            items(notes, key = { it.id.value }) { note ->
                PickerRow(
                    title = note.title.ifBlank { stringResource(R.string.note_untitled) },
                    preview = markdownBodyPreviewPlainText(note.content).takeIf { it.isNotEmpty() },
                    icon = Icons.Rounded.Description,
                    selected = (selected as? ShareTarget.ExistingNote)?.id == note.id,
                    onClick = { onSelectNote(note) },
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    preview: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preview != null) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
