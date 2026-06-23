# Спека №1 — Общие заметки: доступ и UI

- **Дата:** 2026-06-23
- **Статус:** Draft (на ревью)
- **Область:** шаринг заметок по email, роли доступа, папка «Общие», аватары на карточке, модалка участников, выход из общей заметки
- **Не входит:** live-совместное редактирование с курсорами (см. спеку №2)

---

## 1. Цель и контекст

Сейчас приложение полностью приватное: Firestore-структура `users/{uid}/notes/{noteId}`, правила разрешают доступ только владельцу. Sync — local-first (Room) + фоновый WorkManager + snapshot-листенеры, merge по `updatedAt` (last-write-wins). Auth — Firebase Auth + Google Sign-In; у пользователя есть `UserProfile(id, displayName, email, photoUrl)`.

Нужно дать возможность сделать заметку **общей**: владелец приглашает других зарегистрированных пользователей по email с ролью; у приглашённых заметка появляется в системной папке **«Общие»**; на карточке общей заметки показываются аватары участников; через контекстное меню редактора можно выйти из общей заметки.

### Ключевые решения (зафиксированы)
- Инвайт **только для уже зарегистрированных** пользователей (email есть в Firebase Auth/`users`). Нет email — ошибка «пользователь не найден».
- Управлять доступом (звать, менять роли, исключать) может **только владелец**.
- Роли: **Viewer** (чтение), **Editor** (чтение + правка контента), **Manager** (правка + удаление заметки для всех + управление доступом наравне с владельцем — *кроме* передачи владения).
- «Удаление» из ТЗ = роль **Manager**.

---

## 2. Архитектурная проблема: где живут общие заметки

Сегодня заметка лежит в `users/{ownerUid}/notes/{id}` — приватное поддерево владельца. Приглашённый не может читать чужое поддерево. Варианты:

| Вариант | Суть | Плюсы | Минусы |
|---|---|---|---|
| **A. Top-level `sharedNotes/{noteId}` (рекомендую)** | Общие заметки переезжают/дублируются в коллекцию верхнего уровня с полем `members`. Правила: доступ если `request.auth.uid in members`. | Чистые правила, один источник правды, легко листать «общие» через `where members.<uid> != null`. Готово к спеке №2. | Заметка живёт в двух местах при шаринге приватной → нужна миграция документа. |
| B. Оставить в `users/{owner}/notes`, расширить правила | Правило читает поле `members` в самом документе. | Меньше переноса данных. | Firestore не даёт коллекционный запрос «все заметки где я в members» по разным владельцам (collectionGroup + правила сложнее, листинг дорогой). |
| C. Per-folder share | Шарить папку целиком. | — | ТЗ требует общей **заметки**, не папки; «Общие» — агрегат, а не реальная папка. Отвергнуто. |

**Решение: Вариант A.** Вводим коллекцию `sharedNotes/{noteId}`. Приватные заметки остаются в `users/{uid}/notes`. При первом шаринге заметка **переносится** (move) в `sharedNotes`; при выходе всех участников (или анишаринге владельцем) — возвращается в приватное поддерево владельца.

### Firestore-структура

```
users/{uid}                         // профиль (есть сейчас)
  displayName, email, photoUrl, updatedAt
usersByEmail/{emailKey}             // НОВОЕ: индекс email -> uid для инвайта
  uid, email
users/{uid}/notes/{noteId}          // приватные заметки (как сейчас)
users/{uid}/folders/{folderId}      // папки (как сейчас)
sharedNotes/{noteId}                // НОВОЕ: общие заметки
  ownerId: string
  title, content, tags, isPinned, isArchived
  createdAt, updatedAt, deletedAt
  members: { <uid>: { role, displayName, email, photoUrl, joinedAt } }
  memberIds: [<uid>, ...]           // дубль ключей members для array-contains-запросов
```

`emailKey` = email в нижнем регистре, нормализованный (точки/плюс не трогаем — Firebase login email хранится как есть, lower-case). `usersByEmail` заполняется в `AuthRepository.saveProfile()` при каждом входе (idempotent upsert).

`members` — map, чтобы атомарно менять роль одного участника и читать роль текущего юзера в правилах. `memberIds` — массив для запроса `whereArrayContains("memberIds", uid)` (Firestore не умеет query по ключам map).

