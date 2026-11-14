package app.tessera.ui.notes

import android.graphics.Color
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.tessera.TesseraDrawerItemTags
import app.tessera.data.local.entity.NoteEntity
import app.tessera.ui.ShotDir
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer marks the section the list shows (spec 2026-11-13-appearance-screen-and-motion §6), in the real app: the user opens Trash
 * (Archive, Pinned), goes to Settings from the drawer, comes back with Back and opens the drawer — the list is still that section, and
 * the drawer must mark it, not "All notes".
 *
 * The mark is only a background (no `selected` in the semantics), so the test reads the rows' pixels: of the four system rows exactly
 * one has a colour of its own at its right end (the others show the drawer's sheet), and it must be the section's. The same read before
 * Settings is the control of the measurement.
 */
@HiltAndroidTest
class DrawerSelectionAfterSettingsTest : AppearanceAppTest() {

    private val sections = listOf(R.string.folders_all_notes, R.string.drawer_pinned, R.string.notes_archive, R.string.drawer_trash)

    @Test
    fun trashStaysMarkedAfterSettingsAndBack() = staysMarked(R.string.drawer_trash, "n-trash")

    @Test
    fun archiveStaysMarkedAfterSettingsAndBack() = staysMarked(R.string.notes_archive, "n-archive")

    @Test
    fun pinnedStaysMarkedAfterSettingsAndBack() = staysMarked(R.string.drawer_pinned, "n-pinned")

    private fun staysMarked(section: Int, only: String) {
        theme(ThemeMode.LIGHT)
        seed()
        d.launch()
        val name = s(section)
        openDrawer()
        d.tap(row(name))
        d.awaitTrue("the drawer closed") { !drawerOpen() }
        d.awaitTrue("the list shows $name: only $only") { shows(only) }
        openDrawer()
        assertEquals("before Settings: the drawer marks $name (the control of the read)", listOf(name), marked())

        d.tap(d.texts(s(R.string.settings_title)).first { d.onScreen(it) })
        d.await("Settings", 15_000) { d.byTag(UiTestTags.SettingsRoot).firstOrNull() }
        d.settle(800)
        d.back()
        d.await("the list after Back") { d.byTag(UiTestTags.NotesListRoot).firstOrNull() }
        val kept = runCatching { d.awaitTrue("the list still on $name", 5_000) { shows(only) } }.isSuccess
        assertTrue("after Settings → Back the list still shows $name (only $only): ${d.visibleTexts()}", kept)
        openDrawer()
        d.shell("screencap -p $ShotDir/drawer-${only.removePrefix("n-")}-after-settings.png")
        assertEquals("after Settings → Back the drawer marks $name", listOf(name), marked())
    }

    /** A plain note and one of each section: pinned, archived, in the trash. */
    private fun seed() = runBlocking {
        fun entity(id: String, pinned: Boolean = false, archived: Boolean = false, deleted: Boolean = false) = NoteEntity(
            id = id, title = id.removePrefix("n-").replaceFirstChar { it.uppercase() } + " note", content = "Body of $id",
            isPinned = pinned, createdAt = 1L, updatedAt = 1L, deletedAt = if (deleted) 2L else null, syncedAt = 1L, ownerId = null,
            isArchived = archived,
        )
        noteDao.upsert(entity("n-plain"))
        noteDao.upsert(entity("n-pinned", pinned = true))
        noteDao.upsert(entity("n-archive", archived = true))
        noteDao.upsert(entity("n-trash", deleted = true))
    }

    private val ids = listOf("n-plain", "n-pinned", "n-archive", "n-trash")

    /** The list shows the card of [only] and no other of the seeded notes. */
    private fun shows(only: String) = d.card(only) != null && ids.filter { it != only }.none { d.card(it) != null }

    private fun row(label: String) = d.byTag(TesseraDrawerItemTags.row(label)).first { d.onScreen(it) }

    /** The system rows with a colour of their own at their right end (inside the rounded mark, past the label). */
    private fun marked(): List<String> {
        val shot = d.screenshot()
        val px = sections.map(::s).associateWith { label -> row(label).bounds.let { shot.getPixel(it.right - d.dp(12f), it.centerY()) } }
        val sheet = px.values.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
        d.log("drawer rows' right ends: ${px.mapValues { hex(it.value) }}, the sheet ${hex(sheet)}")
        return px.filter { (_, c) -> step(c, sheet) > 2 }.keys.toList()
    }

    private fun step(a: Int, b: Int) =
        maxOf(abs(Color.red(a) - Color.red(b)), abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))
}
