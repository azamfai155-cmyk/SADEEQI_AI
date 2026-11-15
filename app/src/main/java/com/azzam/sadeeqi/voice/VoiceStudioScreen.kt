package com.azzam.sadeeqi.voice

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun VoiceStudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { VoiceSettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var availableVoices by remember { mutableStateOf(emptyList<android.speech.tts.Voice>()) }
    var engineReady by remember { mutableStateOf(false) }
    var voiceMenu by remember { mutableStateOf(false) }
    val previewEngine = remember {
        TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val voices = emptyList<android.speech.tts.Voice>()
                // Voice enumeration is performed after engine initialization.
                engineReady = true
            }
        }
    }
    DisposableEffect(previewEngine) {
        onDispose { previewEngine.stop(); previewEngine.shutdown() }
    }
    androidx.compose.runtime.LaunchedEffect(engineReady) {
        if (engineReady) {
            availableVoices = previewEngine.voices?.filter { !it.isNetworkConnectionRequired }
                ?.sortedWith(compareBy({ it.locale.displayLanguage }, { it.name })).orEmpty()
            val selected = availableVoices.firstOrNull { it.name == settings.voiceName }
            if (selected != null) previewEngine.voice = selected
        }
    }
    fun update(next: VoiceSettings) {
        settings = next
        store.save(next)
    }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SADEEQI VOICE STUDIO", color = Color(0xFFE6C77A))
        Text("إدارة أصوات محرك تحويل النص إلى كلام المثبت على هاتفك. الأسماء والجودة والتوفر تختلف حسب الجهاز والمحرك.", color = Color.White)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الصوت المثبت", color = Color(0xFFE6C77A))
                Button(onClick = { voiceMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = engineReady) {
                    Text(availableVoices.firstOrNull { it.name == settings.voiceName }?.let { "${it.name} — ${it.locale.displayName}" } ?: "اختر صوتاً من الجهاز")
                }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text("${voice.name} • ${voice.locale.displayName} • ${if (voice.isNetworkConnectionRequired) "يتطلب الإنترنت" else "محلي"}") },
                            onClick = {
                                previewEngine.voice = voice
                                update(settings.copy(voiceName = voice.name, languageTag = voice.locale.toLanguageTag()))
                                voiceMenu = false
                            }
                        )
                    }
                }
                Text("سرعة الكلام: ${"%.2f".format(Locale.US, settings.speechRate)}", color = Color.White)
                Slider(value = settings.speechRate, onValueChange = { update(settings.copy(speechRate = it)) }, valueRange = 0.5f..2.0f)
                Text("حدة الصوت: ${"%.2f".format(Locale.US, settings.pitch)}", color = Color.White)
                Slider(value = settings.pitch, onValueChange = { update(settings.copy(pitch = it)) }, valueRange = 0.5f..2.0f)
                Text("مستوى صوت النطق: ${(settings.volume * 100).toInt()}%", color = Color.White)
                Slider(value = settings.volume, onValueChange = { update(settings.copy(volume = it)) }, valueRange = 0f..1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("قراءة الردود تلقائياً", color = Color.White)
                        Text("يقرأ ردود المحادثة بصوت الجهاز", color = Color.LightGray)
                    }
                    Switch(checked = settings.autoRead, onCheckedChange = { update(settings.copy(autoRead = it)) })
                }
                Button(
                    onClick = {
                        previewEngine.language = Locale.forLanguageTag(settings.languageTag)
                        previewEngine.setSpeechRate(settings.speechRate)
                        previewEngine.setPitch(settings.pitch)
                        availableVoices.firstOrNull { it.name == settings.voiceName }?.let { previewEngine.voice = it }
                        previewEngine.speak("مرحباً، أنا صديقي. هذا اختبار للصوت والإعدادات.", TextToSpeech.QUEUE_FLUSH, null, "sadeeqi-voice-preview")
                    },
                    enabled = engineReady,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("▶ اختبار الصوت") }
                Text(if (engineReady) "محرك الجهاز جاهز — ${availableVoices.size} صوتاً محلياً متاحاً" else "جاري تهيئة محرك الصوت…", color = Color(0xFFE6C77A))
            }
        }
    }
}
