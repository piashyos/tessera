package app.tessera.ui.editor.components

/**
 * Pure geometry + canonical-src helpers for the in-editor image resize/rotate overlay.
 * No Android dependencies — unit-tested in [ImageSelectionGeometryTest].
 */

/**
 * New width-percent after dragging a corner handle by [signedDx] px (OUTWARD-positive: a positive
 * delta grows the image, negative shrinks it — the caller negates the raw drag for left/top
 * handles). [startPercent] is the width% at drag start; [defaultWidthPx] is the px width the image
 * renders at 100%. Result clamped to 10..100.
 */
fun dragToWidthPercent(startPercent: Int, signedDx: Float, defaultWidthPx: Int): Int {
    if (defaultWidthPx <= 0) return startPercent.coerceIn(10, 100)
    val startPx = startPercent / 100f * defaultWidthPx
    val newPercent = (startPx + signedDx) / defaultWidthPx * 100f
    return Math.round(newPercent).coerceIn(10, 100)
}

/** Overload for an Int delta. */
fun dragToWidthPercent(startPercent: Int, signedDx: Int, defaultWidthPx: Int): Int =
    dragToWidthPercent(startPercent, signedDx.toFloat(), defaultWidthPx)

/**
 * Rewrite [source] with width-percent [wPercent], producing a CANONICAL
 * `tessera-img://<id>?w=..&rot=..` src that DROPS params equal to their defaults (`w=100`, `rot=0`)
 * so a reset image serializes bare. Returns [source] unchanged if it isn't a tessera-img ref.
 */
fun withWidthPercent(source: String, wPercent: Int): String {
    val ref = parseImageRef(source) ?: return source
    return canonicalSrc(ref.id, wPercent.coerceIn(10, 100), ref.rotDegrees)
}

/** Rewrite [source] with rotation [rotDegrees] (canonical, defaults dropped). See [withWidthPercent]. */
fun withRotation(source: String, rotDegrees: Int): String {
    val ref = parseImageRef(source) ?: return source
    return canonicalSrc(ref.id, ref.wPercent, rotDegrees)
}

private fun canonicalSrc(id: String, wPercent: Int, rotDegrees: Int): String {
    val params = buildList {
        if (wPercent != 100) add("w=$wPercent")
        if (rotDegrees != 0) add("rot=$rotDegrees")
    }
    return if (params.isEmpty()) "tessera-img://$id" else "tessera-img://$id?${params.joinToString("&")}"
}
