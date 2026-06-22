# Material You Redesign — Plan 03: Folders Data Layer (parentId, 2-level hierarchy)

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Расширить folders data layer для поддержки 2-уровневой иерархии (root + 1 уровень детей): добавить `parentId` в domain, entity, миграцию Room (3→4), Firestore RemoteFolder, репозиторий, use cases (`MoveFolderUseCase`, `CreateSubfolderUseCase`, `folderTreeFlow`). Без изменения UI экранов.

**Architecture:** `FolderEntity.parentId` — nullable, с `@ForeignKey(... onDelete = SET_NULL)` и `@Index`. Domain `Folder.parentId: FolderId?`. Валидация depth/cycles живёт в `MoveFolderUseCase` и `CreateSubfolderUseCase` (доменный слой). `folderTreeFlow()` — публикуется через репозиторий, маппит flat-список в `List<FolderTreeNode>` (root + children-под-каждым).

**Tech Stack:** Room 2.6.1 + Migration API, Firestore (RemoteFolder backward-compat: отсутствие поля → root), Kotlin Flow, Hilt.

**Spec reference:** `docs/superpowers/specs/2026-06-19-tessera-material-you-redesign-design.md` §8 (Folders data layer) + §9 (Error handling) + §10 (Migration & rollout).

**Что этот план НЕ делает:**
- НЕ переписывает UI компоненты (`FoldersDrawer`, `FolderPickerPopup`) — это Plan 04 (`NotesListScreen` redesign).
- НЕ убирает старые `*UseCase` (`CreateFolderUseCase`, `DeleteFolderUseCase` и т.д.) — все они продолжают работать, но `createFolder(name)` теперь создаёт root-folder (parentId = null).
- НЕ удаляет старый `FoldersDrawer.kt` — его рефакторинг в Plan 04.

---

## File map

```
app/src/main/java/app/tessera/
  domain/model/Folder.kt                       (MODIFY: + parentId)
  domain/model/FolderTreeNode.kt               (NEW)
  domain/repository/FolderRepository.kt        (MODIFY: + folderTreeFlow, moveFolder, createSubfolder)
  domain/usecase/FolderUseCases.kt             (MODIFY: + MoveFolderUseCase, CreateSubfolderUseCase, ObserveFolderTreeUseCase)
  domain/usecase/FolderValidation.kt           (NEW: depth/cycle validation pure functions)
  data/local/entity/FolderEntity.kt            (MODIFY: + parentId column + @ForeignKey + @Index)
  data/local/dao/FolderDao.kt                  (MODIFY: + updateParent query)
  data/local/Migration3To4.kt                  (NEW)
  data/local/NotesDatabase.kt                  (MODIFY: version 3 → 4, add migration to DI)
  data/repository/FolderRepositoryImpl.kt      (MODIFY: implement new methods, build folderTreeFlow)
  data/remote/RemoteFolder.kt                  (MODIFY: + parentId: String?)
  data/remote/FirestoreFoldersClient.kt        (MODIFY: pass parentId through)
  data/mapper/FolderMapper.kt                  (MODIFY or NEW: entity↔domain & remote↔entity mapping)

app/src/test/java/app/tessera/
  domain/usecase/FolderValidationTest.kt         (NEW)
  domain/usecase/MoveFolderUseCaseTest.kt        (NEW)
  domain/usecase/CreateSubfolderUseCaseTest.kt   (NEW)
  data/local/Migration3To4Test.kt                (NEW — uses Room MigrationTestHelper)
  data/repository/FolderRepositoryImplTest.kt    (MODIFY: + folderTreeFlow + ON DELETE SET NULL cases)
```

---

## Chunk 1: Domain — Folder.parentId + FolderTreeNode + validation

### Task 1: Add `parentId` to `Folder` domain model

**Files:**
- Modify: `app/src/main/java/app/tessera/domain/model/Folder.kt`

- [ ] **Step 1: Modify Folder data class**

Replace contents:

```kotlin
package app.tessera.domain.model

data class FolderId(val value: String)

data class Folder(
    val id: FolderId,
    val name: String,
    val parentId: FolderId?,
    val createdAt: Long,
)
```

- [ ] **Step 2: Build — expect cascading errors**

