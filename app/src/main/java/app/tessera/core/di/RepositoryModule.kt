package app.tessera.core.di

import app.tessera.data.remote.FirestoreFoldersClient
import app.tessera.data.remote.FirestoreNotesClient
import app.tessera.data.remote.FirestoreSharedNotesClient
import app.tessera.data.remote.FirestoreUserDirectory
import app.tessera.data.remote.RemoteFoldersDataSource
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.data.remote.UserDirectoryDataSource
import app.tessera.data.repository.AccountRepositoryImpl
import app.tessera.data.repository.AuthRepositoryImpl
import app.tessera.data.repository.FolderRepositoryImpl
import app.tessera.data.repository.NoteImagesRepositoryImpl
import app.tessera.data.repository.NotesRepositoryImpl
import app.tessera.data.repository.SharedNotesGateway
import app.tessera.data.repository.SharedNotesGatewayImpl
import app.tessera.data.drive.DataStoreDriveFolderCache
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.drive.DriveFolderCache
import app.tessera.data.drive.PlayServicesDriveAuthManager
import app.tessera.data.repository.SyncRepositoryImpl
import app.tessera.domain.repository.AccountRepository
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.FolderRepository
import app.tessera.domain.repository.NoteImagesRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.SyncRepository
import app.tessera.domain.repository.SyncScheduler
import app.tessera.sync.DefaultImageUploadNotifier
import app.tessera.sync.ImageUploadNotifier
import app.tessera.sync.ImageWorkScheduler
import app.tessera.sync.WorkManagerImageWorkScheduler
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
    abstract fun bindRemoteFoldersDataSource(impl: FirestoreFoldersClient): RemoteFoldersDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteSharedNotesDataSource(
        impl: FirestoreSharedNotesClient,
    ): RemoteSharedNotesDataSource

    @Binds
    @Singleton
    abstract fun bindUserDirectoryDataSource(impl: FirestoreUserDirectory): UserDirectoryDataSource

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindSharedNotesGateway(impl: SharedNotesGatewayImpl): SharedNotesGateway

    @Binds
    @Singleton
    abstract fun bindDriveAuthManager(impl: PlayServicesDriveAuthManager): DriveAuthManager

    @Binds
    @Singleton
    abstract fun bindNoteImagesRepository(impl: NoteImagesRepositoryImpl): NoteImagesRepository

    @Binds
    @Singleton
    abstract fun bindImageWorkScheduler(impl: WorkManagerImageWorkScheduler): ImageWorkScheduler

    @Binds
    @Singleton
    abstract fun bindDriveFolderCache(impl: DataStoreDriveFolderCache): DriveFolderCache

    @Binds
    @Singleton
    abstract fun bindImageUploadNotifier(impl: DefaultImageUploadNotifier): ImageUploadNotifier
}
