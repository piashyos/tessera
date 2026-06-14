package app.tessera.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteFtsEntity
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun tagDao(): TagDao
}