---

## 3. Модель данных (Room / domain)

### NoteEntity (+ поля)
```
ownerId: String?        // уже есть
shareState: String      // НОВОЕ: "PRIVATE" | "SHARED"  (default PRIVATE)
myRole: String?         // НОВОЕ: роль текущего юзера в этой заметке (VIEWER/EDITOR/MANAGER/OWNER); null для приватной
```
Список участников **не** храним в `NoteEntity` (нормализация) — отдельная таблица.

### SharedMemberEntity (новая таблица `shared_members`)
```
noteId: String          // FK -> notes.id
uid: String
displayName: String
email: String
photoUrl: String?
role: String            // OWNER | MANAGER | EDITOR | VIEWER
joinedAt: Long
PRIMARY KEY (noteId, uid)
INDEX (noteId)
```
Зеркалит `sharedNotes/{id}.members` локально — чтобы рисовать аватары офлайн и без доп. запросов.

### Domain
```kotlin
enum class ShareRole { OWNER, MANAGER, EDITOR, VIEWER }

data class ShareMember(
    val uid: UserId, val displayName: String, val email: String,
    val photoUrl: String?, val role: ShareRole, val joinedAt: Long,
)

sealed interface ShareState {
    data object Private : ShareState
    data class Shared(val members: List<ShareMember>, val myRole: ShareRole) : ShareState
}
```

Права выводятся из роли (чистая функция, без хранения флагов):
```
canEditContent(role) = role in {OWNER, MANAGER, EDITOR}
canDeleteForAll(role) = role in {OWNER, MANAGER}
canManageAccess(role) = role in {OWNER, MANAGER}
canTransferOwnership(role) = role == OWNER
canLeave(role) = role != OWNER          // владелец не "выходит", он анишарит/удаляет
```

### Миграция Room
`MigrationXToY`: добавить `shareState`, `myRole` в `notes`; создать таблицу `shared_members`. Версия БД +1.

---

## 4. Слой данных и репозитории

### Новый: `RemoteSharedNotesDataSource` + `FirestoreSharedNotesClient`
- `shareNote(note, owner)` — переносит заметку из `users/{uid}/notes` в `sharedNotes/{id}` (batched write: создать shared-док + удалить приватный + tombstone), `members` = только владелец.
- `addMember(noteId, targetUid, role)` / `updateRole(noteId, uid, role)` / `removeMember(noteId, uid)` — обновляют `members.<uid>` и `memberIds` (FieldValue.arrayUnion/arrayRemove).
- `leaveNote(noteId, myUid)` — удаляет себя из `members`/`memberIds`.
- `unshareNote(noteId)` — возвращает заметку в приватное поддерево владельца, удаляет shared-док.
- `observeSharedNotes(uid)` — `whereArrayContains("memberIds", uid)` snapshot listener.
- `pushSharedNote(note)` — апдейт контента (как обычный sync, но в `sharedNotes`).

### Новый: `UserDirectoryDataSource`
- `lookupByEmail(email): UserId?` — читает `usersByEmail/{emailKey}`. Используется при инвайте. Возвращает null → UI показывает «пользователь не найден».

### `SyncRepositoryImpl` (расширение)
- Помимо `users/{uid}/notes` теперь слушает и мёрджит `sharedNotes` где `uid in memberIds`.
- Push pending: общие заметки пушатся в `sharedNotes`, приватные — как сейчас. Маршрутизация по `shareState`.
- Merge: при апдейте `members` из remote — пересобрать `shared_members` локально, пересчитать `myRole`. Если меня убрали из `memberIds` → заметка локально удаляется (не tombstone — просто исчезает из «Общих»), показываем тост «вас исключили из заметки».
- Конфликт прав: пуш контента разрешаем только если `canEditContent(myRole)`. Если роль понизили до Viewer между правками — локальные несинхронизированные правки откатываются к remote (с уведомлением).

### `NotesRepository` (домен, расширение)
```
shareNote(noteId): Result<Unit>                         // владелец делает заметку общей
inviteByEmail(noteId, email, role): Result<InviteResult> // InviteResult: Added | UserNotFound | AlreadyMember
changeRole(noteId, uid, role): Result<Unit>
removeMember(noteId, uid): Result<Unit>
leaveSharedNote(noteId): Result<Unit>
unshareNote(noteId): Result<Unit>
observeMembers(noteId): Flow<List<ShareMember>>
```

