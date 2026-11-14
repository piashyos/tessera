package app.tessera.ui.notes

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.LocaleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inspector.WindowInspector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.testing.WorkManagerTestInitHelper
import app.tessera.MainActivity
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.UserPreferences
import app.tessera.core.ui.UiTestTags
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.FolderDao
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import dagger.hilt.android.testing.HiltAndroidRule
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout

/**
 * The real app under Hilt, driven the way a finger and an eye meet it — with NO Compose test rule: under one, a long press in a text
 * field was seen not to work (memory `compose-test-rule-gesture-artifact`; the cause — the rule driving the clocks — is a guess).
 * Without it the app runs on its real clocks, and a long press on a `combinedClickable` chip works under instrumentation:
 * - what is on the screen: the unmerged semantics of every Compose root of the process (the activity, popups, dialogs), read on the
 *   main thread through [ViewRootForTest], with bounds on the screen;
 * - touches: real events injected into the window (`UiAutomation`), keys and text from the shell (`input`);
 * - colours: pixels of a screenshot.
 *
 * The database starts empty; the test puts folders, tags and notes straight into Room.
 */
abstract class AppearanceAppTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    /** A wait that never ends fails the test instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 1)
    val timeout: Timeout = Timeout.builder().withTimeout(6, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    @Inject lateinit var db: NotesDatabase
    @Inject lateinit var noteDao: NoteDao
    @Inject lateinit var tagDao: TagDao
    @Inject lateinit var folderDao: FolderDao
    @Inject lateinit var prefs: UserPreferences

    internal lateinit var d: AppearanceDevice
    protected val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUpApp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        hilt.inject()
        db.clearAllTables()
        ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        theme(ThemeMode.LIGHT)
        d = AppearanceDevice()
    }

    @After
    fun tearDownApp() {
        d.close()
    }

    /**
     * Light or dark, a palette, dynamic colour off (the palettes are what the user picks in Settings), the list as a list.
     * The live background is off by default (it tints flat colours and moves under a touch); `-e gradient on` runs a class with it
     * on, as a user has it — the gesture regression of the QA. Its animation is set every time: DataStore outlives a test.
     */
    protected fun theme(
        mode: ThemeMode,
        palette: ThemePalette = ThemePalette.Default,
        grain: Boolean = false,
        gradient: Boolean = InstrumentationRegistry.getArguments().getString("gradient") == "on",
        motion: Boolean = true,
    ): Unit = runBlocking {
        prefs.setLanguageTag("")
        prefs.setThemeMode(mode)
        prefs.setDynamicColor(false)
        prefs.setThemePalette(palette)
        prefs.setGrainEnabled(grain) // the film grain is noise over flat colours: off where pixels are compared
        prefs.setGradientEnabled(gradient)
        prefs.setBackgroundMotionEnabled(motion)
        prefs.setNotesViewMode(NotesViewMode.List)
    }

    private var clock = System.currentTimeMillis()

    protected fun folder(id: String, name: String, color: String? = null, icon: String? = null, parent: String? = null) = runBlocking {
        val at = clock++
        folderDao.upsert(FolderEntity(id = id, name = name, parentId = parent, createdAt = at, syncedAt = at, color = color, icon = icon))
    }

    /** A synced note of a signed-out device; newer notes come first in the list. */
    protected fun note(id: String, title: String, tags: List<String> = emptyList(), folder: String? = null, body: String = "Body of $title") =
        runBlocking {
            val at = clock++
            noteDao.upsert(
                NoteEntity(
                    id = id, title = title, content = body, isPinned = false, createdAt = at, updatedAt = at, deletedAt = null,
                    syncedAt = at, ownerId = null,
                ),
            )
            if (tags.isNotEmpty()) {
                tagDao.insertTags(tags.map { TagEntity(id = "tag-$it", name = it) })
                tagDao.linkTagsToNote(tagDao.findByNames(tags).map { NoteTagCrossRef(noteId = id, tagId = it.id) })
            }
            if (folder != null) folderDao.assignNote(NoteFolderCrossRef(noteId = id, folderId = folder))
        }

    protected fun tagStyle(name: String, color: String?, icon: String?) = runBlocking {
        tagDao.setAppearance(name, color, icon, clock++)
    }

    protected fun tagRow(name: String): TagEntity? = runBlocking { tagDao.findByNames(listOf(name)).firstOrNull() }

    protected fun folderRow(id: String): FolderEntity? = runBlocking { folderDao.getById(id) }

    protected fun s(res: Int): String = ctx.getString(res)

    // ------------------------------------------------------------------ the screen's parts

    /** The tag row of the list, shown with the toolbar's tag button. */
    protected fun showTagRow(firstTag: String) {
        d.tap(d.byTag(UiTestTags.TagFilterToggle).first())
        d.await("the tag row") { chip(firstTag) }
        d.settle(500)
    }

    /** The tag's chip in the filter row: the selectable node around its label ("#name", or "name" after an icon). */
    internal fun chip(tag: String): UiNode? {
        val all = d.nodes()
        val labels = all.filter { (it.text == "#$tag" || it.text == tag) && !it.inCard && !it.inPopup && d.onScreen(it) }
        return all.filter { n -> n.selected != null && labels.any { n.bounds.contains(it.bounds) } }
            .minByOrNull { it.bounds.width() * it.bounds.height() }
    }

    protected fun drawerOpen() = d.texts(s(R.string.drawer_folders_section)).any { d.onScreen(it) }

    protected fun openDrawer() {
        d.tap(d.descs(s(R.string.folders_open_drawer)).first { d.onScreen(it) })
        d.awaitTrue("the drawer open") { drawerOpen() }
        d.settle(500)
    }

    /** The drawer → "Settings": the main settings screen. [str] reads a string in the app's language (Russian shots). */
    protected fun openSettings(str: (Int) -> String = ::s) {
        d.tap(d.await("the drawer button") { d.descs(str(R.string.folders_open_drawer)).firstOrNull { d.onScreen(it) } })
        // the drawer slides in: a tap on its row while it moves can miss
        d.awaitTrue("the drawer open") { d.texts(str(R.string.drawer_folders_section)).any { d.onScreen(it) } }
        d.settle(500)
        d.tap(d.await("Settings in the drawer") { d.texts(str(R.string.settings_title)).firstOrNull { d.onScreen(it) } })
        d.await("the settings", 15_000) { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.settle(800)
    }

    /** Settings → "Appearance" (its row is the first of "General", on the first screen). */
    protected fun openAppearance() {
        d.tap(d.await("the Appearance row") { d.byTag(UiTestTags.SettingsAppearanceRow).firstOrNull { d.onScreen(it) } })
        d.await("the Appearance screen", 15_000) { d.byTag(UiTestTags.AppearanceRoot).firstOrNull() }
        d.settle(800)
    }

    /** The drawer row's name (not a card title, not the popup's preview). */
    internal fun folderRowLabel(name: String): UiNode? = d.texts(name).firstOrNull { d.onScreen(it) && !it.inCard && !it.inPopup }

    /** "⋮" of the folder [name] in the open drawer: its menu. */
    protected fun openFolderMenu(name: String) {
        val row = d.await("the drawer row \"$name\"") { folderRowLabel(name) }
        val more = d.descs(s(R.string.folders_more)).filter { d.onScreen(it) }.minByOrNull { abs(it.bounds.centerY() - row.bounds.centerY()) }
            ?: error("no \"${s(R.string.folders_more)}\" in the drawer")
        d.tap(more)
        d.await("the folder menu") { d.texts(s(R.string.folders_edit)).firstOrNull { d.onScreen(it) } }
        d.settle(400)
    }

    /** "⋮" → "Edit" of the folder [name]: the appearance popup with the name field. */
    protected fun editFolder(name: String) {
        openFolderMenu(name)
        d.tap(d.texts(s(R.string.folders_edit)).first { d.onScreen(it) })
        d.await("the folder popup") { d.nodes().firstOrNull { it.inPopup && it.text == s(R.string.folders_edit_title) } }
        d.settle(500)
    }

    /** The popup's name field: its top-most text field (the emoji field is below). */
    internal fun nameField(): UiNode = d.nodes().filter { it.inPopup && it.editable }.minByOrNull { it.bounds.top } ?: error("no name field")

    /** A popup button; when the keyboard covers it, the keyboard is put away first (Back), as a user would. */
    protected fun tapPopupButton(label: String) {
        fun button() = d.nodes().firstOrNull { it.inPopup && it.text == label } ?: error("no \"$label\" in the popup")
        val ime = d.imeTop()
        if (ime != null && button().bounds.bottom > ime) {
            d.log("\"$label\" ${button().bounds} is under the keyboard (top $ime): Back puts the keyboard away")
            d.back()
            d.awaitTrue("the keyboard down") { d.imeTop() == null }
        }
        d.tap(button())
    }

    /** The popup's content scrolled (a finger dragging it up) until [pick] is in view above the buttons, as a user scrolls to it. */
    internal fun revealInPopup(what: String, pick: () -> UiNode?): UiNode {
        repeat(8) {
            val n = pick() ?: error("$what: not in the popup")
            // the top of the buttons' row: the "Save" label sits 10 dp inside its 40 dp button, the row has 20 dp padding
            val limit = d.nodes().first { it.inPopup && it.text == s(R.string.folders_save) }.bounds.top - d.dp(32f)
            val card = d.byTag(UiTestTags.PopupCard).first().bounds
            d.log("reveal $what: ${n.bounds}, buttons' top limit $limit, card $card")
            if (n.bounds.top >= 0 && n.bounds.bottom <= limit) return n
            // from the middle of the card (a drag that starts at the very bottom edge of the scrolling part, by the buttons, does not scroll)
            val y0 = (card.top + limit) / 2
            d.drag(card.centerX(), y0, (y0 - d.dp(140f)).coerceAtLeast(card.top + d.dp(8f)))
        }
        error("$what: not scrolled into view")
    }

    /** Back until the keyboard is down (a field of the popup keeps the focus; the popup stays). */
    protected fun keyboardDown() {
        if (d.imeTop() == null) return
        d.back()
        d.awaitTrue("the keyboard down") { d.imeTop() == null }
    }
}

