package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.RemoteTag
import app.tessera.data.remote.RemoteTagsDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** No-op tags remote: the two-user shared-notes E2E flows never exercise tag appearance. */
class EmptyRemoteTags : RemoteTagsDataSource {
    override suspend fun fetchAll(uid: String): List<RemoteTag> = emptyList()
    override suspend fun upsertAll(uid: String, tags: List<RemoteTag>) {}
    override fun observeTags(uid: String): Flow<List<RemoteTag>> = MutableStateFlow(emptyList())
}
