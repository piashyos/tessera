# Tessera for macOS — Спецификация порта

> Полный нативный порт Android-приложения **Tessera** (заметки) на macOS.
> Технологии: **Swift 6 / SwiftUI**, по всем гайдлайнам Apple (macOS Human Interface Guidelines).
> Источник: Android-приложение `app.tessera` v0.4.1 (Room schema v6, Firebase/Firestore backend).

---

## 0.1. Документы спецификации

| № | Файл | Содержание |
|---|------|-----------|
| 00 | `00-overview.md` | Этот файл. Цели, scope, стек, матрица паритета фич. |
| 01 | `01-architecture.md` | Архитектура приложения, слои, структура проекта, DI. |
| 02 | `02-data-model-and-persistence.md` | Локальная БД (SwiftData), сущности, миграции, маппинг из Room. |
| 03 | `03-backend-sync-auth-sharing.md` | Firebase, схема Firestore, security rules, авторизация, движок синхронизации, шеринг. |
| 04 | `04-rich-text-editor.md` | Редактор, Markdown↔AttributedString, форматирование, таблицы, теги. |
| 05 | `05-ui-screens-navigation.md` | Все экраны, навигация, sidebar, список, редактор, поиск, настройки. |
| 06 | `06-design-system.md` | Цвета, типографика, отступы, формы, компоненты, адаптация под HIG. |
| 07 | `07-macos-platform.md` | Меню-бар, горячие клавиши, окна, drag&drop, системные интеграции. |
| 08 | `08-implementation-roadmap.md` | Фазовый план реализации, вехи, тестирование, критерии приёмки. |

---

## 0.2. Что такое Tessera

**Tessera** — local-first приложение для заметок с облачной синхронизацией и совместным редактированием.

**Слоган:** «Local-first notes, ready for search and quick capture.»

**Ключевые свойства:**
- **Local-first.** Всё работает офлайн; синхронизация — фоновая и опциональная (требует входа).
- **Rich-text заметки** в Markdown (заголовки, списки, чек-листы, цитаты, код, таблицы, инлайн-стили).
- **Организация:** папки (один уровень вложенности), теги, закрепление, архив, корзина.
- **Полнотекстовый поиск** с фильтрами по папке, тегу и периоду.
- **Совместный доступ:** заметку можно расшарить, пригласить участников по email, назначать роли (Owner/Manager/Editor/Viewer).
- **Синхронизация:** Google-аккаунт → Firebase Auth + Cloud Firestore.

---

## 0.3. Цель порта

Полное **1:1 копирование функционала** Android-приложения в нативное macOS-приложение, при этом:

1. **Сохранить общий бэкенд.** macOS-клиент работает с тем же проектом Firebase (`notes-d3abc`), той же схемой Firestore и теми же security rules. Android и macOS клиенты синхронизируют один и тот же набор заметок пользователя без изменений на сервере.
2. **Сохранить формат данных.** Канонический формат тела заметки — Markdown (тот же диалект, что в Android, см. doc 04). Это обеспечивает кросс-платформенную совместимость контента.
3. **Адаптировать UX под macOS.** Не копировать Android-паттерны буквально (FAB, bottom sheet, свайпы), а реализовать их идиоматичные эквиваленты для Mac (toolbar, sidebar, контекстные меню, меню-бар, горячие клавиши) — см. doc 07.
4. **Следовать Apple HIG.** Нативные элементы AppKit/SwiftUI, поддержка светлой/тёмной темы, динамический размер окна, split-view, доступность (VoiceOver, Dynamic Type, full keyboard access).

---

## 0.4. Технологический стек

