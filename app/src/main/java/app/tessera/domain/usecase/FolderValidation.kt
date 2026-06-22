package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId

const val MAX_FOLDER_DEPTH = 2

fun wouldCreateCycle(
    folders: List<Folder>,
    folderId: FolderId,
    newParentId: FolderId?,
): Boolean {
    if (newParentId == null) return false
    if (newParentId == folderId) return true
    val byId = folders.associateBy { it.id }
    var cursor: FolderId? = newParentId
    val seen = HashSet<FolderId>()
    while (cursor != null) {
        if (cursor == folderId) return true
        if (!seen.add(cursor)) return true
        cursor = byId[cursor]?.parentId
    }
    return false
}

fun wouldExceedMaxDepth(
    folders: List<Folder>,
    folderId: FolderId,
    newParentId: FolderId?,
    maxDepth: Int = MAX_FOLDER_DEPTH,
): Boolean {
    val byId = folders.associateBy { it.id }
    val childrenOf = folders.groupBy { it.parentId }

    val depthOfParent: Int = if (newParentId == null) 0 else {
        var d = 1
        var cursor: FolderId? = byId[newParentId]?.parentId
        while (cursor != null) {
            d += 1
            cursor = byId[cursor]?.parentId
        }
        d
    }

    fun subtreeDepth(rootId: FolderId): Int {
        val kids = childrenOf[rootId].orEmpty()
        if (kids.isEmpty()) return 1
        return 1 + kids.maxOf { subtreeDepth(it.id) }
    }

    val subtree = subtreeDepth(folderId)
    return depthOfParent + subtree > maxDepth
}
