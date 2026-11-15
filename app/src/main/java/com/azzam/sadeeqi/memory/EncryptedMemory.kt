package com.azzam.sadeeqi.memory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Room stores only opaque random row IDs and authenticated ciphertext. */
@Entity(tableName = "secure_memory")
data class MemoryRow(@PrimaryKey val id: String, val category: String, val iv: String, val ciphertext: String, val updatedAt: Long)

@Dao interface MemoryDao {
    @Query("SELECT * FROM secure_memory ORDER BY updatedAt DESC") suspend fun all(): List<MemoryRow>
    @Query("SELECT * FROM secure_memory ORDER BY rowid DESC LIMIT :limit") suspend fun recent(limit: Int): List<MemoryRow>
    @Query("SELECT * FROM secure_memory WHERE id NOT LIKE 'v2:%'") suspend fun legacyRows(): List<MemoryRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(row: MemoryRow)
    @Query("DELETE FROM secure_memory WHERE id=:id") suspend fun delete(id: String)
    @Query("DELETE FROM secure_memory") suspend fun clear()
}

@Database(entities = [MemoryRow::class], version = 1, exportSchema = true)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun dao(): MemoryDao
    companion object {
        @Volatile private var instance: MemoryDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MemoryDatabase::class.java, "sadeeqi_secure.db").build().also { instance = it }
        }
    }
}

/**
 * Device-at-rest encryption: AES-256-GCM with a non-exportable Android Keystore key.
 * All user-controlled metadata (logical ID, category and timestamp) is inside ciphertext.
 * The Room columns category/updatedAt contain constant placeholders for compatibility.
 */
class EncryptedMemory(context: Context) {
    private val appContext = context.applicationContext
    private val dao = MemoryDatabase.get(appContext).dao()
    private val alias = "SADEEQI_MEMORY_AES_GCM_V1"
    private val legacyAlias = "SADEEQI_MEMORY_KEY"
    private val legacyPreferences = appContext.getSharedPreferences("sadeeqi_encrypted_memory", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private fun deviceKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun un64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private fun encryptDevice(rowId: String, plain: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        cipher.updateAAD(rowId.toByteArray(StandardCharsets.UTF_8))
        return b64(cipher.iv) to b64(cipher.doFinal(plain))
    }

    private fun decryptDevice(rowId: String, iv: String, ciphertext: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, un64(iv)))
        // New rows authenticate their opaque database key as AAD. Legacy v1 rows did not.
        if (rowId.startsWith("v2:")) cipher.updateAAD(rowId.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(un64(ciphertext))
    }

    private data class Record(val logicalId: String, val category: String, val value: String, val updatedAt: Long)
    private fun decode(row: MemoryRow): Record {
        val raw = String(decryptDevice(row.id, row.iv, row.ciphertext), StandardCharsets.UTF_8)
        return if (row.id.startsWith("v2:")) {
            val j = JSONObject(raw)
            Record(j.getString("id"), j.getString("category"), j.getString("value"), j.getLong("updatedAt"))
        } else {
            // Backward compatibility with the original schema: migrate old plaintext metadata on next write.
            Record(row.id, row.category, raw, row.updatedAt)
        }
    }


