# Folders, Context Menu & Editor Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land four user-facing improvements: (1) UI polish on notes list and tile cards, (2) Apple Notes–style auto-title (first line of body becomes the title), (3) keyboard-focuses-when-tapping-empty-area in editor, (4) folders as a first-class organizing structure with side drawer, modal create/edit, and a long-press context menu on note cards.

**Architecture:**
- Phase 1 (polish): pure UI changes — extend `GlassFab` with optional `HazeState`, retune `NoteCard` paddings, fix Aztec focus on empty-area tap.
- Phase 2 (auto-title): drop the dedicated title field; derive title from `content`'s first non-empty line, persist to existing `title` column on save so list/card rendering keeps working without schema churn.
- Phase 3 (folders): new `folders` + `note_folders` Room tables migrated from v1→v2, `FolderDao`/`FolderRepository`/use-cases mirroring tag plumbing, `Folder` domain model, list-screen drawer with blur, create/edit modal reusing the `TagPickerPopup` chrome, long-press context menu on `SwipeableNoteCard` with pin/delete/move actions, compact folder-picker popup. Firestore sync stays scoped to v1 fields — folders are local-only this phase (sync extension is out of scope).

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Haze (blur), Aztec.

---

## Chunk 1: Phase 1 — UI Polish + Editor Focus Fix

### Task 1.1: GlassFab accepts optional HazeState for blur

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/components/GlassFab.kt`

- [ ] **Step 1: Add `hazeState` parameter and forward it to FloatingCapsule**

```kotlin
// GlassFab.kt — function signature
fun GlassFab(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentDescription: String? = null,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // ...existing semanticsModifier setup unchanged...
    FloatingCapsule(
        modifier = ...,
        shape = CircleShape,
        blurRadius = 10.dp,
        shadowElevation = 3.dp,
        showGlassDecor = false,
        contentPadding = PaddingValues(0.dp),
        hazeState = hazeState,  // NEW — forwarded so the capsule applies blur when provided
    ) { ... }
}
```

- [ ] **Step 2: Pass `hazeState` from `NotesListScreen` to the tag-toggle GlassFab only**

In `NotesListScreen.kt`, locate the tag-toggle `GlassFab(...)` (the one wired to `tagBarExpanded = !tagBarExpanded`, ~line 264) and add `hazeState = hazeState,`. Leave the create-note FAB without blur (user only asked for the tag button).

- [ ] **Step 3: Build & verify visually**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/GlassFab.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt
git commit -m "feat(notes): blur tag-toggle FAB via GlassFab hazeState"
```

### Task 1.2: Tighten tile note-card paddings + tag-chip padding

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt`

- [ ] **Step 1: Reduce tile vertical padding and add tag-chip inner padding**

```kotlin
// Tile: outer modifier — reduce vertical 14.dp → 10.dp; horizontal stays 14.dp
TileNoteCard(note = note, modifier = baseModifier.padding(horizontal = 14.dp, vertical = 10.dp))

// Inside TileNoteCard the Column uses spacedBy(8.dp) — tighten to 6.dp:
verticalArrangement = Arrangement.spacedBy(6.dp),

// TagChip: add inner padding so chips don't look cramped
@Composable
private fun TagChip(name: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = "#$name",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),  // NEW
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
```

- [ ] **Step 2: Verify previews compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt
git commit -m "feat(notes): tighten tile card spacing and pad tag chips"
```