Run: `./gradlew :app:compileDebugKotlin`
Expected: COMPILATION ERRORS at every call site that constructs `Folder(...)` — mappers, tests, fakes, repository impl. Catalog the locations; we'll fix them in subsequent tasks. Это normal — список call-sites:

```bash
grep -rln "Folder(" app/src/main/java/app/tessera/ app/src/test/java/app/tessera/ app/src/androidTest/java/app/tessera/ 2>/dev/null
```

- [ ] **Step 3: Do NOT commit yet**

Wait for next task — мы починим mappers, тогда коммит этой задачи + следующей вместе.

---

### Task 2: Create `FolderTreeNode` value type

**Files:**
- Create: `app/src/main/java/app/tessera/domain/model/FolderTreeNode.kt`

- [ ] **Step 1: Create FolderTreeNode**

```kotlin
package app.tessera.domain.model

/** Tree projection of a flat folder list: a root folder + its immediate children. */
data class FolderTreeNode(
    val folder: Folder,
    val children: List<Folder>,
)
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: same errors as Task 1 (unrelated to new file).

- [ ] **Step 3: Stage but don't commit**

---

### Task 3: Pure validation functions (`FolderValidation.kt`)

**Files:**
- Create: `app/src/main/java/app/tessera/domain/usecase/FolderValidation.kt`
- Create: `app/src/test/java/app/tessera/domain/usecase/FolderValidationTest.kt`

Two pure utility functions:
- `wouldCreateCycle(folders, folderId, newParentId): Boolean`
- `wouldExceedMaxDepth(folders, folderId, newParentId, maxDepth = 2): Boolean`

Depth = 1 for root folders (no parent), 2 for direct children. Spec §8: 2-level max.

- [ ] **Step 1: Write failing tests**

Create `FolderValidationTest.kt`:

```kotlin
package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderValidationTest {
    private fun folder(id: String, parent: String? = null) =
        Folder(FolderId(id), "f-$id", parent?.let(::FolderId), createdAt = 0L)

    @Test fun `cycle detected when newParent is self`() {
        val a = folder("a")
        assertTrue(wouldCreateCycle(listOf(a), FolderId("a"), FolderId("a")))
    }

    @Test fun `cycle detected when newParent is descendant`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        assertTrue(wouldCreateCycle(listOf(a, b), FolderId("a"), FolderId("b")))
    }

    @Test fun `no cycle for sibling move`() {
        val a = folder("a")
        val b = folder("b")
        val c = folder("c", parent = "a")
        assertFalse(wouldCreateCycle(listOf(a, b, c), FolderId("c"), FolderId("b")))
    }

    @Test fun `no cycle when moving to root`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        assertFalse(wouldCreateCycle(listOf(a, b), FolderId("b"), null))
    }

    @Test fun `depth violated when nesting a child under another child`() {
        val a = folder("a")                    // depth 1
        val b = folder("b", parent = "a")      // depth 2
        val c = folder("c")                    // depth 1
        // Moving c under b would make c depth 3.
        assertTrue(wouldExceedMaxDepth(listOf(a, b, c), FolderId("c"), FolderId("b")))
    }

    @Test fun `depth ok when nesting root under root`() {
        val a = folder("a")
        val b = folder("b")
        assertFalse(wouldExceedMaxDepth(listOf(a, b), FolderId("b"), FolderId("a")))
    }

    @Test fun `depth ok when moving to root regardless`() {
        val a = folder("a")
        val b = folder("b", parent = "a")
        assertFalse(wouldExceedMaxDepth(listOf(a, b), FolderId("b"), null))
    }

    @Test fun `subtree depth too deep — moving root that already has children to be a child`() {
        // a has child b. Moving a under c would make b depth 3.
        val a = folder("a")
        val b = folder("b", parent = "a")
        val c = folder("c")
        assertTrue(wouldExceedMaxDepth(listOf(a, b, c), FolderId("a"), FolderId("c")))
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.domain.usecase.FolderValidationTest"`
Expected: COMPILATION ERROR — `wouldCreateCycle` / `wouldExceedMaxDepth` unresolved.

- [ ] **Step 3: Implement FolderValidation.kt**

```kotlin
package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId

const val MAX_FOLDER_DEPTH = 2

/**
 * True iff setting `folder.parent = newParent` would create a cycle.
 * Walking up from newParent must NOT hit folderId.
 */
fun wouldCreateCycle(
    folders: List<Folder>,
    folderId: FolderId,
    newParentId: FolderId?,
): Boolean {
    if (newParentId == null) return false
    if (newParentId == folderId) return true
    val byId = folders.associateBy { it.id }
    var cursor: FolderId? = newParentId
    val seen = HashSet<FolderId>()
    while (cursor != null) {
        if (cursor == folderId) return true
        if (!seen.add(cursor)) return true // defensive: existing cycle in data
        cursor = byId[cursor]?.parentId
    }
    return false
}

/**
 * True iff setting `folder.parent = newParent` would push the subtree rooted at folder
 * past [maxDepth] levels (root = depth 1).
 */
fun wouldExceedMaxDepth(
    folders: List<Folder>,
    folderId: FolderId,
    newParentId: FolderId?,
    maxDepth: Int = MAX_FOLDER_DEPTH,
): Boolean {
    val byId = folders.associateBy { it.id }
    val childrenOf = folders.groupBy { it.parentId }

    val depthOfParent: Int = if (newParentId == null) 0 else {
        // Walk up from newParentId to compute its depth (root = 1).
        var d = 1
        var cursor: FolderId? = byId[newParentId]?.parentId
        while (cursor != null) {
            d += 1
            cursor = byId[cursor]?.parentId
        }
        d
    }

    // Compute max depth of subtree rooted at folderId (folderId itself counts as 1).
    fun subtreeDepth(rootId: FolderId): Int {
        val kids = childrenOf[rootId].orEmpty()
        if (kids.isEmpty()) return 1
        return 1 + kids.maxOf { subtreeDepth(it.id) }
    }

    val subtree = subtreeDepth(folderId)
    return depthOfParent + subtree > maxDepth
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.domain.usecase.FolderValidationTest"`
Expected: PASS (8 tests). Если что-то fails — пересмотреть depthOfParent semantics.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/domain/model/Folder.kt \
        app/src/main/java/app/tessera/domain/model/FolderTreeNode.kt \
        app/src/main/java/app/tessera/domain/usecase/FolderValidation.kt \
        app/src/test/java/app/tessera/domain/usecase/FolderValidationTest.kt
# Этот коммит сломает компиляцию consumer-кода! Мы зафиксируем сломанную
# компиляцию намеренно: следующая задача фиксит mappers с parentId. Атомарный
# коммит «domain + mappers» был бы слишком большим. Альтернатива (рекомендуется):
# подержать в индексе без commit до конца Task 4, then squash. Здесь — выбор автора.
git commit -m "feat(folders): add parentId + FolderTreeNode + depth/cycle validation"
```

**Note:** Если поддерживать атомарность важнее истории мелких коммитов — пропустить step 5 и закоммитить вместе с Task 4 одной командой.

---

## Chunk 2: Data layer — Entity, Migration, DAO

### Task 4: Add `parentId` column to `FolderEntity` + Migration3To4

**Files:**
- Modify: `app/src/main/java/app/tessera/data/local/entity/FolderEntity.kt`
- Create: `app/src/main/java/app/tessera/data/local/Migration3To4.kt`
- Modify: `app/src/main/java/app/tessera/data/local/NotesDatabase.kt`

- [ ] **Step 1: Modify FolderEntity**

```kotlin
package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index("name", unique = true),
        Index("parentId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true,
        ),
    ],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val syncedAt: Long? = null,
)
```

`deferred = true` нужно: при batch-insert children могут приехать до parents, deferred FK откладывает проверку до конца транзакции.

- [ ] **Step 2: Create Migration3To4.kt**

```kotlin
package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds [FolderEntity.parentId] column with FK to self (ON DELETE SET NULL) and an
 * index for parent lookups. Existing rows have parentId = NULL (root).
 *
 * Room FK declarations require recreating the table because `ALTER TABLE ... ADD CONSTRAINT`
 * is not supported by SQLite. We use the deferred-recreate dance:
 *   1. Create folders_new with the new schema (including FK + indexes)
 *   2. Copy data from folders (parentId set to NULL)
 *   3. DROP folders, RENAME folders_new TO folders, recreate indexes
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE folders_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                parentId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncedAt INTEGER,
                FOREIGN KEY(parentId) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO folders_new (id, name, parentId, createdAt, updatedAt, deletedAt, syncedAt)
            SELECT id, name, NULL, createdAt, updatedAt, deletedAt, syncedAt FROM folders
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE folders")
        db.execSQL("ALTER TABLE folders_new RENAME TO folders")
        db.execSQL("CREATE UNIQUE INDEX index_folders_name ON folders(name)")
        db.execSQL("CREATE INDEX index_folders_parentId ON folders(parentId)")
    }
}
```

- [ ] **Step 3: Update NotesDatabase version and register migration**

Modify `app/src/main/java/app/tessera/data/local/NotesDatabase.kt`:

Change `version = 3` → `version = 4`. (Migration registration живёт в DI; см. Step 4.)

- [ ] **Step 4: Register MIGRATION_3_4 in Hilt module**

Найди модуль, где создаётся `Room.databaseBuilder(...)` — обычно `app/src/main/java/app/tessera/core/di/DatabaseModule.kt` или подобный:

```bash
grep -rln "Room.databaseBuilder\|MIGRATION_1_2\|MIGRATION_2_3" app/src/main/java/app/tessera/core/di/
```

В найденном файле:
```kotlin
Room.databaseBuilder(ctx, NotesDatabase::class.java, "tessera.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
```

- [ ] **Step 5: Build check (expect cascading errors not fixed yet)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: Errors at FolderRepositoryImpl, mapper, and fakes referencing `FolderEntity(...)` без parentId; и `Folder(...)` без parentId. Это нормально — fix в Tasks 5–7.

- [ ] **Step 6: Do not commit yet** — wait for Task 5.

---

### Task 5: Folder ↔ entity mapper + DAO update

**Files:**
- Modify: `app/src/main/java/app/tessera/data/local/dao/FolderDao.kt`
- Locate or create mapper: probably inside `FolderRepositoryImpl.kt` private extensions. If a dedicated `FolderMapper.kt` doesn't exist, keep mapping inline.

- [ ] **Step 1: Add updateParent DAO query**

Append to `FolderDao.kt`:

```kotlin
@Query("UPDATE folders SET parentId = :parentId, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
suspend fun updateParent(id: String, parentId: String?, ts: Long)
```

- [ ] **Step 2: Update entity→domain and domain→entity mapping**

Open `app/src/main/java/app/tessera/data/repository/FolderRepositoryImpl.kt` and find existing mapping helpers (e.g. `FolderEntity.toDomain()` / `Folder.toEntity()`). Add `parentId` field both ways:

```kotlin
private fun FolderEntity.toDomain(): Folder = Folder(
    id = FolderId(id),
    name = name,
    parentId = parentId?.let(::FolderId),
    createdAt = createdAt,
)

private fun Folder.toEntity(updatedAt: Long, deletedAt: Long? = null): FolderEntity = FolderEntity(
    id = id.value,
    name = name,
    parentId = parentId?.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncedAt = null,
)
```

(Точные сигнатуры могут отличаться от существующих — править в соответствии с тем, что уже там.)

- [ ] **Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: Errors only в test fakes / RemoteFolder mappings — те fix в Tasks 6–7.

- [ ] **Step 4: Do not commit yet** — Task 6 next.

---

### Task 6: Migration3To4 — MigrationTestHelper

**Files:**
- Create: `app/src/androidTest/java/app/tessera/data/local/Migration3To4Test.kt`

Этот тест — androidTest (нужен SQLite runtime, Robolectric не воспроизводит Room migration helper надёжно).

- [ ] **Step 1: Verify androidTest setup has MigrationTestHelper**

```bash
grep -rln "MigrationTestHelper" app/src/androidTest/ app/build.gradle.kts
```

Если в `build.gradle.kts` нет `androidTestImplementation(libs.androidx.room.testing)` — добавить. Существующая позиция:

```kotlin
androidTestImplementation(libs.androidx.room.testing)  // already in 'libs.androidx.room.testing'
```

(Версия должна совпадать с room — она уже подключена для unit-тестов миграции notes, если такой есть.)

- [ ] **Step 2: Write Migration3To4Test**

```kotlin
package app.tessera.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java,
    )

    @Test fun migrate_3_to_4_preserves_rows_and_adds_null_parentId() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """INSERT INTO folders (id, name, createdAt, updatedAt, deletedAt, syncedAt)
                   VALUES ('f1', 'Work', 100, 100, NULL, NULL)""",
            )
            execSQL(
                """INSERT INTO folders (id, name, createdAt, updatedAt, deletedAt, syncedAt)
                   VALUES ('f2', 'Personal', 200, 200, NULL, NULL)""",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        db.query("SELECT id, name, parentId FROM folders ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("f1", c.getString(0))
            assertEquals("Work", c.getString(1))
            assertNull(if (c.isNull(2)) null else c.getString(2))
            c.moveToNext()
            assertEquals("f2", c.getString(0))
        }
        db.close()
    }

    @Test fun on_delete_set_null_after_migration() {
        // Build the migrated DB through Room (so FK constraints are enabled).
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(ctx, NotesDatabase::class.java, "tessera-fk-test.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        room.openHelper.writableDatabase.apply {
            execSQL("PRAGMA foreign_keys = ON")
            execSQL("INSERT INTO folders (id, name, parentId, createdAt, updatedAt) VALUES ('p', 'parent', NULL, 1, 1)")
            execSQL("INSERT INTO folders (id, name, parentId, createdAt, updatedAt) VALUES ('c', 'child', 'p', 1, 1)")
            execSQL("DELETE FROM folders WHERE id = 'p'")
            query("SELECT parentId FROM folders WHERE id = 'c'").use { c ->
                c.moveToFirst()
                assertNull(if (c.isNull(0)) null else c.getString(0))
            }
        }
        room.close()
        ctx.deleteDatabase("tessera-fk-test.db")
    }

    private companion object { const val TEST_DB = "migration-test" }
}
```

- [ ] **Step 3: Run androidTest (requires emulator)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.data.local.Migration3To4Test"`

