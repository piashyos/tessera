# Tessera — Android Notes App Design Spec

**App name:** Tessera (a mosaic piece — small notes that form a bigger picture)
**Package / applicationId:** `app.tessera`

**Date:** 2026-06-14
**Status:** Draft (pending review)

## 1. Цель и принципы

Простое и быстрое нативное Android-приложение для заметок:
- **Local-first**: работает без аккаунта, все данные в Room.
- **Опциональный облачный синк**: при входе через Google (Firebase Auth) заметки начинают синхронизироваться через Firestore между устройствами того же аккаунта.
- **Современный визуальный язык**: floating-капсулы для контролов с blur и сплошным светлым бордером (упрощённая адаптация iOS 26 без liquid glass).
- **Богатая анимация** там, где она объясняет связь состояний.

## 2. Стек

| Слой | Технологии |
|------|-----------|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| State | ViewModel + StateFlow |
| Локальная БД | Room (с FTS4 для поиска) |
| DI | Hilt |
| Auth | Firebase Auth (Google Sign-In через Credential Manager API) |
| Cloud | Cloud Firestore |
| Background | WorkManager |
| Markdown rendering | Markwon (через `AndroidView`) |
| Min SDK | API 26 (`Modifier.blur` доступен с API 31 — на ниже используется полупрозрачная заливка без blur) |
| Target SDK | 35 |

## 3. Архитектура

Один Gradle-модуль (`:app`), слои:

- **UI layer** — Compose-экраны, ViewModels, мапперы UI-state.
- **Domain layer** — модели (`Note`, `Tag`), use-cases (CreateNote, UpdateNote, DeleteNote, TogglePin, SearchNotes, SignIn, SignOut, Sync).
- **Data layer** — `NotesRepository`, `SyncRepository`, Room DAO, Firestore client. Repository прячет источники: UI всегда читает из Room (через Flow), запись идёт в Room, потом в фоне — в Firestore.

**Принцип**: Room — source of truth. Firestore — реплика для синка. UI никогда не ждёт сеть.

## 4. Визуальный язык

- **Фон**: системный фон + мягкий тёплый/холодный тинт по теме.
- **Floating капсулы** (top bar, FAB, bottom action bar) — отдельные `Surface` с `RoundedCornerShape(28.dp)`, поверх контента:
  - blur заднего слоя (`Modifier.blur` для снапшота фона на API 31+, fallback — полупрозрачная заливка)
  - полупрозрачная заливка surface (`alpha 0.6–0.75`)
  - **сплошной светлый бордер** `Color.White.copy(alpha = 0.15–0.25f)` толщиной 1dp
  - мягкая тень `elevation 8–12dp`
- **Карточки заметок**: tinted surface, скруглённые 20dp, лёгкая тень.
- **Material 3 dynamic color** (Android 12+) с manual override System/Light/Dark.

## 5. Данные

### 5.1 Room (локально)

**`notes`**
| field | type | note |
|-------|------|------|
| id | String (PK) | UUID |
| title | String | |
| content | String | markdown (`[ ]` / `[x]` для чекбоксов) |
| isPinned | Boolean | |
| createdAt | Long | epoch ms |
| updatedAt | Long | epoch ms, ключ LWW-мержа |
| deletedAt | Long? | soft delete; tombstone |
| syncedAt | Long? | `null` = есть локальные изменения для пуша |
| ownerId | String? | `null` = локальная (до логина); иначе Firebase uid |

**`tags`** (`id`, `name`) + **`note_tags`** (`noteId`, `tagId`) — many-to-many.

**`notes_fts`** — FTS4 виртуальная таблица по `title + content`. Синхронизируется с `notes` через SQL-триггеры (`AFTER INSERT/UPDATE/DELETE`), декларированные в Room через `@Fts4(contentEntity = NoteEntity::class)`.

**Индексы**: `(ownerId, updatedAt)`, `(isPinned, updatedAt)`, `(deletedAt)`.

### 5.2 Firestore

```
users/{uid}/notes/{noteId}
  title: String
  content: String
  isPinned: Boolean
  tags: [String]            // массив имён тегов
  createdAt: Number
  updatedAt: Number
  deleted: Boolean
```

Тегов отдельной коллекцией нет — `tags` хранится массивом на документе заметки.

### 5.3 Sync (LWW по `updatedAt`)

1. **На логине**: SyncWorker делает первичный мерж. Тянет всё из Firestore. Для каждой заметки сравнивает `updatedAt` между Room и Firestore — побеждает свежий. Локальные заметки с `ownerId = null` получают текущий uid и пушатся в облако.
2. **Steady state**: Firestore listener на `users/{uid}/notes` пишет изменения в Room. Локальные правки помечаются `syncedAt = null` и WorkManager пушит их в Firestore.
3. **Удаление**: soft delete (`deletedAt != null` локально, `deleted: true` в Firestore). Удаление имеет приоритет, если его `updatedAt` свежее.
4. **Тумбстоуны**: `DeleteTombstonesWorker` (ежедневный) чистит записи с `deletedAt` старше 30 дней.
5. **Race между listener и локальной правкой**: при получении снапшота из Firestore SyncRepository перед записью в Room сравнивает `remote.updatedAt` с локальным `updatedAt`. Если локальный свежее (юзер только что отредактировал) — снапшот игнорируется, локальная версия будет запушена следующим pending-write'ом.

## 6. Экраны и навигация

Navigation Compose, 3 destination: `notes`, `editor/{noteId?}`, `settings`.