| Слой | Android (источник) | macOS (цель) |
|------|--------------------|--------------|
| Язык | Kotlin 2.0 | **Swift 6** (strict concurrency) |
| UI | Jetpack Compose + Material 3 | **SwiftUI** (+ AppKit через `NSViewRepresentable` где нужно) |
| Локальная БД | Room (SQLite) v6 | **SwiftData** (SQLite-backed) |
| Полнотекстовый поиск | Room FTS4 | **SQLite FTS5** через прямой доступ или SwiftData `#Predicate` + индекс |
| Реактивность | Kotlin Flow / StateFlow | **Swift Observation** (`@Observable`) + `AsyncStream` / Combine |
| DI | Hilt | **Ручной композиционный root** + `@Environment` (или Factory/swift-dependencies) |
| Auth | Credential Manager + Google ID | **GoogleSignIn SDK (Firebase)** + `ASWebAuthenticationSession` fallback |
| Backend SDK | firebase-auth, firebase-firestore (KTX) | **Firebase Apple SDK** (FirebaseAuth, FirebaseFirestore) — официально поддерживает macOS |
| Фоновые задачи | WorkManager | **Структурированная конкуренция** (`Task`) + `NSBackgroundActivityScheduler` для периодической очистки |
| Rich-text движок | AztecText (WordPress) | **`NSTextView` + custom markdown engine** (`NSViewRepresentable`), см. doc 04 |
| Шрифты | Inter + Instrument Serif (бандл) | Те же шрифты в бандле (или SF Pro + New York как fallback) |
| Иконки | Lucide | **SF Symbols** (маппинг 1:1, см. doc 06) |

**Минимальная версия:** macOS **14.0 (Sonoma)** — требуется для зрелого SwiftData и современного SwiftUI. Рекомендуется таргетировать также 15.0 для отдельных улучшений (см. заметки в doc 04/05).

**Сборка:** Xcode 16+, Swift Package Manager для зависимостей (Firebase, GoogleSignIn). Bundle ID: `app.tessera.mac` (или `app.tessera` если шарить App Group с будущим iOS-таргетом).

---

## 0.5. Матрица паритета функционала

Полный перечень фич, которые порт обязан воспроизвести. Колонка «Doc» указывает, где описана детальная спека.

### Управление заметками
| Фича | Описание | Doc |
|------|----------|-----|
| Создание заметки | Пустой черновик; первый ввод сохраняет | 02, 05 |
| Авто-заголовок | Title выводится из первой строки content | 02, 04 |
| Автосохранение | Debounce 500ms; flush при закрытии | 04 |
| Soft delete | `deletedAt` ставится; уходит в Корзину | 02 |
| Восстановление | Из Корзины обратно в список | 02, 05 |
| Permanent delete | Полное удаление (локально + remote) | 02, 03 |
| Удаление пустой заметки | content стал пустым → permanent delete | 02, 04 |
| Закрепление (pin) | `isPinned`, отдельная секция сверху | 02, 05 |
| Архив | `isArchived`, отдельный раздел | 02, 05 |
| Дублирование | Копия заметки | 05 |
| Корзина с авто-очисткой | Tombstones > 30 дней удаляются | 03 |

### Организация
| Фича | Описание | Doc |
|------|----------|-----|
| Папки | Один уровень вложенности; уникальное имя | 02, 05 |
| Подпапки | parentId; дерево | 02, 05 |
| Одна папка на заметку | UNIQUE(noteId) | 02 |
| Переименование/перемещение папки | rename / updateParent | 02 |
| Удаление папки | soft-delete + soft-delete вложенных заметок | 02 |
| Теги | Уникальные глобально, нормализация (trim/dedupe/sort) | 02, 04 |
| Назначение/снятие тега | На заметку, many-to-many | 02, 04 |
| Фильтр по тегу | Single-select в списке и поиске | 05 |

### Поиск
| Фича | Описание | Doc |
|------|----------|-----|
| Полнотекстовый поиск | FTS по title+content, prefix-match | 02, 05 |
| Фильтр: папка | All / конкретная | 05 |
| Фильтр: тег | — / конкретный | 05 |
| Фильтр: период | All / Today / Week / Month | 05 |
| Недавние запросы | До 8, most-recent-first | 02, 05 |
| Популярные теги | Подсказки | 05 |

