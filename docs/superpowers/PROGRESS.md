# Tessera — Implementation Progress

**App name:** Tessera · **Package:** `app.tessera`

Tracks completed chunks of the [Tessera plan](plans/2026-06-14-android-notes.md).
Resume in a new session by reading this file + the plan, then continuing with the first **pending** chunk.

## Status

| # | Chunk | Status | Notes |
|---|-------|--------|-------|
| 1 | Project bootstrap | ✅ done | APK builds (`./gradlew :app:assembleDebug`). Wrapper @ Gradle 8.10.2. Commits `a47c8da`, `e54f090`. |
| 2 | Data layer (Room) | ✅ done | Entities, DAO, Room DB, DI, schema export, and 5 instrumented DAO tests added. `./gradlew :app:assembleDebug` and `./gradlew :app:connectedDebugAndroidTest` pass on emulator. |
| 3 | Domain + Repository (local-only) | ✅ done | Expanded to detailed TDD steps, added domain models, repository contracts/impls, local note mapper, Hilt repository bindings, and JVM tests for mapper/repository/use-cases. `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` pass. |
| 4 | UI core: floating capsules + theme | ✅ done | Expanded to detailed TDD steps, added polished theme tokens, `FloatingCapsule`, `GlassFab`, `MarkdownText`, `capsuleScale`, a runtime showcase in `TesseraApp`, plus verification scaffolding: Compose UI smoke test, Roborazzi screenshot baseline, and Maestro emulator flow. `./gradlew :app:testDebugUnitTest`, `./gradlew :app:connectedDebugAndroidTest`, `./gradlew :app:recordRoborazziDebug`, `./gradlew :app:assembleDebug`, and `./scripts/run-maestro-smoke.sh` pass. |
| 5 | Notes list screen | ✅ done | Staggered grid, `SwipeableNoteCard` (anchoredDraggable reveal Pin/Delete), snackbar undo wired to soft-delete/restore, pull-to-refresh no-op surface. Instrumented `NotesListScreenTest` (FAB nav, swipe→pin, delete→undo→restore). `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug` pass (9 instrumented). Note: Roborazzi `recordRoborazziDebug` fails on haze blur shader under Robolectric — UI covered by emulator tests instead. |
| 6 | Note editor screen | ✅ done | Autosave VM with debounce + flush-on-back, checklist pure helpers + interactive rows, markdown toolbar, tag chips picker, stateless `NoteEditorScreen`. Instrumented `NoteEditorScreenTest` (4 tests: title input, checklist toggle by index, back, pin). `./gradlew :app:connectedDebugAndroidTest` — 13 tests pass on emulator (Pixel 9 Pro AVD, API 16); `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL. |
| 7 | Firebase Auth + Settings | ✅ done | Built on `AuthRepository`/new `AccountRepository`: `AccountSwitchDecision` + `HandleAccountSwitchUseCase`, `SignIn/SignOutUseCase`, `AccountRepositoryImpl` (+ `NoteDao.reassignAllNotes`/`deleteByOwner`), `SettingsState`/`SettingsViewModel`, stateless `SettingsScreen` + `AccountSwitchDialog`, settings nav route + top-capsule entry, en/ru strings. **Live auth wired:** `google-services` plugin enabled (`app/google-services.json` → project `notes-d3abc`), `FirebaseModule` (Auth+Firestore), Credential-Manager `AuthRepositoryImpl` (`GetGoogleIdOption` → Firebase `signInWithCredential`), `CurrentActivityHolder` set from `MainActivity`. JVM tests (`HandleAccountSwitchUseCaseTest`, `SettingsViewModelTest`) pass; instrumented `SettingsScreenTest` compiles (needs emulator). `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` pass. Manual device sign-in pending verification. |
| 8 | Firestore sync | ✅ done | TDD-step expansion written in plan. Added `RemoteNote` DTO + `NoteMapper.toRemote`/`toEntity(remote,…)`, `RemoteNotesDataSource` seam + `FirestoreNotesClient` (`users/{uid}/notes`, snapshot listener, tombstone batch-delete). Rewrote `SyncRepository`/`SyncRepositoryImpl`: `syncNow` (push pending non-blank → LWW merge pull), `observeRemote` (listener→merge, race-safe), `purgeTombstones` (30-day local+remote). Added `@HiltWorker` `SyncWorker`/`DeleteTombstonesWorker`, `SyncManager` (observes `currentUser`, enqueues/cancels work + listener), `WorkManagerModule`, `TesseraApplication` `Configuration.Provider` (lazy+guarded sync start), manifest removes default `WorkManagerInitializer`. No DB schema change (v1). Tests: JVM `NoteMapperTest` (remote helpers) + `SyncRepositoryImplTest` (push/pull/LWW/tombstone/race/tags/purge); instrumented `DeleteTombstonesWorkerTest`. `./gradlew :app:testDebugUnitTest` + `:app:assembleDebug` pass; `:app:connectedDebugAndroidTest` — 26 tests pass on emulator. Live multi-device Firestore verification deferred (needs real account + 2 devices). |

### Native editor (Aztec replacement)

| Этап | План | Статус | Заметки |
|---|---|---|---|
| 1 «Ядро» | [plans/2026-11-06-native-editor-phase1.md](plans/2026-11-06-native-editor-phase1.md) | ✅ done (2026-11-07) | спека `specs/2026-11-06-native-editor-design.md`; ветка `feature/native-editor` (не влита); за debug-флагом; QA `qa/native-editor-6b.md` |
| 2 «Объекты и переключение» | [plans/2026-11-07-native-editor-phase2.md](plans/2026-11-07-native-editor-phase2.md) | ✅ done (2026-11-08) | спека `specs/2026-11-07-native-editor-phase2-design.md`; картинки, таблицы, Aztec удалён, флага нет; QA `qa/native-editor-p2-images.md`, `qa/native-editor-p2-tables.md`; ветка не влита |

### Редактор и оформление (2026-11)

| Фича | План | Статус | Заметки |
|---|---|---|---|
| Касания вне текста, ручка вставки, меню «⋯» | [plans/2026-11-11-editor-touch-and-format-sheet.md](plans/2026-11-11-editor-touch-and-format-sheet.md) | ✅ done (2026-11-11), ждёт коммита | спека `specs/2026-11-11-editor-touch-and-format-sheet-design.md`; QA `qa/editor-touch-and-format-sheet.md`; ветка `feature/editor-touch-and-format-sheet` (не закоммичено) |
| Оформление тегов и папок | [plans/2026-11-11-tag-folder-appearance.md](plans/2026-11-11-tag-folder-appearance.md) | ✅ done (2026-11-12), ждёт коммита | спека `specs/2026-11-11-tag-folder-appearance-design.md`; QA `qa/tag-folder-appearance.md`; Room 7→8; правила Firestore — деплой пользователем |

Legend: ✅ done · ⏳ in progress · ⬜ pending · ⚠️ blocked

## How to resume

1. Open this file. Find the first non-✅ chunk.
2. Open `plans/2026-06-14-android-notes.md` and read that chunk's tasks.
3. If chunk is not yet detailed at TDD-step granularity (Chunks 4–8), first expand it using the same style as earlier completed chunks.
4. Continue execution. After each completed chunk, update this table and commit.

## Environment

- macOS, Android SDK at `~/Library/Android/sdk`
- `google-services.json` is gitignored; user provides locally before Chunk 7.
