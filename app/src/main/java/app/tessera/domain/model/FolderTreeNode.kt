package app.tessera.domain.model

/** Tree projection of a flat folder list: a root folder + its immediate children. */
data class FolderTreeNode(
    val folder: Folder,
    val children: List<Folder>,
)
