package com.azzam.sadeeqi.phone

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/** Executes only parser-approved Android intents. Calls/SMS are handed to the system UI, never sent silently. */
object PhoneCommandExecutor {
    fun execute(context: Context, command: PhoneCommand): String {
        val app = context.applicationContext
        return when (command) {
            is PhoneCommand.OpenApp -> {
                val intent = app.packageManager.getLaunchIntentForPackage(command.packageName)
                    ?: return "لم أجد تطبيق ${command.appName} مثبتاً على الهاتف."
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                "أفتح ${command.appName}."
            }
            is PhoneCommand.OpenSettings -> {
                app.startActivity(Intent(command.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "فتحت ${command.label}. أكمل التغيير من إعدادات الهاتف."
            }
            PhoneCommand.Camera -> {
                app.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "فتحت الكاميرا."
            }
            PhoneCommand.Files -> {
                app.startActivity(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "فتحت نافذة اختيار الملفات."
            }
            PhoneCommand.VolumeUp -> {
                val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                "رفعت صوت الوسائط."
            }
            PhoneCommand.VolumeDown -> {
                val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                "خفضت صوت الوسائط."
            }
            is PhoneCommand.SetAlarm -> {
                app.startActivity(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, command.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "جهزت المنبه الساعة ${command.hour} و${command.minute} دقيقة. راجع شاشة الساعة واضغط حفظ."
            }
            is PhoneCommand.Dial -> {
                app.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(command.number)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "فتحت تطبيق الاتصال بالرقم ${command.number}. اضغط اتصال بنفسك لإجراء المكالمة."
            }
            is PhoneCommand.ComposeSms -> {
                app.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(command.number)}"))
                        .putExtra("sms_body", command.body)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "جهزت الرسالة. راجع النص والمستلم واضغط إرسال بنفسك."
            }
        }
    }
}
