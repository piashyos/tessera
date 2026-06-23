package app.tessera.data.mapper

import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.SharedMemberEntity
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.domain.model.ShareRole
import javax.inject.Inject

/**
 * Maps between the `sharedNotes/{id}` document shape ([RemoteSharedNote]) and local Room rows
 * ([NoteEntity] + [SharedMemberEntity]). Pure and JVM-unit-testable.
 */
class SharedNoteMapper @Inject constructor() {
    /** The current user's role on [remote], defaulting to [ShareRole.VIEWER] if absent. */
    fun myRole(remote: RemoteSharedNote, myUid: String): ShareRole =
        ShareRole.fromWire(remote.members[myUid]?.role)

    /** Projects a shared document into the local note row (always `shareState = SHARED`). */
    fun toEntity(remote: RemoteSharedNote, myUid: String, syncedAt: Long?): NoteEntity =
        NoteEntity(
            id = remote.id,
            title = remote.title,
            content = remote.content,
            isPinned = remote.isPinned,
            createdAt = remote.createdAt,
            updatedAt = remote.updatedAt,
            deletedAt = remote.deletedAt,
            syncedAt = syncedAt,
            ownerId = remote.ownerId,
            isArchived = remote.isArchived,
            shareState = NoteEntity.SHARE_STATE_SHARED,
            myRole = myRole(remote, myUid).name,
        )

    /** Flattens the remote `members` map into local mirror rows for [RemoteSharedNote.id]. */
    fun toMemberEntities(remote: RemoteSharedNote): List<SharedMemberEntity> =
        remote.members.map { (uid, member) ->
            SharedMemberEntity(
                noteId = remote.id,
                uid = uid,
                displayName = member.displayName,
                email = member.email,
                photoUrl = member.photoUrl,
                role = member.role,
                joinedAt = member.joinedAt,
            )
        }

    /**
     * Builds the content-bearing shared document pushed by an editing device. Membership fields
     * ([RemoteSharedNote.members]/[RemoteSharedNote.memberIds]) are intentionally left empty —
     * [app.tessera.data.remote.RemoteSharedNotesDataSource.pushSharedNote] never writes them.
     */
    fun toRemoteContent(entity: NoteEntity, tagNames: List<String>): RemoteSharedNote =
        RemoteSharedNote(
            id = entity.id,
            ownerId = entity.ownerId.orEmpty(),
            title = entity.title,
            content = entity.content,
            tags = tagNames,
            isPinned = entity.isPinned,
            isArchived = entity.isArchived,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
        )

    /** Converts a local member row back to its remote map entry (used when first sharing a note). */
    fun toRemoteMember(entity: SharedMemberEntity): RemoteShareMember =
        RemoteShareMember(
            role = entity.role,
            displayName = entity.displayName,
            email = entity.email,
            photoUrl = entity.photoUrl,
            joinedAt = entity.joinedAt,
        )
}
