package app.tessera.domain.usecase

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/**
 * Creates an independent copy of a note: same content, tags and owner, but a fresh id and
 * timestamps. The copy starts unpinned and unarchived. Returns null if the source is gone.
 */
class DuplicateNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId): Note? {
        val original = notesRepository.getNote(id) ?: return null
        val draft = notesRepository.createNote(original.ownerId)
        return notesRepository.updateNote(
            draft.copy(
                content = original.content,
                tags = original.tags,
                isPinned = false,
            ),
        )
    }
}
