package app.tessera.domain.repository

/**
 * Requests that local pending changes be pushed to the remote backend as soon as possible.
 * Implementations coalesce overlapping requests; a single request after a burst of edits is
 * sufficient because the underlying sync pushes ALL pending notes.
 */
interface SyncScheduler {
    fun requestSync()
}
