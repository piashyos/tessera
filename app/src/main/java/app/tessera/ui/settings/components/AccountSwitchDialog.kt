package app.tessera.ui.settings.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.tessera.R
import app.tessera.core.ui.UiTestTags

@Composable
fun AccountSwitchDialog(
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(UiTestTags.AccountSwitchDialog),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_switch_title)) },
        text = { Text(stringResource(R.string.account_switch_message)) },
        confirmButton = {
            TextButton(
                onClick = onMove,
                modifier = Modifier.testTag(UiTestTags.AccountSwitchMove),
            ) {
                Text(stringResource(R.string.account_switch_move))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag(UiTestTags.AccountSwitchDelete),
            ) {
                Text(stringResource(R.string.account_switch_delete))
            }
        },
    )
}
