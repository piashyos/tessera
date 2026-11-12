package app.tessera.ui.notes

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId

/**
 * Colour key of each note's card: its folder's colour, else (a subfolder) its parent's. Notes
 * without a folder, or whose folders have no colour, are not in the map.
 */
fun noteTints(
    notes: List<Note>,
    assignments: Map<NoteId, FolderId>,
    folders: List<Folder>,
): Map<NoteId, String> {
    val byId = folders.associateBy(Folder::id)
    return buildMap {
        for (note in notes) {
            val folder = assignments[note.id]?.let(byId::get) ?: continue
            val color = folder.color ?: folder.parentId?.let(byId::get)?.color
            if (color != null) put(note.id, color)
        }
    }
}
