package app.tessera.core.di

import android.content.Context
import androidx.room.Room
import app.tessera.core.util.Clock
import app.tessera.core.util.SystemClock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
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
        Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db").build()

    @Provides
    fun provideNoteDao(database: NotesDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTagDao(database: NotesDatabase): TagDao = database.tagDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock()
}
