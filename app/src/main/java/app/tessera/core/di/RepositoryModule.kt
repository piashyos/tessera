package app.tessera.core.di

import app.tessera.data.remote.FirestoreNotesClient
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.repository.AccountRepositoryImpl
import app.tessera.data.repository.AuthRepositoryImpl
import app.tessera.data.repository.NotesRepositoryImpl
import app.tessera.data.repository.SyncRepositoryImpl
import app.tessera.domain.repository.AccountRepository
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.SyncRepository
import app.tessera.domain.repository.SyncScheduler
import app.tessera.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: NotesRepositoryImpl): NotesRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindRemoteNotesDataSource(impl: FirestoreNotesClient): RemoteNotesDataSource

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
}
