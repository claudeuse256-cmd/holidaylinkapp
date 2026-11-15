package org.holidaylink.app.net

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Device-to-device message encryption. Private key never leaves Android Keystore. */
class E2ee(private val context: Context) {
    private val alias = "holidaylink-e2ee-device"
    private val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        if (ks.containsAlias(alias)) return
        val gen = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        gen.generateKeyPair()
    }

    fun publicKey(): String {
        ensureKey()
        return Base64.encodeToString(ks.getCertificate(alias).publicKey.encoded, Base64.NO_WRAP)
    }

    private fun privateKey() = run { ensureKey(); ks.getKey(alias, null) }

    private fun keyFor(remotePublicKey: String): SecretKeySpec {
        val bytes = Base64.decode(remotePublicKey, Base64.DEFAULT)
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey())
        agreement.doPhase(pub, true)
        val digest = MessageDigest.getInstance("SHA-256").digest(agreement.generateSecret())
        return SecretKeySpec(digest, "AES")
    }

    private fun encrypt(plain: String, remotePublicKey: String): JSONObject {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(remotePublicKey), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return JSONObject().put("iv", b64(iv)).put("ct", b64(ciphertext))
    }

    private fun decrypt(entry: JSONObject, senderPublicKey: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyFor(senderPublicKey), GCMParameterSpec(128, b64d(entry.getString("iv"))))
        return String(cipher.doFinal(b64d(entry.getString("ct"))), StandardCharsets.UTF_8)
    }

    fun envelope(plain: String, recipients: Map<String, String>): String {
        val out = JSONObject()
            .put("v", 1)
            .put("alg", "ECDH-P256-AES-256-GCM")
            .put("sender", publicKey())
        val entries = JSONObject()
        for ((id, key) in recipients) entries.put(id, encrypt(plain, key))
        out.put("entries", entries)
        return out.toString()
    }

    fun decryptEnvelope(value: String, userId: String): String? {
        return try {
            val o = JSONObject(value)
            if (o.optInt("v") != 1 || !o.has("entries")) return null
            val entry = o.getJSONObject("entries").optJSONObject(userId) ?: return null
            decrypt(entry, o.getString("sender"))
        } catch (_: Exception) { null }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.DEFAULT)
}
