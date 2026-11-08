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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.tessera.core.ui.tessera.TesseraTopBarIconButton
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
    var leaveConfirmVisible by remember { mutableStateOf(false) }
    var deleteForAllConfirmVisible by remember { mutableStateOf(false) }
    var unshareConfirmVisible by remember { mutableStateOf(false) }
    // Live formats at the editor's caret: what the bar and the ⋯ menu show.
    var formatState by remember { mutableStateOf(FormatState.Empty) }
    val imeVisible = WindowInsets.isImeVisible

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
            bottomBar = {
                AnimatedVisibility(
                    visible = imeVisible && !state.isReadOnly,
                    enter = slideInVertically(
                        animationSpec = tween(TesseraMotion.DurShort4Ms, easing = TesseraMotion.EaseDecelerate),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(150, easing = TesseraMotion.EaseStandard),
                    ) { it },
                ) {
                    // Floating pill toolbar above the keyboard (M3 expressive
                    // HorizontalFloatingToolbar shape language) instead of a full-width bar.
                    // The ⋯ block-format panel expands INLINE right above the pill (8dp apart),
                    // visually attached to it instead of a floating DropdownMenu popup.
                    // With the caret inside a TABLE cell the pill swaps to the contextual
                    // TableActionsBar (Docs/OneNote mobile pattern).
                    var formatMenuExpanded by remember { mutableStateOf(false) }
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        val currentActions = actions
                        // Box anchors the ⋯ block-format panel, which overlays ABOVE the pill as a
                        // Popup (see MarkdownFormatMenu) rather than growing the bar and compressing
                        // the editor body above it.
                        Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                            shadowElevation = 3.dp,
                        ) {
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
                                    actions = actions,
                                    menuExpanded = formatMenuExpanded,
                                    onToggleMenu = { formatMenuExpanded = !formatMenuExpanded },
                                    formatState = formatState,
                                )
                            }
                        }
                        if (currentActions != null && nativeTableBar == null) {
                            MarkdownFormatMenu(
                                expanded = formatMenuExpanded,
                                actions = currentActions,
                                onDismiss = { formatMenuExpanded = false },
                                formatState = formatState,
                            )
                        }
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .hazeSource(hazeState),
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
                    onAddTagClick = { tagPickerVisible = true },
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
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                if (!imeVisible) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier,
                            )
                            .padding(horizontal = 20.dp),
                    )
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
