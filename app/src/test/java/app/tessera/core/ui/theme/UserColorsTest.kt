package app.tessera.core.ui.theme

import app.tessera.domain.model.UserId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserColorsTest {
  @Test
  fun `same uid always maps to the same color`() {
    val uid = UserId("user-abc")
    assertEquals(colorForUser(uid), colorForUser(uid))
  }

  @Test
  fun `color is always drawn from the palette`() {
    val uids = (0 until 200).map { UserId("user-$it") }
    uids.forEach { uid ->
      assertTrue(colorForUser(uid) in UserColors.palette, "color for ${uid.value} must be in palette")
    }
  }

  @Test
  fun `different uids spread across more than one palette entry`() {
    val distinct = (0 until 50).map { UserId("user-$it") }.map(::colorForUser).distinct()
    assertTrue(distinct.size > 1, "expected colors to vary across users, got $distinct")
  }
}
