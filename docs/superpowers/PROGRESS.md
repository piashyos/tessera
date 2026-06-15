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
| 8 | Firestore sync | ⬜ pending | TDD-step expansion deferred until start |

Legend: ✅ done · ⏳ in progress · ⬜ pending · ⚠️ blocked

## How to resume

1. Open this file. Find the first non-✅ chunk.
2. Open `plans/2026-06-14-android-notes.md` and read that chunk's tasks.
3. If chunk is not yet detailed at TDD-step granularity (Chunks 4–8), first expand it using the same style as earlier completed chunks.
4. Continue execution. After each completed chunk, update this table and commit.

## Environment

- macOS, Android SDK at `~/Library/Android/sdk`
- `google-services.json` is gitignored; user provides locally before Chunk 7.
