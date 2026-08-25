# 06 — Дизайн-система

> Точный перенос брендовой палитры, типографики, отступов и форм Tessera. Material 3 → SwiftUI/AppKit-эквиваленты. Цель — визуальная идентичность с Android при соблюдении macOS HIG.

---

## 6.1. Брендовая палитра

Терракотовый бренд («warm rust»). Две схемы — light и dark. Значения переносим **точно** (HEX).

### Light (TesseraBrandLight)
| Роль | HEX |
|------|-----|
| primary | `#C96442` |
| onPrimary | `#FFFFFF` |
| primaryContainer | `#FBE3D8` |
| onPrimaryContainer | `#3A1A0F` |
| secondary | `#7C5E4F` |
| onSecondary | `#FFFFFF` |
| secondaryContainer | `#F5E1D6` |
| onSecondaryContainer | `#2F1E14` |
| tertiary | `#5F6A4F` |
| onTertiary | `#FFFFFF` |
| tertiaryContainer | `#E2EAD3` |
| onTertiaryContainer | `#1B2412` |
| error | `#B3261E` |
| errorContainer | `#F9DEDC` |
| onErrorContainer | `#410E0B` |
| background | `#FAF9F5` |
| onBackground | `#262420` |
| surface | `#FFFFFF` |
| onSurface | `#262420` |
| outline | `#8A8478` |

### Dark (TesseraBrandDark)
| Роль | HEX |
|------|-----|
| primary | `#D97757` |
| onPrimary | `#3A1A0F` |
| primaryContainer | `#5C2A18` |
| onPrimaryContainer | `#FBE3D8` |
| secondary | `#D8B8A2` |
| onSecondary | `#3A2818` |
| secondaryContainer | `#523929` |
| onSecondaryContainer | `#F5E1D6` |
| tertiary | `#B7C6A1` |
| onTertiary | `#1F2A12` |
| tertiaryContainer | `#3D4928` |
| onTertiaryContainer | `#E2EAD3` |
| error | `#F2B8B5` |
| errorContainer | `#8C1D18` |
| onErrorContainer | `#F9DEDC` |
| background | `#191815` |
| onBackground | `#ECE9E3` |
| surface | `#201F1D` |
| onSurface | `#ECE9E3` |

> Дополнительные surface-тона (`surfaceContainerLow/High`, lightness 0.84–0.93 в light; тёмные charcoal в dark) — вывести по тем же принципам Material; задать явные значения в asset-каталоге.

### Реализация в SwiftUI
- Завести **Color Set** в `Assets.xcassets` для каждой роли с вариантами Any/Dark Appearance → автоматическое переключение.
- Обёртка `enum TesseraColor` с `Color` computed-свойствами (`.primary`, `.onPrimary`, `.surface`, …), читающими из asset-каталога.
- Тема (Auto/Light/Dark) применяется через `.preferredColorScheme()` на корневой сцене в зависимости от `themeMode` (см. §6.6).

### Динамический цвет (Material You) — НЕ переносим
Android поддерживает извлечение цвета из обоев (Android 12+). На macOS системного API обоев-палитры нет → **функцию опускаем**. Всегда используем брендовую палитру. (Соответствует решению в doc 00/05.) Опционально — поддержать системный accent color пользователя только для отдельных control-tint, но по умолчанию бренд.

---

## 6.2. Типографика

Шрифты: **Inter** (variable) для основного текста и **Instrument Serif** для крупных заголовков-дисплеев и H1 первой строки в редакторе. Бандлим оба (как Android). Fallback: SF Pro / New York.

### Serif (Instrument Serif)
| Стиль | Размер | Line height |
|-------|--------|-------------|
| displayLarge | 52 | 58 |
| displayMedium | 40 | 48 |
| displaySmall | 32 | 40 |
| headlineLarge | 28 | 36 |
| headlineMedium | 24 | 32 |

### Sans (Inter)
| Стиль | Размер | Weight |
|-------|--------|--------|
| headlineSmall | 20 | 600 |
| titleLarge | 18 | 600 |
| titleMedium | 15 | 500 |
| titleSmall | 13 | 600 |
| bodyLarge | 15 | 400 |
| bodyMedium | 13 | 400 |
| bodySmall | 11 | 400 |
| labelLarge | 13 | 500 |
| labelMedium | 11 | 500 |
| labelSmall | 10 | 600 |

### Реализация
```swift
enum TesseraFont {
    static func serif(_ size: CGFloat) -> Font { .custom("InstrumentSerif-Regular", size: size) }
    static func sans(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font { .custom("Inter", size: size).weight(weight) }
    static let titleMedium = sans(15, .medium)
    static let bodyLarge   = sans(15)
    // … все стили
}
```
- Регистрация шрифтов в Info.plist (`ATSApplicationFontsPath` или bundled fonts).
- Для редактора (`NSTextView`) — `NSFont` с теми же семействами (см. doc 04).
- Поддержать масштабирование (Dynamic Type) через `Font.custom(_, size:, relativeTo:)`.

---

## 6.3. Отступы (4pt сетка)

| Токен | pt |
|-------|----|
| xs | 4 |
| sm | 8 |
| md | 12 |
| lg | 16 |
| xl | 20 |
| xxl | 24 |
| xxxl | 32 |
| xxxxl | 40 |
| xxxxxl | 48 |

```swift
enum Spacing { static let xs:CGFloat=4, sm=8, md=12, lg=16, xl=20, xxl=24, xxxl=32, xxxxl=40, xxxxxl=48 }
```

---

## 6.4. Формы (Shapes)

