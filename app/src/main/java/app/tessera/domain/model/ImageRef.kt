package app.tessera.domain.model

/** Parsed `tessera-img` reference: bare id plus optional display params. */
data class ImageRef(val id: String, val wPercent: Int, val rotDegrees: Int)

private const val TESSERA_IMG_SCHEME = "tessera-img://"
private val VALID_ROT = setOf(0, 90, 180, 270)

/**
 * Parse `tessera-img://<id>?w=..&rot=..`; null when [source] isn't a tessera-img ref or has a
 * blank id. Defaults `w=100`, `rot=0`. `w` is clamped to 10..100; `rot` must be one of
 * {0,90,180,270} else it falls back to 0. Tolerates a malformed query without crashing.
 */
fun parseImageRef(source: String): ImageRef? {
    if (!source.startsWith(TESSERA_IMG_SCHEME)) return null
    val rest = source.removePrefix(TESSERA_IMG_SCHEME)
    val id = rest.substringBefore('?')
    if (id.isBlank()) return null
    // Aztec's toHtml hands the query back with `&amp;` (HTML-escaped); decode so splitting works
    // whether the source came from stored markdown (raw `&`) or from editor.toHtml (`&amp;`).
    val query = rest.substringAfter('?', "").replace("&amp;", "&")
    val params = query.split('&').mapNotNull {
        val k = it.substringBefore('=', "")
        val v = it.substringAfter('=', "")
        if (k.isEmpty()) null else k to v
    }.toMap()
    val w = params["w"]?.toIntOrNull()?.coerceIn(10, 100) ?: 100
    val rot = params["rot"]?.toIntOrNull()?.takeIf { it in VALID_ROT } ?: 0
    return ImageRef(id, w, rot)
}

/** The bare id of a tessera-img ref, or null when [source] isn't one. */
fun parseImageId(source: String): String? = parseImageRef(source)?.id
