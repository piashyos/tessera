package app.tessera.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartSearchTest {

    private fun score(title: String, body: String, query: String, tags: List<String> = emptyList()) =
        matchScore(title, body, tags, searchTerms(query))

    @Test
    fun `matches across Cyrillic case (different registers)`() {
        assertNotNull(score(title = "Свадьба", body = "", query = "свадьба"))
        assertNotNull(score(title = "свадьба", body = "", query = "СВАДЬБА"))
    }

    @Test
    fun `matches across Latin case`() {
        assertNotNull(score(title = "Meeting Notes", body = "", query = "meeting"))
        assertNotNull(score(title = "meeting notes", body = "", query = "NOTES"))
    }

    @Test
    fun `is diacritic-insensitive`() {
        assertNotNull(score(title = "Café résumé", body = "", query = "cafe resume"))
        // Russian ё folds to е
        assertNotNull(score(title = "Ёлка", body = "", query = "елка"))
    }

    @Test
    fun `prefix and substring match`() {
        assertNotNull(score(title = "Свадьба", body = "", query = "свад"))
        assertNotNull(score(title = "", body = "планирование бюджета", query = "бюдж"))
    }

    @Test
    fun `tolerates typos on longer terms`() {
        // transposition / missing letter in an 7-char word (budget 2)
        assertNotNull(score(title = "Свадьба", body = "", query = "свадба"))
        assertNotNull(score(title = "Свадьба", body = "", query = "свдьба"))
        // Latin typo
        assertNotNull(score(title = "Groceries", body = "", query = "grocries"))
    }

    @Test
    fun `does not fuzzy-match very short terms`() {
        // 3-char term, budget 0 → must be exact/substring, not fuzzy
        assertNull(score(title = "cat", body = "", query = "dog"))
    }

    @Test
    fun `multi-word query requires all terms to match`() {
        assertNotNull(score(title = "Wedding budget", body = "restaurant and dress", query = "wedding dress"))
        assertNull(score(title = "Wedding budget", body = "restaurant", query = "wedding holiday"))
    }

    @Test
    fun `unrelated query does not match`() {
        assertNull(score(title = "Свадьба", body = "ресторан платье", query = "автомобиль"))
    }

    @Test
    fun `title hits outrank body hits`() {
        val titleHit = score(title = "Kotlin", body = "nothing here", query = "kotlin")!!
        val bodyHit = score(title = "nothing here", body = "Kotlin", query = "kotlin")!!
        assertTrue(titleHit > bodyHit, "title match should score higher than body match")
    }

    @Test
    fun `exact match outranks fuzzy match`() {
        val exact = score(title = "Groceries", body = "", query = "groceries")!!
        val fuzzy = score(title = "Groceries", body = "", query = "grocries")!!
        assertTrue(exact > fuzzy, "exact match should score higher than a typo match")
    }

    @Test
    fun `empty query terms list scores zero (no filtering)`() {
        assertEquals(0, matchScore("anything", "anything", emptyList(), emptyList()))
        assertTrue(searchTerms("   ").isEmpty())
    }

    @Test
    fun `withinEditDistance respects the budget`() {
        assertTrue(withinEditDistance("svadba", "svadьba".lowercase(), 2))
        assertTrue(withinEditDistance("kitten", "sitten", 1))
        assertEquals(false, withinEditDistance("kitten", "sitting", 1))
        assertEquals(false, withinEditDistance("abc", "xyz", 0))
    }
}
