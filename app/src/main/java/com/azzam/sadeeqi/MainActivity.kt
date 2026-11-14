
package com.azzam.sadeeqi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
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
import com.azzam.sadeeqi.voice.VoiceForegroundService
import com.azzam.sadeeqi.memory.EncryptedMemory
import com.azzam.sadeeqi.music.MusicPlaybackState
import com.azzam.sadeeqi.music.MusicPlaybackService
import com.azzam.sadeeqi.music.MusicPlayerManager
import com.azzam.sadeeqi.music.MusicCommand
import com.azzam.sadeeqi.music.MusicCommandParser
import com.azzam.sadeeqi.music.MusicLibraryStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var onVoiceText: ((String) -> Unit)? = null
    private var onVoiceError: ((String) -> Unit)? = null
    private var onVoiceSession: ((Boolean) -> Unit)? = null

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ContextCompat.startForegroundService(this, Intent(this, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_START))
                onVoiceSession?.invoke(true)
            } else {
                onVoiceSession?.invoke(false)
                onVoiceError?.invoke("تم رفض إذن الميكروفون. اسمح به من إعدادات التطبيق.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the bundled premium Sadeeqi background drawable behind the Compose UI.
        window.setBackgroundDrawableResource(R.drawable.sadeeqi_background)

        setContent {
            SadeeqiApp(
                onStartVoice = {
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
        onStartVoice: () -> Unit,
        onStopVoice: () -> Unit,
        onInterruptVoice: () -> Unit,
        onReply: (String) -> Unit,
        onStopListening: () -> Unit,
        onInterrupt: () -> Unit
    ) {

        val gateway = remember { OpenAiGateway() }
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
        var microphoneMuted by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf("الرئيسية") }
        var searchQuery by remember { mutableStateOf("") }
        var musicState by remember { mutableStateOf(MusicPlaybackState()) }
        var musicQueue by remember { mutableStateOf(emptyList<Pair<android.net.Uri, String>>()) }
        var musicHistory by remember { mutableStateOf(emptyList<Pair<android.net.Uri, String>>()) }
        var musicFavorites by remember { mutableStateOf(emptyList<Pair<android.net.Uri, String>>()) }
        val musicLibrary = remember { MusicLibraryStore(this@MainActivity) }
        var saveDataMode by remember { mutableStateOf(true) }
        var musicNotice by remember { mutableStateOf("اختر ملفات صوتية من جهازك لبدء التشغيل.") }
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
        var status by remember {
            mutableStateOf("الإصدار 1.3.0 — المحادثة الصوتية العربية")
        }

        var selectedImageDataUrl by remember {
            mutableStateOf<String?>(null)
        }

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

            scope.launch {

                try {

                    val turns = messages.takeLast(24).map { (who, body) ->

                        OpenAiGateway.Turn(
                            if (who == "صديقي") "assistant" else "user",
                            body
                        )
                    }

                    val answer = withContext(Dispatchers.IO) {
                        gateway.reply(turns, imageForRequest)
                    }

                    messages.add("صديقي" to answer)

                    val stamp = System.currentTimeMillis()

                    memory.save(
                        "chat:$stamp:1",
                        "conversation",
                        "أنت\n$effectiveQuestion"
                    )

                    memory.save(
                        "chat:$stamp:2",
                        "conversation",
                        "صديقي\n$answer"
                    )

                    onReply(answer)

                    status = "متصل — ${BuildConfig.VERSION_NAME}"

                } catch (e: Exception) {

                    messages.add(
                        "صديقي" to
                            "تعذر الاتصال: ${e.message ?: "خطأ غير معروف"}"
                    )

                    status = "تعذر الاتصال بالخادم"
                    // The foreground voice service manages its own retry loop.
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

        onVoiceText = { spoken ->
            if (spoken.isNotBlank()) {
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
                    is MusicCommand.Search -> {
                        musicNotice = "طلب البحث: ${command.query}. البحث عبر الإنترنت ينتظر ربط مزود موسيقى مرخّص."
                        selectedTab = "موسيقى"
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
                            items(messages) { (who, body) ->
                                Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = if (who == "صديقي") Color(0xE626293A) else Color(0xE6332D43))) {
                                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(who, color = Color(0xFFE6C77A), style = MaterialTheme.typography.labelLarge)
                                        Text(body, color = Color.White)
                                    }
                                }
                            }
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xE51A1B2A))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(13.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(76.dp).background(Color(0xFF30243B), RoundedCornerShape(38.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (voiceSessionActive) "◉" else "✦", color = Color(0xFFE6C77A), style = MaterialTheme.typography.displaySmall)
                                }
                                Text(
                                    when { !voiceSessionActive -> "صديقي الصوتي"; microphoneMuted -> "الميكروفون مكتوم"; else -> "المحادثة الصوتية نشطة" },
                                    color = Color(0xFFE6C77A),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (voiceSessionActive) "تحدث بالعربية؛ سأجيبك ثم أستمع من جديد. اضغط مقاطعة للتحدث أثناء ردي." else "ابدأ محادثة صوتية متواصلة مع صديقي.",
                                    color = Color(0xFFD2D0DC),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Button(
                                        onClick = { if (voiceSessionActive) { onStopVoice(); voiceSessionActive = false } else onStartVoice() },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = if (voiceSessionActive) Color(0xFF7A3434) else Color(0xFF8E6B35))
                                    ) { Text(if (voiceSessionActive) "إنهاء" else "ابدأ التحدث") }
                                    OutlinedButton(
                                        onClick = { microphoneMuted = !microphoneMuted; startService(Intent(this@MainActivity, VoiceForegroundService::class.java).setAction(VoiceForegroundService.ACTION_MUTE).putExtra(VoiceForegroundService.EXTRA_MUTED, microphoneMuted)) },
                                        enabled = voiceSessionActive,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (microphoneMuted) "تشغيل الميكروفون" else "كتم الميكروفون") }
                                    OutlinedButton(
                                        onClick = { onInterruptVoice(); microphoneMuted = false },
                                        enabled = voiceSessionActive,
                                        modifier = Modifier.weight(1f)
                                    ) { Text("مقاطعة") }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), label = { Text("اكتب أمرك لصديقي") })
                        Spacer(Modifier.height(7.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("📷 صورة") }
                            OutlinedButton(onClick = { onStartVoice(); status = "بدأت المحادثة الصوتية" }, modifier = Modifier.weight(1f)) { Text("🎙 محادثة صوتية") }
                            Button(onClick = { val q = input; input = ""; sendQuestion(q) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E6B35))) { Text("إرسال") }
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
                    "المهام" -> {
                        Text("المهام والأتمتة", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        Card(modifier = Modifier.padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("✦ مساحة تنظيم المهام", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("ستظهر هنا مهامك ومتابعة تنفيذها. لا توجد مهام مجدولة حالياً.", color = Color(0xFFD2D0DC))
                                Text("هذه واجهة أولية؛ الجدولة التلقائية غير مربوطة بعد.", color = Color(0xFFE6C77A), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    "موسيقى" -> {
                        Text("SADEEQI MUSIC", color = Color(0xFFE6C77A), style = MaterialTheme.typography.headlineSmall)
                        Text("مشغّل ملفاتك الصوتية • يعمل دون اتصال", color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                        Text(musicNotice, color = Color(0xFFE6C77A), style = MaterialTheme.typography.bodySmall)
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
                                Text(if (musicState.isPlaying) "يعمل الآن" else "متوقف مؤقتاً", color = Color(0xFFC7B8E8))
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
                                Text("مفعّل افتراضياً؛ لا يوجد بث شبكي حالياً", color = Color(0xFFB9B4C7), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = saveDataMode, onCheckedChange = { enabled ->
                                saveDataMode = enabled
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
                            "المكتبة العالمية وخدمات البث لم تُربط بعد. لن تظهر نتائج أو موسيقى عبر الإنترنت قبل إضافة مزود مرخّص ومفاتيح API الخاصة به.",
                            color = Color(0xFFE6C77A),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    "الأدوات" -> {
                        Text("أدوات صديقي", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleLarge)
                        Card(modifier = Modifier.padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xE626293A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🎙 المساعد الصوتي", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("تحدث إلى صديقي باستخدام الميكروفون.", color = Color(0xFFD2D0DC))
                                Button(onClick = onStartVoice) { Text("ابدأ المحادثة الصوتية") }
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
                                Text("https://sadeeqi-ai.vercel.app", color = Color(0xFFE6C77A), style = MaterialTheme.typography.bodySmall)
                                Text("الإصدار: " + BuildConfig.VERSION_NAME, color = Color(0xFFD2D0DC))
                                Text("اللغة: العربية  •  المظهر: داكن", color = Color(0xFFD2D0DC))
                            }
                        }
                    }
                }
                if (selectedTab == "المهام" || selectedTab == "الأدوات" || selectedTab == "الإعدادات") {
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                NavigationBar(containerColor = Color(0xF0181926), contentColor = Color.White, tonalElevation = 0.dp) {
                    listOf("الرئيسية", "موسيقى", "البحث", "الذاكرة", "المهام", "الأدوات", "الإعدادات").forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(when (tab) { "الرئيسية" -> "⌂"; "موسيقى" -> "♫"; "البحث" -> "⌕"; "الذاكرة" -> "◈"; "المهام" -> "✓"; "الأدوات" -> "✧"; else -> "⚙" }, color = if (selectedTab == tab) Color(0xFFE6C77A) else Color.LightGray) },
                            label = { Text(tab, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    }
}
