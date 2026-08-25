package app.tessera.domain.repository

import app.tessera.domain.model.NoteId
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    /** Push local pending changes for [uid], then pull and last-write-wins merge remote notes. */
    suspend fun syncNow(uid: UserId): Result<Unit>

    /**
     * Pushes only the local pending folders and notes for [uid] to the cloud (no pull/merge).
     * Used to flush unsynced data to Firebase before a destructive local operation (e.g. clearing
     * the cache on account switch) so nothing that exists only locally is lost.
     */
    suspend fun pushLocalChanges(uid: UserId): Result<Unit>

    /** Purge soft-deleted tombstones older than the retention window, locally and remotely. */
    suspend fun purgeTombstones(uid: UserId): Result<Unit>

    /**
     * Purge soft-deleted tombstones older than the retention window from the local store only.
     * Used at launch so trash is cleaned even for local-only (signed-out) users.
     */
    suspend fun purgeLocalTombstones(): Result<Unit>

    /**
     * Irreversibly removes [ids] both remotely and locally for [uid]. Deleting the remote
     * document first prevents a subsequent pull from resurrecting the note as a tombstone.
     */
    suspend fun permanentlyDelete(uid: UserId, ids: List<NoteId>): Result<Unit>

    /**
     * Hot flow that listens to remote changes for [uid] and merges each snapshot into Room,
     * emitting [Unit] after every applied batch.
     */
    fun observeRemote(uid: UserId): Flow<Unit>
}
