package app.tessera.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodically purges soft-deleted tombstones older than the retention window, locally and remotely.
 */
@HiltWorker
class DeleteTombstonesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.success()
        return syncRepository.purgeTombstones(UserId(uid)).fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val KEY_UID = "uid"
        const val UNIQUE_NAME = "tessera-tombstone-purge"
        const val ONE_TIME_NAME = "tessera-tombstone-purge-once"
    }
}
