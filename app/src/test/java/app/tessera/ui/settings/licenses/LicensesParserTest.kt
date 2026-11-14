package app.tessera.ui.settings.licenses

import org.junit.Assert.assertEquals
import org.junit.Test

class LicensesParserTest {
    // "Привет" is 12 UTF-8 bytes — offsets after it only line up if they are read as bytes.
    private val texts = "Apache 2.0\nПривет\nMIT".toByteArray(Charsets.UTF_8)
    private val apache = "0:10"
    private val ru = "11:12"
    private val mit = "24:3"

    @Test
    fun `entries are sorted by name ignoring case and texts are cut by byte offsets`() {
        val metadata = "$mit zeta\n$apache Beta\n$ru alpha\n"
        val result = LicensesParser.parse(metadata, texts)
        assertEquals(
            listOf(
                LicenseEntry("alpha", "Привет"),
                LicenseEntry("Beta", "Apache 2.0"),
                LicenseEntry("zeta", "MIT"),
            ),
            result,
        )
    }

    @Test
    fun `entries sharing a range share the text and names may contain spaces and colons`() {
        val result = LicensesParser.parse("$apache Foo: Bar Core\n$apache Baz\n", texts)
        assertEquals(listOf("Baz", "Foo: Bar Core"), result.map { it.name })
        assertEquals(listOf("Apache 2.0", "Apache 2.0"), result.map { it.text })
    }

    @Test
    fun `malformed lines and ranges past the end are skipped`() {
        val metadata = "garbage\n0:x bad\n5:999 toolong\n-1:3 negative\n2147483647:1 overflow\n0:10 \n$apache ok\n"
        assertEquals(listOf("ok"), LicensesParser.parse(metadata, texts).map { it.name })
    }

    @Test
    fun `identical metadata lines give one entry`() {
        val result = LicensesParser.parse("$apache Foo\n$apache Foo\n", texts)
        assertEquals(listOf(LicenseEntry("Foo", "Apache 2.0")), result)
    }

    @Test
    fun `debug placeholder sample parses to one entry`() {
        val text = "Licenses are only provided in build variants"
        val result = LicensesParser.parse("0:${text.length} Debug License Info", text.toByteArray())
        assertEquals(listOf(LicenseEntry("Debug License Info", text)), result)
    }
}
