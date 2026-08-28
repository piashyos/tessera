package app.tessera.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.FolderRepository
import app.tessera.domain.usecase.ChangeMemberRoleUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.InsertImageUseCase
import app.tessera.domain.usecase.InviteMemberUseCase
import app.tessera.domain.usecase.LeaveSharedNoteUseCase
import app.tessera.domain.usecase.ObserveMembersUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.PermanentlyDeleteNoteUseCase
import app.tessera.domain.usecase.RemoveMemberUseCase
import app.tessera.domain.usecase.ShareNoteUseCase
import app.tessera.domain.usecase.UnshareNoteUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import app.tessera.ui.editor.components.NoteImageLoaderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wordpress.aztec.Html

private const val AutosaveDebounceMs = 500L

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val getNoteUseCase: GetNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val permanentlyDeleteNoteUseCase: PermanentlyDeleteNoteUseCase,
    private val observeMembersUseCase: ObserveMembersUseCase,
    private val shareNoteUseCase: ShareNoteUseCase,
    private val inviteMemberUseCase: InviteMemberUseCase,
    private val changeMemberRoleUseCase: ChangeMemberRoleUseCase,
    private val removeMemberUseCase: RemoveMemberUseCase,
    private val leaveSharedNoteUseCase: LeaveSharedNoteUseCase,
    private val unshareNoteUseCase: UnshareNoteUseCase,
    private val insertImageUseCase: InsertImageUseCase,
    private val imageLoaderFactory: NoteImageLoaderFactory,
    private val driveAuthManager: app.tessera.data.drive.DriveAuthManager,
    private val authRepository: AuthRepository,
    private val folderRepository: FolderRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: NoteId = NoteId(
        savedStateHandle.get<String>("noteId")
            ?: error("NoteEditorViewModel requires 'noteId' in SavedStateHandle"),
    )

    /**
     * Aztec image getter for this editor, lazily built against [viewModelScope] so its async
     * decode/download work is cancelled with the ViewModel. The screen hands it to the editor.
     */
    val imageGetter: Html.ImageGetter by lazy { imageLoaderFactory.create(viewModelScope) }

    /**
     * Consent prompts from the Drive auth flow. A recipient's first image download (or an owner's
     * first upload) with no cached token makes [DriveAuthManager.accessToken] emit a `PendingIntent`
     * here; the screen launches it via `StartIntentSenderForResult` to obtain the `drive.file` scope.
     */
    val consentRequests: kotlinx.coroutines.flow.SharedFlow<android.app.PendingIntent> =
        driveAuthManager.consentRequests

    /**
     * One-shot stream of newly-inserted imageIds. [insertImage] emits here AFTER
     * [InsertImageUseCase] succeeds; the screen collects it and splices the span at the caret via
     * [RichBodyActions.insertImage]. A replay of 0 keeps re-collection (config change) from
     * re-inserting an already-placed image.
     */
    private val _imageInserts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val imageInserts: SharedFlow<String> = _imageInserts.asSharedFlow()

    private val _state = MutableStateFlow(
        NoteEditorState(
            noteId = noteId.value,
            currentUserId = authRepository.currentUser.value?.value,
        ),
    )
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()

    // The baseline Note preserves immutable fields (id, createdAt, ownerId, deletedAt, syncedAt)
    // across saves. Starts null until the initial load completes.
    private var baselineNote: Note? = null

    private var autosaveJob: Job? = null

    init {
        viewModelScope.launch {
            val note = getNoteUseCase(noteId)
            if (note != null) {
                baselineNote = note
                val me = authRepository.currentUser.value
                _state.update { current ->
                    current.copy(
                        content = note.content,
                        tags = note.tags.map { it.name },
                        isPinned = note.isPinned,
                        updatedAt = note.updatedAt,
                        isOwner = me != null && (note.ownerId == null || note.ownerId == me),
                        isLoading = false,
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }

        viewModelScope.launch {
            observeNotesUseCase().collect { allNotes ->
                val allTagNames = allNotes
                    .flatMap { it.tags }
                    .map { it.name }
                    .distinct()
                _state.update { current ->
                    val noteTags = current.tags.toSet()
                    current.copy(availableTags = allTagNames.filter { it !in noteTags })
                }
                // Live content: when a collaborator's edit has merged into the local store,
                // adopt it in the open editor. observeNotes reflects the same store the remote
                // Firestore listener writes into, so this is near-realtime for shared notes.
                allNotes.firstOrNull { it.id == noteId }?.let(::applyRemoteIfIdle)
            }
        }

        // Folder breadcrumb: the note's folder + its ancestor chain, resolved into a
        // root → leaf list of names. Recomputes when the assignment or any folder changes.
        viewModelScope.launch {
            combine(
                folderRepository.observeFolderForNote(noteId),
                folderRepository.observeFolders(),
            ) { folderId, folders ->
                buildBreadcrumb(folderId, folders)
            }.collect { breadcrumb ->
                _state.update { it.copy(folderBreadcrumb = breadcrumb) }
            }
        }

        // Live membership: drives the avatar/members UI and derives the current user's role.
        // A note is shared exactly when it has members; private notes emit an empty list.
        viewModelScope.launch {
            observeMembersUseCase(noteId).collect { members ->
                val me = authRepository.currentUser.value
                val myRole = members.firstOrNull { it.uid == me }?.role
                _state.update { current ->
                    current.copy(
                        members = members,
                        isShared = members.isNotEmpty(),
                        myRole = myRole,
                    )
                }
            }
        }
    }

    fun onContentChange(content: String) {
        _state.update { it.copy(content = content) }
        scheduleAutosave()
    }

    fun onAddTag(tag: String) {
        _state.update { current ->
            val newTags = (current.tags + tag).distinct()
            current.copy(
                tags = newTags,
                availableTags = current.availableTags.filter { it != tag },
            )
        }
        scheduleAutosave()
    }

    fun onRemoveTag(tag: String) {
        _state.update { current ->
            val newTags = current.tags.filter { it != tag }
            current.copy(tags = newTags)
        }
        scheduleAutosave()
    }

    fun onTogglePin() {
        _state.update { it.copy(isPinned = !it.isPinned) }
        scheduleAutosave()
    }

    /**
     * Compress + store [uri] as a Drive-backed image for this note, then signal the editor to
     * splice `![](tessera-img://<id>)` at the caret. The markup is emitted ONLY after the use case
     * succeeds — a failed compression/insertion inserts nothing. Local inserts are sub-second
     * (per spec), so no progress UI is shown on this path.
     */
    fun insertImage(uri: Uri) {
        viewModelScope.launch {
            insertImageUseCase(noteId, uri).onSuccess { imageId ->
                _imageInserts.emit(imageId)
            }
        }
    }

    fun flush() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch { persist() }
    }

    fun delete() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch { deleteNoteUseCase(noteId) }
    }

    // --- Sharing actions (Phase 4) ---

    /** Owner promotes a private note to shared, then opens the members sheet to invite people. */
    fun makeShared() {
        viewModelScope.launch {
            shareNoteUseCase(noteId)
                .onSuccess {
                    _state.update { it.copy(membersSheetVisible = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(shareError = e.message ?: "Couldn't share this note") }
                }
        }
    }

    /** Dismisses the one-shot [NoteEditorState.shareError] after the UI has shown it. */
    fun clearShareError() {
        _state.update { it.copy(shareError = null) }
    }

    fun openMembersSheet() {
        _state.update { it.copy(membersSheetVisible = true, inviteState = InviteUiState.Idle) }
    }

    fun dismissMembersSheet() {
        _state.update { it.copy(membersSheetVisible = false, inviteState = InviteUiState.Idle) }
    }

    /** Resets the invite feedback when the user edits the email field again. */
    fun onInviteInputChanged() {
        if (_state.value.inviteState != InviteUiState.Idle) {
            _state.update { it.copy(inviteState = InviteUiState.Idle) }
        }
    }

    fun invite(email: String, role: ShareRole) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(inviteState = InviteUiState.Loading) }
        viewModelScope.launch {
            inviteMemberUseCase(noteId, trimmed, role)
                .onSuccess { result ->
                    val next = when (result) {
                        InviteResult.Added -> InviteUiState.Added
                        InviteResult.UserNotFound -> InviteUiState.UserNotFound
                        InviteResult.AlreadyMember -> InviteUiState.AlreadyMember
                    }
                    _state.update { it.copy(inviteState = next) }
                }
                .onFailure {
                    _state.update { it.copy(inviteState = InviteUiState.Error) }
                }
        }
    }

    fun changeRole(uid: UserId, role: ShareRole) {
        viewModelScope.launch {
            changeMemberRoleUseCase(noteId, uid, role).onFailure { e ->
                _state.update { it.copy(shareError = e.message ?: "Couldn't change this member's role") }
            }
        }
    }

    fun removeMember(uid: UserId) {
        viewModelScope.launch {
            removeMemberUseCase(noteId, uid).onFailure { e ->
                _state.update { it.copy(shareError = e.message ?: "Couldn't remove this member") }
            }
        }
    }

    /** Current user leaves the shared note. The screen pops back afterwards. */
    fun leave() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch {
            leaveSharedNoteUseCase(noteId).onFailure { e ->
                _state.update { it.copy(shareError = e.message ?: "Couldn't leave this note") }
            }
        }
    }

    /** Owner returns the note to their private subtree; the editor stays open on the now-private note. */
    fun unshare() {
        viewModelScope.launch {
            unshareNoteUseCase(noteId)
                .onSuccess {
                    _state.update {
                        it.copy(
                            membersSheetVisible = false,
                            isShared = false,
                            myRole = null,
                            members = emptyList(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(shareError = e.message ?: "Couldn't unshare this note") }
                }
        }
    }

    /**
     * Walks the parent chain of [folderId] using [folders], producing the names ordered
     * root → leaf. Returns empty when the note has no folder. A visited-set guards against
     * cyclic `parentId` data so the walk always terminates.
     */
    private fun buildBreadcrumb(folderId: FolderId?, folders: List<Folder>): List<String> {
        if (folderId == null) return emptyList()
        val byId = folders.associateBy { it.id }
        val chain = ArrayDeque<String>()
        val visited = mutableSetOf<FolderId>()
        var current: Folder? = byId[folderId]
        while (current != null && visited.add(current.id)) {
            chain.addFirst(current.name)
            current = current.parentId?.let { byId[it] }
        }
        return chain.toList()
    }

    /**
     * Adopts [remote] into the open editor, but only when the user has no unsaved local edits —
     * otherwise an incoming snapshot would wipe their in-progress typing. This is document-level
     * last-write-wins (v1, pre-CRDT): concurrent edits still resolve by `updatedAt` in the sync
     * layer; here we only surface the already-merged result without clobbering the local draft.
     */
    private fun applyRemoteIfIdle(remote: Note) {
        val baseline = baselineNote ?: return
        val current = _state.value
        val baselineTags = baseline.tags.map { it.name }
        val hasLocalEdits = current.content != baseline.content ||
            current.isPinned != baseline.isPinned ||
            current.tags != baselineTags
        if (hasLocalEdits) return

        // Only adopt a snapshot that is strictly newer than what we already have. Right after
        // an autosave the local store echoes our own write back through Firestore, and that
        // echo can briefly carry the PRE-edit content (eventual consistency) stamped with an
        // older `updatedAt`. Adopting it would push stale text into the open editor — and since
        // the editor then has no unsaved edits, RichNoteBody reloads it, resurrecting the
        // character the user just deleted. A genuine collaborator edit always has a newer
        // `updatedAt`, so live sync still flows through.
        if (remote.updatedAt <= baseline.updatedAt) return

        baselineNote = remote
        val remoteTags = remote.tags.map { it.name }
        if (remote.content != current.content ||
            remote.isPinned != current.isPinned ||
            remoteTags != current.tags ||
            remote.updatedAt != current.updatedAt
        ) {
            _state.update {
                it.copy(
                    content = remote.content,
                    tags = remoteTags,
                    isPinned = remote.isPinned,
                    updatedAt = remote.updatedAt,
                )
            }
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AutosaveDebounceMs)
            persist()
        }
    }

    private suspend fun persist() {
        val current = _state.value
        val baseline = baselineNote ?: return
        // Skip the save entirely when nothing the user can edit has actually changed —
        // otherwise opening a note bumps `updatedAt` and shuffles it to the top of the
        // list without any real edit having happened.
        val contentUnchanged = current.content == baseline.content
        val pinUnchanged = current.isPinned == baseline.isPinned
        val baselineTags = baseline.tags.map { it.name }
        val tagsUnchanged = current.tags == baselineTags
        if (contentUnchanged && pinUnchanged && tagsUnchanged) return

        // A note edited down to blank is removed everywhere (cloud + local), not kept as a draft.
        // A local-only hard delete would leave the remote document intact, and the next remote pull
        // would resurrect the note with its old title. Routing through the permanent-delete use case
        // deletes the cloud doc first (when signed in) so it can't come back. Clear the baseline so a
        // trailing autosave (e.g. the editor's dispose-time flush) can't re-persist the gone note.
        if (current.content.isBlank()) {
            permanentlyDeleteNoteUseCase(noteId)
            baselineNote = null
            return
        }

        val updated = baseline.copy(
            content = current.content,
            tags = current.tags.map { Tag(it) },
            isPinned = current.isPinned,
        )
        val saved = updateNoteUseCase(updated)
        baselineNote = saved
    }
}