### Task 1.3: Editor focuses Aztec when tapping the empty area below body

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt`

Root cause: `RichNoteBody` is `wrap_content` tall, and the trailing `Spacer(160.dp)` below it captures taps without forwarding focus to the AztecText AndroidView. Fix by exposing a `requestFocus` callback from `RichNoteBody` (via `RichBodyActions`) and wrapping the trailing spacer in a `clickable { actions?.focusBody() }` that places the cursor at end-of-text + shows IME.

- [ ] **Step 1: Add `focusBody()` to `RichBodyActions`**

```kotlin
// RichNoteBody.kt — interface
interface RichBodyActions {
    // ...existing...
    /** Programmatically focus the editor and surface the IME, placing the cursor at end of text. */
    fun focusBody()
}
```

- [ ] **Step 2: Implement `focusBody()` against the AztecText instance**

```kotlin
// Inside the onActionsReady block in RichNoteBody.kt:
override fun focusBody() {
    editor.requestFocus()
    editor.setSelection(editor.length())
    val imm = editor.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
        as android.view.inputmethod.InputMethodManager
    imm.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
}
```

- [ ] **Step 3: Wire a clickable spacer below `RichNoteBody` in `NoteEditorScreen`**

Replace the trailing `Spacer(modifier = Modifier.height(160.dp))` after `RichNoteBody` with:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { actions?.focusBody() },
        ),
)
```

