package app.tessera.core.di

import app.tessera.data.repository.AuthRepositoryImpl
import app.tessera.data.repository.NotesRepositoryImpl
import app.tessera.data.repository.SyncRepositoryImpl
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.SyncRepository
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
}
