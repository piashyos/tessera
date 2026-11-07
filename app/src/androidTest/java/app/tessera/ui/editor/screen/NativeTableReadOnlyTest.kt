package app.tessera.ui.editor.screen

import android.app.LocaleManager
import android.os.LocaleList
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.NoteBody
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Phase 2 spec §2 (read-only, accessibility) for tables: a viewer of a shared note sees the table, its cells do not take the focus or the keyboard,
 * the wide table still scrolls sideways; TalkBack finds the table as one node "Table, N rows, M columns" (Russian plurals too) in the tree a screen
 * reader gets, without an action for a viewer. The app needs a signed-in viewer for this; the body is the same `NoteBody(editable = false)`.
 */
@HiltAndroidTest
class NativeTableReadOnlyTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private fun log(msg: String) = Log.i(TAG, msg)
    private val ctx get() = app.instrumentation.targetContext
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val wide = TableGrid(
        listOf("Name", "Qty", "Price", "Shop", "Aisle", "Note"),
        listOf(listOf("apple", "3", "1.20", "market", "4", "red"), listOf("pear", "5", "0.90", "corner", "1", "green")),
    )
    // the content of NoteBodyTableGateTest (12 paragraphs, the table, 12 more): in this rig a table second in a short note got no overlay content
    private val md = note(*(1..12).map(::lorem).toTypedArray(), wide, *(13..24).map(::lorem).toTypedArray())

    private fun viewer(editable: Boolean = false): Pair<ActivityScenario<ComponentActivity>, TesseraEditText> {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { a ->
            // the rig of NoteBodyTableGateTest: edge to edge, the bars and the keyboard as padding
            WindowCompat.setDecorFitsSystemWindows(a.window, false)
            a.setContent {
                TesseraTheme(darkTheme = false, dynamicColor = false, palette = ThemePalette.WARM) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
                        NoteBody(
                            markdown = md, onMarkdownChange = {}, onActionsReady = {}, onFormatStateChanged = {}, editable = editable,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
        compose.waitUntil(10_000) { app.editorOrNull() != null }
        return scenario to app.editorOrNull()!!
    }

    /** The node TalkBack gets for the table: the one whose description starts with [prefix]. */
    private fun tableNode(t: Tables, prefix: String): AccessibilityNodeInfo? {
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.contentDescription?.toString()?.startsWith(prefix) == true) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)?.let { return it }
            return null
        }
        repeat(20) {
            t.ua.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.forEach { w -> w.root?.let(::walk)?.let { return it } }
            Thread.sleep(250)
        }
        return null
    }

    @Test
    fun aViewerSeesTheTableCannotEditItAndScrollsItSideways() {
        val (scenario, e) = viewer()
        try {
            val t = Tables(app)
            t.scrollTableTo(e, (200 * ctx.resources.displayMetrics.density).toInt())
            t.awaitOverlays(e, 1)
            val table = t.onScreen(e)
            val shown = t.cells(table).map { it.first }
            t.tapCell("apple")
            val focused = t.focused()
            val ime = t.imeShown(e)
            fun leftHeader(): String {
                val cells = t.cells(t.onScreen(e)).filter { it.second.width() > 8 }
                val top = cells.minOf { it.second.top }
                return cells.filter { it.second.top == top }.minBy { it.second.left }.first
            }
            val r0 = leftHeader()
            t.shell("input swipe ${table.right - dp(40)} ${table.centerY()} ${table.right - dp(240)} ${table.centerY()} 300")
            app.settle(600)
            val r1 = leftHeader()
            val node = tableNode(t, "Table,")
            log("viewer: cells $shown; after a tap focused ${focused?.first} at ${focused?.second}, keyboard $ime; sideways the header at the left edge \"$r0\" -> \"$r1\"; TalkBack \"${node?.contentDescription}\" actions ${node?.actionList?.map { it.id }}")
            assertTrue("the viewer sees the table: $shown", shown.containsAll(listOf("Name", "apple", "pear")))
            assertFalse("a cell does not take the focus: ${focused?.first}", focused != null && Tables(app).cells(table).any { it.second == focused.second })
            assertFalse("no keyboard", ime)
            assertTrue("the wide table scrolls sideways: the header at its left edge \"$r0\" -> \"$r1\"", r0 != r1)
            assertNotNull("TalkBack finds the table", node)
            assertEquals("Table, 3 rows, 6 columns", node!!.contentDescription.toString())
            assertTrue("no action for a viewer: ${node.actionList.map { it.id }}", node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        } finally {
            scenario.close()
        }
    }

    @Test
    fun talkBackSaysTheTableInRussian() {
        ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("ru")
        Thread.sleep(1_500) // the new locale reaches the app before its activity starts
        try {
            val (scenario, e0) = viewer(editable = true)
            try {
                val t = Tables(app)
                t.scrollTableTo(e0, (200 * ctx.resources.displayMetrics.density).toInt())
                t.awaitOverlays(e0, 1)
                val node = tableNode(t, "Таблица,")
                log("ru: TalkBack \"${node?.contentDescription}\"")
                assertEquals("Таблица, 3 строки, 6 столбцов", node?.contentDescription?.toString())
            } finally {
                scenario.close()
            }
        } finally {
            ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        }
    }

    private companion object {
        const val TAG = "NativeTableReadOnly"
    }
}
