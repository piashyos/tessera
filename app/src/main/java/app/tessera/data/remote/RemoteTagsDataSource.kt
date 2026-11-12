package app.tessera.data.remote

import kotlinx.coroutines.flow.Flow

/** Cloud replica of tag appearances; mirrors [RemoteFoldersDataSource]. */
interface RemoteTagsDataSource {
    suspend fun fetchAll(uid: String): List<RemoteTag>
    suspend fun upsertAll(uid: String, tags: List<RemoteTag>)
    fun observeTags(uid: String): Flow<List<RemoteTag>>
}
