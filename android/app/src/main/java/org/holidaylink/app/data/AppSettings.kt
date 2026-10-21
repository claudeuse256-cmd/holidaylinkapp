package org.holidaylink.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.ui.theme.ChatDensity
import org.holidaylink.app.ui.theme.ThemeMode
import java.util.Locale
import org.json.JSONObject

/** Media auto-download policy, mirroring the three states users expect. */
enum class AutoDownload { ALWAYS, WIFI_ONLY, NEVER }

/** Default timer applied to new messages. */
enum class DisappearingDuration(val seconds: Long, val label: String) {
    OFF(0L, "Off"),
    DAY_1(86_400L, "24 hours"),
    WEEK_1(604_800L, "7 days"),
    MONTH_3(2_592_000L, "90 days");

    companion object {
        fun fromSeconds(seconds: Long): DisappearingDuration =
            entries.firstOrNull { it.seconds == seconds } ?: OFF
    }
}

/** One immutable snapshot of every device-side setting. */
data class SettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: Float = 1f,
    val chatDensity: ChatDensity = ChatDensity.COMFORTABLE,
    val animationsEnabled: Boolean = true,
    val chatWallpaper: String? = null,
    val chatWallpaperSolid: Boolean = false,
    /** Built-in background used by every chat unless the chat overrides it. */
    val chatWallpaperPreset: String = "default",
    /** Per-chat background override: chatId -> preset id ("" clears the override). */
    val chatWallpaperPresets: Map<String, String> = emptyMap(),
    val autoDownloadPhotos: AutoDownload = AutoDownload.WIFI_ONLY,
    val autoDownloadVideos: AutoDownload = AutoDownload.WIFI_ONLY,
    val autoDownloadDocuments: AutoDownload = AutoDownload.NEVER,
    val saveMediaToGallery: Boolean = true,
    val dataSaver: Boolean = false,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val onlineVisibility: Boolean = true,
    val disappearing: DisappearingDuration = DisappearingDuration.OFF,
    val language: String = "en",
    val appLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val appLockPin: String? = null,
    val notifyMessages: Boolean = true,
    val notifyCalls: Boolean = true,
    val notifyStatus: Boolean = true,
    val notifyAi: Boolean = true,
    val notifySound: Boolean = true,
    val notifyVibration: Boolean = true,
    val notifyPreview: Boolean = true,
    val mutedChats: Map<String, Boolean> = emptyMap(),
    val pinnedChats: Map<String, Boolean> = emptyMap(),
    val archivedChats: Map<String, Boolean> = emptyMap(),
    val starredMessages: Map<String, Boolean> = emptyMap(),
    val hiddenMessages: Map<String, Boolean> = emptyMap(),
    val blockedUsers: Map<String, Boolean> = emptyMap(),
    val aiEnabled: Boolean = true,
    val saveAiChats: Boolean = true,
    val aiResponseStyle: String = "balanced"
)

/**
 * Device-side settings backed by SharedPreferences.
 *
 * A single immutable snapshot is exposed as Compose state, so any screen that reads
 * [state] recomposes the instant a setting changes. Nothing here talks to the network;
 * settings that the server also enforces are mirrored locally for instant UI feedback
 * and are pushed to the backend by the screens that own them.
 */
object AppSettings {

    private lateinit var prefs: SharedPreferences

    var state by mutableStateOf(SettingsSnapshot())
        private set

