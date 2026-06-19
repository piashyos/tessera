# Tessera — Material You Redesign (with brand identity)

**Date:** 2026-06-19
**Branch:** `feature/wysiwyg-editor-and-sync`
**Status:** Design — ready for plan
**Mockups:** `design/mockups/index.html` (16 screens, validated)

## 1. Goal

Перенести Tessera (Android-приложение заметок) на Material You design language с сохранением фирменной айдентики: тёплая терракотовая палитра, кремовый фон, Instrument Serif в заголовках. Включает:

- Полную M3-палитру (30 ролей × light/dark) с гибридной dynamic-color стратегией на API 31+ и брендовым fallback на API 26-30.
- Двухшрифтовую типографику: Inter (variable) для UI, Instrument Serif для display/headline/editor H1.
- Редизайн трёх главных экранов: NotesListScreen (drawer + sticky tag chips + плавающий bottom search/FAB), NoteEditorScreen (inline WYSIWYG с serif H1 + format-bar над клавиатурой), SettingsScreen (large top app bar + сгруппированные карточки + sync indicator).
- Папки 2-уровневой иерархии: добавление `parentId` в `Folder`, миграция Room 3→4, обновление Firestore схемы, drawer-based навигация.
- Восстановление цветного M3 ripple на брендовых элементах (FAB, chips, primary buttons).

## 2. Non-goals

- Golden screenshot CI (Paparazzi/Roborazzi) — отдельный future spec.
- Иллюстрации для empty states (используем типографику).
- RTL polish (locale config готов, зеркальность — отдельный issue).
- Tablet/foldable layouts (mobile only).
- TalkBack accessibility аудит (не регрессируем, но не оптимизируем сверх default).
- Server-side search.
- Drag-to-reorder folders / drag-to-move notes (только sheet action).

## 3. Architecture overview

### Слои затрагиваемые

| Слой | Файлы |
|---|---|
| Tokens | `core/ui/theme/{Color,Type,Shape,Theme}.kt`, `res/values/colors.xml`, `res/values-night/colors.xml`, `res/values/themes.xml`, `res/font/*` |
| Reusable components | `core/ui/components/Tessera{TopBar,ChipRow,NoteCard,BottomActionBar,Drawer,BottomSheet,Dialog,Snackbar}.kt` + `tesseraShadow` modifier |
| Notes list | `ui/notes/NotesListScreen.kt`, `NotesListViewModel.kt`, `NotesListState.kt`, `ui/notes/components/{NotesTopBar,TagChipRow,BottomActionBar,SwipeableNoteCard,NoteBottomSheet,TesseraDrawer}.kt` |
| Editor | `ui/editor/NoteEditorScreen.kt`, `ui/editor/components/{EditorMeta,EditorFormatBar,RichNoteBody,TableBlockEditor,TagChipsExpandable}.kt`, `res/layout/rich_note_body.xml`, `core/text/MarkdownHtmlConverter.kt` (serif-H1 span) |
| Settings | `ui/settings/SettingsScreen.kt`, `ui/settings/components/{SettingsGroup,SettingsRow,SyncStatusRow,ThemePickerSheet}.kt` |
| Navigation | `ui/navigation/NotesNavGraph.kt` (drawer wrapper) |
| Data | `domain/model/Folder.kt`, `domain/repository/FolderRepository.kt`, `domain/usecase/FolderUseCases.kt`, `data/local/Migration3To4.kt`, `data/local/entity/FolderEntity.kt`, `data/remote/RemoteFolder.kt`, `data/remote/FirestoreFoldersClient.kt`, `data/repository/FolderRepositoryImpl.kt` |
| Sync indicator | `sync/SyncManager.kt` (новый `Flow<SyncIndicator>`), маппер в `ui/settings` |

### Не трогаем

- `data/repository/NotesRepositoryImpl.kt`, `sync/SyncWorker.kt`, `DeleteTombstonesWorker.kt` (поведение)
- `FlickerlessAztecText.kt` (anti-flicker логика)
- `MarkdownBlocks.kt`, `TableGrid.kt` core логика
- `UserPreferencesRepository` (только тривиальное расширение под theme/dynamic/editor font size, если не уже)
- Save/autosave flow, undo/redo

## 4. Tokens

### 4.1 Colors

