package app.tessera.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Pushes local pending changes and pulls/merges remote notes for the signed-in owner.
 * Idempotent: safe to re-run, and reschedules itself via [androidx.work.ListenableWorker.Result.retry]
 * when the network call fails.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.success()
        Log.d(TAG, "Running sync for uid=$uid")
        return syncRepository.syncNow(UserId(uid)).fold(
            onSuccess = {
                Log.d(TAG, "Sync OK for uid=$uid")
                Result.success()
            },
            onFailure = { error ->
                Log.w(TAG, "Sync failed for uid=$uid — will retry", error)
                Result.retry()
            },
        )
    }

    companion object {
        const val KEY_UID = "uid"
        const val UNIQUE_NAME = "tessera-sync"
        private const val TAG = "SyncWorker"
    }
}
