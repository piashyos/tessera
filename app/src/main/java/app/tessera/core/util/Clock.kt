package app.tessera.core.util

interface Clock {
    fun nowMs(): Long
}

class SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
