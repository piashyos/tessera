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
}
