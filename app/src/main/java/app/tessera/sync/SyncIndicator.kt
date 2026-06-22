package app.tessera.sync

import androidx.work.WorkInfo

/**
 * High-level, UI-facing sync state derived from WorkManager + connectivity.
 *
 * [lastSyncAtMs] in [Ok] is best-effort (epoch millis) — WorkManager does not surface the exact
 * finish time of a worker, so it may be `null` when only the work state is known.
 */
sealed interface SyncIndicator {
    data class Ok(val lastSyncAtMs: Long? = null) : SyncIndicator

    data object Syncing : SyncIndicator

    data object Offline : SyncIndicator

    data class Error(val message: String? = null) : SyncIndicator
}

/**
 * Collapse the (possibly multiple) work states of the unique sync chain into a single
 * representative state, preferring in-progress over terminal states.
 */
internal fun reduceSyncState(states: List<WorkInfo.State>): WorkInfo.State? = when {
    states.isEmpty() -> null
    WorkInfo.State.RUNNING in states -> WorkInfo.State.RUNNING
    WorkInfo.State.ENQUEUED in states || WorkInfo.State.BLOCKED in states -> WorkInfo.State.ENQUEUED
    WorkInfo.State.FAILED in states -> WorkInfo.State.FAILED
    WorkInfo.State.SUCCEEDED in states -> WorkInfo.State.SUCCEEDED
    else -> states.last()
}

/**
 * Pure mapping from a reduced sync [workState] and current connectivity to a [SyncIndicator].
 * Kept side-effect free so it can be unit-tested without WorkManager or Android.
 */
internal fun syncIndicatorOf(
    workState: WorkInfo.State?,
    isOnline: Boolean,
    lastSyncAtMs: Long? = null,
): SyncIndicator = when {
    workState == WorkInfo.State.RUNNING -> SyncIndicator.Syncing
    !isOnline -> SyncIndicator.Offline
    workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.BLOCKED -> SyncIndicator.Syncing
    workState == WorkInfo.State.FAILED -> SyncIndicator.Error()
    else -> SyncIndicator.Ok(lastSyncAtMs)
}