### UseCases (новые)
`ShareNoteUseCase`, `InviteMemberUseCase`, `ChangeMemberRoleUseCase`, `RemoveMemberUseCase`, `LeaveSharedNoteUseCase`, `UnshareNoteUseCase`. Каждый проверяет права текущего юзера до вызова data-source (fail-fast + понятная ошибка).

---

## 5. Firestore Security Rules

```
match /usersByEmail/{emailKey} {
  allow read: if request.auth != null;                 // нужен для lookup при инвайте
  allow write: if request.auth.uid == request.resource.data.uid;
}
match /sharedNotes/{noteId} {
  function member() { return resource.data.members[request.auth.uid]; }
  function role()   { return member().role; }
  allow read:   if request.auth.uid in resource.data.memberIds;
  allow create: if request.auth.uid == request.resource.data.ownerId
                && request.resource.data.members[request.auth.uid].role == 'OWNER';
  // правка контента: editor/manager/owner; правка members: только owner/manager
  allow update: if request.auth.uid in resource.data.memberIds
                && (
                     (onlyContentChanged() && role() in ['OWNER','MANAGER','EDITOR'])
                     || (membersChanged() && role() in ['OWNER','MANAGER'])
                     || (leftSelf())       // любой может удалить себя из members/memberIds
                   );
  allow delete: if role() in ['OWNER','MANAGER'];
}
```
`onlyContentChanged()`, `membersChanged()`, `leftSelf()` — хелперы на diff `resource` vs `request.resource` по затронутым полям. Точные предикаты пишем на фазе реализации правил (с эмулятором + тестами). Передача владения вне scope этой спеки.

---

## 6. UI

### 6.1 Папка «Общие» в drawer
`TesseraNotesDrawer`: новая системная секция (ключ `shared`) между системными («Все», «Закреплённые», «Архив», «Корзина») и пользовательскими папками. Показывается **только если есть хотя бы одна общая заметка, где я участник**. Выбор фильтрует список: `notes where shareState == SHARED && I in members`. Бейдж с количеством — опционально (YAGNI, не делаем в v1).

`NotesListViewModel`/`NotesListState`: новый фильтр `SystemFilter.Shared`. DAO-запрос `observeSharedNotes()`.

### 6.2 Аватары на карточке `TesseraNoteCard`
Новый опциональный проп `members: List<AvatarUi>` (uid, photoUrl, displayName, initials-fallback).

Правила отображения:
- Показываем стопку аватаров **только если в заметке есть кто-то кроме меня** (мой собственный аватар в одиночку — не рисуем).
- Максимум **2** аватара с наслоением (overlap ~8.dp, белая/фоновая обводка 1.5.dp).
- Если участников (кроме меня? или всего?) больше 2 → 2 аватара + **серый кружок `+N`**, где N = остальные. *Уточнение:* считаем всех участников заметки, исключая текущего пользователя из подсчёта аватаров не нужно — показываем со-участников; владелец/я не выделяюсь. (Решение по подсчёту финализируем в плане UI; по умолчанию: стопка = все участники, N = members.size − 2.)
- Тап по стопке → открывает **модалку участников** (см. 6.3). Стопка кликабельна отдельно от карточки (вложенный clickable с остановкой проброса в onClick карточки).

Компонент `MemberAvatarStack` (новый, в `core/ui/tessera`): чистый, тестируемый Roborazzi-снапшотами (1 участник скрыт, 2, 3, 5+).

Загрузка фото: Coil (проверить, есть ли в проекте; если нет — добавить или fallback на инициалы). Аватар-фолбэк — кружок с инициалами и детерминированным цветом по uid.

### 6.3 Модалка участников (`MembersSheet`, ModalBottomSheet M3)
- Заголовок «Доступ к заметке».
- Список участников: аватар, имя, email, бейдж роли. Владелец помечен «Владелец».
- Если я владелец/Manager: у каждого участника меню «Изменить роль» (Viewer/Editor/Manager) и «Исключить»; кнопка «Добавить人» (поле email + выбор роли) внизу.
- Инвайт: ввод email → `InviteMemberUseCase`. Состояния: загрузка, «пользователь не найден», «уже участник», успех (новый чип появляется).
- Если я Viewer/Editor: список read-only.
- Снапшоты Roborazzi: owner-view, viewer-view, состояние «не найден».

