# Роадмап — Общие заметки (Shared Notes)

- **Дата:** 2026-06-23
- **Milestone:** Совместная работа над заметками
- **Спеки:** [Доступ и UI](../superpowers/specs/2026-06-23-shared-notes-access-design.md) · [Live co-editing](../superpowers/specs/2026-06-23-shared-notes-live-coediting-design.md)
- **Ветка:** `feature/notes-list-screen` (или новая `feature/shared-notes`)

## Принцип ведения работ
Для каждой фазы: сначала **план** (детальные задачи), затем **исполнение** по плану, затем **фиксация состояния** в этом файле (таблица ниже) и атомарный коммит. Не переходим к следующей фазе, пока статус текущей не `✅ Done` и не подтверждён (тесты зелёные, демо работает).

> Можно вести через GSD: `/gsd:new-milestone` → `/gsd:plan-phase` → `/gsd:execute-phase` → `/gsd:verify-work`. GSD сам трекает состояние; этот файл — человекочитаемое зеркало.

---

## Фазы

### Блок A — Доступ и UI (спека №1)

**Фаза 1 — Бэкенд доступа (данные + правила).**
`usersByEmail` индекс; коллекция `sharedNotes`; Room-миграция (`shareState`, `myRole`, таблица `shared_members`); `FirestoreSharedNotesClient`, `RemoteSharedNotesDataSource`, `UserDirectoryDataSource`; расширение `SyncRepositoryImpl` (слушать `sharedNotes`, маршрутизация push, обработка исключения/понижения роли); Firestore rules + тесты на эмуляторе.
*Готово, когда:* заметку можно программно сделать общей, пригласить участника, права в rules покрыты тестами.

**Фаза 2 — Домен и UseCases.**
`ShareRole`, `ShareMember`, `ShareState`; функции прав; `NotesRepository` методы; UseCases (Share/Invite/ChangeRole/Remove/Leave/Unshare); unit-тесты прав и маппинга.

**Фаза 3 — UI: папка «Общие» + аватары.**
Системная секция `shared` в `TesseraNotesDrawer` (видна при наличии общих); фильтр в `NotesListViewModel/State`; `MemberAvatarStack` (скрытие одиночного своего аватара, 2 + `+N`); интеграция в `TesseraNoteCard`/`TesseraSwipeableNoteCard`; Roborazzi-снапшоты; i18n.

**Фаза 4 — UI: модалка участников + меню редактора.**
`MembersSheet` (список, роли, инвайт по email, состояния not-found/already-member); пункты ⋮ в `NoteEditorScreen` (Сделать общей / Управление доступом / Выйти / Удалить для всех); read-only режим для Viewer; диалоги подтверждения; i18n; Roborazzi.

**Фаза 5 — Интеграция и приёмка блока A.**
E2E с двумя эмулированными пользователями: инвайт → появление в «Общих» → правка (document-level sync) → смена роли → выход/исключение. Багфиксы. Демо.

### Блок B — Live co-editing (спека №2)

**Фаза 6 — Presence.**
RTDB `sessions/{noteId}/presence` + `onDisconnect`; `sessions/{noteId}/acl` (зеркало членства/ролей); RTDB rules; UI «кто онлайн» в заметке. Низкий риск, проверяет канал.

**Фаза 7 — CRDT-ядро (RGA над markdown).**
Своя sequence-CRDT: insert/delete с `(lamport, uid)`; property-based тесты конвергенции/коммутативности/идемпотентности. **Гейт:** дальше только при зелёном ядре.

**Фаза 8 — Live document streaming.**
diff markdown→ops, apply ops→markdown, точечный апдейт Aztec без сброса каретки; стрим через RTDB `ops`; near-realtime контент у всех. Без чужих кареток.

**Фаза 9 — Чужие каретки и выделения.**
Plain-text позиции + position transform; оверлей кареток над Aztec, цвета по `colorForUser(uid)` (единые с аватарами); выделения; presence cursor.

**Фаза 10 — Persist, compaction, офлайн-реконсиляция.**
snapshot в RTDB + content в Firestore `sharedNotes`; подрезка `ops`; реконсиляция после реконнекта; resilience-тесты. Финальная приёмка milestone.

---

## Состояние выполнения (фиксируется после каждой фазы)

| Фаза | Название | Статус | План | Коммит/PR | Дата | Заметки |
|------|----------|--------|------|-----------|------|---------|
| 1 | Бэкенд доступа | 🟡 In progress | — | — | 2026-06-23 | Сделаны п.1–2: Room-миграция 5→6 (`shareState`/`myRole`, таблица `shared_members`, `SharedMemberDao`, тест миграции) + domain-модель прав (`ShareRole`/`ShareMember`/`ShareState`, тесты). Осталось: data sources, sync, Firestore rules. |
| 2 | Домен и UseCases | ⬜ Todo | — | — | — | |
| 3 | UI: «Общие» + аватары | ⬜ Todo | — | — | — | |
| 4 | UI: модалка + меню | ⬜ Todo | — | — | — | |
| 5 | Интеграция блока A | ⬜ Todo | — | — | — | |
| 6 | Presence | ⬜ Todo | — | — | — | |
| 7 | CRDT-ядро (гейт) | ⬜ Todo | — | — | — | |
| 8 | Live document streaming | ⬜ Todo | — | — | — | |
| 9 | Чужие каретки | ⬜ Todo | — | — | — | |
| 10 | Persist + reconcile | ⬜ Todo | — | — | — | |

Статусы: ⬜ Todo · 🟡 In progress · 🔵 In review · ✅ Done · ⛔ Blocked

## Зависимости
- Блок B зависит от Блока A (нужны `sharedNotes`, роли, members).
- Фаза 8/9 зависит от Фазы 7 (зелёное CRDT-ядро — обязательный гейт).
- Фаза 9 переиспользует `colorForUser(uid)` из Фазы 3 (аватары).
