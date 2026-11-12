package app.tessera.ui.notes

import android.graphics.Rect
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.AccentColor
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tags and folders get a colour and an icon (spec 2026-11-11-tag-folder-appearance §6.4), on the device, the way the user meets them:
 * - a long press on a tag chip of the filter row opens its appearance popup and does not toggle the filter; a tap filters;
 * - a tag's colour and icon (Lucide and emoji, both chosen in the popup) show in the filter row, on the note card, in the editor and
 *   among the popular tags of search;
 * - "⋮" → "Edit" in the drawer: the new name, colour and icon show in the drawer at once, the drawer never closes (plan D7);
 * - a live folder's name (in another case) is refused with an error and the app goes on; a just deleted folder's name is free;
 * - cards of a coloured folder are tinted in "All Notes" (a subfolder without a colour takes its parent's), not in the folder itself,
 *   and tinted again in search from inside the folder.
 *
 * Touches are injected into the window and the app runs on its real clocks (see [AppearanceAppTest]). The device needs a real on-screen
 * keyboard: on an AVD with a hardware keyboard set `show_ime_with_hard_keyboard 1` and `stylus_handwriting_enabled 0`.
 */
@HiltAndroidTest
class TagFolderAppearanceTest : AppearanceAppTest() {
    private val dark = false // the tests run in the light theme

    // ------------------------------------------------------------------ §6.4-1

    @Test
    fun aLongPressOnATagChipOpensItsPopupAndLeavesTheFilterAndATapFilters() {
        note("n-alpha", "Apricot", tags = listOf("alpha"))
        note("n-beta", "Blueberry", tags = listOf("beta"))
        d.launch()
        showTagRow("alpha")
        assertEquals("the chip starts off", false, chip("alpha")?.selected)

        d.longPress(chip("alpha")!!)
        d.await("the appearance popup of #alpha") { d.nodes().firstOrNull { it.inPopup && it.text == "#alpha" } }
        d.settle(500)
        d.save("tag-long-press-popup")
        assertEquals("the long press did not toggle the filter", false, chip("alpha")?.selected)
        assertNotNull("both notes are still listed", d.card("n-beta"))

        d.back()
        d.awaitTrue("Back closes the popup") { !d.popupOpen() }
        assertNull("closing saved nothing", tagRow("alpha")?.color)
        assertEquals("still no filter after the popup", false, chip("alpha")?.selected)

        d.tap(chip("alpha")!!)
        d.awaitTrue("a tap filters the list to #alpha") { d.card("n-beta") == null }
        assertNotNull("the #alpha note stays", d.card("n-alpha"))
        assertEquals("the chip is on", true, chip("alpha")?.selected)
        assertFalse("a tap opens no popup", d.popupOpen())
        d.save("tag-tap-filters")

        d.tap(chip("alpha")!!)
        d.awaitTrue("a second tap lifts the filter") { d.card("n-beta") != null }
    }

    // ------------------------------------------------------------------ §6.4-2

    @Test
    fun aTagsColourAndIconShowInTheFilterRowOnTheCardInTheEditorAndInSearch() {
        note("n-tags", "Apricot", tags = listOf("alpha", "beta"))
        d.launch()
        showTagRow("alpha")

        // Red and the Lucide star, picked in the popup
        d.longPress(chip("alpha")!!)
        d.await("the popup of #alpha") { d.nodes().firstOrNull { it.inPopup && it.text == "#alpha" } }
        d.tap(d.descs(s(R.string.color_red)).single { it.inPopup })
        d.tap(d.descs(s(R.string.icon_star)).single { it.inPopup })
        d.save("tag-popup-red-star")
        tapPopupButton(s(R.string.folders_save))
        d.awaitTrue("Save closes the popup") { !d.popupOpen() }
        d.awaitTrue("#alpha is stored red with a star") { tagRow("alpha")?.color == "red" }
        assertEquals("lucide:star", tagRow("alpha")?.icon)

        // Blue and an emoji pasted into the emoji field
        d.longPress(chip("beta")!!)
        d.await("the popup of #beta") { d.nodes().firstOrNull { it.inPopup && it.text == "#beta" } }
        d.tap(d.descs(s(R.string.color_blue)).single { it.inPopup })
        d.setClip(House)
        d.tap(revealInPopup("the emoji field") { d.nodes().singleOrNull { it.inPopup && it.editable } })
        d.key("KEYCODE_PASTE")
        d.awaitTrue("the emoji is in the field") { d.nodes().any { it.inPopup && it.editable && it.text == House } }
        d.save("tag-popup-blue-emoji")
        tapPopupButton(s(R.string.folders_save))
        d.awaitTrue("Save closes the popup") { !d.popupOpen() }
        d.awaitTrue("#beta is stored blue with the emoji") { tagRow("beta")?.color == "blue" }
        assertEquals("emoji:$House", tagRow("beta")?.icon)
        d.settle(800)

        // the filter row
        checkTag("filter row", "alpha", AccentColor.Red, Mark.Lucide) { !it.inCard && !it.inPopup }
        checkTag("filter row", "beta", AccentColor.Blue, Mark.Emoji) { !it.inCard && !it.inPopup }
        d.save("tags-filter-row-and-card")
        // the card
        checkTag("card", "alpha", AccentColor.Red, Mark.Lucide) { it.inCard }
        checkTag("card", "beta", AccentColor.Blue, Mark.Emoji) { it.inCard }

        // the editor (TagChipsExpandable)
        d.tap(d.card("n-tags")!!)
        d.await("the editor", 60_000) { d.byTag(UiTestTags.EditorRoot).firstOrNull() }
        d.awaitTrue("the list left") { d.byTag(UiTestTags.NotesListRoot).isEmpty() }
        d.settle(1_000)
        checkTag("editor", "alpha", AccentColor.Red, Mark.Lucide) { UiTestTags.EditorRoot in it.tags }
        checkTag("editor", "beta", AccentColor.Blue, Mark.Emoji) { UiTestTags.EditorRoot in it.tags }
        d.save("tags-editor")
        repeat(3) { if (d.byTag(UiTestTags.EditorRoot).isNotEmpty()) d.back() }
        d.awaitTrue("back in the list", 15_000) { d.byTag(UiTestTags.EditorRoot).isEmpty() && d.byTag(UiTestTags.NotesListRoot).isNotEmpty() }

        // search: popular tags
        d.tap(d.texts(s(R.string.search_hint)).first { d.onScreen(it) })
        // the section header is drawn in capitals
        val header = d.await("the popular tags") {
            d.nodes().firstOrNull { it.text.equals(s(R.string.search_popular_tags), ignoreCase = true) && d.onScreen(it) }
        }
        d.settle(800)
        keyboardDown()
        val below: (UiNode) -> Boolean = { !it.inCard && !it.inPopup && it.bounds.top > header.bounds.bottom }
        checkTag("search", "alpha", AccentColor.Red, Mark.Lucide, below)
        checkTag("search", "beta", AccentColor.Blue, Mark.Emoji, below)
        d.save("tags-search-popular")
    }

    private enum class Mark { Lucide, Emoji }

    /**
     * The tag reads in its colour with its icon where the "#" was: no "#name" label, a "name" label on the tag's container colour, and left
     * of it the icon — Lucide strokes in the tag's content colour, or the emoji (its own text, its own colours).
     */
    private fun checkTag(place: String, tag: String, accent: AccentColor, mark: Mark, where: (UiNode) -> Boolean) {
        val all = d.nodes()
        assertTrue("$place: \"#$tag\" is still written — the icon replaces the #", all.none { it.text == "#$tag" && where(it) && d.onScreen(it) })
        val label = all.firstOrNull { it.text == tag && where(it) && d.onScreen(it) } ?: error("$place: no \"$tag\"; on screen: ${d.visibleTexts()}")
        val shot = d.screenshot()
        val container = accent.container(dark)
        val fill = shot.mode(label.bounds)
        d.log("$place $tag: label ${label.bounds}, fill ${hex(fill)}, ${accent.key} container ${hex(container)}")
        assertTrue("$place: \"$tag\" sits on ${accent.key} (${hex(container)}), found ${hex(fill)}", near(fill, container))
        when (mark) {
            Mark.Lucide -> {
                // the icon lies between the chip's start padding and the label (16 dp in the row and search, 12 dp on the card and in the editor)
                val zone = Rect(label.bounds.left - d.dp(22f), label.bounds.centerY() - d.dp(7f), label.bounds.left - 1, label.bounds.centerY() + d.dp(7f))
                val strokes = shot.count(zone) { near(it, accent.content(dark), 40) }
                d.log("$place $tag: ${accent.key} content ${hex(accent.content(dark))} in the icon zone $zone: $strokes px")
                assertTrue("$place: a Lucide icon in ${accent.key}'s content colour left of \"$tag\" ($strokes px in $zone)", strokes >= 8)
            }
            Mark.Emoji -> {
                val emoji = all.firstOrNull {
                    it.text == House && where(it) && it.bounds.right <= label.bounds.left + 2 && abs(it.bounds.centerY() - label.bounds.centerY()) < d.dp(10f)
                } ?: error("$place: no $House before \"$tag\"")
                val own = shot.count(emoji.bounds) { !near(it, container, 24) && !near(it, accent.content(dark), 40) }
                d.log("$place $tag: emoji ${emoji.bounds}, $own px of its own colours")
                assertTrue("$place: the emoji is drawn left of \"$tag\" ($own px of its own colours in ${emoji.bounds})", own >= 10)
            }
        }
    }

    // ------------------------------------------------------------------ §6.4-3, D7

    @Test
    fun editFromTheFolderMenuShowsTheNewNameColourAndIconInTheDrawerThatStaysOpen() {
        folder("f-errands", "Errands")
        note("n-receipt", "Receipt", folder = "f-errands")
        d.launch()
        openDrawer()
        // the glyph before: the default filled folder — its middle is ink (the Lucide briefcase is hollow there, between its two uprights)
        val coreBefore = core(folderRowLabel("Errands")!!)
        editFolder("Errands")
        assertTrue("the drawer stays open under the popup (D7)", drawerOpen())
        assertEquals("the field holds the folder's name", "Errands", nameField().text)

        d.tap(nameField())
        d.awaitIme()
        d.clearField()
        d.type("Projects")
        d.awaitTrue("the new name typed") { nameField().text == "Projects" }
        keyboardDown()
        assertTrue("Back put the keyboard away, the popup stays", d.popupOpen())
        d.tap(d.descs(s(R.string.color_green)).single { it.inPopup })
        d.tap(d.descs(s(R.string.icon_briefcase)).single { it.inPopup })
        d.save("folder-edit-popup")
        tapPopupButton(s(R.string.folders_save))
        d.awaitTrue("Save closes the popup") { !d.popupOpen() }
        assertTrue("the drawer is still open after Save (D7)", drawerOpen())

        val row = d.await("the renamed row") { folderRowLabel("Projects") }
        assertNull("the old name is gone from the drawer", folderRowLabel("Errands"))
        val zone = iconZone(row)
        val green = AccentColor.Green.content(dark)
        val strokes = d.screenshot().count(zone) { near(it, green, 40) }
        val coreAfter = core(row)
        d.log("drawer row Projects ${row.bounds}: green content ${hex(green)} in the icon zone $zone: $strokes px; ink in the icon's middle: before (folder) $coreBefore, after $coreAfter")
        d.save("folder-edit-drawer-after")
        assertTrue("the row's icon is drawn in green ($strokes px in $zone)", strokes >= 8)
        assertTrue("before: the default filled folder (ink in its middle $coreBefore)", coreBefore >= 0.8)
        assertTrue("after: the hollow briefcase, not the folder (ink in its middle $coreAfter)", coreAfter <= 0.3)

        val f = folderRow("f-errands")!!
        assertEquals("Projects", f.name)
        assertEquals("green", f.color)
        assertEquals("lucide:briefcase", f.icon)
    }

    /** A drawer row's icon: 18 dp, 12 dp left of the name. */
    private fun iconZone(row: UiNode) =
        Rect(row.bounds.left - d.dp(32f), row.bounds.centerY() - d.dp(9f), row.bounds.left - d.dp(10f), row.bounds.centerY() + d.dp(9f))

    /** The share of a 3 dp square in the middle of the row's icon (21 dp left of the name) that is not the row's background. */
    private fun core(row: UiNode): Double {
        val shot = d.screenshot()
        val bg = shot.mode(iconZone(row))
        val cx = row.bounds.left - d.dp(21f)
        val h = d.dp(1.5f)
        val square = Rect(cx - h, row.bounds.centerY() - h, cx + h, row.bounds.centerY() + h)
        return shot.count(square) { !near(it, bg, 24) }.toDouble() / (square.width() * square.height())
    }

    // ------------------------------------------------------------------ §6.4-3, the name

    @Test
    fun aLiveFoldersNameIsTakenAndAJustDeletedFoldersNameIsFree() {
        folder("f-alpine", "Alpine")
        folder("f-birch", "Birch")
        d.launch()
        openDrawer()

        editFolder("Birch")
        d.tap(nameField())
        d.awaitIme()
        d.clearField()
        d.type("alpine") // the live folder's name in another case
        d.awaitTrue("the name typed") { nameField().text == "alpine" }
        tapPopupButton(s(R.string.folders_save))
        d.await("the error under the name", 5_000) { d.texts(s(R.string.folders_name_taken)).firstOrNull { it.inPopup } }
        d.save("folder-name-taken")
        assertTrue("the popup stays open", d.popupOpen())
        assertNotNull("the app goes on", d.activity())
        assertEquals("nothing saved", "Birch", folderRow("f-birch")?.name)

        d.tap(nameField())
        d.type("x")
        d.awaitTrue("an edit of the name hides the error") { d.texts(s(R.string.folders_name_taken)).isEmpty() }
        tapPopupButton(s(R.string.folders_cancel))
        d.awaitTrue("Cancel closes the popup") { !d.popupOpen() }
        assertEquals("Cancel saved nothing", "Birch", folderRow("f-birch")?.name)

        // Alpine deleted, its name given to Birch at once
        openFolderMenu("Alpine")
        d.tap(d.texts(s(R.string.folders_delete)).first { d.onScreen(it) })
        d.await("the delete dialog") { d.texts(s(R.string.folders_delete_confirm_title)).firstOrNull { d.onScreen(it) } }
        d.settle(400)
        d.tap(d.texts(s(R.string.folders_delete)).last { d.onScreen(it) })
        d.awaitTrue("Alpine leaves the drawer") { folderRowLabel("Alpine") == null }
        assertNotNull("Alpine is a tombstone", folderRow("f-alpine")?.deletedAt)

        editFolder("Birch")
        d.tap(nameField())
        d.awaitIme()
        d.clearField()
        d.type("Alpine")
        d.awaitTrue("the name typed") { nameField().text == "Alpine" }
        tapPopupButton(s(R.string.folders_save))
        d.awaitTrue("the deleted folder's name is saved: the popup closes") { !d.popupOpen() }
        assertTrue("no error", d.texts(s(R.string.folders_name_taken)).isEmpty())
        assertEquals("Alpine", folderRow("f-birch")?.name)
        assertNotNull("the drawer shows it", folderRowLabel("Alpine"))
        d.save("folder-deleted-name-reused")
    }

    // ------------------------------------------------------------------ §6.4-4

    @Test
    fun cardsOfAColouredFolderAreTintedInAllNotesAndInSearchFromTheFolderButNotInTheFolder() {
        folder("f-garden", "Garden", color = "red")
        folder("f-seeds", "Seeds", parent = "f-garden")
        folder("f-plain", "Plain")
        note("n-plain", "Pebble", folder = "f-plain", body = "tintword pebble")
        note("n-seeds", "Basil", folder = "f-seeds", body = "tintword basil")
        note("n-garden", "Tulip", folder = "f-garden", body = "tintword tulip")
        note("n-loose", "Loose", body = "tintword loose")
        d.launch()
        val ids = listOf("n-loose", "n-garden", "n-seeds", "n-plain")
        d.awaitTrue("all four cards") { ids.all { d.card(it) != null } }
        d.settle(800)

        val tint = AccentColor.Red.cardTint(dark)
        val plain = fill("n-loose")
        d.log("all notes: plain card ${hex(plain)}, red card tint ${hex(tint)}")
        d.save("tint-all-notes")
        assertFalse("the reference card is not tinted", near(plain, tint))
        assertTrue("All Notes: a card of the red folder is tinted (${hex(fill("n-garden"))})", near(fill("n-garden"), tint))
        assertTrue("All Notes: a card of its colourless subfolder takes the parent's tint (${hex(fill("n-seeds"))})", near(fill("n-seeds"), tint))
        assertTrue("All Notes: a card of a colourless folder is not tinted (${hex(fill("n-plain"))})", near(fill("n-plain"), plain, 3))

        openDrawer()
        d.tap(folderRowLabel("Garden")!!)
        d.awaitTrue("the Garden folder open") { !drawerOpen() && d.card("n-loose") == null && d.card("n-garden") != null }
        d.settle(800)
        d.save("tint-in-folder")
        assertTrue("in the folder itself the card is not tinted (${hex(fill("n-garden"))})", near(fill("n-garden"), plain, 3))

        d.tap(d.texts(s(R.string.search_hint)).first { d.onScreen(it) })
        d.settle(800)
        d.tap(d.nodes().last { it.editable && d.onScreen(it) }) // the search field
        d.type("tintword")
        d.awaitTrue("search from the folder reads every folder", 15_000) { d.card("n-seeds") != null && d.card("n-loose") != null }
        d.settle(800)
        keyboardDown()
        d.save("tint-search-from-folder")
        assertTrue("search from the folder: the folder's card is tinted (${hex(fill("n-garden"))})", near(fill("n-garden"), tint))
        assertTrue("search from the folder: the subfolder's card is tinted (${hex(fill("n-seeds"))})", near(fill("n-seeds"), tint))
        assertTrue("search from the folder: a loose card is not (${hex(fill("n-loose"))})", near(fill("n-loose"), plain, 3))
    }

    /** A card's fill: the most common colour of its top band (title row), clear of the rounded corners. */
    private fun fill(id: String): Int {
        val c = d.card(id) ?: error("no card $id")
        val r = Rect(c.bounds.left + d.dp(16f), c.bounds.top + d.dp(6f), c.bounds.right - d.dp(16f), c.bounds.top + d.dp(40f))
        return d.screenshot().mode(r)
    }

    private fun near(a: Int, b: Int, tol: Int) = near(a, androidx.compose.ui.graphics.Color(b), tol)

    private companion object { const val House = "🏠" }
}
