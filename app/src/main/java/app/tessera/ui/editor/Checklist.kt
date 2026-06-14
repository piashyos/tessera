package app.tessera.ui.editor

private val CHECKBOX_REGEX = Regex("""^(\s*)- \[( |x|X)] (.*)$""")

data class ChecklistLine(
    val raw: String,
    val isCheckbox: Boolean,
    val isChecked: Boolean,
    val label: String,
)

fun parseChecklist(source: String): List<ChecklistLine> =
    source.split("\n").map { line ->
        val match = CHECKBOX_REGEX.matchEntire(line)
        if (match != null) {
            val marker = match.groupValues[2]
            val label = match.groupValues[3]
            ChecklistLine(
                raw = line,
                isCheckbox = true,
                isChecked = marker.equals("x", ignoreCase = true),
                label = label,
            )
        } else {
            ChecklistLine(
                raw = line,
                isCheckbox = false,
                isChecked = false,
                label = line,
            )
        }
    }

fun toggleChecklistLine(source: String, lineIndex: Int): String {
    val lines = source.split("\n")
    if (lineIndex < 0 || lineIndex >= lines.size) return source

    val line = lines[lineIndex]
    val match = CHECKBOX_REGEX.matchEntire(line) ?: return source

    val indent = match.groupValues[1]
    val marker = match.groupValues[2]
    val label = match.groupValues[3]

    val newMarker = if (marker.equals("x", ignoreCase = true)) " " else "x"
    val toggled = "$indent- [$newMarker] $label"

    return lines.toMutableList()
        .also { it[lineIndex] = toggled }
        .joinToString("\n")
}
