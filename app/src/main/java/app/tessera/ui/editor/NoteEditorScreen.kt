package app.tessera.ui.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.rememberHaptics
import app.tessera.core.ui.tessera.MembersSheet
import app.tessera.core.ui.tessera.TesseraTopBar
import app.tessera.core.ui.tessera.tesseraGlass
import app.tessera.core.ui.tessera.TesseraTopBarIconButton
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.tesseraGlassShadow
import app.tessera.core.ui.theme.TesseraMotion
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.EditorFormatBar
import app.tessera.ui.editor.components.MarkdownFormatMenu
import app.tessera.ui.editor.components.NoteBody
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.components.EditorMeta
import app.tessera.ui.editor.components.RichBodyActions
import app.tessera.ui.editor.engine.ObjectImageSource
import app.tessera.ui.editor.components.TableActionsBar
import app.tessera.ui.editor.components.TableBarState
import app.tessera.ui.editor.components.TagChipsExpandable
import app.tessera.ui.editor.components.TagPickerPopup
import app.tessera.ui.notes.noteDateLabel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorState,
    isDraftEntry: Boolean = false,
    onContentChange: (String) -> Unit,
    /** Save what is pending now; called when the screen stops (after the native editor handed out its last edit). */
    onFlush: () -> Unit = {},
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMakeShared: () -> Unit = {},
    onManageAccess: () -> Unit = {},
    onUnshare: () -> Unit = {},
    onLeave: () -> Unit = {},
    onDeleteForAll: () -> Unit = {},
    onInvite: (String, app.tessera.domain.model.ShareRole) -> Unit = { _, _ -> },
    onChangeRole: (app.tessera.domain.model.UserId, app.tessera.domain.model.ShareRole) -> Unit = { _, _ -> },
    onRemoveMember: (app.tessera.domain.model.UserId) -> Unit = {},
    onInviteInputChanged: () -> Unit = {},
    onDismissMembers: () -> Unit = {},
    onClearShareError: () -> Unit = {},
    imageSource: ObjectImageSource? = null,
    onInsertImageUri: (Uri) -> Unit = {},
    insertedImageIds: Flow<String> = emptyFlow(),
    consentRequests: Flow<android.app.PendingIntent> = emptyFlow(),
) {
    val haptics = rememberHaptics()
    var actions by remember { mutableStateOf<RichBodyActions?>(null) }
    // Leaving: the editor hands over its pending edit FIRST, then the host saves once (the VM text lags the editor by the
    // engine's debounce; saving before the hand-over writes a stale text and then the real one). The system Back goes through
    // here too: this handler sits deeper than the navigation's own and takes precedence.
    val handOverEdit = { actions?.flushPendingEdit() }
    BackHandler { handOverEdit(); onBack() }
    // The table the cursor is in: the bar of its cell replaces the format bar (the engine does the edits).
    var nativeTableBar by remember { mutableStateOf<TableBarState?>(null) }

    // The closures captured into `actions` (set once when the editor reports ready) must not see stale state.
    val currentContent by rememberUpdatedState(state.content)
    val currentOnContentChange by rememberUpdatedState(onContentChange)
    val currentOnInsertImageUri by rememberUpdatedState(onInsertImageUri)

    // --- Image source launchers (gallery + camera) ---
    val context = LocalContext.current
    // Gallery: photo picker restricted to images. A null result = user cancelled.
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) currentOnInsertImageUri(uri) }
    // Camera: capture into a FileProvider URI under cacheDir/camera/. Held in state across the
    // TakePicture round-trip so the success callback knows which file was written.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) currentOnInsertImageUri(uri)
        pendingCameraUri = null
    }
    val launchCamera: () -> Unit = {
        val uri = createCameraCaptureUri(context)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }
    // Camera needs the CAMERA runtime permission; request it first when not already granted.
    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }
    val onInsertImageClick: () -> Unit = {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
    val onCaptureImageClick: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
    val onImageCommitted: (Uri) -> Boolean = { uri -> currentOnInsertImageUri(uri); true }

    val wrapActions: (RichBodyActions) -> RichBodyActions = { base ->
        object : RichBodyActions by base {
            override fun onInsertImage() = onInsertImageClick()
            override fun onCaptureImage() = onCaptureImageClick()
        }
    }

    val hazeState = remember { HazeState() }
    var tagPickerVisible by remember { mutableStateOf(false) }
    var overflowVisible by remember { mutableStateOf(false) }
    var formatMenuExpanded by remember { mutableStateOf(false) } // the ⋯ sheet; the bar's ⋯ button toggles it
    var leaveConfirmVisible by remember { mutableStateOf(false) }
    var deleteForAllConfirmVisible by remember { mutableStateOf(false) }
    var unshareConfirmVisible by remember { mutableStateOf(false) }
    // Live formats at the editor's caret: what the bar and the ⋯ menu show.
    var formatState by remember { mutableStateOf(FormatState.Empty) }
    val imeVisible = WindowInsets.isImeVisible
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barExtentPx by remember { androidx.compose.runtime.mutableIntStateOf(0) } // the floating format bar with its margins

    val tableBarShown = nativeTableBar != null
    LaunchedEffect(imeVisible, tableBarShown, state.isReadOnly, formatMenuExpanded) {
        if (!imeVisible || tableBarShown || state.isReadOnly) formatMenuExpanded = false
    }
    // Any button of the bar but ⋯ does its job and closes the sheet.
    val barActions = remember(actions) { actions?.closing { formatMenuExpanded = false } }

    // After the VM stores an image and emits its id, splice the span at the editor caret. Reading
    // `actions` inside the collector picks up the live editor action object once it's ready.
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(insertedImageIds) {
        insertedImageIds.collect { imageId -> currentActions?.insertImage(imageId) }
    }

    // Launch Drive consent prompts: a recipient's first image download (or the owner's first upload)
    // with no cached token emits a PendingIntent here; StartIntentSenderForResult surfaces the
    // account/scope chooser. The result is ignored — the next download/upload retry picks up the
    // freshly-granted token.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* token is re-fetched lazily on the next attempt */ }
    LaunchedEffect(consentRequests) {
        consentRequests.collect { pendingIntent ->
            runCatching {
                consentLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(pendingIntent).build(),
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag(UiTestTags.EditorRoot)) {
        // The blur source is the whole screen (top bar included); the glass bar and the sheet's backdrop are its siblings.
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            Scaffold(
                // Transparent so the app-wide background + film grain shows through the editor.
                containerColor = Color.Transparent,
                topBar = {
                    TesseraTopBar(
                        title = {},
                        navigationIcon = {
                            TesseraTopBarIconButton(
                                onClick = { haptics.tick(); handOverEdit(); onBack() },
                                contentDescription = stringResource(R.string.editor_back),
                                modifier = Modifier.testTag(UiTestTags.EditorBack),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        actions = {
                            // Each action is wrapped in a stable `key` so that when one button toggles
                            // (e.g. share state resolves and the delete button appears/disappears) Compose
                            // keeps every other button's node identity instead of positionally reusing a
                            // removed slot — which otherwise briefly morphs the overflow icon into the
                            // trash icon on each recomposition while typing.
                            // Pinning is a content edit — viewers can't pin.
                            key("editor-action-pin") {
                                if (state.canEditContent) {
                                    TesseraTopBarIconButton(
                                        onClick = { haptics.tick(); onTogglePin() },
                                        contentDescription = stringResource(
                                            if (state.isPinned) R.string.editor_unpin else R.string.editor_pin,
                                        ),
                                        modifier = Modifier.testTag(UiTestTags.EditorPin),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PushPin,
                                            contentDescription = null,
                                            tint = if (state.isPinned) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                            // Trash deletes a private note directly. For shared notes, deletion is
                            // "delete for everyone" and lives in the overflow menu (owner/manager only).
                            key("editor-action-delete") {
                                if (!state.isShared) {
                                    TesseraTopBarIconButton(
                                        onClick = { haptics.tick(); handOverEdit(); onDelete() },
                                        contentDescription = stringResource(R.string.editor_delete),
                                        modifier = Modifier.testTag(UiTestTags.EditorDelete),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                            val hasOverflow = state.canMakeShared || state.canManageAccess ||
                                state.canUnshare || state.canLeave || state.canDeleteForAll
                            key("editor-action-overflow") {
                                if (hasOverflow) {
                                    Box {
                                    TesseraTopBarIconButton(
                                        onClick = { haptics.tick(); overflowVisible = true },
                                        contentDescription = stringResource(R.string.editor_more),
                                        modifier = Modifier.testTag(UiTestTags.EditorOverflow),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = overflowVisible,
                                        onDismissRequest = { overflowVisible = false },
                                    ) {
                                        if (state.canMakeShared) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.editor_make_shared)) },
                                                leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                                                onClick = { overflowVisible = false; onMakeShared() },
                                            )
                                        }
                                        if (state.canManageAccess) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.editor_manage_access)) },
                                                leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                                                onClick = { overflowVisible = false; onManageAccess() },
                                            )
                                        }
                                        if (state.canUnshare) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.editor_make_private)) },
                                                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                                onClick = {
                                                    overflowVisible = false
                                                    unshareConfirmVisible = true
                                                },
                                            )
                                        }
                                        if (state.canLeave) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.editor_leave)) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                                                onClick = { overflowVisible = false; leaveConfirmVisible = true },
                                            )
                                        }
                                        if (state.canDeleteForAll) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.editor_delete_for_all),
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                onClick = {
                                                    overflowVisible = false
                                                    deleteForAllConfirmVisible = true
                                                },
                                            )
                                        }
                                    }
                                }
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                // The note runs under the glass format bar (blurred through it): the bar is not in the Scaffold, so the Column keeps clear of the
                // keyboard only. Without the keyboard it reaches the screen edge, under the gesture strip.
                val barInsetPx = if (imeVisible && !state.isReadOnly) barExtentPx else 0
                Column(
                    modifier = Modifier
                        .padding(
                            top = padding.calculateTopPadding(),
                            bottom = if (imeVisible) with(density) { WindowInsets.ime.getBottom(density).toDp() } else 0.dp,
                        )
                        .fillMaxSize(),
                ) {
                    EditorMeta(
                        folderBreadcrumb = state.folderBreadcrumb,
                        relativeDate = if (state.updatedAt > 0L) noteDateLabel(state.updatedAt) else "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                    TagChipsExpandable(
                        expanded = true,
                        tags = state.tags,
                        onRemoveTag = onRemoveTag,
                        onAddTagClick = { actions?.dismissTextMenu(); tagPickerVisible = true },
                        modifier = Modifier
                            .testTag(UiTestTags.EditorTagToggle)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))

                    // One editor for reading and editing: it fills the area and scrolls itself; tables and images are
                    // placeholders in it. Nothing is drawn before the note is loaded.
                    if (state.isLoading) {
                        Box(Modifier.weight(1f).fillMaxWidth())
                    } else {
                        NoteBody(
                            markdown = state.content,
                            onMarkdownChange = { markdown -> if (markdown != currentContent) currentOnContentChange(markdown) },
                            onActionsReady = { actions = wrapActions(it) },
                            onFormatStateChanged = { formatState = it },
                            editable = !state.isReadOnly,
                            onStopFlushed = onFlush,
                            imageSource = imageSource,
                            onImageCommitted = onImageCommitted,
                            onTableBarChanged = { nativeTableBar = it },
                            bottomInsetPx = barInsetPx,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }

        // The ⋯ block-format sheet: a modal over the note, under the format bar (§4.2).
        val sheetActions = actions
        if (sheetActions != null) {
            MarkdownFormatMenu(
                expanded = formatMenuExpanded && imeVisible && nativeTableBar == null && !state.isReadOnly,
                actions = sheetActions,
                onDismiss = { formatMenuExpanded = false },
                hazeState = hazeState,
                bottomInset = with(density) { barExtentPx.toDp() },
                formatState = formatState,
            )
        }

        AnimatedVisibility(
            visible = imeVisible && !state.isReadOnly,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(TesseraMotion.DurShort4Ms, easing = TesseraMotion.EaseDecelerate),
            ) { it },
            exit = slideOutVertically(
                animationSpec = tween(150, easing = TesseraMotion.EaseStandard),
            ) { it },
        ) {
            // Floating pill toolbar above the keyboard (M3 expressive
            // HorizontalFloatingToolbar shape language) instead of a full-width bar.
            // The ⋯ block-format sheet is a modal below this bar in z-order (MarkdownFormatMenu): the bar stays sharp and the ⋯ button closes it.
            // With the caret inside a TABLE cell the pill swaps to the contextual
            // TableActionsBar (Docs/OneNote mobile pattern).
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    // the margins around the pill are part of the bar: a tap there must not reach the note under it (a checkbox, an image,
                    // the caret). Handled in the FINAL pass, after the children are done with the event: the pill's own buttons
                    // and its horizontally scrolling row (whose drag would otherwise arrive consumed) work as usual.
                    // the margins around the pill are part of the bar: a tap there must not reach the note under it (a checkbox, an image,
                    // the caret). Any pointer input on the node already takes the hit from the siblings below it; it must NOT consume the
                    // events, a consumed DOWN makes the pill's horizontally scrolling row ignore the drag.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                // The size of the bar with its margins: the ⋯ sheet stands over it, the note keeps clear of it.
                Box(modifier = Modifier.onSizeChanged { barExtentPx = it.height + with(density) { 16.dp.roundToPx() } }) {
                val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh
                Surface(
                    color = Color.Transparent,
                    contentColor = contentColorFor(pillColor),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    // a shadow only outside the pill: under it the glass would show it as a grey fill
                    modifier = Modifier.tesseraGlassShadow(TesseraElevation.Level2, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
                ) {
                    // inside the Surface, which clips to the pill
                    Box(Modifier.tesseraGlass(hazeState, pillColor)) {
                    val tableBar = nativeTableBar
                    if (tableBar != null) {
                        TableActionsBar(
                            canRemoveRow = tableBar.canRemoveRow,
                            canRemoveColumn = tableBar.canRemoveColumn,
                            canMoveUp = tableBar.canMoveUp,
                            canMoveDown = tableBar.canMoveDown,
                            onAddRow = tableBar.onAddRow,
                            onRemoveRow = tableBar.onRemoveRow,
                            onAddColumn = tableBar.onAddColumn,
                            onRemoveColumn = tableBar.onRemoveColumn,
                            onMoveUp = tableBar.onMoveUp,
                            onMoveDown = tableBar.onMoveDown,
                            onDeleteTable = tableBar.onDeleteTable,
                        )
                    } else {
                        EditorFormatBar(
                            actions = barActions,
                            menuExpanded = formatMenuExpanded,
                            onToggleMenu = { if (!formatMenuExpanded) actions?.dismissTextMenu(); formatMenuExpanded = !formatMenuExpanded },
                            formatState = formatState,
                        )
                    }
                    }
                }
                }
            }
        }

        // Tag picker popup — drawn on top of everything.
        TagPickerPopup(
            visible = tagPickerVisible,
            availableTags = state.availableTags,
            onAddTag = { onAddTag(it); tagPickerVisible = false },
            onDismiss = { tagPickerVisible = false },
            hazeState = hazeState,
        )

        // Members management sheet (shared notes).
        if (state.membersSheetVisible && state.myRole != null) {
            MembersSheet(
                members = state.members,
                myRole = state.myRole,
                currentUserId = state.currentUserId?.let { app.tessera.domain.model.UserId(it) },
                inviteState = state.inviteState,
                onChangeRole = onChangeRole,
                onRemoveMember = onRemoveMember,
                onInvite = onInvite,
                onInviteInputChanged = onInviteInputChanged,
                onDismiss = onDismissMembers,
            )
        }

        if (leaveConfirmVisible) {
            ConfirmDialog(
                title = stringResource(R.string.leave_confirm_title),
                message = stringResource(R.string.leave_confirm_message),
                confirmLabel = stringResource(R.string.leave_confirm_action),
                destructive = true,
                onConfirm = { leaveConfirmVisible = false; onLeave() },
                onDismiss = { leaveConfirmVisible = false },
            )
        }

        if (deleteForAllConfirmVisible) {
            ConfirmDialog(
                title = stringResource(R.string.delete_all_confirm_title),
                message = stringResource(R.string.delete_all_confirm_message),
                confirmLabel = stringResource(R.string.delete_all_confirm_action),
                destructive = true,
                onConfirm = { deleteForAllConfirmVisible = false; onDeleteForAll() },
                onDismiss = { deleteForAllConfirmVisible = false },
            )
        }

        if (unshareConfirmVisible) {
            ConfirmDialog(
                title = stringResource(R.string.unshare_confirm_title),
                message = stringResource(R.string.unshare_confirm_message),
                confirmLabel = stringResource(R.string.unshare_confirm_action),
                destructive = true,
                onConfirm = { unshareConfirmVisible = false; onUnshare() },
                onDismiss = { unshareConfirmVisible = false },
            )
        }

        // Surfaces a failed share action (e.g. denied by security rules, or offline) so the
        // remote-first share doesn't silently do nothing.
        val shareError = state.shareError
        if (shareError != null) {
            AlertDialog(
                onDismissRequest = onClearShareError,
                title = { Text(stringResource(R.string.share_failed_title)) },
                text = {
                    Text(
                        shareError.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.share_failed_message),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onClearShareError) {
                        Text(stringResource(R.string.share_failed_ok))
                    }
                },
            )
        }
    }
}

