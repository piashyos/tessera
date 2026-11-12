package app.tessera.ui.editor

import app.tessera.ui.editor.components.RichBodyActions
import org.junit.Assert.assertEquals
import org.junit.Test

/** The bar's buttons take down the editor's floating text menu and close the ⋯ sheet (the menu is a window above the sheet). */
class NoteEditorClosingActionsTest {
    private val calls = mutableListOf<String>()

    private val base = object : RichBodyActions {
        override fun toggleBold() { calls += "bold" }
        override fun toggleItalic() {}
        override fun toggleStrikethrough() {}
        override fun toggleParagraph() {}
        override fun toggleHeading1() {}
        override fun toggleHeading2() {}
        override fun toggleHeading3() {}
        override fun toggleHeading4() {}
        override fun toggleHeading5() {}
        override fun toggleHeading6() {}
        override fun toggleBulletList() {}
        override fun toggleTaskList() {}
        override fun insertTable() { calls += "table" }
        override fun focusBody() {}
        override fun dismissTextMenu() { calls += "menu-down" }
    }

    @Test fun aBarButtonRunsItsActionThenTakesDownTheTextMenuAndClosesTheSheet() {
        val a = base.closing { calls += "sheet-closed" }
        a.toggleBold()
        a.insertTable()
        assertEquals(listOf("bold", "menu-down", "sheet-closed", "table", "menu-down", "sheet-closed"), calls)
    }

    @Test fun dismissingTheTextMenuAloneDoesNotCloseTheSheet() {
        base.closing { calls += "sheet-closed" }.dismissTextMenu()
        assertEquals(listOf("menu-down"), calls)
    }
}