Если нет устройства — задокументировать «migration test deferred to manual QA» и продолжить (есть unit-test-level гарантии через FolderValidation + integration через FolderRepositoryImplTest in Task 8).

- [ ] **Step 4: Do not commit yet** — Task 7 next.

---

### Task 7: RemoteFolder + Firestore mapping

**Files:**
- Modify: `app/src/main/java/app/tessera/data/remote/RemoteFolder.kt`
- Modify: `app/src/main/java/app/tessera/data/remote/FirestoreFoldersClient.kt`
- Modify: `app/src/main/java/app/tessera/data/remote/RemoteFoldersDataSource.kt`
- Modify: `app/src/main/java/app/tessera/data/repository/SyncRepositoryImpl.kt`

- [ ] **Step 1: Add parentId to RemoteFolder**

```kotlin
data class RemoteFolder(
    val id: String = "",
    val name: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
```

`parentId: String? = null` default — старые Firestore документы без поля десериализуются как root.

- [ ] **Step 2: Propagate parentId через uploads/downloads**

Найти все места, где конструируется `RemoteFolder(...)` или из remote→entity:

```bash
grep -rln "RemoteFolder" app/src/main/java/app/tessera/data
```

Везде: добавить `parentId = entity.parentId` / `parentId = remote.parentId`. Обычно две точки: `FirestoreFoldersClient` или `RemoteFoldersDataSource` (upload mapping) + `SyncRepositoryImpl` (download mapping).

