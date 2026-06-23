package app.tessera.domain.repository

import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>

    fun observeNote(id: NoteId): Flow<Note?>

    fun searchNotes(query: String): Flow<List<Note>>

    fun observeArchivedNotes(): Flow<List<Note>>

    fun observeDeletedNotes(): Flow<List<Note>>

    suspend fun getNote(id: NoteId): Note?

    suspend fun createNote(ownerId: UserId? = null): Note

    suspend fun updateNote(note: Note): Note

    suspend fun deleteNote(id: NoteId)

    /** Irreversibly removes a note (used to empty items from Trash). */
    suspend fun permanentlyDeleteNote(id: NoteId)

    suspend fun restoreNote(id: NoteId)

    suspend fun togglePin(id: NoteId)

    suspend fun setArchived(id: NoteId, archived: Boolean)

    // --- Shared notes (access) ---

    /** Makes a private note shared: moves it into `sharedNotes` with the owner as sole member. */
    suspend fun shareNote(id: NoteId): Result<Unit>

    /** Invites a registered user by [email] with [role]; see [InviteResult] for the outcomes. */
    suspend fun inviteByEmail(id: NoteId, email: String, role: ShareRole): Result<InviteResult>

    /** Changes an existing member's role. */
    suspend fun changeRole(id: NoteId, uid: UserId, role: ShareRole): Result<Unit>

    /** Removes a member from a shared note. */
    suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit>

    /** The current (non-owner) user leaves a shared note; it disappears from their device. */
    suspend fun leaveSharedNote(id: NoteId): Result<Unit>

    /** Owner returns a shared note to their private subtree and removes the shared document. */
    suspend fun unshareNote(id: NoteId): Result<Unit>

    /** Streams the locally-mirrored member list of a shared note. */
    fun observeMembers(id: NoteId): Flow<List<ShareMember>>
}
