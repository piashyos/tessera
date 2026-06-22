package app.tessera.core.ui.tessera

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun TesseraDialog(
  onDismissRequest: () -> Unit,
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
  dismissLabel: String? = null,
  destructive: Boolean = false,
) {
  val confirmColor: Color =
    if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    shape = MaterialTheme.shapes.extraLarge,
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
    text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel, color = confirmColor)
      }
    },
    dismissButton = dismissLabel?.let {
      {
        TextButton(onClick = onDismissRequest) {
          Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
  )
}
