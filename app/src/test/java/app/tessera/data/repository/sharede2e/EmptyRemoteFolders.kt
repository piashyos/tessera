package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.RemoteFolder
import app.tessera.data.remote.RemoteFoldersDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** No-op folders remote: the two-user shared-notes E2E flows never exercise folders. */
class EmptyRemoteFolders : RemoteFoldersDataSource {
    override suspend fun fetchAll(uid: String): List<RemoteFolder> = emptyList()
    override suspend fun upsertAll(uid: String, folders: List<RemoteFolder>) {}
    override fun observeFolders(uid: String): Flow<List<RemoteFolder>> = MutableStateFlow(emptyList())
    override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int = 0
}
