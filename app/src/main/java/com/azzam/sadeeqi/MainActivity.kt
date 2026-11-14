package com.azzam.sadeeqi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.azzam.sadeeqi.core.OpenAiGateway
import com.azzam.sadeeqi.memory.EncryptedMemory
import com.azzam.sadeeqi.voice.VoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF07090D)
private val Panel = Color(0xFF11151B)
private val PanelRaised = Color(0xFF1A2028)
private val Violet = Color(0xFFC49A52)
private val VioletLight = Color(0xFFE8C98A)
private val Muted = Color(0xFF9A9DA5)
private val TextMain = Color(0xFFF5F1E8)
private val Teal = Color(0xFF8BC8B5)
private val Silver = Color(0xFFC5CDD5)

class MainActivity : ComponentActivity() {

    private lateinit var voice: VoiceController
    private var onVoiceText: ((String) -> Unit)? = null
    private var onVoiceError: ((String) -> Unit)? = null

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voice.listen()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voice = VoiceController(
            this,
            { text -> runOnUiThread { onVoiceText?.invoke(text) } },
            { error -> runOnUiThread { onVoiceError?.invoke(error) } }
        )

        setContent {
            SadeeqiApp(
                onListen = {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        voice.listen()
                    } else {
                        requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onReply = { voice.speak(it) }
            )
        }
    }

    override fun onDestroy() {
        voice.close()
        super.onDestroy()
    }

    @Composable
    private fun SadeeqiApp(
        onListen: () -> Unit,
        onReply: (String) -> Unit
    ) {
        val gateway = remember { OpenAiGateway() }
        val memory = remember { EncryptedMemory(this@MainActivity) }
        val scope = rememberCoroutineScope()
        val messages = remember {
            mutableStateListOf(
                "صديقي" to "أهلاً عزام! أنا هنا لمساعدتك. اكتب ما تحتاجه أو أرسل رسالة صوتية."
            )
        }
        val listState = rememberLazyListState()

        var input by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("جاهز لمساعدتك") }
        var selectedTab by remember { mutableStateOf("الرئيسية") }
        var isSending by remember { mutableStateOf(false) }
        var selectedImageDataUrl by remember { mutableStateOf<String?>(null) }
        var memoryItems by remember { mutableStateOf(emptyList<Pair<String, String>>()) }

