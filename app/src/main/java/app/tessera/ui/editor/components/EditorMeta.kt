package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide

/**
 * Editor metadata row: a folder breadcrumb on the left and the note's relative
 * modified-date on the right. Sits between the [TesseraTopBar] and the editor body.
 *
 * @param folderBreadcrumb folder path segments — joined with ` › `. Empty list hides the
 *   breadcrumb (only the date is shown).
 * @param relativeDate pre-formatted relative date (see `formatNoteDate`).
 */
@Composable
fun EditorMeta(
  folderBreadcrumb: List<String>,
  relativeDate: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .heightIn(min = 28.dp)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (folderBreadcrumb.isNotEmpty()) {
      Icon(
        imageVector = Lucide.Folder,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      // Takes all remaining width so the date is pushed to the right edge; the
      // breadcrumb ellipsizes instead of centering the date.
      Text(
        text = folderBreadcrumb.joinToString(" › "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    } else {
      Spacer(Modifier.weight(1f))
    }
    Text(
      text = relativeDate,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun EditorMetaPreview() {
  TesseraTheme(darkTheme = false, dynamicColor = false) {
    Box(Modifier.padding(16.dp)) {
      EditorMeta(folderBreadcrumb = listOf("Work", "Specs"), relativeDate = "Today")
    }
  }
}
