package app.tessera.core.di

import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.OkHttpDriveApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DriveModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun provideDriveApi(client: OkHttpClient): DriveApi = OkHttpDriveApi(client)
}
