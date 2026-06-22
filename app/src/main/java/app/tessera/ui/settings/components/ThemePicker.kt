package app.tessera.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide

@Composable
fun ThemePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelectableSettingsTile(
            label = stringResource(R.string.settings_theme_auto),
            selected = selected == ThemeMode.AUTO,
            onClick = { onSelect(ThemeMode.AUTO) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableSettingsTile(
                label = stringResource(R.string.settings_theme_light_dynamic),
                selected = selected == ThemeMode.LIGHT_DYNAMIC,
                onClick = { onSelect(ThemeMode.LIGHT_DYNAMIC) },
                modifier = Modifier.weight(1f),
            )
            SelectableSettingsTile(
                label = stringResource(R.string.settings_theme_dark_dynamic),
                selected = selected == ThemeMode.DARK_DYNAMIC,
                onClick = { onSelect(ThemeMode.DARK_DYNAMIC) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableSettingsTile(
                label = stringResource(R.string.settings_theme_light_static),
                selected = selected == ThemeMode.LIGHT_STATIC,
                onClick = { onSelect(ThemeMode.LIGHT_STATIC) },
                modifier = Modifier.weight(1f),
            )
            SelectableSettingsTile(
                label = stringResource(R.string.settings_theme_dark_static),
                selected = selected == ThemeMode.DARK_STATIC,
                onClick = { onSelect(ThemeMode.DARK_STATIC) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.settings_theme_static_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun SelectableSettingsTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Lucide.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(modifier = Modifier.size(18.dp))
            }
        }
    }
}
