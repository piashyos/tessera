# WYSIWYG Note Editor (Aztec) + Firestore Sync — Design

**Date:** 2026-06-15
**Status:** Approved by user (pending spec review)

## Problem

Two defects in the Tessera notes app (Android / Kotlin / Jetpack Compose):

1. **The editor is not a real Markdown/rich-text editor.** The body is a plain `BasicTextField` that shows raw text. The toolbar buttons append raw tokens (`"**bold**"`, `"- "`, `"- [ ] "`) to the END of the content (`NotesNavGraph.kt:93-95` → `content + token`) instead of wrapping the selection or inserting at the cursor. Nothing is ever rendered. The Markwon-based `MarkdownText` composable exists but is unused.
2. **Edits do not reach Firestore.** `SyncRepositoryImpl.pushPending()` is only reached via a one-time `SyncWorker` enqueued once at sign-in (`SyncManager.onSignedIn`, `SyncManager.kt:60-66`). The live `observeRemote` listener only pulls (`mergeRemote`). After the initial sync, local edits set `syncedAt = NULL` but are never pushed until the app restarts or the user re-signs-in.

## Goal

Replace the editor body with a **WYSIWYG rich-text editor (Aztec, WordPress)** where the user types plain text, selects a span, taps a toolbar button, and the text is **immediately formatted** with no visible Markdown markers. Supported formatting: **bold, italic, strikethrough, headings (H1/H2), bullet list, and an interactive task-list (tappable checkboxes)**. Markdown remains the canonical storage format (Room / Firestore / FTS search / note-card preview unchanged). Separately, **fix Firestore sync** so every local mutation is pushed.

## Decisions (made during brainstorming)

