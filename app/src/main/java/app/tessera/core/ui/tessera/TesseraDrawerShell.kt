package app.tessera.core.ui.tessera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

object TesseraDrawerItemTags {
  fun row(label: String) = "tessera_drawer_item_$label"
}

@Composable
fun TesseraDrawerShell(
  drawerState: DrawerState,
  header: @Composable () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
  modifier: Modifier = Modifier,
  footer: @Composable () -> Unit = {},
  screenContent: @Composable () -> Unit,
) {
  ModalNavigationDrawer(
    drawerState = drawerState,
    modifier = modifier,
    drawerContent = {
      ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.statusBars)
              .padding(
                top = TesseraSpacing.sm,
                start = TesseraSpacing.xxl,
                end = TesseraSpacing.lg,
                bottom = TesseraSpacing.lg,
              ),
          ) {
            ProvideTextStyle(MaterialTheme.typography.headlineMedium) { header() }
          }
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = TesseraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
            content = content,
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(TesseraSpacing.md),
          ) { footer() }
        }
      }
    },
    content = screenContent,
  )
}

@Composable
fun TesseraDrawerItem(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  indentLevel: Int = 0,
) {
  val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
  val textColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = modifier
      .testTag(TesseraDrawerItemTags.row(label))
      .fillMaxWidth()
      .padding(start = (indentLevel * 40).dp)
      .clip(RoundedCornerShape(24.dp))
      .background(background)
      .clickable(onClick = onClick)
      .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
  ) {
    if (leadingIcon != null) {
      Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        leadingIcon()
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = textColor,
      modifier = Modifier.weight(1f),
    )
    if (trailing != null) {
      Spacer(modifier = Modifier.width(TesseraSpacing.sm))
      trailing()
    }
  }
}
