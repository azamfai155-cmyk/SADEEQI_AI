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
import java.util.concurrent.Executors

/**
 * User-started, visible microphone session. The persistent notification provides
 * an immediate stop action; this service never starts listening on its own.
 */
class VoiceForegroundService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val gateway by lazy { OpenAiGateway() }
    private val history = mutableListOf<OpenAiGateway.Turn>()
    private var voice: VoiceController? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
            ACTION_INTERRUPT -> {
                if (!started) startSession()
                voice?.interruptAndListen()
            }
            ACTION_START, null -> startSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        if (started) {
            voice?.interruptAndListen()
            return
        }
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
        history.clear()
        voice = VoiceController(
            applicationContext,
            { spoken -> handleRecognizedSpeech(spoken) },
            { message -> updateNotification(message) }
        )
        voice?.startConversation()
    }

    private fun handleRecognizedSpeech(spoken: String) {
        if (!started || spoken.isBlank()) return
        val normalized = spoken.trim().replace(Regex("[!！؟?،,.\\s]+"), "")
        if (normalized in setOf("ياصديقي", "صديقي")) {
            val greeting = "نعم يا عزام، أنا معك"
            voice?.speak(greeting)
            return
        }

        history.add(OpenAiGateway.Turn("user", spoken))
        updateNotification("صديقي يعالج كلامك…")
        val snapshot = history.takeLast(24).toList()
        executor.execute {
            try {
                val answer = gateway.reply(snapshot)
                history.add(OpenAiGateway.Turn("assistant", answer))
                mainHandler.post {
                    if (started) {
                        updateNotification("صديقي يرد عليك — الميكروفون نشط")
                        voice?.speak(answer)
                    }
                }
            } catch (error: Exception) {
                mainHandler.post {
                    if (started) {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("صديقي — الاستماع الصوتي")
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الاستماع", stopIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        if (!started) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun stopSession() {
        started = false
        voice?.stopConversation()
        voice?.close()
        voice = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {\n            @Suppress("DEPRECATION")\n            stopForeground(true)\n        }
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        voice?.close()
        voice = null
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.azzam.sadeeqi.voice.START"
        const val ACTION_STOP = "com.azzam.sadeeqi.voice.STOP"
        const val ACTION_INTERRUPT = "com.azzam.sadeeqi.voice.INTERRUPT"\n        const val ACTION_MUTE = "com.azzam.sadeeqi.voice.MUTE"\n        const val EXTRA_MUTED = "microphone_muted"
        private const val CHANNEL_ID = "sadeeqi_voice_session"
        private const val NOTIFICATION_ID = 2407
    }
}
