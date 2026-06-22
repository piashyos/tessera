package app.tessera.core.di

import app.tessera.core.connectivity.AndroidConnectivityObserver
import app.tessera.core.connectivity.ConnectivityObserver
import app.tessera.sync.SyncManager
import app.tessera.sync.SyncStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindConnectivityObserver(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds
    @Singleton
    abstract fun bindSyncStatusProvider(impl: SyncManager): SyncStatusProvider
}