### Rich-text редактор
| Фича | Описание | Doc |
|------|----------|-----|
| Инлайн: bold, italic, strikethrough, underline, code | Markdown `**`,`*`,`~~`,`` ` ``, `<u>` | 04 |
| Блоки: H1, H2 (toolbar H1–H6) | `#`, `##` | 04 |
| Списки: bullet, ordered, task | `-`, `1.`, `- [ ]`/`- [x]` | 04 |
| Цитата | `>` (multiline) | 04 |
| Код-блок | ``` ``` ``` fenced | 04 |
| Горизонтальная линия | `---` | 04 |
| GFM-таблицы | с метаданными ширин/высот в HTML-комменте | 04 |
| Панель форматирования | toolbar с активными состояниями | 04 |
| Undo/redo | нативный | 04 |
| Serif H1 (первая строка) | Instrument Serif | 04, 06 |

### Шеринг и роли
| Фича | Описание | Doc |
|------|----------|-----|
| Make shared | private → sharedNotes/{id} | 03 |
| Make private (unshare) | обратно (только owner) | 03 |
| Invite by email | lookup в usersByEmail | 03 |
| Роли | OWNER/MANAGER/EDITOR/VIEWER | 03 |
| Change role | manager/owner | 03 |
| Remove member | manager/owner | 03 |
| Leave note | non-owner | 03 |
| Delete for everyone | owner/manager | 03 |
| Read-only для Viewer | редактор без ввода | 03, 04 |
| Live-обновления участников | Firestore listener | 03 |
| Аватары участников на карточке | стек | 05 |

### Синхронизация и аккаунт
| Фича | Описание | Doc |
|------|----------|-----|
| Google Sign-In | Firebase Auth | 03 |
| Sign out | очистка сессии | 03 |
| Push/Pull/Merge | last-write-wins по updatedAt | 03 |
| Dirty tracking | `syncedAt == nil` | 02, 03 |
| Realtime listeners | notes / folders / shared | 03 |
| Role-downgrade rollback | откат неавторизованных правок | 03 |
| Adoption локальных заметок | ownerId=nil → uid при первом входе | 03 |
| Account isolation | очистка чужих данных при смене аккаунта | 03 |
| Статус синка | Synced/Syncing/Offline/Error + Last synced | 05 |

### Оформление и локализация
| Фича | Описание | Doc |
|------|----------|-----|
| Тема: Auto/Light/Dark | системная/свет/тьма | 06 |
| Брендовая палитра (терракота) | light + dark схемы | 06 |
| Динамический цвет | Android-only → **на macOS опускаем** (нет обоев-API); см. 06 | 06 |
| Язык: System/EN/RU | переключение | 05 |
| Относительные даты | Today/Yesterday/«2 дек» | 05 |

### Заглушки в Android (не реализованы — на macOS см. рекомендации)
| Фича | Статус | Doc |
|------|--------|-----|
| Export notes | placeholder в Android | 05, 08 |
| Import | placeholder | 05, 08 |
| Empty trash (кнопка) | placeholder | 05, 08 |
| Open source licenses | экран | 05 |

---

## 0.6. Принципы порта (важно)

1. **Бэкенд неизменен.** Любое изменение схемы Firestore или правил ломает Android-клиент. Порт только читает/пишет по существующему контракту (doc 03).
2. **Markdown — система записи.** Редактор macOS обязан читать и писать ровно тот же Markdown-диалект, что и Android (doc 04). HTML — внутренняя эфемерная форма движка, на диск/в сеть не уходит.
3. **Чистая бизнес-логика переносится напрямую.** Алгоритмы синка, разрешения ролей, нормализация тегов, деривация заголовка — это pure-функции; переписываем на Swift 1:1 (doc 02/03).
4. **UI — идиоматичный для Mac.** Не порт Compose-вёрстки, а пересборка под HIG с сохранением информационной архитектуры и всех действий (doc 05/07).
5. **Тесты сохраняют семантику.** Юнит-тесты на синк/merge/permissions портируем как контрольные (doc 08).

---

## 0.7. Идентичность приложения

- **Название:** Tessera
- **Версия порта:** начинаем с `1.0.0` (macOS), отдельная от Android-нумерации.
- **Bundle ID:** `app.tessera.mac`
- **Иконка:** портировать из Android (есть монохромный вариант) в `.icns` со всеми размерами (16–1024) для Mac.
- **Категория App Store:** Productivity.
- **Sandbox:** App Sandbox включён; entitlements — network client, keychain, (опц.) iCloud/App Group в будущем.
