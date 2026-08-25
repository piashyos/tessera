package app.tessera.data.local

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * Runs a block of suspend DAO calls inside a single Room transaction. Extracted behind an interface
 * so sync/merge logic can be unit-tested with fake DAOs (a pass-through implementation) while
 * production wraps the real [NotesDatabase] transaction — where atomic commit/rollback and
 * deferred-foreign-key semantics actually matter.
 */
interface Transactor {
    suspend fun <T> transaction(block: suspend () -> T): T
}

class RoomTransactor @Inject constructor(
    private val database: NotesDatabase,
) : Transactor {
    override suspend fun <T> transaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
