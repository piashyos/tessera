# Swipe-to-reveal actions + trash restore fix

**Date:** 2026-07-03
**Status:** Approved (design)
**Scope:** Notes list screen only — swipe interaction and the deleted-note restore path.

## Problem

1. **Accidental deletes.** The notes list uses Material3 `SwipeToDismissBox`
   (`TesseraSwipeableNoteCard.kt`). Crossing ~55% of the card width in a
   direction *immediately commits* the action (archive on swipe-right, delete on
   swipe-left). A single careless swipe deletes a note. The user wants swipe to
   *reveal compact action buttons* that only fire on an explicit tap.
2. **Restore is unreliable.** A note deleted into trash can't be reliably
   brought back — reportedly even the trash "restore" affordance fails. Local
   `NoteDao.restore` looks correct, so the root cause is unconfirmed and must be
   reproduced before fixing (prime suspect: a remote tombstone being replayed by
   sync merge).

## Part A — Swipe reveals action buttons (tap to act)

### Interaction model
Replace `SwipeToDismissBox` with a custom swipe-to-reveal card built on Compose
Foundation `AnchoredDraggable` (no new dependencies). Three anchors:

```
LeftRevealed  <--  Settled  -->  RightRevealed
```

- **Swipe right** (start-to-end) → card slides right, revealing **one button on
  the left**.
- **Swipe left** (end-to-start) → card slides left, revealing **up to two
  buttons on the right**.
- Buttons have fixed width; the card only slides far enough to expose them (it
  does not fly off-screen).
- **Actions fire only on button tap.** The swipe itself never mutates data →
  accidental deletion is impossible.
- **One card open at a time.** Opening another card, scrolling the list, or
  tapping the open card's body collapses the open one back to `Settled`.
- After an action the card collapses; for **Delete** a Snackbar with **Undo** is
  shown (existing `handleDelete` behavior is preserved).

### Actions per view
The buttons are context-aware, mirroring the existing dispatch in
`NotesListScreen.kt` (lines ~369–376). Two independent gates decide button
presence:
- **Pin** is gated *by view*, matching line ~473: shown only outside
  trash/archive/**shared** views.
- **Delete** is gated *by note*: `canDeleteFromList()` /
  `swipeToDeleteEnabled` (shared notes the user can't delete for everyone).

| View                          | Left panel (swipe right) | Right panel (swipe left)            |
|-------------------------------|--------------------------|-------------------------------------|
| All / folder / search / pinned| **Archive**              | **Pin/Unpin** + **Delete***         |
| Archive                       | **Unarchive**            | **Delete***                         |
| Trash                         | **Restore**              | **Delete forever**                  |
| Shared view                   | **Archive**              | **Delete*** (no pin — view-gated)   |

\* Delete is hidden when `canDeleteFromList()` is false. If a right panel would
end up with **zero** buttons (e.g. shared view + no delete rights), swipe-left
reveals nothing and is inert for that card.

- **Delete** = tap deletes to trash + Snackbar Undo (via existing
  `handleDelete`). **Delete forever** (trash) = tap → `handlePermanentDelete`
  (keeps its existing permanent-delete Snackbar; no undo, matching today).
- **Pin/Unpin** toggles and collapses; the note re-sorts (pinned to top).

### Component shape
`TesseraSwipeableNoteCard` keeps its call-site contract as far as possible but
gains the data it needs to render the right buttons:

- The call site passes an explicit list of actions to render, e.g.
  `leftActions: List<SwipeAction>` and `rightActions: List<SwipeAction>` where
  `SwipeAction` = (icon, label, container/tint colors, `onClick`). The call site
  builds these from the current view, so the component stays dumb about views
  and there is no per-action nullable-lambda ambiguity. An empty list = no panel
  on that side.
- Internal: `AnchoredDraggableState`, a background `Row` of buttons sized to the
  card height, and the foreground `TesseraNoteCard`. Reuse `TesseraShape` /
  `TesseraSpacing` and the existing `secondaryContainer` / `errorContainer`
  color language.
- Collapse coordination: a single "currently open note id" hoisted into the
  list (`NotesListScreen`) so opening one closes the rest. Collapse the open
  card when `listState.isScrollInProgress` becomes true, and on a tap of the
  open card's body.

### Out of scope for Part A
Long-press `NoteActionsSheet` (bottom sheet) is unchanged — it already exposes
archive/pin/delete/restore and remains the full-action surface.

## Part B — Fix trash restore

Follow systematic-debugging:

1. **Reproduce.** Delete a **private** note → open Trash → Restore. Confirm
   whether it (a) reappears in All Notes and (b) survives a sync cycle. Repeat
   for a **shared** note.
2. **Locate root cause.** Candidate paths, in order of suspicion:
   - `SyncRepositoryImpl.mergeRemoteShared` (lines ~278–293) upserts a remote
     shared note even when it still carries a `deletedAt` tombstone, and
     `remoteContentIsNewer` can re-delete a just-restored note.
   - `mergeRemote` (lines ~239–249) last-write-wins vs. push ordering for
     private notes.
   - UI/VM wiring of `onRestoreNote` in the (new) swipe component.
3. **Write failing tests** at the repository/DAO layer that encode "restore
   then merge stale remote tombstone → note stays restored" for **both** the
   private path (`mergeRemote`) and the shared path (`mergeRemoteShared`), then
   make them pass. Add a UI-level check that Restore moves a note out of Trash
   into active.

## Non-goals
- No changes to the Firestore schema, the `deletedAt` soft-delete model, tombstone
  purge, or any screen other than the notes list + its swipe card.
- No new third-party libraries.

## Testing
- Component test: swipe reveals buttons; swipe alone performs no mutation; tap
  fires the correct action; opening a second card collapses the first.
- Regression test for Part B as described above.
- Screenshot/Roborazzi coverage for revealed-state left/right panels (respecting
  the AztecText/Roborazzi gotchas already noted for this repo).
