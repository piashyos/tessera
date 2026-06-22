package app.tessera.ui.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class NoteDateFormatTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)

    private fun ms(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `today renders as Today`() {
        assertEquals("Today", formatNoteDate(ms(today), today, zone))
    }

    @Test
    fun `yesterday renders as Yesterday`() {
        assertEquals("Yesterday", formatNoteDate(ms(today.minusDays(1)), today, zone))
    }

    @Test
    fun `older same-year renders as day and month`() {
        assertEquals("3 Jun", formatNoteDate(ms(LocalDate.of(2026, 6, 3)), today, zone))
    }

    @Test
    fun `previous year includes the year`() {
        assertEquals("3 Jun 2025", formatNoteDate(ms(LocalDate.of(2025, 6, 3)), today, zone))
    }
}
