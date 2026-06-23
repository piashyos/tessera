package app.tessera.domain.model

/**
 * The current user attempted a share operation their role does not permit. Raised by the share
 * UseCases as a fail-fast guard before any data-source call, so the UI can show a clear message
 * without a round-trip that the security rules would reject anyway.
 */
class ShareAccessDeniedException(message: String) : Exception(message)

/** A share operation referenced a note that no longer exists locally. */
class NoteNotFoundException(id: NoteId) : Exception("Note ${id.value} not found")
