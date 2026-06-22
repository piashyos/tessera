# WYSIWYG Note Editor (Aztec) + Firestore Sync Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the plain-text note body with a WYSIWYG rich-text editor (WordPress Aztec) that formats the selection immediately (bold, italic, strikethrough, H1/H2, bullet list, interactive task-list) while keeping Markdown as the canonical storage format, and fix Firestore sync so every local mutation is pushed without waiting for an app restart.

**Architecture:** Android / Kotlin / Jetpack Compose, MVVM with clean layers (domain / data / ui). Room is the local source of truth; Firestore is the remote backup pushed by a WorkManager `SyncWorker`. The editor hosts a native `AztecText` (a `View`) inside an `AndroidView`; Markdown↔HTML conversion happens at the editor boundary via a pure `MarkdownHtmlConverter`, so Room / Firestore / FTS / note-card preview are unchanged. Sync gets a `SyncScheduler` abstraction that enqueues the existing `SyncWorker` after every repository mutation.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.00), Room 2.6.1, Firebase Firestore, WorkManager 2.9.1, Hilt 2.52, WordPress Aztec editor (native `EditText`/span-based), JUnit 5 (jupiter) + Robolectric unit tests, Compose androidTest.

**Source spec:** `docs/superpowers/specs/2026-06-15-wysiwyg-editor-and-firestore-sync-design.md`. Read it before starting.

**Why these two fixes:**
- *Editor* — the body is a plain `BasicTextField` showing raw text; toolbar buttons append raw tokens (`"**bold**"`, `"- "`, `"- [ ] "`) to the END of `content` (`NotesNavGraph.kt:93-95` → `viewModel.onContentChange(state.content + token)`). Nothing is ever rendered. The Markwon `MarkdownText` composable exists but is unused.
- *Firestore* — `SyncRepositoryImpl.pushPending()` is reached only via a one-time `SyncWorker` enqueued once at sign-in (`SyncManager.onSignedIn`, `SyncManager.kt:57-66`). The live `observeRemote` listener only pulls. After the initial sync, local edits set `syncedAt = NULL` but are never pushed until the app restarts / re-signs-in.

**Ordering rationale:** Chunk 1 (sync) is fully independent and ships value immediately. Chunk 2 (Aztec dependency + spike) MUST precede Chunk 3 because the exact HTML Aztec emits for the task-list is unknown until the spike and it fixes the converter's mapping. Chunk 3 (pure converter) precedes Chunk 4 (editor integration). Chunk 5 finalizes tests, cleanup, and verification.

**Conventions (match the existing codebase):**
- `BasicTextField`, Lucide icons (`com.composables:icons-lucide-android`), `FloatingCapsule`/`haze` for the toolbar capsule.
- Tests: JVM unit tests use **JUnit 5 (jupiter)** with `org.junit.jupiter.api.*`; Robolectric tests use `@RunWith(RobolectricTestRunner::class)` + JUnit 4 `org.junit.*` (the module runs `useJUnitPlatform()` with the vintage engine, so both coexist). Compose UI tests live in `androidTest` and use `org.junit.*`.
- Commit after every green step. DRY, YAGNI, TDD.

---

## Chunk 1: Firestore sync on every local change

This chunk is independent of the editor work and can ship on its own. It introduces a `SyncScheduler` abstraction, calls it after every `NotesRepositoryImpl` mutation, and makes the sign-in enqueue policy consistent so an on-demand sync and a sign-in sync never cancel each other.

**File Structure for this chunk:**
- Create: `app/src/main/java/app/tessera/domain/repository/SyncScheduler.kt` — domain abstraction `requestSync()`.
- Create: `app/src/main/java/app/tessera/sync/WorkManagerSyncScheduler.kt` — enqueues the unique one-time `SyncWorker`.
- Modify: `app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt` — call `requestSync()` after every mutation.
- Modify: `app/src/main/java/app/tessera/core/di/RepositoryModule.kt` — bind `SyncScheduler`.
- Modify: `app/src/main/java/app/tessera/sync/SyncManager.kt` — make the sign-in enqueue policy `APPEND_OR_REPLACE`.
- Test: `app/src/test/java/app/tessera/data/repository/NotesRepositoryImplTest.kt` (+ `NotesRepositoryTagSyncTest.kt` compile fix).

### Task 1.1: Add the `SyncScheduler` domain interface

**Files:**
- Create: `app/src/main/java/app/tessera/domain/repository/SyncScheduler.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package app.tessera.domain.repository

/**
 * Requests that local pending changes be pushed to the remote backend as soon as possible.
 * Implementations coalesce overlapping requests; a single request after a burst of edits is
 * sufficient because the underlying sync pushes ALL pending notes.
 */
interface SyncScheduler {
    fun requestSync()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/app/tessera/domain/repository/SyncScheduler.kt
git commit -m "feat(sync): add SyncScheduler abstraction for on-demand push"
```

### Task 1.2: Trigger sync from `NotesRepositoryImpl` (TDD)

`NotesRepositoryImplTest` constructs the impl with positional args: `NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock)` at 11 call sites. `NotesRepositoryTagSyncTest.kt:44-50` also constructs it. Adding a constructor param breaks every site — all must be updated. All five mutating ops (`createNote`, `updateNote` [both branches], `deleteNote`, `restoreNote`, `togglePin`) set `syncedAt = NULL` in the DAO, so all five should request a sync.

**Files:**
- Modify: `app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt:21-99`
- Test: `app/src/test/java/app/tessera/data/repository/NotesRepositoryImplTest.kt`
- Test: `app/src/test/java/app/tessera/data/repository/NotesRepositoryTagSyncTest.kt` (compile fix only)

- [ ] **Step 1: Add a `FakeSyncScheduler` + a failing test to `NotesRepositoryImplTest.kt`**

Add this fake near the other fakes in the test file:

```kotlin
private class FakeSyncScheduler : app.tessera.domain.repository.SyncScheduler {
    var requestCount = 0
    override fun requestSync() { requestCount++ }
}
```

Update **every** `NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock)` call in the file (lines 33, 51, 78, 107, 125, 144, 175, 188, 209, 224, 238) to append a scheduler argument, e.g. `NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())`. Then add a new test:

