# Material You Redesign — Plan 07: testing + QA pass

> **For agentic workers:** Use superpowers:executing-plans + verification-before-completion.

**Goal:** Финальная QA-пройдена для всей Tessera редизайн-серии. Скриншот-тесты для всех новых композ, accessibility audit, performance smoke.

**Spec:** §12–13.

---

## Chunk 1: Screenshot baselines (roborazzi)

### Task 1: NotesListScreen baseline
- Light/Dark variants
- Loading / Empty / Content states
- Drawer open with folder tree
- Search expanded overlay
- Selection mode top bar
- NoteActionsSheet open

### Task 2: NoteEditorScreen baseline
- Light/Dark
- Empty state placeholder
- With table block
- Format bar visible (IME up simulated)
- Tag picker popup

### Task 3: SettingsScreen baseline
- Light/Dark
- ThemePickerSheet open
- Sync states (Ok/Syncing/Offline/Error)

Each task: record baseline → re-run → verify deterministic. Commit baselines under `app/src/test/resources/screenshots/`.

---

## Chunk 2: Accessibility audit

### Task 4: Content descriptions
- Grep all `Icon(`, `IconButton(` для contentDescription, добавить недостающие.
- TalkBack manual smoke: navigate NotesList → editor → settings.

### Task 5: Touch target sizes
- Audit all `IconButton`, `Surface(onClick=)` — minimum 48×48dp.

### Task 6: Color contrast
- Verify all text colors hit WCAG AA (4.5:1) on light & dark backgrounds. Use Compose preview + manual check.

---

## Chunk 3: Performance + final regression

### Task 7: Frame timing
- Run app, scroll notes list 10s, capture systrace. Confirm no jank > 16ms.
- Editor: type 500 chars, confirm no input lag.

### Task 8: Lint clean
- Address all lint warnings introduced by Plans 04-06 (existing pre-existing errors out of scope).

### Task 9: Final smoke matrix
- Phone portrait/landscape
- Tablet (sw600dp)
- Light/Dark theme
- Dynamic color on/off (Android 12+)
- Russian locale

---

## Acceptance criteria

- [ ] Screenshot baselines committed for all key screens × states
- [ ] No missing contentDescriptions
- [ ] All touch targets ≥48dp
- [ ] Lint clean (no new errors from Plans 04-06)
- [ ] Smoke matrix passes
