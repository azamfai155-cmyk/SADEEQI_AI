package com.azzam.sadeeqi.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object EncryptedSessionStore {
    private const val PREFS = "sadeeqi_secure_session"
    private const val DATA = "encrypted_session"
    private const val KEY_ALIAS = "sadeeqi_session_aes_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(context: Context, session: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(session.toString().toByteArray(Charsets.UTF_8))
        val packed = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(DATA, packed).apply()
    }

    fun read(context: Context): JSONObject? = runCatching {
        val packed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, null)
            ?: return null
        val envelope = JSONObject(packed)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val bytes = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        JSONObject(String(cipher.doFinal(bytes), Charsets.UTF_8))
    }.getOrNull()

    fun accessToken(context: Context): String? {
        val session = read(context) ?: return null
        val expiresAt = session.optLong("expires_at", 0L)
        if (expiresAt <= System.currentTimeMillis() / 1000L + 60L) return null
        return session.optString("access_token").takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(DATA).apply()
    }
}
