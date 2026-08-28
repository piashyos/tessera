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

    /**
     * Requests an orphan sweep: stamp images no note references any more and, once past the
     * hysteresis window, trash them from Drive + drop their rows. Triggered after a note is saved
     * (a removed image reference may have just orphaned a row) and on a periodic schedule.
     */
    fun requestCleanup()
}

/**
 * No-op scheduler used as a default so non-WorkManager call sites (and tests) can construct
 * collaborators without wiring background work.
 */
object NoOpImageWorkScheduler : ImageWorkScheduler {
    override fun requestUpload() = Unit

    override fun requestPermissionSync() = Unit

    override fun requestCleanup() = Unit
}
