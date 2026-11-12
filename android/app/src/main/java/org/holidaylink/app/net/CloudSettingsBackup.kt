package org.holidaylink.app.net

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.config.AppConfig
import org.holidaylink.app.data.AppSettings
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional end-to-end encrypted settings backup. Encryption/decryption happens on the phone;
 * the Render server only receives AES-GCM ciphertext, salt and IV. The passphrase is never sent.
 */
object CloudSettingsBackup {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).writeTimeout(25, TimeUnit.SECONDS).build()

    suspend fun emailStatus(context: Context): Pair<Boolean, String> {
        val data = request(context, "/backup/email/status", "GET", null)
        return data.optBoolean("verified") to data.optString("email")
    }

    suspend fun startEmailVerification(context: Context, email: String) {
        post(context, "/backup/email/start", JSONObject().put("email", email.trim()))
    }

    suspend fun verifyEmail(context: Context, code: String): String =
        post(context, "/backup/email/verify", JSONObject().put("code", code.trim())).optString("email")

    suspend fun save(context: Context, passphrase: CharArray) {
        require(passphrase.size >= 8) { "Use a backup passphrase with at least 8 characters." }
        val clear = AppSettings.exportSettingsBackup()
        val encrypted = encrypt(clear.toByteArray(Charsets.UTF_8), passphrase)
        val body = JSONObject().put("encrypted", encrypted.ciphertext)
            .put("salt", encrypted.salt).put("iv", encrypted.iv)
        request(context, "/backup/cloud", "PUT", body)
    }

    suspend fun restore(context: Context, passphrase: CharArray) {
        require(passphrase.size >= 8) { "Enter the passphrase you used when creating the backup." }
        val root = request(context, "/backup/cloud", "GET", null)
        val clear = decrypt(
            Encrypted(root.getString("encrypted"), root.getString("salt"), root.getString("iv")), passphrase
        ).toString(Charsets.UTF_8)
        // Validate fully before changing preferences.
        require(JSONObject(clear).optString("format") == "holidaylink-settings-backup") {
            "This cloud backup is not a valid HolidayLink settings backup."
        }
        AppSettings.importSettingsBackup(clear)
    }

    private data class Encrypted(val ciphertext: String, val salt: String, val iv: String)

    private fun encrypt(clear: ByteArray, passphrase: CharArray): Encrypted {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(clear)
        return Encrypted(b64(ciphertext), b64(salt), b64(iv))
    }

    private fun decrypt(data: Encrypted, passphrase: CharArray): ByteArray {
        val salt = unb64(data.salt)
        val iv = unb64(data.iv)
        require(salt.size == 16 && iv.size == 12) { "Backup encryption metadata is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        return try { cipher.doFinal(unb64(data.ciphertext)) }
        catch (_: Exception) { throw IllegalArgumentException("Wrong passphrase or damaged backup. Nothing was restored.") }
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, 210_000, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun unb64(value: String): ByteArray = Base64.decode(value, Base64.DEFAULT)

    private suspend fun post(context: Context, path: String, json: JSONObject): JSONObject =
        request(context, path, "POST", json)

    private suspend fun request(context: Context, path: String, method: String, json: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()?.token
                ?: throw ApiException("Please sign in again before using cloud backup.", "UNAUTHENTICATED")
            val builder = Request.Builder().url(AppConfig.resolve(context) + path)
                .header("Authorization", "Bearer $token")
            when (method) {
                "GET" -> builder.get()
                "PUT" -> builder.put((json ?: JSONObject()).toString().toRequestBody(jsonType))
                else -> builder.post((json ?: JSONObject()).toString().toRequestBody(jsonType))
            }
            client.newCall(builder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(raw).optString("message").ifBlank { JSONObject(raw).optString("error") } }.getOrNull().orEmpty()
                    throw ApiException(message.ifBlank { "Cloud backup request failed (${response.code})." }, httpCode = response.code)
                }
                JSONObject(raw.ifBlank { "{}" })
            }
        }
}
