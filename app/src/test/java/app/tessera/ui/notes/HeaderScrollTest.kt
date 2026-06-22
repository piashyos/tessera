package app.tessera.ui.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeaderScrollTest {
    @Test fun `no scroll is full size`() = assertEquals(1f, headerTitleScale(0, 200), 0.0001f)
    @Test fun `past threshold is collapsed`() = assertEquals(0.7f, headerTitleScale(400, 200), 0.0001f)
    @Test fun `half threshold is halfway`() = assertEquals(0.85f, headerTitleScale(100, 200), 0.0001f)
}
