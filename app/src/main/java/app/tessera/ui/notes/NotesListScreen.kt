package app.tessera.ui.notes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.components.EdgeFadeScrim
import app.tessera.core.ui.components.GlassFab
import app.tessera.core.ui.components.ScrimEdge
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.ui.notes.components.BottomSearchBar
import app.tessera.ui.notes.components.SwipeableNoteCard
import app.tessera.ui.notes.components.TagFilterBar
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tag
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/** Height of the collapsing header Row, shared with the grid's top content padding. */
private val HeaderHeight = 56.dp

/**
 * Approximate per-item height (px) used only to derive a monotonically growing scroll
 * value for the decorative header scale. Intentionally a rough heuristic, not a measured
 * height; it never needs to be exact because the scale clamps at the collapse threshold.
 */
private const val ApproxItemHeightPx = 200

@Composable
fun NotesListScreen(
    state: NotesListState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onCreateNote: () -> Unit,
    onNoteClick: (NoteId) -> Unit = {},
    onTogglePin: (NoteId) -> Unit = {},
    onDeleteNote: (Note) -> Unit = {},
    onUndoDelete: (NoteId) -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    isRefreshing: Boolean = false,
) {
    var isSearchFocused by remember { mutableStateOf(false) }
    var tagBarExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.note_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val handleDelete: (Note) -> Unit = { note ->
        onDeleteNote(note)
        scope.launch {
            val result =
                snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) {
                onUndoDelete(note.id)
            }
        }
    }
    val fabAlpha by animateFloatAsState(
        targetValue = if (isSearchFocused) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
        label = "fabAlpha",
    )
    val fabScale by animateFloatAsState(
        targetValue = if (isSearchFocused) 0.6f else 1f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
        label = "fabScale",
    )
    val scrimColor = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        0.76f,
    )
    val background =
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                scrimColor,
            ),
        )
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyStaggeredGridState()
    // Assumes ~ApproxItemHeightPx-tall items; this is an intentional approximation that only
    // feeds the decorative header scale effect, so it does not need to be pixel-accurate.
    val scrollPx by remember {
        derivedStateOf {
            gridState.firstVisibleItemScrollOffset + gridState.firstVisibleItemIndex * ApproxItemHeightPx
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.NotesListRoot),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .hazeSource(hazeState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
            Box(modifier = Modifier.weight(1f)) {
                NotesContent(
                    state = state,
                    gridState = gridState,
                    modifier = Modifier.fillMaxSize(),
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    onNoteClick = onNoteClick,
                    onTogglePin = onTogglePin,
                    onDeleteNote = handleDelete,
                )
                if (isSearchFocused) {
                    DismissFocusLayer {
                        focusManager.clearFocus(force = true)
                    }
                }
            }
        }

        EdgeFadeScrim(
            hazeState = hazeState,
            scrimColor = scrimColor,
            edge = ScrimEdge.Top,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(140.dp),
        )

        CollapsingHeader(
            title = stringResource(R.string.notes_all_notes),
            scrollPx = scrollPx,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        EdgeFadeScrim(
            hazeState = hazeState,
            scrimColor = scrimColor,
            edge = ScrimEdge.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .height(120.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = if (isSearchFocused) 8.dp else 18.dp),
        ) {
            if (isSearchFocused) {
                DismissFocusLayer {
                    focusManager.clearFocus(force = true)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TagFilterBar(
                        expanded = tagBarExpanded && !isSearchFocused,
                        availableTags = state.availableTags(),
                        selectedTags = state.selectedTags(),
                        onTagToggle = onTagToggle,
                        hazeState = hazeState,
                    )
                    BottomSearchBar(
                        query = state.query(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = FloatingShadowAmbient,
                                spotColor = FloatingShadowSpot,
                            ),
                        onQueryChange = onQueryChange,
                        onFocusChanged = { isSearchFocused = it },
                        hazeState = hazeState,
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val viewMode = state.viewMode()
                    val (toggleIcon, toggleDesc) = if (viewMode == NotesViewMode.Tiles) {
                        Lucide.Rows3 to stringResource(R.string.notes_view_mode_list)
                    } else {
                        Lucide.LayoutGrid to stringResource(R.string.notes_view_mode_tiles)
                    }
                    GlassFab(
                        onClick = if (isSearchFocused) null else onToggleViewMode,
                        size = 40.dp,
                        contentDescription = toggleDesc,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = fabAlpha
                                scaleX = fabScale
                                scaleY = fabScale
                            }
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = FloatingShadowAmbient,
                                spotColor = FloatingShadowSpot,
                                clip = false,
                            )
                            .testTag(UiTestTags.ViewModeToggle),
                    ) {
                        Icon(
                            imageVector = toggleIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    GlassFab(
                        onClick = if (isSearchFocused) null else { { tagBarExpanded = !tagBarExpanded } },
                        size = 40.dp,
                        contentDescription = stringResource(R.string.notes_filter_tags),
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = fabAlpha
                                scaleX = fabScale
                                scaleY = fabScale
                            }
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = FloatingShadowAmbient,
                                spotColor = FloatingShadowSpot,
                                clip = false,
                            )
                            .testTag(UiTestTags.TagFilterToggle),
                    ) {
                        Icon(
                            imageVector = Lucide.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    GlassFab(
                        onClick = if (isSearchFocused) null else onCreateNote,
                        size = 48.dp,
                        contentDescription = stringResource(R.string.create_note),
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = fabAlpha
                                scaleX = fabScale
                                scaleY = fabScale
                            }
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                ambientColor = FloatingShadowAmbient,
                                spotColor = FloatingShadowSpot,
                                clip = false,
                            )
                            .testTag(UiTestTags.CreateNoteFab),
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 86.dp),
        )
    }
}

@Composable
private fun BoxScope.DismissFocusLayer(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}

@Composable
fun CollapsingHeader(
    scrollPx: Int,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.notes_all_notes),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.graphicsLayer {
                val s = headerTitleScale(scrollPx, HEADER_COLLAPSE_THRESHOLD_PX)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(40.dp)
                .testTag(UiTestTags.OpenSettings),
        ) {
            Icon(
                imageVector = Lucide.Settings,
                contentDescription = stringResource(R.string.settings_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesContent(
    state: NotesListState,
    gridState: LazyStaggeredGridState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNoteClick: (NoteId) -> Unit,
    onTogglePin: (NoteId) -> Unit,
    onDeleteNote: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        NotesListState.Loading ->
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is NotesListState.Empty ->
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.query.isBlank()) {
                            stringResource(R.string.notes_empty_title)
                        } else {
                            stringResource(R.string.notes_search_empty_title, state.query)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (state.query.isBlank()) {
                            stringResource(R.string.notes_empty_subtitle)
                        } else {
                            stringResource(R.string.notes_search_empty_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        is NotesListState.Content -> {
            val sections = groupNotesByDate(state.notes)
            val columns = if (state.viewMode == NotesViewMode.Tiles) 2 else 1
            // The header is HeaderHeight tall and offset down by the status-bar inset, so the
            // grid must clear both to keep the first items below the header at rest.
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val gridTopPadding = HeaderHeight + statusBarTop
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxWidth(),
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(UiTestTags.NotesGrid),
                    contentPadding = PaddingValues(top = gridTopPadding, bottom = 120.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { section ->
                        item(span = StaggeredGridItemSpan.FullLine, key = "header-${section.group}") {
                            SectionHeader(group = section.group)
                        }
                        items(items = section.notes, key = { note -> note.id.value }) { note ->
                            SwipeableNoteCard(
                                note = note,
                                modifier = Modifier.animateItem(),
                                isTile = state.viewMode == NotesViewMode.Tiles,
                                onClick = { onNoteClick(note.id) },
                                onTogglePin = { onTogglePin(note.id) },
                                onDelete = { onDeleteNote(note) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(group: NoteGroup) {
    val labelRes = when (group) {
        NoteGroup.Pinned -> R.string.note_group_pinned
        NoteGroup.Today -> R.string.note_group_today
        NoteGroup.Yesterday -> R.string.note_group_yesterday
        NoteGroup.ThisWeek -> R.string.note_group_this_week
        NoteGroup.ThisMonth -> R.string.note_group_this_month
        NoteGroup.Earlier -> R.string.note_group_earlier
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .testTag(UiTestTags.sectionHeader(group.name)),
    )
}

private fun NotesListState.query(): String =
    when (this) {
        NotesListState.Loading -> ""
        is NotesListState.Empty -> query
        is NotesListState.Content -> query
    }

private fun NotesListState.availableTags(): List<String> =
    when (this) {
        NotesListState.Loading -> emptyList()
        is NotesListState.Empty -> availableTags
        is NotesListState.Content -> availableTags
    }

private fun NotesListState.selectedTags(): Set<String> =
    when (this) {
        NotesListState.Loading -> emptySet()
        is NotesListState.Empty -> selectedTags
        is NotesListState.Content -> selectedTags
    }

private fun NotesListState.viewMode(): NotesViewMode =
    when (this) {
        NotesListState.Loading -> NotesViewMode.Tiles
        is NotesListState.Empty -> viewMode
        is NotesListState.Content -> viewMode
    }
