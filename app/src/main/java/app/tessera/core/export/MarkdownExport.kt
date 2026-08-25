package app.tessera.core.export

import app.tessera.domain.model.Note
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Serialises notes to a `.zip` of Markdown files — one `.md` per note. Each file carries a small
 * YAML front-matter block (title, tags, timestamps, pin) followed by the note's Markdown body,
 * so the dump stays human-readable and re-importable later. Pure/IO-only: no Android types, so it
 * is trivially testable and the caller owns the [OutputStream] (typically a SAF document).
 */
object MarkdownExport {

    private val timestampFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    private val illegalFileChars = Regex("[/\\\\:*?\"<>|]")
    private val whitespaceRun = Regex("\\s+")
    private const val UnsafeYamlChars = ":#[]{},&*!|>'\"%@`"

    /** Writes [notes] as Markdown entries into a zip on [out]. Closes [out]. */
    fun writeZip(notes: List<Note>, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            val usedNames = HashSet<String>()
            notes.forEachIndexed { index, note ->
                val name = uniqueFileName(note, index, usedNames)
                zip.putNextEntry(ZipEntry(name))
                zip.write(markdown(note).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    /** Renders a single note as a front-matter + body Markdown document. */
    fun markdown(note: Note): String = buildString {
        appendLine("---")
        appendLine("title: ${yamlScalar(note.title.ifBlank { "Untitled" })}")
        if (note.tags.isNotEmpty()) {
            appendLine("tags: [${note.tags.joinToString(", ") { yamlScalar(it.name) }}]")
        }
        appendLine("created: ${isoTime(note.createdAt)}")
        appendLine("updated: ${isoTime(note.updatedAt)}")
        if (note.isPinned) appendLine("pinned: true")
        appendLine("---")
        appendLine()
        if (note.title.isNotBlank()) {
            appendLine("# ${note.title}")
            appendLine()
        }
        append(note.content)
    }

    private fun isoTime(epochMs: Long): String =
        timestampFormat.format(Instant.ofEpochMilli(epochMs))

    // Quote scalars that could break YAML parsing; leave simple ones bare for readability.
    private fun yamlScalar(value: String): String =
        if (value.isEmpty() || value.any { it in UnsafeYamlChars } || value.first().isWhitespace()) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            value
        }

    private fun uniqueFileName(note: Note, index: Int, used: MutableSet<String>): String {
        val base = sanitize(note.title).ifBlank { "untitled-${index + 1}" }
        var candidate = "$base.md"
        var suffix = 2
        while (!used.add(candidate.lowercase())) {
            candidate = "$base-$suffix.md"
            suffix++
        }
        return candidate
    }

    private fun sanitize(title: String): String =
        title.trim()
            .replace(illegalFileChars, " ")
            .replace(whitespaceRun, " ")
            .trim()
            .take(60)
            .trim()
}