Mapping 1:1 из `design/mockups/tokens.css`. Полная M3-палитра 30 ролей × {light, dark}:

**Light brand:**
- primary `#C96442` / onPrimary `#FFFFFF` / primaryContainer `#FBE3D8` / onPrimaryContainer `#3A1A0F`
- secondary `#7C5E4F` (harmonized neutral-warm) / containers...
- tertiary `#5F6A4F` (harmonized neutral-green) / containers...
- error `#B3261E` (M3 stock)
- background `#FAF9F5` / onBackground `#262420`
- surface `#FFFFFF` / onSurface `#262420`
- surfaceContainerLowest `#FFFFFF` / Low `#F6F4ED` / default `#F3F0E8` / High `#EDEAE3` / Highest `#E7E3DA`
- onSurfaceVariant `#5C584F` / outline `#8A8478` / outlineVariant `#D9D4C7`
- scrim `rgba(0,0,0,0.32)`
- (extension) shadowWarm `rgba(184,177,166,0.22)` / shadowFloating `rgba(58,26,15,0.18)`

**Dark brand:** зеркально из `tokens.css` (см. `:root[data-theme="dark"]`).

### 4.2 Theme strategy

```kotlin
val scheme = when {
  dynamicColor && SDK >= S -> {
    val dyn = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    dyn.copy(
      // primary/background/surface — ВСЕГДА бренд
      primary = brand.primary,
      onPrimary = brand.onPrimary,
      primaryContainer = brand.primaryContainer,
      onPrimaryContainer = brand.onPrimaryContainer,
      background = brand.background,
      onBackground = brand.onBackground,
      surface = brand.surface,
      onSurface = brand.onSurface,
      // secondary/tertiary/surfaceContainer-* — динамические (M3 гармонизирует от обоев)
    )
  }
  dark -> darkColorScheme(/* full TesseraBrandDark mapping */)
  else -> lightColorScheme(/* full TesseraBrandLight mapping */)
}
```

**Почему не `MaterialColors.harmonize()`:** требует подтянуть `com.google.android.material:material` в Compose-only проект; работает с `@ColorInt`. Стратегия `dyn.copy()` + замена critical ролей даёт тот же эффект проще.

**Crash safety:** `dynamicLightColorScheme`/`dynamicDarkColorScheme` вызовы оборачиваются в try/catch (известные баги на некоторых OEM API 31). Fallback — `TesseraBrandLight/Dark`. Лог в Crashlytics.

### 4.3 Ripple (brand-tinted)

`LocalRippleConfiguration` остаётся дефолтным M3 ripple глобально. На FAB/Chip/Button (брендовые элементы) — через локальный `CompositionLocalProvider`:

```kotlin
val brandRipple = RippleConfiguration(
  color = MaterialTheme.colorScheme.primary,
  rippleAlpha = RippleAlpha(0.10f, 0.12f, 0.16f, 0.20f)
)
```

Существующий `NoIndication`/`LocalRippleConfiguration provides null` из `Theme.kt` — **удаляется**.

### 4.4 XML mirror (для Aztec)

`res/values/colors.xml` + `res/values-night/colors.xml` переписываются под M3-токены. `res/values/themes.xml` — `Theme.Material3.DayNight` base с полным маппингом `colorPrimary/colorOnPrimary/colorBackground/colorSurface/colorOnSurface/colorSurfaceVariant/colorOnSurfaceVariant/colorOutline/colorOutlineVariant` на новые color resources. Aztec/`rich_note_body.xml` тянут цвета через `?attr/...` — синхронизация бесплатна при пересборке Activity.

### 4.5 Typography

**Font resources:**
- `res/font/inter_variable.ttf` (variable, axes wght 100–900)
- `res/font/instrument_serif_regular.ttf`, `instrument_serif_italic.ttf`
- `res/font/inter.xml`, `instrument_serif.xml` — FontFamily resources

**Compose:**

```kotlin
val Inter = FontFamily(
  Font(R.font.inter_variable, FontWeight.W400, variationSettings = ... weight(400)),
  Font(R.font.inter_variable, FontWeight.W500, variationSettings = ... weight(500)),
  Font(R.font.inter_variable, FontWeight.W600, variationSettings = ... weight(600)),
)
val InstrumentSerif = FontFamily(
  Font(R.font.instrument_serif_regular, FontWeight.Normal),
  Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
```

