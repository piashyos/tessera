package app.tessera.data.images

import android.content.Context
import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.local.dao.NoteImageDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cap on the on-disk cache of downloaded (recipient) images: 200 MB. */
private const val MAX_CACHE_BYTES: Long = 200L * 1024L * 1024L

/**
 * Real [ImageDownloader]: pulls a Drive-backed image referenced by a Room row (typically a recipient
 * row — `status=UPLOADED`, `localPath=null`, `driveFileId` set) into `cacheDir/images/<id>.webp` and
 * returns the file for the loader to decode.
 *
 * Failure handling (all return `null` so the loader shows a retryable placeholder rather than crashing):
 *  - no row / no `driveFileId` → nothing to fetch.
 *  - no token (consent pending) → the [DriveAuthManager] has already emitted a consent PendingIntent;
 *    we return WITHOUT hitting the API so the download retries once consent is granted.
 *  - [DriveApiException] (e.g. 403/404 — access lost, file trashed) → give up quietly.
 *  - [DriveAuthException] (401) → drop the cached token so the next attempt re-authorizes.
 */
class DriveImageDownloader @Inject constructor(
    private val driveApi: DriveApi,
    private val driveAuthManager: DriveAuthManager,
    @ApplicationContext private val context: Context,
    private val noteImageDao: NoteImageDao,
) : ImageDownloader {

    override suspend fun download(imageId: String): File? {
        val row = runCatching { noteImageDao.byId(imageId) }.getOrNull() ?: return null
        val fileId = row.driveFileId ?: return null

        // No token → consent is pending (a PendingIntent was already emitted). Don't call the API.
        val token = driveAuthManager.accessToken() ?: return null

        val bytes = try {
            driveApi.downloadFile(token, fileId, row.resourceKey)
        } catch (e: DriveAuthException) {
            // Token rejected (401): invalidate so the next attempt re-authorizes.
            driveAuthManager.invalidateToken()
            return null
        } catch (e: DriveApiException) {
            // 403/404 etc. — access lost or file gone. Loader shows a retryable placeholder.
            return null
        }

        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, IMAGES_DIR).apply { mkdirs() }
            val file = File(dir, "$imageId.webp")
            file.writeBytes(bytes)
            evictImageCache(dir, MAX_CACHE_BYTES)
            file
        }
    }

    private companion object {
        const val IMAGES_DIR = "images"
    }
}

/**
 * LRU eviction for the image cache [dir]: while the total size of its files exceeds [maxBytes],
 * delete the oldest by `lastModified` first. A no-op when already under the cap. Extracted as a
 * top-level function so it can be unit-tested without a Drive round-trip.
 */
internal fun evictImageCache(dir: File, maxBytes: Long) {
    val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
    var total = files.sumOf { it.length() }
    if (total <= maxBytes) return
    for (file in files) {
        if (total <= maxBytes) break
        val len = file.length()
        if (file.delete()) total -= len
    }
}
