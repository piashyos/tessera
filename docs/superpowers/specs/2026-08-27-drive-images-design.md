# Images in Notes via Google Drive — Design

Date: 2026-08-27
Status: Approved by owner (brainstorming session)

## Goal

Let users insert images into notes without paying for Firebase Storage. Images are
stored in the inserting user's own Google Drive (their free quota), shared per-email
to members of shared notes. Insertion works offline and without a Google account;
Drive upload catches up when the user signs in.

## Decisions made

| Question | Decision |
|---|---|
| Storage backend | Google Drive API v3, folder "Tessera", scope `drive.file` |
| Signed-out users | Insertion always works; upload waits for sign-in |
| Image sources | Photo Picker, camera, clipboard paste (incl. keyboard `commitContent`) |
| Compression | Re-encode to WebP q~80, long side ≤ 2048 px; original discarded |
| Access revocation | Removing a member revokes their Drive permission on the note's files |
| Who can insert in shared notes | Any member with write role; each inserter's client manages permissions on their own files |
| Deletion | Drive file trashed after 7-day orphan hysteresis (no references in any live note, including app trash) |
| HTTP client | Drive REST v3 directly via OkHttp/Retrofit (no google-api-services-drive) |

## Open risk → spike (Phase 0)

`drive.file` is documented as per-file access to files the user "created or opened
with the app". It is NOT guaranteed that a recipient's app instance can read a file
created by the *same app under another account* and shared to the recipient per-email.

**Spike:** two Google accounts; owner uploads + grants `type=user, role=reader`;
recipient calls `GET /drive/v3/files/{id}?alt=media` under `drive.file`.

- Works → main scheme stands.
- Fails → fallback: owner additionally sets permission `type=anyone, role=reader`;
  `fileId + resourceKey` live only in the note's Firestore manifest, which is readable
  only by note members (existing security rules). This is a capability-URL — the same
  security model as Firebase Storage download tokens. Per-email permissions stay as an
  extra layer. Recipient code path is unchanged.

## Identification and markup

- Each image gets an app-level UUID `imageId` at insert time (never changes; exists
  offline before any Drive fileId).
- Markdown: `![](tessera-img://<imageId>)`; Aztec HTML: `<img src="tessera-img://<imageId>" />`.
  Round-trip added to `MarkdownHtmlConverter`.

### Room table `note_images`

| column | purpose |
|---|---|
| `imageId` PK | UUID from markup |
| `noteId` | owning note |
| `ownerUid` | who inserted (whose Drive); null while signed out, backfilled on sign-in |
| `localPath` | WebP in `filesDir/images/` |
| `driveFileId`, `resourceKey` | set after upload |
| `status` | `LOCAL_ONLY → UPLOADING → UPLOADED` |
| `permissionsSyncedAt` | last successful permission reconciliation |
| `orphanedAt` | when the image lost all references (deletion hysteresis) |

### Firestore manifest

