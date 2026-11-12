package app.tessera.ui.notes

import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.UiTestTags
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Screens of the tag and folder appearance for the user (there is no mockup) and the D6 measurement — not assertions:
 * - light and dark: the list with tinted cards and the tag row, the tag popup, the drawer with a coloured folder and its "⋮" menu,
 *   the folder popup (also with the keyboard up);
 * - the four palettes of Settings in light and dark (the list with the tag row);
 * - font ×1.3: the drawer, the folder popup, the tag popup;
 * - D6: the folder popup with the keyboard up (focus in the name field) — where the title and the name field are against the status bar
 *   and the keyboard, in the device's geometry and in Pixel 9 Pro's (1280×2856, 480 dpi). Read from logcat (tag `TagFolderAppearance`).
 *
 * Shots go to [AppearanceDevice.ShotDir] on the device (`adb pull` them). Grain stays at its default (on), as the user sees the app.
 */
@HiltAndroidTest
class TagFolderAppearanceShotsTest : AppearanceAppTest() {

    private fun seed() {
        folder("f-garden", "Garden", color = "green", icon = "lucide:leaf")
        folder("f-seeds", "Seeds", parent = "f-garden")
        folder("f-trips", "Trips", color = "blue", icon = "emoji:✈️")
        folder("f-inbox", "Reading")
        note("n-5", "Reading list", tags = listOf("ideas"), folder = "f-inbox", body = "Three books for the winter")
        note("n-4", "Meeting notes", tags = listOf("work"), body = "Quarterly plan and the budget")
        note("n-3", "Compost", tags = listOf("garden", "ideas"), folder = "f-garden", body = "Brown and green layers, turn weekly")
        note("n-2", "Tomato seedlings", tags = listOf("garden"), folder = "f-seeds", body = "Water every two days")
        note("n-1", "Packing list", tags = listOf("travel", "todo"), folder = "f-trips", body = "Passport, charger, sunscreen")
        tagStyle("travel", "orange", "lucide:plane")
        tagStyle("garden", "green", "emoji:🌱")
        tagStyle("ideas", "purple", "lucide:lightbulb")
        // "todo" and "work" keep the default: the hash colour and "#"
    }

    private fun launchList(mode: ThemeMode, palette: ThemePalette = ThemePalette.Default) {
        theme(mode, palette, grain = true)
        d.launch()
        d.awaitTrue("the cards") { d.card("n-1") != null }
        showTagRow("garden") // the first chip: at font ×1.3 the later ones run past the screen's edge
        d.settle(800)
    }