    private val listeners = mutableListOf<(SettingsSnapshot) -> Unit>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext
            .getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE)
        state = read()
    }

    /** Notified after every local change; used to debounce server syncs. */
    fun addListener(listener: (SettingsSnapshot) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (SettingsSnapshot) -> Unit) {
        listeners -= listener
    }

    // ------------------------------------------------------------------ keys

    private object K {
        const val THEME = "set.theme"
        const val FONT_SCALE = "set.fontScale"
        const val DENSITY = "set.chatDensity"
        const val ANIMATIONS = "set.animations"
        const val WALLPAPER = "set.wallpaper"
        const val WALLPAPER_SOLID = "set.wallpaperSolid"
        const val WALLPAPER_PRESET = "set.wallpaperPreset"
        const val WALLPAPER_PRESETS = "set.wallpaperPresets"
        const val DL_PHOTOS = "set.dlPhotos"
        const val DL_VIDEOS = "set.dlVideos"
        const val DL_DOCS = "set.dlDocs"
        const val SAVE_GALLERY = "set.saveGallery"
        const val DATA_SAVER = "set.dataSaver"
        const val RECEIPTS = "set.readReceipts"
        const val TYPING = "set.typing"
        const val ONLINE = "set.onlineVisibility"
        const val DISAPPEARING = "set.disappearing"
        const val LANGUAGE = "set.language"
        const val APP_LOCK = "set.appLock"
        const val BIOMETRIC = "set.biometric"
        const val PIN = "set.pin"
        const val N_MSG = "set.notify.messages"
        const val N_CALL = "set.notify.calls"
        const val N_STATUS = "set.notify.status"
        const val N_AI = "set.notify.ai"
        const val N_SOUND = "set.notify.sound"
        const val N_VIBRATE = "set.notify.vibration"
        const val N_PREVIEW = "set.notify.preview"
        const val MUTED = "set.mutedChats"
        const val PINNED = "set.pinnedChats"
        const val ARCHIVED = "set.archivedChats"
        const val STARRED = "set.starredMessages"
        const val HIDDEN = "set.hiddenMessages"
        const val BLOCKED = "set.blockedUsers"
        const val AI_ENABLED = "set.ai.enabled"
        const val AI_SAVE = "set.ai.save"
        const val AI_STYLE = "set.ai.style"
    }

    private fun read(): SettingsSnapshot {
        val p = prefs
        return SettingsSnapshot(
            themeMode = enumOrDefault(p.getString(K.THEME, null), ThemeMode.SYSTEM),
            fontScale = p.getFloat(K.FONT_SCALE, 1f).coerceIn(0.85f, 1.45f),
            chatDensity = enumOrDefault(p.getString(K.DENSITY, null), ChatDensity.COMFORTABLE),
            animationsEnabled = p.getBoolean(K.ANIMATIONS, true),
            chatWallpaper = p.getString(K.WALLPAPER, null),
            chatWallpaperSolid = p.getBoolean(K.WALLPAPER_SOLID, false),
            chatWallpaperPreset = p.getString(K.WALLPAPER_PRESET, "default") ?: "default",
            chatWallpaperPresets = decodeStringMap(p.getString(K.WALLPAPER_PRESETS, "").orEmpty()),
            autoDownloadPhotos = enumOrDefault(p.getString(K.DL_PHOTOS, null), AutoDownload.WIFI_ONLY),
            autoDownloadVideos = enumOrDefault(p.getString(K.DL_VIDEOS, null), AutoDownload.WIFI_ONLY),
            autoDownloadDocuments = enumOrDefault(p.getString(K.DL_DOCS, null), AutoDownload.NEVER),
            saveMediaToGallery = p.getBoolean(K.SAVE_GALLERY, true),
            dataSaver = p.getBoolean(K.DATA_SAVER, false),
            readReceipts = p.getBoolean(K.RECEIPTS, true),
            typingIndicators = p.getBoolean(K.TYPING, true),
            onlineVisibility = p.getBoolean(K.ONLINE, true),
            disappearing = enumOrDefault(p.getString(K.DISAPPEARING, null), DisappearingDuration.OFF),
            language = p.getString(K.LANGUAGE, "en") ?: "en",
            appLockEnabled = p.getBoolean(K.APP_LOCK, false),
            biometricLockEnabled = p.getBoolean(K.BIOMETRIC, false),
            appLockPin = p.getString(K.PIN, null),
            notifyMessages = p.getBoolean(K.N_MSG, true),
            notifyCalls = p.getBoolean(K.N_CALL, true),
            notifyStatus = p.getBoolean(K.N_STATUS, true),
            notifyAi = p.getBoolean(K.N_AI, true),
            notifySound = p.getBoolean(K.N_SOUND, true),
            notifyVibration = p.getBoolean(K.N_VIBRATE, true),
            notifyPreview = p.getBoolean(K.N_PREVIEW, true),
            mutedChats = decodeMuted(p.getString(K.MUTED, "").orEmpty()),
            pinnedChats = decodeMuted(p.getString(K.PINNED, "").orEmpty()),
            archivedChats = decodeMuted(p.getString(K.ARCHIVED, "").orEmpty()),
            starredMessages = decodeMuted(p.getString(K.STARRED, "").orEmpty()),
            hiddenMessages = decodeMuted(p.getString(K.HIDDEN, "").orEmpty()),
            blockedUsers = decodeMuted(p.getString(K.BLOCKED, "").orEmpty()),
            aiEnabled = p.getBoolean(K.AI_ENABLED, true),
            saveAiChats = p.getBoolean(K.AI_SAVE, true),
            aiResponseStyle = p.getString(K.AI_STYLE, "balanced") ?: "balanced"
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun decodeMuted(raw: String): Map<String, Boolean> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, Boolean>()
        for (pair in raw.split(';')) {
            if (pair.isBlank()) continue
            val idx = pair.lastIndexOf('=')
            if (idx <= 0) continue
            result[pair.substring(0, idx)] = pair.substring(idx + 1) == "1"
        }
        return result
    }

    /** Reads a `key=value` map whose values are plain strings (used for per-chat presets). */
    private fun decodeStringMap(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split(';')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val value = pair.substring(idx + 1)
            if (value.isNotBlank()) result[pair.substring(0, idx)] = value
        }
        return result
    }

    private fun encodeStringMap(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun encodeMuted(map: Map<String, Boolean>): String =
        map.entries.joinToString(";") { "${it.key}=${if (it.value) 1 else 0}" }

    private fun commit(): SettingsSnapshot {
        state = read()
        listeners.toList().forEach { runCatching { it(state) } }
        return state
    }

    /**
     * Export device-side preferences for a user-controlled local backup file.
     * Authentication material and app-lock secrets are deliberately never exported.
     */
    fun exportSettingsBackup(): String {
        check(::prefs.isInitialized) { "App settings have not been initialized" }
        val excluded = setOf("set.pin", "set.appLock", "set.biometric")
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith("set.") || key in excluded || value == null) return@forEach
            val item = JSONObject()
            when (value) {
                is String -> item.put("type", "string").put("value", value)
                is Boolean -> item.put("type", "boolean").put("value", value)
                is Int -> item.put("type", "int").put("value", value)
                is Long -> item.put("type", "long").put("value", value)
                is Float -> item.put("type", "float").put("value", value.toDouble())
                is Set<*> -> item.put("type", "string_set").put("value", org.json.JSONArray(value.filterIsInstance<String>()))
                else -> return@forEach
            }
            values.put(key, item)
        }
        return JSONObject()
            .put("format", "holidaylink-settings-backup")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("settings", values)
            .toString(2)
    }

    /** Restore only supported device settings; never imports credentials or lock secrets. */
    fun importSettingsBackup(json: String) {
        check(::prefs.isInitialized) { "App settings have not been initialized" }
        val root = JSONObject(json)
        require(root.optString("format") == "holidaylink-settings-backup") {
            "This is not a HolidayLink settings backup"
        }
        require(root.optInt("version", -1) == 1) { "Unsupported backup version" }
        val values = root.optJSONObject("settings") ?: error("Backup has no settings")
        val excluded = setOf("set.pin", "set.appLock", "set.biometric")
        val editor = prefs.edit()
        // Replace only this app's setting keys; leave auth/session and unrelated preferences alone.
        prefs.all.keys.filter { it.startsWith("set.") && it !in excluded }.forEach(editor::remove)
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith("set.") || key in excluded) continue
            val item = values.optJSONObject(key) ?: continue
            val value = item.opt("value")
            when (item.optString("type")) {
                "string" -> if (value is String) editor.putString(key, value)
                "boolean" -> if (value is Boolean) editor.putBoolean(key, value)
                "int" -> if (value is Number) editor.putInt(key, value.toInt())
                "long" -> if (value is Number) editor.putLong(key, value.toLong())
                "float" -> if (value is Number) editor.putFloat(key, value.toFloat())
                "string_set" -> {
                    val array = item.optJSONArray("value") ?: continue
                    val set = buildSet { for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotEmpty() }?.let(::add) }
                    editor.putStringSet(key, set)
                }
            }
        }
        check(editor.commit()) { "Could not save restored settings" }
        commit()
    }

    // ------------------------------------------------------------------ mutations

    private inline fun <reified T : Enum<T>> putEnum(key: String, value: T) {
        prefs.edit().putString(key, value.name).apply()
        commit()
    }

    fun setThemeMode(value: ThemeMode) = putEnum(K.THEME, value)
    fun setChatDensity(value: ChatDensity) = putEnum(K.DENSITY, value)
    fun setFontScale(value: Float) {
        prefs.edit().putFloat(K.FONT_SCALE, value.coerceIn(0.85f, 1.45f)).apply()
        commit()
    }

    fun setAnimations(value: Boolean) {
        prefs.edit().putBoolean(K.ANIMATIONS, value).apply()
        commit()
    }

    fun setChatWallpaper(uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove(K.WALLPAPER) else putString(K.WALLPAPER, uri)
        }.apply()
        commit()
    }

    fun setChatWallpaperSolid(value: Boolean) {
        prefs.edit().putBoolean(K.WALLPAPER_SOLID, value).apply()
        commit()
    }

    /** The built-in background every chat uses by default. */
    fun setChatWallpaperPreset(value: String) {
        prefs.edit().putString(K.WALLPAPER_PRESET, value).apply()
        commit()
    }

    /** Background chosen for one conversation only; null removes the override. */
    fun setChatWallpaperPresetFor(chatId: String, preset: String?) {
        val next = LinkedHashMap(state.chatWallpaperPresets)
        if (preset.isNullOrBlank()) next.remove(chatId) else next[chatId] = preset
        prefs.edit().putString(K.WALLPAPER_PRESETS, encodeStringMap(next)).apply()
        commit()
    }

    /** The background actually used by a chat: its own choice, else the global default. */
    fun wallpaperPresetFor(chatId: String): String =
        state.chatWallpaperPresets[chatId] ?: state.chatWallpaperPreset

    fun setAutoDownloadPhotos(value: AutoDownload) = putEnum(K.DL_PHOTOS, value)
    fun setAutoDownloadVideos(value: AutoDownload) = putEnum(K.DL_VIDEOS, value)
    fun setAutoDownloadDocuments(value: AutoDownload) = putEnum(K.DL_DOCS, value)

    fun setSaveMediaToGallery(value: Boolean) {
        prefs.edit().putBoolean(K.SAVE_GALLERY, value).apply()
        commit()
    }

    fun setDataSaver(value: Boolean) {
        prefs.edit().putBoolean(K.DATA_SAVER, value).apply()
        commit()
    }

    fun setReadReceipts(value: Boolean) {
        prefs.edit().putBoolean(K.RECEIPTS, value).apply()
        commit()
    }

    fun setTypingIndicators(value: Boolean) {
        prefs.edit().putBoolean(K.TYPING, value).apply()
        commit()
    }

    fun setOnlineVisibility(value: Boolean) {
        prefs.edit().putBoolean(K.ONLINE, value).apply()
        commit()
    }

    fun setDisappearing(value: DisappearingDuration) = putEnum(K.DISAPPEARING, value)

    fun setLanguage(tag: String) {
        prefs.edit().putString(K.LANGUAGE, tag.lowercase(Locale.US)).apply()
        commit()
    }

    fun setAppLockEnabled(value: Boolean) {
        prefs.edit().putBoolean(K.APP_LOCK, value).apply()
        commit()
    }

    fun setBiometricLockEnabled(value: Boolean) {
        prefs.edit().putBoolean(K.BIOMETRIC, value).apply()
        commit()
    }

    fun setAppLockPin(pin: String?) {
        prefs.edit().apply { if (pin == null) remove(K.PIN) else putString(K.PIN, pin) }.apply()
        commit()
    }

    fun setNotifyMessages(value: Boolean) = flag(K.N_MSG, value)
    fun setNotifyCalls(value: Boolean) = flag(K.N_CALL, value)
    fun setNotifyStatus(value: Boolean) = flag(K.N_STATUS, value)
    fun setNotifyAi(value: Boolean) = flag(K.N_AI, value)
    fun setNotifySound(value: Boolean) = flag(K.N_SOUND, value)
    fun setNotifyVibration(value: Boolean) = flag(K.N_VIBRATE, value)
    fun setNotifyPreview(value: Boolean) = flag(K.N_PREVIEW, value)

    private fun flag(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        commit()
    }

    fun isChatMuted(conversationId: String): Boolean =
        state.mutedChats[conversationId] == true

    fun setChatMuted(conversationId: String, muted: Boolean) {
        val next = LinkedHashMap(state.mutedChats)
        if (muted) next[conversationId] = true else next.remove(conversationId)
        prefs.edit().putString(K.MUTED, encodeMuted(next)).apply()
        commit()
    }

    fun isChatPinned(conversationId: String): Boolean =
        state.pinnedChats[conversationId] == true

    fun setChatPinned(conversationId: String, pinned: Boolean) {
        val next = LinkedHashMap(state.pinnedChats)
        if (pinned) next[conversationId] = true else next.remove(conversationId)
        prefs.edit().putString(K.PINNED, encodeMuted(next)).apply()
        commit()
    }

    fun isChatArchived(conversationId: String): Boolean =
        state.archivedChats[conversationId] == true

    fun setChatArchived(conversationId: String, archived: Boolean) {
        val next = LinkedHashMap(state.archivedChats)
        if (archived) next[conversationId] = true else next.remove(conversationId)
        prefs.edit().putString(K.ARCHIVED, encodeMuted(next)).apply()
        commit()
    }

    fun isMessageStarred(chatId: String, messageId: String): Boolean =
        state.starredMessages["$chatId/$messageId"] == true

    fun setMessageStarred(chatId: String, messageId: String, starred: Boolean) {
        val key = "$chatId/$messageId"
        val next = LinkedHashMap(state.starredMessages)
        if (starred) next[key] = true else next.remove(key)
        prefs.edit().putString(K.STARRED, encodeMuted(next)).apply()
        commit()
    }

    /** Local "delete for me": the message stays on the server for everyone else. */
    fun isMessageHidden(chatId: String, messageId: String): Boolean =
        state.hiddenMessages["$chatId/$messageId"] == true

    fun setMessageHidden(chatId: String, messageId: String, hidden: Boolean) {
        val key = "$chatId/$messageId"
        val next = LinkedHashMap(state.hiddenMessages)
        if (hidden) next[key] = true else next.remove(key)
        prefs.edit().putString(K.HIDDEN, encodeMuted(next)).apply()
        commit()
    }

    /** Local contact block: the composer locks on this device. */
    fun isUserBlocked(userId: String): Boolean = state.blockedUsers[userId] == true

    fun setUserBlocked(userId: String, blocked: Boolean) {
        val next = LinkedHashMap(state.blockedUsers)
        if (blocked) next[userId] = true else next.remove(userId)
        prefs.edit().putString(K.BLOCKED, encodeMuted(next)).apply()
        commit()
    }

    fun setAiEnabled(value: Boolean) = flag(K.AI_ENABLED, value)
    fun setSaveAiChats(value: Boolean) = flag(K.AI_SAVE, value)
    fun setAiResponseStyle(value: String) {
        prefs.edit().putString(K.AI_STYLE, value).apply()
        commit()
    }

    /**
     * Clears local presentation state only.
     * Deliberately never touches the auth token, the app-lock PIN or any server data,
     * so "Clear cache" can never destroy a session or unlock the app for someone else.
     */
    fun clearLocalCache() {
        prefs.edit()
            .remove(K.WALLPAPER)
            .remove(K.MUTED)
            .apply()
        commit()
    }
}
