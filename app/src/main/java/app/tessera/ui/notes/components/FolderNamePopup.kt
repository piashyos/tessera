package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.components.TesseraTextField
import dev.chrisbanes.haze.HazeState

/**
 * Glass-styled popup for creating or renaming a folder.
 * [initialName] is empty when creating; non-empty when renaming.
 */
@Composable
fun FolderNamePopup(
    visible: Boolean,
    initialName: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    var name by remember(visible, initialName) { mutableStateOf(initialName) }
    val submit: () -> Unit = {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            onSubmit(trimmed)
        }
    }

    TesseraPopup(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                if (initialName.isEmpty()) R.string.folders_new else R.string.folders_rename,
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FloatingCapsule(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TesseraTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.folders_new_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp),
                imeAction = ImeAction.Done,
                onImeAction = submit,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Text(stringResource(R.string.folders_cancel))
            }
            Button(
                onClick = submit,
                enabled = name.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Text(stringResource(R.string.folders_save))
            }
        }
    }
}