    /**
     * One-time migration from the earlier AES-GCM SharedPreferences store.
     * Old keys are plaintext logical IDs; decrypt every record first and only
     * clear the legacy file after all records have been committed to Room.
     */
    private suspend fun migrateLegacyPreferences() {
        val entries = legacyPreferences.all.mapNotNull { (id, value) ->
            (value as? String)?.let { id to it }
        }
        if (entries.isEmpty()) return

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val legacyKey = keyStore.getKey(legacyAlias, null) as? SecretKey
            ?: throw IllegalStateException("تعذر العثور على مفتاح الذاكرة القديم؛ لم تُحذف البيانات القديمة.")
        val decoded = entries.map { (id, encoded) ->
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > 12) { "سجل ذاكرة قديم غير صالح؛ لم تُحذف البيانات القديمة." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(128, combined.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(combined.copyOfRange(12, combined.size)), StandardCharsets.UTF_8))
            Triple(id, json.optString("category", "legacy"), json.getString("content"))
        }
        decoded.forEach { (id, category, value) ->
            storeRecord(Record(id, category, value, System.currentTimeMillis()))
        }
        check(legacyPreferences.edit().clear().commit()) {
            "تم نقل الذاكرة لكن تعذر تنظيف التخزين القديم؛ ستُعاد محاولة التحقق في المرة القادمة."
        }
    }

    private suspend fun migrateLegacyData() {
        migrateLegacyPreferences()
        migrateLegacyRows()
    }

    private suspend fun storeRecord(record: Record) {
        val rowId = "v2:" + UUID.randomUUID().toString()
        val payload = JSONObject().put("id", record.logicalId).put("category", record.category)
            .put("value", record.value).put("updatedAt", record.updatedAt).toString()
        val (iv, ct) = encryptDevice(rowId, payload.toByteArray(StandardCharsets.UTF_8))
        dao.put(MemoryRow(rowId, "__encrypted__", iv, ct, 0L))
    }

    private suspend fun migrateLegacyRows() {
        dao.legacyRows().forEach { old ->
            val record = decode(old)
            val rowId = "v2:" + UUID.randomUUID().toString()
            val payload = JSONObject().put("id", record.logicalId).put("category", record.category)
                .put("value", record.value).put("updatedAt", record.updatedAt).toString()
            val (iv, ct) = encryptDevice(rowId, payload.toByteArray(StandardCharsets.UTF_8))
            dao.put(MemoryRow(rowId, "__encrypted__", iv, ct, 0L))
            dao.delete(old.id)
        }
    }

    suspend fun save(id: String, category: String, value: String) = withContext(Dispatchers.IO) {
        require(id.isNotBlank() && category.isNotBlank())
        migrateLegacyData()
        storeRecord(Record(id, category, value, System.currentTimeMillis()))
    }

    suspend fun read(id: String): String? = withContext(Dispatchers.IO) {
        migrateLegacyData()
        dao.all().firstNotNullOfOrNull { row -> runCatching { decode(row).takeIf { it.logicalId == id }?.value }.getOrNull() }
    }

    /** Returns a bounded set of newest encrypted records for relevance ranking. */
    suspend fun recent(limit: Int = 200): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        require(limit in 1..500) { "حد استرجاع الذاكرة غير صالح." }
        migrateLegacyData()
        dao.recent(limit).mapNotNull { row -> runCatching { decode(row).let { it.logicalId to it.value } }.getOrNull() }
    }

    suspend fun search(query: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        migrateLegacyData()
        dao.all().mapNotNull { row -> runCatching { decode(row) }.getOrNull() }
            .filter { it.logicalId.contains(query, true) || it.category.contains(query, true) || it.value.contains(query, true) }
            .map { it.logicalId to it.value }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        migrateLegacyData()
        dao.all().filter { row -> runCatching { decode(row).logicalId == id }.getOrDefault(false) }.forEach { dao.delete(it.id) }
    }

    suspend fun update(id: String, category: String, value: String) = withContext(Dispatchers.IO) {
        migrateLegacyData()
        dao.all().filter { row -> runCatching { decode(row).logicalId == id }.getOrDefault(false) }.forEach { dao.delete(it.id) }
        storeRecord(Record(id, category, value, System.currentTimeMillis()))
    }

    /** Portable encrypted backup. Password-derived key is independent of this device's Keystore. */
    suspend fun exportEncrypted(password: CharArray): String = withContext(Dispatchers.IO) {
        require(password.size >= 12) { "استخدم كلمة مرور من 12 خانة على الأقل" }
        migrateLegacyData()
        val records = JSONArray()
        dao.all().forEach { row -> decode(row).let { r -> records.put(JSONObject().put("id", r.logicalId).put("category", r.category).put("value", r.value).put("updatedAt", r.updatedAt)) } }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveBackupKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD("SADEEQI_BACKUP_V1".toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.doFinal(records.toString().toByteArray(StandardCharsets.UTF_8))
        JSONObject().put("format", "SADEEQI_BACKUP_V1").put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", 250000).put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(encrypted)).toString()
    }

    suspend fun importEncrypted(blob: String, password: CharArray) = withContext(Dispatchers.IO) {
        require(password.size >= 12) { "كلمة المرور غير صالحة" }
        val envelope = JSONObject(blob)
        require(envelope.getString("format") == "SADEEQI_BACKUP_V1")
        require(envelope.getInt("iterations") == 250000)
        val key = deriveBackupKey(password, un64(envelope.getString("salt")))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, un64(envelope.getString("iv"))))
        cipher.updateAAD("SADEEQI_BACKUP_V1".toByteArray(StandardCharsets.UTF_8))
        val records = JSONArray(String(cipher.doFinal(un64(envelope.getString("data"))), StandardCharsets.UTF_8))
        // Authenticate and parse the complete archive before modifying the database.
        val validated = (0 until records.length()).map { i -> records.getJSONObject(i).let { j ->
            Record(j.getString("id"), j.getString("category"), j.getString("value"), j.getLong("updatedAt"))
        } }
        migrateLegacyData()
        validated.forEach { r ->
            storeRecord(r)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        migrateLegacyData()
        dao.clear()
    }

    private fun deriveBackupKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, 250000, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
}
