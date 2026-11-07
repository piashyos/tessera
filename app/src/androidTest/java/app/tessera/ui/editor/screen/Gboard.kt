package app.tessera.ui.editor.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * The device's real keyboard (Gboard), pressed through the shell at the keys it exposes to accessibility — the same events a finger
 * makes. Gboard's keys are nodes of the input-method window: letters by their character, "Space", "Delete", "Shift", the action key;
 * the suggestion strip shows candidate words as text.
 */
internal class Gboard(private val app: AppDriver) {
    private val ua = app.instrumentation.uiAutomation.also {
        it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    private fun imeRoot(): AccessibilityNodeInfo? = ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = buildList {
        fun walk(n: AccessibilityNodeInfo) { add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::walk) }
        root?.let(::walk)
    }

    private fun bounds(n: AccessibilityNodeInfo) = Rect().also { n.getBoundsInScreen(it) }

    /** The keys by their description, once the keyboard stopped sliding in. */
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

    /** The keyboard window is up and has stopped sliding in, whatever its layout (the key names follow the layout's language). */
    fun awaitWindow() {
        val until = SystemClock.uptimeMillis() + 15_000
        var last: Rect? = null
        while (SystemClock.uptimeMillis() < until) {
            val r = ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.let { w -> Rect().also { w.getBoundsInScreen(it) } }
            if (r != null && !r.isEmpty && r == last) return
            last = r
            Thread.sleep(300)
        }
        error("no keyboard window")
    }

    /**
     * A key by its description; letters match in either case (Gboard names them "H" while Shift is on). The keys are named in the
     * app's language: "Space" / "Delete" find their Russian names too.
     */
    fun key(desc: String): Rect? {
        val names = ALIASES[desc] ?: listOf(desc)
        return nodes(imeRoot()).firstOrNull { n -> names.any { n.contentDescription?.toString().equals(it, ignoreCase = desc.length == 1) } }?.let(::bounds)
    }

    /** The action key of a multi-line field (Enter). */
    fun enterKey(): Rect = listOf("Enter", "Return", "New line", "Next line", "Ввод").firstNotNullOfOrNull { key(it) }
        ?: nodes(imeRoot()).filter { it.contentDescription != null }.maxBy { bounds(it).let { b -> b.right + b.bottom } }.let(::bounds)

    fun tap(r: Rect) = shell("input tap ${r.centerX()} ${r.centerY()}")

    fun press(desc: String) = tap(key(desc) ?: error("no key \"$desc\"; keys: ${descriptions()}"))

    /** Letters and spaces, one key at a time. */
    fun type(s: String) = s.forEach { ch -> press(if (ch == ' ') "Space" else ch.toString()) }

    /** Candidate words in the suggestion strip: texts above the first row of keys (the digits on the keys are hints, not candidates). */
    fun suggestions(): List<Pair<String, Rect>> {
        val firstRow = (key("q") ?: key("й"))?.top ?: return emptyList()
        return nodes(imeRoot()).mapNotNull { n ->
            val t = n.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val b = bounds(n)
            if (b.bottom > firstRow) null else t to b
        }
    }

    fun descriptions(): List<String> = nodes(imeRoot()).mapNotNull { it.contentDescription?.toString() }

    /** A glide over the letters of [word] (one finger, down on the first, through the others, up on the last). */
    fun glide(word: String) {
        val points = word.map { key(it.toString()) ?: error("no key $it") }
        run("input motionevent DOWN ${points[0].centerX()} ${points[0].centerY()}")
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            for (k in 1..4) run("input motionevent MOVE ${a.centerX() + (b.centerX() - a.centerX()) * k / 4} ${a.centerY() + (b.centerY() - a.centerY()) * k / 4}")
        }
        shell("input motionevent UP ${points.last().centerX()} ${points.last().centerY()}")
    }

    fun log(msg: String) = Log.i("NativeEditorGboard", msg)

    private fun run(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }

    private fun shell(cmd: String) {
        run(cmd)
        app.instrumentation.waitForIdleSync()
        Thread.sleep(250)
        app.instrumentation.waitForIdleSync()
    }

    private companion object {
        val ALIASES = mapOf("Space" to listOf("Space", "Пробел"), "Delete" to listOf("Delete", "Удалить"))
    }
}
