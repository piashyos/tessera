package app.tessera.domain.usecase

import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.sync.ImageWorkScheduler
import javax.inject.Inject

/**
 * Tap-to-retry for a `FAILED` image the current user owns. Per spec, `FAILED` is recoverable ONLY
 * while the local source file is still present: a row with `localPath != null` is flipped back to
 * `LOCAL_ONLY` and re-scheduled for upload; a row whose `localPath` is gone is a no-op (retry
 * suppressed — there's nothing left to upload).
 */
class RetryImageUploadUseCase @Inject constructor(
    private val dao: NoteImageDao,
    private val scheduler: ImageWorkScheduler,
) {
    suspend operator fun invoke(imageId: String) {
        val row = dao.byId(imageId) ?: return
        if (row.status != ImageStatus.FAILED || row.localPath == null) return
        dao.upsert(row.copy(status = ImageStatus.LOCAL_ONLY))
        scheduler.requestUpload()
    }
}
