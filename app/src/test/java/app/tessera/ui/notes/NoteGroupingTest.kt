package app.tessera.ui.notes

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class NoteGroupingTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)

    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun note(id: String, day: LocalDate, pinned: Boolean = false) =
        Note(NoteId(id), id, "", emptyList(), pinned, ms(day), ms(day), null, null, null)

    @Test
    fun `pinned notes group under Pinned regardless of date`() {
        val groups = groupNotesByDate(
            listOf(note("p", today.minusDays(40), pinned = true), note("t", today)),
            today, zone,
        )
        assertEquals(NoteGroup.Pinned, groups.first().group)
        assertEquals("p", groups.first().notes.single().id.value)
    }

    @Test
    fun `today yesterday and earlier buckets are ordered`() {
        val groups = groupNotesByDate(
            listOf(
                note("a", today),
                note("b", today.minusDays(1)),
                note("c", today.minusDays(200)),
            ),
            today, zone,
        )
        assertEquals(listOf(NoteGroup.Today, NoteGroup.Yesterday, NoteGroup.Earlier), groups.map { it.group })
    }

    @Test
    fun `empty input yields no groups`() {
        assertEquals(emptyList<NoteSection>(), groupNotesByDate(emptyList(), today, zone))
    }

    @Test
    fun `note earlier this week but before yesterday groups under ThisWeek`() {
        val thursday = LocalDate.of(2026, 6, 18)
        // Monday of the same week: after startOfWeek (Mon 06-15) and 3 days before today.
        val monday = thursday.minusDays(3)
        val groups = groupNotesByDate(listOf(note("w", monday)), thursday, zone)
        val section = groups.single()
        assertEquals(NoteGroup.ThisWeek, section.group)
        assertEquals("w", section.notes.single().id.value)
    }

    @Test
    fun `note earlier this month but before this week groups under ThisMonth`() {
        val thursday = LocalDate.of(2026, 6, 18)
        // Same month (startOfMonth 06-01) but before startOfWeek (Mon 06-15).
        val earlierInMonth = LocalDate.of(2026, 6, 5)
        val groups = groupNotesByDate(listOf(note("m", earlierInMonth)), thursday, zone)
        val section = groups.single()
        assertEquals(NoteGroup.ThisMonth, section.group)
        assertEquals("m", section.notes.single().id.value)
    }
}
