package app.tessera.ui.editor.components

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.TableZone
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.tableZones
import kotlin.math.abs

/** What the screen's contextual table bar shows while a cell holds the focus (the native editor), and what its buttons do. */
class TableBarState(
    val canRemoveRow: Boolean,
    val canRemoveColumn: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onAddRow: () -> Unit,
    val onRemoveRow: () -> Unit,
    val onAddColumn: () -> Unit,
    val onRemoveColumn: () -> Unit,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onDeleteTable: () -> Unit,
)

/** The theme the overlays are drawn in: they are separate compositions and know nothing of the screen's. */
internal class OverlayTheme(val colors: ColorScheme, val typography: Typography, val shapes: Shapes)

/**
 * The editor and, over each table of its text, an overlay with the table's cells ([TableBlockEditor], phase 2 spec §4 variant A, plan E1).
 *
 * - **Placement.** The span reserves the table's room, the overlay sits on it: before every draw of the editor (the same pass, so a scroll never
 *   shows the overlay a frame late) each overlay gets the rect of its table. The height goes the other way: an overlay's measured height is what
 *   the span reserves, in the same layout pass.
 * - **Touch.** A vertical drag that starts on an overlay scrolls the document (the overlay would keep the gesture): once it is clearly vertical
 *   the host takes it and hands it to the editor. A tap, and a sideways drag, stay with the cells.
 * - **Edits.** A cell edit is shown at once and committed as one step of the history when its cell loses the focus, after a pause of
 *   [COMMIT_PAUSE_MS], or before any command ([commitCells]).
 * - **Keyboard.** A cell that gets the focus at the bottom edge is lifted above the keyboard ([requestChildRectangleOnScreen]).
 */
internal class TableOverlayHost(context: Context, val editor: TesseraEditText) : FrameLayout(context) {
    var theme by mutableStateOf<OverlayTheme?>(null)

    /** The contextual bar of the screen: the state while a cell has the focus, `null` otherwise. */
    var onTableBar: ((TableBarState?) -> Unit)? = null

    private var overlays = ArrayList<Overlay>()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var overlayGesture = false
    private var dragging = false
    private var lastReveal: Pair<View, Rect>? = null

    init {
        addView(editor, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private val placeBeforeDraw = android.view.ViewTreeObserver.OnPreDrawListener { place(); true }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(placeBeforeDraw)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(placeBeforeDraw)
        super.onDetachedFromWindow()
    }

    /** Commits every cell edit that is waiting (leaving the screen, a command of the editor, the bar). */
    fun commitCells() {
        for (o in overlays) o.commit()
    }

    /**
     * An overlay belongs to a table, not to a place in the list: one that shows a grid keeps it when the text is rebuilt around it (a collaborator
     * inserted a table above), so the cell that has the focus stays in its own table. First by the grid (the nearest to where the overlay was), then
     * what is left by order; an overlay left without a table goes.
     */
    private fun place() {
        val zones = editor.tableZones()
        val claimed = arrayOfNulls<Overlay>(zones.size)
        val free = ArrayList(overlays)
        for (o in overlays) {
            val g = o.committed ?: continue
            val zi = zones.indices.filter { claimed[it] == null && zones[it].grid == g }.minByOrNull { abs(it - o.index) } ?: continue
            claimed[zi] = o
            free -= o
        }
        for (zi in zones.indices) if (claimed[zi] == null) {
            claimed[zi] = if (free.isNotEmpty()) free.removeAt(0) else Overlay(zones[zi].grid).also { addView(it, LayoutParams(zones[zi].rect.width().coerceAtLeast(1), ViewGroup.LayoutParams.WRAP_CONTENT)) }
        }
        for (o in free) { o.commit(); o.release(); removeView(o) }
        overlays = ArrayList(claimed.map { it!! })
        zones.forEachIndexed { i, z -> overlays[i].place(i, z) }
        // an overlay that was just added is measured now, so its height is the span's in this pass and not only after the next frame
        for (o in overlays) if (o.height == 0 && o.isAttachedToWindow) {
            val w = o.layoutParams.width.coerceAtLeast(1)
            o.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (o.measuredHeight > 0) o.layout(0, 0, w, o.measuredHeight)
        }
    }

    /** An overlay is as high as its table: it is measured without a limit (the room of the host is not the table's), the note scrolls with it. */
    override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int, parentHeightMeasureSpec: Int, heightUsed: Int) {
        if (child !is Overlay) return super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
        val lp = child.layoutParams as MarginLayoutParams
        val w = getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin + widthUsed, lp.width)
        child.measure(w, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
    }

