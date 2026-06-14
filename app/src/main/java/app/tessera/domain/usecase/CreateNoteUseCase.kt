package app.tessera.domain.usecase

import app.tessera.domain.model.Note
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

class CreateNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(ownerId: UserId? = null): Note = notesRepository.createNote(ownerId)
}
