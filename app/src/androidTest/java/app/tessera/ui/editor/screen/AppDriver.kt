package app.tessera.ui.editor.screen

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.tessera.MainActivity
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.UiTestTags
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.ui.editor.engine.DocBinder
import app.tessera.ui.editor.engine.TesseraEditText
import kotlinx.coroutines.runBlocking
import org.wordpress.aztec.AztecText

/**
 * The whole app on the device, the way a user goes through it: the notes list of [MainActivity], a tap on a note card, the
 * editor, Back. Notes are put straight into the app's Room database ([seed]) and read back from it ([entity]) — what is saved is
 * what the database holds after the screen is gone.
 */
internal class AppDriver(private val compose: ComposeTestRule, private val dao: NoteDao) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    var scenario: ActivityScenario<MainActivity>? = null

    fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    fun close() {
        scenario?.close()
        scenario = null
    }

    /** A note as it was saved by an older build (legacy Markdown), synced, owned by nobody (a signed-out device). */
    fun seed(id: String, content: String, updatedAt: Long): NoteEntity {
        val e = NoteEntity(
            id = id, title = "", content = content, isPinned = false, createdAt = updatedAt, updatedAt = updatedAt,
            deletedAt = null, syncedAt = updatedAt, ownerId = null,
        )
        runBlocking { dao.upsert(e) }
        return e
    }

    fun entity(id: String): NoteEntity? = runBlocking { dao.getById(id) }

    /** [block] on the main thread; called there already (a helper inside a helper), it just runs. */
    fun <T> ui(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        var r: Result<T>? = null
        instrumentation.runOnMainSync { r = runCatching(block) }
        return r!!.getOrThrow()
    }

    fun activity(): Activity = ui {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull() ?: error("no resumed activity")
    }

    fun editorOrNull(): TesseraEditText? = ui {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            ?.window?.decorView?.let { find(it, TesseraEditText::class.java) }
            ?.takeIf { it.isAttachedToWindow && it.layout != null }
    }

    fun aztecOrNull(): AztecText? = ui {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            ?.window?.decorView?.let { find(it, AztecText::class.java) }
    }

    private fun <V : View> find(v: View, type: Class<V>): V? {
        if (type.isInstance(v)) return type.cast(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) find(v.getChildAt(i), type)?.let { return it }
        return null
    }

    fun awaitCard(id: String) = compose.waitUntil(15_000) { compose.onAllNodesWithTag(UiTestTags.noteCard(id)).fetchSemanticsNodes().isNotEmpty() }

    /** A tap on the note's card in the list; the native editor of that note, laid out. */
    fun open(id: String): TesseraEditText {
        tapCard(id)
        // a card still moving into its place (a new note, a pinned section appearing) takes the tap as a drag: tap once more
        val opened = runCatching { compose.waitUntil(5_000) { editorOrNull() != null } }.isSuccess
        if (!opened) tapCard(id)
        return awaitEditor()
    }

    fun tapCard(id: String) {
        awaitCard(id)
        compose.waitForIdle()
        Thread.sleep(600) // the list's item animations settle
        compose.onNodeWithTag(UiTestTags.noteCard(id)).performClick()
    }

    fun awaitEditor(): TesseraEditText {
        compose.waitUntil(15_000) { editorOrNull() != null }
        settle()
        return editorOrNull()!!
    }

    /** The editor's Back arrow; waits until the list is back. */
    fun back() {
        compose.onNodeWithTag(UiTestTags.EditorBack).performClick()
        compose.waitUntil(15_000) { editorOrNull() == null && aztecOrNull() == null }
        settle()
    }

    /** The system Back (gesture / key). */
    fun systemBack() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(15_000) { editorOrNull() == null && aztecOrNull() == null }
        settle()
    }

    /** Past the editor's 300 ms emit and the 500 ms autosave, with the database write done. */
    fun settle(ms: Long = 1_200) {
        instrumentation.waitForIdleSync()
        Thread.sleep(ms)
        instrumentation.waitForIdleSync()
        compose.waitForIdle() // the test's clock drives Compose: transitions run to their end only while it is advanced
    }

    fun ic(e: TesseraEditText): InputConnection = ui { e.onCreateInputConnection(EditorInfo())!! }

    fun ime(e: TesseraEditText, block: InputConnection.() -> Unit) {
        ui { e.onCreateInputConnection(EditorInfo())!!.block() }
        instrumentation.waitForIdleSync()
    }

    fun md(e: TesseraEditText) = ui { e.markdown() }
    fun text(e: TesseraEditText) = ui { e.text.toString() }
    fun kinds(e: TesseraEditText): List<ParagraphKind?> = ui { DocBinder.read(e.text!!).blocks.map { (it as? NoteBlock.Text)?.kind } }
}
