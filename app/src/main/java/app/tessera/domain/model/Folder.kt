package app.tessera.domain.model

data class FolderId(val value: String)

data class Folder(
    val id: FolderId,
    val name: String,
    val createdAt: Long,
)
