package app.tessera.core.connectivity

import kotlinx.coroutines.flow.Flow

/** Observes whether the device currently has validated internet connectivity. */
interface ConnectivityObserver {
    /** Emits the current online state and every subsequent change. */
    val isOnline: Flow<Boolean>
}
