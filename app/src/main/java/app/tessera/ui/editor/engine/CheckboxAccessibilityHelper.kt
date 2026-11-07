package app.tessera.ui.editor.engine

import android.widget.CheckBox
import android.widget.ImageView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import app.tessera.R

/**
 * Spec §6.10: every visible check box of the text is a virtual node of the editor — "checkbox, checked / not checked", a double tap
 * toggles it (not in read-only). Phase 2 §2: so is every image — "Image", a double tap selects it (not in read-only). The text itself is
 * read as by a plain `EditText`: the host node is the view's own. The id of a node is the start offset of its paragraph.
 */
internal class CheckboxAccessibilityHelper(private val host: TesseraEditText) : ExploreByTouchHelper(host) {

    override fun getVirtualViewAt(x: Float, y: Float): Int =
        host.checkboxZoneAt(x, y)?.ps ?: host.imageZones().firstOrNull { it.rect.contains(x.toInt(), y.toInt()) }?.ps ?: host.tableZones().firstOrNull { it.rect.contains(x.toInt(), y.toInt()) }?.ps ?: INVALID_ID

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        host.checkboxZones().forEach { virtualViewIds += it.ps }
        host.imageZones().forEach { virtualViewIds += it.ps }
        host.tableZones().forEach { virtualViewIds += it.ps }
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        host.imageZones().firstOrNull { it.ps == virtualViewId }?.let { image ->
            node.className = ImageView::class.java.name
            node.contentDescription = host.context.getString(R.string.editor_object_image_a11y)
            node.setBoundsInParent(image.rect)
            if (host.editable) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
            return
        }
        host.tableZones().firstOrNull { it.ps == virtualViewId }?.let { table -> // the cells are the overlay's own nodes; this one says what the table is
            val res = host.resources
            node.className = android.widget.GridView::class.java.name
            node.contentDescription = host.context.getString(
                R.string.editor_object_table_a11y,
                res.getQuantityString(R.plurals.editor_table_rows, table.grid.rowCount + 1, table.grid.rowCount + 1),
                res.getQuantityString(R.plurals.editor_table_columns, table.grid.columnCount, table.grid.columnCount),
            )
            node.setBoundsInParent(table.rect)
            return
        }
        val zone = host.checkboxZones().firstOrNull { it.ps == virtualViewId }
        if (zone == null) { // the node went with a scroll or an edit; the framework wants a valid one anyway
            node.contentDescription = " "
            node.setBoundsInParent(android.graphics.Rect(0, 0, 1, 1))
            return
        }
        node.className = CheckBox::class.java.name
        node.isCheckable = true
        node.isChecked = zone.checked
        if (zone.label.isEmpty()) node.contentDescription = " " else node.text = zone.label // no new strings: an empty item has only its state
        node.setBoundsInParent(zone.rect)
        if (host.editable) node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: android.os.Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !host.editable) return false
        if (host.imageZones().any { it.ps == virtualViewId }) { // a double tap on an image selects it, as a tap does (no keyboard)
            host.requestFocus() // the handles are shown for an editor that has the focus
            host.setSelection(virtualViewId, virtualViewId + 1)
            return true
        }
        val done = host.toggleCheckAt(virtualViewId)
        if (done) invalidateVirtualView(virtualViewId)
        return done
    }
}
