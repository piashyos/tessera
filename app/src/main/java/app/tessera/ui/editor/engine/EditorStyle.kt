package app.tessera.ui.editor.engine

import android.graphics.Typeface

/**
 * Everything the spans paint with: ARGB colors and px sizes, built from `MaterialTheme`, the tokens and the mockup by `NoteBody`
 * (Chunk 5); the engine never reads a theme itself and tests pass any values. Colors are painted as they are, so a translucent
 * fill (the quote fill is `primary` at 8% on the Aztec path) comes with its alpha already in. The real values of the geometry
 * fields (bar, gaps, paddings, radii) are filled by Chunk 5 from the mockup (`editor-content-*.png`) and `TesseraSpacing`.
 */
data class EditorStyle(
    // text
    val textColor: Int,
    /** `onSurfaceVariant`: text of a checked list item. */
    val mutedTextColor: Int,
    val bodyTextSizePx: Float,
    val titleTextSizePx: Float,
    val headingTextSizePx: Float,
    val subheadingTextSizePx: Float,
    /** Playfair Display for Title and Heading; `null` falls back to the platform serif. */
    val serifTypeface: Typeface? = null,
    // list markers
    val accentColor: Int,
    val checkboxTickColor: Int,
    val bulletRadiusPx: Float,
    val checkboxSizePx: Float,
    val checkboxCornerPx: Float,
    val checkboxStrokePx: Float,
    /** Space the marker takes before the text of a level-0 item. */
    val listMarkerAreaPx: Float,
    /** Extra indent per list level. */
    val listLevelStepPx: Float,
    // quote
    val quoteBarColor: Int,
    val quoteTextColor: Int,
    val quoteFillColor: Int,
    val quoteBarWidthPx: Float,
    /** Gap between the bar and the text; the text starts after bar + gap. */
    val quoteGapPx: Float,
    // mono
    val monoBackgroundColor: Int,
    val monoTextColor: Int,
    val monoCornerPx: Float,
    /** Text inset from the left edge of the block. */
    val monoPaddingHorizontalPx: Float,
    /** Space above the first and below the last line of a series. */
    val monoPaddingVerticalPx: Float,
    // inline code
    val codeBackgroundColor: Int,
    // objects
    val dividerColor: Int,
    val dividerThicknessPx: Float,
    val placeholderBorderColor: Int,
    val placeholderTextColor: Int,
    val placeholderCornerPx: Float,
    val placeholderPaddingPx: Float,
    /** Captions of the stage-1 placeholders ("Изображение" / "Таблица"), resolved by the host (D14). */
    val imageLabel: String,
    val tableLabel: String,
)
