package app.tessera.domain.usecase

import app.tessera.data.drive.DrivePermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputePermissionDiffTest {
    private val myEmail = "owner@example.com"

    private fun user(id: String, email: String, role: String = "reader") =
        DrivePermission(id = id, emailAddress = email, type = "user", role = role)

    private fun anyone(id: String = "anyone-1") =
        DrivePermission(id = id, emailAddress = null, type = "anyone", role = "reader")

    private val owner = DrivePermission(id = "owner-perm", emailAddress = myEmail, type = "user", role = "owner")

    @Test
    fun `grants target emails with no existing reader permission`() {
        val diff = computePermissionDiff(
            targetEmails = setOf("alice@example.com", "bob@example.com"),
            actual = listOf(owner, user("p1", "alice@example.com")),
            myEmail = myEmail,
            anyoneLinkMode = false,
        )

        assertEquals(listOf("bob@example.com"), diff.toGrant)
        assertTrue(diff.toRevoke.isEmpty())
        assertFalse(diff.ensureAnyoneLink)
    }

    @Test
    fun `lowercases target emails before comparison`() {
        val diff = computePermissionDiff(
            targetEmails = setOf("Alice@Example.com"),
            actual = listOf(owner, user("p1", "alice@example.com")),
            myEmail = myEmail,
            anyoneLinkMode = false,
        )

        assertTrue(diff.toGrant.isEmpty())
        assertTrue(diff.toRevoke.isEmpty())
    }

    @Test
    fun `revokes user readers not in target`() {
        val stray = user("p-stray", "removed@example.com")
        val diff = computePermissionDiff(
            targetEmails = setOf("alice@example.com"),
            actual = listOf(owner, user("p1", "alice@example.com"), stray),
            myEmail = myEmail,
            anyoneLinkMode = false,
        )

        assertTrue(diff.toGrant.isEmpty())
        assertEquals(listOf(stray), diff.toRevoke)
    }

    @Test
    fun `never revokes owner or my own email`() {
        // My own account appears both as owner and (defensively) as a reader; neither is revoked
        // even though my email is not a member.
        val myReader = user("p-me", "OWNER@example.com")
        val diff = computePermissionDiff(
            targetEmails = setOf("alice@example.com"),
            actual = listOf(owner, myReader, user("p1", "alice@example.com")),
            myEmail = myEmail,
            anyoneLinkMode = false,
        )

        assertTrue(diff.toRevoke.isEmpty())
        assertTrue(diff.toGrant.isEmpty())
    }

    @Test
    fun `main mode always revokes stray anyone link and never ensures one`() {
        val link = anyone()
        val diff = computePermissionDiff(
            targetEmails = setOf("alice@example.com"),
            actual = listOf(owner, link, user("p1", "alice@example.com")),
            myEmail = myEmail,
            anyoneLinkMode = false,
        )

        assertEquals(listOf(link), diff.toRevoke)
        assertFalse(diff.ensureAnyoneLink)
    }

    @Test
    fun `fallback mode ensures anyone link when shared and none exists`() {
        val diff = computePermissionDiff(
            targetEmails = setOf("alice@example.com"),
            actual = listOf(owner),
            myEmail = myEmail,
            anyoneLinkMode = true,
        )

        assertTrue(diff.ensureAnyoneLink)
        // In fallback mode per-email grants are not created.
        assertTrue(diff.toRevoke.isEmpty())
    }

    @Test
    fun `fallback mode keeps existing anyone link and does not re-ensure`() {
        val diff = computePermissionDiff(
            targetEmails = setOf("alice@example.com"),
            actual = listOf(owner, anyone()),
            myEmail = myEmail,
            anyoneLinkMode = true,
        )

        assertFalse(diff.ensureAnyoneLink)
        assertTrue(diff.toRevoke.isEmpty())
    }

    @Test
    fun `fallback mode revokes anyone link when note went private`() {
        val link = anyone()
        val diff = computePermissionDiff(
            targetEmails = emptySet(),
            actual = listOf(owner, link),
            myEmail = myEmail,
            anyoneLinkMode = true,
        )

        assertEquals(listOf(link), diff.toRevoke)
        assertFalse(diff.ensureAnyoneLink)
    }

    @Test
    fun `main mode revokes all user readers when note went private`() {
        val a = user("p1", "alice@example.com")
        val b = user("p2", "bob@example.com")
        val diff = computePermissionDiff(
            targetEmails = emptySet(),
            actual = listOf(owner, a, b),
            myEmail = myEmail,
            anyoneLinkMode = false,
        )

        assertEquals(setOf(a, b), diff.toRevoke.toSet())
        assertTrue(diff.toGrant.isEmpty())
        assertFalse(diff.ensureAnyoneLink)
    }

    @Test
    fun `fallback mode does not ensure link for a private note`() {
        val diff = computePermissionDiff(
            targetEmails = emptySet(),
            actual = listOf(owner),
            myEmail = myEmail,
            anyoneLinkMode = true,
        )

        assertFalse(diff.ensureAnyoneLink)
        assertTrue(diff.toRevoke.isEmpty())
    }
}
