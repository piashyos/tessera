package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.SharedMemberDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.SharedMemberEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.mapper.SharedNoteMapper
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.data.remote.UserDirectoryDataSource
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.NoteNotFoundException
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SharedNotesGatewayImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val sharedMemberDao: SharedMemberDao,
    private val remoteShared: RemoteSharedNotesDataSource,
    private val userDirectory: UserDirectoryDataSource,
    private val authRepository: AuthRepository,
    private val noteMapper: NoteMapper,
    private val sharedMapper: SharedNoteMapper,
    private val clock: Clock,
) : SharedNotesGateway {
    override suspend fun shareNote(id: NoteId): Result<Unit> =
        runCatching {
            val entity = noteDao.getById(id.value) ?: throw NoteNotFoundException(id)
            val profile = authRepository.currentProfile.value ?: error("Not signed in")
            val ownerUid = profile.id.value
            val ownerMember =
                SharedMemberEntity(
                    noteId = id.value,
                    uid = ownerUid,
                    displayName = profile.displayName.orEmpty(),
                    email = profile.email.orEmpty(),
                    photoUrl = profile.photoUrl,
                    role = ShareRole.OWNER.name,
                    joinedAt = clock.nowMs(),
                )
            val document =
                sharedMapper.toRemoteContent(entity, tagDao.tagsForNote(id.value)).copy(
                    ownerId = ownerUid,
                    members = mapOf(ownerUid to sharedMapper.toRemoteMember(ownerMember)),
                    memberIds = listOf(ownerUid),
                )

            remoteShared.shareNote(ownerUid, document)
            noteDao.setShareState(id.value, NoteEntity.SHARE_STATE_SHARED, ShareRole.OWNER.name)
            sharedMemberDao.replaceForNote(id.value, listOf(ownerMember))
        }

    override suspend fun inviteByEmail(
        id: NoteId,
        email: String,
        role: ShareRole,
    ): Result<InviteResult> =
        runCatching {
            val user = userDirectory.lookupByEmail(email) ?: return@runCatching InviteResult.UserNotFound
            val targetUid = user.uid.value
            if (sharedMemberDao.getForNote(id.value).any { it.uid == targetUid }) {
                return@runCatching InviteResult.AlreadyMember
            }

            val member =
                SharedMemberEntity(
                    noteId = id.value,
                    uid = targetUid,
                    displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email,
                    email = user.email,
                    photoUrl = user.photoUrl,
                    role = role.name,
                    joinedAt = clock.nowMs(),
                )

            remoteShared.addMember(id.value, targetUid, sharedMapper.toRemoteMember(member))
            sharedMemberDao.upsertAll(listOf(member))
            InviteResult.Added
        }

    override suspend fun changeRole(
        id: NoteId,
        uid: UserId,
        role: ShareRole,
    ): Result<Unit> =
        runCatching {
            remoteShared.updateRole(id.value, uid.value, role.name)
            val updated =
                sharedMemberDao.getForNote(id.value).map { member ->
                    if (member.uid == uid.value) member.copy(role = role.name) else member
                }
            sharedMemberDao.replaceForNote(id.value, updated)
        }

    override suspend fun removeMember(id: NoteId, uid: UserId): Result<Unit> =
        runCatching {
            remoteShared.removeMember(id.value, uid.value)
            val remaining = sharedMemberDao.getForNote(id.value).filterNot { it.uid == uid.value }
            sharedMemberDao.replaceForNote(id.value, remaining)
        }

    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> =
        runCatching {
            val myUid = authRepository.currentUser.value?.value ?: error("Not signed in")
            remoteShared.leaveNote(id.value, myUid)
            // The note leaves this device entirely; CASCADE clears its shared_members rows.
            tagDao.clearTagsForNote(id.value)
            noteDao.hardDelete(id.value)
        }

    override suspend fun unshareNote(id: NoteId): Result<Unit> =
        runCatching {
            val entity = noteDao.getById(id.value) ?: throw NoteNotFoundException(id)
            val ownerUid =
                entity.ownerId ?: authRepository.currentUser.value?.value ?: error("No owner")
            val privateNote = noteMapper.toRemote(entity, tagDao.tagsForNote(id.value))

            remoteShared.unshareNote(ownerUid, privateNote)
            noteDao.setShareState(id.value, NoteEntity.SHARE_STATE_PRIVATE, null)
            sharedMemberDao.deleteForNote(id.value)
        }

    override fun observeMembers(id: NoteId): Flow<List<ShareMember>> =
        sharedMemberDao.observeForNote(id.value).map { rows ->
            rows.map(sharedMapper::toDomainMember)
        }

    override suspend fun membersForNote(id: NoteId): List<ShareMember> =
        sharedMemberDao.getForNote(id.value).map(sharedMapper::toDomainMember)
}
