package app.tessera.ui.settings.licenses

/** One open-source dependency: its [name] and the full license [text]. */
data class LicenseEntry(val name: String, val text: String)

/**
 * Reads the two files the Google oss-licenses plugin generates: `third_party_license_metadata`
 * (lines `offset:length name`) and `third_party_licenses` (the texts, addressed by those offsets
 * in **bytes**, UTF-8). Entries that share a license share the same range. Malformed lines and
 * ranges outside the text are skipped; the result is sorted by name, case-insensitively.
 */
object LicensesParser {
    fun parse(metadata: String, licenses: ByteArray): List<LicenseEntry> =
        metadata.lineSequence()
            .mapNotNull { line -> parseLine(line, licenses) }
            .distinct()
            .sortedBy { it.name.lowercase() }
            .toList()

    private fun parseLine(line: String, licenses: ByteArray): LicenseEntry? {
        val space = line.indexOf(' ')
        if (space < 0) return null
        val range = line.substring(0, space).split(':')
        if (range.size != 2) return null
        val offset = range[0].toIntOrNull() ?: return null
        val length = range[1].toIntOrNull() ?: return null
        if (offset < 0 || length < 0 || length > licenses.size - offset) return null
        val name = line.substring(space + 1).trim()
        if (name.isEmpty()) return null
        return LicenseEntry(name, String(licenses, offset, length, Charsets.UTF_8))
    }
}
