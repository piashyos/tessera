package app.tessera.domain.model

sealed interface FolderSaveResult {
    data class Saved(val folder: Folder) : FolderSaveResult
    data object NameTaken : FolderSaveResult
    data class Invalid(val reason: String) : FolderSaveResult
}