Add imports: `androidx.compose.foundation.clickable`, `androidx.compose.foundation.interaction.MutableInteractionSource`.

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual test on device/emulator** — tap the empty area below note body; IME opens, cursor positions at end of content. (Document as manual since Aztec isn't covered by instrumented tests.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt
git commit -m "feat(editor): tap empty area below body to focus and open keyboard"
```

---

## Chunk 2: Phase 2 — Apple Notes–style Auto-Title

### Background research

Apple Notes derives the note's display title from the **first non-empty line** of the content. There is no separate title field in the UI — the first line is styled as Title and acts as the visible name in lists and search. If the first line is blank/whitespace-only, it falls back to the next non-empty line; if the whole note is empty, the title displays as "New Note" (we already render `R.string.note_untitled`).

Behavior we'll mirror:
- Single editor surface — no separate "Title" input.
- The first non-empty line of `content` becomes `title` whenever the note is persisted.
- `title` is still stored in the `notes` table so list rendering, FTS, and remote sync continue to work without churn.
- Markdown formatting on the first line is stripped for the display title (e.g. `# Heading` → "Heading"), reusing the existing `markdownToPreviewPlainText` extractor.

### Task 2.1: Add `deriveTitleFromContent` helper

**Files:**
- Create: `app/src/main/java/app/tessera/core/text/AutoTitle.kt`
- Test: `app/src/test/java/app/tessera/core/text/AutoTitleTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tessera.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTitleTest {
    @Test fun `picks first non-blank line`() {
        assertEquals("Hello", deriveTitleFromContent("\n  \nHello\nworld"))
    }

    @Test fun `strips heading markers`() {
        assertEquals("Plans", deriveTitleFromContent("# Plans\n- a"))
        assertEquals("Plans", deriveTitleFromContent("## Plans"))
    }

    @Test fun `strips list bullets and checklist boxes`() {
        assertEquals("Buy milk", deriveTitleFromContent("- Buy milk\n- Eggs"))
        assertEquals("Buy milk", deriveTitleFromContent("- [ ] Buy milk"))
        assertEquals("Buy milk", deriveTitleFromContent("- [x] Buy milk"))
    }

    @Test fun `returns empty when content is blank`() {
        assertEquals("", deriveTitleFromContent(""))
        assertEquals("", deriveTitleFromContent("   \n\n"))
    }

    @Test fun `caps title length`() {
        val long = "a".repeat(200)
        assertEquals(120, deriveTitleFromContent(long).length)
    }
}
```

- [ ] **Step 2: Run test — expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.AutoTitleTest"`
Expected: FAIL — "Unresolved reference: deriveTitleFromContent".

- [ ] **Step 3: Implement the helper**

```kotlin
package app.tessera.core.text

private const val MAX_TITLE_CHARS = 120

private val HeadingPrefix = Regex("^#{1,6}\\s+")
private val ChecklistPrefix = Regex("^[-*]\\s+\\[[ xX]?]\\s+")
private val ListPrefix = Regex("^[-*+]\\s+")

/**
 * Returns the first non-blank line of [content], stripped of markdown leading markers
 * (heading hashes, list bullets, checklist boxes) and clipped to [MAX_TITLE_CHARS].
 * Mirrors Apple Notes' first-line-as-title convention.
 */
fun deriveTitleFromContent(content: String): String {
    val firstLine = content.lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    val stripped = firstLine
        .replaceFirst(HeadingPrefix, "")
        .replaceFirst(ChecklistPrefix, "")
        .replaceFirst(ListPrefix, "")
        .trim()
    return if (stripped.length > MAX_TITLE_CHARS) stripped.take(MAX_TITLE_CHARS) else stripped
}
```

- [ ] **Step 4: Re-run — expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.AutoTitleTest"`
Expected: BUILD SUCCESSFUL, 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/text/AutoTitle.kt \
        app/src/test/java/app/tessera/core/text/AutoTitleTest.kt
git commit -m "feat(notes): add deriveTitleFromContent helper"
```

### Task 2.2: Apply derived title in repository updates

**Files:**
- Modify: `app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt`

- [ ] **Step 1: In `updateNote`, override `title` with the derived value before persist**

Locate the `persisted = note.copy(...)` block (line ~80). Change to:

```kotlin
val derivedTitle = deriveTitleFromContent(note.content)
val persisted =
    note.copy(
        title = derivedTitle,
        tags = noteMapper.normalizeTags(note.tags),
        updatedAt = clock.nowMs(),
        syncedAt = null,
    )
```

Add the import: `import app.tessera.core.text.deriveTitleFromContent`.

Also update the `isBlankDraft` check: a note is now blank iff `content` is blank (title is derived). Adjust `Note.isBlankDraft()`:

```kotlin
// Note.kt
fun isBlankDraft(): Boolean = content.isBlank()
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run existing repository tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*Repository*"`
Expected: All pass. Fix any test that asserted on the previously-untouched title behavior by updating its expectation to the derived value.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt \
        app/src/main/java/app/tessera/domain/model/Note.kt
git commit -m "feat(notes): derive note title from first line of content on save"
```

### Task 2.3: Remove the separate title field from the editor UI

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorState.kt` (if title is held in state)
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt` (drop `onTitleChange`)

- [ ] **Step 1: Drop the `BasicTextField` for title and the `onTitleChange` prop**

Replace the title `BasicTextField` block (NoteEditorScreen.kt:128–154) with just a top spacer (e.g. `Spacer(modifier = Modifier.height(8.dp))`) — the first line of the Aztec body will visually act as the title via its inline H1/larger styling, or simply via the user's own choice of formatting. Remove `onTitleChange` from the composable signature and propagate the deletion through the call site (`NoteEditorRoute`/whatever owns the ViewModel).

- [ ] **Step 2: Remove `title` from `NoteEditorState`** (if present as separate state). Reads to `state.title` must be deleted; the editor no longer renders or owns a title field.

- [ ] **Step 3: Update ViewModel** — remove any `onTitleChange` handler and any path that persists title separately. When the body changes, the existing `onContentChange` path saves the note; the repository now derives title.

- [ ] **Step 4: Update preview composables in `NoteEditorScreen.kt`** to not pass `title` and `onTitleChange`.

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run editor tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*Editor*"`
Expected: All pass. Update any test that referenced `onTitleChange` or `state.title` to reflect the new flow.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/
git commit -m "feat(editor): drop separate title field; title now derived from body"
```

---

## Chunk 3: Phase 3a — Folders Data Layer

### Task 3.1: `folders` and `note_folders` Room entities + migration v1→v2

**Files:**
- Create: `app/src/main/java/app/tessera/data/local/entity/FolderEntity.kt`
- Create: `app/src/main/java/app/tessera/data/local/entity/NoteFolderCrossRef.kt`
- Modify: `app/src/main/java/app/tessera/data/local/NotesDatabase.kt`
- Create: `app/src/main/java/app/tessera/data/local/Migration1To2.kt`

- [ ] **Step 1: Define `FolderEntity`**

```kotlin
package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index("name", unique = true)],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)
```

- [ ] **Step 2: Define `NoteFolderCrossRef`** (notes belong to at most one folder; nullable folderId is simpler but a cross-ref table mirrors the tag schema — pick the cross-ref form for future-proofing multi-folder support, and rely on UNIQUE INDEX on noteId)

```kotlin
package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "note_folders",
    primaryKeys = ["noteId", "folderId"],
    indices = [
        Index("noteId", unique = true),  // one folder per note
        Index("folderId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NoteFolderCrossRef(
    val noteId: String,
    val folderId: String,
)
```

- [ ] **Step 3: Bump DB version to 2 and register entities + migration**

```kotlin
// NotesDatabase.kt
@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class,
        FolderEntity::class,         // NEW
        NoteFolderCrossRef::class,   // NEW
    ],
    version = 2,
    exportSchema = true,
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao  // NEW
}
```

- [ ] **Step 4: Write the v1→v2 migration**

```kotlin
package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folders (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_folders_name ON folders(name)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS note_folders (
                noteId TEXT NOT NULL,
                folderId TEXT NOT NULL,
                PRIMARY KEY(noteId, folderId),
                FOREIGN KEY(folderId) REFERENCES folders(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_folders_noteId ON note_folders(noteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folders_folderId ON note_folders(folderId)")
    }
}
```

- [ ] **Step 5: Register the migration in the Hilt module**

Find the `@Provides fun provideDatabase(...)` (search `Room.databaseBuilder`); add `.addMigrations(MIGRATION_1_2)`.

- [ ] **Step 6: Schema export** — Room will require the new schema JSON when version bumps. Build once: `./gradlew :app:compileDebugKotlin`. If KSP errors complain about missing schema, run `./gradlew :app:assembleDebug` once locally to regenerate.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/data/local/
git commit -m "feat(folders): add folders + note_folders tables with v1->v2 migration"
```

### Task 3.2: `FolderDao`

**Files:**
- Create: `app/src/main/java/app/tessera/data/local/dao/FolderDao.kt`

- [ ] **Step 1: Write the DAO**

```kotlin
package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignNote(ref: NoteFolderCrossRef)

    @Query("DELETE FROM note_folders WHERE noteId = :noteId")
    suspend fun clearAssignment(noteId: String)

    @Query("SELECT folderId FROM note_folders WHERE noteId = :noteId LIMIT 1")
    fun observeFolderForNote(noteId: String): Flow<String?>

    @Query(
        """
        SELECT notes.* FROM notes
        JOIN note_folders ON notes.id = note_folders.noteId
        WHERE note_folders.folderId = :folderId AND notes.deletedAt IS NULL
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
        """,
    )
    fun observeNotesInFolder(folderId: String): Flow<List<app.tessera.data.local.entity.NoteEntity>>
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/data/local/dao/FolderDao.kt
git commit -m "feat(folders): add FolderDao"
```

### Task 3.3: Domain model + repository

**Files:**
- Create: `app/src/main/java/app/tessera/domain/model/Folder.kt`
- Create: `app/src/main/java/app/tessera/domain/repository/FolderRepository.kt`
- Create: `app/src/main/java/app/tessera/data/repository/FolderRepositoryImpl.kt`
- Modify: `app/src/main/java/app/tessera/data/repository/<Hilt module bind>` (wherever repositories are bound)

- [ ] **Step 1: Domain types**

```kotlin
// Folder.kt
package app.tessera.domain.model

data class FolderId(val value: String)

data class Folder(
    val id: FolderId,
    val name: String,
    val createdAt: Long,
)
```

- [ ] **Step 2: Repository interface**

```kotlin
// FolderRepository.kt
package app.tessera.domain.repository

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.NoteId
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>
    fun observeFolderForNote(noteId: NoteId): Flow<FolderId?>
    suspend fun createFolder(name: String): Folder
    suspend fun renameFolder(id: FolderId, name: String)
    suspend fun deleteFolder(id: FolderId)
    suspend fun assignNote(noteId: NoteId, folderId: FolderId?)
}
```

- [ ] **Step 3: Repository implementation**

```kotlin
// FolderRepositoryImpl.kt
package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.FolderDao
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.FolderRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao,
    private val clock: Clock,
) : FolderRepository {

    override fun observeFolders(): Flow<List<Folder>> =
        folderDao.observeAll().map { entities -> entities.map(::toDomain) }

    override fun observeFolderForNote(noteId: NoteId): Flow<FolderId?> =
        folderDao.observeFolderForNote(noteId.value).map { it?.let(::FolderId) }

    override suspend fun createFolder(name: String): Folder {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "folder name must not be blank" }
        val folder = FolderEntity(id = UUID.randomUUID().toString(), name = trimmed, createdAt = clock.nowMs())
        folderDao.upsert(folder)
        return toDomain(folder)
    }

    override suspend fun renameFolder(id: FolderId, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "folder name must not be blank" }
        folderDao.rename(id.value, trimmed)
    }

    override suspend fun deleteFolder(id: FolderId) = folderDao.delete(id.value)

    override suspend fun assignNote(noteId: NoteId, folderId: FolderId?) {
        folderDao.clearAssignment(noteId.value)
        if (folderId != null) {
            folderDao.assignNote(NoteFolderCrossRef(noteId = noteId.value, folderId = folderId.value))
        }
    }

    private fun toDomain(e: FolderEntity) = Folder(FolderId(e.id), e.name, e.createdAt)
}
```

- [ ] **Step 4: Bind in Hilt module**

Locate the `@Module` that binds `NotesRepository`/`TagDao` (search for `@Binds.*NotesRepository`). Add the same `@Binds` for `FolderRepository → FolderRepositoryImpl`, and add a `@Provides` for `FolderDao` from `NotesDatabase`.

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/domain/model/Folder.kt \
        app/src/main/java/app/tessera/domain/repository/FolderRepository.kt \
        app/src/main/java/app/tessera/data/repository/FolderRepositoryImpl.kt \
        app/src/main/java/app/tessera/<wherever the module lives>.kt
git commit -m "feat(folders): add Folder domain + FolderRepository"
```

### Task 3.4: Use-cases

**Files:**
- Create: `app/src/main/java/app/tessera/domain/usecase/ObserveFoldersUseCase.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/CreateFolderUseCase.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/RenameFolderUseCase.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/DeleteFolderUseCase.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/MoveNoteToFolderUseCase.kt`

- [ ] **Step 1: Add thin use-case wrappers** mirroring the tag/note use-cases.

```kotlin
class ObserveFoldersUseCase @Inject constructor(private val repo: FolderRepository) {
    operator fun invoke() = repo.observeFolders()
}
class CreateFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(name: String) = repo.createFolder(name)
}
class RenameFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(id: FolderId, name: String) = repo.renameFolder(id, name)
}
class DeleteFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(id: FolderId) = repo.deleteFolder(id)
}
class MoveNoteToFolderUseCase @Inject constructor(private val repo: FolderRepository) {
    suspend operator fun invoke(noteId: NoteId, folderId: FolderId?) = repo.assignNote(noteId, folderId)
}
```

- [ ] **Step 2: Build & commit**

```bash
./gradlew :app:compileDebugKotlin
git add app/src/main/java/app/tessera/domain/usecase/
git commit -m "feat(folders): add folder use-cases"
```

---

## Chunk 4: Phase 3b — Folders UI

### Task 4.1: Side drawer with folder list + blur

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/FoldersDrawer.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListState.kt`

