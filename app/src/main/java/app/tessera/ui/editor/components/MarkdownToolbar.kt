package app.tessera.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Italic
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Strikethrough

@Composable
fun MarkdownToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrikethrough: () -> Unit,
    onHeading1: () -> Unit,
    onHeading2: () -> Unit,
    onBullet: () -> Unit,
    onTaskList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIcon(Lucide.Bold, stringResource(R.string.toolbar_bold), onClick = onBold)
        ToolbarIcon(Lucide.Italic, stringResource(R.string.toolbar_italic), onClick = onItalic)
        ToolbarIcon(Lucide.Strikethrough, stringResource(R.string.toolbar_strikethrough), onClick = onStrikethrough)
        ToolbarIcon(Lucide.Heading1, stringResource(R.string.toolbar_h1), onClick = onHeading1)
        ToolbarIcon(Lucide.Heading2, stringResource(R.string.toolbar_h2), onClick = onHeading2)
        ToolbarIcon(Lucide.List, stringResource(R.string.toolbar_bullet), onClick = onBullet)
        ToolbarIcon(Lucide.ListChecks, stringResource(R.string.toolbar_checklist), onClick = onTaskList)
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
            MarkdownToolbar(
                onBold = {},
                onItalic = {},
                onStrikethrough = {},
                onHeading1 = {},
                onHeading2 = {},
                onBullet = {},
                onTaskList = {},
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
private fun MarkdownToolbarDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            MarkdownToolbar(
                onBold = {},
                onItalic = {},
                onStrikethrough = {},
                onHeading1 = {},
                onHeading2 = {},
                onBullet = {},
                onTaskList = {},
            )
        }
    }
}
