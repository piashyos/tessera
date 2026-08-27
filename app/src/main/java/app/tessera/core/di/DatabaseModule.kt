package app.tessera.core.di

import android.content.Context
import androidx.room.Room
import app.tessera.core.util.Clock
import app.tessera.core.util.SystemClock
import app.tessera.data.local.MIGRATION_1_2
import app.tessera.data.local.MIGRATION_2_3
import app.tessera.data.local.MIGRATION_3_4
import app.tessera.data.local.MIGRATION_4_5
import app.tessera.data.local.MIGRATION_5_6
import app.tessera.data.local.MIGRATION_6_7
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.RoomTransactor
import app.tessera.data.local.Transactor
import app.tessera.data.local.dao.FolderDao
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.dao.SharedMemberDao
import app.tessera.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    @Singleton
    fun provideTransactor(database: NotesDatabase): Transactor = RoomTransactor(database)

    @Provides
    fun provideNoteDao(database: NotesDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTagDao(database: NotesDatabase): TagDao = database.tagDao()

    @Provides
    fun provideFolderDao(database: NotesDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideSharedMemberDao(database: NotesDatabase): SharedMemberDao = database.sharedMemberDao()

    @Provides
    fun provideNoteImageDao(database: NotesDatabase): NoteImageDao = database.noteImageDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}