- [ ] **Step 1: Extend `NotesListState`** to expose `folders: List<Folder>`, `selectedFolderId: FolderId?`. ViewModel: inject `ObserveFoldersUseCase`, combine into state. Filtering: when `selectedFolderId != null`, filter the observed notes through `FolderDao.observeNotesInFolder(...)` instead of `observeActive()` (introduce a thin filtered-observe path in the repo or do the filter in VM with a join flow).

- [ ] **Step 2: Build `FoldersDrawer`** — a `ModalDrawerSheet` (Material3) with `hazeEffect` background. Items: "All Notes" + each folder. Each item has tap-to-select and long-press for rename/delete. A "+" row at the bottom opens the create-folder modal.

```kotlin
@Composable
fun FoldersDrawer(
    folders: List<Folder>,
    selectedFolderId: FolderId?,
    hazeState: HazeState,
    onSelectAll: () -> Unit,
    onSelectFolder: (FolderId) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (FolderId) -> Unit,
    onDeleteFolder: (FolderId) -> Unit,
) { /* implementation with hazeEffect + LazyColumn */ }
```

- [ ] **Step 3: Wire `ModalNavigationDrawer` in `NotesListScreen`** — the drawer wraps the existing content, and a new menu button in the collapsing header opens it. Add a `hamburger` icon at the leading edge of `CollapsingHeader`.