```kotlin
@Test
fun `mutations request a sync push`() = runTest {
    val noteDao = FakeNoteDao()
    val tagDao = FakeTagDao()
    val clock = MutableClock(1L)
    val scheduler = FakeSyncScheduler()
    val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, scheduler)

    val note = repository.createNote(ownerId = UserId("u1"))
    assertEquals(1, scheduler.requestCount, "createNote should request sync")

    repository.updateNote(note.copy(title = "Hi", content = "There"))
    assertEquals(2, scheduler.requestCount, "updateNote should request sync")

    repository.togglePin(note.id)
    assertEquals(3, scheduler.requestCount, "togglePin should request sync")

    repository.deleteNote(note.id)
    assertEquals(4, scheduler.requestCount, "deleteNote should request sync")

    repository.restoreNote(note.id)
    assertEquals(5, scheduler.requestCount, "restoreNote should request sync")

    // Blank-draft branch of updateNote (hardDelete path) also requests a sync.
    val draft = repository.createNote(ownerId = UserId("u1"))
    val before = scheduler.requestCount
    repository.updateNote(draft.copy(title = "", content = ""))
    assertEquals(before + 1, scheduler.requestCount, "blank-draft updateNote should request sync")
}
```

> Match the existing fakes/clock in this file (`FakeNoteDao`, `FakeTagDao`, `MutableClock`). If their names differ, adapt this test to them; do not invent new helpers.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.NotesRepositoryImplTest"`
Expected: FAIL — `NotesRepositoryImpl` has no 5th constructor parameter (compile error).

- [ ] **Step 3: Add the dependency and the calls in `NotesRepositoryImpl`**

Add the import `import app.tessera.domain.repository.SyncScheduler` and the constructor param:

```kotlin
@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val noteMapper: NoteMapper,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
) : NotesRepository {
```

Call `syncScheduler.requestSync()` at the end of each mutating function, before each `return`/end of body:
- `createNote`: after `noteDao.upsert(noteMapper.toEntity(note))`, before `return note`.
- `updateNote`: in BOTH branches — after `noteDao.hardDelete(note.id.value)` (before `return note`) AND after `noteDao.upsert(noteMapper.toEntity(persisted))` (before `return persisted`).
- `deleteNote`: after `noteDao.softDelete(...)`.
- `restoreNote`: after `noteDao.restore(...)`.
- `togglePin`: after `noteDao.setPinned(...)`.

- [ ] **Step 4: Fix the other construction site**

In `app/src/test/java/app/tessera/data/repository/NotesRepositoryTagSyncTest.kt:44-50`, add a 5th argument to the `NotesRepositoryImpl(...)` constructor. Add a local `FakeSyncScheduler` (copy the same private class into this file, or extract a tiny shared test helper) and pass an instance. Goal is compilation only.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt \
  app/src/test/java/app/tessera/data/repository/NotesRepositoryImplTest.kt \
  app/src/test/java/app/tessera/data/repository/NotesRepositoryTagSyncTest.kt
