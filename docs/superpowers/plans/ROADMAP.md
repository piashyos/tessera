# Roadmap — Editor & App Polish (Plans A–D)

Continuous execution path through the four plans created on 2026-06-16. Each plan terminates with `finishing-a-development-branch`; after that, move on to the next.

| # | Plan | File | Risk | Est. tasks | Depends on |
|---|---|---|---|---|---|
| 1 | **A** — Editor UX polish | `2026-06-16-editor-ux-polish.md` | Low | 10 | — |
| 2 | **D** — Settings: themes + i18n | `2026-06-16-settings-theme-and-i18n.md` | Low–Med | 8 | A (just to ship in stable order) |
| 3 | **B** — Shared-element transitions | `2026-06-16-shared-element-screen-transitions.md` | Med | 8 | A, D (Compose BOM bump is here — break carefully if tests added in A/D run first) |
| 4 | **C** — Tables in editor | `2026-06-16-tables-in-editor.md` | High | 8 | A (Plan C adds an "Insert table" entry to Plan A's dropdown) |

## Why this order

1. **A first** — concrete requirements, no open design questions, ships UX wins fast.
2. **D second** — independent of A; gets theme + locale infrastructure in before transitions affect screen entry/exit. The DataStore + preferences foundation is reused by future settings work.
3. **B third** — needs Compose BOM bump (`2024.10.00 → 2025.01.00`). Doing this AFTER A/D means we don't fight regressions during simpler work.
4. **C last** — biggest scope, requires the dropdown from Plan A and benefits from a stable theme system. Plan C explicitly modifies `MarkdownFormatMenu.kt` which is created by Plan A.

## Execution protocol per plan

For each plan, in order:

1. **Read** the plan top-to-bottom. Surface anything that's stale (the codebase will have moved since the plan was written).
2. **Run** `superpowers:subagent-driven-development` with the plan file as input.
3. **Walk** the bite-sized tasks chunk by chunk. Each chunk ends in a commit.
4. **Verify** the smoke tests at the end of each chunk before moving on.
5. **Finish** the branch via `superpowers:finishing-a-development-branch` once the plan's final task is done.
6. **Update this roadmap** — mark the plan as ✅ done.

## Status

- [x] Plan A — Editor UX polish
- [x] Plan D — Settings: themes + i18n
- [x] Plan B — Shared-element transitions
- [~] Plan C — Tables in editor (foundation done; integration deferred)

> **Plan C status (2026-06-17):** Tasks 1–4 committed (`d356f8e` TableGrid, `0845076` GFM parser, `b2b982b` block split/join, `8df7d37` TableBlockEditor). Pure new files in `core/text/markdownblocks/` + `ui/editor/components/TableBlockEditor.kt`, plus JVM/Compose tests — all green, isolated from rest of codebase. Tasks 5–7 (block-stack rendering in `NoteEditorScreen`, insert-table action via `MarkdownFormatMenu`, column drag-resize) were skipped because the worktree carries uncommitted WIP in `NoteEditorScreen.kt`, `RichNoteBody.kt`, `MarkdownFormatMenu.kt` (looks like the folders/editor-polish plan in flight). Resume Plan C from Task 5 after that WIP is committed or stashed — see plan file Chunk 3 onward.

## Cross-plan notes

- **Plan A → Plan C dependency:** Plan C's Task 6 adds an "Insert table" entry to `MarkdownFormatMenu` (created in Plan A). If Plan C executes first, fall back to a separate toolbar button.
- **Plan B → BOM bump:** if Plan A or D pinned any 1.6.x-only Compose APIs, they'll need a tweak when B bumps the BOM. Both plans are written to avoid such APIs.
- **Plan D theme variants visible in Plan B's shared-element transition:** the morph in Plan B should look correct under all 5 theme modes — verify after both land.
- **Tests:** every plan ships its own tests + smoke checks. No global test reorganisation needed.

## Re-planning gates

Re-run brainstorming + writing-plans for any of these if circumstances change before execution:

- Plan B: if Compose BOM 2025.01.00+ surfaces a blocker (e.g., other dependencies don't support it), re-plan with `Crossfade` + manual `Animatable` instead of `SharedTransitionLayout`.
- Plan C: if the block-stack model proves too disruptive in spike, fall back to "single AztecText editor + read-only table render below". Re-plan accordingly.
- Plan D: if `AppCompatDelegate.setApplicationLocales` causes activity-restart issues with unsaved edits, plan a save-then-switch flow.
