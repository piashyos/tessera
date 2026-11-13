package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme

/**
 * The editor's top-bar title: a centered two-line block — the folder path over the note's relative modified-date.
 * Rendered in the `title` slot of a centered [app.tessera.core.ui.tessera.TesseraTopBar].
 *
 * | folder | date | shows |
 * | --- | --- | --- |
 * | yes | yes | path (labelLarge) over date (bodySmall) |
 * | yes | no  | path only |
 * | no  | yes | date only (bodySmall) |
 * | no  | no  | nothing |
 *
 * @param folderBreadcrumb folder path segments — joined with ` › `; a long path loses its start (`… › Last`).
 * @param relativeDate pre-formatted relative date (see `noteDateLabel`); blank = unknown.
 */
@Composable
fun EditorMeta(
  folderBreadcrumb: List<String>,
  relativeDate: String,
  modifier: Modifier = Modifier,
) {
  val hasPath = folderBreadcrumb.isNotEmpty()
  val hasDate = relativeDate.isNotEmpty()
  if (!hasPath && !hasDate) return
  Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    if (hasPath) {
      Text(
        text = folderBreadcrumb.joinToString(" › "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.StartEllipsis,
      )
    }
    if (hasDate) {
      Text(
        text = relativeDate,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
    }
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

@Preview
@Composable
private fun EditorMetaDarkPreview() {
  TesseraTheme(darkTheme = true, dynamicColor = false) {
    Surface {
      Box(Modifier.padding(16.dp)) {
        EditorMeta(folderBreadcrumb = listOf("Work", "Specs"), relativeDate = "Today")
      }
    }
  }
}
