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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.GlassFab
import app.tessera.core.ui.components.ScrimEdge
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.ui.notes.components.BottomSearchBar
import app.tessera.ui.notes.components.SwipeableNoteCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun NotesListScreen(
    state: NotesListState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    TopHeaderCapsule(onOpenSettings = onOpenSettings)
                    NotesContent(
                        state = state,
                        modifier = Modifier.weight(1f),
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        onNoteClick = onNoteClick,
                        onTogglePin = onTogglePin,
                        onDeleteNote = handleDelete,
                    )
                }
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
                BottomSearchBar(
                    query = state.query(),
                    availableTags = state.availableTags(),
                    selectedTags = state.selectedTags(),
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = FloatingShadowAmbient,
                            spotColor = FloatingShadowSpot,
                        ),
                    onQueryChange = onQueryChange,
                    onTagToggle = onTagToggle,
                    onFocusChanged = { isSearchFocused = it },
                    showFilters = !isSearchFocused,
                    hazeState = hazeState,
                )
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
private fun TopHeaderCapsule(onOpenSettings: () -> Unit) {
    FloatingCapsule(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "T",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Tessera",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesContent(
    state: NotesListState,
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

        is NotesListState.Content ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = modifier.fillMaxWidth(),
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(UiTestTags.NotesGrid),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.notes, key = { note -> note.id.value }) { note ->
                        SwipeableNoteCard(
                            note = note,
                            modifier = Modifier.animateItem(),
                            onClick = { onNoteClick(note.id) },
                            onTogglePin = { onTogglePin(note.id) },
                            onDelete = { onDeleteNote(note) },
                        )
                    }
                }
            }
    }
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