- [ ] **Step 3: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Все Folder(...)/FolderEntity(...)/RemoteFolder(...) call-sites закрыты.)

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS включая FolderValidationTest. Старые тесты могут потребовать обновления — поправь конструкторы Folder(...) добавив `parentId = null`.

```bash
grep -rln "Folder(FolderId" app/src/test/ app/src/androidTest/ 2>/dev/null
```

Каждый Folder(...) в тестах: добавить `parentId = null,` в нужной позиции.

- [ ] **Step 5: Commit (Chunk 1+2 atomic)**

```bash
git add -A
git commit -m "feat(folders): parentId 2-level hierarchy — domain, entity, Migration3To4, RemoteFolder"
```

Большой коммит — но атомарность важнее: после него код снова компилируется + все тесты зелёные. Если предпочитаешь мелкие коммиты — split с `--patch`.

---

## Chunk 3: Repository methods + use cases

### Task 8: Repository — moveFolder, createSubfolder, folderTreeFlow

**Files:**
- Modify: `app/src/main/java/app/tessera/domain/repository/FolderRepository.kt`
- Modify: `app/src/main/java/app/tessera/data/repository/FolderRepositoryImpl.kt`
- Modify: `app/src/test/java/app/tessera/data/repository/FolderRepositoryImplTest.kt` (если есть; иначе создать минимальный)

