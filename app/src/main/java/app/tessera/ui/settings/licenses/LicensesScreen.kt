package app.tessera.ui.settings.licenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.tessera.TesseraBottomSheet
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.ui.settings.components.SettingsChevron
import app.tessera.ui.settings.components.SettingsDivider
import app.tessera.ui.settings.components.SettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the plugin-generated license files off the main thread; empty until loaded. */
@Composable
fun rememberLicenses(): List<LicenseEntry> {
    val context = LocalContext.current
    return produceState(emptyList<LicenseEntry>(), context) {
        value = withContext(Dispatchers.IO) {
            val res = context.resources
            val metadata = res.openRawResource(R.raw.third_party_license_metadata).use { it.readBytes() }
            val texts = res.openRawResource(R.raw.third_party_licenses).use { it.readBytes() }
            LicensesParser.parse(metadata.toString(Charsets.UTF_8), texts)
        }
    }.value
}

/** Alphabetical list of open-source dependencies; a tap opens the license text in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    licenses: List<LicenseEntry>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    var selected by remember { mutableStateOf<LicenseEntry?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.LicensesRoot)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_licenses),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(UiTestTags.LicensesBack),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = TesseraSpacing.xxxl),
        ) {
            itemsIndexed(licenses) { index, entry ->
                SettingsRow(
                    title = entry.name,
                    onClick = { selected = entry },
                    trailing = { SettingsChevron() },
                )
                // Between rows only; rows without an icon, so inset to the text on both sides.
                if (index < licenses.lastIndex) {
                    SettingsDivider(modifier = Modifier.padding(end = TesseraSpacing.lg), inset = TesseraSpacing.lg)
                }
            }
        }
    }

    selected?.let { entry ->
        TesseraBottomSheet(
            onDismissRequest = { selected = null },
            header = { Text(entry.name) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TesseraSpacing.xxl),
            ) {
                SelectionContainer {
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(UiTestTags.LicensesText),
                    )
                }
            }
        }
    }
}
