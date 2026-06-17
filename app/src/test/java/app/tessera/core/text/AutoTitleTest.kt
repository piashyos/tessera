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

    @Test fun `returns empty when content is blank`() {
        assertEquals("", deriveTitleFromContent(""))
        assertEquals("", deriveTitleFromContent("   \n\n"))
    }

    @Test fun `caps title length`() {
        val long = "a".repeat(200)
        assertEquals(120, deriveTitleFromContent(long).length)
    }
}
