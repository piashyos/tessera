package app.tessera.domain.repository

interface SyncRepository {
    suspend fun syncNow(): Result<Unit>
}
