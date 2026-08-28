package app.tessera.data.remote

/**
 * One entry of a note's Firestore `images` manifest (map keyed by app-level `imageId`).
 *
 * Points at the Drive file backing an image so any device — the owner's second device or a
 * share recipient — can locate and download it. No-arg defaults are required for Firestore's
 * reflective deserialization.
 *
 * @property driveFileId Drive file id of the uploaded WebP.
 * @property resourceKey Drive resource key (present for link-shared files), null otherwise.
 * @property ownerUid uid of the user who uploaded (and owns) the Drive file — never the recipient.
 * @property width intrinsic pixel width, used to lay out the placeholder before the bytes arrive.
 * @property height intrinsic pixel height.
 */
data class RemoteImageRef(
    val driveFileId: String = "",
    val resourceKey: String? = null,
    val ownerUid: String = "",
    val width: Int = 0,
    val height: Int = 0,
)
