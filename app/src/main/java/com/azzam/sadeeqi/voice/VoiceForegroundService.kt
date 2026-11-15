package com.azzam.sadeeqi.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.azzam.sadeeqi.MainActivity
import com.azzam.sadeeqi.R
import com.azzam.sadeeqi.core.OpenAiGateway
import com.azzam.sadeeqi.auth.SupabaseAuthClient
import com.azzam.sadeeqi.music.MusicCommand
import com.azzam.sadeeqi.music.MusicCommandParser
import com.azzam.sadeeqi.phone.DeviceCommand
import com.azzam.sadeeqi.phone.DeviceCommandParser
import com.azzam.sadeeqi.phone.PhoneControl
import com.azzam.sadeeqi.music.MusicPlayerManager
import com.azzam.sadeeqi.music.AudiusMusicRepository
import java.util.concurrent.Executors

/**
 * User-started, visible microphone session. The persistent notification provides
 * an immediate stop action; this service never starts listening on its own.
 */
class VoiceForegroundService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val gateway by lazy { OpenAiGateway { SupabaseAuthClient(this@VoiceForegroundService).accessToken() } }
    private val history = mutableListOf<OpenAiGateway.Turn>()
    private val historyLock = Any()
    private val turnGate = VoiceTurnGate()
    private var voice: VoiceController? = null
    private var musicManager: MusicPlayerManager? = null
    @Volatile private var started = false
    @Volatile private var microphoneMuted = false
    private var pendingPhoneCommand: DeviceCommand? = null
    private val phoneControl by lazy(LazyThreadSafetyMode.NONE) { PhoneControl(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
            ACTION_INTERRUPT -> {
                if (!started) {
                    startSession()
                } else {
                    invalidateCurrentTurn()
                    voice?.interruptAndListen()
                }
            }
            ACTION_MUTE -> {
                val muted = intent.getBooleanExtra(EXTRA_MUTED, !microphoneMuted)
                microphoneMuted = muted
                voice?.setMicrophoneMuted(muted)
                updateNotification(if (muted) "الميكروفون مكتوم" else "الميكروفون نشط")
            }
            ACTION_DICTATE -> startDictationSession()
            ACTION_START, null -> startSession()
        }
        return START_NOT_STICKY
    }

    private fun startDictationSession() {
        if (started) stopSession()
        invalidateCurrentTurn()
        createNotificationChannel()
        val notification = buildNotification("استمع صديقي إلى رسالتك الصوتية")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            stopSelf()
            return
        }
        started = true
        microphoneMuted = false
        synchronized(historyLock) { history.clear() }
        if (musicManager == null) musicManager = MusicPlayerManager(this) { }
        voice = VoiceController(
            applicationContext,
            { spoken -> handleRecognizedSpeech(spoken) },
            { message -> updateNotification(message) }
        )
        voice?.listen()
    }

    private fun startSession() {
        if (started) {
            invalidateCurrentTurn()
            voice?.interruptAndListen()
            return
        }
        invalidateCurrentTurn()
        createNotificationChannel()
        val notification = buildNotification("جلسة الاستماع نشطة — اضغط إيقاف لإنهائها")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            stopSelf()
            return
        }

        started = true
        microphoneMuted = false
        synchronized(historyLock) { history.clear() }
        if (musicManager == null) musicManager = MusicPlayerManager(this) { }
        voice = VoiceController(
            applicationContext,
            { spoken -> handleRecognizedSpeech(spoken) },
            { message -> updateNotification(message) }
        )
        voice?.startConversation()
    }

    private fun normalizeVoiceConfirmation(value: String): String = value.trim().lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .replace(Regex("[!！؟?،,.]+"), " ")
        .replace(Regex("\\s+"), " ")

    private fun executeVoicePhoneCommand(command: DeviceCommand): String {
        when (command) {
            is DeviceCommand.LaunchApp -> check(phoneControl.launchApp(command.packageName)) {
                "تطبيق ${command.appName} غير مثبت أو لا يمكن فتحه."
            }
            DeviceCommand.OpenCamera -> phoneControl.openCamera()
            DeviceCommand.OpenSettings -> phoneControl.openSettings()
            DeviceCommand.VolumeUp -> phoneControl.adjustMusicVolume(true)
            DeviceCommand.VolumeDown -> phoneControl.adjustMusicVolume(false)
            is DeviceCommand.SetAlarm -> phoneControl.createAlarm(command.hour, command.minute, "منبه صديقي")
        }
        return when (command) {
            is DeviceCommand.LaunchApp -> "تم فتح تطبيق ${command.appName}."
            DeviceCommand.OpenCamera -> "تم فتح الكاميرا."
            DeviceCommand.OpenSettings -> "تم فتح إعدادات الهاتف."
            DeviceCommand.VolumeUp -> "تم رفع مستوى صوت الوسائط."
            DeviceCommand.VolumeDown -> "تم خفض مستوى صوت الوسائط."
            is DeviceCommand.SetAlarm -> "فُتح تطبيق الساعة. أكمل حفظ المنبه هناك."
        }
    }

    private fun handleRecognizedSpeech(spoken: String) {
        if (!started || spoken.isBlank()) return
        val requestId = turnGate.begin()
        runCatching { gateway.cancelCurrentRequest() }

        // Voice phone actions require a separate spoken confirmation before execution.
        val pending = pendingPhoneCommand
        if (pending != null) {
            val confirmation = normalizeVoiceConfirmation(spoken)
            if (confirmation in setOf("نعم", "ايوه", "موافق", "اكد", "نفذ", "نفذ الامر", "تمام")) {
                pendingPhoneCommand = null
                val result = runCatching { executeVoicePhoneCommand(pending) }
                val reply = result.getOrElse { error ->
                    "تعذر تنفيذ الأمر: ${error.message ?: "تحقق من إعدادات الهاتف."}"
                }
                updateNotification(reply)
                voice?.speak(reply)
            } else if (confirmation in setOf("لا", "الغاء", "الغاء الامر", "تراجع", "مش موافق")) {
                pendingPhoneCommand = null
                updateNotification("تم إلغاء أمر الهاتف.")
                voice?.speak("تم إلغاء أمر الهاتف.")
            } else {
                voice?.speak("لم أفهم التأكيد. قل نعم للتنفيذ أو لا للإلغاء.")
            }
            return
        }

        val phoneCommand = DeviceCommandParser.parse(spoken)
        if (phoneCommand != null) {
            pendingPhoneCommand = phoneCommand
            val prompt = DeviceCommandParser.confirmationText(phoneCommand) + " قل نعم للتأكيد أو لا للإلغاء."
            updateNotification(prompt)
            voice?.speak(prompt)
            return
        }

        when (val command = MusicCommandParser.parse(spoken)) {
            MusicCommand.Pause -> { musicManager?.pause(); voice?.speak("تم إيقاف الموسيقى مؤقتاً"); return }
            MusicCommand.Resume -> { musicManager?.resume(); voice?.speak("تم استئناف الموسيقى"); return }
            MusicCommand.Next -> { musicManager?.next(); voice?.speak("انتقلت إلى المقطع التالي"); return }
            MusicCommand.Previous -> { musicManager?.previous(); voice?.speak("عدت إلى المقطع السابق"); return }
            MusicCommand.VolumeUp -> { musicManager?.adjustVolume(0.1f); voice?.speak("تم رفع الصوت"); return }
            MusicCommand.VolumeDown -> { musicManager?.adjustVolume(-0.1f); voice?.speak("تم خفض الصوت"); return }
            is MusicCommand.Search -> {
                updateNotification("جاري البحث عن ${command.query} في مكتبة الموسيقى…")
                executor.execute {
                    try {
                        val tracks = AudiusMusicRepository().search(command.query)
                        mainHandler.post {
                            if (!started || !turnGate.isCurrent(requestId)) return@post
                            if (tracks.isEmpty()) {
                                voice?.speak("لم أجد نتائج قابلة للبث. جرّب اسم فنان أو أغنية أخرى.")
                            } else {
                                musicManager?.playRemoteQueue(tracks, 0)
                                updateNotification("يتم تشغيل ${tracks.first().title} من Audius")
                                voice?.speak("وجدت ${tracks.size} مقطعاً. بدأت تشغيل ${tracks.first().title} للفنان ${tracks.first().artist}")
                            }
                        }
                    } catch (error: Exception) {
                        mainHandler.post {
                            if (started && turnGate.isCurrent(requestId)) {
                                updateNotification("تعذر البحث عن الموسيقى")
                                voice?.speak("تعذر البحث عن الموسيقى. تحقق من اتصال الإنترنت.")
                            }
                        }
                    }
                }
                return
            }
            null -> Unit
        }
        val normalized = spoken.trim().replace(Regex("[!！؟?،,.\\s]+"), "")
        if (normalized in setOf("ياصديقي", "صديقي")) {
            val greeting = "نعم يا عزام، أنا معك"
            voice?.speak(greeting)
            return
        }

        val snapshot = synchronized(historyLock) {
            history.add(OpenAiGateway.Turn("user", spoken))
            history.takeLast(24).toList()
        }
        updateNotification("صديقي يعالج كلامك…")
        executor.execute {
            if (!started || !turnGate.isCurrent(requestId)) return@execute
            try {
                val answer = gateway.reply(snapshot)
                val accepted = synchronized(historyLock) {
                    if (started && turnGate.isCurrent(requestId)) {
                        history.add(OpenAiGateway.Turn("assistant", answer))
                        true
                    } else false
                }
                if (accepted) mainHandler.post {
                    if (started && turnGate.isCurrent(requestId)) {
                        updateNotification("صديقي يرد عليك — الميكروفون نشط")
                        voice?.speak(answer)
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (started && turnGate.isCurrent(requestId)) {
                        updateNotification("تعذر الرد: ${error.message ?: "خطأ اتصال"}")
                        voice?.resumeAfterTurnFailure()
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "جلسة صديقي الصوتية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "يوضح متى تكون جلسة الميكروفون النشطة قيد التشغيل"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 11,
            Intent(this, VoiceForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteIntent = PendingIntent.getService(
            this, 12,
            Intent(this, VoiceForegroundService::class.java)
                .setAction(ACTION_MUTE)
                .putExtra(EXTRA_MUTED, !microphoneMuted),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("صديقي — الاستماع الصوتي")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                if (microphoneMuted) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (microphoneMuted) "تشغيل الميكروفون" else "كتم الميكروفون",
                muteIntent
            )
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الاستماع", stopIntent)
            .build()
    }

    private fun invalidateCurrentTurn() {
        turnGate.invalidate()
        runCatching { gateway.cancelCurrentRequest() }
    }

    private fun updateNotification(message: String) {
        if (!started) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun stopSession() {
        invalidateCurrentTurn()
        started = false
        microphoneMuted = false
        synchronized(historyLock) { history.clear() }
        pendingPhoneCommand = null
        voice?.stopConversation()
        voice?.close()
        voice = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        invalidateCurrentTurn()
        started = false
        microphoneMuted = false
        synchronized(historyLock) { history.clear() }
        pendingPhoneCommand = null
        voice?.close()
        voice = null
        musicManager?.release()
        musicManager = null
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.azzam.sadeeqi.voice.START"
        const val ACTION_DICTATE = "com.azzam.sadeeqi.voice.DICTATE"
        const val ACTION_STOP = "com.azzam.sadeeqi.voice.STOP"
        const val ACTION_INTERRUPT = "com.azzam.sadeeqi.voice.INTERRUPT"
        const val ACTION_MUTE = "com.azzam.sadeeqi.voice.MUTE"
        const val EXTRA_MUTED = "microphone_muted"
        private const val CHANNEL_ID = "sadeeqi_voice_session"
        private const val NOTIFICATION_ID = 2407
    }
}
