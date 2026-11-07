package app.tessera.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [parseImageRef] — splitting a tessera-img ref into id + display params. */
class ImageRefTest {

    @Test fun `bare ref defaults to w100 rot0`() {
        assertEquals(ImageRef("abc-1", 100, 0), parseImageRef("tessera-img://abc-1"))
    }

    @Test fun `parses w and rot`() {
        assertEquals(ImageRef("abc-1", 50, 90), parseImageRef("tessera-img://abc-1?w=50&rot=90"))
    }

    @Test fun `params in any order`() {
        assertEquals(ImageRef("abc-1", 50, 90), parseImageRef("tessera-img://abc-1?rot=90&w=50"))
    }

    @Test fun `unknown params ignored`() {
        assertEquals(ImageRef("abc-1", 50, 0), parseImageRef("tessera-img://abc-1?w=50&foo=bar"))
    }

    @Test fun `w below range clamped to 10`() {
        assertEquals(10, parseImageRef("tessera-img://abc-1?w=5")!!.wPercent)
    }

    @Test fun `w above range clamped to 100`() {
        assertEquals(100, parseImageRef("tessera-img://abc-1?w=250")!!.wPercent)
    }

    @Test fun `invalid rot rejected to 0`() {
        assertEquals(0, parseImageRef("tessera-img://abc-1?rot=45")!!.rotDegrees)
    }

    @Test fun `valid rotations accepted`() {
        assertEquals(180, parseImageRef("tessera-img://abc-1?rot=180")!!.rotDegrees)
        assertEquals(270, parseImageRef("tessera-img://abc-1?rot=270")!!.rotDegrees)
    }

    @Test fun `amp-encoded ampersand decoded before split`() {
        assertEquals(ImageRef("abc-1", 50, 90), parseImageRef("tessera-img://abc-1?w=50&amp;rot=90"))
    }

    @Test fun `non tessera source is null`() {
        assertNull(parseImageRef("https://example.com/x.png"))
    }

    @Test fun `blank id is null`() {
        assertNull(parseImageRef("tessera-img://"))
        assertNull(parseImageRef("tessera-img://?w=50"))
    }

    @Test fun `malformed query tolerated`() {
        // Trailing separators, empty pairs, missing values — no crash, defaults preserved.
        assertEquals(ImageRef("abc-1", 100, 0), parseImageRef("tessera-img://abc-1?&&=&w=&rot="))
    }

    @Test fun `parseImageId still returns bare id`() {
        assertEquals("abc-1", parseImageId("tessera-img://abc-1?w=50&rot=90"))
        assertEquals("abc-1", parseImageId("tessera-img://abc-1"))
        assertNull(parseImageId("https://example.com"))
    }
}
