package app.tessera.domain.usecase

import app.tessera.data.drive.DrivePermission

/**
 * The declarative delta between a Drive file's current permissions and the set of note members who
 * should be able to read it. The owner's own permission is never included in [toRevoke].
 */
data class PermissionDiff(
    /** Emails (lowercased) that need a new `type=user` reader permission created. */
    val toGrant: List<String>,
    /** Existing `type=user` reader permissions that must be deleted. */
    val toRevoke: List<DrivePermission>,
    /** Fallback mode only: an `anyone` reader permission must be created on this file. */
    val ensureAnyoneLink: Boolean,
)

private const val TYPE_USER = "user"
private const val TYPE_ANYONE = "anyone"
private const val ROLE_READER = "reader"
private const val ROLE_OWNER = "owner"

/**
 * Pure reconciliation of a Drive file's permissions against its target reader set.
 *
 * @param targetEmails member emails the file should be readable by (lowercased before comparison).
 * @param actual the file's current Drive permissions (from `permissions.list`).
 * @param myEmail the signed-in owner's email; their own permission is never revoked (case-insensitive).
 * @param anyoneLinkMode `true` when the capability-URL fallback is active (per-email grants failed).
 */
fun computePermissionDiff(
    targetEmails: Set<String>,
    actual: List<DrivePermission>,
    myEmail: String,
    anyoneLinkMode: Boolean,
): PermissionDiff {
    val target = targetEmails.mapTo(mutableSetOf()) { it.lowercase() }
    val mine = myEmail.lowercase()
    val isShared = target.isNotEmpty()

    val userReaders = actual.filter { it.type == TYPE_USER && it.role == ROLE_READER }
    val existingReaderEmails =
        userReaders.mapNotNull { it.emailAddress?.lowercase() }.toSet()

    val toGrant = target.filter { it !in existingReaderEmails }

    val toRevoke = mutableListOf<DrivePermission>()
    for (perm in actual) {
        when (perm.type) {
            TYPE_USER -> {
                // Never touch the owner or my own permission; only prune stray reader grants.
                if (perm.role == ROLE_OWNER) continue
                if (perm.role != ROLE_READER) continue
                val email = perm.emailAddress?.lowercase()
                if (email == mine) continue
                if (email == null || email !in target) toRevoke += perm
            }
            TYPE_ANYONE -> {
                // Main scheme: stray anyone links are always removed. Fallback: keep the link while
                // the note is shared, but drop it once the note goes private (target empty).
                if (!anyoneLinkMode || !isShared) toRevoke += perm
            }
        }
    }

    val hasAnyone = actual.any { it.type == TYPE_ANYONE }
    val ensureAnyoneLink = anyoneLinkMode && isShared && !hasAnyone

    return PermissionDiff(toGrant = toGrant, toRevoke = toRevoke, ensureAnyoneLink = ensureAnyoneLink)
}
