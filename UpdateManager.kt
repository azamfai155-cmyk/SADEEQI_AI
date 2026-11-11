package com.azzam.sadeeqi.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

 data class ReleaseInfo(val versionName:String,val versionCode:Long,val notes:String,val apkUrl:String,val expectedSignerSha256:String)
/** GitHub Releases-compatible JSON endpoint. Configure an owned official repository endpoint; no endpoint is hard-coded. */
class UpdateManager(private val context:Context,private val releasesApi:String){
 private val http=OkHttpClient()
 fun check():ReleaseInfo?{require(releasesApi.startsWith("https://"));val req=Request.Builder().url(releasesApi).header("Accept","application/vnd.github+json").build();http.newCall(req).execute().use{r->check(r.isSuccessful){"Update server HTTP ${r.code}"};val j=JSONObject(r.body!!.string());val assets=j.getJSONArray("assets");var apk="";for(i in 0 until assets.length()){val a=assets.getJSONObject(i);if(a.getString("name").endsWith(".apk",true)){apk=a.getString("browser_download_url");break}};if(apk.isBlank())return null;val tag=j.optString("tag_name").removePrefix("v");val notes=j.optString("body");val code=tag.substringBefore('.').toLongOrNull()?.times(10000)?.plus(tag.substringAfter('.',"0").substringBefore('.').toLongOrNull()?:0)?:0
 return ReleaseInfo(tag,code,notes,apk,j.optString("sadeeqi_signer_sha256"))}}
 fun download(release:ReleaseInfo):Long{require(release.apkUrl.startsWith("https://"));val req=DownloadManager.Request(Uri.parse(release.apkUrl)).setTitle("صديقي ${release.versionName}").setDescription("تنزيل تحديث صديقي").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"sadeeqi-${release.versionName}.apk");return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)}
 fun installedSignerSha256():String{val info=if(Build.VERSION.SDK_INT>=28)context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo!!.apkContentsSigners else @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES).signatures!![0].let{arrayOf(it)};val cert=if(info is Array<*>)info[0] as android.content.pm.Signature else info as android.content.pm.Signature;return MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString(""){"%02X".format(it)} }
 fun verifyDownloadedApk(apkPath:String,expectedSigner:String):Boolean {
  if(expectedSigner.isBlank()) return false
  @Suppress("DEPRECATION") val info=context.packageManager.getPackageArchiveInfo(apkPath,PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES) ?: return false
  val signatures=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") info.signatures
  val cert=signatures?.firstOrNull() ?: return false
  val actual=MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString(""){"%02X".format(it)}
  return actual.equals(expectedSigner.replace(":",""),true) && info.packageName==context.packageName
 }
 fun requestInstaller(uri:Uri){val i=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(i)}
}
