package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderValidationTest {
    private fun folder(id: String, parent: String? = null) =
        Folder(FolderId(id), "f-$id", parent?.let(::FolderId), createdAt = 0L)

    @Test fun `cycle detected when newParent is self`() {
        val a = folder("a")
        assertTrue(wouldCreateCycle(listOf(a), FolderId("a"), FolderId("a")))
    }

    @Test fun `cycle detected when newParent is descendant`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        assertTrue(wouldCreateCycle(listOf(a, b), FolderId("a"), FolderId("b")))
    }

    @Test fun `no cycle for sibling move`() {
        val a = folder("a")
        val b = folder("b")
        val c = folder("c", parent = "a")
        assertFalse(wouldCreateCycle(listOf(a, b, c), FolderId("c"), FolderId("b")))
    }

    @Test fun `no cycle when moving to root`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        assertFalse(wouldCreateCycle(listOf(a, b), FolderId("b"), null))
    }

    @Test fun `depth violated when nesting a child under another child`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        val c = folder("c")
        assertTrue(wouldExceedMaxDepth(listOf(a, b, c), FolderId("c"), FolderId("b")))
    }

    @Test fun `depth ok when nesting root under root`() {
        val a = folder("a")
        val b = folder("b")
        assertFalse(wouldExceedMaxDepth(listOf(a, b), FolderId("b"), FolderId("a")))
    }

    @Test fun `depth ok when moving to root regardless`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        assertFalse(wouldExceedMaxDepth(listOf(a, b), FolderId("b"), null))
    }

    @Test fun `subtree depth too deep — moving root that already has children to be a child`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        val c = folder("c")
        assertTrue(wouldExceedMaxDepth(listOf(a, b, c), FolderId("a"), FolderId("c")))
    }
}
