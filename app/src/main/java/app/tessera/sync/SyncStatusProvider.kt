package app.tessera.sync

import kotlinx.coroutines.flow.Flow

/**
 * Exposes the live [SyncIndicator] derived from background sync work and connectivity. Implemented
 * by [SyncManager]; UI/ViewModels depend on this abstraction so they can be tested with fake flows.
 */
interface SyncStatusProvider {
    val syncStatus: Flow<SyncIndicator>
}
