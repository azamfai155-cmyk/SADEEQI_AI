
package com.azzam.sadeeqi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.azzam.sadeeqi.core.OpenAiGateway
import com.azzam.sadeeqi.auth.AuthActivity
import com.azzam.sadeeqi.auth.EncryptedSessionStore
import com.azzam.sadeeqi.auth.SupabaseAuthClient
import com.azzam.sadeeqi.voice.VoiceForegroundService
import com.azzam.sadeeqi.voice.VoiceSettingsStore
import com.azzam.sadeeqi.avatar.Avatar3DScreen
import com.azzam.sadeeqi.voice.VoiceStudioScreen
import com.azzam.sadeeqi.memory.EncryptedMemory
import com.azzam.sadeeqi.diagnostics.DeviceDiagnosticsSnapshot
import com.azzam.sadeeqi.music.MusicPlaybackState
import com.azzam.sadeeqi.music.MusicPlaybackService
import com.azzam.sadeeqi.music.MusicPlayerManager
import com.azzam.sadeeqi.music.MusicCommand
import com.azzam.sadeeqi.music.MusicCommandParser
import com.azzam.sadeeqi.music.MusicLibraryStore
import com.azzam.sadeeqi.music.MusicCatalogRepository
import com.azzam.sadeeqi.music.RuqyahRepository
import com.azzam.sadeeqi.music.QuranAudioRepository
import com.azzam.sadeeqi.music.RemoteMusicTrack
import com.azzam.sadeeqi.social.SocialHubScreen
import com.azzam.sadeeqi.phone.DeviceCommand
import com.azzam.sadeeqi.phone.DeviceCommandParser
import com.azzam.sadeeqi.phone.PhoneControl
import com.azzam.sadeeqi.trading.TradingIntelligenceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun formatDiagnosticBytes(value: Long?): String {
    if (value == null || value < 0) return "غير متاح"
    return String.format(Locale.US, "%.2f MB", value / 1_048_576.0)
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_VOICE_ORB = "com.azzam.sadeeqi.OPEN_VOICE_ORB"
    }

    private val openVoiceOrbRequested = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_VOICE_ORB, false)) openVoiceOrbRequested.value = true
    }

    private var requestedVoiceAction: String = VoiceForegroundService.ACTION_START
    private var onVoiceText: ((String) -> Unit)? = null
    private var onVoiceError: ((String) -> Unit)? = null
    private var onVoiceSession: ((Boolean) -> Unit)? = null

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ContextCompat.startForegroundService(this, Intent(this, VoiceForegroundService::class.java).setAction(requestedVoiceAction))
                onVoiceSession?.invoke(true)
            } else {
                onVoiceSession?.invoke(false)
                onVoiceError?.invoke("تم رفض إذن الميكروفون. اسمح به من إعدادات التطبيق.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openVoiceOrbRequested.value = intent.getBooleanExtra(EXTRA_OPEN_VOICE_ORB, false)
        if (openVoiceOrbRequested.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        if (EncryptedSessionStore.accessToken(this).isNullOrBlank()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // Use the bundled premium Sadeeqi background drawable behind the Compose UI.
        window.setBackgroundDrawableResource(R.drawable.sadeeqi_background)

        setContent {
            SadeeqiApp(
                onStartDictation = {
                    requestedVoiceAction = VoiceForegroundService.ACTION_DICTATE
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        ContextCompat.startForegroundService(this, Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_DICTATE))
                        onVoiceSession?.invoke(true)
                    } else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                onStartVoice = {
                    requestedVoiceAction = VoiceForegroundService.ACTION_START
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        ContextCompat.startForegroundService(this, Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_START))
                        onVoiceSession?.invoke(true)
                    } else {
                        requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopVoice = {
                    startService(Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_STOP))
                    onVoiceSession?.invoke(false)
                },
                onInterruptVoice = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        ContextCompat.startForegroundService(this, Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_INTERRUPT))
                        onVoiceSession?.invoke(true)
                    } else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                },
                onReply = { },
                onStopListening = { startService(Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_STOP)) },
                onInterrupt = { ContextCompat.startForegroundService(this, Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_INTERRUPT)) }
            )
        }
    }

    @Composable
    private fun SadeeqiApp(
        onStartDictation: () -> Unit,
        onStartVoice: () -> Unit,
        onStopVoice: () -> Unit,
        onInterruptVoice: () -> Unit,
        onReply: (String) -> Unit,
        onStopListening: () -> Unit,
        onInterrupt: () -> Unit
    ) {

        val gateway = remember { OpenAiGateway { SupabaseAuthClient(this@MainActivity).accessToken() } }
        val phoneControl = remember { PhoneControl(this@MainActivity) }
        val memory = remember { EncryptedMemory(this@MainActivity) }
        val scope = rememberCoroutineScope()

        val messages = remember {
            mutableStateListOf(
                "صديقي" to "أهلاً عزام! أنا صديقي. اكتب رسالتك أو اضغط الميكروفون."
            )
        }

        var input by remember {
            mutableStateOf("")
        }
        var voiceSessionActive by remember { mutableStateOf(false) }
        var voiceSpeaking by remember { mutableStateOf(false) }
        DisposableEffect(Unit) {
            val speechStateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: Intent?) {
                    voiceSpeaking = intent?.getBooleanExtra(VoiceForegroundService.EXTRA_SPEAKING, false) ?: false
                }
            }
            ContextCompat.registerReceiver(
                this@MainActivity,
                speechStateReceiver,
                IntentFilter(VoiceForegroundService.ACTION_SPEECH_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            onDispose {
                runCatching { unregisterReceiver(speechStateReceiver) }
                voiceSpeaking = false
            }
        }
        var isGenerating by remember { mutableStateOf(false) }
        var generationJob by remember { mutableStateOf<Job?>(null) }
        var attachmentMenuExpanded by remember { mutableStateOf(false) }
        var openMessageMenuIndex by remember { mutableStateOf<Int?>(null) }
        val messageReader = remember { TextToSpeech(this@MainActivity, null) }
        DisposableEffect(messageReader) {
            messageReader.setLanguage(Locale("ar"))
            onDispose { messageReader.stop(); messageReader.shutdown() }
        }
        var microphoneMuted by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(if (openVoiceOrbRequested.value) "الشخصية ثلاثية الأبعاد" else "الرئيسية") }
        LaunchedEffect(openVoiceOrbRequested.value) {
            if (openVoiceOrbRequested.value) {
                selectedTab = "الشخصية ثلاثية الأبعاد"
                openVoiceOrbRequested.value = false
            }
        }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val navigationItems = listOf("الرئيسية", "استوديو الصوت", "الشخصية ثلاثية الأبعاد", "التداول", "موسيقى", "SOCIAL HUB", "البحث", "الذاكرة", "التشخيصات", "الأدوات", "الإعدادات")
        var searchQuery by remember { mutableStateOf("") }
        var diagnosticsBaseline by remember { mutableStateOf<DeviceDiagnosticsSnapshot?>(null) }
        var diagnosticsLatest by remember { mutableStateOf<DeviceDiagnosticsSnapshot?>(null) }
        var diagnosticsBusy by remember { mutableStateOf(false) }
        var diagnosticsError by remember { mutableStateOf<String?>(null) }
        var musicState by remember { mutableStateOf(MusicPlaybackState()) }
        var musicQueue by remember { mutableStateOf(emptyList<Pair<android.net.Uri, String>>()) }
        var musicHistory by remember { mutableStateOf(emptyList<Pair<android.net.Uri, String>>()) }
        var musicFavorites by remember { mutableStateOf(emptyList<Pair<android.net.Uri, String>>()) }
        val musicLibrary = remember { MusicLibraryStore(this@MainActivity) }
        val musicCatalogRepository = remember { MusicCatalogRepository() }
        val quranRepository = remember { QuranAudioRepository() }
        var quranMenuExpanded by remember { mutableStateOf(false) }
        var selectedQuranReciter by remember { mutableStateOf(QuranAudioRepository.DEFAULT_RECITER) }
        var reciterMenuExpanded by remember { mutableStateOf(false) }
        var remoteSearchQuery by remember { mutableStateOf("") }
        var remoteMusicResults by remember { mutableStateOf(emptyList<RemoteMusicTrack>()) }
        var isSearchingMusic by remember { mutableStateOf(false) }
        var saveDataMode by remember { mutableStateOf(true) }
        var musicNotice by remember { mutableStateOf("ابحث عن موسيقى عالمية أو اختر ملفات من هاتفك.") }
        val musicManager = remember {
            MusicPlayerManager(this@MainActivity) { state -> musicState = state }
        }
        DisposableEffect(musicManager) {
            onDispose { musicManager.release() }
        }
        LaunchedEffect(Unit) {
            musicQueue = musicLibrary.loadQueue()
            musicHistory = musicLibrary.loadHistory()
            musicFavorites = musicLibrary.loadFavorites()
            saveDataMode = musicLibrary.isDataSaverEnabled()
        }
        LaunchedEffect(saveDataMode) {
            musicManager.setDataSaver(saveDataMode)
        }
        fun captureDiagnostics(asBaseline: Boolean) {
            if (diagnosticsBusy) return
            diagnosticsBusy = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        DeviceDiagnosticsSnapshot.capture(this@MainActivity)
                    }
                }
                result.onSuccess { snapshot ->
                    diagnosticsLatest = snapshot
                    if (asBaseline) diagnosticsBaseline = snapshot
                }.onFailure { error ->
                    diagnosticsError = "تعذر التقاط القياسات: ${error.message ?: "خطأ غير معروف"}"
                }
                diagnosticsBusy = false
            }
        }

        var status by remember {
            mutableStateOf("الإصدار 1.5.1 — المحادثة والصوت والموسيقى")
        }

        var selectedImageDataUrl by remember {
            mutableStateOf<String?>(null)
        }
        var pendingDeviceCommand by remember { mutableStateOf<DeviceCommand?>(null) }

        val audioPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            if (uris.isNotEmpty()) {
                val tracks = uris.map { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val title = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: uri.lastPathSegment ?: "ملف صوتي"
                    uri to title
                }
                musicQueue = tracks
                scope.launch {
                    musicLibrary.saveQueue(tracks)
                    tracks.firstOrNull()?.let { musicLibrary.recordPlayed(it.first, it.second) }
                    musicHistory = musicLibrary.loadHistory()
                }
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, MusicPlaybackService::class.java)
                )
                musicManager.playQueue(tracks)
                selectedTab = "موسيقى"
                status = "بدأ تشغيل ملفاتك الصوتية"
            }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                scope.launch {
                    val dataUrl = withContext(Dispatchers.IO) {
                        val output = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                        "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    }
                    selectedImageDataUrl = dataUrl
                    status = "تم تجهيز الصورة من الكاميرا — اكتب سؤالك."
                }
            } else {
                status = "تم إلغاء التقاط الصورة."
            }
        }

        val imagePicker = rememberLauncherForActivityResult(
            PickVisualMedia()
        ) { uri ->
            if (uri == null) {
                selectedImageDataUrl = null
            } else {
                status = "جاري تجهيز الصورة بأمان…"
                scope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = contentResolver
                                .openInputStream(uri)
                                ?.use { it.readBytes() }
                                ?: error("تعذر قراءة الصورة")

                            require(bytes.isNotEmpty()) { "الصورة فارغة." }
                            require(bytes.size <= 8 * 1024 * 1024) {
                                "حجم الصورة أكبر من 8 ميغابايت"
                            }

                            val mime = contentResolver.getType(uri) ?: "image/jpeg"
                            require(mime.startsWith("image/")) {
                                "الملف المحدد ليس صورة."
                            }

                            "data:$mime;base64," +
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                    }

                    loaded.onSuccess { dataUrl ->
                        selectedImageDataUrl = dataUrl
                        status = "تم تجهيز الصورة — اكتب سؤالك واضغط إرسال."
                    }.onFailure { error ->
                        selectedImageDataUrl = null
                        status = error.message ?: "تعذر تحميل الصورة."
                    }
                }
            }
        }

        fun sendQuestion(question: String) {

            if (question.isBlank() && selectedImageDataUrl == null) return

            val localCommand = DeviceCommandParser.parse(question)
            if (localCommand != null && selectedImageDataUrl == null) {
                messages.add("أنت" to question)
                pendingDeviceCommand = localCommand
                status = "بانتظار موافقتك لتنفيذ أمر على الهاتف."
                return
            }

            val imageForRequest = selectedImageDataUrl
            selectedImageDataUrl = null

            val effectiveQuestion = question.ifBlank {
                "حلل هذه الصورة واشرح ما يظهر فيها."
            }

            messages.add(
                "أنت" to if (imageForRequest != null) {
                    "$effectiveQuestion\n[صورة مرفقة]"
                } else {
                    effectiveQuestion
                }
            )

            status = "يتصل بمحرك صديقي عبر الإنترنت…"

            isGenerating = true
            generationJob = scope.launch {
                try {
                    val turns = messages.takeLast(24).map { (who, body) ->
                        OpenAiGateway.Turn(if (who == "صديقي") "assistant" else "user", body)
                    }
                    val answer = withContext(Dispatchers.IO) {
                        gateway.reply(turns, imageForRequest)
                    }
                    messages.add("صديقي" to answer)
                    val voiceSettings = VoiceSettingsStore(this@MainActivity).load()
                    if (voiceSettings.autoRead) {
                        withContext(Dispatchers.Main) {
                            messageReader.language = Locale.forLanguageTag(voiceSettings.languageTag)
                            messageReader.setSpeechRate(voiceSettings.speechRate)
                            messageReader.setPitch(voiceSettings.pitch)
                            messageReader.voices?.firstOrNull { it.name == voiceSettings.voiceName }?.let { messageReader.voice = it }
                            val speechParams = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceSettings.volume) }
                            messageReader.speak(answer, TextToSpeech.QUEUE_FLUSH, speechParams, "sadeeqi-auto-read")
                        }
                    }
                    val stamp = System.currentTimeMillis()
                    memory.save("chat:$stamp:1", "conversation", "أنت\n$effectiveQuestion")
                    memory.save("chat:$stamp:2", "conversation", "صديقي\n$answer")
                    onReply(answer)
                    status = "متصل — ${BuildConfig.VERSION_NAME}"
                } catch (e: kotlinx.coroutines.CancellationException) {
                    status = "تم إيقاف توليد الرد."
                    throw e
                } catch (e: Exception) {
                    messages.add("صديقي" to "تعذر الاتصال: ${e.message ?: "خطأ غير معروف"}")
                    status = "تعذر الاتصال بالخادم"
                } finally {
                    isGenerating = false
                    generationJob = null
                }
            }
        }

        LaunchedEffect(Unit) {

            val saved = memory.search("chat:")

            if (saved.isNotEmpty()) {

                messages.clear()

                saved.sortedBy { it.first }.forEach { entry ->

                    val parts = entry.second.split("\n", limit = 2)

                    if (parts.size == 2) {
                        messages.add(parts[0] to parts[1])
                    }
                }
            }
        }

        fun playQuranSurah(surahNumber: Int, surahName: String) {
            selectedTab = "موسيقى"
            musicNotice = "جاري تجهيز سورة $surahName بصوت القارئ المحدد…"
            scope.launch {
                try {
                    val tracks = withContext(Dispatchers.IO) {
                        quranRepository.loadSurah(surahNumber, selectedQuranReciter)
                    }
                    check(tracks.isNotEmpty()) { "لم تتوفر روابط صوتية لهذه السورة حالياً." }
                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, MusicPlaybackService::class.java))
                    musicManager.playRemoteQueue(tracks)
                    musicNotice = "بدأت تلاوة سورة $surahName — ${tracks.first().artist}"
                } catch (error: Exception) {
                    musicNotice = "تعذر تشغيل التلاوة: ${error.message ?: "تحقق من الإنترنت."}"
                }
            }
        }
        fun playRuqyah() {
            selectedTab = "موسيقى"
            musicNotice = "جاري تجهيز آيات الرقية الشرعية…"
            scope.launch {
                try {
                    val tracks = withContext(Dispatchers.IO) {
                        RuqyahRepository(quranRepository).loadPlaylist(selectedQuranReciter)
                    }
                    check(tracks.isNotEmpty()) { "لم تتوفر روابط آيات الرقية حالياً." }
                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, MusicPlaybackService::class.java))
                    musicManager.playRemoteQueue(tracks)
                    musicNotice = "بدأ تشغيل آيات الرقية من القرآن الكريم — ${tracks.first().artist}"
                } catch (error: Exception) {
                    musicNotice = "تعذر تشغيل الرقية: ${error.message ?: "تحقق من الإنترنت."}"
                }
            }
        }
        fun searchOnlineMusic(query: String) {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                musicNotice = "اكتب اسم الأغنية أو الفنان أولاً."
                return
            }
            remoteSearchQuery = cleanQuery
            selectedTab = "موسيقى"
            isSearchingMusic = true
            musicNotice = "جاري البحث في المكتبات الموسيقية المجانية…"
            scope.launch {
                try {
                    val results = withContext(Dispatchers.IO) {
                        musicCatalogRepository.search(cleanQuery, limit = if (saveDataMode) 20 else 40)
                    }
                    remoteMusicResults = results
                    musicNotice = if (results.isEmpty()) {
                        "لم نعثر على نتائج قابلة للبث. جرّب اسم فنان أو أغنية أخرى."
                    } else {
                        "تم العثور على ${results.size} نتيجة. اختر مقطعاً للتشغيل."
                    }
                } catch (error: Exception) {
                    remoteMusicResults = emptyList()
                    musicNotice = "تعذر البحث: ${error.message ?: "تحقق من اتصال الإنترنت."}"
                } finally {
                    isSearchingMusic = false
                }
            }
        }

        onVoiceText = { spoken ->
            if (spoken.isNotBlank()) {
                val phoneCommand = DeviceCommandParser.parse(spoken)
                if (phoneCommand != null) {
                    messages.add("أنت" to spoken)
                    pendingDeviceCommand = phoneCommand
                    status = "بانتظار موافقتك لتنفيذ أمر على الهاتف."
                } else {
                    when (val command = MusicCommandParser.parse(spoken)) {
                    MusicCommand.Pause -> {
                        musicManager.pause()
                        musicNotice = "تم إيقاف الموسيقى مؤقتاً."
                        selectedTab = "موسيقى"
                    }
                    MusicCommand.Resume -> {
                        musicManager.resume()
                        musicNotice = "تم طلب استئناف التشغيل."
                        selectedTab = "موسيقى"
                    }
                    MusicCommand.Next -> {
                        musicManager.next()
                        musicNotice = "انتقلت إلى المقطع التالي."
                        selectedTab = "موسيقى"
                    }
                    MusicCommand.Previous -> {
                        musicManager.previous()
                        musicNotice = "عدت إلى المقطع السابق."
                        selectedTab = "موسيقى"
                    }
                    MusicCommand.VolumeUp -> {
                        musicManager.adjustVolume(0.1f)
                        musicNotice = "تم رفع مستوى صوت المشغل."
                        selectedTab = "موسيقى"
                    }
                    MusicCommand.VolumeDown -> {
                        musicManager.adjustVolume(-0.1f)
                        musicNotice = "تم خفض مستوى صوت المشغل."
                        selectedTab = "موسيقى"
                    }
                    MusicCommand.OpenQuran -> {
                        selectedTab = "موسيقى"
                        quranMenuExpanded = true
                        musicNotice = "اختر السورة التي تريد الاستماع إليها."
                    }
                    MusicCommand.PlayRuqyah -> {
                        selectedTab = "موسيقى"
                        musicNotice = "جاري تجهيز الرقية من آيات القرآن الكريم…"
                        scope.launch {
                            try {
                                val tracks = withContext(Dispatchers.IO) { RuqyahRepository(quranRepository).loadPlaylist(selectedQuranReciter) }
                                check(tracks.isNotEmpty()) { "لم تتوفر روابط الرقية حالياً." }
                                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, MusicPlaybackService::class.java))
                                musicManager.playRemoteQueue(tracks)
                                musicNotice = "بدأ تشغيل آيات الرقية الشرعية من القرآن الكريم."
                            } catch (error: Exception) {
                                musicNotice = "تعذر تجهيز الرقية: ${error.message ?: "تحقق من الإنترنت."}"
                            }
                        }
                    }
                    is MusicCommand.PlayQuran -> {
                        playQuranSurah(command.surahNumber, command.surahName)
                    }
                    is MusicCommand.Search -> {
                        searchOnlineMusic(command.query)
                    }
                    null -> {
                        if (spoken.trim().replace(Regex("[!！؟?،,.\\s]+"), "") in setOf("ياصديقي", "صديقي")) {
                            val greeting = "نعم يا عزام، أنا معك"
                            messages.add("أنت" to spoken)
                            messages.add("صديقي" to greeting)
                            onReply(greeting)
                        } else sendQuestion(spoken)
                    }
                }
                }
            }
        }

        LaunchedEffect(musicState.isPlaying, selectedTab) {
            while (selectedTab == "موسيقى" && musicState.isPlaying) {
                delay(1000)
                musicManager.refresh()
            }
        }

        onVoiceError = {
            status = it
            if (it.contains("إذن الميكروفون") || it.contains("رفض")) voiceSessionActive = false
        }
        onVoiceSession = { active ->
            voiceSessionActive = active
            if (active) microphoneMuted = false
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFE6C77A),
                background = Color(0xFF10111A),
                surface = Color(0xFF1B1D2A)
            )
        ) {
            pendingDeviceCommand?.let { command ->
                AlertDialog(
                    onDismissRequest = { pendingDeviceCommand = null },
                    title = { Text("تأكيد أمر الهاتف", color = Color.White) },
                    text = { Text(DeviceCommandParser.confirmationText(command), color = Color(0xFFD2D0DC)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val result = runCatching {
                                when (command) {
                                    is DeviceCommand.LaunchApp -> check(phoneControl.launchApp(command.packageName)) {
                                        "تطبيق ${command.appName} غير مثبت أو لا يمكن فتحه."
                                    }
                                    DeviceCommand.OpenCamera -> phoneControl.openCamera()
                                    DeviceCommand.OpenSettings -> phoneControl.openSettings()
                                    DeviceCommand.VolumeUp -> phoneControl.adjustMusicVolume(true)
                                    DeviceCommand.VolumeDown -> phoneControl.adjustMusicVolume(false)
                                    is DeviceCommand.SetAlarm -> phoneControl.createAlarm(command.hour, command.minute, "منبه صديقي")
                                }
                            }
                            result.onSuccess {
                                val confirmation = when (command) {
                                    is DeviceCommand.LaunchApp -> "تم فتح تطبيق ${command.appName}."
                                    DeviceCommand.OpenCamera -> "تم فتح الكاميرا."
                                    DeviceCommand.OpenSettings -> "تم فتح إعدادات الهاتف."
                                    DeviceCommand.VolumeUp -> "تم طلب رفع مستوى صوت الوسائط."
                                    DeviceCommand.VolumeDown -> "تم طلب خفض مستوى صوت الوسائط."
                                    is DeviceCommand.SetAlarm -> "فُتح تطبيق الساعة. أكمل حفظ المنبه هناك."
                                }
                                messages.add("صديقي" to confirmation)
                                status = confirmation
                            }.onFailure { error ->
                                val message = error.message ?: "تعذر تنفيذ الأمر."
                                messages.add("صديقي" to message)
                                status = message
                            }
                            pendingDeviceCommand = null
                        }) { Text("تأكيد", color = Color(0xFF62A9FF)) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            messages.add("صديقي" to "تم إلغاء أمر الهاتف بناءً على طلبك.")
                            pendingDeviceCommand = null
                            status = "تم إلغاء الأمر."
                        }) { Text("إلغاء", color = Color(0xFFB9B4C7)) }
                    },
                    containerColor = Color(0xFF242333)
                )
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = Color(0xFF171824),
                        drawerContentColor = Color.White
                    ) {
                        Text(
                            "صديقي",
                            modifier = Modifier.padding(start = 22.dp, top = 24.dp, bottom = 4.dp),
                            color = Color(0xFFE6C77A),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "مساعدك الرقمي",
                            modifier = Modifier.padding(start = 22.dp, bottom = 18.dp),
                            color = Color(0xFFB9B4C7),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = {
                                messages.clear()
                                messages.add("صديقي" to "أهلاً! أنا صديقي. كيف أستطيع مساعدتك؟")
                                input = ""
                                scope.launch {
                                    memory.search("chat:").forEach { (recordId, _) -> memory.delete(recordId) }
                                    status = "تم حذف سجل المحادثة من ذاكرة هذا الجهاز."
                                }
                                selectedTab = "الرئيسية"
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                        ) {
                            Text("＋ محادثة جديدة", color = Color(0xFFE6C77A))
                        }
                        Spacer(Modifier.height(10.dp))
                        navigationItems.forEach { tab ->
                            NavigationDrawerItem(
                                label = { Text(tab) },
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFF393044),
                                    selectedTextColor = Color(0xFFE6C77A),
                                    unselectedTextColor = Color(0xFFE4E1EA)
                                )
                            )
                        }
                    }
                },
                gesturesEnabled = true
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xE51A1B2A),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰", color = Color(0xFFE6C77A), style = MaterialTheme.typography.headlineSmall)
                        }
                        Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(15.dp), color = Color(0xFF332A4A)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✦", color = Color(0xFFE6C77A), style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("صديقي", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text("مساعدك الرقمي الذكي", color = Color(0xFFC7B8E8), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("● جاهز", color = Color(0xFF9DE0B3), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when (selectedTab) {
                    "الرئيسية" -> {
                        Text("مرحباً بك من جديد", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleMedium)
                        Text("أنا هنا لمساعدتك في التفكير والبحث وإنجاز المهام.", color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                        Text(status, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                            itemsIndexed(messages) { index, (who, body) ->
                                Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = if (who == "صديقي") Color(0xE626293A) else Color(0xE6332D43))) {
                                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(who, color = Color(0xFFE6C77A), style = MaterialTheme.typography.labelLarge)
                                            Spacer(Modifier.weight(1f))
                                            Box {
                                                TextButton(onClick = { openMessageMenuIndex = index }) {
                                                    Text("⋯", color = Color(0xFFB9B4C7), style = MaterialTheme.typography.titleLarge)
                                                }
                                                DropdownMenu(
                                                    expanded = openMessageMenuIndex == index,
                                                    onDismissRequest = { openMessageMenuIndex = null },
                                                    modifier = Modifier.background(Color(0xFF242333))
                                                ) {
                                                    if (who == "صديقي") {
                                                        DropdownMenuItem(text = { Text("نسخ الرد") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                                                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("رد صديقي", body))
                                                            status = "تم نسخ الرد."
                                                        })
                                                        DropdownMenuItem(text = { Text("قراءة الرد صوتياً") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            messageReader.speak(body, TextToSpeech.QUEUE_FLUSH, null, "sadeeqi-reply")
                                                        })
                                                        DropdownMenuItem(text = { Text("إيقاف القراءة") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            messageReader.stop()
                                                        })
                                                        DropdownMenuItem(text = { Text("مشاركة الرد") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_TEXT, body)
                                                            }
                                                            startActivity(Intent.createChooser(share, "مشاركة رد صديقي"))
                                                        })
                                                        DropdownMenuItem(text = { Text("حفظ الرد في الذاكرة") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            scope.launch {
                                                                memory.save("saved-reply:${System.currentTimeMillis()}", "saved_reply", body)
                                                                status = "تم حفظ الرد في ذاكرتك."
                                                            }
                                                        })
                                                        DropdownMenuItem(text = { Text("إعادة توليد الرد") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            val previousUser = messages.take(index).lastOrNull { it.first == "أنت" }?.second.orEmpty()
                                                            if (previousUser.isNotBlank()) {
                                                                while (messages.size > index) messages.removeAt(messages.lastIndex)
                                                                sendQuestion(previousUser)
                                                            } else status = "لا توجد رسالة سابقة لإعادة المحاولة."
                                                        })
                                                    } else {
                                                        DropdownMenuItem(text = { Text("تعديل الرسالة") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            input = body
                                                            messages.removeAt(index)
                                                            scope.launch {
                                                                memory.search("chat:").filter { it.second.endsWith(body) }.forEach { memory.delete(it.first) }
                                                            }
                                                        })
                                                        DropdownMenuItem(text = { Text("نسخ الرسالة") }, onClick = {
                                                            openMessageMenuIndex = null
                                                            getSystemService(android.content.ClipboardManager::class.java)
                                                                ?.setPrimaryClip(android.content.ClipData.newPlainText("رسالة", body))
                                                            status = "تم نسخ الرسالة."
                                                        })
                                                    }
                                                }
                                            }
                                        }
                                        Text(body, color = Color.White)
                                        if (who == "صديقي") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = { status = "شكراً على تقييمك 👍" }) { Text("👍", color = Color(0xFFB9B4C7)) }
                                                TextButton(onClick = { status = "شكراً على ملاحظتك 👎" }) { Text("👎", color = Color(0xFFB9B4C7)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xF01B1D2A),
                            shape = RoundedCornerShape(30.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x553C3B4C)),
                            shadowElevation = 5.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box {
                                    IconButton(
                                        onClick = { attachmentMenuExpanded = true },
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Text("+", color = Color(0xFFE6C77A), style = MaterialTheme.typography.headlineMedium)
                                    }
                                    DropdownMenu(
                                        expanded = attachmentMenuExpanded,
                                        onDismissRequest = { attachmentMenuExpanded = false },
                                        modifier = Modifier.background(Color(0xFF242333))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("إرفاق صورة") },
                                            onClick = {
                                                attachmentMenuExpanded = false
                                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("التقاط صورة بالكاميرا") },
                                            onClick = {
                                                attachmentMenuExpanded = false
                                                cameraLauncher.launch(null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("البحث في الإنترنت") },
                                            onClick = {
                                                attachmentMenuExpanded = false
                                                val query = input.trim()
                                                if (query.isNotBlank()) {
                                                    input = ""
                                                    sendQuestion("ابحث في الإنترنت عن: $query")
                                                } else {
                                                    status = "اكتب موضوع البحث أولاً."
                                                }
                                            }
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { input = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("اسأل صديقي أي شيء", color = Color(0xFF9C9AAA)) },
                                    singleLine = false,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(22.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        cursorColor = Color(0xFFE6C77A),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                IconButton(
                                    onClick = {
                                        if (voiceSessionActive) {
                                            onStopVoice()
                                            voiceSessionActive = false
                                            status = "تم إيقاف الاستماع"
                                        } else {
                                            onStartDictation()
                                            status = "استمع صديقي إلى رسالتك الصوتية"
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Text("🎙", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                }
                                IconButton(
                                    onClick = {
                                        if (voiceSessionActive) {
                                            onStopVoice()
                                            voiceSessionActive = false
                                            status = "تم إيقاف المحادثة الصوتية"
                                        } else {
                                            onStartVoice()
                                            status = "المحادثة الصوتية المباشرة نشطة"
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Text("〰", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                                }
                                if (isGenerating) {
                                    IconButton(
                                        onClick = {
                                            gateway.cancelCurrentRequest()
                                            generationJob?.cancel()
                                        },
                                        modifier = Modifier.size(38.dp).background(Color(0xFF1687F8), RoundedCornerShape(11.dp))
                                    ) {
                                        Box(Modifier.size(12.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                    }
                                } else if (input.isNotBlank() || selectedImageDataUrl != null) {
                                    IconButton(
                                        onClick = {
                                            val question = input
                                            input = ""
                                            sendQuestion(question)
                                        },
                                        modifier = Modifier.size(38.dp).background(Color(0xFF1687F8), RoundedCornerShape(11.dp))
                                    ) {
                                        Text("↑", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                    }
                                }
                            }
                        }
                        if (selectedImageDataUrl != null) Text("تم اختيار صورة — أرسل سؤالك لتحليلها", color = Color(0xFFE6C77A))
                    }
                    "البحث" -> {
                        Text("البحث في محادثاتك", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ابحث في سجل المحادثات") })
                        val matches = messages.filter { searchQuery.isBlank() || it.second.contains(searchQuery, ignoreCase = true) }
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(matches) { (who, body) ->
                                Card(modifier = Modifier.padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                                    Column(Modifier.padding(12.dp)) { Text(who, color = Color(0xFFE6C77A)); Text(body, color = Color.White) }
                                }
                            }
                        }
                    }
                    "الذاكرة" -> {
                        Text("ذاكرة صديقي", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        Text("المحادثات التي حفظها التطبيق على هذا الجهاز", color = Color(0xFFD2D0DC))
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(messages) { (who, body) ->
                                Card(modifier = Modifier.padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                                    Column(Modifier.padding(12.dp)) { Text(who, color = Color(0xFFE6C77A)); Text(body, color = Color.White) }
                                }
                            }
                        }
                    }
                    "التشخيصات" -> {
                        Text("تشخيص الجهاز", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        Text("قياسات لحظية للبطارية والذاكرة واستهلاك شبكة التطبيق. التقط خط البداية ثم أعد القياس بعد تنفيذ المهمة.", color = Color(0xFFD2D0DC))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !diagnosticsBusy, onClick = { captureDiagnostics(true) }, modifier = Modifier.weight(1f)) { Text("حفظ خط البداية") }
                            Button(enabled = !diagnosticsBusy, onClick = { captureDiagnostics(false) }, modifier = Modifier.weight(1f)) { Text("قراءة حالية") }
                        }
                        if (diagnosticsBusy) Text("جارٍ التقاط القياسات…", color = Color(0xFFE6C77A))
                        diagnosticsError?.let { Text(it, color = Color(0xFFFFB4AB)) }
                        val snapshot = diagnosticsLatest
                        if (snapshot != null) {
                            Text("وقت القياس: ${snapshot.capturedAtEpochMs}", color = Color(0xFFB9B4C7))
                            Text("البطارية: ${snapshot.batteryPercent?.let { "$it%" } ?: "غير متاح"}")
                            Text("حرارة البطارية: ${snapshot.batteryTemperatureC?.let { "$it °C" } ?: "غير متاح"}")
                            Text("ذاكرة العملية PSS: ${snapshot.processPssKb} KB")
                            Text("Java Heap: ${snapshot.javaHeapUsedBytes / 1_048_576} / ${snapshot.javaHeapMaxBytes / 1_048_576} MB")
                            Text("RAM المتاحة: ${snapshot.availableDeviceMemoryBytes / 1_048_576} MB من ${snapshot.totalDeviceMemoryBytes / 1_048_576} MB")
                            Text("استقبال التطبيق RX: ${formatDiagnosticBytes(snapshot.appRxBytes)}")
                            Text("إرسال التطبيق TX: ${formatDiagnosticBytes(snapshot.appTxBytes)}")
                            Text(if (snapshot.lowMemory) "تحذير: النظام يعلن انخفاض الذاكرة." else "حالة الذاكرة: لا يوجد تحذير انخفاض ذاكرة من النظام.")
                            diagnosticsBaseline?.let { baseline ->
                                val delta = snapshot.deltaSince(baseline)
                                Text("الفرق منذ خط البداية (${delta.elapsedMs / 1000} ثانية):", color = Color(0xFFE6C77A))
                                Text("تغير البطارية: ${delta.batteryPercentagePoints?.let { "$it نقطة مئوية" } ?: "غير متاح"}")
                                Text("تغير PSS: ${delta.processPssDeltaKb} KB")
                                Text("بيانات RX: ${formatDiagnosticBytes(delta.appRxDeltaBytes)}")
                                Text("بيانات TX: ${formatDiagnosticBytes(delta.appTxDeltaBytes)}")
                            }
                        } else {
                            Text("لم تُلتقط قياسات بعد.", color = Color(0xFFD2D0DC))
                        }
                        Text("ملاحظة: عدادات الشبكة تخص التطبيق وتتراكم منذ الإقلاع أو بدء استخدام UID؛ وليست قياساً حصرياً للميزة التي تختبرها.", color = Color(0xFFB9B4C7))
                    }
                    "موسيقى" -> {
                        Text("SADEEQI MUSIC", color = Color(0xFFE6C77A), style = MaterialTheme.typography.headlineSmall)
                        Text("بحث موحد في Audius وOpenverse وWikimedia Commons وInternet Archive + ملفاتك المحلية", color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                        Text(musicNotice, color = Color(0xFFE6C77A), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = remoteSearchQuery,
                            onValueChange = { remoteSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("ابحث عن أغنية أو فنان عالمياً") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(onClick = { quranMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("📖 القرآن الكريم • اختر سورة")
                                }
                                DropdownMenu(expanded = quranMenuExpanded, onDismissRequest = { quranMenuExpanded = false }) {
                                    MusicCommandParser.allSurahs().forEach { (number, name) ->
                                        DropdownMenuItem(
                                            text = { Text("$number. $name") },
                                            onClick = { quranMenuExpanded = false; playQuranSurah(number, name) }
                                        )
                                    }
                                }
                            }
                            Box {
                                OutlinedButton(onClick = { reciterMenuExpanded = true }) { Text("القارئ") }
                                DropdownMenu(expanded = reciterMenuExpanded, onDismissRequest = { reciterMenuExpanded = false }) {
                                    listOf(
                                        "ar.alafasy" to "مشاري العفاسي",
                                        "ar.abdurrahmaansudais" to "عبدالرحمن السديس",
                                        "ar.husary" to "محمود خليل الحصري",
                                        "ar.minshawi" to "محمد صديق المنشاوي"
                                    ).forEach { (edition, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { selectedQuranReciter = edition; reciterMenuExpanded = false })
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { playRuqyah() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("۞ تشغيل الرقية الشرعية من القرآن")
                        }
                        Button(
                            onClick = { searchOnlineMusic(remoteSearchQuery) },
                            enabled = !isSearchingMusic && remoteSearchQuery.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E6B35))
                        ) {
                            Text(if (isSearchingMusic) "جاري البحث…" else "⌕ ابحث وشغّل")
                        }
                        if (remoteMusicResults.isNotEmpty()) {
                            Text("نتائج الموسيقى العالمية", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleMedium)
                            remoteMusicResults.take(12).forEachIndexed { index, track ->
                                TextButton(
                                    onClick = {
                                        ContextCompat.startForegroundService(
                                            this@MainActivity,
                                            Intent(this@MainActivity, MusicPlaybackService::class.java)
                                        )
                                        musicManager.playRemoteQueue(remoteMusicResults, index)
                                        musicNotice = "يتم تشغيل ${track.title} — ${track.artist}"
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text("♫  ${track.title}", color = Color.White, maxLines = 1)
                                        Text(track.artist, color = Color(0xFFB9B4C7), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        Text("${track.sourceName}${track.license?.let { " · ترخيص $it" } ?: ""}", color = Color(0xFF8F9AA8), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        track.attribution?.let { Text(it, color = Color(0xFF8F9AA8), style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xE526293A))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(164.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color(0xFF292238)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("♫", color = Color(0xFFE6C77A), style = MaterialTheme.typography.displayLarge)
                                    }
                                }
                                Text(musicState.title, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                                Text(
                                    musicState.errorMessage ?: if (musicState.isPlaying) "يعمل الآن" else "متوقف مؤقتاً",
                                    color = if (musicState.errorMessage != null) Color(0xFFFF8A80) else Color(0xFFC7B8E8)
                                )
                                val duration = musicState.durationMs.coerceAtLeast(1L)
                                Slider(
                                    value = musicState.positionMs.coerceIn(0L, duration).toFloat(),
                                    onValueChange = { musicManager.seekTo(it.toLong()) },
                                    valueRange = 0f..duration.toFloat()
                                )
                                Text("مستوى الصوت", color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = musicState.volume,
                                    onValueChange = { musicManager.setVolume(it) },
                                    valueRange = 0f..1f
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { musicManager.cycleRepeat() }) {
                                        Text(when (musicState.repeatMode) { 1 -> "تكرار المقطع"; 2 -> "تكرار الكل"; else -> "تكرار: إيقاف" }, color = Color(0xFFE6C77A))
                                    }
                                    IconButton(onClick = { musicManager.previous() }) {
                                        Text("⏮", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                    }
                                    FilledIconButton(
                                        onClick = { musicManager.toggle() },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF9A7438))
                                    ) {
                                        Text(if (musicState.isPlaying) "Ⅱ" else "▶", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                    }
                                    IconButton(onClick = { musicManager.next() }) {
                                        Text("⏭", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                                    }
                                    TextButton(onClick = { musicManager.toggleShuffle() }) {
                                        Text(if (musicState.shuffleEnabled) "عشوائي ✓" else "عشوائي", color = Color(0xFFE6C77A))
                                    }
                                }
                                val currentTrack = musicQueue.firstOrNull { it.second == musicState.title }
                        if (currentTrack != null) {
                            val isFavorite = musicFavorites.any { it.first == currentTrack.first }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        musicLibrary.toggleFavorite(currentTrack.first, currentTrack.second)
                                        musicFavorites = musicLibrary.loadFavorites()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (isFavorite) "★ إزالة من المفضلة" else "☆ أضف إلى المفضلة") }
                        }
                        OutlinedButton(
                                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("＋ اختر ملفات صوتية من الهاتف") }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("وضع توفير الإنترنت", color = Color.White)
                                Text("يقلل نتائج البحث إلى 8 ويحذف صور الأغاني ويخزن الصوت مؤقتاً؛ بث Audius نفسه يبقى بجودته الأصلية", color = Color(0xFFB9B4C7), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = saveDataMode, onCheckedChange = { enabled ->
                                saveDataMode = enabled
                                musicManager.setDataSaver(enabled)
                                scope.launch { musicLibrary.setDataSaverEnabled(enabled) }
                            })
                        }
                        Text("قائمة التشغيل الحالية", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleMedium)
                        if (musicQueue.isEmpty()) {
                            Text("لم تختر ملفات بعد. اختر ملفات صوتية من جهازك للبدء.", color = Color(0xFFD2D0DC))
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                items(musicQueue) { track ->
                                    TextButton(
                                        onClick = {
                                            val index = musicQueue.indexOf(track)
                                            ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, MusicPlaybackService::class.java))
                                            musicManager.playQueue(musicQueue, index)
                                            scope.launch {
                                                musicLibrary.recordPlayed(track.first, track.second)
                                                musicHistory = musicLibrary.loadHistory()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("♫  " + track.second, color = Color.White, modifier = Modifier.fillMaxWidth(), maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (musicFavorites.isNotEmpty()) {
                            Text("المفضلة", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleMedium)
                            musicFavorites.take(5).forEach { favorite ->
                                TextButton(onClick = {
                                    ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, MusicPlaybackService::class.java))
                                    musicManager.playQueue(musicFavorites, musicFavorites.indexOf(favorite))
                                }) {
                                    Text("★  " + favorite.second, color = Color.White, modifier = Modifier.fillMaxWidth(), maxLines = 1)
                                }
                            }
                        }
                        if (musicHistory.isNotEmpty()) {
                            Text("سجل الاستماع", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleMedium)
                            musicHistory.take(5).forEach { historyItem ->
                                Text("• " + historyItem.second, color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "المصدر: Audius — كتالوج مفتوح يضم موسيقى لفنانين مستقلين. توفر كل مقطع وحقوقه وشروط استخدامه تخضع لبيانات الفنان والمنصة؛ لا يعني ذلك توفر كل الإصدارات التجارية.",
                            color = Color(0xFFB9B4C7),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    "استوديو الصوت" -> {
                        VoiceStudioScreen(modifier = Modifier.weight(1f))
                    }
                    "الشخصية ثلاثية الأبعاد" -> {
                        Avatar3DScreen(isSpeaking = voiceSpeaking, modifier = Modifier.weight(1f))
                    }
                    "التداول" -> {
                        TradingIntelligenceScreen(memory = memory, modifier = Modifier.weight(1f))
                    }
                    "SOCIAL HUB" -> {
                        SocialHubScreen(accessTokenProvider = { SupabaseAuthClient(this@MainActivity).accessToken() }, modifier = Modifier.weight(1f))
                    }
                    "الأدوات" -> {
                        Text("أدوات صديقي", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        Card(modifier = Modifier.padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🎙 المساعد الصوتي", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("تحدث إلى صديقي باستخدام الميكروفون.", color = Color(0xFFD2D0DC))
                                Button(onClick = onStartVoice) { Text("ابدأ المحادثة الصوتية") }
                                OutlinedButton(
                                    onClick = {
                                        requestedVoiceAction = VoiceForegroundService.ACTION_WAKE_WORD
                                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            ContextCompat.startForegroundService(
                                                this@MainActivity,
                                                Intent(this@MainActivity, VoiceForegroundService::class.java)
                                                    .setAction(VoiceForegroundService.ACTION_WAKE_WORD)
                                            )
                                            onVoiceSession?.invoke(true)
                                        } else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                ) { Text("انتظر نداء «صديقي»") }
                            }
                        }
                        Card(modifier = Modifier.padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("▧ تحليل الصور", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("اختر صورة ثم انتقل للمحادثة الرئيسية لطرح سؤالك.", color = Color(0xFFD2D0DC))
                                OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("اختيار صورة") }
                            }
                        }
                    }
                    else -> {
                        Text("الإعدادات والملف الشخصي", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        Card(modifier = Modifier.padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("الملف الشخصي", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("المستخدم: عزام", color = Color(0xFFD2D0DC))
                                Text("المساعد: صديقي", color = Color(0xFFD2D0DC))
                                Divider(color = Color(0xFF555064))
                                Text("حالة الخادم: " + status, color = Color(0xFFD2D0DC))
                                Text(BuildConfig.SADEEQI_API_BASE_URL, color = Color(0xFFE6C77A), style = MaterialTheme.typography.bodySmall)
                                Text("الإصدار: " + BuildConfig.VERSION_NAME, color = Color(0xFFD2D0DC))
                                Text("اللغة: العربية  •  المظهر: داكن", color = Color(0xFFD2D0DC))
                                Button(
                                    onClick = {
                                        startService(Intent(this@MainActivity, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_STOP))
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                runCatching { SupabaseAuthClient(this@MainActivity).signOut() }
                                            }
                                            startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("تسجيل الخروج") }
                            }
                        }
                    }
                }
                if (selectedTab == "الأدوات" || selectedTab == "الإعدادات" || selectedTab == "SOCIAL HUB") {
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
            }
        }
    }
}