- [ ] **Step 4: Build + screenshot test** — preview the drawer in light/dark.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/
git commit -m "feat(folders): side drawer listing folders with blur"
```

### Task 4.2: Create / Rename folder modal (reuse `TagPickerPopup` chrome)

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/FolderNamePopup.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`

- [ ] **Step 1: Adapt the `TagPickerPopup` glass styling** into a focused single-field popup with title "New folder" / "Rename folder", a text input, and "Cancel" / "Save" buttons.

- [ ] **Step 2: Wire to `CreateFolderUseCase` / `RenameFolderUseCase`** via the VM.

- [ ] **Step 3: Build + commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/FolderNamePopup.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt
git commit -m "feat(folders): create/rename folder popup"
```

### Task 4.3: Long-press context menu on note cards (pin / delete / move)

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt`
- Create: `app/src/main/java/app/tessera/ui/notes/components/NoteContextMenu.kt`
- Create: `app/src/main/java/app/tessera/ui/notes/components/FolderPickerPopup.kt`

- [ ] **Step 1: Wrap the note card's clickable area with a `combinedClickable(onLongClick = ...)`** that flips `var contextMenuVisible by remember { mutableStateOf(false) }`.

- [ ] **Step 2: `NoteContextMenu`** — a Popup styled like the markdown format menu (rounded surface + haze + shadow padding wrapper). Items: Pin/Unpin, Move to folder…, Delete.

