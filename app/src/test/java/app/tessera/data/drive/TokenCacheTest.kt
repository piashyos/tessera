package app.tessera.data.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenCacheTest {
    private var now = 0L
    private val cache = TokenCache(clock = { now })

    @Test
    fun `fresh token is reused`() {
        cache.put("tok1", ttlMillis = 1_000)

        assertEquals("tok1", cache.get())
    }

    @Test
    fun `expired token is dropped`() {
        cache.put("tok1", ttlMillis = 1_000)

        now += 1_001

        assertNull(cache.get())
    }

    @Test
    fun `invalidate clears the cached token`() {
        cache.put("tok1", ttlMillis = 1_000)

        cache.invalidate()

        assertNull(cache.get())
    }

    @Test
    fun `token exactly at deadline is expired`() {
        cache.put("tok1", ttlMillis = 1_000)

        now += 1_000

        assertNull(cache.get())
    }
}
