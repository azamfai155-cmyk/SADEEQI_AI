package com.azzam.sadeeqi.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.azzam.sadeeqi.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

data class ReleaseInfo(
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val apkUrl: String,
    val expectedSignerSha256: String
)

/**
 * Strict GitHub Releases-compatible updater.
 * Release notes must include:
 * versionCode: 8
 * signerSha256: 64 hex characters (SHA-256 of APK signing certificate)
 */
class UpdateManager(private val context: Context, private val releasesApi: String) {
    private val http = OkHttpClient()

    fun check(): ReleaseInfo? {
        require(releasesApi.startsWith("https://")) { "يجب استخدام HTTPS لخادم التحديث." }
        val request = Request.Builder().url(releasesApi)
            .header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Update server HTTP ${response.code}" }
            val release = JSONObject(response.body?.string().orEmpty())
            val notes = release.optString("body")
            val versionName = release.optString("tag_name").removePrefix("v")
            require(versionName.isNotBlank()) { "اسم إصدار التحديث غير صالح." }
            val versionCode = Regex("(?im)^\\s*versionCode\\s*:\\s*(\\d+)\\s*$")
                .find(notes)?.groupValues?.get(1)?.toLongOrNull()
                ?: throw IllegalStateException("بيانات الإصدار لا تحتوي versionCode صحيحاً.")
            val signer = Regex("(?im)^\\s*signerSha256\\s*:\\s*([A-Fa-f0-9:]{64,95})\\s*$")
                .find(notes)?.groupValues?.get(1)?.replace(":", "")?.uppercase()
                ?: throw IllegalStateException("بصمة توقيع APK غير موجودة في بيانات الإصدار.")
            require(signer.matches(Regex("[A-F0-9]{64}"))) { "بصمة توقيع APK غير صالحة." }

            val assets = release.optJSONArray("assets") ?: return null
            val apkUrl = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url").orEmpty()
            if (apkUrl.isBlank()) return null
            require(apkUrl.startsWith("https://")) { "رابط APK غير آمن." }

            if (versionCode <= BuildConfig.VERSION_CODE.toLong()) return null
            return ReleaseInfo(versionName, versionCode, notes, apkUrl, signer)
        }
    }

    fun download(release: ReleaseInfo): Long {
        require(release.apkUrl.startsWith("https://"))
        require(release.versionCode > BuildConfig.VERSION_CODE.toLong()) {
            "لا يمكن تنزيل إصدار أقدم أو مساوٍ للإصدار المثبت."
        }
        require(release.expectedSignerSha256.matches(Regex("[A-Fa-f0-9]{64}"))) {
            "لا يمكن تنزيل تحديث دون بصمة توقيع موثوقة."
        }
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("صديقي ${release.versionName}")
            .setDescription("تنزيل تحديث صديقي")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, "sadeeqi-${release.versionName}.apk"
            )
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun installedSignerSha256(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signature = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION") packageInfo.signatures?.firstOrNull()
        } ?: error("تعذر قراءة شهادة توقيع التطبيق.")
        return sha256(signature.toByteArray())
    }

    fun verifyDownloadedApk(apkPath: String, expectedSigner: String): Boolean {
        if (!expectedSigner.matches(Regex("[A-Fa-f0-9]{64}"))) return false
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(
            apkPath, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        ) ?: return false
        if (info.packageName != context.packageName) return false
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        val certificate = signatures?.firstOrNull() ?: return false
        return sha256(certificate.toByteArray()).equals(expectedSigner, ignoreCase = true)
    }

    /** Caller must invoke this only after verifyDownloadedApk returned true. */
    fun requestInstaller(uri: Uri, signatureVerified: Boolean) {
        require(signatureVerified) { "تم منع فتح مثبت الحزم: لم يتم التحقق من توقيع APK." }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
}