- [ ] **Step 3: `FolderPickerPopup`** — compact popup opened from "Move to folder". Lists folders + "None" option; on selection invokes `MoveNoteToFolderUseCase` then dismisses both popups. Uses the same glass chrome as the format dropdown.

- [ ] **Step 4: Wire VM handlers** — `onTogglePin(noteId)`, `onDelete(note)`, `onMove(noteId, folderId?)`.

- [ ] **Step 5: Build + commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/
git commit -m "feat(notes): long-press context menu with pin/delete/move"
```

### Task 4.4: Wire folder filter into list

**Files:**
- Modify: `app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt`

- [ ] **Step 1: Add `observeNotesInFolder(folderId: FolderId)`** to `NotesRepository` + impl that delegates to `FolderDao.observeNotesInFolder` + maps to domain.

- [ ] **Step 2: In `NotesListViewModel`,** swap the observed source based on `selectedFolderId`:

```kotlin
val notes = selectedFolderIdFlow.flatMapLatest { folderId ->
    if (folderId == null) notesRepository.observeNotes()
    else notesRepository.observeNotesInFolder(folderId)
}
```

- [ ] **Step 3: Build + manual test** — selecting a folder filters the list; "All Notes" restores the full list.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt \
        app/src/main/java/app/tessera/domain/repository/NotesRepository.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt
git commit -m "feat(folders): filter notes list by selected folder"
```

---

## Verification Checklist

After all chunks land, run:

- [ ] `./gradlew :app:compileDebugKotlin` — clean compile
- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests pass
- [ ] Manual: install on device/emulator, walk through:
  - Tap the tag-toggle FAB — verifies blur
  - Open editor, tap empty area below body — keyboard appears
  - Type "Buy milk\n\n- eggs" — title in list reads "Buy milk"
  - Open drawer — folders appear with blur
  - Create folder "Work" → rename to "Personal" → delete
  - Long-press a note → Move to "Personal" → switch drawer to "Personal" — note appears
  - Long-press → Pin / Unpin / Delete still work as before
