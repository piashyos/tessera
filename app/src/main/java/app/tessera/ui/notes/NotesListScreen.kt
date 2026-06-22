package app.tessera.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.Color
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
import app.tessera.core.ui.rememberHaptics
import app.tessera.core.ui.components.EdgeFadeScrim
import app.tessera.core.ui.components.GlassFab
import app.tessera.core.ui.components.ScrimEdge
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import app.tessera.core.ui.theme.Motion
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.ui.navigation.LocalAnimatedVisibilityScope
import app.tessera.ui.navigation.LocalSharedTransitionScope
import app.tessera.ui.notes.components.BottomSearchBar
import app.tessera.ui.notes.components.FolderNamePopup
import app.tessera.ui.notes.components.FolderPickerPopup
import app.tessera.ui.notes.components.FoldersDrawer
import app.tessera.ui.notes.components.NoteContextMenu
import app.tessera.ui.notes.components.SwipeableNoteCard
import app.tessera.ui.notes.components.TagFilterBar
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/** Height of the collapsing header Row, shared with the grid's top content padding. */
/**
 * Total header height: top icon row (~56.dp) plus the title's resting band below it.
 * The grid's top content padding tracks this so the first card never starts under the header.
 */
private val HeaderTopRow = 56.dp
private val HeaderTitleRow = 56.dp
private val HeaderHeight = HeaderTopRow + HeaderTitleRow

/**
 * Approximate per-item height (px) used only to derive a monotonically growing scroll
 * value for the decorative header scale. Intentionally a rough heuristic, not a measured
 * height; it never needs to be exact because the scale clamps at the collapse threshold.
 */
private const val ApproxItemHeightPx = 200