- [ ] **Step 1: Extend interface**

```kotlin
package app.tessera.domain.repository

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeFolderTree(): Flow<List<FolderTreeNode>>
    fun observeFolderForNote(noteId: NoteId): Flow<FolderId?>
    fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>>
    suspend fun createFolder(name: String): Folder
    suspend fun createSubfolder(parentId: FolderId, name: String): Folder
    suspend fun renameFolder(id: FolderId, name: String)
    suspend fun moveFolder(id: FolderId, newParentId: FolderId?)
    suspend fun deleteFolder(id: FolderId)
    suspend fun assignNote(noteId: NoteId, folderId: FolderId?)
}
```

- [ ] **Step 2: Implement in FolderRepositoryImpl**

В `FolderRepositoryImpl.kt`:

```kotlin
override fun observeFolderTree(): Flow<List<FolderTreeNode>> =
    observeFolders().map { flat ->
        val roots = flat.filter { it.parentId == null }
        val byParent = flat.filter { it.parentId != null }.groupBy { it.parentId!! }
        roots.map { root -> FolderTreeNode(root, byParent[root.id].orEmpty()) }
    }

override suspend fun createSubfolder(parentId: FolderId, name: String): Folder {
    val now = clock.now()
    val newFolder = Folder(
        id = FolderId(idGenerator.next()),
        name = name,
        parentId = parentId,
        createdAt = now,
    )
    dao.upsert(newFolder.toEntity(updatedAt = now))
    return newFolder
}

override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) {
    dao.updateParent(id.value, newParentId?.value, clock.now())
}
```

