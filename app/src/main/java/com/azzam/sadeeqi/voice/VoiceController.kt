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

/** Arabic speech input and TTS, alternating listen -> AI reply -> speak -> listen. */
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
    @Volatile private var conversationActive = false
    @Volatile private var listening = false
    @Volatile private var microphoneMuted = false
    @Volatile private var speaking = false
    @Volatile private var lastUtteranceId: String? = null
    private val settingsStore = VoiceSettingsStore(appContext)

    init { tts = TextToSpeech(appContext, this) }

    fun isConversationActive(): Boolean = conversationActive

    fun startConversation() {
        microphoneMuted = false
        conversationActive = true
        runCatching { tts?.stop() }
        speaking = false
        startListeningInternal()
    }

    fun stopConversation() {
        conversationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        listening = false
        runCatching { tts?.stop() }
        speaking = false
    }

    fun interruptAndListen() {
        conversationActive = true
        microphoneMuted = false
        lastUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        speaking = false
        runCatching { recognizer?.cancel() }
        listening = false
        startListeningInternal()
    }

    fun listen() {
        microphoneMuted = false
        conversationActive = false
        startListeningInternal()
    }

    fun setMicrophoneMuted(muted: Boolean) {
        microphoneMuted = muted
        if (muted) {
            runCatching { recognizer?.cancel() }
            listening = false
        } else if (conversationActive && !speaking) {
            startListeningInternal()
        }
    }

    fun resumeAfterTurnFailure() {
        if (conversationActive && !microphoneMuted) scheduleListening(400)
    }

    private fun startListeningInternal() {
        if (microphoneMuted || speaking) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("خدمة التعرف على الكلام غير متاحة على هذا الجهاز.")
            return
        }
        if (listening) return
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer?.setRecognitionListener(this)
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
            listening = true
            recognizer?.startListening(intent)
        } catch (e: SecurityException) {
            listening = false
            onError("إذن الميكروفون مرفوض. اسمح لصديقي باستخدام الميكروفون من إعدادات التطبيق.")
        } catch (e: Exception) {
            listening = false
            onError("تعذر تشغيل الميكروفون: " + (e.message ?: "خطأ غير معروف"))
        }
    }

    fun speak(text: String) {
        if (!ttsReady) {
            onError("محرك النطق لم يجهز بعد. تحقق من تثبيت صوت عربي في إعدادات الهاتف.")
            if (conversationActive) scheduleListening(900)
            return
        }
        val cleanText = text
            .replace(Regex("https?://\\S+"), "رابط")
            .replace(Regex("[*_#>\\[\\]{}]"), " ")
            .replace(Regex("[\\t\\r\\n]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (cleanText.isBlank()) {
            if (conversationActive) scheduleListening(250)
            return
        }
        val settings = settingsStore.load()
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }
        val utteranceId = "sadeeqi-" + System.currentTimeMillis()
        lastUtteranceId = utteranceId
        speaking = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val settings = settingsStore.load()
            val locale = Locale.forLanguageTag(settings.languageTag)
            val result = tts?.setLanguage(locale)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            val selectedVoice = tts?.voices?.firstOrNull { it.name == settings.voiceName }
            if (selectedVoice != null) tts?.voice = selectedVoice
            tts?.setSpeechRate(settings.speechRate)
            tts?.setPitch(settings.pitch)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == lastUtteranceId) speaking = true
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == lastUtteranceId) {
                        speaking = false
                        if (conversationActive && !microphoneMuted) scheduleListening(250)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == lastUtteranceId) {
                        speaking = false
                        if (conversationActive && !microphoneMuted) scheduleListening(700)
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == lastUtteranceId) {
                        speaking = false
                        if (conversationActive && !microphoneMuted) scheduleListening(700)
                    }
                }
            })
            if (!ttsReady) onError("الصوت العربي غير متوفر. ثبّت بيانات صوت عربي من إعدادات تحويل النص إلى كلام.")
        } else {
            onError("تعذر تشغيل خدمة النطق في الهاتف.")
        }
    }

    private fun scheduleListening(delayMs: Long) {
        if (!conversationActive || microphoneMuted) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (conversationActive && !microphoneMuted && !speaking) {
                listening = false
                startListeningInternal()
            }
        }, delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) { listening = true }
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
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "انقطع اتصال خدمة التعرف على الكلام. تحقق من الإنترنت."
            SpeechRecognizer.ERROR_NO_MATCH -> "لم أتمكن من فهم الكلام، حاول مرة أخرى."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة الصوت مشغولة، سأحاول مجدداً."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم أسمع كلاماً، سأستمع مجدداً."
            else -> "حدث خطأ أثناء الاستماع."
        }
        onError(message)
        if (conversationActive && !microphoneMuted && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            scheduleListening(if (error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) 1400 else 500)
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (recognized.isNotBlank()) {
            onText(recognized)
        } else if (conversationActive) {
            scheduleListening(400)
        } else {
            onError("لم أتمكن من التعرف على الكلام.")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    fun close() {
        conversationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        recognizer?.destroy()
        recognizer = null
        runCatching { tts?.stop() }
        speaking = false
        tts?.shutdown()
        tts = null
        ttsReady = false
        listening = false
    }
}
