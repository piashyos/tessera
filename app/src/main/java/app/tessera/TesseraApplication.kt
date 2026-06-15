package app.tessera

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.tessera.sync.SyncManager
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TesseraApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncManager: Lazy<SyncManager>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Sync init touches Firebase; never let it crash app startup (e.g. tests without Firebase).
        runCatching { syncManager.get().start() }
            .onFailure { Log.w(TAG, "Sync manager failed to start", it) }
    }

    private companion object {
        const val TAG = "TesseraApplication"
    }
}
