package app.tessera.data.remote

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the cloud replica of folders. Mirrors [RemoteNotesDataSource] so the
 * sync repository can treat folder records the same way it treats note records.
 */
interface RemoteFoldersDataSource {
    suspend fun fetchAll(uid: String): List<RemoteFolder>
    suspend fun upsertAll(uid: String, folders: List<RemoteFolder>)
    fun observeFolders(uid: String): Flow<List<RemoteFolder>>
    suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int
}
