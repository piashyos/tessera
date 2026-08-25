package app.tessera.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tessera.core.ui.theme.TesseraSpacing

/**
 * A settings section: an optional uppercase label above a `surfaceContainer` card with 16dp
 * corners. Rows are placed in the [content] column; separate them with [SettingsDivider].
 *
 * Note: dividers are explicit ([SettingsDivider]) rather than auto-inserted — Compose slot APIs
 * can't count an unknown number of slot children without a custom layout, and explicit dividers
 * keep the simple `ColumnScope` content API the plan specifies.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            androidx.compose.material3.Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = TesseraSpacing.lg, bottom = TesseraSpacing.sm),
            )
        }
        // Expressive settings group (PixelPlayer's ExpressiveSettingsGroup shape language):
        // a 24dp container on the surfaceContainerLow tier.
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/** Inset hairline separator between rows inside a [SettingsGroup]. */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 56.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
