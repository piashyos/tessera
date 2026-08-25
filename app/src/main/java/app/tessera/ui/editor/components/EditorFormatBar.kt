package app.tessera.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Lucide
import org.wordpress.aztec.AztecTextFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove

/**
 * IME-anchored formatting bar that replaces the old `MarkdownToolbar` inside a
 * `FloatingCapsule`. A horizontally-scrollable 48dp row of 36dp toggle buttons grouped by
 * function, with thin [outlineVariant] separators between groups. Active formats render on
 * a `secondaryContainer` pill.
 *
 * Emits the same [RichBodyActions] the editor already understands — the trailing overflow
 * button anchors the [MarkdownFormatMenu] (paragraph / headings) Material 3 dropdown.
 */
@Composable
fun EditorFormatBar(
  actions: RichBodyActions?,
  activeFormats: Set<AztecTextFormat>,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .height(48.dp)
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    // [B][I][U][S]
    FormatToggle(Icons.Rounded.FormatBold, R.string.toolbar_bold, AztecTextFormat.FORMAT_BOLD in activeFormats) { actions?.toggleBold() }
    FormatToggle(Icons.Rounded.FormatItalic, R.string.toolbar_italic, AztecTextFormat.FORMAT_ITALIC in activeFormats) { actions?.toggleItalic() }
    FormatToggle(Icons.Rounded.FormatUnderlined, R.string.toolbar_underline, AztecTextFormat.FORMAT_UNDERLINE in activeFormats) { actions?.toggleUnderline() }
    FormatToggle(Icons.Rounded.FormatStrikethrough, R.string.toolbar_strikethrough, AztecTextFormat.FORMAT_STRIKETHROUGH in activeFormats) { actions?.toggleStrikethrough() }

    GroupSeparator()
    // [H1][H2]
    FormatToggle(Lucide.Heading1, R.string.toolbar_h1, AztecTextFormat.FORMAT_HEADING_1 in activeFormats) { actions?.toggleHeading1() }
    FormatToggle(Lucide.Heading2, R.string.toolbar_h2, AztecTextFormat.FORMAT_HEADING_2 in activeFormats) { actions?.toggleHeading2() }

    GroupSeparator()
    // [•][1.][☐]
    FormatToggle(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.toolbar_bullet, AztecTextFormat.FORMAT_UNORDERED_LIST in activeFormats) { actions?.toggleBulletList() }
    FormatToggle(Icons.Rounded.FormatListNumbered, R.string.toolbar_ordered, AztecTextFormat.FORMAT_ORDERED_LIST in activeFormats) { actions?.toggleOrderedList() }
    FormatToggle(Icons.Rounded.Checklist, R.string.toolbar_checklist, AztecTextFormat.FORMAT_TASK_LIST in activeFormats) { actions?.toggleTaskList() }

    GroupSeparator()
    // [«][</>][—]
    FormatToggle(Icons.Rounded.FormatQuote, R.string.toolbar_quote, AztecTextFormat.FORMAT_QUOTE in activeFormats) { actions?.toggleQuote() }
    FormatToggle(Icons.Rounded.Code, R.string.toolbar_code, AztecTextFormat.FORMAT_PREFORMAT in activeFormats) { actions?.toggleCode() }
    FormatToggle(Icons.Rounded.Remove, R.string.toolbar_divider, active = false) { actions?.insertHorizontalRule() }

    GroupSeparator()
    // [⋯] — anchors the paragraph/headings Material 3 dropdown.
    Box {
      var menuExpanded by remember { mutableStateOf(false) }
      // The menu's popup is non-focusable (so it doesn't dismiss the keyboard), which means a
      // tap on this button while the menu is open is delivered BOTH to the popup's
      // dismiss-on-outside handler AND to this button's onClick. Without a guard the dismiss
      // closes the menu and the onClick immediately reopens it. Record when a dismiss happened
      // and ignore a reopen that lands within the same tap, so the button truly toggles.
      var lastDismissMs by remember { mutableLongStateOf(0L) }
      FormatToggle(Icons.Rounded.MoreHoriz, R.string.toolbar_more, active = menuExpanded) {
        if (System.currentTimeMillis() - lastDismissMs > 200L) menuExpanded = true
      }
      if (actions != null) {
        MarkdownFormatMenu(
          expanded = menuExpanded,
          activeFormats = activeFormats,
          actions = actions,
          onDismiss = {
            menuExpanded = false
            lastDismissMs = System.currentTimeMillis()
          },
        )
      }
    }
  }
}

@Composable
private fun RowScope.GroupSeparator() {
  Box(
    Modifier
      .padding(horizontal = 4.dp)
      .width(1.dp)
      .height(24.dp)
      .align(Alignment.CenterVertically)
      .background(MaterialTheme.colorScheme.outlineVariant),
  )
}

@Composable
private fun FormatToggle(
  icon: ImageVector,
  contentDescriptionRes: Int,
  active: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(8.dp),
    color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    contentColor = if (active) {
      MaterialTheme.colorScheme.onSecondaryContainer
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    modifier = Modifier.size(36.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = stringResource(contentDescriptionRes),
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun EditorFormatBarLightPreview() {
  TesseraTheme(darkTheme = false, dynamicColor = false) {
    Box(Modifier.padding(8.dp)) {
      EditorFormatBar(
        actions = null,
        activeFormats = setOf(AztecTextFormat.FORMAT_BOLD),
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
private fun EditorFormatBarDarkPreview() {
  TesseraTheme(darkTheme = true, dynamicColor = false) {
    Box(Modifier.padding(8.dp)) {
      EditorFormatBar(actions = null, activeFormats = emptySet())
    }
  }
}
