package app.tessera.data.drive

/**
 * Pure cache for a Drive access token plus its expiry deadline. Split out from
 * [PlayServicesDriveAuthManager] so the caching/expiry logic can be unit-tested without
 * touching Play Services.
 */
class TokenCache(private val clock: () -> Long) {
    private var token: String? = null
    private var deadline: Long = 0L

    /** Returns the cached token if it is still fresh, else null. */
    fun get(): String? = token?.takeIf { clock() < deadline }

    /** Stores [token], valid until `clock() + ttlMillis`. */
    fun put(token: String, ttlMillis: Long) {
        this.token = token
        this.deadline = clock() + ttlMillis
    }

    /** Drops the cached token so the next [get] returns null. */
    fun invalidate() {
        token = null
        deadline = 0L
    }
}
