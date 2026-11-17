package com.azzam.sadeeqi.voice

import android.speech.tts.TextToSpeech
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var recognitionLanguageMenu by remember { mutableStateOf(false) }
    var voiceNotice by remember { mutableStateOf("سيتم اختيار أفضل صوت عربي متاح على جهازك تلقائياً.") }
    val sampleRecorder = remember { WakeWordSampleRecorder(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var sampleConsent by remember { mutableStateOf(false) }
    var samplePhrase by remember { mutableStateOf("صديقي") }
    var sampleRecording by remember { mutableStateOf(false) }
    var sampleCount by remember { mutableStateOf(sampleRecorder.sampleFiles().size) }
    var sampleNotice by remember { mutableStateOf("لا يبدأ التسجيل إلا بعد موافقتك والضغط على زر التسجيل.") }
    var confirmDeleteSamples by remember { mutableStateOf(false) }
    fun beginSampleCapture() {
        if (!sampleConsent) {
            sampleNotice = "فعّل الموافقة الصريحة أولاً بعد قراءة توضيح الخصوصية."
            return
        }
        val started = sampleRecorder.start(samplePhrase) { file, error ->
            mainHandler.post {
                sampleRecording = false
                sampleCount = sampleRecorder.sampleFiles().size
                sampleNotice = if (file != null) "حُفظت العينة محلياً: ${file.name}" else (error ?: "انتهى التسجيل دون حفظ عينة.")
            }
        }
        if (started) {
            sampleRecording = true
            sampleNotice = "الميكروفون نشط الآن — قل «${samplePhrase}» بوضوح. سيتوقف التسجيل تلقائياً بعد 2.5 ثانية."
        }
    }
    val samplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginSampleCapture()
        else sampleNotice = "لم يُمنح إذن الميكروفون؛ لم يتم تسجيل أي صوت."
    }
    DisposableEffect(sampleRecorder) {
        onDispose { sampleRecorder.stop() }
    }
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
    Column(modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SADEEQI VOICE STUDIO", color = Color(0xFFE6C77A))
        Text("اختر شخصية صوتية مستقلة. لغة التعرّف على كلامك منفصلة عن لغة النطق؛ اختيار العربية اليمنية تجريبي ويتوقف على دعم محرك التعرف.", color = Color.White)
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
                                val savedProfile = store.profileSettingsFor(profile.id)
                                val profileVoice = availableVoices.firstOrNull { it.name == savedProfile.voiceName }
                                    ?: availableVoices.firstOrNull { it.locale.language == "ar" && !it.isNetworkConnectionRequired }
                                    ?: availableVoices.firstOrNull { it.locale.language == "ar" }
                                if (profileVoice != null) previewEngine.voice = profileVoice
                                update(settings.copy(
                                    dialectProfileId = profile.id,
                                    voiceName = profileVoice?.name.orEmpty(),
                                    languageTag = savedProfile.languageTag,
                                    speechRate = savedProfile.speechRate,
                                    pitch = savedProfile.pitch
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
                Text("لغة التعرّف على كلامك", color = Color(0xFFE6C77A))
                Text(
                    if (settings.recognitionLanguageTag == "ar-YE") {
                        "العربية اليمنية — إذا كان محرك التعرف يدعمها؛ قد يرجع المحرك إلى العربية العامة."
                    } else {
                        "العربية العامة — الخيار الأوسع توافقاً مع محركات التعرف."
                    },
                    color = Color.LightGray
                )
                Button(
                    onClick = { recognitionLanguageMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اختيار لغة التعرّف") }
                DropdownMenu(
                    expanded = recognitionLanguageMenu,
                    onDismissRequest = { recognitionLanguageMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("العربية العامة") },
                        onClick = {
                            update(settings.copy(recognitionLanguageTag = "ar"))
                            recognitionLanguageMenu = false
                            voiceNotice = "تم اختيار العربية العامة للتعرّف."
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("العربية اليمنية (تجريبية)") },
                        onClick = {
                            update(settings.copy(recognitionLanguageTag = "ar-YE"))
                            recognitionLanguageMenu = false
                            voiceNotice = "تم اختيار العربية اليمنية. نجاحها يعتمد على دعم محرك التعرف المثبت."
                        }
                    )
                }
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
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تجهيز كلمة التنبيه — عينات صوتية محلية", color = Color(0xFFE6C77A))
                Text("سجّل عينات منفصلة لعبارتي «صديقي» و«يا صديقي». مدة كل عينة 2.5 ثانية بصيغة WAV أحادية القناة 16 kHz / PCM 16-bit.", color = Color.White)
                Text("الخصوصية: التسجيل لا يبدأ تلقائياً. بعد موافقتك، تُحفظ الملفات داخل مساحة التطبيق الخاصة على هذا الهاتف فقط. لا يوجد رفع إلى خادم أو إرسال إلى أي جهة.", color = Color.LightGray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { samplePhrase = "صديقي" }, enabled = !sampleRecording, modifier = Modifier.weight(1f)) {
                        Text(if (samplePhrase == "صديقي") "✓ صديقي" else "صديقي")
                    }
                    OutlinedButton(onClick = { samplePhrase = "يا صديقي" }, enabled = !sampleRecording, modifier = Modifier.weight(1f)) {
                        Text(if (samplePhrase == "يا صديقي") "✓ يا صديقي" else "يا صديقي")
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = sampleConsent, onCheckedChange = { sampleConsent = it }, enabled = !sampleRecording)
                    Text("أوافق على تسجيل صوتي وحفظ العينات محلياً لهذا الغرض فقط.", color = Color.White)
                }
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            beginSampleCapture()
                        } else samplePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !sampleRecording && sampleConsent,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("● تسجيل عينة «${samplePhrase}»") }
                OutlinedButton(
                    onClick = {
                        sampleRecorder.stop()
                        sampleNotice = "تم طلب إيقاف التسجيل؛ جارٍ إنهاء وحفظ الجزء الملتقط."
                    },
                    enabled = sampleRecording,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إيقاف التسجيل الآن") }
                Text("العينات المحفوظة: ${sampleCount}", color = Color.White)
                Text("الصيغة: WAV • 16,000 Hz • Mono • PCM 16-bit", color = Color.LightGray)
                Text(sampleNotice, color = Color(0xFFE6C77A))
                OutlinedButton(
                    onClick = { confirmDeleteSamples = true },
                    enabled = sampleCount > 0 && !sampleRecording,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("حذف جميع العينات المحلية") }
            }
        }
        if (confirmDeleteSamples) {
            AlertDialog(
                onDismissRequest = { confirmDeleteSamples = false },
                title = { Text("حذف العينات الصوتية؟") },
                text = { Text("سيتم حذف جميع ملفات عينات «صديقي» و«يا صديقي» من مساحة التطبيق الخاصة نهائياً.") },
                confirmButton = {
                    TextButton(onClick = {
                        val deleted = sampleRecorder.deleteAllSamples()
                        sampleCount = sampleRecorder.sampleFiles().size
                        sampleNotice = if (deleted) "تم حذف جميع العينات المحلية." else "تعذر حذف مجلد العينات."
                        confirmDeleteSamples = false
                    }) { Text("حذف نهائياً") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteSamples = false }) { Text("إلغاء") }
                }
            )
        }
    }
}