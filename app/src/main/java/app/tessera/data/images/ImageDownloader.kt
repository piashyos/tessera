package app.tessera.data.images

import java.io.File

/**
 * The downloader seam Aztec's image getter (`TesseraImageLoader`) delegates to when an image has no
 * local file yet. The DI-provided implementation is [DriveImageDownloader]; a no-op default (returning
 * `null`) is used where Drive isn't wired, which surfaces the "unavailable" placeholder.
 *
 * Lives in the data layer (not `ui/editor/components`) so the concrete Drive-backed implementation
 * doesn't force a data → ui dependency.
 */
fun interface ImageDownloader {
    /** Fetch [imageId] to a local file, or return `null` when it can't be retrieved. */
    suspend fun download(imageId: String): File?
}
