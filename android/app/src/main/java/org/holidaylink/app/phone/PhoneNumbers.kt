package org.holidaylink.app.phone

import java.security.MessageDigest

data class CountryCode(
    val iso: String,
    val name: String,
    val dialCode: String,
    val flag: String
) {
    val dialLabel: String get() = "+$dialCode"
    val searchLabel: String get() = "$flag  $name  +$dialCode"
}

object Countries {
    /** East Africa first (HolidayLink's core market), then common international destinations. */
    val ALL: List<CountryCode> = listOf(
        CountryCode("UG", "Uganda", "256", "\uD83C\uDDFA\uD83C\uDDEC"),
        CountryCode("KE", "Kenya", "254", "\uD83C\uDDF0\uD83C\uDDEA"),
        CountryCode("TZ", "Tanzania", "255", "\uD83C\uDDF9\uD83C\uDDFF"),
        CountryCode("RW", "Rwanda", "250", "\uD83C\uDDF1\uD83C\uDDF7"),
        CountryCode("SS", "South Sudan", "211", "\uD83C\uDDF8\uD83C\uDDF8"),
        CountryCode("US", "United States", "1", "\uD83C\uDDFA\uD83C\uDDF8"),
        CountryCode("GB", "United Kingdom", "44", "\uD83C\uDDEC\uD83C\uDDE7"),
        CountryCode("NG", "Nigeria", "234", "\uD83C\uDDF3\uD83C\uDDEC"),
        CountryCode("GH", "Ghana", "233", "\uD83C\uDDEC\uD83C\uDDED"),
        CountryCode("BI", "Burundi", "257", "\uD83C\uDDE7\uD83C\uDDEE"),
        CountryCode("CD", "DR Congo", "243", "\uD83C\uDDE8\uD83C\uDDED"),
        CountryCode("SO", "Somalia", "252", "\uD83C\uDDF4\uD83C\uDDF0"),
        CountryCode("ET", "Ethiopia", "251", "\uD83C\uDDEC\uD83C\uDDED"),
        CountryCode("ZA", "South Africa", "27", "\uD83C\uDDFF\uD83C\uDDE6"),
        CountryCode("EG", "Egypt", "20", "\uD83C\uDDEA\uD83C\uDDEC"),
        CountryCode("IN", "India", "91", "\uD83C\uDDEE\uD83C\uDDF3")
    )

    const val DEFAULT_ISO = "UG"

    val DEFAULT: CountryCode get() = byIso(DEFAULT_ISO)

    fun byIso(iso: String?): CountryCode =
        ALL.firstOrNull { it.iso.equals(iso?.trim(), ignoreCase = true) } ?: DEFAULT

    fun dialCodeForRegion(iso: String?): String? =
        ALL.firstOrNull { it.iso.equals(iso?.trim(), ignoreCase = true) }?.dialCode
}

/**
 * Phone number handling shared by sign in, sign up and contact matching.
 * Everything the backend sees is a single E.164 style value such as +256745473752.
 */
object PhoneNumbers {

    /**
     * Turns what the user typed plus the selected country into an international number.
     *  - 0745473752  with +256  ->  +256745473752
     *  - +256745473752          ->  +256745473752 (never double prefixed)
     *  - 00 256 745 473 752    ->  +256745473752
     *  - 256745473752           ->  +256745473752
     * Spaces, dashes, dots and brackets are always removed.
     */
    fun toInternational(dialCode: String, input: String): String {
        val raw = input.trim()
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val code = dialCode.filter { it.isDigit() }

        // Already international: "+256745..." or the 00 international prefix.
        if (raw.startsWith("+")) return "+$digits"
        if (raw.startsWith("00")) {
            val stripped = digits.drop(2)
            return if (stripped.isEmpty()) "" else "+$stripped"
        }

        // Country code typed without a plus sign, e.g. "256745473752".
        if (code.isNotEmpty() && digits.startsWith(code) && digits.length > code.length + 6) {
            return "+$digits"
        }

        // National format with trunk zero: "0745473752" -> "745473752".
        val national = digits.trimStart('0')
        if (national.isEmpty()) return ""
        return "+$code$national"
    }

    /** E.164 length check: 8 to 15 digits including the country code. */
    fun isValidInternational(value: String): Boolean {
        if (!value.startsWith("+")) return false
        val digits = value.filter { it.isDigit() }
        return digits.length in 8..15 && value.length == digits.length + 1
    }

    fun digitsOf(value: String): String = value.filter { it.isDigit() }

    /** Keeps only digits, used for OTP entry so spaces or dashes from the SMS never break input. */
    fun sanitizeOtp(value: String): String = value.filter { it.isDigit() }.take(6)

    /** "+256745473752" -> "+256•••••752", used for on-screen confirmations only. */
    fun mask(value: String): String {
        val digits = digitsOf(value)
        if (digits.length < 6) return "+$digits"
        val hidden = "\u2022".repeat(digits.length - 6)
        return "+${digits.take(3)}$hidden${digits.takeLast(3)}"
    }

    /**
     * Candidate international forms for a phone number coming from the device address book.
     * Local numbers are ambiguous (0745... could be Uganda or Kenya), so each configured
     * dial code is tried. Only SHA-256 hashes of the results are ever uploaded.
     */
    fun candidatesFor(raw: String, dialCodes: List<String>): List<String> {
        val value = raw.trim()
        val digits = digitsOf(value)
        if (digits.isEmpty()) return emptyList()
        val codes = dialCodes.filter { it.isNotBlank() }.distinct()

        if (value.startsWith("+")) return listOf("+$digits")
        if (value.startsWith("00")) {
            val stripped = digits.drop(2)
            return if (stripped.isEmpty()) emptyList() else listOf("+$stripped")
        }
        if (codes.any { digits.startsWith(it) && digits.length > it.length + 6 }) return listOf("+$digits")

        val national = digits.trimStart('0')
        if (national.isEmpty()) return emptyList()
        return codes.map { "+$it$national" }.distinct()
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
