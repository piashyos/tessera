package app.tessera.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.components.TesseraTextField
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerPopup(
    visible: Boolean,
    availableTags: List<String>,
    onAddTag: (String) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    var newTag by remember(visible) { mutableStateOf("") }
    val submit: () -> Unit = {
        val trimmed = newTag.trim()
        if (trimmed.isNotBlank()) {
            onAddTag(trimmed)
            newTag = ""
        }
    }

    TesseraPopup(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.editor_add_tag),
            style = MaterialTheme.typography.titleMedium,
        )
        TesseraTextField(
            value = newTag,
            onValueChange = { newTag = it },
            placeholder = stringResource(R.string.editor_add_tag),
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Done,
            onImeAction = submit,
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
                    AssistChip(onClick = { onAddTag(tag) }, label = { Text("#$tag") })
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun TagPickerPopupLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TagPickerPopup(
                visible = true,
                availableTags = listOf("work", "personal", "ideas"),
                onAddTag = {},
                onDismiss = {},
                hazeState = remember { HazeState() },
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
private fun TagPickerPopupDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TagPickerPopup(
                visible = true,
                availableTags = listOf("work", "kotlin"),
                onAddTag = {},
                onDismiss = {},
                hazeState = remember { HazeState() },
            )
        }
    }
}
