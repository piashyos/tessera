package app.tessera.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.tessera.domain.repository.AuthRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed [ImageWorkScheduler]. Enqueues a single unique [ImageUploadWorker] chain gated on
 * connectivity with exponential backoff; overlapping requests coalesce (`APPEND_OR_REPLACE`), mirroring
 * [WorkManagerSyncScheduler].
 */
@Singleton
class WorkManagerImageWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
) : ImageWorkScheduler {
    override fun requestUpload() {
        val uid = authRepository.currentUser.value ?: return
        val request =
            OneTimeWorkRequestBuilder<ImageUploadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(ImageUploadWorker.KEY_UID to uid.value))
                .build()
        workManager.enqueueUniqueWork(
            ImageUploadWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
