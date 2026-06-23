package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareMember
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Streams a shared note's members. Readable by any member, so no permission gate is needed. */
class ObserveMembersUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    operator fun invoke(id: NoteId): Flow<List<ShareMember>> =
        notesRepository.observeMembers(id)
}
