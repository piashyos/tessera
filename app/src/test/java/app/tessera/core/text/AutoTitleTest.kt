package app.tessera.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTitleTest {
    @Test fun `picks first non-blank line`() {
        assertEquals("Hello", deriveTitleFromContent("\n  \nHello\nworld"))
    }

    @Test fun `strips heading markers`() {
        assertEquals("Plans", deriveTitleFromContent("# Plans\n- a"))
        assertEquals("Plans", deriveTitleFromContent("## Plans"))
    }

    @Test fun `strips list bullets and checklist boxes`() {
        assertEquals("Buy milk", deriveTitleFromContent("- Buy milk\n- Eggs"))
        assertEquals("Buy milk", deriveTitleFromContent("- [ ] Buy milk"))
        assertEquals("Buy milk", deriveTitleFromContent("- [x] Buy milk"))
    }

    @Test fun `strips inline emphasis and code markers`() {
        assertEquals("Bold plan", deriveTitleFromContent("**Bold** plan"))
        assertEquals("hint text", deriveTitleFromContent("*hint* text"))
        assertEquals("old text", deriveTitleFromContent("~~old~~ text"))
        assertEquals("run gradlew now", deriveTitleFromContent("run `gradlew` now"))
    }

    @Test fun `strips ordered list and quote markers`() {
        assertEquals("First step", deriveTitleFromContent("1. First step\n2. Second"))
        assertEquals("Quoted line", deriveTitleFromContent("> Quoted line"))
    }

    @Test fun `returns empty when content is blank`() {
        assertEquals("", deriveTitleFromContent(""))
        assertEquals("", deriveTitleFromContent("   \n\n"))
    }

    @Test fun `caps title length`() {
        val long = "a".repeat(200)
        assertEquals(120, deriveTitleFromContent(long).length)
    }

    @Test fun `note starting with a table titles from cell text, not pipe markup`() {
        val md = "| Task | Owner |\n| --- | --- |\n| Design | Ann |"
        assertEquals("Task Owner", deriveTitleFromContent(md))
    }

    @Test fun `table sizing metadata comment never becomes the title`() {
        val md = "<!-- tessera:cols=115,264;rows=44,44 -->\n| Task | Owner |\n| --- | --- |"
        assertEquals("Task Owner", deriveTitleFromContent(md))
    }
}
