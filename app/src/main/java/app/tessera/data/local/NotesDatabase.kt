package app.tessera.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.tessera.data.local.dao.FolderDao
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.dao.SharedMemberDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import app.tessera.data.local.entity.NoteFtsEntity
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.SharedMemberEntity
import app.tessera.data.local.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class,
        FolderEntity::class,
        NoteFolderCrossRef::class,
        SharedMemberEntity::class,
        NoteImageEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(ImageStatusConverter::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun tagDao(): TagDao

    abstract fun folderDao(): FolderDao

    abstract fun sharedMemberDao(): SharedMemberDao

    abstract fun noteImageDao(): NoteImageDao
}