/** One semantics node as the screen shows it. [tags]: its own test tag and those of its ancestors. */
internal data class UiNode(
    val text: String,
    val desc: String,
    val tag: String?,
    val tags: List<String>,
    val selected: Boolean?,
    val editable: Boolean,
    val bounds: Rect,
    /** A switch's state: on / off, `null` for a node that is not toggleable. */
    val toggled: Boolean? = null,
) {
    val inCard get() = tags.any { it.startsWith("note_card_") }
    val inPopup get() = UiTestTags.PopupCard in tags
}

internal class AppearanceDevice {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val ctx get() = instrumentation.targetContext
    val ua = instrumentation.uiAutomation.also {
        it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }
    private var scenario: ActivityScenario<MainActivity>? = null
    val density get() = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * density).roundToInt()

    init {
        shell("mkdir -p $ShotDir")
    }

    // ------------------------------------------------------------------ the app

    /** The app from a cold start, until the notes list is there (a loaded host takes long: generous wait). */
    fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await("the notes list", 60_000) { nodes().firstOrNull { it.tag == UiTestTags.NotesListRoot } }
        settle(800)
    }

    fun close() {
        scenario?.close()
        scenario = null
    }

    fun <T> ui(block: () -> T): T {
        var r: Result<T>? = null
        instrumentation.runOnMainSync { r = runCatching(block) }
        return r!!.getOrThrow()
    }

    fun activity(): Activity? = ui { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull() }

    fun settle(ms: Long = 500) {
        instrumentation.waitForIdleSync()
        Thread.sleep(ms)
        instrumentation.waitForIdleSync()
    }

    // ------------------------------------------------------------------ what is on the screen

    fun nodes(): List<UiNode> = ui {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()?.window?.decorView?.let {
            screenW = it.width
            screenH = it.height
        }
        val roots = mutableListOf<ViewRootForTest>()
        WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow && it.isShown }.forEach { collect(it, roots) }
        buildList { roots.forEach { walk(it.semanticsOwner.unmergedRootSemanticsNode, emptyList(), this) } }
    }

    private fun collect(v: View, out: MutableList<ViewRootForTest>) {
        if (v is ViewRootForTest) { out += v; return }
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), out)
    }

    private fun walk(n: SemanticsNode, parentTags: List<String>, out: MutableList<UiNode>) {
        val c = n.config
        val tag = c.getOrNull(SemanticsProperties.TestTag)
        val tags = if (tag != null) parentTags + tag else parentTags
        val p = n.positionOnScreen
        out += UiNode(
            text = listOfNotNull(
                c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text },
                c.getOrNull(SemanticsProperties.EditableText)?.text,
            ).joinToString(""),
            desc = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty(),
            tag = tag,
            tags = tags,
            selected = c.getOrNull(SemanticsProperties.Selected),
            editable = SemanticsProperties.EditableText in c,
            bounds = Rect(p.x.roundToInt(), p.y.roundToInt(), (p.x + n.size.width).roundToInt(), (p.y + n.size.height).roundToInt()),
            toggled = c.getOrNull(SemanticsProperties.ToggleableState)?.let { it == ToggleableState.On },
        )
        n.children.forEach { walk(it, tags, out) }
    }

    /** The activity window's size, as of the last [nodes]: after `wm size` the instrumentation's own resources may hold the old metrics. */
    @Volatile private var screenW = 0
    @Volatile private var screenH = 0
    fun screenWidth() = screenW.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels
    fun screenHeight() = screenH.takeIf { it > 0 } ?: ctx.resources.displayMetrics.heightPixels

    /** Inside the screen horizontally: a closed drawer keeps its rows composed, off to the left. */
    fun onScreen(n: UiNode) = n.bounds.left >= 0 && n.bounds.right <= screenWidth() + 1 && n.bounds.width() > 0

    fun texts(s: String) = nodes().filter { it.text == s }
    fun descs(s: String) = nodes().filter { it.desc == s }
    fun byTag(tag: String) = nodes().filter { it.tag == tag }

    /** The card of note [id]; the last one in drawing order (search results lie over the list). */
    fun card(id: String): UiNode? = byTag(UiTestTags.noteCard(id)).lastOrNull()
    fun popupOpen() = byTag(UiTestTags.PopupCard).isNotEmpty()

    fun <T : Any> await(what: String, ms: Long = 10_000, probe: () -> T?): T {
        val until = SystemClock.uptimeMillis() + ms
        while (true) {
            probe()?.let { return it }
            if (SystemClock.uptimeMillis() > until) error("$what: not within $ms ms; texts on screen: ${visibleTexts()}")
            Thread.sleep(150)
        }
    }

    fun awaitTrue(what: String, ms: Long = 10_000, cond: () -> Boolean) {
        await(what, ms) { if (cond()) Unit else null }
        settle(400)
    }

    fun visibleTexts() = nodes().filter { (it.text.isNotEmpty() || it.desc.isNotEmpty()) && onScreen(it) }
        .joinToString(" | ") { it.text.ifEmpty { "[${it.desc}]" } }

    // ------------------------------------------------------------------ touches, keys

    private fun inject(down: Long, at: Long, action: Int, x: Int, y: Int) {
        val ev = MotionEvent.obtain(down, at, action, x.toFloat(), y.toFloat(), 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        ua.injectInputEvent(ev, true)
        ev.recycle()
    }

    fun tap(x: Int, y: Int, settleMs: Long = 600) {
        val t = SystemClock.uptimeMillis()
        inject(t, t, MotionEvent.ACTION_DOWN, x, y)
        inject(t, t + 60, MotionEvent.ACTION_UP, x, y)
        settle(settleMs)
    }

    fun tap(n: UiNode, settleMs: Long = 600) {
        check(onScreen(n) && n.bounds.centerY() in 0..screenHeight()) { "\"${n.text}${n.desc}\" is off the screen: ${n.bounds}" }
        tap(n.bounds.centerX(), n.bounds.centerY(), settleMs)
    }

    /** A slow vertical drag (one move per frame, held still before lifting: a scroll, not a fling). */
    fun drag(x: Int, y0: Int, y1: Int, steps: Int = 20) {
        val t0 = SystemClock.uptimeMillis()
        inject(t0, t0, MotionEvent.ACTION_DOWN, x, y0)
        for (i in 1..steps) {
            val at = t0 + i * 16L
            while (SystemClock.uptimeMillis() < at) Thread.sleep(1)
            inject(t0, at, MotionEvent.ACTION_MOVE, x, y0 + (y1 - y0) * i / steps)
        }
        Thread.sleep(150)
        inject(t0, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y1)
        settle(500)
    }

    /** A finger held still for [ms] (the platform's long-press timeout is 400 ms). */
    fun longPress(n: UiNode, ms: Long = 1_000) {
        val (x, y) = n.bounds.centerX() to n.bounds.centerY()
        val t = SystemClock.uptimeMillis()
        inject(t, t, MotionEvent.ACTION_DOWN, x, y)
        Thread.sleep(ms)
        inject(t, t + ms, MotionEvent.ACTION_UP, x, y)
        settle(700)
    }

    fun shell(cmd: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes().decodeToString() }

    fun key(vararg codes: String) {
        shell("input keyevent ${codes.joinToString(" ")}")
        settle(400)
    }

    /** Characters as a hardware keyboard sends them to the focused field (ASCII only). */
    fun type(s: String) {
        shell("input text " + s.replace(" ", "%s"))
        settle(500)
    }

    /** The focused field emptied: the caret to its end, then Backspace. */
    fun clearField(chars: Int = 24) {
        key("KEYCODE_MOVE_END")
        key(*Array(chars) { "KEYCODE_DEL" })
    }

    /** [s] on the clipboard; waits until the system's clipboard chip (bottom left, it takes touches) is gone. */
    fun setClip(s: String) {
        ui { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("qa", s)) }
        val until = SystemClock.uptimeMillis() + 15_000
        Thread.sleep(500)
        while (shell("dumpsys window windows").contains("ClipboardOverlay") && SystemClock.uptimeMillis() < until) Thread.sleep(300)
    }

    fun back() = key("KEYCODE_BACK")

    // ------------------------------------------------------------------ insets, keyboard

    fun statusBarBottom(): Int = activity()?.let { a -> ui { a.window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top } } ?: 0

    /** The keyboard window's top on the screen, `null` when it is not up. */
    fun imeTop(): Int? = ua.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { w -> Rect().also { w.getBoundsInScreen(it) } }?.takeIf { !it.isEmpty }?.top

    /** The keyboard up and still (its slide-in done). */
    fun awaitIme(): Int {
        var last: Int? = null
        var stable = 0
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            val top = imeTop()
            if (top != null && top == last) { if (++stable >= 3) break } else stable = 0
            last = top
            Thread.sleep(150)
        }
        settle(400)
        return imeTop() ?: error(
            "no on-screen keyboard. On an AVD with a hardware keyboard: settings put secure show_ime_with_hard_keyboard 1 " +
                "and settings put secure stylus_handwriting_enabled 0",
        )
    }

    // ------------------------------------------------------------------ pixels

    fun screenshot(): Bitmap = (ua.takeScreenshot() ?: error("no screenshot")).let {
        if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
    }

    /** The whole screen as `adb shell screencap` sees it, to [ShotDir]/[name].png (pulled by the QA report). */
    fun save(name: String) {
        shell("screencap -p $ShotDir/$name.png")
        Log.i(TAG, "shot $name")
    }

    fun log(msg: String) = Log.i(TAG, msg)

    companion object {
        const val ShotDir = "/data/local/tmp/tag-folder-appearance-qa"
        const val TAG = "TagFolderAppearance"
    }
}

