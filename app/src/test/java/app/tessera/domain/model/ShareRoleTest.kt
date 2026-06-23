package app.tessera.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareRoleTest {
    @Test
    fun `canEditContent allows owner manager editor, denies viewer`() {
        assertTrue(ShareRole.OWNER.canEditContent)
        assertTrue(ShareRole.MANAGER.canEditContent)
        assertTrue(ShareRole.EDITOR.canEditContent)
        assertFalse(ShareRole.VIEWER.canEditContent)
    }

    @Test
    fun `canDeleteForAll allows owner manager only`() {
        assertTrue(ShareRole.OWNER.canDeleteForAll)
        assertTrue(ShareRole.MANAGER.canDeleteForAll)
        assertFalse(ShareRole.EDITOR.canDeleteForAll)
        assertFalse(ShareRole.VIEWER.canDeleteForAll)
    }

    @Test
    fun `canManageAccess allows owner manager only`() {
        assertTrue(ShareRole.OWNER.canManageAccess)
        assertTrue(ShareRole.MANAGER.canManageAccess)
        assertFalse(ShareRole.EDITOR.canManageAccess)
        assertFalse(ShareRole.VIEWER.canManageAccess)
    }

    @Test
    fun `canTransferOwnership is owner only`() {
        assertTrue(ShareRole.OWNER.canTransferOwnership)
        assertFalse(ShareRole.MANAGER.canTransferOwnership)
        assertFalse(ShareRole.EDITOR.canTransferOwnership)
        assertFalse(ShareRole.VIEWER.canTransferOwnership)
    }

    @Test
    fun `canLeave is everyone except owner`() {
        assertFalse(ShareRole.OWNER.canLeave)
        assertTrue(ShareRole.MANAGER.canLeave)
        assertTrue(ShareRole.EDITOR.canLeave)
        assertTrue(ShareRole.VIEWER.canLeave)
    }

    @Test
    fun `fromWire parses known roles case-insensitively, defaults to viewer`() {
        assertEquals(ShareRole.OWNER, ShareRole.fromWire("OWNER"))
        assertEquals(ShareRole.MANAGER, ShareRole.fromWire("manager"))
        assertEquals(ShareRole.EDITOR, ShareRole.fromWire("Editor"))
        assertEquals(ShareRole.VIEWER, ShareRole.fromWire("VIEWER"))
        assertEquals(ShareRole.VIEWER, ShareRole.fromWire(null))
        assertEquals(ShareRole.VIEWER, ShareRole.fromWire("garbage"))
    }
}
