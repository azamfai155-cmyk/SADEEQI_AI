package com.azzam.sadeeqi.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceController(context: Context, private val onText: (String) -> Unit, private val onError: (String) -> Unit) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(appContext) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale("ar") }
    }
    fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sadeeqi_reply") }
    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) { onError("التعرف على الصوت غير متاح على هذا الجهاز"); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) { onText(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()) }
                override fun onError(error: Int) { onError("تعذر فهم الصوت (رمز $error)") }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-YE")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            }
            r.startListening(intent)
        }
    }
    fun close() { recognizer?.destroy(); recognizer = null; tts?.stop(); tts?.shutdown(); tts = null }
}
