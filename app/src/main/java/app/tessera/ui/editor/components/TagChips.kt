package app.tessera.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagChips(
    tags: List<String>,
    availableTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { onRemoveTag(tag) },
                    label = {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium)
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }

        Surface(
            onClick = { showSheet = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Lucide.Plus,
                    contentDescription = stringResource(R.string.editor_add_tag),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                newTagText = ""
            },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.editor_add_tag),
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text(stringResource(R.string.editor_add_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val trimmed = newTagText.trim()
                            if (trimmed.isNotBlank()) {
                                onAddTag(trimmed)
                                newTagText = ""
                                showSheet = false
                            }
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (newTagText.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    val trimmed = newTagText.trim()
                                    if (trimmed.isNotBlank()) {
                                        onAddTag(trimmed)
                                        newTagText = ""
                                        showSheet = false
                                    }
                                },
                            ) {
                                Text("Add")
                            }
                        }
                    },
                )

                if (availableTags.isNotEmpty()) {
                    Text(
                        text = "Existing tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        availableTags.forEach { tag ->
                            AssistChip(
                                onClick = {
                                    onAddTag(tag)
                                    showSheet = false
                                },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun TagChipsEmptyLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            TagChips(
                tags = emptyList(),
                availableTags = listOf("work", "personal", "ideas"),
                onAddTag = {},
                onRemoveTag = {},
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun TagChipsPopulatedLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            TagChips(
                tags = listOf("work", "kotlin"),
                availableTags = listOf("personal", "ideas"),
                onAddTag = {},
                onRemoveTag = {},
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TagChipsPopulatedDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            TagChips(
                tags = listOf("work", "kotlin"),
                availableTags = listOf("personal", "ideas"),
                onAddTag = {},
                onRemoveTag = {},
            )
        }
    }
}
