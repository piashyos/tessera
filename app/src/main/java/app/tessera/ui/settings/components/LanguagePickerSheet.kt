package app.tessera.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tessera.R
import app.tessera.core.ui.tessera.TesseraBottomSheet
import app.tessera.core.ui.theme.TesseraSpacing

private val languageOptions = listOf(
    "" to R.string.settings_language_system,
    "en" to R.string.settings_language_en,
    "ru" to R.string.settings_language_ru,
)

/** Bottom sheet listing the supported app languages; selecting one dismisses the sheet. */
@Composable
fun LanguagePickerSheet(
    selectedTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TesseraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        header = { Text(stringResource(R.string.settings_section_language)) },
    ) {
        languageOptions.forEach { (tag, labelRes) ->
            val selected = selectedTag == tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = {
                            onSelect(tag)
                            onDismiss()
                        },
                    )
                    .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
