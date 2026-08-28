package app.tessera.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tessera.core.util.Clock
import app.tessera.data.drive.DriveApi
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.domain.usecase.findOrphans
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException

/**
 * Sweeps images no note references any more and, once they have been unreferenced for at least
 * [hysteresisMillis] (7 days by default), reclaims them:
 *  - **My rows** (`ownerUid == uid`) are trashed from Drive (a Drive *trash*, not a hard delete, so
 *    the user can recover them), their Firestore manifest entry is removed, and their local file +
 *    Room row are dropped.
 *  - **Recipient rows** (someone else's image cached on this device) only lose their Room row — the
 *    Drive file belongs to the owner and must never be touched, and the cached bytes are the LRU
 *    cache's concern.
 *
 * The hysteresis window guards against a transient sync race briefly making an image look
 * unreferenced (e.g. a note pull that hasn't landed yet). [ImageStatus.ACCESS_LOST] rows are
 * excluded entirely: a revoked shared image is neither orphaned nor trashed here.
 *
 * Idempotent: a Drive 404 on trash is treated as already-gone, a missing Drive token defers only the
 * Drive-dependent work (recipient rows and bookkeeping still proceed), and an auth failure retries
 * after invalidating the token.
 */
@HiltWorker
class ImageCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveApi: DriveApi,
    private val driveAuthManager: DriveAuthManager,
    private val noteImageDao: NoteImageDao,
    private val noteDao: NoteDao,
    private val notesRemote: RemoteNotesDataSource,
    private val sharedRemote: RemoteSharedNotesDataSource,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {
    /**
     * Grace period an image must stay unreferenced before it is trashed. Set by the secondary
     * constructor for tests / tuning (Task 15 shortens it without a debug hack); Hilt always uses the
     * primary constructor, which leaves it at [SEVEN_DAYS_MILLIS].
     */
    private var hysteresisMillis: Long = SEVEN_DAYS_MILLIS

    @Suppress("LongParameterList")
    constructor(
        appContext: Context,
        params: WorkerParameters,
        driveApi: DriveApi,
        driveAuthManager: DriveAuthManager,
        noteImageDao: NoteImageDao,
        noteDao: NoteDao,
        notesRemote: RemoteNotesDataSource,
        sharedRemote: RemoteSharedNotesDataSource,
        clock: Clock,
        hysteresisMillis: Long,
    ) : this(
        appContext,
        params,
        driveApi,
        driveAuthManager,
        noteImageDao,
        noteDao,
        notesRemote,
        sharedRemote,
        clock,
    ) {
        this.hysteresisMillis = hysteresisMillis
    }

    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val now = clock.nowMs()

        // 1. Referenced set = the content of EVERY live note, including app-trash tombstones (a
        //    soft-deleted note still keeps its images alive until it is purged).
        val notesContent = noteDao.getAll().associate { it.id to it.content }

        // 2. Stamp newly-unreferenced rows and clear re-referenced ones (ACCESS_LOST rows untouched).
        val orphanResult = findOrphans(noteImageDao.all(), notesContent, now)
        orphanResult.toClear.forEach { noteImageDao.upsert(it) }
        orphanResult.toStamp.forEach { noteImageDao.upsert(it) }

        val token = driveAuthManager.accessToken()

        // 3. Reclaim rows whose grace window has fully elapsed. Re-read so we see the stamps above.
        var needsRetry = false
        for (row in noteImageDao.all()) {
            if (row.status == ImageStatus.ACCESS_LOST) continue
            val orphanedAt = row.orphanedAt ?: continue
            if (now - orphanedAt < hysteresisMillis) continue

            if (row.ownerUid == uid) {
                when (reclaimMine(uid, token, row)) {
                    RowResult.DONE -> Unit
                    RowResult.TRANSIENT -> {
                        needsRetry = true
                        continue
                    }
                    RowResult.DEFERRED -> continue // no Drive token this run; try again later
                    RowResult.AUTH -> {
                        driveAuthManager.invalidateToken()
                        return Result.retry()
                    }
                }
            } else {
                // Recipient's cached row for a note that is genuinely gone: drop only the Room row.
                // Never call Drive — the file is the owner's; the cached bytes are the LRU's job.
                noteImageDao.delete(row.imageId)
            }
        }
        return if (needsRetry) Result.retry() else Result.success()
    }

    /** Trashes (Drive), de-manifests, and drops one of my orphaned rows. */
    private suspend fun reclaimMine(uid: String, token: String?, row: NoteImageEntity): RowResult {
        if (row.driveFileId != null) {
            if (token == null) return RowResult.DEFERRED // needs Drive; leave the stamp for a later run
            when (val trashed = trash(token, row.driveFileId)) {
                RowResult.DONE -> Unit
                else -> return trashed // AUTH or TRANSIENT — do not delete the row yet
            }
            // The Drive file is gone; remove its manifest entry so pulls stop resurfacing it. Only
            // when the owning note doc still exists — if the note was purged, its manifest went with
            // it. A transient failure here retries (trash is idempotent via the 404 tolerance above).
            when (removeManifest(uid, row)) {
                RowResult.DONE -> Unit
                else -> return RowResult.TRANSIENT
            }
        }
        deleteLocalFile(row)
        noteImageDao.delete(row.imageId)
        return RowResult.DONE
    }

    private suspend fun trash(token: String, driveFileId: String): RowResult =
        try {
            driveApi.trashFile(token, driveFileId)
            RowResult.DONE
        } catch (e: DriveAuthException) {
            RowResult.AUTH
        } catch (e: DriveApiException) {
            if (e.code == HTTP_NOT_FOUND) {
                Log.d(TAG, "Drive file $driveFileId already gone (404); treating as trashed")
                RowResult.DONE
            } else {
                Log.w(TAG, "Transient Drive error trashing $driveFileId — will retry", e)
                RowResult.TRANSIENT
            }
        } catch (e: IOException) {
            Log.w(TAG, "Network error trashing $driveFileId — will retry", e)
            RowResult.TRANSIENT
        }

    private suspend fun removeManifest(uid: String, row: NoteImageEntity): RowResult {
        val note = noteDao.getById(row.noteId) ?: return RowResult.DONE // note (and its manifest) gone
        return try {
            if (note.shareState == NoteEntity.SHARE_STATE_SHARED) {
                sharedRemote.updateImageManifest(row.noteId, row.imageId, ref = null)
            } else {
                notesRemote.updateImageManifest(uid, row.noteId, row.imageId, ref = null)
            }
            RowResult.DONE
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove manifest entry for ${row.imageId} — will retry", e)
            RowResult.TRANSIENT
        }
    }

    private fun deleteLocalFile(row: NoteImageEntity) {
        val path = row.localPath ?: return
        runCatching { File(path).takeIf(File::exists)?.delete() }
            .onFailure { Log.w(TAG, "Could not delete local file for ${row.imageId}", it) }
    }

    private enum class RowResult { DONE, TRANSIENT, DEFERRED, AUTH }

    companion object {
        const val KEY_UID = "uid"
        const val UNIQUE_NAME = "tessera-image-cleanup"
        const val PERIODIC_NAME = "tessera-image-cleanup-periodic"
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
        private const val HTTP_NOT_FOUND = 404
        private const val TAG = "ImageCleanupWorker"
    }
}