        val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            selectedImageDataUrl = uri?.let { picked ->
                runCatching {
                    val bytes = contentResolver.openInputStream(picked)
                        ?.use { it.readBytes() } ?: error("تعذرت قراءة الصورة")
                    require(bytes.size <= 8 * 1024 * 1024) {
                        "حجم الصورة أكبر من 8 ميغابايت"
                    }
                    val mime = contentResolver.getType(picked) ?: "image/jpeg"
                    "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }.getOrElse {
                    status = it.message ?: "تعذر تحميل الصورة"
                    null
                }
            }
            if (selectedImageDataUrl != null) {
                selectedTab = "المحادثة"
                status = "الصورة جاهزة للإرسال"
            }
        }

        fun sendQuestion(question: String) {
            if (isSending || (question.isBlank() && selectedImageDataUrl == null)) return

            val imageForRequest = selectedImageDataUrl
            selectedImageDataUrl = null
            val effectiveQuestion = question.trim().ifBlank {
                "حلل هذه الصورة واشرح ما يظهر فيها."
            }

            messages.add("أنت" to effectiveQuestion)
            isSending = true
            status = "صديقي يفكر الآن…"

            scope.launch {
                try {
                    val turns = messages.takeLast(24).map { (speaker, body) ->
                        OpenAiGateway.Turn(
                            if (speaker == "صديقي") "assistant" else "user",
                            body
                        )
                    }
                    val answer = withContext(Dispatchers.IO) {
                        gateway.reply(turns, imageForRequest)
                    }
                    messages.add("صديقي" to answer)

                    val stamp = System.currentTimeMillis()
                    memory.save("chat:$stamp:1", "conversation", "أنت\n$effectiveQuestion")
                    memory.save("chat:$stamp:2", "conversation", "صديقي\n$answer")
                    memoryItems = memory.search("chat:").sortedByDescending { it.first }
                    onReply(answer)
                    status = "متصل — الإصدار ${BuildConfig.VERSION_NAME}"
                } catch (error: Exception) {
                    val reason = error.message ?: "خطأ غير معروف"
                    messages.add("صديقي" to "لم أتمكن من إكمال الطلب. $reason")
                    status = "تعذر الاتصال — تحقق من إعداد الخادم والإنترنت"
                } finally {
                    isSending = false
                }
            }
        }

        LaunchedEffect(Unit) {
            val saved = memory.search("chat:").sortedBy { it.first }
            memoryItems = saved.reversed()
            if (saved.isNotEmpty()) {
                messages.clear()
                saved.forEach { entry ->
                    val parts = entry.second.split("\n", limit = 2)
                    if (parts.size == 2) messages.add(parts[0] to parts[1])
                }
            }
        }

        LaunchedEffect(messages.size, selectedTab) {
            if (selectedTab == "المحادثة" && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }

        onVoiceText = { spoken ->
            if (spoken.isNotBlank()) {
                selectedTab = "المحادثة"
                sendQuestion(spoken)
            }
        }
        onVoiceError = { status = it }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = VioletLight,
                onPrimary = Ink,
                secondary = Teal,
                background = Ink,
                surface = Panel,
                onSurface = TextMain
            )
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Ink
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF17130D), Color(0xFF0B0D11), Ink)
                            )
                        )
                        .padding(horizontal = 18.dp)
                ) {
                    Header(status = status)

                    when (selectedTab) {
                        "الرئيسية" -> HomeScreen(
                            onOpenChat = { selectedTab = "المحادثة" },
                            onListen = {
                                selectedTab = "المحادثة"
                                onListen()
                            },
                            onChooseImage = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            recentMessage = messages.lastOrNull()?.second.orEmpty()
                        )

                        "المحادثة" -> ChatScreen(
                            messages = messages,
                            listState = listState,
                            input = input,
                            onInputChange = { input = it },
                            isSending = isSending,
                            status = status,
                            hasImage = selectedImageDataUrl != null,
                            onListen = onListen,
                            onChooseImage = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onSend = {
                                val question = input
                                input = ""
                                sendQuestion(question)
                            }
                        )

                        "الذاكرة" -> MemoryScreen(memoryItems)
                        else -> SettingsScreen(status)
                    }

                    BottomNavigation(
                        selected = selectedTab,
                        onSelect = {
                            selectedTab = it
                            if (it == "الذاكرة") {
                                memoryItems = memory.search("chat:").sortedByDescending { row -> row.first }
                            }
                        }
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun Header(status: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF0D49A), Color(0xFF8E642D), Color(0xFF3A3022))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = Color(0xFFFFE6B3), fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SADEEQI AI", color = VioletLight, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text("صَديقك الذكي • إصدار شخصي", color = Muted, fontSize = 10.sp)
            }
            Surface(
                color = Color(0xFF17352F),
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Teal))
                    Spacer(Modifier.width(6.dp))
                    Text("جاهز", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF171725))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", color = if (status.startsWith("متصل")) Teal else VioletLight, fontSize = 9.sp)
            Spacer(Modifier.width(8.dp))
            Text(status, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ColumnScope.HomeScreen(
    onOpenChat: () -> Unit,
    onListen: () -> Unit,
    onChooseImage: () -> Unit,
    recentMessage: String
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text("مساء الخير، عزام 👋", color = Muted, fontSize = 14.sp)
        Text(
            "كيف أقدر أساعدك\nاليوم؟",
            color = TextMain,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenChat),
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF3A2B17), Color(0xFF191A1D), Color(0xFF101319))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SADEEQI AI  /  CORE ONLINE", color = VioletLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                    Text(
                        "ذكاء يفهمك.\nومساعد يرافقك.",
                        color = Color.White,
                        fontSize = 21.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onOpenChat,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletLight),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("ابدأ محادثة  →", color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text("اختصارات سريعة", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("🎙", "تحدث", Modifier.weight(1f), onListen)
            QuickAction("▧", "حلّل صورة", Modifier.weight(1f), onChooseImage)
            QuickAction("✎", "اكتب سؤالاً", Modifier.weight(1f), onOpenChat)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Column(Modifier.padding(15.dp)) {
                Text("آخر نشاط", color = VioletLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    recentMessage.take(100).ifBlank { "ابدأ أول محادثة مع صديقي." },
                    color = Muted,
                    fontSize = 13.sp,
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "خصوصيتك مهمة — تُحفظ الذاكرة محلياً بتشفير Android Keystore.",
            color = Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun QuickAction(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(symbol, color = VioletLight, fontSize = 22.sp)
            Text(label, color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ColumnScope.ChatScreen(
    messages: List<Pair<String, String>>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    input: String,
    onInputChange: (String) -> Unit,
    isSending: Boolean,
    status: String,
    hasImage: Boolean,
    onListen: () -> Unit,
    onChooseImage: () -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("المحادثة", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("تحدث مع مساعدك الشخصي", color = Muted, fontSize = 11.sp)
            }
            Surface(color = PanelRaised, shape = CircleShape) {
                Text("محادثة مشفّرة محلياً", color = VioletLight, fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp)
        ) {
            itemsIndexed(messages) { _, item ->
                val isAssistant = item.first == "صديقي"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 310.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 19.dp,
                                    topEnd = 19.dp,
                                    bottomEnd = if (isAssistant) 19.dp else 5.dp,
                                    bottomStart = if (isAssistant) 5.dp else 19.dp
                                )
                            )
                            .background(if (isAssistant) PanelRaised else Color(0xFF3A2E5C))
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Text(
                            if (isAssistant) "✦  صديقي" else "أنت",
                            color = if (isAssistant) VioletLight else Color(0xFFE0D5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(item.second, color = TextMain, fontSize = 14.sp, lineHeight = 21.sp)
                    }
                }
            }
            if (isSending) {
                item {
                    Text("صديقي يكتب الآن…", color = VioletLight, fontSize = 12.sp)
                }
            }
        }

        if (hasImage) {
            Text("✓ تم اختيار صورة — أضف سؤالك أو أرسلها للتحليل", color = VioletLight, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        Surface(
            color = Panel,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Column(Modifier.padding(9.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("اكتب رسالتك إلى صديقي…", color = Muted, fontSize = 13.sp) },
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                        cursorColor = VioletLight
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallAction("▧", onChooseImage)
                    SmallAction("🎙", onListen)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onSend,
                        enabled = !isSending && (input.isNotBlank() || hasImage),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VioletLight,
                            contentColor = Ink,
                            disabledContainerColor = Color(0xFF454154),
                            disabledContentColor = Muted
                        )
                    ) {
                        Text(if (isSending) "انتظر…" else "إرسال  ➤", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(symbol: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        color = PanelRaised,
        shape = RoundedCornerShape(13.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, color = VioletLight, fontSize = 17.sp)
        }
    }
}

@Composable
private fun ColumnScope.MemoryScreen(items: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ذاكرة صديقي", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("سجل المحادثات المحفوظ على هذا الجهاز", color = Muted, fontSize = 12.sp)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Panel)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔐", fontSize = 25.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("تخزين محلي مشفّر", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("AES-GCM عبر Android Keystore", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Text("الرسائل المحفوظة: ${items.size}", color = VioletLight, fontSize = 13.sp)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            itemsIndexed(items) { _, entry ->
                Card(
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Column(Modifier.fillMaxWidth().padding(13.dp)) {
                        Text(entry.second.substringBefore("\n"), color = VioletLight, fontSize = 10.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(entry.second.substringAfter("\n", entry.second).take(160),
                            color = TextMain, fontSize = 12.sp, maxLines = 3)
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Text("لا توجد محادثات محفوظة بعد.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ColumnScope.SettingsScreen(status: String) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("الإعدادات", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("إدارة تجربة صديقي", color = Muted, fontSize = 12.sp)
        SettingRow("✦", "هوية المساعد", "صديقي — مساعدك الشخصي")
        SettingRow("◉", "حالة الاتصال", status)
        SettingRow("🎙", "الصوت", "التعرف على الكلام والنطق عبر خدمات Android")
        SettingRow("🔐", "الخصوصية", "الذاكرة المحلية مشفّرة على الجهاز")
        SettingRow("⬆", "الإصدار", BuildConfig.VERSION_NAME)
        Spacer(Modifier.height(8.dp))
        Text(
            "تنبيه: التحكم بالتطبيقات وإجراءات الهاتف يحتاج أذونات وموافقات صريحة، ولم يتم تفعيل تنفيذ الأوامر على مستوى النظام في هذه النسخة.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun SettingRow(icon: String, title: String, detail: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, color = VioletLight, fontSize = 20.sp)
            Spacer(Modifier.width(13.dp))
            Column {
                Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BottomNavigation(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Triple("⌂", "الرئيسية", "الرئيسية"),
        Triple("✉", "المحادثة", "المحادثة"),
        Triple("▤", "الذاكرة", "الذاكرة"),
        Triple("⚙", "الإعدادات", "الإعدادات")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 9.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEach { tab ->
            val active = selected == tab.second
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) Color(0xFF352A55) else Color.Transparent)
                    .clickable { onSelect(tab.second) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(tab.first, color = if (active) VioletLight else Muted, fontSize = 19.sp)
                Text(tab.third, color = if (active) VioletLight else Muted, fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
