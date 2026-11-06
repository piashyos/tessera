package app.tessera.ui.editor.engine

/** Any ARGB values and px sizes: the engine only paints with what it is given. */
object EngineTestStyle {
    const val TEXT = 0xFF111111.toInt()
    const val MUTED = 0xFF777777.toInt()
    const val ACCENT = 0xFFCC3300.toInt()
    const val TICK = 0xFFFFFFFF.toInt()
    const val QUOTE_BAR = 0xFF00AA00.toInt()
    const val QUOTE_FILL = 0xFFCCFFCC.toInt()
    const val MONO_BG = 0xFFDDDDFF.toInt()
    const val DIVIDER = 0xFF0000FF.toInt()

    fun create(imageLabel: String = "Image", tableLabel: String = "Table") = EditorStyle(
        textColor = TEXT,
        mutedTextColor = MUTED,
        bodyTextSizePx = 20f,
        titleTextSizePx = 34f,
        headingTextSizePx = 26f,
        subheadingTextSizePx = 23f,
        accentColor = ACCENT,
        checkboxTickColor = TICK,
        bulletRadiusPx = 4f,
        checkboxSizePx = 18f,
        checkboxCornerPx = 3f,
        checkboxStrokePx = 2f,
        listMarkerAreaPx = 30f,
        listLevelStepPx = 24f,
        quoteBarColor = QUOTE_BAR,
        quoteTextColor = 0xFF333333.toInt(),
        quoteFillColor = QUOTE_FILL,
        quoteBarWidthPx = 4f,
        quoteGapPx = 12f,
        monoBackgroundColor = MONO_BG,
        monoTextColor = 0xFF222222.toInt(),
        monoCornerPx = 12f,
        monoPaddingHorizontalPx = 10f,
        monoPaddingVerticalPx = 10f,
        titleSpaceTopPx = 12f,
        titleSpaceBottomPx = 8f,
        headingSpaceTopPx = 12f,
        headingSpaceBottomPx = 6f,
        blockEdgePx = 10f,
        listItemPaddingPx = 3f,
        quotePaddingVerticalPx = 4f,
        codeBackgroundColor = 0xFFEEEEEE.toInt(),
        dividerColor = DIVIDER,
        dividerThicknessPx = 2f,
        placeholderBorderColor = 0xFF999999.toInt(),
        placeholderTextColor = MUTED,
        placeholderCornerPx = 8f,
        placeholderPaddingPx = 8f,
        imageLabel = imageLabel,
        tableLabel = tableLabel,
    )
}
