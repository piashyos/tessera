package app.tessera.domain.usecase

import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity

/** The scheme every note-content reference to a Tessera image uses: `tessera-img://<imageId>`. */
const val TESSERA_IMAGE_SCHEME = "tessera-img://"

/**
 * The two orphan-bookkeeping deltas a sweep produces:
 *  - [toStamp]: rows that just became unreferenced — copies with `orphanedAt` set to `now`.
 *  - [toClear]: rows that were orphaned but are referenced again — copies with `orphanedAt = null`.
 */
data class OrphanResult(
    val toStamp: List<NoteImageEntity>,
    val toClear: List<NoteImageEntity>,
)

/**
 * Pure orphan bookkeeping. An image is *referenced* iff SOME note's content contains
 * `tessera-img://<imageId>`. [notesContent] is every live note's content by id, INCLUDING app-trash
 * (a soft-deleted note with `deletedAt != null` still keeps its images alive until it is purged).
 *
 * - Unreferenced rows with `orphanedAt == null` are stamped ([toStamp]).
 * - Re-referenced rows with `orphanedAt != null` are cleared back to null ([toClear]).
 * - [ImageStatus.ACCESS_LOST] rows are skipped entirely: they are neither stamped nor cleared, so a
 *   shared image whose access was revoked is never trashed nor resurrected by this sweep — its fate
 *   is decided only when access is regained (see the shared-note reappearing flow).
 */
fun findOrphans(
    images: List<NoteImageEntity>,
    notesContent: Map<String, String>,
    now: Long,
): OrphanResult {
    val contents = notesContent.values
    val toStamp = mutableListOf<NoteImageEntity>()
    val toClear = mutableListOf<NoteImageEntity>()
    for (image in images) {
        if (image.status == ImageStatus.ACCESS_LOST) continue
        val marker = TESSERA_IMAGE_SCHEME + image.imageId
        val referenced = contents.any { it.contains(marker) }
        when {
            referenced && image.orphanedAt != null -> toClear += image.copy(orphanedAt = null)
            !referenced && image.orphanedAt == null -> toStamp += image.copy(orphanedAt = now)
        }
    }
    return OrphanResult(toStamp = toStamp, toClear = toClear)
}
