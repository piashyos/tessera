package app.tessera.data.drive

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Caches the id of the "Tessera" Drive folder so the upload worker resolves (find-or-create) it once
 * per install instead of on every run. Backed by its own DataStore under key `drive_folder_id`.
 */
interface DriveFolderCache {
    /** The cached Tessera-folder id, or null when it has not been resolved yet. */
    suspend fun get(): String?

    /** Persists the resolved Tessera-folder id. */
    suspend fun put(folderId: String)
}

private val Context.driveDataStore by preferencesDataStore(name = "drive_prefs")
private val KEY_DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")

@Singleton
class DataStoreDriveFolderCache
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DriveFolderCache {
        private val store: DataStore<Preferences> = context.driveDataStore

        override suspend fun get(): String? = store.data.first()[KEY_DRIVE_FOLDER_ID]

        override suspend fun put(folderId: String) {
            store.edit { it[KEY_DRIVE_FOLDER_ID] = folderId }
        }
    }
