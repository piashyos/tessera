package app.tessera.sync

import javax.inject.Inject

/**
 * Requests background work for Drive-backed images. Implementations coalesce overlapping
 * requests, similar to [app.tessera.domain.repository.SyncScheduler].
 */
interface ImageWorkScheduler {
    /** Requests that pending `LOCAL_ONLY` images be uploaded to Drive as soon as possible. */
    fun requestUpload()
}

/** No-op placeholder until a WorkManager-backed implementation lands (Chunk 4). */
class NoOpImageWorkScheduler @Inject constructor() : ImageWorkScheduler {
    override fun requestUpload() = Unit
}
