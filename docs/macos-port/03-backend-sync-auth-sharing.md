# 03 — Backend, синхронизация, авторизация, шеринг

> Тот же Firebase-проект, что у Android. macOS-клиент — равноправный участник: общие коллекции, общие security rules. Здесь — полный контракт Firestore, движок синка, авторизация на Mac и сценарии шеринга. **Сервер не меняется.**

---

## 3.1. Firebase-проект

| Параметр | Значение |
|----------|----------|
| Project ID | `notes-d3abc` |
| Project Number | `947642610417` |
| Storage Bucket | `notes-d3abc.firebasestorage.app` |
| Сервисы | Firebase Auth, Cloud Firestore |
| Auth provider | Google Sign-In |

**На macOS:**
- Добавить **macOS-приложение** в Firebase Console (новый App с bundle ID `app.tessera.mac`), скачать `GoogleService-Info.plist`, положить в `Resources/`.
- Создать **OAuth Client ID** типа *macOS/iOS* в Google Cloud для того же проекта (Android-клиент `947642610417-…apps.googleusercontent.com` не подходит — нужен отдельный для платформы). Reversed client ID добавить в `CFBundleURLTypes` (для GoogleSignIn redirect).
- Зависимости (SPM): `firebase-ios-sdk` → продукты `FirebaseAuth`, `FirebaseFirestore`; `GoogleSignIn-iOS` → `GoogleSignIn`. Все поддерживают macOS.

> Существующие данные в Firestore не трогаем. Новый App в консоли — только клиентская регистрация, на данные не влияет.

---

## 3.2. Схема Firestore (контракт)

```
users/{uid}                       # публичный профиль
  ├─ notes/{noteId}              # приватные заметки владельца
  └─ folders/{folderId}          # папки владельца
usersByEmail/{emailKey}           # индекс email→uid для инвайтов
sharedNotes/{noteId}              # расшаренные заметки (доступ по членству)
```

### users/{uid}
```json
{ "displayName": "string", "email": "string", "photoUrl": "string", "updatedAt": 0 }
```
Читать может любой залогиненный (для отображения участников); писать — только сам.

### users/{uid}/notes/{noteId} — RemoteNote
```swift
struct RemoteNote: Codable {
    var id: String = ""
    var title: String = ""
    var content: String = ""
    var tags: [String] = []
    var isPinned: Bool = false
    var createdAt: Int64 = 0
    var updatedAt: Int64 = 0
    var deletedAt: Int64? = nil      // tombstone (retention 30 дней)
    var folderId: String? = nil
    var isArchived: Bool = false
}
```

### users/{uid}/folders/{folderId} — RemoteFolder
```swift
struct RemoteFolder: Codable {
    var id = ""; var name = ""; var parentId: String? = nil
    var createdAt: Int64 = 0; var updatedAt: Int64 = 0; var deletedAt: Int64? = nil
}
```

### usersByEmail/{emailKey}
`emailKey` = email, нормализованный (lowercase, trim). Документ:
```json
{ "uid": "string", "email": "string" }
```

### sharedNotes/{noteId} — RemoteSharedNote
```swift
struct RemoteSharedNote: Codable {
    var id = ""
    var ownerId = ""
    var title = ""; var content = ""; var tags: [String] = []
    var isPinned = false; var isArchived = false
    var createdAt: Int64 = 0; var updatedAt: Int64 = 0; var deletedAt: Int64? = nil
    var members: [String: RemoteShareMember] = [:]   // ключ = uid
    var memberIds: [String] = []                       // дубль ключей для array-query
}
struct RemoteShareMember: Codable {
    var role = "VIEWER"
    var displayName = ""; var email = ""; var photoUrl: String? = nil
    var joinedAt: Int64 = 0
}
```

> `Int64`-таймстемпы кодируются как Firestore number. Внимание: Firestore хранит числа как `Double`/`Int`; используем `Int64` и проверяем round-trip. `deletedAt: Int64?` — отсутствие/`null` означает «активна».

---

