# Drive-Images Phase-0 Spike — Findings

**Task 4 gate status: DEFERRED (not yet run on two live Google accounts).**

The spike verifies whether a recipient signed into `drive.file` scope can read a file
that another user uploaded and granted to them per-email (`createPermission type=user role=reader`).
It requires two real Google accounts on device(s) — an agent cannot run it.

## Decision (proceeding without the live spike)

`DriveImagesConfig.ANYONE_LINK_MODE = false` (main scheme). Per-email grants only; no
`type=anyone` capability-link fallback. This is the optimistic default the spec favours.

Chunk 4 is built so the fallback is a **single compile-time constant flip**:
- `ANYONE_LINK_MODE = false` → per-email grants; stray `type=anyone` perms are revoked.
- `ANYONE_LINK_MODE = true` → additionally ensure one `type=anyone, role=reader` per shared file.

`ComputePermissionDiff` handles both branches and is unit-tested for both, so flipping the
constant after the live spike needs no code changes.

## To finalise (manual, when two accounts are available)

1. On account A (signed into `drive.file`): upload a tiny file, `createPermission(email of B)`.
2. On account B (signed into `drive.file`): `downloadFile(fileId, resourceKey)`.
3. Record result here:
   - **B reads it** → keep `ANYONE_LINK_MODE = false`. Done.
   - **B gets 403/404** → set `ANYONE_LINK_MODE = true` and note it here.

Until this is recorded, treat cross-account image visibility under the main scheme as
**unverified on live Drive** — the per-email grant path is built and unit-tested, but the
real read has not been confirmed.
