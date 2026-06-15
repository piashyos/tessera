package app.tessera.core.di

import android.content.Context
import androidx.work.WorkManager
import app.tessera.domain.repository.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    // TODO(Task 1.3): Replace with WorkManagerSyncScheduler
    @Provides
    @Singleton
    fun provideSyncScheduler(): SyncScheduler = object : SyncScheduler {
        override fun requestSync() {}
    }
}
