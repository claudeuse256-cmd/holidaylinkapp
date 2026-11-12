package org.holidaylink.app.net

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Awaits a Firebase task but gives up after [timeoutMs].
 *
 * Without a ceiling, a Firestore read on a flaky connection retries for a long time and the
 * screen sits on "Loading…" with no way out. Callers treat null as "couldn't load" and show
 * their error/empty state instead.
 */
suspend fun <T> Task<T>.awaitOrNull(timeoutMs: Long = 12_000): T? =
    withTimeoutOrNull(timeoutMs) { await() }
