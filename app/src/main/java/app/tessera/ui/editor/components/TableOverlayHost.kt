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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.TableZone
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.paragraphEnd
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

    private val overlays = ArrayList<Overlay>()
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

    private fun place() {
        val zones = editor.tableZones()
        while (overlays.size < zones.size) overlays += Overlay(overlays.size).also { addView(it, LayoutParams(1, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        while (overlays.size > zones.size) removeView(overlays.removeAt(overlays.lastIndex).also { it.commit(); it.release() })
        zones.forEachIndexed { i, z -> overlays[i].place(z) }
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
        android.util.Log.i("GATE", "reveal rect=$rect ty=${child.translationY} hostH=$height delta=$delta scrollY=${editor.scrollY} max=$maxScroll -> $target")
        if (target != editor.scrollY) editor.scrollTo(0, target)
        return true
    }

    // ------------------------------------------------------------------ the overlay of one table

    private inner class Overlay(val index: Int) : FrameLayout(context) {
        private val compose = ComposeView(context)
        var ps = -1
        private var committed: TableGrid? = null
        private var shown by mutableStateOf<TableGrid?>(null)
        private var dirty = false
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
                LaunchedEffect(cellFocused, shown, ps) { publishBar() }
            }
            addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ -> editor.reportTableHeight(index, b - t) }
        }

        fun release() {
            removeCallbacks(commitRunnable)
            if (cellFocused) onTableBar?.invoke(null)
            compose.disposeComposition()
        }

        fun contains(x: Float, y: Float): Boolean =
            visibility == VISIBLE && x >= translationX && x <= translationX + width && y >= translationY && y <= translationY + height

        /** Puts the overlay on the table's rect and brings what it shows up to the document (an undo, an update from outside). */
        fun place(z: TableZone) {
            ps = z.ps
            readOnly = !editor.editable
            if (z.grid != committed) { // the document's table is not the one last seen: it wins over what is shown, unless an edit waits
                committed = z.grid
                if (!dirty) shown = z.grid else { dirty = false; removeCallbacks(commitRunnable); shown = z.grid }
            }
            val w = z.rect.width().coerceAtLeast(1)
            if (layoutParams.width != w) layoutParams = layoutParams.also { it.width = w }
            translationX = z.rect.left.toFloat()
            translationY = z.rect.top.toFloat()
        }

        fun edit(g: TableGrid) {
            if (g == shown) return
            shown = g
            dirty = true
            removeCallbacks(commitRunnable)
            postDelayed(commitRunnable, COMMIT_PAUSE_MS)
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
                return
            }
            commit() // the focus left a cell: its edit is done
            post { if (!anyCellFocused) cellFocused = false } // the focus may only be on its way to the next cell
        }

        private fun structure(f: (TableGrid) -> TableGrid) {
            val g = shown ?: return
            edit(f(g))
            commit()
        }

        private fun publishBar() {
            val cb = onTableBar ?: return
            val g = shown
            if (!cellFocused || g == null) { cb(null); return }
            val t = editor.text
            val len = t?.length ?: 0
            val pe = if (t == null) ps else paragraphEnd(t, ps)
            cb(
                TableBarState(
                    canRemoveRow = g.rowCount > 0,
                    canRemoveColumn = g.columnCount > 1,
                    canMoveUp = ps > 0,
                    canMoveDown = pe + 1 < len, // under the table there is more than the empty paragraph that always ends the text
                    onAddRow = { structure { it.addRow() } },
                    onRemoveRow = { structure { it.removeRow(it.rowCount - 1) } },
                    onAddColumn = { structure { it.addColumn() } },
                    onRemoveColumn = { structure { it.removeColumn(it.columnCount - 1) } },
                    onMoveUp = { commit(); clearFocus(); editor.moveTable(ps, -1) },
                    onMoveDown = { commit(); clearFocus(); editor.moveTable(ps, +1) },
                    onDeleteTable = { dirty = false; removeCallbacks(commitRunnable); clearFocus(); editor.deleteObjectAt(ps) },
                ),
            )
        }
    }

    private companion object {
        const val COMMIT_PAUSE_MS = 500L
    }
}
