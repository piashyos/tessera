package app.tessera.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tessera.R

@Composable
fun LanguagePicker(
    selectedTag: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        "" to R.string.settings_language_system,
        "en" to R.string.settings_language_en,
        "ru" to R.string.settings_language_ru,
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (tag, labelRes) ->
            SelectableSettingsTile(
                label = stringResource(labelRes),
                selected = selectedTag == tag,
                onClick = { onSelect(tag) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
