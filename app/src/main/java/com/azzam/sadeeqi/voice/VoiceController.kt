package com.azzam.sadeeqi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Hands-free, turn-based Arabic voice conversation.
 * Android SpeechRecognizer performs STT and Android TTS speaks the AI response.
 * The microphone is never kept open while Sadeeqi is speaking.
 */
class VoiceController(
    context: Context,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var continuous = false
    private var microphoneMuted = false
    private var listening = false
    private var speaking = false

    init {
        tts = TextToSpeech(appContext, this)
    }

    /** Start a voice session that automatically listens again after each spoken answer. */
    fun startConversation() {
        continuous = true
        microphoneMuted = false
        stopSpeech()
        listen()
    }

    /** One-shot listening remains available for callers that do not want a session. */
    fun listen() {
        if (microphoneMuted) return
        stopSpeech()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("خدمة التعرف على الكلام غير متاحة على هذا الجهاز.")
            return
        }

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also {
                    it.setRecognitionListener(this)
                }
            } else {
                recognizer?.cancel()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
            listening = true
            recognizer?.startListening(intent)
        } catch (e: SecurityException) {
            listening = false
            onError("إذن الميكروفون مرفوض. اسمح لصديقي باستخدام الميكروفون من إعدادات الهاتف.")
        } catch (e: Exception) {
            listening = false
            onError("تعذر تشغيل الميكروفون: ${e.message ?: "خطأ غير معروف"}")
            retryListeningIfActive(1200)
        }
    }

    fun setMicrophoneMuted(muted: Boolean) {
        microphoneMuted = muted
        if (muted) {
            listening = false
            runCatching { recognizer?.cancel() }
        } else if (continuous && !speaking) {
            listen()
        }
    }

    /** Interrupt the current TTS output and immediately return to microphone capture. */
    fun interruptAndListen() {
        continuous = true
        microphoneMuted = false
        stopSpeech()
        listen()
    }

    fun stopConversation() {
        continuous = false
        microphoneMuted = false
        listening = false
        speaking = false
        runCatching { recognizer?.cancel() }
        stopSpeech()
    }

    /** Called if the network/AI request fails; keep an active voice session usable. */
    fun resumeAfterTurnFailure() {
        if (continuous && !microphoneMuted) retryListeningIfActive(700)
    }

    fun speak(text: String) {
        if (!ttsReady) {
            onError("خدمة النطق لم تجهز بعد. انتظر لحظة ثم حاول مجدداً.")
            resumeAfterTurnFailure()
            return
        }
        val spokenText = cleanForSpeech(text)
        if (spokenText.isBlank()) {
            resumeAfterTurnFailure()
            return
        }
        speaking = true
        listening = false
        runCatching { recognizer?.cancel() }
        val result = tts?.speak(
            spokenText,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "sadeeqi-${System.currentTimeMillis()}"
        )
        if (result == TextToSpeech.ERROR) {
            speaking = false
            onError("تعذر تشغيل الرد الصوتي على هذا الجهاز.")
            resumeAfterTurnFailure()
        }
    }

    private fun cleanForSpeech(value: String): String = value
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("https?://\\S+"), "رابط")
        .replace(Regex("[*_#>]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun stopSpeech() {
        runCatching { tts?.stop() }
        speaking = false
    }

    private fun retryListeningIfActive(delayMs: Long) {
        if (!continuous || microphoneMuted) return
        mainHandler.postDelayed({
            if (continuous && !microphoneMuted && !speaking && !listening) listen()
        }, delayMs)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts
            val preferred = engine?.setLanguage(Locale("ar"))
            ttsReady = preferred != TextToSpeech.LANG_MISSING_DATA &&
                preferred != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
                val fallback = engine?.setLanguage(Locale("ar", "SA"))
                ttsReady = fallback != TextToSpeech.LANG_MISSING_DATA &&
                    fallback != TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (!ttsReady) onError("لا توجد حزمة صوت عربي مثبتة. ثبّت صوتاً عربياً من إعدادات تحويل النص إلى كلام.")
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking = true
                }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        if (continuous && !microphoneMuted) listen()
                    }
                }
                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        speaking = false
                        onError("حدث خطأ أثناء تشغيل الصوت.")
                        resumeAfterTurnFailure()
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    mainHandler.post {
                        speaking = false
                        onError("تعذر إكمال تشغيل الرد الصوتي.")
                        resumeAfterTurnFailure()
                    }
                }
            })
        } else {
            ttsReady = false
            onError("تعذر تشغيل خدمة النطق.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { listening = false }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onError(error: Int) {
        listening = false
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "حدث خطأ في تسجيل الصوت."
            SpeechRecognizer.ERROR_CLIENT -> "تعذر تشغيل خدمة التعرف على الكلام."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "إذن الميكروفون غير متاح."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انقطع اتصال التعرف على الكلام. تحقق من الإنترنت."
            SpeechRecognizer.ERROR_NO_MATCH -> "لم أتمكن من فهم الكلام، حاول مرة أخرى."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة الصوت مشغولة، سأحاول مجدداً."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم أسمع كلاماً."
            else -> "حدث خطأ أثناء الاستماع."
        }
        onError(message)
        if (continuous && !microphoneMuted) {
            retryListeningIfActive(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 650)
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isNotBlank()) onText(text) else {
            onError("لم أتمكن من التعرف على الكلام.")
            retryListeningIfActive(500)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    fun close() {
        continuous = false
        microphoneMuted = true
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
