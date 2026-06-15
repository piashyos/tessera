package app.tessera.domain.repository

import app.tessera.domain.model.UserId

/**
 * Account-ownership surface for local notes. Kept separate from [NotesRepository] so the
 * auth feature stays self-contained and the existing notes fakes are untouched.
 */
interface AccountRepository {
    /** Owner of the local notes, or null when notes are unowned (never signed in). */
    suspend fun currentOwnerId(): UserId?

    /** Assigns unowned local notes to [uid] (used after a clean first sign-in). */
    suspend fun adoptLocalNotes(uid: UserId)

    /** Reassigns every local note to [uid] and marks them unsynced (move to new account). */
    suspend fun moveAllNotesTo(uid: UserId)

    /** Hard-deletes every local note owned by [uid]. */
    suspend fun deleteNotesOwnedBy(uid: UserId)
}
