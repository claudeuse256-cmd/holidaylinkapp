package org.holidaylink.app.net

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * imgbb image hosting: same key and pattern the website uses for every
 * upload (FormData image + ?key=). Only images go here; video, voice and
 * documents use Firebase Storage.
 */
object Imgbb {

    // Same key as holidaylink.org. Public by design there; overridable here.
    const val KEY = "004a77cb11087502ad96cdf0555781e7"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadJpeg(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) throw ApiException("That image couldn't be read. Try another one.")
        val body = FormBody.Builder()
            .add("key", KEY)
            .add("image", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .build()
        val req = Request.Builder().url("https://api.imgbb.com/1/upload").post(body).build()
        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException("Image upload failed. Check your internet and try again.")
            val url = runCatching { JSONObject(raw).optJSONObject("data")?.optString("url") }.getOrNull()
            if (url.isNullOrBlank()) throw ApiException("Image upload failed. Check your internet and try again.")
            url
        }
    }
}
