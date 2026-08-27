package app.tessera.core.di

import app.tessera.ui.editor.components.DefaultNoteImageLoaderFactory
import app.tessera.ui.editor.components.ImageDownloader
import app.tessera.ui.editor.components.NoteImageLoaderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    /**
     * No-op downloader until Chunk 4 / Task 13 wires the real Drive-backed [ImageDownloader].
     * Returning `null` makes the image getter fall through to the "unavailable" placeholder.
     */
    @Provides
    @Singleton
    fun provideImageDownloader(): ImageDownloader = ImageDownloader { null }

    @Provides
    @Singleton
    fun provideNoteImageLoaderFactory(
        impl: DefaultNoteImageLoaderFactory,
    ): NoteImageLoaderFactory = impl
}
