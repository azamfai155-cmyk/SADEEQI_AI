package com.azzam.sadeeqi.auth

import android.content.Context
import com.azzam.sadeeqi.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Private Supabase Storage client. Object paths are always scoped to the
 * authenticated user's UUID; callers cannot select another user's folder.
 */
class SupabaseStorageClient(context: Context) {
    private val auth = SupabaseAuthClient(context.applicationContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = auth.isConfigured()

    fun upload(file: File, contentType: String): String {
        require(file.isFile && file.canRead()) { "الملف غير موجود أو لا يمكن قراءته." }
        require(file.length() in 1..MAX_FILE_BYTES) { "حجم الملف يجب ألا يتجاوز 10 MB وألا يكون فارغاً." }
        require(contentType in ALLOWED_TYPES) { "نوع الملف غير مسموح." }
        val userId = currentUserId()
        val safeName = file.name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(100)
            .ifBlank { "file" }
        val objectPath = "$userId/${UUID.randomUUID()}_$safeName"
        val request = storageRequest("object/$BUCKET/$objectPath")
            .header("Content-Type", contentType)
            .header("x-upsert", "false")
            .post(file.readBytes().toRequestBody(contentType.toMediaType()))
            .build()
        execute(request) { response ->
            require(response.isSuccessful) { errorMessage(response.code, response.body?.string().orEmpty()) }
            objectPath
        }
    }

    /** Replace an existing object only when it belongs to the signed-in user. */
    fun update(objectPath: String, file: File, contentType: String): String {
        validateOwnedPath(objectPath, currentUserId())
        require(file.isFile && file.canRead()) { "الملف غير موجود أو لا يمكن قراءته." }
        require(file.length() in 1..MAX_FILE_BYTES) { "حجم الملف يجب ألا يتجاوز 10 MB وألا يكون فارغاً." }
        require(contentType in ALLOWED_TYPES) { "نوع الملف غير مسموح." }
        val request = storageRequest("object/$BUCKET/$objectPath")
            .header("Content-Type", contentType)
            .header("x-upsert", "true")
            .put(file.readBytes().toRequestBody(contentType.toMediaType()))
            .build()
        execute(request) { response ->
            require(response.isSuccessful) { errorMessage(response.code, response.body?.string().orEmpty()) }
            objectPath
        }
    }

    fun download(objectPath: String): ByteArray {
        validateOwnedPath(objectPath, currentUserId())
        val request = storageRequest("object/$BUCKET/$objectPath").get().build()
        return execute(request) { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            require(response.isSuccessful) {
                errorMessage(response.code, String(bytes, Charsets.UTF_8))
            }
            bytes
        }
    }

    fun list(limit: Int = 100): JSONArray {
        require(limit in 1..100) { "قيمة limit يجب أن تكون بين 1 و100." }
        val userId = currentUserId()
        val body = JSONObject()
            .put("prefix", "$userId/")
            .put("limit", limit)
            .put("offset", 0)
            .put("sortBy", JSONObject().put("column", "created_at").put("order", "desc"))
        val request = storageRequest("object/list/$BUCKET")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request) { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { errorMessage(response.code, raw) }
            JSONArray(raw)
        }
    }

    fun delete(objectPath: String) {
        validateOwnedPath(objectPath, currentUserId())
        val body = JSONArray().put(objectPath)
        val request = storageRequest("object/$BUCKET")
            .delete(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(request) { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { errorMessage(response.code, raw) }
            Unit
        }
    }

    private fun currentUserId(): String {
        val request = Request.Builder()
            .url("${BuildConfig.SADEEQI_SUPABASE_URL.trimEnd('/')}/auth/v1/user")
            .header("apikey", BuildConfig.SADEEQI_SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${auth.accessToken()}")
            .get()
            .build()
        return execute(request) { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { errorMessage(response.code, raw) }
            val id = JSONObject(raw).optString("id")
            require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "تعذر تحديد هوية المستخدم." }
            id
        }
    }

    private fun storageRequest(path: String): Request.Builder =
        Request.Builder()
            .url("${BuildConfig.SADEEQI_SUPABASE_URL.trimEnd('/')}/storage/v1/$path")
            .header("apikey", BuildConfig.SADEEQI_SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${auth.accessToken()}")

    private inline fun <T> execute(request: Request, block: (okhttp3.Response) -> T): T =
        http.newCall(request).execute().use(block)

    private fun validateOwnedPath(path: String, userId: String) {
        require(path.startsWith("$userId/") && path.length > userId.length + 1) {
            "لا يمكنك الوصول إلى ملف لا يخص حسابك."
        }
        require(!path.split('/').any { it == "." || it == ".." }) { "مسار الملف غير صالح." }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val detail = runCatching {
            val json = JSONObject(raw)
            json.optString("message").ifBlank { json.optString("error") }
        }.getOrNull().orEmpty()
        return when (code) {
            401, 403 -> "ليس لديك صلاحية للوصول إلى هذا الملف."
            404 -> "الملف غير موجود."
            413 -> "حجم الملف أكبر من الحد المسموح."
            else -> detail.ifBlank { "تعذر تنفيذ عملية التخزين (HTTP $code)." }
        }
    }

    companion object {
        const val BUCKET = "sadeeqi-private-files"
        const val MAX_FILE_BYTES = 10L * 1024L * 1024L
        val ALLOWED_TYPES = setOf(
            "image/jpeg", "image/png", "image/webp",
            "application/pdf", "text/plain"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
