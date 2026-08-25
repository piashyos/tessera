package app.tessera.ui.settings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.tessera.R

/**
 * Confirmation for the irreversible "Empty trash" action. [count] notes will be permanently
 * deleted; the confirm button is tinted with the error color to signal the destructive outcome.
 */
@Composable
fun EmptyTrashDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_empty_trash_dialog_title)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.settings_empty_trash_dialog_message,
                    count,
                    count,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_empty_trash_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_empty_trash_cancel))
            }
        },
    )
}