**TesseraTypography — 15 ролей M3** (1:1 из tokens.css; чуть компактнее стандартного M3):
- display{Large,Medium,Small} — Instrument Serif 52/40/32 sp
- headline{Large,Medium} — Instrument Serif 28/24 sp
- headlineSmall — Inter 20 W600
- title{Large,Medium,Small} — Inter 18 W600 / 15 W500 / 13 W600 letterSpacing 0.8
- body{Large,Medium,Small} — Inter 15/13/11
- label{Large,Medium,Small} — Inter W500 13/11 / W600 10 letterSpacing 0.8

**Where each role goes:** см. mapping table в discussion (settings header → headlineLarge serif, drawer header → headlineMedium serif, top-bar title → titleMedium 16sp W600 override, note card title → bodyMedium W600 override, chip → labelMedium, section labels → labelSmall uppercase, editor H1 inline → кастомный Aztec span Instrument Serif 26sp, body paragraph → bodyLarge, editor H2 → titleLarge).

**`letterSpacing = 0sp`** override на body/title (M3 default 0.25-0.5 размазан).

minSdk 26 поддерживает `FontVariation.Settings` — OK.

### 4.6 Shape

```kotlin
val TesseraShapes = Shapes(
  extraSmall = RoundedCornerShape(4.dp),
  small      = RoundedCornerShape(8.dp),
  medium     = RoundedCornerShape(12.dp),
  large      = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(28.dp),
)
object TesseraShape {
  val noteCard       = RoundedCornerShape(14.dp)
  val fab            = RoundedCornerShape(16.dp)
  val fabSmall       = RoundedCornerShape(12.dp)
  val pill           = RoundedCornerShape(999.dp)
  val bottomSheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val formatBarTop   = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
```

### 4.7 Spacing

```kotlin
object TesseraSpacing {
  val xs=4.dp; val sm=8.dp; val md=12.dp; val lg=16.dp
  val xl=20.dp; val xxl=24.dp; val xxxl=32.dp; val xxxxl=40.dp; val xxxxxl=48.dp
}
```

### 4.8 Elevation

**Tonal elevation** автоматическая через `surfaceContainer-*`. Дополнительные **drop shadows** для FAB и плавающего search bar — кастомный модификатор:

```kotlin
fun Modifier.tesseraShadow(elevation: TesseraElevation): Modifier
enum class TesseraElevation(val dp: Dp) {
  Level1(2.dp), Level2(4.dp), Level3(8.dp), Level4(12.dp), Level5(18.dp)
}
```

Цвета теней warm-tinted (light) / pure black (dark) — см. shadowWarm/shadowFloating токены.

### 4.9 Motion

```kotlin
object TesseraMotion {
  val EaseStandard      = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  val EaseEmphasized    = CubicBezierEasing(0.2f, 0f, 0f, 1f)
  val EaseDecelerate    = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
  val DurShort1  = 50.milliseconds
  val DurShort4  = 200.milliseconds
  val DurMedium2 = 300.milliseconds
  val DurMedium4 = 400.milliseconds
}
```

## 5. NotesListScreen

### Layout

```
TopAppBar (56dp): [☰] {selectedFolder?.name ?: "All notes"} · count   ▢  [⋯]
TagChipRow (48dp, sticky): [All] [#work] [#urgent] ...
LazyColumn (padding-bottom 140dp): SwipeableNoteCard×N
BottomActionBar (floating, 52dp + 24dp bottom margin):
  SearchPill (weight 1, pill shape, surfaceContainerHigh, Level2 shadow)
  FAB 52×52 (16dp radius, primaryContainer, Level3 shadow)
```

### Search expand

Тап на SearchPill: pill → full-width bar поверх top-bar (200ms slideUp + fade). FAB исчезает (`AnimatedVisibility scale+fade out`). Под ним — recent searches + filters rows. Контент фильтруется live.

### Drawer (ModalNavigationDrawer)

- Width 304dp, `surface` background
- Header: «Tessera» (headlineMedium Instrument Serif), padding-top `statusBar + 8dp`
- System group: All / Pinned / Archive / Trash (pill items, 24dp radius)
- `FOLDERS` section label (labelSmall primary uppercase) + trailing `[+]`
- LazyColumn folders: root-level + indented children (padding-left 56dp для child)
- Selected folder → `secondaryContainer` background
- Bottom: divider + Settings row + Profile row

