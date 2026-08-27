package app.tessera.domain.usecase

import android.net.Uri
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.NoteImagesRepository
import javax.inject.Inject

/**
 * Inserts a picked image into [noteId]: compresses it, stores a `LOCAL_ONLY` row, and requests an
 * upload. Returns the new imageId.
 */
class InsertImageUseCase @Inject constructor(
    private val imagesRepository: NoteImagesRepository,
) {
    suspend operator fun invoke(noteId: NoteId, source: Uri): Result<String> =
        imagesRepository.insertLocalImage(noteId, source)
}
