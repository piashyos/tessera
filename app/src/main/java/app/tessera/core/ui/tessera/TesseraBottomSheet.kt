package app.tessera.core.ui.tessera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesseraBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  header: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    modifier = modifier,
    shape = TesseraShape.bottomSheetTop,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      if (header != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.lg),
        ) {
          ProvideTextStyle(MaterialTheme.typography.titleMedium) { header() }
        }
      }
      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
      )
    }
  }
}

@Composable
fun TesseraSheetItem(
  label: String,
  leadingIcon: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  destructive: Boolean = false,
) {
  val labelColor =
    if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
  ) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      leadingIcon()
    }
    Text(label, color = labelColor, style = MaterialTheme.typography.bodyLarge)
  }
}
