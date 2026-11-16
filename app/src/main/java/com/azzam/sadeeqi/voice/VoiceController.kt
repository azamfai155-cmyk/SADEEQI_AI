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
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var recognizerGeneration = 0L
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var conversationActive = false
    @Volatile private var dictationActive = false
    @Volatile private var listening = false
    @Volatile private var microphoneMuted = false
    @Volatile private var speaking = false
    private var lastUtteranceId: String? = null
    private val settingsStore = VoiceSettingsStore(appContext)

    init { tts = TextToSpeech(appContext, this) }

    fun isConversationActive(): Boolean = conversationActive

    fun startConversation() {
        microphoneMuted = false
        dictationActive = false
        conversationActive = true
        runCatching { tts?.stop() }
        speaking = false
        startListeningInternal()
    }

    fun stopConversation() {
        conversationActive = false
        dictationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        resetRecognizer()
        listening = false
        runCatching { tts?.stop() }
        speaking = false
    }

    fun interruptAndListen() {
        conversationActive = true
        dictationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        speaking = false
        resetRecognizer()
        listening = false
        startListeningInternal()
    }

    fun listen() {
        microphoneMuted = false
        conversationActive = false
        dictationActive = true
        startListeningInternal()
    }

    fun setMicrophoneMuted(muted: Boolean) {
        microphoneMuted = muted
        if (muted) {
            resetRecognizer()
            listening = false
        } else if ((conversationActive || dictationActive) && !speaking) {
            resetRecognizer()
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
                val generation = ++recognizerGeneration
                recognizer?.setRecognitionListener(createRecognitionListener(generation))
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

    private fun createRecognitionListener(generation: Long): RecognitionListener = object : RecognitionListener {
        private fun deliver(action: () -> Unit) {
            if (generation == recognizerGeneration) action()
        }
        override fun onReadyForSpeech(params: Bundle?) = deliver { listening = true }
        override fun onBeginningOfSpeech() = deliver { }
        override fun onRmsChanged(rmsdB: Float) = deliver { }
        override fun onBufferReceived(buffer: ByteArray?) = deliver { }
        override fun onEndOfSpeech() = deliver { listening = false }
        override fun onEvent(eventType: Int, params: Bundle?) = deliver { }
        override fun onError(error: Int) = deliver { handleRecognitionError(error) }
        override fun onResults(results: Bundle?) = deliver { handleRecognitionResults(results) }
        override fun onPartialResults(partialResults: Bundle?) = deliver { }
    }

    private fun handleRecognitionError(error: Int) {
        // Cancellation can race with callbacks from the platform recognizer.
        // Never surface errors or schedule a retry while the microphone is muted.
        if (microphoneMuted || (!conversationActive && !dictationActive)) {
            listening = false
            return
        }
        listening = false
        val wasDictation = dictationActive
        dictationActive = false
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
        if (!wasDictation && conversationActive && !microphoneMuted && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            scheduleListening(if (error == SpeechRecognizer.ERROR_NETWORK ||
                error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) 1400 else 500)
        }
    }

    private fun handleRecognitionResults(results: Bundle?) {
        // A final result may arrive after SpeechRecognizer.cancel() during mute/stop.
        if (microphoneMuted || (!conversationActive && !dictationActive)) {
            listening = false
            return
        }
        listening = false
        dictationActive = false
        val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (recognized.isNotBlank()) {
            onText(recognized)
        } else if (conversationActive) {
            scheduleListening(400)
        } else {
            onError("لم أتمكن من التعرف على الكلام.")
        }
    }

    private fun resetRecognizer() {
        recognizerGeneration += 1
        val oldRecognizer = recognizer
        recognizer = null
        runCatching { oldRecognizer?.cancel() }
        runCatching { oldRecognizer?.destroy() }
    }

    fun close() {
        conversationActive = false
        dictationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        mainHandler.removeCallbacksAndMessages(null)
        resetRecognizer()
        runCatching { tts?.stop() }
        speaking = false
        tts?.shutdown()
        tts = null
        ttsReady = false
        listening = false
    }
}
