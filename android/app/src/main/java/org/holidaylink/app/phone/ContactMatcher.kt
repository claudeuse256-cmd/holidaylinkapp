package org.holidaylink.app.phone

import android.content.Context
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Reads the device address book for the existing contact-matching feature.
 *
 * Privacy rules enforced here:
 *  - nothing is read before the user grants READ_CONTACTS through the system prompt
 *  - only SHA-256 hashes leave this function; raw numbers are never returned
 *  - a local number becomes at most a couple of candidates (device region plus the selected
 *    country) because a stored number like 0745... is ambiguous
 */
object ContactMatcher {

    private const val TAG = "HolidayLinkContacts"
    const val MAX_HASHES = 5000

    fun deviceRegionDialCode(context: Context): String? {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        val iso = runCatching { manager.networkCountryIso }.getOrNull()
        return Countries.dialCodeForRegion(iso)
    }

    /** Blocking: always call from Dispatchers.IO. */
    fun readPhoneHashes(context: Context, dialCodes: List<String>): List<String> {
        val hashes = LinkedHashSet<String>()
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)

        val cursor = runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
        }.onFailure { Log.w(TAG, "contact query failed: ${it.message}") }.getOrNull()

        cursor?.use { c ->
            val index = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (index >= 0) {
                while (c.moveToNext()) {
                    val raw = runCatching { c.getString(index) }.getOrNull() ?: continue
                    for (candidate in PhoneNumbers.candidatesFor(raw, dialCodes)) {
                        hashes.add(PhoneNumbers.sha256(candidate))
                    }
                    if (hashes.size >= MAX_HASHES) break
                }
            }
        }
        return hashes.toList()
    }
}