Map field `images` on the note document: `imageId → { driveFileId, resourceKey,
ownerUid, width, height }`. Recipients (and the owner's other devices) resolve
`tessera-img://` through it. Protected by the note's existing security rules.

## Insert pipeline

Single entry point `InsertImageUseCase(source)`:

1. Sources: `PickVisualMedia` photo picker; `TakePicture` + `CAMERA` permission
   (buttons in `EditorFormatBar`); clipboard via `InputConnectionCompat.createWrapper`
   `commitContent` in `FlickerlessAztecText` + context-menu Paste when `ClipData` has `image/*`.
2. Decode with `inSampleSize` downsampling (OOM safety) → apply EXIF orientation →
   resize long side ≤ 2048 px → encode WebP q~80 → `filesDir/images/<imageId>.webp`.
   GPS/EXIF metadata is dropped by re-encoding.
3. Insert `note_images` row (`LOCAL_ONLY`), insert `<img>` span at cursor —
   image is visible immediately from the local file.
4. Enqueue upload work. Compression runs on `Dispatchers.Default` with a spinner
   placeholder in the editor.

## Drive auth

`DriveAuthManager` wraps `Identity.getAuthorizationClient().authorize()` with scope
`drive.file` (non-sensitive, no Google verification needed). One-time consent
(handles `PendingIntent` UI when required); access token (~1 h) cached and refreshed.
Consent declined → images stay `LOCAL_ONLY` with a subtle "not synced" badge; nothing blocks.
Recipients get the same lazy consent on first download of another member's image.

## Upload — `ImageUploadWorker` (WorkManager)

1. Select current user's `LOCAL_ONLY` rows.
2. Find/create "Tessera" folder once (folder fileId cached in DataStore).
3. `POST /upload/drive/v3/files?uploadType=multipart` — name `imageId.webp`,
   parent folder, `appProperties: {tesseraImageId}` + WebP body.
4. Persist `driveFileId`/`resourceKey`, status → `UPLOADED`, patch Firestore manifest.

Constraints `NETWORK_CONNECTED`, exponential backoff. Triggers: image inserted,
sign-in, network regained.

## Permission reconciliation — `ImagePermissionWorker`

Declarative sync, not event reactions (events can be missed offline). For each of my
`UPLOADED` images: target readers = emails of the note's current members
(from `ShareState.Shared.members` via `users/{uid}`); compare with actual
`GET /files/{id}/permissions`; `POST` missing (`type=user, role=reader,
sendNotificationEmail=false`), `DELETE` extra. Un-sharing / member removal is handled
by the same diff. Triggers: membership change, upload success, daily periodic safety run.

**Known limitation (accepted):** only the file's owner can grant permissions. If the
inserter is offline, new members see that inserter's images as "awaiting access"
placeholders until the inserter's device comes online. Not fixable without a server.

## Rendering and cache

`TesseraImageLoader` implements Aztec's `Html.ImageGetter`, resolving `tessera-img://`:

1. `localPath` exists → decode local WebP (downsampled to editor width).
2. Else manifest has `driveFileId` → placeholder + `GET /files/{id}?alt=media`
   (with `resourceKey` header when present) → save to `cacheDir/images/<imageId>.webp` → redraw span.
3. Else (offline / permission not yet granted / 403) → persistent "unavailable"
   placeholder with tap-to-retry.

Own images live in `filesDir` (source of truth); downloaded members' images in
`cacheDir` with ~200 MB LRU. `MarkdownPreview` strips `![](tessera-img://…)` from
card previews (exact rendering decided in preview tests). No image thumbnails on
note cards in v1.

## Deletion — `ImageCleanupWorker`

Daily periodic + after note save:

1. Mark orphans: my `note_images` row whose `imageId` appears in no live note's
   `content` (notes in app trash count as live) → set `orphanedAt = now`.
   Reference reappears (undo / restore) → reset to null.
2. `orphanedAt` older than 7 days → `DELETE /drive/v3/files/{id}` (goes to Drive
   trash, 30 more days manually recoverable), delete local file, Room row, manifest entry.
3. Other members' cached images are untouched (LRU handles `cacheDir`).

## Testing

- **Unit:** `tessera-img://` markdown↔HTML round-trip (incl. adjacent to tables/lists);
  permission diff as a pure function; orphan detection + `orphanedAt` reset; resize
  policy (dimensions, EXIF orientation).
- **Drive client:** `MockWebServer` — multipart upload, permissions CRUD,
  401→token refresh, recipient 403/404.
- **Robolectric/Compose:** insert via fake picker → span + Room row; three placeholder
  states; Roborazzi screenshot of editor with image (`captureScreenRoboImage`).
- **Manual spike checklist:** two accounts — insert → share → recipient reads;
  remove member → 403.

## Out of scope (v1)

Card thumbnails, fullscreen viewer with zoom, in-text image resize/alignment,
content-based deduplication.