## 3.3. Security Rules (как есть на сервере — клиент обязан им соответствовать)

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == uid;
    }
    match /users/{uid}/notes/{noteId} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /users/{uid}/folders/{folderId} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /usersByEmail/{emailKey} {
      allow read: if request.auth != null;
      allow create, update: if request.auth != null
                            && request.auth.uid == request.resource.data.uid;
    }
    match /sharedNotes/{noteId} {
      function myRole() { return resource.data.members[request.auth.uid].role; }
      function isMember() { return request.auth != null && request.auth.uid in resource.data.memberIds; }
      function contentKeys() { return ['title','content','tags','isPinned','isArchived','updatedAt','deletedAt']; }
      function onlyContentChanged() { return request.resource.data.diff(resource.data).affectedKeys().hasOnly(contentKeys()); }
      function touchesDeletedAt() { return request.resource.data.deletedAt != resource.data.deletedAt; }
      function membersChanged() { return request.resource.data.diff(resource.data).affectedKeys().hasAny(['members','memberIds']); }
      function leftSelf() {
        return (request.auth.uid in resource.data.memberIds)
               && !(request.auth.uid in request.resource.data.memberIds)
               && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['members','memberIds']);
      }
      allow read: if isMember();
      allow create: if request.auth != null
                    && request.auth.uid == request.resource.data.ownerId
                    && request.auth.uid in request.resource.data.memberIds
                    && request.resource.data.members[request.auth.uid].role == 'OWNER';
      allow update: if isMember() && (
          (onlyContentChanged()
            && (touchesDeletedAt() ? myRole() in ['OWNER','MANAGER']
                                   : myRole() in ['OWNER','MANAGER','EDITOR']))
          || (membersChanged() && myRole() in ['OWNER','MANAGER'])
          || leftSelf()
      );
      allow delete: if isMember() && myRole() in ['OWNER','MANAGER'];
    }
  }
}
```

**Следствия для клиента macOS (обязательно соблюдать):**
1. Контент-push расшаренной заметки обновляет **только** content-ключи; никогда не трогает `members`/`memberIds` в том же запросе.
2. Установка/снятие `deletedAt` (удаление «для всех») — только OWNER/MANAGER.
3. EDITOR может менять контент, но не `deletedAt`.
4. Изменение членства — только OWNER/MANAGER.
5. Выход участника (`leftSelf`) — отдельный апдейт, меняющий **только** `members`/`memberIds` (без piggyback контента).
6. `create` sharedNote — пишущий = ownerId, в memberIds, роль OWNER.

---

## 3.4. Авторизация на macOS

Android: Credential Manager + Google ID token → `GoogleAuthProvider.credential` → Firebase.

**macOS-эквивалент (GoogleSignIn SDK):**
```swift
func signIn() async throws -> UserID {
    guard let presenting = NSApp.keyWindow else { throw AuthError.noWindow }
    let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenting)
    guard let idToken = result.user.idToken?.tokenString else { throw AuthError.noToken }
    let accessToken = result.user.accessToken.tokenString
    let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
    let authResult = try await Auth.auth().signIn(with: credential)
    let user = authResult.user
    try await saveProfile(UserProfile(id: .init(user.uid),
                                      displayName: user.displayName,
                                      email: user.email,
                                      photoUrl: user.photoURL?.absoluteString))
    return .init(user.uid)
}
```

`saveProfile` — батч (как Android): пишем `users/{uid}` и `usersByEmail/{emailKey(email)}`.
```swift
func saveProfile(_ p: UserProfile) async throws {
    let batch = firestore.batch()
    batch.setData([
        "displayName": p.displayName ?? "", "email": p.email ?? "",
        "photoUrl": p.photoUrl ?? "", "updatedAt": clock.nowMs()
    ], forDocument: firestore.collection("users").document(p.id.value))
    if let email = p.email {
        batch.setData(["uid": p.id.value, "email": email],
                      forDocument: firestore.collection("usersByEmail").document(emailKey(email)))
    }
    try await batch.commit()
}
func emailKey(_ e: String) -> String { e.trimmingCharacters(in: .whitespaces).lowercased() }
```

**Состояние сессии:**
```swift
protocol AuthRepository: Sendable {
    var currentUser: any AsyncSequence /* UserID? */ { get }   // через Auth.addStateDidChangeListener
    var currentProfile: UserProfile? { get }
    func signIn() async throws -> UserID
    func signOut() throws
}
```
- `Auth.auth().addStateDidChangeListener` → публикуем `UserID?` в `@Observable`/`AsyncStream`.
- Сессия персистится Firebase SDK (Keychain). Sign-out: `try Auth.auth().signOut()` + `GIDSignIn.sharedInstance.signOut()`.

**Entitlements / Info.plist:**
- App Sandbox: `com.apple.security.network.client` = YES.
- Keychain Access Group (для Firebase Auth persistence).
- `CFBundleURLTypes` с reversed client ID из `GoogleService-Info.plist` (`REVERSED_CLIENT_ID`).
- Обработка redirect: `GIDSignIn.sharedInstance.handle(url)` в `application(_:open:)` (через `NSAppleEventManager`/`onOpenURL`).

---

## 3.5. Движок синхронизации

Полностью повторяет `SyncRepositoryImpl`. Чистый алгоритм — переносим 1:1; меняется только инфраструктура (Task вместо WorkManager, Firestore Apple SDK вместо KTX).

### syncNow(uid)
```
1. pushPendingFolders(uid)
2. pushPending(uid)                      // private + shared
3. remoteFolders = fetchAllFolders(uid)
   remoteNotes   = fetchAllNotes(uid)
   remoteShared  = fetchAllShared(uid)   // where memberIds contains uid
