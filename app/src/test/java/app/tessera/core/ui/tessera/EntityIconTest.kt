package app.tessera.core.ui.tessera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric: isAcceptableEmoji uses android.icu BreakIterator (host JDK 17 has no grapheme clusters).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntityIconTest {
  @Test fun lucideSet_has30UniqueKeysInSpecOrder() {
    val keys = LucideIconSet.map { it.key }
    assertEquals(30, keys.size)
    assertEquals(30, keys.toSet().size)
    assertEquals("folder", keys.first())
    assertEquals("gift", keys.last())
  }

  @Test fun parse_lucide() {
    assertEquals("star", (parseEntityIcon("lucide:star") as EntityIcon.Vector).key)
    LucideIconSet.forEach { assertEquals(it, parseEntityIcon(it.toRaw())) }
  }

  @Test fun parse_emoji() {
    assertEquals(EntityIcon.Emoji("🏠"), parseEntityIcon("emoji:🏠"))
    assertEquals("emoji:🏠", EntityIcon.Emoji("🏠").toRaw())
  }

  @Test fun parse_badInput_isNull() {
    assertNull(parseEntityIcon(null))
    assertNull(parseEntityIcon("lucide:does-not-exist"))
    assertNull(parseEntityIcon("emoji:a"))
    assertNull(parseEntityIcon("emoji:"))
    assertNull(parseEntityIcon("garbage"))
    assertNull(parseEntityIcon(""))
  }

  @Test fun emoji_accepted() {
    assertTrue(isAcceptableEmoji("🏠"))
    assertTrue(isAcceptableEmoji("1️⃣"))
    assertTrue(isAcceptableEmoji("👨‍👩‍👧"))
    assertTrue(isAcceptableEmoji("🇯🇵"))
  }

  @Test fun emoji_rejected() {
    assertFalse(isAcceptableEmoji("a"))
    assertFalse(isAcceptableEmoji("é"))
    assertFalse(isAcceptableEmoji("1"))
    assertFalse(isAcceptableEmoji(" "))
    assertFalse(isAcceptableEmoji("🏠🏠"))
    assertFalse(isAcceptableEmoji(""))
    // invisible single code points
    assertFalse("NBSP", isAcceptableEmoji("\u00A0"))
    assertFalse("narrow NBSP", isAcceptableEmoji("\u202F"))
    assertFalse("ZWSP", isAcceptableEmoji("\u200B"))
    assertFalse("lone VS16", isAcceptableEmoji("\uFE0F"))
    assertFalse("control", isAcceptableEmoji("\u0007"))
    assertFalse("lone surrogate", isAcceptableEmoji("\uD83C"))
    assertFalse("private use", isAcceptableEmoji("\uE000"))
  }
}
