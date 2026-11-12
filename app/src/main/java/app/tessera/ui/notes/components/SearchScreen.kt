package app.tessera.ui.notes.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraSpacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sell

/**
 * Full-screen search surface (see redesign spec §"Search"). When the query and
 * all filters are empty it shows the landing — filter shortcuts, recent searches
 * and popular tags. Once a query is entered (or a filter narrows the list) the
 * caller-supplied [resultsContent] is shown instead.
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onCollapse: () -> Unit,
    showResults: Boolean,
    folderValue: String,
    tagValue: String,
    periodValue: String,
    recentSearches: List<String>,
    popularTags: List<String>,
    onPickFolder: () -> Unit,
    onPickTag: () -> Unit,
    onPickPeriod: () -> Unit,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPopularTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    resultsContent: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                onSubmitQuery = onSubmitQuery,
                onCollapse = onCollapse,
            )
            // Filters stay pinned so an applied filter can always be changed or cleared.
            SectionHeader(stringResource(R.string.search_filters_header))
            FilterRow(
                icon = Icons.Rounded.Folder,
                label = stringResource(R.string.search_filter_folder),
                value = folderValue,
                onClick = onPickFolder,
            )
            FilterRow(
                icon = Icons.Rounded.Sell,
                label = stringResource(R.string.search_filter_tag),
                value = tagValue,
                onClick = onPickTag,
            )
            FilterRow(
                icon = Icons.Rounded.CalendarToday,
                label = stringResource(R.string.search_filter_period),
                value = periodValue,
                onClick = onPickPeriod,
            )
            if (showResults) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { resultsContent() }
            } else {
                SearchSuggestions(
                    modifier = Modifier.weight(1f),
                    recentSearches = recentSearches,
                    popularTags = popularTags,
                    onRecentClick = onRecentClick,
                    onRecentRemove = onRecentRemove,
                    onClearHistory = onClearHistory,
                    onPopularTagClick = onPopularTagClick,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: () -> Unit,
    onCollapse: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.md),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(start = TesseraSpacing.lg, end = TesseraSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmitQuery() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                    ),
                )
                IconButton(onClick = { if (query.isEmpty()) onCollapse() else onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(
                            if (query.isEmpty()) R.string.search_close else R.string.search_clear,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSuggestions(
    recentSearches: List<String>,
    popularTags: List<String>,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPopularTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = TesseraSpacing.xxl),
    ) {
        if (recentSearches.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_recent_header))
            recentSearches.forEach { entry ->
                RecentRow(
                    text = entry,
                    onClick = { onRecentClick(entry) },
                    onRemove = { onRecentRemove(entry) },
                )
            }
            Text(
                text = stringResource(R.string.search_clear_history),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearHistory)
                    .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.md),
            )
        }

        if (popularTags.isNotEmpty()) {
            SectionHeader(stringResource(R.string.search_popular_tags))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TesseraSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
            ) {
                popularTags.forEach { tag ->
                    TagPill(tag = tag, onClick = { onPopularTagClick(tag) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = TesseraSpacing.lg,
            end = TesseraSpacing.lg,
            top = TesseraSpacing.lg,
            bottom = TesseraSpacing.sm,
        ),
    )
}

@Composable
private fun FilterRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RecentRow(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = TesseraSpacing.lg, end = TesseraSpacing.xs, top = TesseraSpacing.xs, bottom = TesseraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = TesseraSpacing.sm),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.search_recent_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TagPill(tag: String, onClick: () -> Unit) {
    val tagColors = app.tessera.core.ui.tessera.rememberTagChipColors(tag)
    val tagIcon = app.tessera.core.ui.tessera.rememberTagIcon(tag)
    Surface(
        color = tagColors.container,
        contentColor = tagColors.content,
        shape = androidx.compose.foundation.shape.CircleShape,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = TesseraSpacing.md, vertical = TesseraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
        ) {
            if (tagIcon != null) app.tessera.core.ui.tessera.EntityIconView(tagIcon, tint = tagColors.content, size = 16.dp)
            Text(
                text = if (tagIcon != null) tag else "#$tag",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** A selectable option for [SearchOptionPopup]. */
data class SearchOption(val key: String, val label: String)

/** Single-choice modal used by the search filter rows (tag / period). */
@Composable
fun SearchOptionPopup(
    visible: Boolean,
    title: String,
    options: List<SearchOption>,
    selectedKey: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
) {
    app.tessera.core.ui.components.TesseraPopup(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        options.forEach { option ->
            val selected = option.key == selectedKey
            Surface(
                onClick = { onPick(option.key); onDismiss() },
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TesseraSpacing.md, vertical = TesseraSpacing.md),
                )
            }
        }
    }
}