Конкретные сигнатуры — подстроить под уже существующий стиль файла (clock, idGenerator).

- [ ] **Step 3: Test — folderTreeFlow shapes**

В `FolderRepositoryImplTest.kt` добавить:

```kotlin
@Test fun `folderTree groups children under their parents`() = runTest {
    repo.createFolder("Work")
    repo.createFolder("Personal")
    val work = repo.observeFolders().first().first { it.name == "Work" }
    val q4 = repo.createSubfolder(work.id, "Q4 plans")
    val tree = repo.observeFolderTree().first()
    val workNode = tree.first { it.folder.id == work.id }
    assertEquals(listOf(q4.id), workNode.children.map { it.id })
    val personalNode = tree.first { it.folder.name == "Personal" }
    assertTrue(personalNode.children.isEmpty())
}
```

- [ ] **Step 4: Build + run tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/domain/repository/FolderRepository.kt \
        app/src/main/java/app/tessera/data/repository/FolderRepositoryImpl.kt \
        app/src/test/java/app/tessera/data/repository/FolderRepositoryImplTest.kt
git commit -m "feat(folders): repository — moveFolder, createSubfolder, observeFolderTree"
```

---

### Task 9: MoveFolderUseCase + CreateSubfolderUseCase + ObserveFolderTreeUseCase

**Files:**
- Modify: `app/src/main/java/app/tessera/domain/usecase/FolderUseCases.kt`
- Create: `app/src/test/java/app/tessera/domain/usecase/MoveFolderUseCaseTest.kt`
- Create: `app/src/test/java/app/tessera/domain/usecase/CreateSubfolderUseCaseTest.kt`

`MoveFolderUseCase` возвращает `Result<Unit>` для UI Snackbar. `CreateSubfolderUseCase` — то же.

- [ ] **Step 1: Failing tests — MoveFolderUseCaseTest**

```kotlin
package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.repository.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveFolderUseCaseTest {
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private val moves = mutableListOf<Pair<FolderId, FolderId?>>()
    private val fake = object : FakeFolderRepo() {
        override fun observeFolders() = foldersFlow
        override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) {
            moves += id to newParentId
        }
    }
    private val useCase = MoveFolderUseCase(fake)

    private fun folder(id: String, parent: String? = null) =
        Folder(FolderId(id), "f-$id", parent?.let(::FolderId), 0L)

    @Test fun `rejects self-parent`() = runTest {
        foldersFlow.value = listOf(folder("a"))
        val result = useCase(FolderId("a"), FolderId("a"))
        assertTrue(result.isFailure)
        assertTrue(moves.isEmpty())
    }

    @Test fun `rejects cycle through descendant`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b", "a"))
        val result = useCase(FolderId("a"), FolderId("b"))
        assertTrue(result.isFailure)
    }

    @Test fun `rejects depth violation`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b", "a"), folder("c"))
        val result = useCase(FolderId("c"), FolderId("b")) // depth would be 3
        assertTrue(result.isFailure)
    }

    @Test fun `accepts valid root-to-root move`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b"))
        val result = useCase(FolderId("b"), FolderId("a"))
        assertTrue(result.isSuccess)
        assertTrue(moves.contains(FolderId("b") to FolderId("a")))
    }

    @Test fun `accepts move to root`() = runTest {
        foldersFlow.value = listOf(folder("a"), folder("b", "a"))
        val result = useCase(FolderId("b"), null)
        assertTrue(result.isSuccess)
        assertTrue(moves.contains(FolderId("b") to null))
    }
}
```

`FakeFolderRepo` — abstract no-op implementation тех методов FolderRepository, которые не нужны для теста. Добавь его в `app/src/test/java/app/tessera/domain/usecase/FakeFolderRepo.kt`:

```kotlin
package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

