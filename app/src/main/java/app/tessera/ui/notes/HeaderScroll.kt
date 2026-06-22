package app.tessera.ui.notes

const val HEADER_COLLAPSED_SCALE = 0.7f

/** Scroll distance (in px) over which the header title shrinks from full to collapsed scale. */
const val HEADER_COLLAPSE_THRESHOLD_PX = 200

fun headerTitleScale(scrollPx: Int, thresholdPx: Int): Float {
    if (thresholdPx <= 0) return HEADER_COLLAPSED_SCALE
    val progress = (scrollPx.toFloat() / thresholdPx).coerceIn(0f, 1f)
    return 1f - ((1f - HEADER_COLLAPSED_SCALE) * progress)
}