Material 3 + кастомные семантические:
| Токен | Радиус |
|-------|--------|
| extraSmall | 4 |
| small | 8 |
| medium | 12 |
| large | 16 |
| extraLarge | 28 |
| **noteCard** | 14 |
| **fab** | 16 (на Mac — не используется, оставить для совместимости компонентов) |
| **pill** | 999 (capsule) |
| bottomSheetTop | 28 (на Mac → sheet/inspector скругление системное) |
| formatBarTop | 16 |

```swift
enum TesseraShape {
    static let noteCard = RoundedRectangle(cornerRadius: 14)
    static let pill = Capsule()
    // …
}
```

---

## 6.5. Иконки: Lucide → SF Symbols

Android использует Lucide. На macOS — **SF Symbols** (нативно, поддержка веса/масштаба/тонирования). Маппинг ключевых:

| Lucide (Android) | SF Symbol | Контекст |
|------------------|-----------|----------|
| Menu | `sidebar.left` | toggle sidebar |
| Inbox | `tray` | All Notes |
| Pin | `pin` / `pin.fill` | Pinned / pin toggle |
| Archive | `archivebox` | Archive |
| ArchiveRestore | `archivebox.fill` / `arrow.up.bin` | Unarchive/Restore |
| Trash / Trash2 | `trash` | Trash/Delete |
| Users | `person.2` | Shared / members |
| Folder | `folder` | Folder |
| FolderInput | `folder.badge.plus` / `arrow.right.doc.on.clipboard` | Move to folder |
| Plus | `plus` | New folder/note |
| Tag | `tag` | Tags |
| ListChecks | `checklist` | Select / task list |
| Copy | `doc.on.doc` | Duplicate |
| Search | `magnifyingglass` | Search |
| ArrowLeft | `chevron.backward` | Back |
| EllipsisVertical | `ellipsis` / `ellipsis.circle` | Overflow menu |
| Palette | `paintpalette` | Theme |
| Languages | `globe` | Language |
| Download | `square.and.arrow.down` | Export |
| Upload | `square.and.arrow.up` | Import |
| Scale | `text.book.closed` | Licenses |
| Bold/Italic/Underline/Strikethrough | `bold` / `italic` / `underline` / `strikethrough` | Format |
| Heading | `textformat.size` / `textformat` | Headings |
| List | `list.bullet` / `list.number` / `checklist` | Lists |
| Quote | `text.quote` | Quote |
| Code | `chevron.left.forwardslash.chevron.right` | Code |
| Minus | `minus` | HR |
| Table | `tablecells` | Insert table |
| CloudCheck/Sync | `checkmark.icloud` / `arrow.triangle.2.circlepath` | Sync status |
| CloudOff | `bolt.horizontal.icloud` / `wifi.slash` | Offline |

```swift
enum Icon { static let allNotes="tray", pinned="pin", archive="archivebox", trash="trash",
            shared="person.2", folder="folder", newNote="square.and.pencil", search="magnifyingglass",
            overflow="ellipsis", bold="bold", italic="italic" /* … */ }
```

---

## 6.6. Применение темы

```swift
enum ThemeMode: String { case AUTO, LIGHT, DARK }

// На корневой сцене:
RootView()
  .preferredColorScheme(themeMode == .AUTO ? nil
                        : themeMode == .DARK ? .dark : .light)
```
- `.AUTO` → `nil` (следовать системе).
- Цвета берутся из asset-каталога с Dark Appearance вариантами → переключаются автоматически.
- `ThemeMode.resolveDark(isSystemDark:)` — для случаев, когда нужно явно знать (например, для `NSTextView`-атрибутов в редакторе).

---

## 6.7. Компоненты (переиспользуемые)

| Android-компонент | macOS-эквивалент |
|-------------------|------------------|
| TesseraNoteCard | `NoteCardView` (RoundedRectangle 14, surface/secondaryContainer, selection border) |
| TesseraChip | `TagChipView` (Capsule, secondaryContainer) |
| TesseraTopBar | window `.toolbar` |
| TesseraBottomSheet | `.sheet` / `Popover` / Inspector |
| TesseraSheetItem | `Button` в Menu/List с иконкой+лейблом |
| MemberAvatarStack | `AvatarStackView` (ZStack кругов с overlap) |
| FloatingCapsule (tag) | Capsule + анимация |
| SyncStatusRow | `SyncStatusView` |
| BottomActionBar (FAB+search) | toolbar buttons |

Аватары: `AsyncImage` (Coil → AsyncImage) с fallback-инициалами; кэш — системный URLCache.

---

## 6.8. Анимации

- Переходы колонок/выбора — системные SwiftUI.
- Чипы тегов — `.transition(.scale.combined(with: .opacity))` (как spring в Android).
- Tag filter row — expand/collapse `.transition`.
- Уважать **Reduce Motion** (`accessibilityReduceMotion`).

---

## 6.9. Контрольный список дизайн-системы

- [ ] Color Sets (light+dark) для всех ролей в Assets.xcassets.
- [ ] `TesseraColor` обёртка.
- [ ] Бандл Inter + Instrument Serif; `TesseraFont` со всеми стилями.
- [ ] `Spacing`, `TesseraShape`.
- [ ] `Icon` маппинг Lucide→SF Symbols.
- [ ] Применение темы (preferredColorScheme), без dynamicColor.
- [ ] Переиспользуемые компоненты (NoteCard, TagChip, AvatarStack, SyncStatus).
- [ ] Поддержка Reduce Motion / Dynamic Type / контраст.