abstract class FakeFolderRepo : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = flowOf(emptyList())
    override fun observeFolderTree(): Flow<List<FolderTreeNode>> = flowOf(emptyList())
    override fun observeFolderForNote(noteId: NoteId): Flow<FolderId?> = flowOf(null)
    override fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>> = flowOf(emptyList())
    override suspend fun createFolder(name: String): Folder = error("not stubbed")
    override suspend fun createSubfolder(parentId: FolderId, name: String): Folder = error("not stubbed")
    override suspend fun renameFolder(id: FolderId, name: String) = Unit
    override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) = Unit
    override suspend fun deleteFolder(id: FolderId) = Unit
    override suspend fun assignNote(noteId: NoteId, folderId: FolderId?) = Unit
}
```

- [ ] **Step 2: Run tests — expect FAIL (unresolved)**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.domain.usecase.MoveFolderUseCaseTest"`
Expected: COMPILATION ERROR — `MoveFolderUseCase` unresolved.

- [ ] **Step 3: Implement use cases (append to FolderUseCases.kt)**

```kotlin
class ObserveFolderTreeUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke() = repo.observeFolderTree()
}

class MoveFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(folderId: FolderId, newParentId: FolderId?): Result<Unit> {
        val folders = repo.observeFolders().first()
        if (wouldCreateCycle(folders, folderId, newParentId)) {
            return Result.failure(FolderValidationException("cycle"))
        }
        if (wouldExceedMaxDepth(folders, folderId, newParentId)) {
            return Result.failure(FolderValidationException("depth"))
        }
        return runCatching { repo.moveFolder(folderId, newParentId) }
    }
}

class CreateSubfolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(parentId: FolderId, name: String): Result<Folder> {
        val folders = repo.observeFolders().first()
        // Subfolder of parent: this adds depth (1 if parent root → child depth 2).
        // Reject if parent is already a child (depth 2 → would create depth 3).
        val parent = folders.firstOrNull { it.id == parentId }
            ?: return Result.failure(FolderValidationException("parent not found"))
        if (parent.parentId != null) {
            return Result.failure(FolderValidationException("depth"))
        }
        return runCatching { repo.createSubfolder(parentId, name) }
    }
}

class FolderValidationException(message: String) : RuntimeException(message)
```

Не забудь добавить `import kotlinx.coroutines.flow.first` в начале файла.

- [ ] **Step 4: Run tests — PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.domain.usecase.MoveFolderUseCaseTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Write CreateSubfolderUseCaseTest**

