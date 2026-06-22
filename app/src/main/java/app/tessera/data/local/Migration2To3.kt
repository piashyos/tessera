package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds last-write-wins + sync metadata columns to `folders`, mirroring the schema of
 * the `notes` table. Existing rows are seeded with `updatedAt = createdAt` and
 * `syncedAt = NULL` so the next push uploads them.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE folders ADD COLUMN deletedAt INTEGER")
        db.execSQL("ALTER TABLE folders ADD COLUMN syncedAt INTEGER")
        db.execSQL("UPDATE folders SET updatedAt = createdAt WHERE updatedAt = 0")
    }
}