/** [this] whose bar actions also call [close] (the ⋯ sheet closes on any button of the bar but ⋯; the editor's text menu goes down). */
internal fun RichBodyActions.closing(close: () -> Unit): RichBodyActions {
    val base = this
    fun done() { base.dismissTextMenu(); close() } // the editor's floating menu is a window above the sheet: down with it too
    return object : RichBodyActions by base {
        override fun undo() { base.undo(); done() }
        override fun redo() { base.redo(); done() }
        override fun toggleTaskList() { base.toggleTaskList(); done() }
        override fun toggleBulletList() { base.toggleBulletList(); done() }
        override fun toggleDashList() { base.toggleDashList(); done() }
        override fun toggleOrderedList() { base.toggleOrderedList(); done() }
        override fun toggleBold() { base.toggleBold(); done() }
        override fun toggleItalic() { base.toggleItalic(); done() }
        override fun toggleUnderline() { base.toggleUnderline(); done() }
        override fun toggleStrikethrough() { base.toggleStrikethrough(); done() }
        override fun toggleHeading1() { base.toggleHeading1(); done() }
        override fun toggleHeading2() { base.toggleHeading2(); done() }
        override fun indent() { base.indent(); done() }
        override fun outdent() { base.outdent(); done() }
        override fun toggleQuote() { base.toggleQuote(); done() }
        override fun toggleCode() { base.toggleCode(); done() }
        override fun insertHorizontalRule() { base.insertHorizontalRule(); done() }
        override fun insertTable() { base.insertTable(); done() }
        override fun onInsertImage() { base.onInsertImage(); done() }
        override fun onCaptureImage() { base.onCaptureImage(); done() }
    }
}

/**
 * Create a fresh FileProvider URI under `cacheDir/camera/` for [ActivityResultContracts.TakePicture]
 * to write into. The `camera/` path + `${applicationId}.fileprovider` authority were declared in
 * `file_paths.xml` (Task 1).
 */
private fun createCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.share_cancel)) }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun NoteEditorScreenLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        NoteEditorScreen(
            state = NoteEditorState(
                content = "My first note\n\n- [ ] Buy groceries\n- [x] Call the dentist",
                tags = listOf("work", "personal"),
                isPinned = false,
                availableTags = listOf("ideas", "kotlin"),
                isLoading = false,
            ),
            onContentChange = {},
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }
}
