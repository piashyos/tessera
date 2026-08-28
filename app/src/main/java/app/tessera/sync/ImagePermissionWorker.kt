package app.tessera.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.tessera.core.config.DriveImagesConfig
import app.tessera.core.util.Clock
import app.tessera.data.drive.DriveApiException
import app.tessera.data.drive.DriveAuthException
import app.tessera.data.drive.DriveAuthManager
import app.tessera.data.drive.DriveApi
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.dao.SharedMemberDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.usecase.PermissionDiff
import app.tessera.domain.usecase.computePermissionDiff
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Reconciles the Drive sharing permissions of the signed-in user's `UPLOADED` images with the
 * current membership of each owning note. Declarative and idempotent: it diffs the file's live
 * Drive permissions against the note's member set (see [computePermissionDiff]) and applies only the
 * delta, so it is safe to run on every membership change, after each upload, and on a 24h schedule.
 *
 * Only images this user owns are touched — permission changes are an owner-only operation. A missing
 * Drive token is treated as a no-op success (lazy re-consent); auth failures retry after invalidating
 * the token; transient Drive/network errors retry.
 */
@HiltWorker
class ImagePermissionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveApi: DriveApi,
    private val driveAuthManager: DriveAuthManager,
    private val noteImageDao: NoteImageDao,
    private val sharedMemberDao: SharedMemberDao,
    private val authRepository: AuthRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID)?.takeIf { it.isNotBlank() } ?: return Result.success()

        val token = driveAuthManager.accessToken()
        if (token == null) {
            // Consent missing/declined/revoked — leave rows untouched and succeed so lazy re-consent
            // (next insert / editor open) re-triggers reconciliation later.
            Log.d(TAG, "No Drive access token; deferring permission sync until consent is granted")
            return Result.success()
        }

        val myEmail = authRepository.currentProfile.value?.email.orEmpty()

        // Only files that finished uploading have a Drive id to reconcile.
        val rows = noteImageDao
            .byOwnerAndStatus(uid, ImageStatus.UPLOADED)
            .filter { !it.driveFileId.isNullOrBlank() }
        if (rows.isEmpty()) return Result.success()

        // Member set per note is stable across a note's images; resolve each note once.
        val targetsByNote = HashMap<String, Set<String>>()
        var needsRetry = false

        for (row in rows) {
            val target = targetsByNote.getOrPut(row.noteId) { targetEmailsFor(row.noteId, myEmail) }
            when (reconcile(token, row, target, myEmail)) {
                RowResult.DONE -> Unit
                RowResult.TRANSIENT -> needsRetry = true
                RowResult.AUTH -> {
                    driveAuthManager.invalidateToken()
                    return Result.retry()
                }
            }
        }
        return if (needsRetry) Result.retry() else Result.success()
    }

    /** Members who should be able to read this note's images — the owner's own email is excluded. */
    private suspend fun targetEmailsFor(noteId: String, myEmail: String): Set<String> {
        val mine = myEmail.lowercase()
        return sharedMemberDao.getForNote(noteId)
            .mapNotNull { it.email.takeIf { e -> e.isNotBlank() }?.lowercase() }
            .filterTo(mutableSetOf()) { it != mine }
    }

    private suspend fun reconcile(
        token: String,
        row: NoteImageEntity,
        target: Set<String>,
        myEmail: String,
    ): RowResult {
        val fileId = row.driveFileId ?: return RowResult.DONE
        return try {
            val actual = driveApi.listPermissions(token, fileId)
            val diff: PermissionDiff =
                computePermissionDiff(target, actual, myEmail, DriveImagesConfig.ANYONE_LINK_MODE)

            for (email in diff.toGrant) driveApi.createPermission(token, fileId, email)
            for (perm in diff.toRevoke) driveApi.deletePermission(token, fileId, perm.id)
            if (diff.ensureAnyoneLink) driveApi.createAnyonePermission(token, fileId)

            noteImageDao.upsert(row.copy(permissionsSyncedAt = clock.nowMs()))
            RowResult.DONE
        } catch (e: DriveAuthException) {
            RowResult.AUTH
        } catch (e: DriveApiException) {
            Log.w(TAG, "Transient Drive error syncing permissions for ${row.imageId} — will retry", e)
            RowResult.TRANSIENT
        } catch (e: IOException) {
            Log.w(TAG, "Network error syncing permissions for ${row.imageId} — will retry", e)
            RowResult.TRANSIENT
        }
    }

    private enum class RowResult { DONE, TRANSIENT, AUTH }

    companion object {
        const val KEY_UID = "uid"
        const val UNIQUE_NAME = "tessera-image-permission-sync"
        const val PERIODIC_NAME = "tessera-image-permission-sync-periodic"
        private const val TAG = "ImagePermissionWorker"
    }
}
