package com.azzam.sadeeqi.phone

import android.provider.AlarmClock
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

/** Executes only allow-listed Android actions through public platform APIs. */
class PhoneControl(private val context: Context) {
    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openCamera() {
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(intent.resolveActivity(context.packageManager) != null) {
            "لا يوجد تطبيق كاميرا متاح على الهاتف."
        }
        context.startActivity(intent)
    }

    fun adjustMusicVolume(raise: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    /** Opens the system clock's alarm editor; the user confirms in the clock app. */
    fun createAlarm(hour: Int, minute: Int, label: String) {
        require(hour in 0..23 && minute in 0..59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        check(intent.resolveActivity(context.packageManager) != null) {
            "لا يوجد تطبيق ساعة يدعم إنشاء المنبه."
        }
        context.startActivity(intent)
    }
}
