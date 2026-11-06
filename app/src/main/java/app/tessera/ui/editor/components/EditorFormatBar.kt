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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.IndentDecrease
import com.composables.icons.lucide.IndentIncrease
import com.composables.icons.lucide.ListMinus
import com.composables.icons.lucide.Lucide
import org.wordpress.aztec.AztecTextFormat
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Remove

/**
 * IME-anchored formatting bar that replaces the old `MarkdownToolbar` inside a
 * `FloatingCapsule`. A horizontally-scrollable 48dp row of 36dp toggle buttons grouped by
 * function, with thin [outlineVariant] separators between groups. Active formats render on
 * a `secondaryContainer` pill.
 *
 * Emits the same [RichBodyActions] the editor already understands — the trailing overflow
 * button anchors the [MarkdownFormatMenu] (paragraph / headings) Material 3 dropdown.
 *
 * With [nativeEngine] the buttons read [formatState] instead of [activeFormats], undo/redo follow
 * `canUndo`/`canRedo`, a dashed list and the two indent buttons are added, and table / image / camera
 * are hidden (the native editor has no objects to insert in stage 1).
 */
@Composable
fun EditorFormatBar(
  actions: RichBodyActions?,
  activeFormats: Set<AztecTextFormat>,
  modifier: Modifier = Modifier,
  menuExpanded: Boolean = false,
  onToggleMenu: () -> Unit = {},
  nativeEngine: Boolean = false,
  formatState: FormatState? = null,
) {
  val fs = if (nativeEngine) formatState ?: FormatState.Empty else null
  val kind = ((fs?.paragraph as? ParagraphFormat.Single)?.kind)
  fun marked(m: Mark) = fs != null && m in fs.marks
  fun isList(type: ListType) = (kind as? ParagraphKind.ListItem)?.type == type
  // Layout: [undo][redo] are pinned at the start and [⋯] at the end — the panic action and the
  // overflow menu must never hide inside the horizontal scroll (the old all-scrolling bar put
  // the ⋯ ~500dp off-screen with no scroll indicator). Only the format groups scroll.
  CompositionLocalProvider(LocalReportsSelected provides nativeEngine) {
  Row(
    modifier = modifier
      .height(48.dp)
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    // Pinned: [↶][↷]
    FormatToggle(Icons.AutoMirrored.Rounded.Undo, R.string.toolbar_undo, active = false, enabled = fs?.canUndo ?: true) { actions?.undo() }
    FormatToggle(Icons.AutoMirrored.Rounded.Redo, R.string.toolbar_redo, active = false, enabled = fs?.canRedo ?: true) { actions?.redo() }
    GroupSeparator()

    Row(
      modifier = Modifier
        .weight(1f, fill = false)
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // [B][I][U][S]
      FormatToggle(Icons.Rounded.FormatBold, R.string.toolbar_bold, if (fs != null) marked(Mark.Bold) else AztecTextFormat.FORMAT_BOLD in activeFormats) { actions?.toggleBold() }
      FormatToggle(Icons.Rounded.FormatItalic, R.string.toolbar_italic, if (fs != null) marked(Mark.Italic) else AztecTextFormat.FORMAT_ITALIC in activeFormats) { actions?.toggleItalic() }
      FormatToggle(Icons.Rounded.FormatUnderlined, R.string.toolbar_underline, if (fs != null) marked(Mark.Underline) else AztecTextFormat.FORMAT_UNDERLINE in activeFormats) { actions?.toggleUnderline() }
      FormatToggle(Icons.Rounded.FormatStrikethrough, R.string.toolbar_strikethrough, if (fs != null) marked(Mark.Strike) else AztecTextFormat.FORMAT_STRIKETHROUGH in activeFormats) { actions?.toggleStrikethrough() }

      GroupSeparator()
      // [H1][H2]
      FormatToggle(Lucide.Heading1, R.string.toolbar_h1, if (fs != null) kind == ParagraphKind.Title else AztecTextFormat.FORMAT_HEADING_1 in activeFormats) { actions?.toggleHeading1() }
      FormatToggle(Lucide.Heading2, R.string.toolbar_h2, if (fs != null) kind == ParagraphKind.Heading else AztecTextFormat.FORMAT_HEADING_2 in activeFormats) { actions?.toggleHeading2() }

      GroupSeparator()
      // [•][–][1.][☐] and, natively, [→][←]
      FormatToggle(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.toolbar_bullet, if (fs != null) isList(ListType.Bullet) else AztecTextFormat.FORMAT_UNORDERED_LIST in activeFormats) { actions?.toggleBulletList() }
      if (fs != null) {
        FormatToggle(Lucide.ListMinus, R.string.editor_list_dash, isList(ListType.Dash)) { actions?.toggleDashList() }
      }
      FormatToggle(Icons.Rounded.FormatListNumbered, R.string.toolbar_ordered, if (fs != null) isList(ListType.Number) else AztecTextFormat.FORMAT_ORDERED_LIST in activeFormats) { actions?.toggleOrderedList() }
      FormatToggle(Icons.Rounded.Checklist, R.string.toolbar_checklist, if (fs != null) isList(ListType.Check) else AztecTextFormat.FORMAT_TASK_LIST in activeFormats) { actions?.toggleTaskList() }
      if (fs != null) {
        FormatToggle(Lucide.IndentIncrease, R.string.editor_indent, active = false, enabled = fs.canIndent) { actions?.indent() }
        FormatToggle(Lucide.IndentDecrease, R.string.editor_outdent, active = false, enabled = fs.canOutdent) { actions?.outdent() }
      }

      GroupSeparator()
      // [«][</>][—][⊞]
      FormatToggle(Icons.Rounded.FormatQuote, R.string.toolbar_quote, if (fs != null) kind == ParagraphKind.Quote else AztecTextFormat.FORMAT_QUOTE in activeFormats) { actions?.toggleQuote() }
      FormatToggle(Icons.Rounded.Code, R.string.toolbar_code, if (fs != null) kind == ParagraphKind.Mono else AztecTextFormat.FORMAT_PREFORMAT in activeFormats) { actions?.toggleCode() }
      FormatToggle(Icons.Rounded.Remove, R.string.toolbar_divider, active = false) { actions?.insertHorizontalRule() }
      if (fs == null) {
        FormatToggle(Icons.Rounded.TableChart, R.string.toolbar_table, active = false) { actions?.insertTable() }

        GroupSeparator()
        // [🖼][📷] — attach an image from the gallery or capture one with the camera.
        FormatToggle(Icons.Rounded.Image, R.string.toolbar_image, active = false) { actions?.onInsertImage() }
        FormatToggle(Icons.Rounded.PhotoCamera, R.string.toolbar_camera, active = false) { actions?.onCaptureImage() }
      }
    }

    GroupSeparator()
    // Pinned: [⋯] — toggles the block-format panel the caller renders directly above this bar.
    FormatToggle(Icons.Rounded.MoreHoriz, R.string.toolbar_more, active = menuExpanded, onClick = onToggleMenu)
  }
  }
}

/** The native bar tells TalkBack (and tests) which toggles are on; the Aztec bar keeps its semantics as they were. */
private val LocalReportsSelected = compositionLocalOf { false }

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
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(8.dp),
    color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
    contentColor = if (active) {
      MaterialTheme.colorScheme.onSecondaryContainer
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    }.copy(alpha = if (enabled) 1f else 0.38f),
    modifier = Modifier
      .size(36.dp)
      .then(if (LocalReportsSelected.current) Modifier.semantics { selected = active } else Modifier),
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
