package com.azzam.sadeeqi.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Environment
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
 * Preliminary GitHub Releases client.
 *
 * This class is intentionally not wired into the app UI: a trusted release
 * endpoint and signing-certificate fingerprint must be configured before
 * enabling an automatic update flow.
 */
class UpdateManager(
    private val context: Context,
    private val releasesApi: String
) {
    private val http = OkHttpClient()

    fun check(): ReleaseInfo? {
        require(releasesApi.startsWith("https://")) { "Release API must use HTTPS" }
        val request = Request.Builder()
            .url(releasesApi)
            .header("Accept", "application/vnd.github+json")
            .build()

        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Update server HTTP ${response.code}" }
            val json = JSONObject(response.body?.string() ?: error("Empty release response"))
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null

            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    if (apkUrl != null) break
                }
            }

            val url = apkUrl ?: return null
            val tag = json.optString("tag_name").removePrefix("v")
            val versionParts = tag.split(".").map { it.toLongOrNull() ?: 0L }
            val versionCode = (versionParts.getOrNull(0) ?: 0L) * 1_000_000L +
                (versionParts.getOrNull(1) ?: 0L) * 1_000L +
                (versionParts.getOrNull(2) ?: 0L)

            return ReleaseInfo(
                versionName = tag,
                versionCode = versionCode,
                notes = json.optString("body"),
                apkUrl = url,
                expectedSignerSha256 = json.optString("sadeeqi_signer_sha256")
            )
        }
    }

    fun download(release: ReleaseInfo): Long {
        require(release.apkUrl.startsWith("https://")) { "APK URL must use HTTPS" }
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("صديقي ${release.versionName}")
            .setDescription("تنزيل تحديث صديقي")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "sadeeqi-${release.versionName}.apk"
            )

        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
            .enqueue(request)
    }

    fun installedSignerSha256(): String {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, flags)
        val certificate = signingCertificates(packageInfo)?.firstOrNull()
            ?: error("Installed app signing certificate is unavailable")
        return certificate.sha256()
    }

    fun verifyDownloadedApk(apkPath: String, expectedSigner: String): Boolean {
        if (expectedSigner.isBlank()) return false
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(apkPath, flags) ?: return false
        val certificate = signingCertificates(packageInfo)?.firstOrNull() ?: return false
        val expected = expectedSigner.replace(":", "").replace(" ", "")
        return certificate.sha256().equals(expected, ignoreCase = true) &&
            packageInfo.packageName == context.packageName
    }

    /**
     * The supplied URI must be a readable content:// URI (for example, from a
     * FileProvider). A raw file:// URI is not accepted by modern Android.
     */
    fun requestInstaller(uri: Uri) {
        require(uri.scheme == "content") { "Installer URI must use content://" }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    private fun signingCertificates(info: PackageInfo): Array<Signature>? {
        return if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            legacySignatures(info)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySignatures(info: PackageInfo): Array<Signature>? = info.signatures

    private fun Signature.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { "%02X".format(it) }
}
