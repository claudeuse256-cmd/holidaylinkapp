package org.holidaylink.app.net

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date

/**
 * Tolerant date reading.
 *
 * Firestore's own `DocumentSnapshot.getTimestamp(field)` *throws*
 * (`Field 'x' is not a com.google.firebase.Timestamp`) as soon as the field exists but was
 * written in another shape — and the website stores dates as a real Timestamp, as epoch
 * millis, or as an ISO string depending on the screen. A single such document inside a
 * snapshot listener used to take the whole app down, because the listener runs on the main
 * thread. Reading through [safeTimestamp] means one oddly-shaped document can never crash a
 * screen or a listener again.
 */
fun DocumentSnapshot.safeTimestamp(field: String): Timestamp? = toTimestamp(get(field))

/** Epoch-millis form of the same field, 0 when missing or unparseable. */
fun DocumentSnapshot.safeTimestampSeconds(field: String): Long =
    safeTimestamp(field)?.seconds ?: 0L

/** Converts a raw Firestore value into a [Timestamp], whatever shape it was stored in. */
fun toTimestamp(raw: Any?): Timestamp? = when (raw) {
    null -> null
    is Timestamp -> raw
    is Date -> Timestamp(raw)
    is Number -> millisToTimestamp(raw.toLong())
    is String -> parseMillis(raw)?.let { millisToTimestamp(it) }
    is Map<*, *> -> (raw["seconds"] as? Number)?.toLong()?.let { seconds ->
        Timestamp(seconds, ((raw["nanoseconds"] as? Number)?.toLong() ?: 0L).toInt())
    }
    else -> null
}

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis.div(1000L), (millis.mod(1000L) * 1_000_000L).toInt())

/** Epoch millis from an ISO-8601 string, a plain `yyyy-MM-dd HH:mm:ss`, or a numeric string. */
fun parseMillis(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return null
    try {
        return Instant.parse(t).toEpochMilli()
    } catch (_: Exception) {
        // Not a strict ISO instant; try the other shapes below.
    }
    try {
        return OffsetDateTime.parse(t).toInstant().toEpochMilli()
    } catch (_: Exception) {
        // Not an offset date-time either.
    }
    for (pattern in DATE_PATTERNS) {
        try {
            val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            fmt.isLenient = true
            fmt.parse(t)?.let { return it.time }
        } catch (_: Exception) {
            // Wrong pattern for this value; keep trying.
        }
    }
    return t.toLongOrNull()
}

private val DATE_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss.SSS",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd",
    "MMM d, yyyy h:mm:ss a",
    "M/d/yyyy h:mm:ss a",
    "M/d/yyyy"
)