@OptIn(ExperimentalSharedTransitionApi::class)
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
    onSelectFolder: (app.tessera.domain.model.FolderId?) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (app.tessera.domain.model.FolderId, String) -> Unit = { _, _ -> },
    onDeleteFolder: (app.tessera.domain.model.FolderId) -> Unit = {},
    onMoveNoteToFolder: (NoteId, app.tessera.domain.model.FolderId?) -> Unit = { _, _ -> },
    onEnterSelectionMode: (NoteId) -> Unit = {},
    onExitSelectionMode: () -> Unit = {},
    onToggleSelection: (NoteId) -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onDeleteSelected: () -> List<NoteId> = { emptyList() },
    onMoveSelectedToFolder: (app.tessera.domain.model.FolderId?) -> Unit = {},
    onRestoreNotes: (List<NoteId>) -> Unit = {},
    isRefreshing: Boolean = false,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val drawerWidthDp = 300.dp
    val drawerWidthPx = with(density) { drawerWidthDp.toPx() }
    // Single source of truth for the drawer's horizontal position. -drawerWidthPx is
    // fully closed (off-screen left); 0f is fully open. Driving the drawer through an
    // Animatable lets swipe gestures and button taps share the same animation.
    val drawerOffset = remember { androidx.compose.animation.core.Animatable(-drawerWidthPx) }
    val drawerOpen by remember { androidx.compose.runtime.derivedStateOf { drawerOffset.value > -drawerWidthPx * 0.5f } }
    val drawerScope = rememberCoroutineScope()
    val drawerSpring = remember {
        androidx.compose.animation.core.spring<Float>(
            dampingRatio = 0.85f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        )
    }
    val openDrawer: () -> Unit = {
        drawerScope.launch { drawerOffset.animateTo(0f, drawerSpring) }
    }
    val closeDrawer: () -> Unit = {
        drawerScope.launch { drawerOffset.animateTo(-drawerWidthPx, drawerSpring) }
    }
    var folderEditorTarget by remember { mutableStateOf<app.tessera.domain.model.Folder?>(null) }
    var folderCreatorVisible by remember { mutableStateOf(false) }
    var contextMenuNote by remember { mutableStateOf<Note?>(null) }
    var contextMenuAnchor by remember { mutableStateOf<androidx.compose.ui.unit.IntOffset?>(null) }
    var folderPickerNote by remember { mutableStateOf<Note?>(null) }
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

    val haptics = rememberHaptics()
    val selectionMode = state.selectionActive()
    val selection = state.selectedIds()
    val visibleNotes = (state as? NotesListState.Content)?.notes.orEmpty()
    val allSelected = visibleNotes.isNotEmpty() && selection.size >= visibleNotes.size
    var selectionMovePickerVisible by remember { mutableStateOf(false) }
    val selectionDeletedMessage = stringResource(R.string.notes_selection_deleted)

    BackHandler(enabled = selectionMode) { onExitSelectionMode() }

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
                    onLongClickNote = { note, offset ->
                        haptics.longPress()
                        contextMenuNote = note
                        contextMenuAnchor = offset
                    },
                    selectionMode = selectionMode,
                    selection = selection,
                    onToggleSelection = onToggleSelection,
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
            scrimColor = MaterialTheme.colorScheme.background,
            edge = ScrimEdge.Top,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(140.dp),
        )

        val currentFolderName = state.selectedFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.name
        } ?: stringResource(R.string.notes_all_notes)
        val selectionTitle = stringResource(R.string.notes_selection_count, selection.size)
        CollapsingHeader(
            title = if (selectionMode) selectionTitle else currentFolderName,
            scrollPx = scrollPx,
            viewMode = state.viewMode(),
            onToggleViewMode = { haptics.tick(); onToggleViewMode() },
            onOpenSettings = { haptics.tick(); onOpenSettings() },
            onOpenDrawer = { haptics.tick(); openDrawer() },
            selectionMode = selectionMode,
            allSelected = allSelected,
            onToggleSelectAll = { haptics.tick(); onToggleSelectAll() },
            onMoveSelected = { haptics.tick(); selectionMovePickerVisible = true },
            onDeleteSelected = {
                haptics.tick()
                val ids = onDeleteSelected()
                if (ids.isNotEmpty()) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = selectionDeletedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) onRestoreNotes(ids)
                    }
                }
            },
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Row 1 — tag filter bar paired with its toggle. CenterVertically keeps
                // the chips visually centered on the tag toggle FAB.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Box pins TagFilterBar to the End so the GlassFab below it stays
                    // anchored to the right edge even when the chips bar is collapsed
                    // (AnimatedVisibility reports 0 width when hidden).
                    Box(
                        modifier = Modifier.weight(1f, fill = true),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        TagFilterBar(
                            expanded = tagBarExpanded && !isSearchFocused,
                            availableTags = state.availableTags(),
                            selectedTags = state.selectedTags(),
                            onTagToggle = onTagToggle,
                            hazeState = hazeState,
                        )
                    }
                    GlassFab(
                        onClick = if (isSearchFocused) null else { { haptics.tick(); tagBarExpanded = !tagBarExpanded } },
                        size = 40.dp,
                        contentDescription = stringResource(R.string.notes_filter_tags),
                        hazeState = hazeState,
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
                }
                // Row 2 — search bar paired with the create FAB. On focus the FAB slot
                // (button + 12.dp gap) collapses to 0.dp so the weighted search bar grows
                // to the full row width.
                val createFabSlot by animateDpAsState(
                    targetValue = if (isSearchFocused) 0.dp else 60.dp,
                    animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
                    label = "createFabSlot",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomSearchBar(
                        query = state.query(),
                        modifier = Modifier
                            .weight(1f)
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
                    Box(
                        modifier = Modifier.width(createFabSlot),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                    GlassFab(
                        onClick = if (isSearchFocused) null else { { haptics.tick(); onCreateNote() } },
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
                            .testTag(UiTestTags.CreateNoteFab)
                            .then(
                                if (sharedScope != null && visibilityScope != null) {
                                    with(sharedScope) {
                                        Modifier.sharedElement(
                                            state = rememberSharedContentState(key = "note-card-draft"),
                                            animatedVisibilityScope = visibilityScope,
                                            boundsTransform = { _, _ -> tween(Motion.SharedElementDurationMs) },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
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
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 86.dp),
        )

        // Edge-swipe zone — captures a horizontal drag that originates within ~24dp
        // of the leading edge while the drawer is closed, opening it as the user pulls
        // right. Stays at the closed state's offset, so it never blocks taps once the
        // drawer is open (the scrim takes over then).
        if (!drawerOpen) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(drawerWidthPx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, delta ->
                                drawerScope.launch {
                                    drawerOffset.snapTo(
                                        (drawerOffset.value + delta).coerceIn(-drawerWidthPx, 0f),
                                    )
                                }
                            },
                            onDragEnd = {
                                drawerScope.launch {
                                    val target = if (drawerOffset.value > -drawerWidthPx * 0.5f) 0f else -drawerWidthPx
                                    drawerOffset.animateTo(target, drawerSpring)
                                }
                            },
                            onDragCancel = {
                                drawerScope.launch {
                                    drawerOffset.animateTo(-drawerWidthPx, drawerSpring)
                                }
                            },
                        )
                    },
            )
        }

        // Scrim — alpha tracks the drawer's open progress, taps dismiss.
        val drawerProgress = ((drawerOffset.value + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
        if (drawerProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * drawerProgress))
                    .then(
                        if (drawerOpen) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = closeDrawer,
                            )
                        } else Modifier,
                    ),
            )
        }

        // Drawer panel — positioned by drawerOffset and draggable in both directions
        // so the user can swipe it closed.
        FoldersDrawer(
            folders = state.folders,
            selectedFolderId = state.selectedFolderId,
            hazeState = hazeState,
            onSelectAll = { onSelectFolder(null); closeDrawer() },
            onSelectFolder = { id -> onSelectFolder(id); closeDrawer() },
            onCreateFolder = { folderCreatorVisible = true },
            onRenameFolder = { folder -> folderEditorTarget = folder },
            onDeleteFolder = { folder -> onDeleteFolder(folder.id) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { androidx.compose.ui.unit.IntOffset(drawerOffset.value.toInt(), 0) }
                .pointerInput(drawerWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, delta ->
                            drawerScope.launch {
                                drawerOffset.snapTo(
                                    (drawerOffset.value + delta).coerceIn(-drawerWidthPx, 0f),
                                )
                            }
                        },
                        onDragEnd = {
                            drawerScope.launch {
                                val target = if (drawerOffset.value > -drawerWidthPx * 0.5f) 0f else -drawerWidthPx
                                drawerOffset.animateTo(target, drawerSpring)
                            }
                        },
                        onDragCancel = {
                            drawerScope.launch {
                                val target = if (drawerOffset.value > -drawerWidthPx * 0.5f) 0f else -drawerWidthPx
                                drawerOffset.animateTo(target, drawerSpring)
                            }
                        },
                    )
                },
        )

        // Create folder popup
        FolderNamePopup(
            visible = folderCreatorVisible,
            initialName = "",
            onSubmit = { name ->
                onCreateFolder(name)
                folderCreatorVisible = false
            },
            onDismiss = { folderCreatorVisible = false },
            hazeState = hazeState,
        )

        // Rename folder popup
        val renameTarget = folderEditorTarget
        FolderNamePopup(
            visible = renameTarget != null,
            initialName = renameTarget?.name.orEmpty(),
            onSubmit = { name ->
                if (renameTarget != null) onRenameFolder(renameTarget.id, name)
                folderEditorTarget = null
            },
            onDismiss = { folderEditorTarget = null },
            hazeState = hazeState,
        )

        // Long-press context menu
        val ctxNote = contextMenuNote
        NoteContextMenu(
            visible = ctxNote != null,
            anchor = contextMenuAnchor,
            isPinned = ctxNote?.isPinned == true,
            onDismiss = {
                contextMenuNote = null
                contextMenuAnchor = null
            },
            onTogglePin = { haptics.tick(); ctxNote?.let { onTogglePin(it.id) } },
            onSelect = { ctxNote?.let { onEnterSelectionMode(it.id) } },
            onMove = {
                folderPickerNote = ctxNote
                contextMenuNote = null
            },
            onDelete = { haptics.tick(); ctxNote?.let { handleDelete(it) } },
            hazeState = hazeState,
        )

        // Folder picker (Move-to)
        val pickerNote = folderPickerNote
        FolderPickerPopup(
            visible = pickerNote != null,
            folders = state.folders,
            onPick = { folderId ->
                if (pickerNote != null) onMoveNoteToFolder(pickerNote.id, folderId)
            },
            onDismiss = { folderPickerNote = null },
            hazeState = hazeState,
        )

        // Folder picker for batch move of the current selection
        FolderPickerPopup(
            visible = selectionMovePickerVisible,
            folders = state.folders,
            onPick = { folderId -> onMoveSelectedToFolder(folderId) },
            onDismiss = { selectionMovePickerVisible = false },
            hazeState = hazeState,
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
    viewMode: NotesViewMode,
    onToggleViewMode: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.notes_all_notes),
    onOpenDrawer: () -> Unit = {},
    selectionMode: Boolean = false,
    allSelected: Boolean = false,
    onToggleSelectAll: () -> Unit = {},
    onMoveSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderHeight),
    ) {
        // Top row. Normally: burger (leading) + view-mode + settings (trailing).
        // In selection mode the same slots become: select-all + move + delete.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderTopRow),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = if (selectionMode) onToggleSelectAll else onOpenDrawer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (selectionMode) Lucide.ListChecks else Lucide.Menu,
                    contentDescription = stringResource(
                        if (selectionMode) {
                            if (allSelected) R.string.notes_selection_deselect_all else R.string.notes_selection_select_all
                        } else {
                            R.string.folders_open_drawer
                        },
                    ),
                    tint = if (selectionMode && allSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    IconButton(
                        onClick = onMoveSelected,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.FolderInput,
                            contentDescription = stringResource(R.string.note_action_move),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.Trash2,
                            contentDescription = stringResource(R.string.note_action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    val (toggleIcon, toggleDesc) = if (viewMode == NotesViewMode.Tiles) {
                        Lucide.Rows3 to stringResource(R.string.notes_view_mode_list)
                    } else {
                        Lucide.LayoutGrid to stringResource(R.string.notes_view_mode_tiles)
                    }
                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag(UiTestTags.ViewModeToggle),
                    ) {
                        Icon(
                            imageVector = toggleIcon,
                            contentDescription = toggleDesc,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // Title row beneath the burger. At rest the title sits in this second band
        // (visually directly under the burger). On scroll it animates upward and
        // shrinks until it lines up horizontally with the icons in the top row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderTitleRow),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val topRowPx = with(density) { HeaderTopRow.toPx() }
            // Slide the title past the burger icon (40dp button + 8dp gap) when
            // collapsed so the two don't overlap horizontally.
            val burgerOffsetPx = with(density) { 48.dp.toPx() }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .graphicsLayer {
                        val progress = (scrollPx.toFloat() / HEADER_COLLAPSE_THRESHOLD_PX).coerceIn(0f, 1f)
                        val s = 1f - ((1f - HEADER_COLLAPSED_SCALE) * progress)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationY = -topRowPx * progress
                        translationX = burgerOffsetPx * progress
                    },
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
    onLongClickNote: (Note, androidx.compose.ui.unit.IntOffset) -> Unit,
    selectionMode: Boolean,
    selection: Set<NoteId>,
    onToggleSelection: (NoteId) -> Unit,
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
                val isTilesMode = state.viewMode == NotesViewMode.Tiles
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(UiTestTags.NotesGrid),
                    contentPadding = PaddingValues(top = gridTopPadding, bottom = 120.dp),
                    verticalItemSpacing = if (isTilesMode) 12.dp else 0.dp,
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
                                selectionMode = selectionMode,
                                selected = note.id in selection,
                                onToggleSelect = { onToggleSelection(note.id) },
                                onClick = { onNoteClick(note.id) },
                                onTogglePin = { onTogglePin(note.id) },
                                onDelete = { onDeleteNote(note) },
                                onLongClick = { offset -> onLongClickNote(note, offset) },
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 10.dp)
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

private fun NotesListState.selectionActive(): Boolean =
    (this as? NotesListState.Content)?.selectionMode == true

private fun NotesListState.selectedIds(): Set<NoteId> =
    (this as? NotesListState.Content)?.selection ?: emptySet()
