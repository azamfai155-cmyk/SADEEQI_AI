package com.azzam.sadeeqi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceController(
    context: Context,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(appContext, this)
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("خدمة التعرف على الكلام غير متاحة على هذا الجهاز.")
            return
        }

        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer?.setRecognitionListener(this)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("تعذر تشغيل الميكروفون: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    fun speak(text: String) {
        if (!ttsReady) {
            onError("خدمة النطق لم تجهز بعد.")
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "sadeeqi-${System.currentTimeMillis()}"
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED

            if (!ttsReady) {
                onError("الصوت العربي غير متوفر في محرك النطق.")
            }
        } else {
            onError("تعذر تشغيل خدمة النطق.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "حدث خطأ في تسجيل الصوت."
            SpeechRecognizer.ERROR_CLIENT -> "تعذر تشغيل خدمة التعرف على الكلام."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "إذن الميكروفون غير متاح."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تحقق من اتصال الإنترنت."
            SpeechRecognizer.ERROR_NO_MATCH -> "لم أتمكن من فهم الكلام."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة الصوت مشغولة، حاول مجدداً."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم أسمع أي كلام."
            else -> "حدث خطأ أثناء الاستماع."
        }
        onError(message)
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

        if (text.isNotBlank()) {
            onText(text)
        } else {
            onError("لم أتمكن من التعرف على الكلام.")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    fun close() {
        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
