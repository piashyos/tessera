package app.tessera.data.repository

import app.tessera.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor() : SyncRepository {
    override suspend fun syncNow(): Result<Unit> = Result.success(Unit)
}
