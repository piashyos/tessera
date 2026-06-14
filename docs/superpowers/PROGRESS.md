# Implementation Progress

Tracks completed chunks of the [Android Notes plan](plans/2026-06-14-android-notes.md).
Resume in a new session by reading this file + the plan, then continuing with the first **pending** chunk.

## Status

| # | Chunk | Status | Notes |
|---|-------|--------|-------|
| 1 | Project bootstrap | ⏳ in progress | — |
| 2 | Data layer (Room) | ⬜ pending | — |
| 3 | Domain + Repository (local-only) | ⬜ pending | TDD-step expansion deferred until start |
| 4 | UI core: floating capsules + theme | ⬜ pending | TDD-step expansion deferred until start |
| 5 | Notes list screen | ⬜ pending | TDD-step expansion deferred until start |
| 6 | Note editor screen | ⬜ pending | TDD-step expansion deferred until start |
| 7 | Firebase Auth + Settings | ⬜ pending | Requires `app/google-services.json` from user |
| 8 | Firestore sync | ⬜ pending | TDD-step expansion deferred until start |

Legend: ✅ done · ⏳ in progress · ⬜ pending · ⚠️ blocked

## How to resume

1. Open this file. Find the first non-✅ chunk.
2. Open `plans/2026-06-14-android-notes.md` and read that chunk's tasks.
3. If chunk is not yet detailed at TDD-step granularity (Chunks 3–8), first expand it using the same style as Chunks 1–2.
4. Continue execution. After each completed chunk, update this table and commit.

## Environment

- macOS, Android SDK at `~/Library/Android/sdk`
- `google-services.json` is gitignored; user provides locally before Chunk 7.
