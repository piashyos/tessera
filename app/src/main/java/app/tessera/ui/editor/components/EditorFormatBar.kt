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
  Row(
    modifier = modifier
      .height(48.dp)
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    // Pinned: [↶][↷]
    FormatToggle(Icons.AutoMirrored.Rounded.Undo, R.string.toolbar_undo, active = false, enabled = fs?.canUndo ?: true, reportsSelected = nativeEngine) { actions?.undo() }
    FormatToggle(Icons.AutoMirrored.Rounded.Redo, R.string.toolbar_redo, active = false, enabled = fs?.canRedo ?: true, reportsSelected = nativeEngine) { actions?.redo() }
    GroupSeparator()

    Row(
      modifier = Modifier
        .weight(1f, fill = false)
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      // [B][I][U][S]
      @Composable fun RowScope.marks() {
        FormatToggle(Icons.Rounded.FormatBold, R.string.toolbar_bold, if (fs != null) marked(Mark.Bold) else AztecTextFormat.FORMAT_BOLD in activeFormats, reportsSelected = nativeEngine) { actions?.toggleBold() }
        FormatToggle(Icons.Rounded.FormatItalic, R.string.toolbar_italic, if (fs != null) marked(Mark.Italic) else AztecTextFormat.FORMAT_ITALIC in activeFormats, reportsSelected = nativeEngine) { actions?.toggleItalic() }
        FormatToggle(Icons.Rounded.FormatUnderlined, R.string.toolbar_underline, if (fs != null) marked(Mark.Underline) else AztecTextFormat.FORMAT_UNDERLINE in activeFormats, reportsSelected = nativeEngine) { actions?.toggleUnderline() }
        FormatToggle(Icons.Rounded.FormatStrikethrough, R.string.toolbar_strikethrough, if (fs != null) marked(Mark.Strike) else AztecTextFormat.FORMAT_STRIKETHROUGH in activeFormats, reportsSelected = nativeEngine) { actions?.toggleStrikethrough() }
      }
      // [H1][H2]
      @Composable fun RowScope.headings() {
        FormatToggle(Lucide.Heading1, R.string.toolbar_h1, if (fs != null) kind == ParagraphKind.Title else AztecTextFormat.FORMAT_HEADING_1 in activeFormats, reportsSelected = nativeEngine) { actions?.toggleHeading1() }
        FormatToggle(Lucide.Heading2, R.string.toolbar_h2, if (fs != null) kind == ParagraphKind.Heading else AztecTextFormat.FORMAT_HEADING_2 in activeFormats, reportsSelected = nativeEngine) { actions?.toggleHeading2() }
      }
      // [•][1.][☐] on the Aztec path; [☐][•][–][1.] natively
      @Composable fun RowScope.bullet() = FormatToggle(Icons.AutoMirrored.Rounded.FormatListBulleted, R.string.toolbar_bullet, if (fs != null) isList(ListType.Bullet) else AztecTextFormat.FORMAT_UNORDERED_LIST in activeFormats, reportsSelected = nativeEngine) { actions?.toggleBulletList() }
      @Composable fun RowScope.ordered() = FormatToggle(Icons.Rounded.FormatListNumbered, R.string.toolbar_ordered, if (fs != null) isList(ListType.Number) else AztecTextFormat.FORMAT_ORDERED_LIST in activeFormats, reportsSelected = nativeEngine) { actions?.toggleOrderedList() }
      @Composable fun RowScope.checklist() = FormatToggle(Icons.Rounded.Checklist, R.string.toolbar_checklist, if (fs != null) isList(ListType.Check) else AztecTextFormat.FORMAT_TASK_LIST in activeFormats, reportsSelected = nativeEngine) { actions?.toggleTaskList() }
      @Composable fun RowScope.blocks() {
        FormatToggle(Icons.Rounded.FormatQuote, R.string.toolbar_quote, if (fs != null) kind == ParagraphKind.Quote else AztecTextFormat.FORMAT_QUOTE in activeFormats, reportsSelected = nativeEngine) { actions?.toggleQuote() }
        FormatToggle(Icons.Rounded.Code, R.string.toolbar_code, if (fs != null) kind == ParagraphKind.Mono else AztecTextFormat.FORMAT_PREFORMAT in activeFormats, reportsSelected = nativeEngine) { actions?.toggleCode() }
        FormatToggle(Icons.Rounded.Remove, R.string.toolbar_divider, active = false, reportsSelected = nativeEngine) { actions?.insertHorizontalRule() }
      }

      if (fs != null) {
        // Native: the lists come first so they are in the frame of a phone, then marks, headings, indents, blocks.
        checklist(); bullet()
        FormatToggle(Lucide.ListMinus, R.string.editor_list_dash, isList(ListType.Dash), reportsSelected = nativeEngine) { actions?.toggleDashList() }
        ordered()
        GroupSeparator()
        marks()
        GroupSeparator()
        headings()
        GroupSeparator()
        FormatToggle(Lucide.IndentIncrease, R.string.editor_indent, active = false, enabled = fs.canIndent, reportsSelected = nativeEngine) { actions?.indent() }
        FormatToggle(Lucide.IndentDecrease, R.string.editor_outdent, active = false, enabled = fs.canOutdent, reportsSelected = nativeEngine) { actions?.outdent() }
        GroupSeparator()
        blocks()
      } else {
        marks()
        GroupSeparator()
        headings()
        GroupSeparator()
        bullet(); ordered(); checklist()
        GroupSeparator()
        blocks()
        FormatToggle(Icons.Rounded.TableChart, R.string.toolbar_table, active = false, reportsSelected = nativeEngine) { actions?.insertTable() }

        GroupSeparator()
        // [🖼][📷] — attach an image from the gallery or capture one with the camera.
        FormatToggle(Icons.Rounded.Image, R.string.toolbar_image, active = false, reportsSelected = nativeEngine) { actions?.onInsertImage() }
        FormatToggle(Icons.Rounded.PhotoCamera, R.string.toolbar_camera, active = false, reportsSelected = nativeEngine) { actions?.onCaptureImage() }
      }
    }

    GroupSeparator()
    // Pinned: [⋯] — toggles the block-format panel the caller renders directly above this bar.
    FormatToggle(Icons.Rounded.MoreHoriz, R.string.toolbar_more, active = menuExpanded, reportsSelected = nativeEngine, onClick = onToggleMenu)
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
  enabled: Boolean = true,
  /** The native bar tells TalkBack (and tests) which toggles are on; the Aztec bar keeps its semantics as they were. */
  reportsSelected: Boolean = false,
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
      .then(if (reportsSelected) Modifier.semantics { selected = active } else Modifier),
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