4. mergeRemoteFolders(uid, remoteFolders)
   mergeRemote(uid, remoteNotes)
   mergeRemoteShared(uid, remoteShared)
```
Возвращает `Result`. Push всегда перед pull.

### pushPending(uid)
- Берём заметки `syncedAt == nil`, исключаем blank-черновики.
- Делим на SHARED / PRIVATE.

**pushPendingPrivate:** map → RemoteNote (текущие теги + папка); `upsertAll` в `users/{uid}/notes`; `markSynced(now)`.

**pushPendingShared:** для каждой:
```
role = ShareRole(entity.myRole)
if !role.canEditContent { continue }
if entity.deletedAt != nil && !role.canDeleteForAll { continue }
do {
   pushSharedNote(toRemoteContent(entity))   // только content-ключи!
   markSynced(entity.id, now)
} catch {
   // роль понижена/прав нет — НЕ markSynced; merge откатит
   log.warn("shared push rejected; leave pending for rollback")
}
```

**pushPendingFolders:** map → RemoteFolder; upsert; markSynced.

### mergeRemote(uid, remoteNotes) — приватные
Для каждой remote-заметки:
```
local = get(id)
if local?.shareState == SHARED { continue }       // не затирать расшаренную копию tombstone'ом
if local != nil && local.updatedAt >= remote.updatedAt { continue }  // LWW
upsert(toEntity(remote, uid, syncedAt: now)); replaceTags(remote.tags); replaceFolder(remote.folderId)
```

### mergeRemoteShared(uid, remoteShared)
```
remoteIds = set(remoteShared.id)
// 1. Eviction: локальные SHARED, которых нет в remote и не OWNER → hardDelete (каскад чистит members)
for evicted in getSharedNotes() where evicted.id ∉ remoteIds && evicted.myRole != OWNER {
    hardDelete(evicted.id)
}
// 2. Для каждой remote:
for r in remoteShared {
    local = get(r.id)
    myRole = sharedMapper.myRole(r, uid)
    hasUnsyncedLocalEdits = (local != nil && local.syncedAt == nil)
    remoteNewer = (local == nil || r.updatedAt > local.updatedAt)
    rollbackDowngrade = hasUnsyncedLocalEdits && !myRole.canEditContent
    if remoteNewer || rollbackDowngrade {
        upsert(toEntity(r, uid, syncedAt: now)); replaceTags(r.id, r.tags)
    } else {
        setShareState(r.id, SHARED, myRole)        // оставить локальный контент, обновить роль
    }
    sharedMemberStore.replaceForNote(r.id, toMemberEntities(r))  // зеркало участников
}
```

### mergeRemoteFolders — LWW по updatedAt.

### Tombstones
- `purgeTombstones(uid)`: удалить soft-deleted (notes+folders) старше **30 дней** (2 592 000 000 ms) локально и remote.
- `purgeLocalTombstones()`: только локально (на запуске, для не вошедших).
- `permanentlyDelete(uid, ids)`: сначала remote (`users/{uid}/notes/{id}` и/или `sharedNotes/{id}`), затем local hardDelete.

### Realtime listeners — observeRemote(uid)
Три независимых `AsyncThrowingStream` поверх `addSnapshotListener`:
- notes (`users/{uid}/notes`), folders (`users/{uid}/folders`), shared (`sharedNotes where memberIds contains uid`).
- Каждый в своём `Task`; ошибка одного не рушит другие (per-stream `try?`+log).
- На каждый снапшот применяем те же merge-функции.
- Активны только когда signed-in; пересоздаются при смене аккаунта.

```swift
func observeRemote(uid: UserID) -> AsyncStream<Void> {
    AsyncStream { continuation in
        let tasks = [
            Task { for await snap in notesSnapshots(uid) { await mergeRemote(uid, snap); continuation.yield(()) } },
            Task { for await snap in folderSnapshots(uid) { await mergeRemoteFolders(uid, snap); continuation.yield(()) } },
            Task { for await snap in sharedSnapshots(uid) { await mergeRemoteShared(uid, snap); continuation.yield(()) } },
        ]
        continuation.onTermination = { _ in tasks.forEach { $0.cancel() } }
    }
}
```

### Планировщик (SyncScheduler) — замена WorkManager
- `requestSync()` после любого локального изменения: debounce + single-flight (если синк уже идёт — пометить «надо повторить»).
- Реализация: actor с текущим `Task?`; новый запрос отменяет/коалесцирует.
- Constraints сети: проверять `NWPathMonitor`; если офлайн — отложить, повторить по `pathUpdateHandler`.
- Бэкофф при ошибке: экспоненциальный (10s старт), как в Android.

---

## 3.6. Шеринг — сценарии (SharedNotesGatewayImpl)

### shareNote(id) — сделать общей
1. Профиль текущего пользователя → создать owner-member (`role=OWNER`, `joinedAt=now`).
2. Построить `RemoteSharedNote` (ownerId, members={owner}, memberIds=[owner]).
3. **Атомарный батч** (FirestoreSharedNotesClient.shareNote): `set sharedNotes/{id}` + `delete users/{uid}/notes/{id}`.
4. Локально: `adoptOwnerAndShareState(id, ownerUid, SHARED, OWNER)`; `replaceForNote(members)`.

UseCase-проверки (ShareNoteUseCase): заметка существует; пользователь вошёл; если `note.ownerId != nil && != me` → `ShareAccessDenied`.

### inviteByEmail(id, email, role)
1. lookup в `usersByEmail/{emailKey(email)}` → uid; если нет → `InviteResult.userNotFound`.
2. fetch профиль `users/{uid}` (читаем как залогиненный).
3. если уже в members → `alreadyMember`.
4. создать member (`joinedAt=now`); remote update:
   ```swift
   sharedNotes.document(noteId).updateData([
       "members.\(uid)": memberDict,
       "memberIds": FieldValue.arrayUnion([uid])
   ])
   ```
5. локально upsert member. Вернуть `added`.

### changeRole(id, uid, role)
`updateData(["members.\(uid).role": role.rawValue])`; локально обновить роль member'а. (OWNER не меняется — назначаемые: MANAGER/EDITOR/VIEWER.)

### removeMember(id, uid)
```swift
updateData(["members.\(uid)": FieldValue.delete(), "memberIds": FieldValue.arrayRemove([uid])])
```
локально удалить member.

### leaveSharedNote(id) — выход (non-owner)
- UseCase: `myRole != OWNER` (canLeave), иначе `ShareAccessDenied("Owner cannot leave")`.
- remote: `leaveNote(id, myUid)` = тот же removeMember, но как **leftSelf** апдейт (только members/memberIds).
- локально: clear tags + `hardDelete(id)` (заметка исчезает с устройства).

### unshareNote(id) — сделать приватной (owner)
- ownerUid = entity.ownerId ?? currentUid.
- map entity → RemoteNote (приватный формат).
- **Атомарный батч**: `set users/{uid}/notes/{id}` + `delete sharedNotes/{id}`.
- локально: `setShareState(id, PRIVATE, nil)`; `deleteForNote(members)`.

### Удаление расшаренной «для всех»
- soft-delete: ставим `deletedAt=now`, push разрешён только OWNER/MANAGER (rules). EDITOR — push отклоняется, правка остаётся pending, merge откатывает.
- hard-delete из Корзины: `permanentlyDelete` удаляет и `sharedNotes/{id}`.

### Редактирование расшаренной заметки
1. Локальное изменение → upsert, `syncedAt=nil`.
2. На синке: проверка локальной `myRole` (оптимизация) → `pushSharedNote` (только content-ключи). Сервер — авторитет.

### observeMembers(id)
`SharedMemberStore.observeForNote(id)` (sort joinedAt asc) → map в domain `ShareMember`.

---

## 3.7. Роли и разрешения (pure)

```swift
enum ShareRole: String, Sendable { case OWNER, MANAGER, EDITOR, VIEWER
    var canEditContent: Bool  { self == .OWNER || self == .MANAGER || self == .EDITOR }
    var canDeleteForAll: Bool { self == .OWNER || self == .MANAGER }
    var canManageAccess: Bool { self == .OWNER || self == .MANAGER }
    var canLeave: Bool        { self != .OWNER }
    static func fromWire(_ s: String?) -> ShareRole { ShareRole(rawValue: s ?? "") ?? .VIEWER }
}
```

---

## 3.8. Аккаунт: adoption и изоляция

### Первый вход — adoption
`adoptLocalNotes(uid)`: `ownerId = uid where ownerId == nil; syncedAt = nil`. Следующий push выгрузит их.

### Смена аккаунта — изоляция (SyncManager.onSignedIn)
```
1. stop previous listeners
2. if hasForeignData(uid):
     pushLocalChanges(uid)        // не потерять unsynced
     accountRepository.clearLocalData()
