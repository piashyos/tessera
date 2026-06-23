package app.tessera.data.remote

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the cloud replica of notes. Hides the Firestore SDK so the sync merge
 * logic in [app.tessera.data.repository.SyncRepositoryImpl] stays JVM-unit-testable.
 *
 * All operations are scoped to a single owner (`uid`); documents live under
 * `users/{uid}/notes/{noteId}`.
 */
interface RemoteNotesDataSource {
    /** One-shot pull of every note document for [uid] (including tombstones). */
    suspend fun fetchAll(uid: String): List<RemoteNote>

    /** Upserts the given notes for [uid]. */
    suspend fun upsertAll(uid: String, notes: List<RemoteNote>)

    /** Hot stream of the full note set for [uid], re-emitting on every remote change. */
    fun observeNotes(uid: String): Flow<List<RemoteNote>>

    /**
     * Hard-deletes remote tombstones whose `deletedAt` is strictly older than [threshold].
     * Returns the number of documents removed.
     */
    suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int

    /** Hard-deletes the remote documents with the given [ids] for [uid]. */
    suspend fun deleteByIds(uid: String, ids: List<String>)
}
