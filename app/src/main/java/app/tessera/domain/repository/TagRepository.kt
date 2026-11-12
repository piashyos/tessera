package app.tessera.domain.repository

import app.tessera.domain.model.TagAppearance
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    /** Only tags with a colour or an icon set, keyed by tag name. */
    fun observeAppearances(): Flow<Map<String, TagAppearance>>

    suspend fun setAppearance(name: String, color: String?, icon: String?)
}
