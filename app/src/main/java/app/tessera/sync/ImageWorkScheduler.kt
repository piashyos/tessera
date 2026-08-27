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

    /**
     * Requests that the signed-in user's uploaded images have their Drive sharing permissions
     * reconciled against their notes' current member sets as soon as possible.
     */
    fun requestPermissionSync()
}

/**
 * No-op scheduler used as a default so non-WorkManager call sites (and tests) can construct
 * collaborators without wiring background work.
 */
object NoOpImageWorkScheduler : ImageWorkScheduler {
    override fun requestUpload() = Unit

    override fun requestPermissionSync() = Unit
}