### 6.4 Контекстное меню редактора
`NoteEditorScreen` — в overflow-меню (⋮) добавить пункты в зависимости от роли:
- **Поделиться / Управление доступом** (owner/manager) → открывает `MembersSheet`.
- **Выйти из заметки** (любой не-владелец) → `LeaveSharedNoteUseCase`, подтверждение диалогом, навигация назад + тост.
- **Сделать общей** (owner, если заметка ещё приватная) → `ShareNoteUseCase` затем `MembersSheet`.
- **Удалить для всех** (owner/manager) — отдельно от обычного delete.
- Если роль Viewer — редактор открывается в **read-only** (Aztec disabled, формат-бар скрыт).

### 6.5 Строки (i18n)
Все новые строки — в `values/strings.xml` и `values-ru/strings.xml` (проект двуязычный, ru — основной по последним коммитам).

---

## 7. Поток данных (happy path: владелец шарит)

1. Владелец в редакторе → ⋮ → «Сделать общей» → `ShareNoteUseCase`.
2. Data-source переносит заметку в `sharedNotes/{id}` (members = {owner: OWNER}), локально `shareState=SHARED`, `myRole=OWNER`.
3. Открывается `MembersSheet`, владелец вводит email → `lookupByEmail` → uid → `addMember(uid, EDITOR)`.
4. Firestore обновляет `members`/`memberIds`. У приглашённого snapshot-listener (`whereArrayContains memberIds`) ловит новый документ → мёрджит в Room → заметка появляется в папке «Общие», на карточке — аватары.
5. Любой Editor/Manager правит контент → обычный sync пушит в `sharedNotes`, остальные видят апдейт (на уровне документа; посимвольный live — спека №2).

---

## 8. Обработка ошибок и краевые случаи

- **Email не найден** → `InviteResult.UserNotFound`, тост/inline-ошибка, заметка не меняется.
- **Уже участник** → `AlreadyMember`, no-op + сообщение.
- **Меня исключили / понизили роль** во время правки → откат несинхронизированных правок, тост.
- **Офлайн-инвайт** → блокируем (lookup требует сети); показываем «нужен интернет». Изменения контента — работают офлайн как обычно, синкаются позже.
- **Владелец выходит** — запрещено; только «Удалить для всех» или «Сделать приватной» (unshare).
- **Последний участник кроме владельца вышел** — заметка остаётся общей (владелец может снова приглашать) либо авто-unshare. По умолчанию: остаётся SHARED до явного unshare владельцем.
- **Удаление заметки владельцем/Manager** → tombstone в `sharedNotes`, у всех исчезает.
- **Конкурентная правка ролей** — Firestore транзакция/merge на `members.<uid>`; last-write-wins на уровне поля участника.

---

## 9. Тестирование

- **Unit:** UseCases (проверки прав), маппинг ролей, `canEdit/canDelete/...`, merge-логика в `SyncRepositoryImpl` (исключение из memberIds → удаление; понижение роли → откат).
- **Data:** Room-миграция (схема + данные), DAO-запросы общих заметок и участников.
- **Rules:** Firestore emulator — таблица сценариев (viewer не пишет контент, editor пишет, не-участник не читает, leftSelf разрешён, manager управляет доступом).
- **UI / Roborazzi:** `MemberAvatarStack` (1 скрыт/2/3/5+), `MembersSheet` (owner/viewer/not-found), карточка с аватарами, drawer с/без «Общие», редактор read-only для Viewer.
- **Integration:** два эмулированных пользователя — инвайт → появление в «Общих» → правка → выход.

---

## 10. Открытые вопросы (на фазу планирования)

1. Подсчёт N в `+N`: все участники минус 2, или со-участники (без меня) минус 2? (по умолчанию — первое).
2. Есть ли в проекте загрузчик картинок (Coil)? Если нет — добавляем или только инициалы в v1.
3. Нормализация email-ключа (lower-case достаточно для Google-аккаунтов).
4. Передача владения — отдельной мини-фазой или вне milestone.
