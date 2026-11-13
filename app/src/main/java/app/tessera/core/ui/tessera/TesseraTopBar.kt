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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

/**
 * [centerTitle]: the title is centered on the whole bar (not between the buttons) and kept clear of the wider
 * of the two button slots on both sides; by default it fills the gap between the slots, left-aligned.
 */
@Composable
fun TesseraTopBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  containerColor: Color = Color.Transparent,
  centerTitle: Boolean = false,
) {
  Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
    val barModifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.statusBars)
      .height(56.dp)
      .padding(horizontal = TesseraSpacing.xs)
    if (centerTitle) {
      val titleGap = with(LocalDensity.current) { TesseraSpacing.sm.roundToPx() }
      Layout(
        contents = listOf(
          { Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { navigationIcon() } },
          { TitleSlot(title) },
          { Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
        ),
        modifier = barModifier,
      ) { (navigation, titles, actionRows), constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val nav = navigation.map { it.measure(loose) }
        val act = actionRows.map { it.measure(loose) }
        val side = maxOf(nav.maxOfOrNull { it.width } ?: 0, act.maxOfOrNull { it.width } ?: 0)
        val titleMax = (constraints.maxWidth - 2 * (side + titleGap)).coerceAtLeast(0)
        val titlePlaceables = titles.map { it.measure(Constraints(maxWidth = titleMax, maxHeight = constraints.maxHeight)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
          nav.forEach { it.placeRelative(0, (constraints.maxHeight - it.height) / 2) }
          act.forEach { it.placeRelative(constraints.maxWidth - it.width, (constraints.maxHeight - it.height) / 2) }
          titlePlaceables.forEach { it.placeRelative((constraints.maxWidth - it.width) / 2, (constraints.maxHeight - it.height) / 2) }
        }
      }
    } else {
      Row(modifier = barModifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
        Box(modifier = Modifier.weight(1f).padding(horizontal = TesseraSpacing.sm)) { TitleSlot(title) }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
      }
    }
  }
}

@Composable
private fun TitleSlot(title: @Composable () -> Unit) {
  ProvideTextStyle(MaterialTheme.typography.titleLarge) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      title()
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