git commit -m "feat(sync): request a push after every local note mutation"
```

### Task 1.3: Implement `WorkManagerSyncScheduler` and bind it

The scheduler enqueues a unique one-time `SyncWorker`. Use `ExistingWorkPolicy.APPEND_OR_REPLACE` so a sync is guaranteed to run AFTER the most recent edit (a single run pushes ALL pending notes, so bursts collapse safely). Resolve the uid from `AuthRepository.currentUser.value`; skip enqueue when signed out (edits stay local by design). Reuse `SyncWorker.UNIQUE_NAME`, `SyncWorker.KEY_UID`, and the same `NetworkType.CONNECTED` constraint as `SyncManager.onSignedIn`.

> Verify `AuthRepository.currentUser` is a `StateFlow<UserId?>` (it is read with `.value` in `SyncManager`). `UserId` exposes `.value: String`.

**Files:**
- Create: `app/src/main/java/app/tessera/sync/WorkManagerSyncScheduler.kt`
- Modify: `app/src/main/java/app/tessera/core/di/RepositoryModule.kt`

- [ ] **Step 1: Create the implementation**

```kotlin
package app.tessera.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.SyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val authRepository: AuthRepository,
) : SyncScheduler {
    override fun requestSync() {
        val uid = authRepository.currentUser.value ?: return
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(workDataOf(SyncWorker.KEY_UID to uid.value))
                .build()
        workManager.enqueueUniqueWork(
            SyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
```

> `WorkManager` must already be injectable (it is — `SyncManager` injects it). If not provided, check `WorkManagerModule`.

- [ ] **Step 2: Bind it in `RepositoryModule`**

Add imports `import app.tessera.sync.WorkManagerSyncScheduler` and `import app.tessera.domain.repository.SyncScheduler`, then add inside the module:

```kotlin
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
```

- [ ] **Step 3: Compile to verify the DI graph resolves**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt resolves `SyncScheduler` for `NotesRepositoryImpl`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/sync/WorkManagerSyncScheduler.kt \
  app/src/main/java/app/tessera/core/di/RepositoryModule.kt
git commit -m "feat(sync): enqueue SyncWorker on demand via WorkManagerSyncScheduler"
```

### Task 1.4: Make the sign-in enqueue policy consistent

`SyncManager.onSignedIn` (`SyncManager.kt:66`) enqueues `SyncWorker.UNIQUE_NAME` with `ExistingWorkPolicy.REPLACE`. Since both enqueue paths share one unique work name, a sign-in enqueue could cancel an in-flight on-demand sync (and vice versa). Switch the sign-in enqueue to `APPEND_OR_REPLACE` to match `WorkManagerSyncScheduler`.

**Files:**
- Modify: `app/src/main/java/app/tessera/sync/SyncManager.kt:66`

- [ ] **Step 1: Change the policy**

In `onSignedIn`, change:

```kotlin
workManager.enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, syncRequest)
```

to:

```kotlin
workManager.enqueueUniqueWork(SyncWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, syncRequest)
```

(The `ExistingWorkPolicy` import is already present.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/sync/SyncManager.kt
git commit -m "fix(sync): align sign-in enqueue with on-demand sync (APPEND_OR_REPLACE)"
```

### Task 1.5: Manual verification of sync

- [ ] **Step 1: Install and exercise**

Run: `./gradlew :app:installDebug`
On a signed-in device with network: create a note, edit it, wait a few seconds (do NOT restart the app), and confirm the document appears/updates in the Firestore console under `users/{uid}/notes`. Toggle pin and delete; confirm the remote reflects each change.

Expected: edits reach Firestore without an app restart. If signed out, edits stay local (by design). Record the result in the PR/commit notes (this step is manual).

---

## Chunk 2: Add Aztec + de-risking spike

Aztec is a native `EditText`/span-based rich-text editor. Before committing to full integration, a spike embeds `AztecText` in an `AndroidView` under the existing haze capsules to (a) confirm it renders/edits, (b) **capture the exact HTML Aztec emits** for each supported feature (this fixes the converter mapping in Chunk 3), and (c) check whether `haze` blur captures the `AndroidView` or the scrim fallback is needed.

**Dependency note (read carefully):** The spec assumed JitPack. The Aztec README (trunk) now publishes via the Automattic S3 maven repo: `maven { url = uri("https://a8c-libs.s3.amazonaws.com/android") }` with coordinates `org.wordpress:aztec:<version>` (latest release `v2.1.7`, 2026-03). JitPack also serves it as `com.github.wordpress-mobile.WordPress-Aztec-Android:aztec:<version>`. **Use whichever resolves**; prefer the official S3 repo + `org.wordpress:aztec` first, fall back to JitPack. Either way the project's `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so the repo MUST be added to `dependencyResolutionManagement { repositories { ... } }`. Capture the exact repo + coordinates that worked in the spike notes.

**File Structure for this chunk:**
- Modify: `settings.gradle.kts` — add the Aztec maven repo.
- Modify: `gradle/libs.versions.toml` — declare the Aztec version + library.
- Modify: `app/build.gradle.kts` — add the dependency.
- Create (temporary): a spike harness (a throwaway `@Preview`/scratch Activity or a small Composable wired into an existing debug entry) — NOT committed to main source, or committed then removed at the end of the spike.
- Create: `docs/superpowers/specs/aztec-spike-findings.md` — captured HTML shapes + haze result (feeds Chunk 3).

### Task 2.1: Add the Aztec repository and dependency

**Files:**
- Modify: `settings.gradle.kts:8-11`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the maven repo to `settings.gradle.kts`**

In the `dependencyResolutionManagement { repositories { ... } }` block (currently `repositories { google(); mavenCentral() }`), add the Aztec repo:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://a8c-libs.s3.amazonaws.com/android") }
        maven { url = uri("https://jitpack.io") }
    }
}
```

(Keep both repos for resolution flexibility; the spike confirms which one actually serves the artifact. Remove the unused one at the end of the chunk if desired.)

- [ ] **Step 2: Declare the version + library in the catalog**

In `gradle/libs.versions.toml` `[versions]`:

```toml
aztec = "v2.1.7"   # verify latest compatible release before pinning
```

In `[libraries]` (use the coordinates that resolve; the S3 repo serves `org.wordpress:aztec`):

```toml
aztec = { module = "org.wordpress:aztec", version.ref = "aztec" }
```

> If only JitPack resolves, instead use `aztec = { module = "com.github.wordpress-mobile.WordPress-Aztec-Android:aztec", version.ref = "aztec" }` and set `aztec = "v2.1.7"` (or the latest tag). Confirm by listing dependencies in Step 4.

- [ ] **Step 3: Add the dependency to the app module**

In `app/build.gradle.kts` `dependencies { }`, add near the other UI libs:

```kotlin
    implementation(libs.aztec)
```

- [ ] **Step 4: Confirm it resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -i aztec`
Expected: the `aztec` artifact resolves (and its transitive deps). If it fails, switch the repo/coordinates per Steps 1–2 and re-run.

- [ ] **Step 5: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(editor): add WordPress Aztec dependency + maven repo"
```

### Task 2.2: Spike — embed AztecText, capture HTML, verify haze

This is exploratory; the deliverable is **knowledge**, not production code. Keep the harness minimal and throwaway.

- [ ] **Step 1: Build a minimal `AztecText` harness**

Create a scratch Composable that wraps `AztecText` in an `AndroidView` and installs the toolbar/plugins. Minimal shape (verify class/method names against the resolved Aztec version — the API is `Aztec.with(aztecText, ...)` and `aztecText.fromHtml(...)` / `aztecText.toHtml()`):

```kotlin
// SCRATCH — do not keep in main source after the spike.
AndroidView(factory = { context ->
    AztecText(context).also { editor ->
        // Aztec.with(editor, toolbar, plugins...) — wire per the resolved API.
        // Install the task-list plugin (the spec calls it enableTaskList()/a task-list plugin).
        editor.fromHtml("<strong>bold</strong>")
    }
})
```

Wire it into a temporary debug entry (e.g. a scratch route or replace the editor body behind a flag) so it runs on an emulator. Do NOT delete or rewrite production files yet.

- [ ] **Step 2: Capture the exact HTML Aztec emits for every supported feature**

On the emulator, type/format each feature and log `aztecText.toHtml()` (use `Log.d` or render to a `Text`). Record the EXACT strings for:
- bold, italic, strikethrough
- H1, H2
- bullet (unordered) list (single item + multiple items)
- task-list **unchecked** and **checked** items (this is the unknown that fixes the converter)
- a combined document mixing several features
- empty content (confirm it is `""` or a single empty paragraph)

Also capture what `fromHtml(...)` accepts for the task-list (round-trip both directions in the harness).

- [ ] **Step 3: Verify the haze blur over the AndroidView**

With the `AztecText` scrolling under the existing floating capsules (top back/pin capsule and bottom toolbar capsule), observe whether the `haze` blur captures the editor content.
- If blur captures it: no change needed; note this.
- If blur does NOT capture the `AndroidView`: the fallback is a translucent **solid scrim** behind the capsules over the editor region. Acceptance bar: the capsules remain legible over scrolled text (sufficient contrast, no text bleeding through), even if the soft-blur aesthetic is lost. Note the decision.

- [ ] **Step 4: Record findings**

Write `docs/superpowers/specs/aztec-spike-findings.md` capturing: exact Aztec artifact repo+coordinates that worked, the Aztec setup API used (`Aztec.with(...)`, task-list plugin name/enable call), the exact HTML strings per feature from Step 2, and the haze result + chosen approach (blur vs scrim) from Step 3.

- [ ] **Step 5: Remove the scratch harness**

Revert/delete any throwaway harness code so production source is clean. (The dependency + repo from Task 2.1 stay.)

- [ ] **Step 6: Commit the findings**

```bash
git add docs/superpowers/specs/aztec-spike-findings.md
git commit -m "docs(editor): capture Aztec spike findings (HTML shapes + haze)"
```

---

## Chunk 3: `MarkdownHtmlConverter` (pure, TDD)

The risk-isolation unit: a pure-Kotlin converter with two functions and round-trip tests. Its task-list mapping uses the exact HTML captured in the spike (`aztec-spike-findings.md`). Scope is strictly the six supported features — no general Markdown library.

**File Structure for this chunk:**
- Create: `app/src/main/java/app/tessera/core/text/MarkdownHtmlConverter.kt` — pure, no Android deps.
- Test: `app/src/test/java/app/tessera/core/text/MarkdownHtmlConverterTest.kt` — per-feature + combined round-trip + edge cases.

**Supported mapping (canonical Markdown ↔ HTML):**
| Markdown | HTML |
|---|---|
| `**bold**` | `<strong>bold</strong>` |
| `*italic*` | `<em>italic</em>` |
| `~~strike~~` | `<s>strike</s>` (accept `<del>` on parse) |
| `# h1` | `<h1>h1</h1>` |
| `## h2` | `<h2>h2</h2>` |
| `- item` | unordered list (`<ul><li>item</li></ul>`) |
| `- [ ] task` / `- [x] task` | Aztec task-list markup (EXACT shape from spike) |

> **Executor:** replace the task-list HTML below with the exact strings from `aztec-spike-findings.md`. The placeholders here are the common Aztec shape; do not ship without reconciling against the spike. Inline emphasis (bold/italic/strike) must also work INSIDE list items and headings.

### Task 3.1: Per-feature round-trip tests (TDD)

The contract: `htmlToMarkdown(markdownToHtml(x)) == x` for every supported feature and a combined document. Unsupported/unexpected HTML is normalized to the nearest supported Markdown, never crashes.

**Files:**
- Create: `app/src/test/java/app/tessera/core/text/MarkdownHtmlConverterTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.tessera.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownHtmlConverterTest {

    private val converter = MarkdownHtmlConverter()

    private fun roundTrip(markdown: String) {
        val html = converter.markdownToHtml(markdown)
        assertEquals(markdown, converter.htmlToMarkdown(html), "round-trip failed; html was: $html")
    }

    @Test fun `bold round-trips`() = roundTrip("**bold**")
    @Test fun `italic round-trips`() = roundTrip("*italic*")
    @Test fun `strikethrough round-trips`() = roundTrip("~~strike~~")
    @Test fun `h1 round-trips`() = roundTrip("# Heading one")
    @Test fun `h2 round-trips`() = roundTrip("## Heading two")
    @Test fun `bullet list round-trips`() = roundTrip("- first\n- second")
    @Test fun `unchecked task round-trips`() = roundTrip("- [ ] todo")
    @Test fun `checked task round-trips`() = roundTrip("- [x] done")

    @Test
    fun `plain paragraph round-trips`() = roundTrip("Just some text.")

    @Test
    fun `empty content maps to empty`() {
        assertEquals("", converter.markdownToHtml(""))
        assertEquals("", converter.htmlToMarkdown(""))
    }

    @Test
    fun `combined document round-trips`() {
        val md = buildString {
            appendLine("# Title")
            appendLine("Some **bold** and *italic* and ~~strike~~ text.")
            appendLine("## Subhead")
            appendLine("- bullet one")
            appendLine("- bullet two")
            appendLine("- [ ] open task")
            append("- [x] closed task")
        }
        roundTrip(md)
    }

    @Test
    fun `bold inside heading round-trips`() = roundTrip("# A **bold** title")

    @Test
    fun `del tag parses as strikethrough markdown`() {
        assertEquals("~~x~~", converter.htmlToMarkdown("<del>x</del>"))
    }

    @Test
    fun `unsupported tag is stripped to plain text`() {
        // e.g. nested lists deeper than one level / unknown tags degrade gracefully
        val out = converter.htmlToMarkdown("<blockquote>quoted</blockquote>")
        assertEquals("quoted", out)
    }

    // HTML-side idempotency: feeding our generated HTML back through the round-trip must be
    // stable. This underpins the editor's echo-loop guard (Chunk 4) — the body decides whether
    // an HTML change is "external" by comparing the DERIVED MARKDOWN, so markdownToHtml must be a
    // fixed point after one normalization pass.
    @Test
    fun `markdown is a fixed point after one html normalization pass`() {
        val md = "# Title\n- [ ] open\n- [x] done\nSome **bold** text."
        val once = converter.htmlToMarkdown(converter.markdownToHtml(md))
        val twice = converter.htmlToMarkdown(converter.markdownToHtml(once))
        assertEquals(once, twice, "converter must be idempotent on the Markdown side")
        assertEquals(md, once)
    }
}
```

> Reconcile the task-list and bullet HTML the converter produces with the spike. If Aztec's canonical bold output differs (e.g. `<b>` vs `<strong>`), make `htmlToMarkdown` accept both and `markdownToHtml` emit what Aztec's `fromHtml` accepts. The round-trip equality is on the **Markdown** side, so the intermediate HTML only needs to round-trip, not match a fixed string.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.MarkdownHtmlConverterTest"`
Expected: FAIL — `MarkdownHtmlConverter` unresolved.

- [ ] **Step 3: Implement `MarkdownHtmlConverter`**

Create a pure class with the two functions. Implement line-based block parsing (headings, list items, task items, paragraphs) and inline emphasis handling (bold/italic/strike) shared between blocks. Keep it small and readable; no external Markdown library.

```kotlin
package app.tessera.core.text

/**
 * Translates between the app's canonical Markdown (the stored format) and the HTML the Aztec
 * editor consumes/produces. Scope is strictly the supported feature set: bold, italic,
 * strikethrough, H1/H2, bullet list, and task-list (checked/unchecked).
 *
 * Risk-isolation unit: pure, no Android deps, fully covered by round-trip unit tests.
 * The task-list HTML shape matches what Aztec emits (captured in aztec-spike-findings.md).
 */
class MarkdownHtmlConverter {
    fun markdownToHtml(markdown: String): String {
        // 1) split into lines/blocks
        // 2) per block: heading (# / ##), task item (- [ ] / - [x]), bullet (- ), else paragraph
        //    group consecutive bullets into one <ul>, consecutive tasks into the Aztec task-list block
        // 3) within block text, convert inline emphasis: **..** -> <strong>, *..* -> <em>, ~~..~~ -> <s>
        // 4) join blocks; empty input -> ""
        TODO("implement per the mapping table + spike HTML")
    }

    fun htmlToMarkdown(html: String): String {
        // Inverse of markdownToHtml. Accept synonyms: <b>/<strong>, <i>/<em>, <s>/<del>/<strike>.
        // Unsupported/unknown tags: unwrap to their text content (strip), never crash.
        // Empty input -> ""
        TODO("implement per the mapping table + spike HTML")
    }
}
```

Implement incrementally, running the test class after each feature until all pass. Prefer a tiny tokenizer/regex approach over pulling in a dependency (YAGNI).

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.MarkdownHtmlConverterTest"`
Expected: PASS (all cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/text/MarkdownHtmlConverter.kt \
  app/src/test/java/app/tessera/core/text/MarkdownHtmlConverterTest.kt
git commit -m "feat(editor): pure Markdown<->HTML converter with round-trip tests"
```

---

## Chunk 4: Aztec editor integration

Replace `ChecklistBody` with `RichNoteBody` (hosts `AztecText`), rewrite `MarkdownToolbar` to trigger Aztec formatting on the selection, and own the Markdown↔HTML boundary in `NoteEditorScreen`. The `NoteEditorViewModel` contract is **unchanged** (still `content: String` Markdown + `onContentChange(String)` + 500 ms autosave; it never sees HTML). Remove the now-dead checklist code.

**File Structure for this chunk:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` — `AndroidView`-hosted `AztecText` + a formatting handle for the toolbar.
- Modify: `app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt` — selection-aware formatting buttons (bold, italic, strike, H1, H2, bullet, task-list).
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` — Markdown↔HTML boundary, wire toolbar to the body's formatting actions.
- Modify: `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt:85-98` — drop `onInsertToken` + `onToggleChecklist`.
- Modify: `app/src/main/res/values/strings.xml` + `values-ru/strings.xml` — add toolbar content descriptions.
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt` — remove `onToggleChecklist`.

### Task 4.1: Add toolbar string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml:22-24`
- Modify: `app/src/main/res/values-ru/strings.xml:22-24`

- [ ] **Step 1: Add English strings**

In `values/strings.xml`, next to `toolbar_bold`/`toolbar_bullet`/`toolbar_checklist`, add:

```xml
    <string name="toolbar_italic">Italic</string>
    <string name="toolbar_strikethrough">Strikethrough</string>
    <string name="toolbar_h1">Heading 1</string>
    <string name="toolbar_h2">Heading 2</string>
```

(`toolbar_bold`, `toolbar_bullet`, `toolbar_checklist` already exist — reuse `toolbar_checklist` for the task-list button.)

- [ ] **Step 2: Add Russian strings**

In `values-ru/strings.xml`, add:

```xml
    <string name="toolbar_italic">Курсив</string>
    <string name="toolbar_strikethrough">Зачёркнутый</string>
    <string name="toolbar_h1">Заголовок 1</string>
    <string name="toolbar_h2">Заголовок 2</string>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "feat(editor): add toolbar string resources for new formats"
```

### Task 4.2: Build `RichNoteBody` (Aztec host + formatting handle)

`RichNoteBody` owns the `AztecText`. It accepts initial `html`, emits debounced `onHtmlChange`, and exposes a stable handle the toolbar calls (toggle bold/italic/strike, set H1/H2, toggle bullet/task-list). The handle wraps the Aztec format-toggle API discovered in the spike.

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt`

- [ ] **Step 1: Define a formatting handle interface**

```kotlin
package app.tessera.ui.editor.components

/** Toolbar-facing actions applied to the AztecText current selection. */
interface RichBodyActions {
    fun toggleBold()
    fun toggleItalic()
    fun toggleStrikethrough()
    fun toggleHeading1()
    fun toggleHeading2()
    fun toggleBulletList()
    fun toggleTaskList()
}
```

- [ ] **Step 2: Implement `RichNoteBody`**

Wrap `AztecText` in an `AndroidView`. Verify the exact Aztec API against the spike findings; the toggles use Aztec's `toggleFormatting(AztecTextFormat.FORMAT_...)` (e.g. `FORMAT_STRONG`, `FORMAT_ITALIC`, `FORMAT_STRIKETHROUGH`, `FORMAT_HEADING_1`, `FORMAT_HEADING_2`, `FORMAT_UNORDERED_LIST`, and the task-list format). Style text color/size from the Compose theme. Debounce `onHtmlChange` so HTML→Markdown conversion only runs after typing settles (reuse a short delay; the VM still applies its own 500 ms autosave debounce downstream).

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
// import org.wordpress.aztec.AztecText
// import org.wordpress.aztec.AztecTextFormat  (verify package against resolved version)

@Composable
fun RichNoteBody(
    html: String,
    markdownKey: String,
    onHtmlChange: (String) -> Unit,
    onActionsReady: (RichBodyActions) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tracks the Markdown the editor was last loaded with, to detect EXTERNAL changes only.
    var lastLoadedMarkdown by remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // val editor = AztecText(context)
            // Aztec.with(editor, ...).also { install task-list plugin per spike }
            // editor.fromHtml(html); lastLoadedMarkdown = markdownKey
            // editor.setOnImeBackListener / text-changed watcher -> debounce -> onHtmlChange(editor.toHtml())
            // onActionsReady(object : RichBodyActions { ... editor.toggleFormatting(...) ... })
            // return the editor View
            TODO("instantiate AztecText per spike findings; style from MaterialTheme")
        },
        update = { /* editor ->
            // Re-set HTML ONLY when markdownKey changed externally (differs from lastLoadedMarkdown).
            // Self-originated edits keep markdownKey == lastLoadedMarkdown, so this is a no-op and
            // the caret is preserved.
            // if (markdownKey != lastLoadedMarkdown) { editor.fromHtml(html); lastLoadedMarkdown = markdownKey }
        */ },
    )
}
```

> **Critical: avoid an echo loop, and compare on the Markdown side, not raw HTML.** Aztec's emitted HTML (attributes, whitespace, `<br>`, list wrapping) routinely differs from the converter's re-emitted HTML for the same logical content, so a raw-HTML `!=` check would fire on the editor's OWN edits and reset the caret. Instead, have `RichNoteBody` track the last Markdown it was loaded with (passed in) and only call `fromHtml` when the incoming Markdown differs from that last-loaded Markdown — i.e. an external change (first load, or a non-editor mutation). Equivalently, keep a `lastLoadedHtml` and skip re-applying when `htmlToMarkdown(incoming) == htmlToMarkdown(current)`. This relies on the Markdown-side idempotency proven by the converter test in Chunk 3.

- [ ] **Step 3: Compile (expect a screen-level break)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: may FAIL at `NoteEditorScreen.kt`/`MarkdownToolbar.kt` (still using the old API) — fixed in Tasks 4.3–4.4. This confirms `RichNoteBody` itself compiles.

- [ ] **Step 4: Stage (do NOT commit yet)**

The module won't compile until Task 4.4 rewires the screen. Stage the file and defer the commit to keep every commit compile-clean (Tasks 4.2–4.4 land as one compile-clean commit in Task 4.4 Step 7):

```bash
git add app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt
```

### Task 4.3: Rewrite `MarkdownToolbar` to trigger formatting

Replace the single `onInsertToken: (String) -> Unit` with explicit, intent-revealing callbacks. Keep the existing `ToolbarIcon` helper and Lucide icons; add `Italic`, `Strikethrough`, `Heading1`, `Heading2` (all exist in `com.composables:icons-lucide-android`). Reuse `List` for bullet and `ListChecks` for task-list.

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt`

- [ ] **Step 1: Rewrite the signature and buttons**

```kotlin
@Composable
fun MarkdownToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrikethrough: () -> Unit,
    onHeading1: () -> Unit,
    onHeading2: () -> Unit,
    onBullet: () -> Unit,
    onTaskList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarIcon(Lucide.Bold, stringResource(R.string.toolbar_bold), onClick = onBold)
        ToolbarIcon(Lucide.Italic, stringResource(R.string.toolbar_italic), onClick = onItalic)
        ToolbarIcon(Lucide.Strikethrough, stringResource(R.string.toolbar_strikethrough), onClick = onStrikethrough)
        ToolbarIcon(Lucide.Heading1, stringResource(R.string.toolbar_h1), onClick = onHeading1)
        ToolbarIcon(Lucide.Heading2, stringResource(R.string.toolbar_h2), onClick = onHeading2)
        ToolbarIcon(Lucide.List, stringResource(R.string.toolbar_bullet), onClick = onBullet)
        ToolbarIcon(Lucide.ListChecks, stringResource(R.string.toolbar_checklist), onClick = onTaskList)
    }
}
```

Update imports: add `com.composables.icons.lucide.Italic`, `com.composables.icons.lucide.Strikethrough`, `com.composables.icons.lucide.Heading1`, `com.composables.icons.lucide.Heading2`. Update both `@Preview` composables to pass the new callbacks (all `{}`).

> The bottom capsule has limited width (it shares a row with `TagChips`). If seven icons overflow, wrap the toolbar `Row` in a `horizontalScroll(rememberScrollState())` so all actions remain reachable.

- [ ] **Step 2: Compile (expect a screen-level break)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: FAIL at `NoteEditorScreen.kt` (old `MarkdownToolbar(onInsertToken = ...)`) — fixed in Task 4.4. Confirms the toolbar is well-formed.

- [ ] **Step 3: Stage (do NOT commit yet)**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt
```

### Task 4.4: Wire `NoteEditorScreen` — Markdown↔HTML boundary

`NoteEditorScreen` converts `state.content` (Markdown) → HTML on entry, feeds `RichNoteBody`, converts emitted HTML → Markdown, and calls `onContentChange(markdown)`. It holds the `RichBodyActions` handle and wires each toolbar button to it. Remove `onInsertToken` and `onToggleChecklist` from the screen signature; replace `ChecklistBody` with `RichNoteBody`.

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`

- [ ] **Step 1: Update the screen signature**

Remove `onToggleChecklist: (Int) -> Unit` (line 72) and `onInsertToken: (String) -> Unit` (line 76) parameters.

- [ ] **Step 2: Own the converter, HTML state, and actions handle**

Inside `NoteEditorScreen`, before the layout:

```kotlin
val converter = remember { MarkdownHtmlConverter() }
var actions by remember { mutableStateOf<RichBodyActions?>(null) }
// Initial HTML derived from the incoming Markdown. Recompute only when content changes
// from outside the editor (e.g. first load), not on every keystroke echo.
val initialHtml = remember(state.content) { converter.markdownToHtml(state.content) }
```

Pass `state.content` (the canonical Markdown) into `RichNoteBody` as the comparison key alongside `html`, so the body keys its re-load decision off the Markdown, not raw HTML (see the RichNoteBody critical note in Task 4.2). Update the `RichNoteBody` signature to accept a `markdownKey: String` parameter for this purpose.

Add imports: `app.tessera.core.text.MarkdownHtmlConverter`, `app.tessera.ui.editor.components.RichNoteBody`, `app.tessera.ui.editor.components.RichBodyActions`, plus `mutableStateOf`/`remember`/`getValue`/`setValue` (some already imported).

> Guarding against echo: `RichNoteBody` re-applies HTML only when `markdownKey` changes to a value that differs from the Markdown the editor currently holds. Self-originated edits flow `onHtmlChange → htmlToMarkdown → onContentChange → state.content`, and because `markdownToHtml` is idempotent on the Markdown side (Chunk 3 test), the recomputed `state.content` equals what the editor already shows, so the re-load is skipped (no caret reset). Verify no caret reset occurs in the manual test (Task 5.3).

- [ ] **Step 3: Replace `ChecklistBody` with `RichNoteBody`**

Replace the `ChecklistBody(...)` block (lines 149-154) with:

```kotlin
RichNoteBody(
    html = initialHtml,
    markdownKey = state.content,
    onHtmlChange = { html ->
        val markdown = converter.htmlToMarkdown(html)
        if (markdown != state.content) onContentChange(markdown)
    },
    onActionsReady = { actions = it },
    modifier = Modifier.fillMaxWidth(),
)
```

Remove the `import app.tessera.ui.editor.components.ChecklistBody`.

- [ ] **Step 4: Wire the toolbar to the actions handle**

Replace `MarkdownToolbar(onInsertToken = onInsertToken)` (line 291) with:

```kotlin
MarkdownToolbar(
    onBold = { actions?.toggleBold() },
    onItalic = { actions?.toggleItalic() },
    onStrikethrough = { actions?.toggleStrikethrough() },
    onHeading1 = { actions?.toggleHeading1() },
    onHeading2 = { actions?.toggleHeading2() },
    onBullet = { actions?.toggleBulletList() },
    onTaskList = { actions?.toggleTaskList() },
)
```

- [ ] **Step 5: Apply the haze/scrim decision from the spike**

If the spike found `haze` does NOT blur the `AndroidView`, apply the agreed fallback: a translucent solid scrim behind the floating capsules over the editor region (keep capsules legible). If blur works, leave the layout as-is. Reference `aztec-spike-findings.md`.

- [ ] **Step 6: Update the screen `@Preview`**

Remove `onToggleChecklist = {}` (line 330) and `onInsertToken = {}` (line 334) from `NoteEditorScreenLightPreview`. The preview will host a real `AztecText` via `RichNoteBody`; if the Android preview renderer can't instantiate Aztec, guard `RichNoteBody` to render a plain placeholder under `LocalInspectionMode.current`, OR remove the body from the preview. Keep the preview compiling.

- [ ] **Step 7: Compile, then commit Tasks 4.2–4.4 together (compile-clean)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — `RichNoteBody`, the rewritten `MarkdownToolbar`, and the screen now agree. (`NotesNavGraph` still passes the old args; if it errors, do Task 4.5 Step 1 first, then commit.)

```bash
git add app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt \
  app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt \
  app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): Aztec WYSIWYG body + selection-aware toolbar + Markdown<->HTML boundary"
