package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSubfolderUseCaseTest {
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private var created: Folder? = null
    private val fake = object : FakeFolderRepo() {
        override fun observeFolders() = foldersFlow
        override suspend fun createSubfolder(
            parentId: FolderId,
            name: String,
            color: String?,
            icon: String?,
        ): FolderSaveResult {
            val f = Folder(FolderId("new"), name, parentId, 0L, color, icon)
            created = f
            return FolderSaveResult.Saved(f)
        }
    }
    private val useCase = CreateSubfolderUseCase(fake)

    @Test fun `creates under root parent`() = runTest {
        val root = Folder(FolderId("root"), "Root", null, 0L)
        foldersFlow.value = listOf(root)
        val result = useCase(root.id, "Sub", "blue", "lucide:star")
        assertTrue(result is FolderSaveResult.Saved)
        assertEquals("Sub", created?.name)
        assertEquals("blue", created?.color)
        assertEquals("lucide:star", created?.icon)
    }

    @Test fun `rejects when parent is a child`() = runTest {
        val root = Folder(FolderId("root"), "Root", null, 0L)
        val child = Folder(FolderId("child"), "Child", root.id, 0L)
        foldersFlow.value = listOf(root, child)
        val result = useCase(child.id, "Deep")
        assertEquals(FolderSaveResult.Invalid("depth"), result)
        assertEquals(null, created)
    }

    @Test fun `rejects when parent doesn't exist`() = runTest {
        foldersFlow.value = emptyList()
        val result = useCase(FolderId("ghost"), "X")
        assertEquals(FolderSaveResult.Invalid("parent not found"), result)
        assertEquals(null, created)
    }
}
