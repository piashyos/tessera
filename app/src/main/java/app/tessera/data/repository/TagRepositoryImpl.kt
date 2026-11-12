package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.TagDao
import app.tessera.domain.model.TagAppearance
import app.tessera.domain.repository.SyncScheduler
import app.tessera.domain.repository.TagRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TagRepositoryImpl @Inject constructor(
    private val tagDao: TagDao,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) : TagRepository {

    override fun observeAppearances(): Flow<Map<String, TagAppearance>> =
        tagDao.observeAppearances().map { tags ->
            tags.associate { it.name to TagAppearance(it.name, it.color, it.icon) }
        }

    override suspend fun setAppearance(name: String, color: String?, icon: String?) {
        tagDao.setAppearance(name, color, icon, clock.nowMs())
        syncScheduler.requestSync()
    }
}
