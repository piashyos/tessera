package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveFolderUseCaseTest {
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private val moves = mutableListOf<Pair<FolderId, FolderId?>>()
    private val fake = object : FakeFolderRepo() {
        override fun observeFolders() = foldersFlow
        override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) {
            moves += id to newParentId
        }
    }
    private val useCase = MoveFolderUseCase(fake)

    private fun folder(id: String, parent: String? = null) =
        Folder(FolderId(id), "f-$id", parent?.let(::FolderId), 0L)

    @Test fun `rejects self-parent`() = runTest {
        foldersFlow.value = listOf(folder("a"))
        val result = useCase(FolderId("a"), FolderId("a"))
        assertTrue(result.isFailure)
        assertTrue(moves.isEmpty())
    }

    @Test fun `rejects cycle through descendant`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b", "a"))
        val result = useCase(FolderId("a"), FolderId("b"))
        assertTrue(result.isFailure)
    }

    @Test fun `rejects depth violation`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b", "a"), folder("c"))
        val result = useCase(FolderId("c"), FolderId("b"))
        assertTrue(result.isFailure)
    }

    @Test fun `accepts valid root-to-root move`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b"))
        val result = useCase(FolderId("b"), FolderId("a"))
        assertTrue(result.isSuccess)
        assertTrue(moves.contains(FolderId("b") to FolderId("a")))
    }

    @Test fun `accepts move to root`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b", "a"))
        val result = useCase(FolderId("b"), null)
        assertTrue(result.isSuccess)
        assertTrue(moves.contains(FolderId("b") to null))
    }
}
