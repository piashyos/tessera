package app.tessera.core.di

import app.tessera.data.images.DriveImageDownloader
import android.content.Context
import app.tessera.data.images.ImageDownloader
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import app.tessera.ui.editor.components.DefaultNoteImageLoaderFactory
import app.tessera.ui.editor.components.NoteImageLoaderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    /** Real Drive-backed downloader (Chunk 4 / Task 13): fetches recipient images into the cache. */
    @Provides
    @Singleton
    fun provideImageDownloader(impl: DriveImageDownloader): ImageDownloader = impl

    @Provides
    @Singleton
    fun provideNoteImageDecoder(
        @ApplicationContext context: Context,
        noteImageDao: NoteImageDao,
        downloader: ImageDownloader,
    ): NoteImageDecoder = NoteImageDecoder(noteImageDao, context.filesDir, context.cacheDir, downloader)

    @Provides
    @Singleton
    fun provideNoteImageLoaderFactory(
        impl: DefaultNoteImageLoaderFactory,
    ): NoteImageLoaderFactory = impl
}