    @Test
    fun lightAndDark() {
        seed()
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val t = mode.name.lowercase()
            launchList(mode)
            d.save("all-notes-tinted-tag-row-$t")

            d.longPress(chip("travel")!!)
            d.await("the tag popup") { d.nodes().firstOrNull { it.inPopup && it.text == "#travel" } }
            d.settle(600)
            d.save("popup-tag-$t")
            revealInPopup("the tag preview") { d.nodes().lastOrNull { it.inPopup && it.text == "travel" } }
            d.save("popup-tag-scrolled-$t")
            d.back()
            d.awaitTrue("the popup closed") { !d.popupOpen() }

            openDrawer()
            d.save("drawer-$t")
            openFolderMenu("Garden")
            d.save("drawer-menu-$t")
            d.tap(d.texts(s(R.string.folders_edit)).first { d.onScreen(it) })
            d.await("the folder popup") { d.nodes().firstOrNull { it.inPopup && it.text == s(R.string.folders_edit_title) } }
            d.settle(600)
            d.save("popup-folder-$t")
            d.tap(nameField())
            d.awaitIme()
            d.save("popup-folder-keyboard-$t")
            keyboardDown()
            revealInPopup("the folder preview") { d.nodes().lastOrNull { it.inPopup && it.text == "Garden" && !it.editable } }
            d.save("popup-folder-scrolled-$t")
            d.close()
        }
    }

    @Test
    fun palettes() {
        seed()
        for (palette in ThemePalette.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            launchList(mode, palette)
            d.save("palette-${palette.name.lowercase()}-${mode.name.lowercase()}")
            d.close()
        }
    }

    /** Material You (spec §3.1: the contrast test does not cover it, eyes on the shots do). */
    @Test
    fun dynamicColor() {
        seed()
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode, grain = true)
            kotlinx.coroutines.runBlocking { prefs.setDynamicColor(true) }
            d.launch()
            d.awaitTrue("the cards") { d.card("n-1") != null }
            showTagRow("garden")
            d.settle(800)
            d.save("dynamic-list-${mode.name.lowercase()}")
            openDrawer()
            d.save("dynamic-drawer-${mode.name.lowercase()}")
            d.close()
        }
    }

    @Test
    fun fontScale130() {
        seed()
        try {
            d.shell("settings put system font_scale 1.3")
            launchList(ThemeMode.LIGHT)
            d.save("font130-list")
            d.longPress(chip("garden")!!)
            d.await("the tag popup") { d.nodes().firstOrNull { it.inPopup && it.text == "#garden" } }
            d.settle(600)
            d.save("font130-popup-tag")
            d.back()
            d.awaitTrue("the popup closed") { !d.popupOpen() }
            openDrawer()
            d.save("font130-drawer")
            openFolderMenu("Garden")
            d.save("font130-drawer-menu")
            d.tap(d.texts(s(R.string.folders_edit)).first { d.onScreen(it) })
            d.await("the folder popup") { d.nodes().firstOrNull { it.inPopup && it.text == s(R.string.folders_edit_title) } }
            d.settle(600)
            d.save("font130-popup-folder")
            d.tap(nameField())
            d.awaitIme()
            d.save("font130-popup-folder-keyboard")
            measureD6("font130")
        } finally {
            d.close()
            d.shell("settings put system font_scale 1.0")
        }
    }

    // ------------------------------------------------------------------ D6

    @Test
    fun d6TheFolderPopupWithTheKeyboardInTheDeviceGeometry() = d6("device")

    @Test
    fun d6TheFolderPopupWithTheKeyboardInPixel9ProGeometry() {
        try {
            d.shell("wm size 1280x2856")
            d.shell("wm density 480")
            d.settle(2_000)
            d6("pixel9pro")
        } finally {
            d.close()
            d.shell("wm size reset")
            d.shell("wm density reset")
            d.settle(2_000)
        }
    }

    private fun d6(geometry: String) {
        seed()
        theme(ThemeMode.LIGHT, grain = true)
        d.launch()
        openDrawer()
        editFolder("Garden")
        d.tap(nameField())
        d.awaitIme()
        d.settle(800)
        measureD6(geometry)
        d.save("d6-$geometry-folder-keyboard")
        // the new folder popup (no "Reset") the same way
        d.back() // the keyboard
        d.back() // the popup
        d.awaitTrue("the popup closed") { !d.popupOpen() }
        d.tap(d.descs(s(R.string.folders_create)).first { d.onScreen(it) })
        d.await("the new folder popup") { d.nodes().firstOrNull { it.inPopup && it.text == s(R.string.folders_new) } }
        d.tap(nameField())
        d.awaitIme()
        d.settle(800)
        measureD6("$geometry-new")
        d.save("d6-$geometry-new-folder-keyboard")
    }

    /** Where the popup's title, name field and buttons are against the status bar and the keyboard (logcat). */
    private fun measureD6(geometry: String) {
        val nodes = d.nodes()
        val title = nodes.first { it.inPopup && (it.text == s(R.string.folders_edit_title) || it.text == s(R.string.folders_new)) }
        val field = nameField()
        val save = nodes.first { it.inPopup && it.text == s(R.string.folders_save) }
        val card = d.byTag(UiTestTags.PopupCard).first()
        val status = d.statusBarBottom()
        val ime = d.imeTop() ?: -1
        val dpi = d.activity()?.resources?.displayMetrics?.densityDpi
        d.log(
            "D6 $geometry: window ${d.screenWidth()}x${d.screenHeight()} @$dpi dpi, status bar bottom $status, keyboard top $ime, card ${card.bounds}, " +
                "title ${title.bounds}, name field ${field.bounds}, Save ${save.bounds}",
        )
        d.log(
            "D6 $geometry verdict: title below the status bar ${title.bounds.top >= status}, title above the keyboard ${title.bounds.bottom <= ime}, " +
                "field below the status bar ${field.bounds.top >= status}, field above the keyboard ${field.bounds.bottom <= ime}, " +
                "Save above the keyboard ${save.bounds.bottom <= ime}",
        )
    }
}
