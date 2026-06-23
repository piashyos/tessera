package app.tessera.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.usecase.ChangeMemberRoleUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.GetNoteUseCase
import app.tessera.domain.usecase.InviteMemberUseCase
import app.tessera.domain.usecase.LeaveSharedNoteUseCase
import app.tessera.domain.usecase.ObserveMembersUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.RemoveMemberUseCase
import app.tessera.domain.usecase.ShareNoteUseCase
import app.tessera.domain.usecase.UnshareNoteUseCase
import app.tessera.domain.usecase.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AutosaveDebounceMs = 500L

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val getNoteUseCase: GetNoteUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val observeNotesUseCase: ObserveNotesUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val observeMembersUseCase: ObserveMembersUseCase,
    private val shareNoteUseCase: ShareNoteUseCase,
    private val inviteMemberUseCase: InviteMemberUseCase,
    private val changeMemberRoleUseCase: ChangeMemberRoleUseCase,
    private val removeMemberUseCase: RemoveMemberUseCase,
    private val leaveSharedNoteUseCase: LeaveSharedNoteUseCase,
    private val unshareNoteUseCase: UnshareNoteUseCase,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: NoteId = NoteId(
        savedStateHandle.get<String>("noteId")
            ?: error("NoteEditorViewModel requires 'noteId' in SavedStateHandle"),
    )

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
                        isOwner = note.ownerId != null && note.ownerId == me,
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
            shareNoteUseCase(noteId).onSuccess {
                _state.update { it.copy(membersSheetVisible = true) }
            }
        }
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
        viewModelScope.launch { changeMemberRoleUseCase(noteId, uid, role) }
    }

    fun removeMember(uid: UserId) {
        viewModelScope.launch { removeMemberUseCase(noteId, uid) }
    }

    /** Current user leaves the shared note. The screen pops back afterwards. */
    fun leave() {
        autosaveJob?.cancel()
        autosaveJob = null
        viewModelScope.launch { leaveSharedNoteUseCase(noteId) }
    }

    /** Owner returns the note to their private subtree; the editor stays open on the now-private note. */
    fun unshare() {
        viewModelScope.launch {
            unshareNoteUseCase(noteId).onSuccess {
                _state.update {
                    it.copy(
                        membersSheetVisible = false,
                        isShared = false,
                        myRole = null,
                        members = emptyList(),
                    )
                }
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

        val updated = baseline.copy(
            content = current.content,
            tags = current.tags.map { Tag(it) },
            isPinned = current.isPinned,
        )
        val saved = updateNoteUseCase(updated)
        baselineNote = saved
    }
}
