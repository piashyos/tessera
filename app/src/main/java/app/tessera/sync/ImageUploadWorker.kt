package app.tessera.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.drive.DriveFolderCache
import app.tessera.data.drive.DriveQuotaException
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.mapper.NoteImageMapper
import app.tessera.data.remote.RemoteImageRef
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteSharedNotesDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException

/**
 * Uploads the signed-in user's `LOCAL_ONLY` images to their Drive "Tessera" folder and records each in
 * the owning note's Firestore `images` manifest. Idempotent and self-rescheduling via
 * [androidx.work.ListenableWorker.Result.retry] on transient failures.
 *
 * On entry it resets any stale `UPLOADING` rows (from a crashed previous run, or a run aborted by a
 * missing Drive consent) back to `LOCAL_ONLY` so they are retried — deliberately the SAME
 * [NoteImageDao.transition] call used by the consent-revocation path.
 */
@HiltWorker
class ImageUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveApi: DriveApi,
    private val driveAuthManager: DriveAuthManager,
    private val folderCache: DriveFolderCache,
    private val noteImageDao: NoteImageDao,
    private val noteDao: NoteDao,
    private val notesRemote: RemoteNotesDataSource,
    private val sharedRemote: RemoteSharedNotesDataSource,
    private val imageMapper: NoteImageMapper,
    private val notifier: ImageUploadNotifier,
    private val imageWorkScheduler: ImageWorkScheduler,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.success()

        // Recover from a crashed previous run (or an earlier consent-less run): any row still marked
        // UPLOADING is not actually in flight, so return it to LOCAL_ONLY before selecting work.
        noteImageDao.transition(uid, ImageStatus.UPLOADING, ImageStatus.LOCAL_ONLY)

        val token = driveAuthManager.accessToken()
        if (token == null) {
            // Consent missing/declined/revoked — the auth manager has emitted a consent PendingIntent.
            // Leave UPLOADED rows alone; my UPLOADING rows are already back to LOCAL_ONLY above. Succeed
            // so lazy re-consent (next insert / editor open) re-triggers the upload later.
            Log.d(TAG, "No Drive access token; deferring upload until consent is granted")
            return Result.success()
        }

        val pending = noteImageDao.byOwnerAndStatus(uid, ImageStatus.LOCAL_ONLY)
        if (pending.isEmpty()) return Result.success()

        val folderId =
            try {
                resolveFolderId(token)
            } catch (e: DriveAuthException) {
                driveAuthManager.invalidateToken()
                return Result.retry()
            } catch (e: DriveApiException) {
                Log.w(TAG, "Could not resolve Tessera folder — will retry", e)
                return Result.retry()
            } catch (e: IOException) {
                Log.w(TAG, "Network error resolving Tessera folder — will retry", e)
                return Result.retry()
            }

        var needsRetry = false
        var anyUploaded = false
        for (row in pending) {
            when (uploadRow(uid, token, folderId, row)) {
                RowResult.UPLOADED -> anyUploaded = true
                RowResult.FAILED_TERMINAL -> Unit
                RowResult.TRANSIENT -> needsRetry = true
                RowResult.AUTH -> {
                    driveAuthManager.invalidateToken()
                    return Result.retry()
                }
                // Quota is account-wide and terminal: no point trying the rest this run, and no
                // WorkManager retry (Result.success). A user message was already emitted.
                RowResult.QUOTA -> {
                    if (anyUploaded) imageWorkScheduler.requestPermissionSync()
                    return Result.success()
                }
            }
        }
        // A freshly uploaded file starts with only the owner's permission; reconcile it against the
        // note's member set so shared-note readers gain access.
        if (anyUploaded) imageWorkScheduler.requestPermissionSync()
        return if (needsRetry) Result.retry() else Result.success()
    }

    private suspend fun resolveFolderId(token: String): String {
        folderCache.get()?.let { return it }
        val id = driveApi.findFolder(token, FOLDER_NAME) ?: driveApi.createFolder(token, FOLDER_NAME)
        folderCache.put(id)
        return id
    }

    private suspend fun uploadRow(
        uid: String,
        token: String,
        folderId: String,
        row: NoteImageEntity,
    ): RowResult {
        noteImageDao.upsert(row.copy(status = ImageStatus.UPLOADING))

        val file = row.localPath?.let(::File)
        if (file == null || !file.exists()) {
            // The compressed source is gone (e.g. cache eviction) — nothing can be uploaded. Mark
            // terminal and drop the dangling path so recovery flows know there is no local copy.
            noteImageDao.upsert(row.copy(status = ImageStatus.FAILED, localPath = null))
            return RowResult.FAILED_TERMINAL
        }

        val bytes =
            try {
                file.readBytes()
            } catch (e: IOException) {
                Log.w(TAG, "Could not read image ${row.imageId} — will retry", e)
                return RowResult.TRANSIENT // left UPLOADING; the entry reset on the next run re-queues it
            }

        return try {
            val driveFile = driveApi.uploadFile(token, "${row.imageId}.webp", folderId, row.imageId, bytes)
            val uploaded =
                row.copy(
                    status = ImageStatus.UPLOADED,
                    driveFileId = driveFile.id,
                    resourceKey = driveFile.resourceKey,
                )
            noteImageDao.upsert(uploaded)
            pushManifest(uid, uploaded)
            RowResult.UPLOADED
        } catch (e: DriveQuotaException) {
            noteImageDao.upsert(row.copy(status = ImageStatus.FAILED))
            notifier.report(ImageUploadMessage.DRIVE_QUOTA_EXCEEDED)
            Log.w(TAG, "Drive quota exceeded uploading ${row.imageId}", e)
            RowResult.QUOTA
        } catch (e: DriveAuthException) {
            RowResult.AUTH // left UPLOADING; caller invalidates the token and retries
        } catch (e: DriveApiException) {
            Log.w(TAG, "Transient Drive error uploading ${row.imageId} — will retry", e)
            RowResult.TRANSIENT
        } catch (e: IOException) {
            Log.w(TAG, "Network error uploading ${row.imageId} — will retry", e)
            RowResult.TRANSIENT
        }
    }

    /** Routes the manifest write to the shared or private document depending on the note's share state. */
    private suspend fun pushManifest(uid: String, row: NoteImageEntity) {
        val ref = imageMapper.toRemoteRef(row.copy(ownerUid = uid))
        val note = noteDao.getById(row.noteId)
        if (note?.shareState == NoteEntity.SHARE_STATE_SHARED) {
            sharedRemote.updateImageManifest(row.noteId, row.imageId, ref)
        } else {
            notesRemote.updateImageManifest(uid, row.noteId, row.imageId, ref)
        }
    }

    private enum class RowResult { UPLOADED, TRANSIENT, FAILED_TERMINAL, AUTH, QUOTA }

    companion object {
        const val KEY_UID = "uid"
        const val UNIQUE_NAME = "tessera-image-upload"
        const val FOLDER_NAME = "Tessera"
        private const val TAG = "ImageUploadWorker"
    }
}
