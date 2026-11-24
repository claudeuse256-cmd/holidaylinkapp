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

/**
 * A message waiting to go out at a chosen time.
 *
 * Kept locally as a lightweight display cache. The actual scheduled send is queued on Render.
 */
data class ScheduledMessage(
    val id: String,
    val chatId: String,
    val chatLabel: String,
    val text: String,
    val atMs: Long
)

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
    /** 1f is untouched; lower values darken the wallpaper so text stays readable. */
    val wallpaperBrightness: Float = 1f,
    /** User crop zoom for a custom photo wallpaper. */
    val wallpaperCropZoom: Float = 1f,
    val wallpaperCropX: Float = 0f,
    val wallpaperCropY: Float = 0f,
    /** Id of the screen transition the user picked (see org.holidaylink.app.ui.NavTransition). */
    val transitionStyle: String = "none",
    /** Colours of the message bubbles: a preset id or "custom:RRGGBB" (see ui/ChatThemes). */
    val chatTheme: String = "default",
    /** Chats that must be unlocked biometrically every time they are opened. */
    val lockedChats: Map<String, Boolean> = emptyMap(),
    val scheduledMessages: List<ScheduledMessage> = emptyList(),
    val autoDownloadPhotos: AutoDownload = AutoDownload.WIFI_ONLY,
    val autoDownloadVideos: AutoDownload = AutoDownload.WIFI_ONLY,
    val autoDownloadDocuments: AutoDownload = AutoDownload.NEVER,
    val saveMediaToGallery: Boolean = true,
    val dataSaver: Boolean = false,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val onlineVisibility: Boolean = true,
    /** iPhone-style liquid/transparent surfaces across the app. */
    val transparentLook: Boolean = false,
    /** Lower animation frame rate and disable non-essential motion to save battery. */
    val performanceMode: Boolean = false,
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
        const val WALLPAPER_BRIGHTNESS = "set.wallpaperBrightness"
        const val WALLPAPER_CROP_ZOOM = "set.wallpaperCropZoom"
        const val WALLPAPER_CROP_X = "set.wallpaperCropX"
        const val WALLPAPER_CROP_Y = "set.wallpaperCropY"
        const val TRANSITION = "set.transition"
        const val CHAT_THEME = "set.chatTheme"
        const val LOCKED_CHATS = "set.lockedChats"
        const val SCHEDULED = "set.scheduled"
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
    const val TRANSPARENT = "set.transparent"
    const val PERFORMANCE = "set.performance"
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
            wallpaperBrightness = p.getFloat(K.WALLPAPER_BRIGHTNESS, 1f).coerceIn(0.35f, 1f),
            wallpaperCropZoom = p.getFloat(K.WALLPAPER_CROP_ZOOM, 1f).coerceIn(1f, 2.5f),
            wallpaperCropX = p.getFloat(K.WALLPAPER_CROP_X, 0f).coerceIn(-1f, 1f),
            wallpaperCropY = p.getFloat(K.WALLPAPER_CROP_Y, 0f).coerceIn(-1f, 1f),
            transitionStyle = p.getString(K.TRANSITION, "none") ?: "none",
            chatTheme = p.getString(K.CHAT_THEME, "default") ?: "default",
            lockedChats = decodeMuted(p.getString(K.LOCKED_CHATS, "").orEmpty()),
            scheduledMessages = decodeScheduled(p.getString(K.SCHEDULED, "").orEmpty()),
            autoDownloadPhotos = enumOrDefault(p.getString(K.DL_PHOTOS, null), AutoDownload.WIFI_ONLY),
            autoDownloadVideos = enumOrDefault(p.getString(K.DL_VIDEOS, null), AutoDownload.WIFI_ONLY),
            autoDownloadDocuments = enumOrDefault(p.getString(K.DL_DOCS, null), AutoDownload.NEVER),
            saveMediaToGallery = p.getBoolean(K.SAVE_GALLERY, true),
            dataSaver = p.getBoolean(K.DATA_SAVER, false),
            readReceipts = p.getBoolean(K.RECEIPTS, true),
            typingIndicators = p.getBoolean(K.TYPING, true),
            onlineVisibility = p.getBoolean(K.ONLINE, true),
            transparentLook = p.getBoolean(K.TRANSPARENT, false),
            performanceMode = p.getBoolean(K.PERFORMANCE, false),
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

    /** Scheduled messages hold free text, so they are stored as JSON rather than `key=value`. */
    private fun decodeScheduled(raw: String): List<ScheduledMessage> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val chatId = o.optString("chatId")
                val at = o.optLong("atMs")
                if (chatId.isBlank() || at <= 0L) return@mapNotNull null
                ScheduledMessage(
                    id = o.optString("id").ifBlank { "$chatId-$at" },
                    chatId = chatId,
                    chatLabel = o.optString("chatLabel"),
                    text = o.optString("text"),
                    atMs = at
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeScheduled(list: List<ScheduledMessage>): String {
        val array = org.json.JSONArray()
        list.sortedBy { it.atMs }.forEach { m ->
            array.put(
                JSONObject()
                    .put("id", m.id)
                    .put("chatId", m.chatId)
                    .put("chatLabel", m.chatLabel)
                    .put("text", m.text)
                    .put("atMs", m.atMs)
            )
        }
        return array.toString()
    }

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

    /**
     * A personal photo and a built-in design are alternatives, not layers. Picking a photo
     * clears the preset and the solid flag, otherwise a preset chosen earlier keeps winning
     * the precedence rule in ChatWallpaper and the photo never appears anywhere.
     */
    fun setChatWallpaper(uri: String?) {
        prefs.edit().apply {
            if (uri == null) {
                remove(K.WALLPAPER)
            } else {
                putString(K.WALLPAPER, uri)
                putString(K.WALLPAPER_PRESET, "default")
                putBoolean(K.WALLPAPER_SOLID, false)
                // A wallpaper picked from the device is a "set it everywhere" action: drop
                // per-chat presets, otherwise those chats keep their old design and the new
                // photo looks like it was never applied.
                remove(K.WALLPAPER_PRESETS)
            }
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

    fun setWallpaperCropZoom(value: Float) {
        prefs.edit().putFloat(K.WALLPAPER_CROP_ZOOM, value.coerceIn(1f, 2.5f)).apply()
        commit()
    }
    fun setWallpaperCropPosition(x: Float, y: Float) {
        prefs.edit().putFloat(K.WALLPAPER_CROP_X, x.coerceIn(-1f, 1f))
            .putFloat(K.WALLPAPER_CROP_Y, y.coerceIn(-1f, 1f)).apply()
        commit()
    }

    fun setWallpaperBrightness(value: Float) {
        prefs.edit().putFloat(K.WALLPAPER_BRIGHTNESS, value.coerceIn(0.35f, 1f)).apply()
        commit()
    }

    fun setTransitionStyle(value: String) {
        prefs.edit().putString(K.TRANSITION, value).apply()
        commit()
    }

    fun setChatTheme(value: String) {
        prefs.edit().putString(K.CHAT_THEME, value).apply()
        commit()
    }

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

    fun setTransparentLook(value: Boolean) {
        prefs.edit().putBoolean(K.TRANSPARENT, value).apply()
        commit()
    }

    fun setPerformanceMode(value: Boolean) {
        prefs.edit().putBoolean(K.PERFORMANCE, value).apply()
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

    // ------------------------------------------------------------------ chat lock

    /**
     * Chats unlocked during this session; cleared whenever the app leaves the foreground.
     * Snapshot-backed so the lock screen disappears the moment the user authenticates.
     */
    private val unlockedNow = androidx.compose.runtime.mutableStateListOf<String>()

    fun isChatLocked(chatId: String): Boolean = state.lockedChats[chatId] == true

    fun setChatLocked(chatId: String, locked: Boolean) {
        val next = LinkedHashMap(state.lockedChats)
        if (locked) next[chatId] = true else next.remove(chatId)
        if (!locked) unlockedNow.remove(chatId)
        prefs.edit().putString(K.LOCKED_CHATS, encodeMuted(next)).apply()
        commit()
    }

    fun isChatUnlockedNow(chatId: String): Boolean = unlockedNow.contains(chatId)

    fun markChatUnlocked(chatId: String) { unlockedNow.add(chatId) }

    fun clearUnlockedChats() { unlockedNow.clear() }

    // ------------------------------------------------------------------ scheduled messages

    fun scheduledFor(chatId: String): List<ScheduledMessage> =
        state.scheduledMessages.filter { it.chatId == chatId }.sortedBy { it.atMs }

    /** Legacy local due-message query; delivery is now owned by the Render server. */
    fun dueScheduled(nowMs: Long = System.currentTimeMillis()): List<ScheduledMessage> =
        state.scheduledMessages.filter { it.atMs <= nowMs }.sortedBy { it.atMs }

    fun addScheduled(chatId: String, chatLabel: String, text: String, atMs: Long): ScheduledMessage {
        val item = ScheduledMessage(
            id = java.util.UUID.randomUUID().toString(),
            chatId = chatId,
            chatLabel = chatLabel,
            text = text,
            atMs = atMs
        )
        writeScheduled(state.scheduledMessages + item)
        return item
    }

    fun removeScheduled(id: String) {
        writeScheduled(state.scheduledMessages.filter { it.id != id })
    }

    private fun writeScheduled(list: List<ScheduledMessage>) {
        prefs.edit().putString(K.SCHEDULED, encodeScheduled(list)).apply()
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
