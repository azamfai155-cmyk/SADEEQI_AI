package com.azzam.sadeeqi.memory

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

class EncryptedMemory(context: Context) {

    private val appContext = context.applicationContext

    private val preferences = appContext.getSharedPreferences(
        "sadeeqi_encrypted_memory",
        Context.MODE_PRIVATE
    )

    private val alias = "SADEEQI_MEMORY_KEY"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(alias, null) as? SecretKey

        if (existingKey != null) {
            return existingKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(specification)
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            getSecretKey()
        )

        val encrypted = cipher.doFinal(
            value.toByteArray(Charsets.UTF_8)
        )

        val combined = cipher.iv + encrypted

        return Base64.encodeToString(
            combined,
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(
            value,
            Base64.NO_WRAP
        )

        require(combined.size > IV_SIZE) {
            "بيانات الذاكرة غير صالحة."
        }

        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(
            IV_SIZE,
            combined.size
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(
            Cipher.DECRYPT_MODE,
            getSecretKey(),
            GCMParameterSpec(TAG_SIZE, iv)
        )

        return String(
            cipher.doFinal(encrypted),
            Charsets.UTF_8
        )
    }

    fun save(
        key: String,
        category: String,
        content: String
    ) {
        require(key.isNotBlank()) {
            "مفتاح الذاكرة لا يمكن أن يكون فارغاً."
        }

        val record = JSONObject()
            .put("category", category)
            .put("content", content)
            .toString()

        val encryptedRecord = encrypt(record)

        preferences.edit()
            .putString(key, encryptedRecord)
            .apply()
    }

    fun search(query: String): List<Pair<String, String>> {
        return preferences.all
            .filterKeys { it.contains(query, ignoreCase = true) }
            .mapNotNull { (key, value) ->
                try {
                    val encryptedRecord = value as? String
                        ?: return@mapNotNull null

                    val decryptedRecord = JSONObject(
                        decrypt(encryptedRecord)
                    )

                    val content = decryptedRecord.optString("content")

                    if (content.isNotBlank()) {
                        key to content
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
    }

    fun delete(key: String) {
        preferences.edit()
            .remove(key)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }
}