### Files

- `NotesListScreen.kt` — переписан, обёртка `ModalNavigationDrawer` снаружи `Scaffold`
- `components/NotesTopBar.kt` — новый
- `components/TagChipRow.kt` — новый, sticky chips с тонким нижним shadow (Level1)
- `components/SwipeableNoteCard.kt` — модифицируется: 14dp radius, 78dp fixed height, pinned-indicator `primary` 3dp vertical bar, swipe actions архивируются `secondary`, удаляются `error`
- `components/BottomActionBar.kt` — новый
- `components/NoteBottomSheet.kt` — замена `NoteContextMenu`: `ModalBottomSheet`, header preview card, sheet-items (Pin, Move to folder, Add tags, Duplicate, Share, Archive, divider, Delete destructive)
- `components/TesseraDrawer.kt` — новый
- `NotesListState.kt` + `NotesListViewModel.kt` — добавляются: `selectedFolder: Folder?`, `tagFilter: String?`, `searchExpanded: Boolean`, `folderTree: List<FolderTreeNode>`. Actions: `selectFolder`, `selectTag`, `expandSearch`, `collapseSearch`, `createFolder`, `createSubfolder`, `moveNoteToFolder`

### Empty state

`headlineSmall override Instrument Serif italic` «No notes here yet» + `bodySmall on-surface-variant` «Tap + to create your first note». Без иллюстрации.

## 6. NoteEditorScreen

### Layout

```
TopAppBar (56dp, icon-only): [←]   ──── empty ────   [☆] [⋯]
EditorMeta (28dp): 📁 Work › Q4 plans      · 2 days ago
TagChipsExpandable (36dp): [#planning] [#priority] [+]   — tertiary-container chips
HorizontalDivider (1dp outlineVariant)
EditorBody (flex, scrollable): RichNoteBody (AndroidView)
  ↳ первая строка — H1 Instrument Serif 26sp (через Aztec span)
  ↳ body paragraphs — Inter 15sp/24
EditorFormatBar (48dp, floating над клавиатурой):
  [B][I][U][S] | [H1][H2] | [•][1.][☐] | [«][</>][—] | [🔗][🖼][📎] [⋯]
```

### Files

- `NoteEditorScreen.kt` — `Scaffold(topBar = TesseraTopBar(...), bottomBar = if (imeVisible) EditorFormatBar(...))`. Между topBar и body — `Column { EditorMeta(); TagChipsExpandable(); HorizontalDivider() }`. Body `RichNoteBody` padding `horizontal=20.dp, bottom=200.dp`.
- `components/EditorMeta.kt` — новый: `Row { FolderIcon; breadcrumb с " › " separator; Spacer; relativeDate }`. bodySmall on-surface-variant.
- `components/EditorFormatBar.kt` — новый, заменяет inline `MarkdownToolbar`. Scrollable Row 36dp button cells, group separators (`1dp × 24dp outlineVariant`), active=`secondaryContainer` 8dp radius. Эмитит те же команды что `MarkdownFormatMenu` (последний остаётся как overflow `⋯` меню — clear formatting / indent / find&replace).
- `components/RichNoteBody.kt` — `setTypeface(ResourcesCompat.getFont(ctx, R.font.inter_variable))`. После Aztec parsing — `applyFirstLineSerifH1(spannable)` накладывает `TypefaceSpan(InstrumentSerif) + AbsoluteSizeSpan(26.sp)` на первый `<h1>`.
- `res/layout/rich_note_body.xml` — background `?attr/colorBackground`, AztecText `textColor=?attr/colorOnSurface`, `textColorHint=?attr/colorOnSurfaceVariant`, `textSize=15sp`, `lineSpacingExtra=4dp`.
- `core/text/MarkdownHtmlConverter.kt` — добавляется `applyFirstLineSerifH1` helper. Если первая строка не H1 — no-op.
- `components/TableBlockEditor.kt` — заменить hardcoded colors на `MaterialTheme.colorScheme.{surfaceContainer, outlineVariant}`. Behavior не трогаем.
- Quote span: новый `TesseraQuoteSpan` (left bar 3dp `primary`, padding-start 12dp, italic InstrumentSerif).
- Checklist: checked-fill = `primary`, через `colors.xml`.