```kotlin
package app.tessera.domain.usecase

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSubfolderUseCaseTest {
    private val foldersFlow = MutableStateFlow<List<Folder>>(emptyList())
    private var created: Folder? = null
    private val fake = object : FakeFolderRepo() {
        override fun observeFolders() = foldersFlow
        override suspend fun createSubfolder(parentId: FolderId, name: String): Folder {
            val f = Folder(FolderId("new"), name, parentId, 0L)
            created = f
            return f
        }
    }
    private val useCase = CreateSubfolderUseCase(fake)

    @Test fun `creates under root parent`() = runTest {
        val root = Folder(FolderId("root"), "Root", null, 0L)
        foldersFlow.value = listOf(root)
        val result = useCase(root.id, "Sub")
        assertTrue(result.isSuccess)
        assertEquals("Sub", created?.name)
    }

    @Test fun `rejects when parent is a child`() = runTest {
        val root = Folder(FolderId("root"), "Root", null, 0L)
        val child = Folder(FolderId("child"), "Child", root.id, 0L)
        foldersFlow.value = listOf(root, child)
        val result = useCase(child.id, "Deep")
        assertTrue(result.isFailure)
    }

    @Test fun `rejects when parent doesn't exist`() = runTest {
        foldersFlow.value = emptyList()
        val result = useCase(FolderId("ghost"), "X")
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 6: Run tests — PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.domain.usecase.CreateSubfolderUseCaseTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/domain/usecase/FolderUseCases.kt \
        app/src/test/java/app/tessera/domain/usecase/FakeFolderRepo.kt \
        app/src/test/java/app/tessera/domain/usecase/MoveFolderUseCaseTest.kt \
        app/src/test/java/app/tessera/domain/usecase/CreateSubfolderUseCaseTest.kt
git commit -m "feat(folders): MoveFolderUseCase + CreateSubfolderUseCase with validation"
```

---

## Chunk 4: Final verification

### Task 10: Full build + tests + smoke

- [ ] **Step 1: Clean build**

Run: `./gradlew clean :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full unit test pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS. Включая новые:
- `FolderValidationTest` (8 tests)
- `MoveFolderUseCaseTest` (5 tests)
- `CreateSubfolderUseCaseTest` (3 tests)
- Расширенный `FolderRepositoryImplTest`

If failed: investigate. Если flaky `UnsatisfiedLinkError` на roborazzi — рерун в изоляции (`--tests "app.tessera.ui.notes.components.NoteCardRenderTest"`); это known macOS native-lib issue, не блок.

- [ ] **Step 3: AndroidTest migration (если доступен emulator)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.data.local.Migration3To4Test"`
Expected: PASS. Если нет устройства — задокументировать в final commit message.

- [ ] **Step 4: Smoke launch (на устройстве)**

Установить debug APK поверх предыдущей версии с заполненными folders → upgrade → открыть drawer → все папки видны на root level (parentId = NULL). Если crash — Migration3To4 не отработала, разбирать.

- [ ] **Step 5: Final commit if anything left**

```bash
git status
# если есть мелкие правки — commit с сообщением "chore(folders): plan-03 verification fixups"
```

---

## What this plan does NOT do

- НЕ создаёт UI для folder tree (drawer, picker, indented rows) — Plan 04 (`NotesListScreen` redesign).
- НЕ добавляет drag-to-reorder / drag-to-move (spec §14: future).
- НЕ мигрирует существующие Firestore documents (back-compat через default `parentId = null`).
- НЕ убирает старые `FoldersDrawer`, `FolderPickerPopup`, `FolderNamePopup` — их рефакторинг приходит с экраном-консьюмером в Plan 04.

## Acceptance criteria

- [ ] `Folder` domain model имеет `parentId: FolderId?`
- [ ] `FolderEntity` имеет FK `parentId → folders(id) ON DELETE SET NULL` + index
- [ ] `MIGRATION_3_4` зарегистрирована в DI; `NotesDatabase.version = 4`
- [ ] `RemoteFolder` имеет `parentId: String? = null` (back-compat)
- [ ] `FolderRepository` имеет `observeFolderTree`, `moveFolder`, `createSubfolder`
- [ ] `MoveFolderUseCase` валидирует depth + cycles, возвращает `Result.failure(FolderValidationException)`
- [ ] `CreateSubfolderUseCase` валидирует depth, возвращает `Result.failure` если родитель — child
- [ ] `FolderValidationTest`, `MoveFolderUseCaseTest`, `CreateSubfolderUseCaseTest` — passing
- [ ] `Migration3To4Test` (androidTest) passes на устройстве
- [ ] `./gradlew clean :app:assembleDebug :app:testDebugUnitTest` green

## Next plan

`docs/superpowers/plans/YYYY-MM-DD-material-you-redesign-04-notes-list.md` — `NotesListScreen` redesign: использовать `TesseraDrawerShell` (Plan 02) + `observeFolderTree` (Plan 03) + `TesseraNoteCard` + `TesseraChipRow` + `TesseraBottomActionBar`. Удалит старые `FoldersDrawer.kt`, `BottomSearchBar.kt`, `FloatingCapsule.kt`, `GlassFab.kt`.
