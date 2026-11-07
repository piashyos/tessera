package app.tessera.ui.editor.components

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** The keyboard of the device for the table tests (P2-2): `screen/Gboard` needs an AppDriver, this one only the instrumentation. Plus the focused field and the IME top. */
internal class TableGboard(private val instr: Instrumentation) {
    val ua = instr.uiAutomation.also {
        it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    private fun imeWindow() = ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    private fun imeRoot(): AccessibilityNodeInfo? = imeWindow()?.root
    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = buildList {
        fun walk(n: AccessibilityNodeInfo) { add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::walk) }
        root?.let(::walk)
    }
    private fun bounds(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }

    /** Top of the keyboard window on the screen, `null` when it is not shown. */
    fun imeTop(): Int? = imeWindow()?.let { w -> Rect().also { w.getBoundsInScreen(it) } }?.takeIf { !it.isEmpty }?.top

    /** Bounds (screen) of the field that has the input focus, in any window of the app. */
    fun focusedField(): Rect? = ua.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .firstNotNullOfOrNull { it.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }?.let(::bounds)

    fun awaitShown() {
        val until = SystemClock.uptimeMillis() + 15_000
        var last: Rect? = null
        while (SystemClock.uptimeMillis() < until) {
            val r = key("Delete")
            if (r != null && r == last) return
            last = r
            Thread.sleep(300)
        }
        error("Gboard did not show")
    }

    fun key(desc: String): Rect? {
        val names = ALIASES[desc] ?: listOf(desc)
        return nodes(imeRoot()).firstOrNull { n -> names.any { n.contentDescription?.toString().equals(it, ignoreCase = desc.length == 1) } }?.let(::bounds)
    }

    fun enterKey(): Rect = listOf("Enter", "Return", "New line", "Next line", "Ввод").firstNotNullOfOrNull { key(it) }
        ?: nodes(imeRoot()).filter { it.contentDescription != null }.maxBy { bounds(it).let { b -> b.right + b.bottom } }.let(::bounds)

    fun tap(x: Int, y: Int) = shell("input tap $x $y")
    fun tap(r: Rect) = tap(r.centerX(), r.centerY())
    fun press(desc: String) = tap(key(desc) ?: error("no key \"$desc\"; keys: ${descriptions()}"))
    fun type(s: String) = s.forEach { ch -> press(if (ch == ' ') "Space" else ch.toString()) }
    fun descriptions(): List<String> = nodes(imeRoot()).mapNotNull { it.contentDescription?.toString() }

    fun suggestions(): List<String> {
        val firstRow = (key("q") ?: key("й"))?.top ?: return emptyList()
        return nodes(imeRoot()).mapNotNull { n -> n.text?.toString()?.takeIf { it.isNotBlank() && bounds(n).bottom <= firstRow } }
    }

    fun run(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }

    private fun shell(cmd: String) {
        run(cmd)
        instr.waitForIdleSync()
        Thread.sleep(300)
        instr.waitForIdleSync()
    }

    private companion object {
        val ALIASES = mapOf("Space" to listOf("Space", "Пробел"), "Delete" to listOf("Delete", "Удалить"))
    }
}
