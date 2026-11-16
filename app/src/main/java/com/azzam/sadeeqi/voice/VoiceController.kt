package com.azzam.sadeeqi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private val onError: (String) -> Unit,
    private val remoteSpeech: OpenAiRealtimeSpeech? = null
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduledListening: Runnable? = null
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var hasAudioFocus = false
    private val speechAudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    ).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    ).setOnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS) {
            mainHandler.post {
                if (speaking) {
                    // A call or another app took audio focus. Stop this utterance cleanly
                    // instead of talking over the higher-priority audio session.
                    lastUtteranceId = null
                    pendingSpeech = null
                    remoteSpeech?.cancel()
                    runCatching { tts?.stop() }
                    speaking = false
                    abandonSpeechAudioFocus()
                    if (conversationActive && !microphoneMuted) scheduleListening(350)
                }
            }
        }
    }.build()
    @Volatile private var recognizerGeneration = 0L
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile private var closed = false
    @Volatile private var conversationActive = false
    @Volatile private var dictationActive = false
    @Volatile private var listening = false
    @Volatile private var microphoneMuted = false
    @Volatile private var speaking = false
    private var lastUtteranceId: String? = null
    private val settingsStore = VoiceSettingsStore(appContext)
    private data class PendingSpeech(
        val text: String,
        val settings: VoiceSettings,
        val utteranceId: String
    )
    private var pendingSpeech: PendingSpeech? = null

    init { tts = TextToSpeech(appContext, this) }

    fun isConversationActive(): Boolean = conversationActive

    fun startConversation() {
        if (closed) return
        lastUtteranceId = null
        pendingSpeech = null
        microphoneMuted = false
        dictationActive = false
        conversationActive = true
        remoteSpeech?.cancel()
        runCatching { tts?.stop() }
        speaking = false
        abandonSpeechAudioFocus()
        startListeningInternal()
    }

    fun stopConversation() {
        if (closed) return
        conversationActive = false
        dictationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        pendingSpeech = null
        cancelScheduledListening()
        mainHandler.removeCallbacksAndMessages(null)
        resetRecognizer()
        listening = false
        remoteSpeech?.cancel()
        runCatching { tts?.stop() }
        speaking = false
        abandonSpeechAudioFocus()
    }

    fun interruptAndListen() {
        if (closed) return
        conversationActive = true
        dictationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        pendingSpeech = null
        mainHandler.removeCallbacksAndMessages(null)
        remoteSpeech?.cancel()
        runCatching { tts?.stop() }
        speaking = false
        abandonSpeechAudioFocus()
        resetRecognizer()
        listening = false
        startListeningInternal()
    }

    fun listen() {
        if (closed) return
        microphoneMuted = false
        conversationActive = false
        dictationActive = true
        startListeningInternal()
    }

    fun setMicrophoneMuted(muted: Boolean) {
        if (closed) return
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
        if (!closed && conversationActive && !microphoneMuted) scheduleListening(400)
    }

    private fun startListeningInternal() {
        if (closed || microphoneMuted || speaking) return
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
                val recognitionLanguage = settingsStore.load().recognitionLanguageTag
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguage)
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
        if (closed) return
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
        if (!requestSpeechAudioFocus()) {
            onError("الصوت مشغول بتطبيق أو مكالمة أخرى. سأنتظر حتى يصبح متاحاً.")
            if (conversationActive && !microphoneMuted) scheduleListening(700)
            return
        }
        val settings = settingsStore.load()
        val utteranceId = "sadeeqi-" + System.currentTimeMillis()
        lastUtteranceId = utteranceId
        pendingSpeech = null
        if (settings.useOpenAiVoice && remoteSpeech != null) {
            speaking = true
            try {
                remoteSpeech.speak(
                    cleanText,
                    settings.openAiVoiceName,
                    onDone = {
                        mainHandler.post {
                            if (utteranceId == lastUtteranceId) {
                                speaking = false
                                abandonSpeechAudioFocus()
                                if (conversationActive && !microphoneMuted) scheduleListening(250)
                            }
                        }
                    },
                    onError = {
                        mainHandler.post {
                            if (utteranceId == lastUtteranceId) {
                                speaking = false
                                speakLocal(cleanText, settings, utteranceId)
                            }
                        }
                    }
                )
            } catch (_: Exception) {
                // A closed/rejected realtime worker must not leave the listen/speak
                // state machine stuck; fall back to the already initialized local TTS.
                speaking = false
                speakLocal(cleanText, settings, utteranceId)
            }
            return
        }
        speakLocal(cleanText, settings, utteranceId)
    }

    private fun speakLocal(cleanText: String, settings: VoiceSettings, utteranceId: String) {
        if (!ttsReady) {
            // TTS initialization is asynchronous. Keep the latest response instead
            // of dropping it when the first answer arrives before the engine is ready.
            pendingSpeech = PendingSpeech(cleanText, settings, utteranceId)
            speaking = true
            return
        }
        val locale = Locale.forLanguageTag(settings.languageTag)
        val languageResult = tts?.setLanguage(locale) ?: TextToSpeech.ERROR
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED ||
            languageResult == TextToSpeech.ERROR
        ) {
            speaking = false
            abandonSpeechAudioFocus()
            onError("الصوت المحدد غير متوفر. اختر محركاً أو صوتاً عربياً من إعدادات الصوت.")
            if (conversationActive && !microphoneMuted) scheduleListening(700)
            return
        }
        tts?.voices?.firstOrNull { it.name == settings.voiceName }?.let { tts?.voice = it }
        tts?.setSpeechRate(settings.speechRate)
        tts?.setPitch(settings.pitch)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }
        speaking = true
        val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            // Some TTS engines fail synchronously and never deliver an utterance
            // callback. Release the speaking gate so the microphone can recover.
            speaking = false
            abandonSpeechAudioFocus()
            onError("تعذر بدء النطق. تحقق من محرك تحويل النص إلى كلام والصوت المختار.")
            if (conversationActive && !microphoneMuted) scheduleListening(700)
        }
    }

    override fun onInit(status: Int) {
        if (closed) {
            runCatching { tts?.shutdown() }
            tts = null
            return
        }
        if (status == TextToSpeech.SUCCESS) {
            val settings = settingsStore.load()
            val locale = Locale.forLanguageTag(settings.languageTag)
            val result = tts?.setLanguage(locale)
            ttsReady = result != null &&
                result != TextToSpeech.ERROR &&
                result != TextToSpeech.LANG_MISSING_DATA &&
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
                        abandonSpeechAudioFocus()
                        if (conversationActive && !microphoneMuted) scheduleListening(250)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == lastUtteranceId) {
                        speaking = false
                        abandonSpeechAudioFocus()
                        if (conversationActive && !microphoneMuted) scheduleListening(700)
                    }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == lastUtteranceId) {
                        speaking = false
                        abandonSpeechAudioFocus()
                        if (conversationActive && !microphoneMuted) scheduleListening(700)
                    }
                }
            })
            if (ttsReady) {
                val pending = pendingSpeech
                pendingSpeech = null
                if (pending != null) {
                    mainHandler.post {
                        if (pending.utteranceId == lastUtteranceId) {
                            speakLocal(pending.text, pending.settings, pending.utteranceId)
                        }
                    }
                }
            } else {
                pendingSpeech = null
                speaking = false
                abandonSpeechAudioFocus()
                onError("الصوت العربي غير متوفر. ثبّت بيانات صوت عربي من إعدادات تحويل النص إلى كلام.")
                if (conversationActive && !microphoneMuted) scheduleListening(700)
            }
        } else {
            pendingSpeech = null
            speaking = false
            abandonSpeechAudioFocus()
            onError("تعذر تشغيل خدمة النطق في الهاتف.")
            if (conversationActive && !microphoneMuted) scheduleListening(700)
        }
    }

    private fun requestSpeechAudioFocus(): Boolean {
        val granted = runCatching {
            audioManager.requestAudioFocus(speechAudioFocusRequest) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        hasAudioFocus = granted
        return granted
    }

    private fun abandonSpeechAudioFocus() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        runCatching { audioManager.abandonAudioFocusRequest(speechAudioFocusRequest) }
    }

    private fun cancelScheduledListening() {
        scheduledListening?.let(mainHandler::removeCallbacks)
        scheduledListening = null
    }

    private fun scheduleListening(delayMs: Long) {
        if (closed || !conversationActive || microphoneMuted) return
        // Replace only the pending recognition retry. Clearing the entire Handler
        // queue here could discard TTS or audio-focus callbacks posted to it.
        cancelScheduledListening()
        val retry = Runnable {
            scheduledListening = null
            if (conversationActive && !microphoneMuted && !speaking) {
                listening = false
                startListeningInternal()
            }
        }
        scheduledListening = retry
        mainHandler.postDelayed(retry, delayMs)
    }

    private fun createRecognitionListener(generation: Long): RecognitionListener = object : RecognitionListener {
        private fun deliver(action: () -> Unit) {
            if (!closed && generation == recognizerGeneration) action()
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
        if (closed) return
        closed = true
        conversationActive = false
        dictationActive = false
        microphoneMuted = false
        lastUtteranceId = null
        pendingSpeech = null
        mainHandler.removeCallbacksAndMessages(null)
        resetRecognizer()
        remoteSpeech?.cancel()
        runCatching { tts?.stop() }
        speaking = false
        abandonSpeechAudioFocus()
        tts?.shutdown()
        tts = null
        ttsReady = false
        listening = false
    }
}
