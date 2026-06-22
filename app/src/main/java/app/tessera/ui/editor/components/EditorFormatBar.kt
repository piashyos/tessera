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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Italic
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.ListOrdered
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Quote
import com.composables.icons.lucide.Strikethrough
import com.composables.icons.lucide.Underline
import org.wordpress.aztec.AztecTextFormat

/**
 * IME-anchored formatting bar that replaces the old `MarkdownToolbar` inside a
 * `FloatingCapsule`. A horizontally-scrollable 48dp row of 36dp toggle buttons grouped by
 * function, with thin [outlineVariant] separators between groups. Active formats render on
 * a `secondaryContainer` pill.
 *
 * Emits the same [RichBodyActions] the editor already understands — the trailing overflow
 * button defers headings 3–6 / paragraph to [onOpenOverflow] (the `MarkdownFormatMenu`).
 */
@Composable
fun EditorFormatBar(
  actions: RichBodyActions?,
  activeFormats: Set<AztecTextFormat>,
  onOpenOverflow: () -> Unit,
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
    FormatToggle(Lucide.Bold, R.string.toolbar_bold, AztecTextFormat.FORMAT_BOLD in activeFormats) { actions?.toggleBold() }
    FormatToggle(Lucide.Italic, R.string.toolbar_italic, AztecTextFormat.FORMAT_ITALIC in activeFormats) { actions?.toggleItalic() }
    FormatToggle(Lucide.Underline, R.string.toolbar_underline, AztecTextFormat.FORMAT_UNDERLINE in activeFormats) { actions?.toggleUnderline() }
    FormatToggle(Lucide.Strikethrough, R.string.toolbar_strikethrough, AztecTextFormat.FORMAT_STRIKETHROUGH in activeFormats) { actions?.toggleStrikethrough() }

    GroupSeparator()
    // [H1][H2]
    FormatToggle(Lucide.Heading1, R.string.toolbar_h1, AztecTextFormat.FORMAT_HEADING_1 in activeFormats) { actions?.toggleHeading1() }
    FormatToggle(Lucide.Heading2, R.string.toolbar_h2, AztecTextFormat.FORMAT_HEADING_2 in activeFormats) { actions?.toggleHeading2() }

    GroupSeparator()
    // [•][1.][☐]
    FormatToggle(Lucide.List, R.string.toolbar_bullet, AztecTextFormat.FORMAT_UNORDERED_LIST in activeFormats) { actions?.toggleBulletList() }
    FormatToggle(Lucide.ListOrdered, R.string.toolbar_ordered, AztecTextFormat.FORMAT_ORDERED_LIST in activeFormats) { actions?.toggleOrderedList() }
    FormatToggle(Lucide.ListChecks, R.string.toolbar_checklist, AztecTextFormat.FORMAT_TASK_LIST in activeFormats) { actions?.toggleTaskList() }

    GroupSeparator()
    // [«][</>][—]
    FormatToggle(Lucide.Quote, R.string.toolbar_quote, AztecTextFormat.FORMAT_QUOTE in activeFormats) { actions?.toggleQuote() }
    FormatToggle(Lucide.Code, R.string.toolbar_code, AztecTextFormat.FORMAT_CODE in activeFormats) { actions?.toggleCode() }
    FormatToggle(Lucide.Minus, R.string.toolbar_divider, active = false) { actions?.insertHorizontalRule() }

    GroupSeparator()
    // [⋯]
    FormatToggle(Lucide.Ellipsis, R.string.toolbar_more, active = false, onClick = onOpenOverflow)
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
        onOpenOverflow = {},
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
      EditorFormatBar(actions = null, activeFormats = emptySet(), onOpenOverflow = {})
    }
  }
}