    /** What a cell asks of the host while its table is on the screen (the screen's bar shows it): only the overlay that has the focus. */
    private var focusedOverlay: Overlay? = null

    private fun publish() = onTableBar?.invoke(focusedOverlay?.barState())

    /** The handles of the editor's own caret are not for a cell that has the focus. */
    private fun cellFocusChanged(o: Overlay, focused: Boolean) {
        if (focused) focusedOverlay = o else if (focusedOverlay === o) focusedOverlay = null
        editor.handlesHidden = focusedOverlay != null
        publish()
    }

    // ------------------------------------------------------------------ touch

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; dragging = false
                overlayGesture = overlays.any { it.contains(ev.x, ev.y) }
            }
            MotionEvent.ACTION_MOVE ->
                if (overlayGesture && !dragging && abs(ev.y - downY) > slop && abs(ev.y - downY) > abs(ev.x - downX)) {
                    dragging = true
                    // the editor never saw the start of this gesture: it gets it now, from where the finger went down
                    val down = MotionEvent.obtain(ev.downTime, ev.downTime, MotionEvent.ACTION_DOWN, downX, downY, 0)
                    editor.dispatchTouchEvent(down)
                    down.recycle()
                    editor.dispatchTouchEvent(ev)
                }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> overlayGesture = false
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(ev)
        editor.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) { dragging = false; overlayGesture = false }
        return true
    }

    // ------------------------------------------------------------------ keyboard

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // the keyboard took room: the cell that has the focus is lifted again
        if (h < oldh) lastReveal?.let { (child, r) -> if (overlays.any { it === child && it.cellFocused }) reveal(child, r) }
    }

    /** A cell asks to be shown (the compose root forwards it up the views): the editor scrolls so that the cell is above the bottom edge. */
    override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
        if (child !is Overlay) return super.requestChildRectangleOnScreen(child, rect, immediate)
        lastReveal = child to Rect(rect)
        return reveal(child, rect)
    }

    private fun reveal(child: View, rect: Rect): Boolean {
        val margin = (16 * resources.displayMetrics.density).toInt()
        val bottom = child.translationY + rect.bottom + margin
        val top = child.translationY + rect.top - margin
        val l = editor.layout
        val maxScroll = ((l?.height ?: 0) + editor.totalPaddingTop + editor.totalPaddingBottom - editor.height).coerceAtLeast(0)
        val delta = when {
            bottom > height -> (bottom - height).toInt()
            top < 0 -> top.toInt()
            else -> 0
        }
        val target = (editor.scrollY + delta).coerceIn(0, maxScroll)
        if (target != editor.scrollY) editor.scrollTo(0, target)
        return true
    }

    // ------------------------------------------------------------------ the overlay of one table

    private inner class Overlay(first: TableGrid) : FrameLayout(context) {
        var index = 0
        private val compose = ComposeView(context)
        var ps = -1
        var committed: TableGrid? = first
        private var shown by mutableStateOf<TableGrid?>(first)
        var dirty = false
            private set
        private var anyCellFocused = false
        var cellFocused by mutableStateOf(false)
            private set
        private var readOnly by mutableStateOf(false)
        private val commitRunnable = Runnable { commit() }

        init {
            addView(compose, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            compose.setContent {
                val th = theme
                val grid = shown
                if (th != null && grid != null) MaterialTheme(colorScheme = th.colors, typography = th.typography, shapes = th.shapes) {
                    TableBlockEditor(
                        grid = grid,
                        onGridChange = ::edit,
                        modifier = Modifier.fillMaxWidth(),
                        onCellFocusChanged = ::onCellFocus,
                        readOnly = readOnly,
                        enterMovesOn = true,
                        onEnterInLastCell = { commit(); editor.focusAfterTable(ps) },
                    )
                }
            }
            addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ -> editor.reportTableHeight(index, b - t) }
        }

        fun release() {
            removeCallbacks(commitRunnable)
            if (focusedOverlay === this) cellFocusChanged(this, false)
            compose.disposeComposition()
        }

        fun contains(x: Float, y: Float): Boolean =
            visibility == VISIBLE && x >= translationX && x <= translationX + width && y >= translationY && y <= translationY + height

        /** Puts the overlay on the table's rect and brings what it shows up to the document (an undo, an update from outside). */
        fun place(i: Int, z: TableZone) {
            index = i
            val moved = ps != z.ps
            ps = z.ps
            readOnly = !editor.editable
            var fromDocument = moved
            if (z.grid != committed) { // the document's table is not the one last seen
                if (dirty) commit() // an edit that waits is the user's and wins (spec §6.9); the host has committed it before any update from outside
                else {
                    committed = z.grid
                    shown = z.grid
                    fromDocument = true
                    if (cellFocused) compose.clearFocus() // the table under the focus is another one now
                    // the accessibility tree learns the new text of the cells (an undo or a redo of an edit)
                    compose.post { compose.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) }
                }
            }
            if (fromDocument && focusedOverlay === this) publish() // what the bar can do (move up, move down) depends on where the table is
            val w = z.rect.width().coerceAtLeast(1)
            if (layoutParams.width != w) layoutParams = layoutParams.also { it.width = w }
            translationX = z.rect.left.toFloat()
            translationY = z.rect.top.toFloat()
            // the note's area clips the table as it clips the text: what is above or below the host is not drawn (no table over the screen's header)
            val top = (-z.rect.top).coerceAtLeast(0)
            val bottom = (this@TableOverlayHost.height - z.rect.top).coerceAtMost(maxOf(height, 1))
            clipBounds = if (bottom > top) Rect(0, top, maxOf(w, width), bottom) else Rect(0, 0, 0, 0)
        }

        fun edit(g: TableGrid) {
            if (g == shown) return
            shown = g
            dirty = true
            removeCallbacks(commitRunnable)
            postDelayed(commitRunnable, COMMIT_PAUSE_MS)
            if (focusedOverlay === this) publish() // the bar's buttons depend on the grid
        }

        /** The edit that waits becomes a step of the history (and the Markdown goes out). */
        fun commit() {
            removeCallbacks(commitRunnable)
            if (!dirty) return
            dirty = false
            val g = shown ?: return
            committed = g
            editor.replaceTable(ps, g)
        }

        private fun onCellFocus(focused: Boolean) {
            anyCellFocused = focused
            if (focused) {
                cellFocused = true
                // TextView pulls its selection into view whenever the layout changes, focus or not: with the caret on the table's own paragraph that
                // keeps the table in view while the keyboard comes and a cell grows (without it the document jumps to the caret, wherever it was)
                if (editor.selectionStart != ps || editor.selectionEnd != ps) editor.setSelection(ps)
                cellFocusChanged(this, true)
                return
            }
            commit() // the focus left a cell: its edit is done
            post { // the focus may only be on its way to the next cell
                if (!anyCellFocused) { cellFocused = false; cellFocusChanged(this, false) }
            }
        }

        private fun structure(f: (TableGrid) -> TableGrid) {
            val g = shown ?: return
            edit(f(g))
            commit()
        }

        /** What the screen's table bar shows and does for this table. */
        fun barState(): TableBarState? {
            val g = shown ?: return null
            return TableBarState(
                canRemoveRow = g.rowCount > 0,
                canRemoveColumn = g.columnCount > 1,
                canMoveUp = editor.canMoveTable(ps, -1),
                canMoveDown = editor.canMoveTable(ps, +1),
                onAddRow = { structure { it.addRow() } },
                onRemoveRow = { structure { it.removeRow(it.rowCount - 1) } },
                onAddColumn = { structure { it.addColumn() } },
                onRemoveColumn = { structure { it.removeColumn(it.columnCount - 1) } },
                onMoveUp = { commit(); clearFocus(); editor.moveTable(ps, -1) },
                onMoveDown = { commit(); clearFocus(); editor.moveTable(ps, +1) },
                onDeleteTable = { dirty = false; removeCallbacks(commitRunnable); clearFocus(); editor.deleteObjectAt(ps) },
            )
        }
    }

    private companion object {
        const val COMMIT_PAUSE_MS = 500L
    }
}
