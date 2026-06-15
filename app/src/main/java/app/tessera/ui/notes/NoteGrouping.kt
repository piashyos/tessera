package app.tessera.ui.notes

import app.tessera.domain.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class NoteGroup { Pinned, Today, Yesterday, ThisWeek, ThisMonth, Earlier }

data class NoteSection(val group: NoteGroup, val notes: List<Note>)

fun groupNotesByDate(
    notes: List<Note>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<NoteSection> {
    if (notes.isEmpty()) return emptyList()
    val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val startOfMonth = today.withDayOfMonth(1)

    fun bucket(note: Note): NoteGroup {
        if (note.isPinned) return NoteGroup.Pinned
        val date = Instant.ofEpochMilli(note.updatedAt).atZone(zone).toLocalDate()
        return when {
            date == today -> NoteGroup.Today
            date == today.minusDays(1) -> NoteGroup.Yesterday
            !date.isBefore(startOfWeek) -> NoteGroup.ThisWeek
            !date.isBefore(startOfMonth) -> NoteGroup.ThisMonth
            else -> NoteGroup.Earlier
        }
    }

    return notes
        .groupBy(::bucket)
        .toList()
        .sortedBy { (group, _) -> group.ordinal }
        .map { (group, groupNotes) -> NoteSection(group, groupNotes) }
}