/** The colour most pixels of [r] have (a chip's or a card's fill: text and icons are thin). */
internal fun Bitmap.mode(r: Rect): Int {
    val counts = HashMap<Int, Int>()
    for (y in r.top.coerceAtLeast(0) until r.bottom.coerceAtMost(height)) {
        for (x in r.left.coerceAtLeast(0) until r.right.coerceAtMost(width)) counts.merge(getPixel(x, y), 1, Int::plus)
    }
    return counts.maxByOrNull { it.value }?.key ?: error("empty region $r")
}

internal fun Bitmap.count(r: Rect, pred: (Int) -> Boolean): Int {
    var n = 0
    for (y in r.top.coerceAtLeast(0) until r.bottom.coerceAtMost(height)) {
        for (x in r.left.coerceAtLeast(0) until r.right.coerceAtMost(width)) if (pred(getPixel(x, y))) n++
    }
    return n
}

internal fun near(px: Int, c: Color, tol: Int = 6): Boolean {
    val e = c.toArgb()
    return abs(android.graphics.Color.red(px) - android.graphics.Color.red(e)) <= tol &&
        abs(android.graphics.Color.green(px) - android.graphics.Color.green(e)) <= tol &&
        abs(android.graphics.Color.blue(px) - android.graphics.Color.blue(e)) <= tol
}

internal fun hex(px: Int) = "#%06X".format(px and 0xFFFFFF)
internal fun hex(c: Color) = hex(c.toArgb())
