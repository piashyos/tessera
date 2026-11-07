package app.tessera.ui.editor.screen

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performSemanticsAction
import app.tessera.R
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 spec §3.3: the "Image" and "Camera" buttons of the native bar, through the system's own screens — the photo picker and the
 * emulator's camera app, pressed at the controls they expose to accessibility, as a finger would. The picture comes back to the editor as
 * its own paragraph after the caret's, stored as the app's file with its row.
 */
@HiltAndroidTest
class NativeImageGalleryTest : NativeAppTest() {
    @Inject lateinit var imageDao: NoteImageDao

    private val ctx get() = app.instrumentation.targetContext
    private val ids = ArrayList<String>()
    private val media = ArrayList<Uri>()

    private val ua by lazy {
        app.instrumentation.uiAutomation.also { it.serviceInfo = it.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS } }
    }

    @After
    fun clean() {
        ImageFixtures.removeApp(ctx, ids)
        media.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
    }

    private fun log(msg: String) = Log.i(TAG, msg)

    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun walk(n: AccessibilityNodeInfo) { add(n); for (i in 0 until n.childCount) n.getChild(i)?.let(::walk) }
        ua.windows.forEach { w -> w.root?.let(::walk) }
    }

    private fun label(n: AccessibilityNodeInfo) = (n.contentDescription ?: n.text)?.toString() ?: ""

    private fun shell(cmd: String) = ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }

    private fun tap(n: AccessibilityNodeInfo) {
        val r = Rect().also { n.getBoundsInScreen(it) }
        shell("input tap ${r.centerX()} ${r.centerY()}")
    }

    /** Presses through another app's screens: [pick] chooses what to press among the nodes on screen, until the editor is back. */
    private fun throughSystemScreens(what: String, pick: (List<AccessibilityNodeInfo>) -> AccessibilityNodeInfo?): TesseraEditText {
        val until = SystemClock.uptimeMillis() + 40_000
        var pressed = 0
        while (SystemClock.uptimeMillis() < until) {
            app.editorOrNull()?.let { if (pressed > 0) return it }
            val ns = nodes().filter { it.packageName?.toString() != ctx.packageName }
            val n = pick(ns)
            if (n != null) {
                log("$what: press \"${label(n)}\" (${n.packageName})")
                tap(n)
                pressed++
                Thread.sleep(1_500)
            } else {
                Thread.sleep(500)
            }
        }
        error("$what: the editor did not come back; on screen: ${nodes().map { "${it.packageName}:${label(it)}" }.filter { !it.endsWith(":") }.distinct().take(60)}")
    }

    private fun openForInsert(id: String): TesseraEditText {
        app.launch()
        app.seed(id, "# Insert\nabove one\nbelow", System.currentTimeMillis())
        val e = app.open(id)
        app.ui { e.requestFocus(); e.setSelection(e.text.toString().indexOf("above one") + "above one".length) }
        app.ui {
            val imm = e.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            imm.showSoftInput(e, 0)
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(ctx.getString(R.string.toolbar_image))).fetchSemanticsNodes().isNotEmpty() }
        return e
    }

    private fun checkInserted(id: String, how: String) {
        val e = app.awaitEditor()
        val until = SystemClock.uptimeMillis() + 15_000
        while (!Regex("tessera-img://[0-9a-f-]{36}").containsMatchIn(app.md(e)) && SystemClock.uptimeMillis() < until) Thread.sleep(100)
        val md = app.md(e)
        log("$how: ${md.replace("\n", "\\n")}")
        val newId = Regex("tessera-img://([0-9a-f-]{36})").find(md)?.groupValues?.get(1)
        assertNotNull("$how: a picture was inserted: $md", newId)
        ids += newId!!
        assertTrue("$how: as its own paragraph after the caret's: $md", md.startsWith("# Insert\nabove one\n![](tessera-img://$newId)\nbelow"))
        assertTrue("$how: the file is the app's", ImageFixtures.appFile(ctx, newId).exists())
        assertNotNull("$how: the row", runBlocking { imageDao.byId(newId) })
        val until2 = SystemClock.uptimeMillis() + 15_000
        while (app.ui { shownImages(e).firstOrNull()?.loaded } != true && SystemClock.uptimeMillis() < until2) Thread.sleep(100)
        assertTrue("$how: the picture is shown: ${app.ui { shownImages(e) }}", app.ui { shownImages(e).firstOrNull()?.loaded } == true)
        app.back()
        assertEquals("$how: saved", md, app.entity(id)?.content)
    }

    @Test
    fun aPictureFromTheGalleryIsInserted() {
        // a photo in the shared Pictures, the newest one: the first the picker shows
        val b = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(ImageFixtures.FILL) }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "qa-gallery-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/native-editor-qa")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        media += uri

        val id = "qa-ins-gallery"
        openForInsert(id)
        compose.onNode(hasContentDescription(ctx.getString(R.string.toolbar_image))).performSemanticsAction(SemanticsActions.OnClick)
        throughSystemScreens("photo picker") { ns ->
            // the newest first: the one put there above ("Photo taken on <date>"; the tab is "Photos")
            ns.filter { it.isVisibleToUser }.firstOrNull { label(it).startsWith("Photo taken", ignoreCase = true) }
        }
        checkInserted(id, "gallery")
    }

    @Test
    fun aPictureFromTheCameraIsInserted() {
        ua.grantRuntimePermission(ctx.packageName, Manifest.permission.CAMERA)
        val id = "qa-ins-camera"
        openForInsert(id)
        compose.onNode(hasContentDescription(ctx.getString(R.string.toolbar_camera))).performSemanticsAction(SemanticsActions.OnClick)
        val buttons = listOf("Shutter", "Done", "OK", "Allow", "While using the app", "Only this time", "Next", "Got it", "Skip")
        throughSystemScreens("camera") { ns ->
            ns.filter { it.isVisibleToUser && it.isEnabled }.firstOrNull { n -> buttons.any { label(n).equals(it, ignoreCase = true) } }
        }
        checkInserted(id, "camera")
    }

    private companion object {
        const val TAG = "NativeImageGallery"
    }
}