- **Editor library: Aztec.** Chosen over `compose-rich-editor` because the user prioritized runtime speed and stability: Aztec is a native `EditText`/span-based editor, production-proven in the WordPress app, and is the only ready-made library that natively supports interactive task-list checkboxes (`enableTaskList()`). `compose-rich-editor` lacks checkbox support (open issues #126, #430) and is still RC.
- **Storage stays Markdown (option A).** Convert Markdown↔HTML at the editor boundary so Firestore shape, FTS search, note-card preview, and all existing notes remain unchanged. No data migration. The conversion is the isolated risk point and is covered by round-trip unit tests.
- **WYSIWYG, not syntax highlighting.** Markers (`**`, `#`) are never shown to the user.

## Non-goals

- No change to the Room schema, the Firestore document shape (`RemoteNote`), the FTS search, or the note-card preview rendering.
- No new formatting beyond the six listed features (no tables, images, code blocks, links, colors). YAGNI.
- No migration of stored content (it stays Markdown).
- No change to the autosave debounce (existing 500 ms) or the tag/pin/delete flows.

## Architecture

### Components

1. **`MarkdownHtmlConverter`** — new, pure Kotlin (no Android deps), in `app/src/main/java/app/tessera/core/text/`.
   - `fun markdownToHtml(markdown: String): String`
   - `fun htmlToMarkdown(html: String): String`
   - Scope is strictly the supported feature set: `**bold**` ↔ `<strong>`, `*italic*` ↔ `<em>`, `~~strike~~` ↔ `<s>`/`<del>`, `# `/`## ` ↔ `<h1>`/`<h2>`, `- ` ↔ unordered list, and `- [ ]`/`- [x]` ↔ Aztec task-list markup.
   - **This is the risk-isolation unit.** It has a single responsibility (format translation), a tiny interface (two pure functions), and is fully unit-testable in isolation with round-trip tests. A narrow hand-written converter is preferred over a general library (flexmark) to keep the dependency surface small and the behavior exactly matched to Aztec's HTML and to the existing `- [ ]` Markdown convention.
   - **The exact Aztec task-list HTML shape is unknown until the spike (Task 1).** The converter's task-list mapping is finalized against the real HTML Aztec emits, captured in the spike.

2. **`RichNoteBody`** — new Composable in `app/src/main/java/app/tessera/ui/editor/components/`, replacing `ChecklistBody`. Wraps `AztecText` (a `View`) in an `AndroidView`.
   - Input: `html: String`, `onHtmlChange: (String) -> Unit`, plus a handle/state exposing formatting actions to the toolbar.
   - Owns the `Aztec`/`AztecText` instance, installs the task-list plugin (`enableTaskList()`), styles text color/size from the Compose theme.
   - Debounces `onHtmlChange` emissions (e.g. reuse a short debounce) so the screen converts HTML→Markdown only after typing settles.

3. **`MarkdownToolbar`** (modified) — buttons trigger Aztec formatting actions on the current selection instead of inserting tokens. Buttons: bold, italic, strikethrough, H1, H2, bullet list, task-list. Lives in the existing bottom floating capsule.

4. **`NoteEditorScreen`** (modified) — owns the Markdown↔HTML boundary. On entry: `markdownToHtml(state.content)` → load into `RichNoteBody`. On change: `RichNoteBody` emits HTML → `htmlToMarkdown(html)` → `onContentChange(markdown)`. Wires toolbar buttons to the editor's formatting actions.

5. **`NoteEditorViewModel`** — **contract unchanged**: still exposes `content: String` (Markdown) and `onContentChange(String)`, still autosaves with the existing 500 ms debounce. It never sees HTML.

6. **Sync fix** — `SyncScheduler` abstraction (domain interface) + `WorkManagerSyncScheduler` impl that enqueues a one-time `SyncWorker` (`ExistingWorkPolicy.APPEND_OR_REPLACE`, uid from `AuthRepository.currentUser.value`). `NotesRepositoryImpl` calls `requestSync()` after every mutation (`createNote`, `updateNote` — both branches, `deleteNote`, `restoreNote`, `togglePin`). All these DAO ops already set `syncedAt = NULL`, so `pushPending` picks them up.
   - **Work-policy consistency:** `SyncManager.onSignedIn` currently enqueues the same `SyncWorker.UNIQUE_NAME` with `ExistingWorkPolicy.REPLACE`. Since both enqueue paths share one unique work name, the plan must make them consistent — the initial sign-in enqueue should also move to `APPEND_OR_REPLACE` (or route through the same `SyncScheduler`) so a sign-in enqueue cannot cancel an in-flight on-demand sync (and vice versa).

### Removed

- `ChecklistBody`, `parseChecklist`, `toggleChecklistLine`, and the `Checklist.kt` parser (Aztec owns checkboxes now).
- `MarkdownToolbar`'s `onInsertToken` API and the `NotesNavGraph.kt:93-95` `content + token` wiring.
- The unused `MarkdownText` composable is left in place only if note-card preview later wants it; it is NOT wired into the editor. (If it remains unused after this work, it can be deleted — decided during planning.)

## Data flow

**Open note:**
`VM.content (Markdown)` → `NoteEditorScreen.markdownToHtml()` → `RichNoteBody` → `aztec.fromHtml(html)` renders WYSIWYG.

**Edit / format:**
user types or taps a toolbar button → `AztecText` updates → debounced → `aztec.toHtml()` → `NoteEditorScreen.htmlToMarkdown()` → `onContentChange(markdown)` → existing 500 ms autosave → `UpdateNoteUseCase` → `NotesRepositoryImpl.updateNote()` → Room (`syncedAt = NULL`) → `syncScheduler.requestSync()` → `SyncWorker` → `pushPending` → Firestore.

**Search & preview:** `observeNotes` / `searchNotes` / note-card preview consume the Markdown `content` exactly as today — unchanged.

## Error handling & edge cases

- **Conversion fidelity:** `htmlToMarkdown(markdownToHtml(x)) == x` must hold for all supported features and a combined document. Round-trip unit tests are the gate. Unsupported HTML that Aztec might emit (e.g. nested lists deeper than one level, unexpected tags) is normalized/stripped to the nearest supported Markdown rather than crashing.
- **Empty content:** empty Markdown ↔ empty HTML ↔ empty editor; the blank-draft deletion path in `updateNote` (`isBlankDraft()`) still works because the VM still receives empty Markdown.
- **Signed-out user:** `requestSync()` reads `AuthRepository.currentUser.value`; if null it no-ops (edits stay local by design).
- **Sync coalescing:** `APPEND_OR_REPLACE` guarantees a sync runs after the most recent edit; a single run pushes ALL pending notes, so bursts of edits collapse safely.
- **haze blur over AndroidView (visual risk):** the editor scrolls under floating capsules that apply a `haze` blur. `haze` blurs Compose layers; an `AndroidView`-hosted `AztecText` may not be captured, so the capsules might stop blurring the editor content. **Mitigation:** Task 1 is a spike to verify; fallback is a translucent solid scrim over the editor region instead of blur.

## Risks / spike

**Task 1 (spike, before committing to full integration):** Embed `AztecText` in an `AndroidView` inside the editor layout under the existing haze capsules. This first requires **adding the JitPack repository** (see Dependency setup below), since the spike cannot resolve the artifact otherwise. Verify (a) the editor renders/edits correctly, (b) capture the exact HTML Aztec emits for bold/italic/strike/H1/H2/bullet/task-list (this fixes the converter's mapping), (c) whether haze blur captures the AndroidView or the scrim fallback is needed. The spike's findings feed the converter spec and the haze decision.

**Acceptance bar for the haze fallback:** if blur does not capture the AndroidView, the scrim is acceptable when the floating capsules remain legible over scrolled editor text (sufficient contrast, no text bleeding through) — i.e. the capsules must not become transparent/illegible, even if the soft-blur aesthetic is lost.

## Dependency setup

Aztec (WordPress) is published on **JitPack**, not Maven Central or Google. The project's `settings.gradle.kts` uses `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` with only `google()` and `mavenCentral()`. Adding the Aztec dependency therefore also requires adding `maven { url = uri("https://jitpack.io") }` to the `dependencyResolutionManagement { repositories { ... } }` block in `settings.gradle.kts`. Without this, dependency resolution fails. This must happen as part of Task 1 (the spike must resolve the artifact to embed `AztecText`).

## Testing strategy

- **`MarkdownHtmlConverterTest`** (JUnit5, pure): per-feature round-trip tests (bold, italic, strike, H1, H2, bullet, task checked/unchecked) + a combined-document round-trip + edge cases (empty, plain paragraph, mixed).
- **`NotesRepositoryImplTest`** (extended): a fake `SyncScheduler` asserts `requestSync()` is called after create/update/delete/restore/togglePin. `NotesRepositoryTagSyncTest` updated for the new constructor param (compile fix).
- **Editor androidTest** (`NoteEditorScreenTest`): type text, select + tap bold → formatted; toggle a checkbox; reopen note preserves formatting; updated for the new screen API (no `onInsertToken`).
- **Manual:** on a signed-in device, edit a note and confirm it reaches Firestore without an app restart; format text and confirm WYSIWYG behavior; confirm the haze/scrim visual is acceptable.

## Affected files (indicative)

- Create: `core/text/MarkdownHtmlConverter.kt`, `ui/editor/components/RichNoteBody.kt`, `domain/repository/SyncScheduler.kt`, `sync/WorkManagerSyncScheduler.kt`, `core/text/MarkdownHtmlConverterTest.kt`.
- Modify: `ui/editor/components/MarkdownToolbar.kt`, `ui/editor/NoteEditorScreen.kt`, `ui/navigation/NotesNavGraph.kt`, `data/repository/NotesRepositoryImpl.kt`, `sync/SyncManager.kt` (work-policy consistency), `core/di/RepositoryModule.kt`, `settings.gradle.kts` (add JitPack repo), `gradle/libs.versions.toml`, `app/build.gradle.kts`, `NotesRepositoryImplTest.kt`, `NotesRepositoryTagSyncTest.kt`, `NoteEditorScreenTest.kt`.
- Remove: `ui/editor/Checklist.kt`, `ui/editor/components/ChecklistBody.kt` (+ their tests `ChecklistTest.kt`, and checklist assertions in `NoteEditorViewModelTest.kt`).

## Open items resolved during planning

- Exact Aztec artifact coordinates/version and Gradle setup → captured in the spike (Task 1) and the implementation plan.
- Final task-list HTML↔Markdown mapping → fixed by the spike before the converter is implemented.
- Whether to delete the now-unused `MarkdownText` → decided in the plan.
