package app.tessera.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import android.util.Log
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.tessera.core.connectivity.ConnectivityObserver
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.SyncRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Bridges authentication state to background sync. When a user is signed in it enqueues a
 * one-time [SyncWorker], a daily [DeleteTombstonesWorker], and starts a remote snapshot listener.
 * On sign-out it cancels the scheduled work and the listener.
 */
@Singleton
class SyncManager @Inject constructor(
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    connectivityObserver: ConnectivityObserver,
) : SyncStatusProvider {
    /**
     * Live sync state for the UI: RUNNING work → [SyncIndicator.Syncing], no network →
     * [SyncIndicator.Offline], failed work → [SyncIndicator.Error], otherwise [SyncIndicator.Ok].
     */
    override val syncStatus: Flow<SyncIndicator> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_NAME),
            connectivityObserver.isOnline,
        ) { infos, online ->
            syncIndicatorOf(reduceSyncState(infos.map { it.state }), online)
        }.distinctUntilChanged()

    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Sync coroutine failed", throwable)
        }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private var remoteJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        // Clean expired trash locally on every launch, even for signed-out / local-only users.
        scope.launch { syncRepository.purgeLocalTombstones() }
        scope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user != null) onSignedIn(user) else onSignedOut()
            }
        }
    }

    private fun onSignedIn(uid: UserId) {
        val data = workDataOf(SyncWorker.KEY_UID to uid.value)

        val syncRequest =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(data)
                .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, syncRequest)

        // Run a purge now (at launch / sign-in) so 30-day-old trash is removed everywhere,
        // including Firebase, without waiting for the periodic worker's deferred first run.
        val purgeNowRequest =
            OneTimeWorkRequestBuilder<DeleteTombstonesWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(DeleteTombstonesWorker.KEY_UID to uid.value))
                .build()
        workManager.enqueueUniqueWork(
            DeleteTombstonesWorker.ONE_TIME_NAME,
            ExistingWorkPolicy.REPLACE,
            purgeNowRequest,
        )

        val purgeRequest =
            PeriodicWorkRequestBuilder<DeleteTombstonesWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(DeleteTombstonesWorker.KEY_UID to uid.value))
                .build()
        workManager.enqueueUniquePeriodicWork(
            DeleteTombstonesWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            purgeRequest,
        )

        remoteJob?.cancel()
        remoteJob =
            scope.launch {
                syncRepository
                    .observeRemote(uid)
                    .catch { Log.w(TAG, "Remote sync listener stopped", it) }
                    .collect { }
            }
    }

    private fun onSignedOut() {
        remoteJob?.cancel()
        remoteJob = null
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(DeleteTombstonesWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(DeleteTombstonesWorker.ONE_TIME_NAME)
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}
