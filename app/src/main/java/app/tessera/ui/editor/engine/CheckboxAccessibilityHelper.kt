package app.tessera.ui.editor.engine

import android.widget.CheckBox
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Spec §6.10: every visible check box of the text is a virtual node of the editor — "checkbox, checked / not checked", a double tap
 * toggles it (not in read-only). The text itself is read as by a plain `EditText`: the host node is the view's own. The id of a node
 * is the start offset of its paragraph.
 */
internal class CheckboxAccessibilityHelper(private val host: TesseraEditText) : ExploreByTouchHelper(host) {

    override fun getVirtualViewAt(x: Float, y: Float): Int = host.checkboxZoneAt(x, y)?.ps ?: INVALID_ID

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        host.checkboxZones().forEach { virtualViewIds += it.ps }
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
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
        val done = host.toggleCheckAt(virtualViewId)
        if (done) invalidateVirtualView(virtualViewId)
        return done
    }
}
