package app.tessera.ui.notes

const val HEADER_COLLAPSED_SCALE = 0.7f

fun headerTitleScale(scrollPx: Int, thresholdPx: Int): Float {
    if (thresholdPx <= 0) return HEADER_COLLAPSED_SCALE
    val progress = (scrollPx.toFloat() / thresholdPx).coerceIn(0f, 1f)
    return 1f - ((1f - HEADER_COLLAPSED_SCALE) * progress)
}
