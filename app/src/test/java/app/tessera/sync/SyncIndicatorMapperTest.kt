package app.tessera.sync

import androidx.work.WorkInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncIndicatorMapperTest {
    @Test
    fun `running work maps to Syncing even when offline`() {
        assertEquals(SyncIndicator.Syncing, syncIndicatorOf(WorkInfo.State.RUNNING, isOnline = false))
    }

    @Test
    fun `no network maps to Offline when not actively running`() {
        assertEquals(SyncIndicator.Offline, syncIndicatorOf(WorkInfo.State.ENQUEUED, isOnline = false))
        assertEquals(SyncIndicator.Offline, syncIndicatorOf(WorkInfo.State.SUCCEEDED, isOnline = false))
        assertEquals(SyncIndicator.Offline, syncIndicatorOf(null, isOnline = false))
    }

    @Test
    fun `enqueued or blocked online maps to Syncing`() {
        assertEquals(SyncIndicator.Syncing, syncIndicatorOf(WorkInfo.State.ENQUEUED, isOnline = true))
        assertEquals(SyncIndicator.Syncing, syncIndicatorOf(WorkInfo.State.BLOCKED, isOnline = true))
    }

    @Test
    fun `failed work online maps to Error`() {
        assertEquals(SyncIndicator.Error(), syncIndicatorOf(WorkInfo.State.FAILED, isOnline = true))
    }

    @Test
    fun `succeeded or idle online maps to Ok with timestamp`() {
        assertEquals(SyncIndicator.Ok(123L), syncIndicatorOf(WorkInfo.State.SUCCEEDED, isOnline = true, lastSyncAtMs = 123L))
        assertEquals(SyncIndicator.Ok(null), syncIndicatorOf(null, isOnline = true))
    }

    @Test
    fun `reduce prefers running over terminal states`() {
        assertEquals(
            WorkInfo.State.RUNNING,
            reduceSyncState(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.RUNNING)),
        )
    }

    @Test
    fun `reduce prefers enqueued over failed and succeeded`() {
        assertEquals(
            WorkInfo.State.ENQUEUED,
            reduceSyncState(listOf(WorkInfo.State.FAILED, WorkInfo.State.ENQUEUED, WorkInfo.State.SUCCEEDED)),
        )
    }

    @Test
    fun `reduce of empty list is null`() {
        assertEquals(null, reduceSyncState(emptyList()))
    }

    @Test
    fun `reduce prefers failed over succeeded`() {
        assertEquals(
            WorkInfo.State.FAILED,
            reduceSyncState(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED)),
        )
    }
}
