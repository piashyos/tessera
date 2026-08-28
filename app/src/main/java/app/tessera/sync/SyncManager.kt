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
import app.tessera.domain.repository.AccountRepository
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
    private val accountRepository: AccountRepository,
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

    // Tracks whether the previous auth state had a signed-in user, so we can tell a real sign-out
    // (signed-in -> null) apart from a cold start that simply begins signed-out. Only the former
    // must wipe the local cache; clearing on the initial null would delete a local-only user's notes
    // on every launch.
    private var hadSignedInUser = false

    fun start() {
        if (started) return
        started = true
        // Clean expired trash locally on every launch, even for signed-out / local-only users.
        scope.launch { syncRepository.purgeLocalTombstones() }
        scope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user != null) {
                    hadSignedInUser = true
                    onSignedIn(user)
                } else {
                    val wasSignedIn = hadSignedInUser
                    hadSignedInUser = false
                    onSignedOut(wasSignedIn)
                }
            }
        }
    }

    private suspend fun onSignedIn(uid: UserId) {
        // Stop the previous account's remote snapshot listener first, so it can't repopulate the
        // cache while/after we clear it below.
        remoteJob?.cancel()
        remoteJob = null

        // Account isolation: if the local cache holds data from a different account — foreign-owned
        // private notes, or shared notes this account isn't a member of (e.g. a note shared between
        // two other users that was cached while one of them was signed in on this device) — wipe the
        // cache before syncing so only this account's cloud data repopulates. Null-owned local drafts
        // are not foreign, so the signed-out -> first-sign-in adoption flow is preserved.
        runCatching {
            if (accountRepository.hasForeignData(uid)) {
                // Flush this account's own unsynced folders/notes to Firebase first so wiping the
                // cache can't drop data that only exists locally. Only clear if the push succeeded
                // (e.g. when offline it fails — keep the cache and retry on the next sign-in; the
                // shared-note eviction in mergeRemoteShared still provides a fallback).
                val flushed = syncRepository.pushLocalChanges(uid).isSuccess
                if (flushed) {
                    Log.i(TAG, "Foreign local data detected on sign-in; clearing cache for account isolation")
                    accountRepository.clearLocalData()
                } else {
                    Log.w(TAG, "Could not flush local changes before cache clear; deferring isolation wipe")
                }
            }
        }.onFailure { Log.w(TAG, "Account-isolation cache check failed", it) }

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

        // Reconcile Drive image sharing permissions daily as a safety net for membership changes
        // missed while offline. Event-driven syncs (membership merge, upload success) do most of
        // the work; this backstops drift.
        val permissionSyncRequest =
            PeriodicWorkRequestBuilder<ImagePermissionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(ImagePermissionWorker.KEY_UID to uid.value))
                .build()
        workManager.enqueueUniquePeriodicWork(
            ImagePermissionWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            permissionSyncRequest,
        )

        // Sweep orphaned images daily: stamp images no note references any more and, once past the
        // 7-day hysteresis, trash them from Drive + drop their rows. The after-save trigger does the
        // prompt work; this backstops orphans created while offline or by remote deletions.
        val cleanupRequest =
            PeriodicWorkRequestBuilder<ImageCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(ImageCleanupWorker.KEY_UID to uid.value))
                .build()
        workManager.enqueueUniquePeriodicWork(
            ImageCleanupWorker.PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest,
        )

        remoteJob =
            scope.launch {
                syncRepository
                    .observeRemote(uid)
                    .catch { Log.w(TAG, "Remote sync listener stopped", it) }
                    .collect { }
            }
    }

    private suspend fun onSignedOut(wasSignedIn: Boolean) {
        // Stop the remote snapshot listener FIRST so a late Firestore snapshot can't repopulate the
        // cache after we wipe it below.
        remoteJob?.cancel()
        remoteJob = null
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(DeleteTombstonesWorker.UNIQUE_NAME)
        workManager.cancelUniqueWork(DeleteTombstonesWorker.ONE_TIME_NAME)
        workManager.cancelUniqueWork(ImagePermissionWorker.PERIODIC_NAME)
        workManager.cancelUniqueWork(ImageCleanupWorker.PERIODIC_NAME)

        // On a genuine sign-out, the account's notes were cloud-backed, so drop the local cache —
        // otherwise the signed-in user's notes linger on the device for the next (signed-out or
        // different) user. A cold start that begins signed-out must NOT clear: those notes are a
        // local-only user's and exist nowhere else.
        if (wasSignedIn) {
            runCatching { accountRepository.clearLocalData() }
                .onFailure { Log.w(TAG, "Failed to clear local cache on sign-out", it) }
        }
    }

    private companion object {
        const val TAG = "SyncManager"
    }
}
