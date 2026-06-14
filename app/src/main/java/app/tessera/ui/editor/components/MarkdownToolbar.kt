package app.tessera.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraTheme

@Composable
fun MarkdownToolbar(
    onInsertToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIcon(
            icon = Lucide.Bold,
            contentDescription = stringResource(R.string.toolbar_bold),
            onClick = { onInsertToken("**bold**") },
        )
        ToolbarIcon(
            icon = Lucide.List,
            contentDescription = stringResource(R.string.toolbar_bullet),
            onClick = { onInsertToken("- ") },
        )
        ToolbarIcon(
            icon = Lucide.ListChecks,
            contentDescription = stringResource(R.string.toolbar_checklist),
            onClick = { onInsertToken("- [ ] ") },
        )
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun MarkdownToolbarLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            MarkdownToolbar(onInsertToken = {})
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MarkdownToolbarDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            MarkdownToolbar(onInsertToken = {})
        }
    }
}
