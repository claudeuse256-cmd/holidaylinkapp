package org.holidaylink.app.net

/** Pure parsing helper; actual mention delivery always uses the selected user IDs. */
object StatusMentionParser {
    private val handle = Regex("(?<![A-Za-z0-9._%+-])@([A-Za-z0-9_]{1,32})")

    fun handles(text: String): List<String> = handle.findAll(text)
        .map { it.groupValues[1] }
        .distinctBy { it.lowercase() }
        .toList()
}
