package org.holidaylink.app.net

import com.google.firebase.firestore.Source

/**
 * WhatsApp-style local-first list loading.
 *
 * Firestore already keeps a copy of every document on the device (its disk cache). This
 * helper uses it in two passes so a screen can paint the last-known list immediately — no
 * full-screen spinner when the data is already on the phone — and then refresh from the
 * server in the background.
 *
 * It also keeps the last-good list in memory for the running session, so navigating away
 * and back is instant.
 */
object DeviceCache {

    private val store = java.util.concurrent.ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> memory(key: String): T? = store[key] as? T

    fun put(key: String, value: Any) {
        store[key] = value
    }

    /**
     * Paints cached data first, then server data. [fetch] is invoked with [Source.CACHE] and
     * then [Source.DEFAULT]; [onData] is called with each non-empty result. Returns the fresh
     * (server) result, or null if the server was unreachable — in which case the cached copy
     * is still what the caller has already drawn.
     */
    suspend fun <T : Any> load(
        key: String,
        fetch: suspend (Source) -> T,
        onData: (T) -> Unit
    ): T? {
        // 1. Session memory: instant, survives tab switches.
        memory<T>(key)?.let(onData)

        // 2. Firestore's on-device disk cache: instant, survives app restarts.
        runCatching { fetch(Source.CACHE) }.onSuccess { cached ->
            if (cached !is Collection<*> || cached.isNotEmpty()) onData(cached)
        }

        // 3. Fresh from the server, remembered for next time.
        return runCatching { fetch(Source.DEFAULT) }
            .onSuccess {
                put(key, it)
                onData(it)
            }
            .getOrNull()
    }
}
