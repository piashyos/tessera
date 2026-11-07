package app.tessera.ui.editor.engine

import android.graphics.drawable.Drawable
import android.util.Size

sealed interface ImageResult {
    data class Loaded(val drawable: Drawable) : ImageResult
    data object Unavailable : ImageResult
}

/**
 * Where the image spans of the engine get their pictures (spec §3.1). The engine knows nothing of files, Room or Drive; the host gives it
 * an implementation (`NoteObjectImageSource`). [src] is the `tessera-img://` source of the block, [maxWidthPx] the width of the text area:
 * the drawable comes baked (`w` and `rot` applied, width = min(rotated width, maxWidth) × w%) and is drawn in its own size.
 */
interface ObjectImageSource {
    /** The drawable if it is in the memory cache, else `null`. Synchronous, no disk. */
    fun peek(src: String, maxWidthPx: Int): Drawable?

    /** The size [src] will have at [maxWidthPx], known before the decode (prefetched bounds), else `null`. Synchronous. */
    fun size(src: String, maxWidthPx: Int): Size?

    /**
     * Starts loading [src]. [onResult] runs on the main thread, at most once, and never after the returned cancel function was called.
     */
    fun load(src: String, maxWidthPx: Int, onResult: (ImageResult) -> Unit): () -> Unit
}
