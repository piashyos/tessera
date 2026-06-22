# Material You Redesign — QA pass checklist (Plan 07)

Tracks what the Plan 07 QA pass automated versus what stays a manual, device-bound
check. Automated items are guarded by the unit-test suite (`./gradlew :app:testDebugUnitTest`);
manual items need a real device/emulator and a human eye, and are listed here so the pass is
reproducible rather than implicit.

## 1. Automated coverage (delivered by Plan 07)

### Screenshot baselines (roborazzi, Robolectric NATIVE)

Stored under `app/src/test/screenshots/ui/`. Record/verify:

```
./gradlew :app:recordRoborazziDebug   # regenerate baselines
./gradlew :app:verifyRoborazziDebug   # fail on pixel drift
```

| Screen | States captured |
| --- | --- |
| `NotesListScreen` | Loading, Empty, Content, selection mode, expanded search — each light + dark; folder drawer open; `NoteActionsSheet` modal |
| `NoteEditorScreen` | Content, empty, GFM table block — each light + dark; tag picker popup. Real AztecText body (inflates + renders deterministically under Robolectric) |
| `SettingsScreen` | Signed-in (light + dark), signed-out, sync indicator Ok/Syncing/Offline/Error, `ThemePickerSheet` modal |

### Accessibility audit (`AccessibilityAuditTest`)

- **Content descriptions** — every `Role.Button` node on the three redesigned screens must expose
  an accessible name. Guards against unlabeled icon buttons. (Found + fixed during this pass: the
  per-tag remove button had `contentDescription = null` → `editor_remove_tag`.)
- **Touch targets** — every visible clickable under 48dp must be a documented exception
  (see §3). A new sub-48dp control fails the test until consciously allowlisted.

## 2. Color contrast (WCAG AA) — computed from `TesseraColor`

Ratios for the key text/background pairs. Target: 4.5:1 for normal text, 3.0:1 for large
text / UI components.

| Pair | Light | Dark |
| --- | --- | --- |
| onSurface / surface | 15.49 ✓ | 13.59 ✓ |
| onBackground / background | 14.70 ✓ | 14.65 ✓ |
| onSurfaceVariant / surface (secondary text) | 7.09 ✓ | 9.38 ✓ |
| onSurfaceVariant / surfaceContainer (cards) | 6.22 ✓ | 8.83 ✓ |
| onPrimaryContainer / primaryContainer | 12.80 ✓ | 9.49 ✓ |
| onSecondaryContainer / secondaryContainer (active toggle) | 12.63 ✓ | 8.41 ✓ |
| error / surface | 6.54 ✓ | 9.65 ✓ |
| **primary / surface (accent)** | **3.90** | 5.28 ✓ |
| **onPrimary / primary (button label)** | **3.90** | 5.04 ✓ |

**Watch item:** in the light theme the terracotta `primary` against white sits at **3.90:1** — it
clears AA for large text (≥18.66px bold / ≥24px) and the 3:1 UI-component bar, but is below the
4.5:1 normal-text threshold. Acceptable for FAB / filled-button labels and large accents (current
usage); do **not** use `primary` for small body text on a light surface. Manual check below
confirms no such usage regressed.

## 3. Accessibility deviations registry (accepted, Plan 07 decision)

Compact custom `Surface(onClick=)` controls below the 48dp Material touch minimum. Kept as-is to
preserve the dense layout from Plans 04–06; mirrored in `AccessibilityAuditTest.acceptedSubMinimumTargets`.

| Control | Visual size | Rationale / mitigation |
| --- | --- | --- |
| Tag remove "X" (editor) | 20dp | Sits inside a larger chip row; whole chip is the comfortable target. Now labeled. |
| Add-tag "+" (editor) | 28dp | Adjacent to chips; opens a full popup. |
| Format-bar toggles (B/I/U/…) | 36dp | Dense editor toolbar, matches common rich-text editors; horizontally scrollable. |
| Tag filter chip (notes) | 32dp tall | Standard Material filter-chip height. |

Standard Material3 buttons (top-bar icon buttons, Sign out, view-mode toggle) report ~40dp layout
bounds but reserve a full 48dp touch slop via `minimumInteractiveComponentSize`, so their real
target is compliant.

## 4. Manual checklist (device / emulator required)

Not automatable in the headless CI/Robolectric environment — run on a device before release.

### TalkBack
- [ ] Enable TalkBack. Navigate NotesList → open a note (editor) → back → open Settings.
- [ ] Every actionable control announces a meaningful label; no "unlabeled button".
- [ ] Editor body announces as an editable text field with its hint/content.
- [ ] Reading order is logical top-to-bottom; drawer and bottom sheets trap focus correctly.

### Performance (systrace / frame timing)
- [ ] Scroll the notes list for ~10s; capture a trace. No frames > 16ms (no jank) on a mid-tier device.
- [ ] Editor: type ~500 characters continuously; no perceptible input lag, no dropped keystrokes.

### Smoke matrix
Verify layout + theming on each combination:
- [ ] Phone — portrait
- [ ] Phone — landscape
- [ ] Tablet (sw600dp)
- [ ] Light theme
- [ ] Dark theme
- [ ] Dynamic color ON (Android 12+)
- [ ] Dynamic color OFF (Tessera brand palette)
- [ ] Russian locale (`values-ru`) — no truncation/overlap, all strings translated

### Light-theme primary contrast (follow-up to §2)
- [ ] Confirm the terracotta `primary` is not used as small body text on a light surface
      (FAB, filled buttons, and large accents are fine at 3.9:1).