### 6.1 NotesListScreen
- **Floating top capsule**: профиль-иконка (тап → Settings) + заголовок "Заметки".
- **Контент**: `LazyVerticalStaggeredGrid` (2 колонки). Сначала секция "Pinned", затем "Notes". Карточка = title + 3–4 строки plain-text превью + чипы тегов.
- **Floating bottom capsule (центр)**: поле поиска (тап раскрывает) + фильтр по тегу.
- **Floating FAB (правый низ)**: "+" — новая заметка.
- **Pull-to-refresh** сверху — ручной синк.
- **Свайп влево** на карточке — раскрывает action-слоты ⭐ Pin и 🗑 Delete; длинный свайп — сразу удаление с undo-snackbar.
- **Long-press** — режим выделения; bottom capsule морфится в action-bar (pin/delete для выделенных).

### 6.2 NoteEditorScreen
- **Floating top capsule**: ← назад (автосейв) + pin / delete / share.
- **Контент**: TextField для title (крупный) + TextField для тела. Чекбоксы `[ ]` / `[x]` рендерятся inline кликабельно — тап переключает.
- **Floating bottom capsule**: чипы тегов (тап открывает picker) + иконки markdown (bold / list / checkbox-insert).
- **Автосейв** в Room с debounce 500ms.
- Пустая заметка (title + content пустые) при выходе — удаляется hard delete из Room (она ни разу не успела синхронизироваться, т.к. автосейв не пишет пустые заметки в Firestore — `pushNote` пропускает их).

### 6.3 SettingsScreen
- Sign in with Google / Sign out (Credential Manager).
- Статус синхронизации (последний синк, ошибки).
- Тема (System / Light / Dark).
- About.

### 6.4 noteId
Параметр `noteId` опционален. `null` = новая заметка: репозиторий вставляет пустую запись и возвращает её id, чтобы автосейв сразу мог писать в существующую строку.

## 7. Анимации

Принципы: spring-based (Compose `spring(stiffness = Low/Medium)`), 200–350ms, без декоративных анимаций. Каждая анимация объясняет связь старого и нового состояния.

**Переходы между экранами**
- Карточка → editor: shared element transition (контейнер, title, preview-текст).
- FAB "+" → editor: круговой ripple-морф из точки FAB.
- Settings: slide-up modal sheet.

**Список**
- Появление новой заметки: `animateItemPlacement` + scale-in 0.9→1.0.
- Удаление: slide влево + alpha 1→0, остальные плавно перестраиваются.
- Pin/unpin: `animateItemPlacement` в секцию Pinned + кратковременный elevation pulse.

**Жесты и контролы**
- Свайп влево — реактивный (карточка следует за пальцем 1:1), action-слоты раскрываются пружинкой.
- Чекбокс в редакторе — scale 1→1.2→1 + цветовой переход.
- Floating капсулы — на скролл вниз сжимаются (scale 1.0→0.92, alpha слегка ↓), на скролл вверх восстанавливаются.
- Long-press для выделения — haptic bump + scale.
- Bottom capsule в режиме выделения — морфится из поиска в action-bar (cross-fade + width tween).

## 8. Error handling и edge cases

### 8.1 Оффлайн
- Всё работает локально, изменения копятся (`syncedAt = null`).
- Индикатор в Settings + точка на профиль-иконке, если есть неотправленные изменения.
- WorkManager пушит при `NetworkType.CONNECTED`.

### 8.2 Конфликты (LWW)
- Сравнение по `updatedAt`. Проигравшая версия логируется (Logcat), но не сохраняется. Версионирование заметок — backlog (вне MVP).

### 8.3 Sign-in / Sign-out
- **Sign-in**: Credential Manager API → Firebase Auth. Локальные заметки с `ownerId = null` получают текущий uid и пушатся.
- **Sign-out**: диалог "Заметки останутся на устройстве, но не будут синхронизироваться". Локальные данные **не** чистим (включая `ownerId`), чтобы повторный логин тем же аккаунтом не создал дубликаты.
- **Другой аккаунт**: при sign-in проверяем в Room наличие заметок с `ownerId != null && ownerId != newUid`. Если такие есть — показываем явный диалог "На устройстве есть заметки из другого аккаунта. Загрузить в новый аккаунт или удалить?". "Загрузить" = перезаписать `ownerId` на новый uid и пометить `syncedAt = null` для пуша. "Удалить" = hard delete этих строк.

### 8.4 Удаление
- Soft delete + undo snackbar (5 сек).
- DeleteTombstonesWorker ежедневно чистит записи старше 30 дней.

### 8.5 Sync errors
- WorkManager `BackoffPolicy.EXPONENTIAL` (10s → 5 мин).
- Ошибка > 24ч — snackbar в Settings "Проблемы с синхронизацией".

## 9. Тестирование

- **Unit (JVM)**: use-cases, merge-logic в SyncRepository (LWW, тумбстоуны, миграция local→cloud при логине).
- **Room тесты**: in-memory DB — DAO + FTS-поиск.
- **Compose UI тесты**: создать/удалить заметку (с undo), свайп влево раскрывает actions, чекбокс переключается, диалог account-switch на sign-in с заметками другого uid.
- **Worker тесты**: `DeleteTombstonesWorker` чистит записи на границе 30 дней (тест с фиксированным `Clock`).
- **Firestore (опц.)**: SyncRepository против Firebase Local Emulator в CI.

## 10. Не входит в MVP (backlog)

- Версионирование заметок и просмотр истории.
- Шаринг заметки другому пользователю.
- Прикреплённые файлы / изображения.
- Виджеты на главный экран.
- Экспорт/импорт.
- Email/password auth (только Google для MVP).
- iOS / Web версии.
