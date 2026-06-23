package app.tessera.data.remote

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the cloud replica of shared notes (`sharedNotes/{id}`). Hides the Firestore
 * SDK so the routing/merge logic in [app.tessera.data.repository.SyncRepositoryImpl] stays
 * JVM-unit-testable.
 *
 * Content operations ([pushSharedNote]) and membership operations ([addMember]/[updateRole]/
 * [removeMember]/[leaveNote]) are deliberately separate: content is owned by the editing device,
 * but membership/roles are mutated independently and must never be clobbered by a stale local
 * mirror.
 */
interface RemoteSharedNotesDataSource {
    /**
     * Moves a note from the owner's private subtree into `sharedNotes` in one batch: writes the
     * shared document and deletes the private `users/{ownerUid}/notes/{id}` document.
     */
    suspend fun shareNote(ownerUid: String, note: RemoteSharedNote)

    /** Returns the note to its owner's private subtree and deletes the shared document. */
    suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote)

    /** Adds [member] under [uid] and appends [uid] to `memberIds`. */
    suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember)

    /** Changes only the role of an existing member. */
    suspend fun updateRole(noteId: String, uid: String, role: String)

    /** Removes a member: deletes `members.<uid>` and pulls [uid] from `memberIds`. */
    suspend fun removeMember(noteId: String, uid: String)

    /** A member removing themselves — same wire effect as [removeMember], distinct intent. */
    suspend fun leaveNote(noteId: String, myUid: String)

    /** Updates only the content fields of an existing shared note; never touches membership. */
    suspend fun pushSharedNote(note: RemoteSharedNote)

    /** One-shot pull of every shared note where [uid] is a member (including tombstones). */
    suspend fun fetchAll(uid: String): List<RemoteSharedNote>

    /** Hot stream of every shared note where [uid] is a member, re-emitting on each change. */
    fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>>
}