3. adoptLocalNotes(uid)           // если были локальные без owner
4. syncNow(uid)
5. start listeners(uid)
6. schedule tombstone purge
```
`hasForeignData(uid)` = exists note с `ownerId != nil && != uid && id ∉ shared_members(uid)`.

`clearLocalData()` — hardDelete всех notes/folders/tags/members (+ FTS).

---

## 3.9. Контрольный список backend/sync

- [ ] Firebase macOS App + GoogleService-Info.plist + OAuth client + URL scheme.
- [ ] FirebaseAuth + GoogleSignIn интеграция, state listener, persist в Keychain.
- [ ] DTO: RemoteNote/RemoteSharedNote/RemoteFolder/RemoteShareMember (Codable, Int64 round-trip).
- [ ] 4 Firestore-клиента (notes/folders/shared/userDirectory) с точными операциями (батчи, FieldValue).
- [ ] syncNow: push(folders, private, shared) → fetch → merge(folders, private, shared).
- [ ] mergeRemoteShared: eviction, rollback-on-downgrade, member-mirror.
- [ ] Tombstone purge (30 дней), permanentlyDelete (remote-first).
- [ ] 3 realtime listener'а, per-stream изоляция.
- [ ] SyncScheduler (debounce + single-flight + сеть + бэкофф).
- [ ] Все 7 шеринг-операций с соблюдением rules (content-only push, leftSelf, атомарные батчи).
- [ ] Adoption + account isolation в SyncManager.
- [ ] Соблюдение rules: НИКОГДА не миксовать членство и контент в одном update.
