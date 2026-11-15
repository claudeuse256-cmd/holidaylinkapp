package org.holidaylink.app.phone

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import java.text.Collator
import java.util.Locale

/** One phone number belonging to a device contact. */
data class DevicePhone(
    val raw: String,
    /** Canonical international form actually used for matching, when it could be derived. */
    val normalized: String?,
    /** SHA-256 of the canonical form. This is the only value ever sent to the server. */
    val hash: String?,
    val isPrimary: Boolean = false
) {
    /** Human readable rendering used in the contacts list. */
    fun display(): String = normalized?.let { pretty(it) } ?: pretty(raw)

    private fun pretty(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.isEmpty()) return value
        return if (value.startsWith("+")) {
            val grouped = digits.dropLast(6).chunked(3).joinToString(" ")
            if (digits.length > 6) "+${digits.take(3)} $grouped ${digits.takeLast(6)}"
            else "+$digits"
        } else digits
    }
}

/** A contact from the device address book. Names and numbers never leave the device. */
data class DeviceContact(
    val contactId: String,
    val displayName: String,
    val photoUri: String?,
    val phones: List<DevicePhone>,
    val starred: Boolean = false
) {
    val searchKey: String get() = displayName.trim().lowercase(Locale.getDefault())

    fun firstLetter(): Char =
        displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
}

/**
 * Reads the full device address book for the Contacts screen.
 *
 * Privacy contract:
 *  - nothing is read unless the user granted READ_CONTACTS through the system prompt
 *  - display names, photos and raw numbers stay on the device and are used only for display
 *  - only [DevicePhone.hash] values (SHA-256 of a canonical number) are ever uploaded
 *
 * Contacts without a phone number are skipped: there is nothing to match, invite or call.
 */
object DeviceContacts {

    private const val TAG = "HolidayLinkDeviceContacts"

    private val collator: Collator by lazy {
        Collator.getInstance().apply { strength = Collator.SECONDARY }
    }

    /** Blocking. Always call from Dispatchers.IO. */
    fun read(context: Context, dialCodes: List<String>): List<DeviceContact> {
        val byId = LinkedHashMap<String, MutableContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
        )

        val cursor = runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
            )
        }.onFailure { Log.w(TAG, "contact query failed: ${it.message}") }.getOrNull()

        cursor?.use { c ->
            val idIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val thumbIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val superPrimary = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
            val primary = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)

            if (idIndex < 0 || numberIndex < 0) return emptyList()

            while (c.moveToNext()) {
                val contactId = runCatching { c.getString(idIndex) }.getOrNull() ?: continue
                val raw = runCatching { c.getString(numberIndex) }.getOrNull()?.trim().orEmpty()
                if (raw.isEmpty()) continue

                val name = runCatching { c.getString(nameIndex) }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: raw

                val photo = (if (photoIndex >= 0) runCatching { c.getString(photoIndex) }.getOrNull() else null)
                    ?: (if (thumbIndex >= 0) runCatching { c.getString(thumbIndex) }.getOrNull() else null)

                val isPrimary = (superPrimary >= 0 && runCatching { c.getInt(superPrimary) }.getOrNull() == 1) ||
                    (primary >= 0 && runCatching { c.getInt(primary) }.getOrNull() == 1)

                val candidates = PhoneNumbers.candidatesFor(raw, dialCodes)
                val phone = DevicePhone(
                    raw = raw,
                    normalized = candidates.firstOrNull(),
                    hash = candidates.firstOrNull()?.let { PhoneNumbers.sha256(it) },
                    isPrimary = isPrimary
                )

                val existing = byId.getOrPut(contactId) { MutableContact(contactId, name, photo) }
                if (existing.name == existing.fallbackName && name != existing.fallbackName) {
                    existing.name = name
                }
                if (existing.photo == null && photo != null) existing.photo = photo
                if (existing.phones.none { it.raw == phone.raw }) existing.phones += phone
            }
        }

        return byId.values
            .map { it.toContact() }
            .sortedWith { a, b -> collator.compare(a.displayName, b.displayName) }
    }

    /**
     * Builds a fallback contact-list URI for a raw id, used when a row has no photo column.
     * [Uri.withAppendedPath] keeps this valid across Android versions.
     */
    fun contactUri(contactId: String): Uri = Uri.withAppendedPath(
        ContactsContract.Contacts.CONTENT_LOOKUP_URI,
        contactId
    )

    private class MutableContact(
        val id: String,
        var name: String,
        var photo: String?
    ) {
        val fallbackName: String = name
        val phones = mutableListOf<DevicePhone>()

        fun toContact(): DeviceContact = DeviceContact(
            contactId = id,
            displayName = name,
            photoUri = photo,
            phones = phones.sortedByDescending { it.isPrimary }
        )
    }
}
