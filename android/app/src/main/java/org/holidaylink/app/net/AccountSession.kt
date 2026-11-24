package org.holidaylink.app.net

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.config.AppConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Multi-account sessions.
 *
 * Firebase Auth only ever holds one signed-in user at a time, so switching accounts
 * cannot be a purely local action. Instead each saved account keeps a long-lived
 * *account token* — minted by [mintToken] while that account's own session is held — and
 * trades it for a fresh Firebase custom token in /account/switch. Because the server only
 * issues a token to an already-authenticated session, a phone number never has to be
 * verified again just to hop between accounts.
 *
 * Deliberately not routed through [Api]: that client sends the HolidayLink session token,
 * while the server wants the Firebase ID token here (exactly like /push/send).
 */
object AccountSession {

    data class SwitchResult(val customToken: String, val uid: String, val phone: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Asks for a new account token for whoever is signed in right now. */
    suspend fun mintToken(context: Context): String? {
        val body = post(context, "/account/token", JSONObject()) ?: return null
        return body.optString("accountToken").takeIf { it.isNotBlank() }
    }

    /** Turns a stored account token into a fresh Firebase custom token. */
    suspend fun switch(context: Context, accountToken: String): SwitchResult {
        val body = post(context, "/account/switch", JSONObject().put("accountToken", accountToken))
            ?: throw ApiException("Can't connect right now. Check your internet connection and try again.", "NETWORK")
        val token = body.optString("token")
        val uid = body.optString("uid")
        if (token.isBlank() || uid.isBlank()) {
            throw ApiException(
                body.optString("message").takeIf { it.isNotBlank() } ?: "Sign in to this account again.",
                body.optString("error").takeIf { it.isNotBlank() } ?: "ACCOUNT_TOKEN_INVALID"
            )
        }
        return SwitchResult(token, uid, body.optString("phone"))
    }

    /** Forgets a removed account's token and its push token for this device. */
    suspend fun revoke(context: Context, accountToken: String, fcmToken: String?) {
        if (accountToken.isBlank()) return
        val payload = JSONObject().put("accountToken", accountToken)
        if (!fcmToken.isNullOrBlank()) payload.put("fcmToken", fcmToken)
        post(context, "/account/revoke", payload)
    }

    private suspend fun post(context: Context, path: String, payload: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            if (idToken.isNullOrBlank()) return@withContext null
            val request = Request.Builder()
                .url(AppConfig.resolve(context) + path)
                .addHeader("Authorization", "Bearer $idToken")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw.ifBlank { "{}" }) }.getOrDefault(JSONObject())
                if (!response.isSuccessful) {
                    throw ApiException(
                        json.optString("message").takeIf { it.isNotBlank() }
                            ?: "We couldn't do that right now. Please try again.",
                        json.optString("error")
                    )
                }
                return@use json
            }
        }
}
