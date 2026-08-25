package app.tessera.ui.editor.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import org.wordpress.aztec.AztecTextFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.TextFields

@Composable
fun MarkdownToolbar(
    onOpenMenu: () -> Unit,
    actions: RichBodyActions?,
    activeFormats: Set<AztecTextFormat>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .size(34.dp)
                .testTag(UiTestTags.EditorFormatMenuTrigger),
        ) {
            Icon(
                imageVector = Icons.Rounded.TextFields,
                contentDescription = stringResource(R.string.toolbar_format),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        FormatToggle(
            icon = Icons.Rounded.FormatBold,
            contentDescriptionRes = R.string.toolbar_bold,
            active = AztecTextFormat.FORMAT_BOLD in activeFormats,
            onClick = { actions?.toggleBold() },
        )
        FormatToggle(
            icon = Icons.Rounded.FormatItalic,
            contentDescriptionRes = R.string.toolbar_italic,
            active = AztecTextFormat.FORMAT_ITALIC in activeFormats,
            onClick = { actions?.toggleItalic() },
        )
        FormatToggle(
            icon = Icons.Rounded.FormatStrikethrough,
            contentDescriptionRes = R.string.toolbar_strikethrough,
            active = AztecTextFormat.FORMAT_STRIKETHROUGH in activeFormats,
            onClick = { actions?.toggleStrikethrough() },
        )
        FormatToggle(
            icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
            contentDescriptionRes = R.string.toolbar_bullet,
            active = AztecTextFormat.FORMAT_UNORDERED_LIST in activeFormats,
            onClick = { actions?.toggleBulletList() },
        )
        FormatToggle(
            icon = Icons.Rounded.Checklist,
            contentDescriptionRes = R.string.toolbar_checklist,
            active = AztecTextFormat.FORMAT_TASK_LIST in activeFormats,
            onClick = { actions?.toggleTaskList() },
        )
    }
}

@Composable
private fun FormatToggle(
    icon: ImageVector,
    contentDescriptionRes: Int,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun MarkdownToolbarLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            MarkdownToolbar(onOpenMenu = {}, actions = null, activeFormats = emptySet())
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
            MarkdownToolbar(onOpenMenu = {}, actions = null, activeFormats = emptySet())
        }
    }
}
