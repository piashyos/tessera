package app.tessera.domain.repository

import android.net.Uri
import app.tessera.domain.model.NoteId

/**
 * Owns the on-disk + local-DB side of adding a Drive-backed image to a note: compressing the
 * picked image into `filesDir/images/<uuid>.webp`, inserting the `note_images` row, and
 * requesting an upload via the sync seam.
 */
interface NoteImagesRepository {
    /**
     * Compresses [source] into `filesDir/images/<uuid>.webp`, inserts a `LOCAL_ONLY` row owned by
     * the current signed-in user (or unowned when signed out) for [noteId], and requests an
     * upload. Returns the new imageId, or a failure if compression/insertion fails.
     */
    suspend fun insertLocalImage(noteId: NoteId, source: Uri): Result<String>
}
