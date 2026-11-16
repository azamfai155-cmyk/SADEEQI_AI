package com.azzam.sadeeqi.voice

import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import android.os.Bundle
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
    var profileMenu by remember { mutableStateOf(false) }
    var voiceNotice by remember { mutableStateOf("سيتم اختيار أفضل صوت عربي متاح على جهازك تلقائياً.") }
    val previewEngine = remember {
        TextToSpeech(context.applicationContext) { status ->
            Handler(Looper.getMainLooper()).post {
                engineReady = status == TextToSpeech.SUCCESS
            }
        }
    }
    DisposableEffect(previewEngine) {
        onDispose { previewEngine.stop(); previewEngine.shutdown() }
    }
    androidx.compose.runtime.LaunchedEffect(engineReady) {
        if (engineReady) {
            availableVoices = previewEngine.voices.orEmpty().sortedWith(
                compareByDescending<android.speech.tts.Voice> { it.locale.language == "ar" }
                    .thenByDescending { !it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.name }
            )
            val saved = availableVoices.firstOrNull { it.name == settings.voiceName }
            val bestArabic = availableVoices.firstOrNull {
                it.locale.language == "ar" && !it.isNetworkConnectionRequired
            } ?: availableVoices.firstOrNull { it.locale.language == "ar" }
            val chosen = saved ?: bestArabic
            if (chosen != null) {
                previewEngine.voice = chosen
                if (saved == null) {
                    val next = settings.copy(voiceName = chosen.name, languageTag = chosen.locale.toLanguageTag())
                    settings = next
                    store.save(next)
                    voiceNotice = if (chosen.isNetworkConnectionRequired) {
                        "تم اختيار أفضل صوت عربي متاح، لكنه يحتاج إلى الإنترنت."
                    } else {
                        "تم اختيار أفضل صوت عربي محلي متاح تلقائياً."
                    }
                }
            } else {
                voiceNotice = "لم يعثر محرك الهاتف على صوت عربي. ثبّت بيانات صوت عربي من إعدادات تحويل النص إلى كلام."
            }
        }
    }
    fun update(next: VoiceSettings) {
        settings = next
        store.save(next)
    }
    fun preview(voice: android.speech.tts.Voice?) {
        if (!engineReady) return
        previewEngine.language = Locale.forLanguageTag(settings.languageTag)
        if (voice != null) previewEngine.voice = voice
        previewEngine.setSpeechRate(settings.speechRate)
        previewEngine.setPitch(settings.pitch)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume)
        }
        val selectedProfile = VoiceDialectProfiles.find(settings.dialectProfileId)
        previewEngine.speak(
            selectedProfile.sampleText,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "sadeeqi-voice-preview"
        )
    }
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SADEEQI VOICE STUDIO", color = Color(0xFFE6C77A))
        Text("اختر شخصية صوتية مستقلة. اللهجة الإبّية هي الهدف، أما نطق اللهجة فعلياً فيعتمد على نموذج الصوت المثبت وسيتم التحقق منه بالاستماع.", color = Color.White)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الشخصية واللهجة", color = Color(0xFFE6C77A))
                val selectedProfile = VoiceDialectProfiles.find(settings.dialectProfileId)
                Text("${selectedProfile.displayName} — ${selectedProfile.dialectLabel}", color = Color.White)
                Text(selectedProfile.description, color = Color.LightGray)
                Button(
                    onClick = { profileMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اختيار الشخصية الصوتية") }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    VoiceDialectProfiles.all.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text("${profile.displayName} • ${profile.dialectLabel}") },
                            onClick = {
                                val savedVoiceName = store.voiceNameFor(profile.id)
                                val profileVoice = availableVoices.firstOrNull { it.name == savedVoiceName }
                                    ?: availableVoices.firstOrNull { it.locale.language == "ar" && !it.isNetworkConnectionRequired }
                                    ?: availableVoices.firstOrNull { it.locale.language == "ar" }
                                if (profileVoice != null) previewEngine.voice = profileVoice
                                update(settings.copy(
                                    dialectProfileId = profile.id,
                                    voiceName = profileVoice?.name.orEmpty(),
                                    languageTag = profile.languageTag,
                                    speechRate = profile.speechRate,
                                    pitch = profile.pitch
                                ))
                                profileMenu = false
                                voiceNotice = "تم اختيار ${profile.displayName}. إعداد الصوت محفوظ بشكل مستقل لهذه الشخصية."
                            }
                        )
                    }
                }
                Button(
                    onClick = { preview(availableVoices.firstOrNull { it.name == settings.voiceName }) },
                    enabled = engineReady,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("▶ معاينة الشخصية المختارة") }
                Text("الصوت المحلي", color = Color(0xFFE6C77A))
                Text("يعمل دون مفتاح API أو اشتراك. اختلاف اللهجة الإبّية بدقة يحتاج نموذجاً مدرّباً عليها؛ إعدادات السرعة والحدة وحدها لا تصنع لهجة أصلية.", color = Color.LightGray)
                Text("الصوت المعتمد", color = Color(0xFFE6C77A))
                Text(
                    availableVoices.firstOrNull { it.name == settings.voiceName }?.let {
                        "${it.name} — ${it.locale.displayName}"
                    } ?: "صوت المحرك الافتراضي",
                    color = Color.White
                )
                Button(
                    onClick = { voiceMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = engineReady && availableVoices.isNotEmpty()
                ) { Text("عرض وتجربة جميع الأصوات") }
                DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
                    availableVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${if (voice.locale.language == "ar") "عربي" else voice.locale.displayLanguage} • ${voice.name} • ${if (voice.isNetworkConnectionRequired) "يتطلب الإنترنت" else "محلي"}"
                                )
                            },
                            onClick = {
                                previewEngine.voice = voice
                                update(settings.copy(voiceName = voice.name, languageTag = voice.locale.toLanguageTag()))
                                voiceMenu = false
                                voiceNotice = "تم اعتماد ${voice.name}. اضغط اختبار الصوت للاستماع إليه."
                            }
                        )
                    }
                }
                Button(
                    onClick = { preview(availableVoices.firstOrNull { it.name == settings.voiceName }) },
                    enabled = engineReady,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("▶ استمع إلى الصوت المعتمد") }
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
                Text(voiceNotice, color = Color(0xFFE6C77A))
                Text("ملاحظة: ملفات إبّي وهادي وذكي مستقلة في الاسم والإعدادات والمعاينة. لا ندّعي دقة اللهجة الإبّية حتى نختبر نموذجاً صوتياً متخصصاً.", color = Color.LightGray)
                Text(
                    if (engineReady) "المحرك جاهز — ${availableVoices.size} صوتاً مثبتاً/متاحاً" else "جاري تهيئة محرك الصوت…",
                    color = Color(0xFFE6C77A)
                )
            }
        }
    }
}