### Format-bar IME-aware

```kotlin
val imeVisible = WindowInsets.isImeVisible
AnimatedVisibility(
  visible = imeVisible && !isReadOnly,
  enter = slideInVertically(tween(200, TesseraMotion.EaseDecelerate)) { it },
  exit  = slideOutVertically(tween(150, TesseraMotion.EaseStandard))   { it },
) { EditorFormatBar(...) }
```

### Empty state

H1 placeholder Instrument Serif italic 45% opacity «Untitled note»; body Inter 15sp 45% opacity «Start typing…».

## 7. SettingsScreen

### Layout

```
LargeTopAppBar (collapsible)
  [←]   (small row 56dp)
  Settings   (Instrument Serif headlineLarge 28sp)

[ACCOUNT]  — section label primary 10sp uppercase
  profile-card (surfaceContainer 16dp radius, 16dp padding):
    [VG avatar 44dp primaryContainer]  Vlad Gareev  /  email  [Sign out]

[SYNC]
  SyncStatusRow: leading icon (primary if ok), title "Synced · just now", subtitle "Last sync 2 sec ago"

[APPEARANCE]
  Theme — System | trailing chevron → ThemePickerSheet
  Dynamic color — switch (visible if SDK >= 31)
  Editor font size — Medium | trailing chevron

[DATA]
  Export notes ›
  Import... ›
  Empty trash › (destructive)

[ABOUT]
  Version 1.4.2 (340)
  Open source licenses ›
```

### Files

- `SettingsScreen.kt` — переписан с `LargeTopAppBar` + `LazyColumn` секций. Account top.
- `components/SettingsGroup.kt` — Column 16dp radius surfaceContainer, auto-divider.
- `components/SettingsRow.kt` — `leadingIcon, title, subtitle?, trailing: { Value | Switch | Chevron }, onClick`.
- `components/SyncStatusRow.kt` — special `SettingsRow` подкейс:
  ```kotlin
  sealed class SyncIndicator {
    data class Ok(val lastSync: Instant) : SyncIndicator()
    data object Syncing : SyncIndicator()
    data object Offline : SyncIndicator()
    data class Error(val message: String, val onRetry: () -> Unit) : SyncIndicator()
  }
  ```
  State из нового `SyncManager.syncStatusFlow: Flow<SyncIndicator>`.
- `components/ThemePickerSheet.kt` — `ModalBottomSheet`: System/Light/Dark radio rows + «Use dynamic color» switch (SDK gated) + preview card.

### Switch

Стандартный `androidx.compose.material3.Switch`. Override `checkedTrackColor = primary, uncheckedTrackColor = surfaceContainerHighest` для соответствия mockup.

## 8. Folders data layer

### Domain

```kotlin
data class Folder(
  val id: FolderId,
  val name: String,
  val parentId: FolderId?,  // null = root
  val createdAt: Long,
)
```

### Migration 3→4

```sql
ALTER TABLE folders ADD COLUMN parent_id TEXT REFERENCES folders(id) ON DELETE SET NULL;
CREATE INDEX idx_folders_parent ON folders(parent_id);
```

`ON DELETE SET NULL` — удаление родителя поднимает детей в root, не каскадно.

### Validation

- 2-level max depth (root + 1 child level)
- Циклы запрещены (`a.parent != a`, `a.parent.parent != a`)
- В UI: «New subfolder» на child folder — disabled (38% opacity)

### Firestore

`RemoteFolder.parentId: String? = null`. Отсутствие поля → root. Старые версии приложения игнорируют поле. Конфликты при одновременном move — last-write-wins (текущее поведение sync).

### Use cases

- `MoveFolderUseCase(folderId, newParentId)` — с depth/cycle validation, возвращает `Result.failure(ValidationException)` при нарушении.
- `CreateSubfolderUseCase(parentId, name)` — с depth ≤ 2 check.
- `folderTreeFlow(): Flow<List<FolderTreeNode>>` — flat → tree (root + 1 уровень детей).

## 9. Error handling

