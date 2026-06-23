package app.tessera.domain.model

/**
 * Access level a user holds on a shared note. Ordered from most to least privileged.
 *
 * Permissions are derived from the role as pure functions — we never persist capability flags,
 * so a role change instantly re-derives what the user may do.
 */
enum class ShareRole {
    OWNER,
    MANAGER,
    EDITOR,
    VIEWER,
    ;

    companion object {
        /** Parses a wire/storage value case-insensitively; unknown or null falls back to [VIEWER]. */
        fun fromWire(raw: String?): ShareRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIEWER
    }
}

/** May edit the note's content (title/body/tags). */
val ShareRole.canEditContent: Boolean
    get() = this == ShareRole.OWNER || this == ShareRole.MANAGER || this == ShareRole.EDITOR

/** May delete the note for every member. */
val ShareRole.canDeleteForAll: Boolean
    get() = this == ShareRole.OWNER || this == ShareRole.MANAGER

/** May invite/remove members and change roles. */
val ShareRole.canManageAccess: Boolean
    get() = this == ShareRole.OWNER || this == ShareRole.MANAGER

/** May hand ownership to another member. Out of scope for the current milestone. */
val ShareRole.canTransferOwnership: Boolean
    get() = this == ShareRole.OWNER

/** May leave the note. The owner cannot leave — they unshare or delete instead. */
val ShareRole.canLeave: Boolean
    get() = this != ShareRole.OWNER