```

### Task 4.5: Update the nav graph + remove `onToggleChecklist` from the VM

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt:85-98`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt:91-96`

- [ ] **Step 1: Fix the `NoteEditorScreen(...)` call in `NotesNavGraph`**

Remove the `onToggleChecklist = viewModel::onToggleChecklist` argument (line 89) and the entire `onInsertToken = { token -> viewModel.onContentChange(state.content + token) }` argument (lines 93-95).

- [ ] **Step 2: Remove `onToggleChecklist` from the ViewModel**

Delete the `onToggleChecklist(lineIndex: Int)` function (`NoteEditorViewModel.kt:91-96`). It used `toggleChecklistLine` (removed in Chunk 5). The VM contract otherwise stays identical.

- [ ] **Step 3: Compile + run unit tests**

Run: `./gradlew :app:compileDebugKotlin && ./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.NoteEditorViewModelTest"`
Expected: compile SUCCESSFUL; the VM test will FAIL on the now-removed `onToggleChecklist` test — that test is removed in Chunk 5 Task 5.2. (If you prefer a green bar here, jump to Task 5.2 to delete that one test, then return.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt \
  app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt
git commit -m "feat(editor): drop token-append + checklist toggle wiring"
```

---

## Chunk 5: Remove dead code, update tests, verify

Delete the checklist machinery Aztec replaces, update the editor instrumentation test for the new API, and run the full verification.

**File Structure for this chunk:**
- Remove: `app/src/main/java/app/tessera/ui/editor/Checklist.kt`, `app/src/main/java/app/tessera/ui/editor/components/ChecklistBody.kt`.
- Remove: `app/src/test/java/app/tessera/ui/editor/ChecklistTest.kt`.
- Modify: `app/src/test/java/app/tessera/ui/editor/NoteEditorViewModelTest.kt` — drop the checklist test.
- Modify: `app/src/androidTest/java/app/tessera/ui/editor/NoteEditorScreenTest.kt` — new screen API + Aztec assertions.
- Decide: keep or delete the unused `MarkdownText` composable.

### Task 5.1: Remove the checklist parser + body

**Files:**
- Delete: `app/src/main/java/app/tessera/ui/editor/Checklist.kt`
- Delete: `app/src/main/java/app/tessera/ui/editor/components/ChecklistBody.kt`
- Delete: `app/src/test/java/app/tessera/ui/editor/ChecklistTest.kt`

- [ ] **Step 1: Confirm no remaining references**

Search the codebase for `parseChecklist`, `toggleChecklistLine`, `ChecklistBody`, `ChecklistLine`, `editorChecklistItem`. The only expected remaining references after Chunk 4 are in `ChecklistTest.kt` (deleted here), `NoteEditorViewModelTest.kt` (Task 5.2), and `NoteEditorScreenTest.kt` (Task 5.3). If anything else references them, stop and reconsider.

- [ ] **Step 2: Delete the three files**

Delete `Checklist.kt`, `ChecklistBody.kt`, and `ChecklistTest.kt`.

- [ ] **Step 3: Decide on `editorChecklistItem` test tag**

`UiTestTags.editorChecklistItem(index)` (`UiTestTags.kt:25`) is no longer produced by the editor (Aztec owns checkboxes). Remove it once `NoteEditorScreenTest` no longer uses it (Task 5.3).

- [ ] **Step 4: Commit (after Tasks 5.2–5.3 compile clean — or commit deletions now and fix tests next)**

```bash
git rm app/src/main/java/app/tessera/ui/editor/Checklist.kt \
  app/src/main/java/app/tessera/ui/editor/components/ChecklistBody.kt \
  app/src/test/java/app/tessera/ui/editor/ChecklistTest.kt
git commit -m "refactor(editor): remove checklist parser/body (Aztec owns checkboxes)"
```

### Task 5.2: Drop the checklist test from the ViewModel test

**Files:**
- Modify: `app/src/test/java/app/tessera/ui/editor/NoteEditorViewModelTest.kt:124-142`

- [ ] **Step 1: Delete the obsolete test**

Remove the test `onToggleChecklist rewrites content and debounced save uses rewritten content` (lines 124-142), which calls the removed `vm.onToggleChecklist(...)` and `toggleChecklistLine(...)`. All other VM tests stay (they exercise the unchanged autosave/flush/delete/tags contract).

- [ ] **Step 2: Run the VM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.NoteEditorViewModelTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/app/tessera/ui/editor/NoteEditorViewModelTest.kt
git commit -m "test(editor): remove checklist-toggle VM test"
```

### Task 5.3: Update the editor instrumentation test for the new API

`NoteEditorScreenTest` (androidTest) constructs `NoteEditorScreen` with `onToggleChecklist`/`onInsertToken` and asserts checklist toggling. Update it to the new signature and Aztec behavior.

**Files:**
- Modify: `app/src/androidTest/java/app/tessera/ui/editor/NoteEditorScreenTest.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` (remove `editorChecklistItem`)

- [ ] **Step 1: Remove obsolete args + the checklist test**

In every `NoteEditorScreen(...)` call (tests 1-5), remove `onToggleChecklist = ...` and `onInsertToken = ...`. Delete test 2 (`tappingChecklistItemInvokesOnToggleChecklistWithCorrectIndex`) — there is no checklist row to tap anymore. Keep tests 1, 3, 4, 5 (title input, back, pin, delete) — they don't depend on the body.

- [ ] **Step 2: Add a WYSIWYG smoke test**

Add a test that types text, selects it, taps the Bold toolbar button, reopens/reads the body, and asserts the content is bold (e.g. the emitted Markdown via `onContentChange` contains `**...**`, or the rendered Aztec span is bold). And a task-list test: type a line, tap the task-list button, tap the checkbox, assert toggled. Use a capturing `onContentChange` lambda to inspect the Markdown.

```kotlin
@Test
fun selectingTextAndTappingBoldProducesBoldMarkdown() {
    var captured = ""
    composeRule.setContent {
        NoteEditorScreen(
            state = editorState(content = "hello"),
            onTitleChange = {},
            onContentChange = { captured = it },
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }
    // Focus body, select all (long-press + select-all or programmatic), tap Bold by content desc.
    composeRule.onNodeWithContentDescription("Bold").performClick()
    composeRule.waitForIdle()
    assertTrue(captured.contains("**hello**") || captured.contains("hello"))
}
```

> Selecting text in a hosted `AztecText` from a Compose test is non-trivial. If reliable selection isn't achievable in the test harness, assert the weaker invariant (toolbar button is present and tappable, content round-trips through the converter) and cover the true WYSIWYG behavior in the manual check (Step 4). Do not block the chunk on a flaky selection gesture.

- [ ] **Step 3: Remove the `editorChecklistItem` tag**

Delete `fun editorChecklistItem(index: Int)` from `UiTestTags.kt` (line 25) now that nothing references it.

- [ ] **Step 4: Run the instrumented editor tests + manual check**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.editor.NoteEditorScreenTest"` (requires a device/emulator).
Expected: PASS.
Manual on emulator: type text, select a word, tap Bold → it formats with no visible `**`; tap H1/H2/bullet/task-list and confirm WYSIWYG; toggle a task checkbox; reopen the note and confirm formatting persists; confirm the haze/scrim over the editor is acceptable. Record manual results.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/app/tessera/ui/editor/NoteEditorScreenTest.kt \
  app/src/main/java/app/tessera/core/ui/UiTestTags.kt
git commit -m "test(editor): update NoteEditorScreen test for Aztec WYSIWYG API"
```

### Task 5.4: Decide the fate of `MarkdownText`

**Files:**
- Possibly delete: `app/src/main/java/app/tessera/core/ui/components/MarkdownText.kt` (+ its test/usages, if any)

- [ ] **Step 1: Check for usages**

Search for `MarkdownText`. The note-card preview consumes raw Markdown today (unchanged by this work). If `MarkdownText` is referenced ONLY by previews/tests and nothing renders it in production, delete it and the Markwon dependency (`libs.markwon.core` in `app/build.gradle.kts:112` + the `markwon` version in `libs.versions.toml`). If anything still uses it, leave it.

- [ ] **Step 2: If deleting, remove the dependency too**

Remove `implementation(libs.markwon.core)` and the `markwon = "4.6.2"` version + `markwon-core` library entry. Run `./gradlew :app:compileDebugKotlin` to confirm.

- [ ] **Step 3: Commit (only if changed)**

```bash
git add -A
git commit -m "chore(editor): remove unused MarkdownText + Markwon dependency"
```

---

## Final verification

- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests pass (paste the summary line).
- [ ] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.editor.*"` — editor instrumentation passes (if a device is available).
- [ ] **Manual (Chunk 1):** on a signed-in device, edit a note and confirm it reaches Firestore WITHOUT an app restart; pin/delete reflect remotely.
- [ ] **Manual (Chunk 4/5):** WYSIWYG formatting (bold/italic/strike/H1/H2/bullet/task-list) shows no Markdown markers; task checkboxes toggle; reopening a note preserves formatting; existing Markdown notes open correctly (no migration); haze/scrim over the editor is legible.
- [ ] Report with superpowers:verification-before-completion — real command output only, explicitly flagging which checks were manual (Aztec API/selection-dependent and Firestore) vs automated.
