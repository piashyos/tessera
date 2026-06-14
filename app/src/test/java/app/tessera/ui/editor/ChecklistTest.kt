package app.tessera.ui.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChecklistTest {

    // ---------------------------------------------------------------------------
    // parseChecklist
    // ---------------------------------------------------------------------------

    @Test
    fun `parseChecklist - single unchecked line`() {
        val result = parseChecklist("- [ ] buy milk")
        assertEquals(1, result.size)
        val line = result[0]
        assertTrue(line.isCheckbox)
        assertFalse(line.isChecked)
        assertEquals("buy milk", line.label)
        assertEquals("- [ ] buy milk", line.raw)
    }

    @Test
    fun `parseChecklist - single checked line lowercase x`() {
        val result = parseChecklist("- [x] buy milk")
        assertEquals(1, result.size)
        val line = result[0]
        assertTrue(line.isCheckbox)
        assertTrue(line.isChecked)
        assertEquals("buy milk", line.label)
        assertEquals("- [x] buy milk", line.raw)
    }

    @Test
    fun `parseChecklist - single checked line uppercase X`() {
        val result = parseChecklist("- [X] buy milk")
        assertEquals(1, result.size)
        val line = result[0]
        assertTrue(line.isCheckbox)
        assertTrue(line.isChecked)
        assertEquals("buy milk", line.label)
        assertEquals("- [X] buy milk", line.raw)
    }

    @Test
    fun `parseChecklist - leading whitespace on checked line`() {
        val result = parseChecklist("  - [X] buy milk")
        assertEquals(1, result.size)
        val line = result[0]
        assertTrue(line.isCheckbox)
        assertTrue(line.isChecked)
        assertEquals("buy milk", line.label)
        assertEquals("  - [X] buy milk", line.raw)
    }

    @Test
    fun `parseChecklist - plain text line is not a checkbox`() {
        val result = parseChecklist("hello")
        assertEquals(1, result.size)
        val line = result[0]
        assertFalse(line.isCheckbox)
        assertFalse(line.isChecked)
        assertEquals("hello", line.label)
        assertEquals("hello", line.raw)
    }

    @Test
    fun `parseChecklist - multiline source returns one entry per line`() {
        val source = "- [ ] a\n- [ ] b\n- [x] c"
        val result = parseChecklist(source)
        assertEquals(3, result.size)

        assertTrue(result[0].isCheckbox)
        assertFalse(result[0].isChecked)
        assertEquals("a", result[0].label)

        assertTrue(result[1].isCheckbox)
        assertFalse(result[1].isChecked)
        assertEquals("b", result[1].label)

        assertTrue(result[2].isCheckbox)
        assertTrue(result[2].isChecked)
        assertEquals("c", result[2].label)
    }

    @Test
    fun `parseChecklist - mixed checkbox and plain lines`() {
        val source = "Title\n- [ ] task\nsome note"
        val result = parseChecklist(source)
        assertEquals(3, result.size)

        assertFalse(result[0].isCheckbox)
        assertEquals("Title", result[0].label)

        assertTrue(result[1].isCheckbox)
        assertFalse(result[1].isChecked)
        assertEquals("task", result[1].label)

        assertFalse(result[2].isCheckbox)
        assertEquals("some note", result[2].label)
    }

    @Test
    fun `parseChecklist - empty string produces one empty plain line`() {
        val result = parseChecklist("")
        assertEquals(1, result.size)
        assertFalse(result[0].isCheckbox)
        assertEquals("", result[0].label)
        assertEquals("", result[0].raw)
    }

    @Test
    fun `parseChecklist - trailing blank line is preserved as plain line`() {
        val source = "- [ ] task\n"
        val result = parseChecklist(source)
        assertEquals(2, result.size)
        assertTrue(result[0].isCheckbox)
        assertFalse(result[1].isCheckbox)
        assertEquals("", result[1].raw)
    }

    // ---------------------------------------------------------------------------
    // toggleChecklistLine
    // ---------------------------------------------------------------------------

    @Test
    fun `toggleChecklistLine - unchecked becomes checked`() {
        val source = "- [ ] buy milk"
        val result = toggleChecklistLine(source, 0)
        assertEquals("- [x] buy milk", result)
    }

    @Test
    fun `toggleChecklistLine - checked becomes unchecked`() {
        val source = "- [x] buy milk"
        val result = toggleChecklistLine(source, 0)
        assertEquals("- [ ] buy milk", result)
    }

    @Test
    fun `toggleChecklistLine - uppercase X becomes unchecked`() {
        val source = "- [X] buy milk"
        val result = toggleChecklistLine(source, 0)
        assertEquals("- [ ] buy milk", result)
    }

    @Test
    fun `toggleChecklistLine - preserves leading whitespace`() {
        val source = "  - [ ] indented"
        val result = toggleChecklistLine(source, 0)
        assertEquals("  - [x] indented", result)
    }

    @Test
    fun `toggleChecklistLine - toggles only the specified line in multiline source`() {
        val source = "- [ ] a\n- [ ] b\n- [x] c"
        val result = toggleChecklistLine(source, 1)
        assertEquals("- [ ] a\n- [x] b\n- [x] c", result)
    }

    @Test
    fun `toggleChecklistLine - toggling line 0 leaves other lines unchanged`() {
        val source = "- [ ] a\n- [ ] b\n- [x] c"
        val result = toggleChecklistLine(source, 0)
        assertEquals("- [x] a\n- [ ] b\n- [x] c", result)
    }

    @Test
    fun `toggleChecklistLine - toggling checked line 2 unchecks it`() {
        val source = "- [ ] a\n- [ ] b\n- [x] c"
        val result = toggleChecklistLine(source, 2)
        assertEquals("- [ ] a\n- [ ] b\n- [ ] c", result)
    }

    @Test
    fun `toggleChecklistLine - non-checkbox line returns source unchanged`() {
        val source = "hello world"
        val result = toggleChecklistLine(source, 0)
        assertEquals(source, result)
    }

    @Test
    fun `toggleChecklistLine - out-of-range index returns source unchanged`() {
        val source = "- [ ] task"
        assertEquals(source, toggleChecklistLine(source, 5))
        assertEquals(source, toggleChecklistLine(source, -1))
    }

    @Test
    fun `toggleChecklistLine - preserves trailing blank line`() {
        val source = "- [ ] task\n"
        val result = toggleChecklistLine(source, 0)
        assertEquals("- [x] task\n", result)
    }

    @Test
    fun `toggleChecklistLine - preserves label text verbatim including spaces`() {
        val source = "- [ ]   label with spaces  "
        val result = toggleChecklistLine(source, 0)
        assertEquals("- [x]   label with spaces  ", result)
    }
}
