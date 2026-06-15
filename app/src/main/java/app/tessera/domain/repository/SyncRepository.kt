package app.tessera.domain.repository

import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    /** Push local pending changes for [uid], then pull and last-write-wins merge remote notes. */
    suspend fun syncNow(uid: UserId): Result<Unit>

    /** Purge soft-deleted tombstones older than the retention window, locally and remotely. */
    suspend fun purgeTombstones(uid: UserId): Result<Unit>

    /**
     * Hot flow that listens to remote changes for [uid] and merges each snapshot into Room,
     * emitting [Unit] after every applied batch.
     */
    fun observeRemote(uid: UserId): Flow<Unit>
}
