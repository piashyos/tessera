package app.tessera.sync

/**
 * Requests background work for Drive-backed images. Implementations coalesce overlapping
 * requests, similar to [app.tessera.domain.repository.SyncScheduler].
 *
 * @see WorkManagerImageWorkScheduler
 */
interface ImageWorkScheduler {
    /** Requests that pending `LOCAL_ONLY` images be uploaded to Drive as soon as possible. */
    fun requestUpload()
}