- **Migration3To4 fail:** Room callback try/catch. Без `fallbackToDestructiveMigrationFrom(3)` — лучше crash + Crashlytics, чем потеря данных. Тщательно тестируется заранее.
- **Folder cycle / depth violation:** `Result.failure` → Snackbar «Can't move folder here». Без техдеталей.
- **Firestore parentId race:** last-write-wins, не блокирует UI.
- **Dynamic color crash на OEM:** try/catch вокруг `dynamicLightColorScheme/dynamicDarkColorScheme`. Fallback `TesseraBrandLight/Dark`. Crashlytics лог.
- **Font load failure:** Compose fallback к system sans-serif автоматически.

## 10. Migration & rollout

- Room version 3→4. Тестируется через `MigrationTestHelper`.
- Firestore back-compat: старые folder без `parentId` → root.
- Нет feature flags. Pet-project, минорная аудитория.
- Asset size: Inter Variable ~340 KB + Instrument Serif ~70 KB = +410 KB к APK (текущий ~12 MB).

## 11. Implementation order (для writing-plans)

1. **Tokens foundation:** Color.kt / Type.kt / Shape.kt / Theme.kt + colors.xml + themes.xml + font resources.
2. **Reusable composables:** TesseraTopBar, TesseraChipRow, TesseraNoteCard, TesseraBottomActionBar, TesseraDrawer, TesseraBottomSheet, TesseraDialog, TesseraSnackbar, tesseraShadow modifier.
3. **Folders data:** Migration3To4, Folder.parentId, FolderRepository updates, use cases.
4. **NotesListScreen redesign:** drawer + sticky chips + bottom action bar + search expand.
5. **NoteEditorScreen redesign:** TopAppBar + EditorMeta + inline tags + format-bar + RichNoteBody res ретонинг + serif-H1 span.
6. **SettingsScreen redesign:** SettingsGroup + SyncStatusRow + ThemePickerSheet.
7. **Update existing tests + add new tests** (см. §12).
8. **Manual QA pass** (см. §13).

## 12. Testing

### Unit

- `FolderRepositoryImplTest` — depth validation, cycle prevention, ON DELETE SET NULL.
- `MoveFolderUseCaseTest` — все edge cases (root→child, child→root, child→same parent no-op, descendant of self → reject).
- `Migration3To4Test` — `MigrationTestHelper` end-to-end.
- `SyncIndicatorMapperTest` — `SyncManager` state → `SyncIndicator` с throttling «just now» / «N sec ago» / «N min ago».
- Существующие `MarkdownHtmlConverterTest`, `MarkdownBlocksTest` — должны проходить без правок.

### Compose UI (`androidTest`)

- `NotesListScreenTest` (существующий) — апдейт: drawer toggle, sticky chips visible, FAB hides on search expand.
- Новые: `NotesListDrawerTest`, `EditorFormatBarTest`, `BottomActionBarSearchExpandTest`, `SettingsSyncIndicatorTest`, `TesseraShowcaseScreenTest` (refresh всех новых композов).

### Не входит

Golden screenshot CI (Paparazzi/Roborazzi) — отдельный future spec.

## 13. Manual QA checklist

- Dynamic color on (API 31+): primary терракотовый, secondary/surface — обои-tinted.
- Dynamic color off (API <31): полная бренд-палитра.
- Force dark / battery saver — text legibility OK всех экранов.
- Font scale ×0.85 / ×1.0 / ×1.3 / ×2.0 — note-card 78dp fixed, title/preview `maxLines=1 overflow=Ellipsis` не клиппится визуально неожиданно.
- Network off → indicator «Offline»; обратно → «Syncing» → «Synced · just now».
- Migration from existing app: установлена prod-версия с 3 папками без `parentId` → upgrade → все папки видны в drawer, ничего не потеряно.
- Folder move: root→child OK, child→другой-root OK, child→собственный потомок rejected с Snackbar.
- Editor: первая строка автоматически serif H1; формат-бар появляется только при открытой клавиатуре; tap [B]/[I] toggle работает.
- Drawer: swipe-from-left открывает drawer, scrim tap закрывает, selected folder подсвечен.

## 14. Open follow-ups (not in this spec)

- RTL polish QA pass.
- Golden screenshot CI.
- Drag-to-reorder folders + drag-to-move notes (gesture-based).
- TalkBack accessibility аудит.
- Tablet/foldable layouts.
- Иллюстрации empty states (если решим, что только типографики мало).
