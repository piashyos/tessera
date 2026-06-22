package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

@Composable
fun TesseraTopBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  containerColor: Color = Color.Transparent,
) {
  Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .height(56.dp)
        .padding(horizontal = TesseraSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
      Box(modifier = Modifier.weight(1f).padding(horizontal = TesseraSpacing.sm)) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
          CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            title()
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
  }
}

@Composable
fun TesseraTopBarIconButton(
  onClick: () -> Unit,
  contentDescription: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier
      .size(48.dp)
      .semantics { this.contentDescription = contentDescription },
    content = content,
  )
}
