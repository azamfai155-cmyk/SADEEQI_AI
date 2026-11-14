
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.azzam.sadeeqi.core.OpenAiGateway
import com.azzam.sadeeqi.voice.VoiceController
import com.azzam.sadeeqi.memory.EncryptedMemory
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        // Use the bundled premium Sadeeqi background drawable behind the Compose UI.
        window.setBackgroundDrawableResource(R.drawable.sadeeqi_background)

        voice = VoiceController(
            this,
            { text ->
                runOnUiThread {
                    onVoiceText?.invoke(text)
                }
            },
            { err ->
                runOnUiThread {
                    onVoiceError?.invoke(err)
                }
            }
        )

        setContent {
            SadeeqiApp(
                onListen = {
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        voice.listen()
                    } else {
                        requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onReply = {
                    voice.speak(it)
                }
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
                "صديقي" to "أهلاً عزام! أنا صديقي. اكتب رسالتك أو اضغط الميكروفون."
            )
        }

        var input by remember {
            mutableStateOf("")
        }

        // تعريف الحالة قبل استخدامها داخل imagePicker
        var status by remember {
            mutableStateOf("الإصدار 1.2.0 — يحتاج إعداد خادم API")
        }

        var selectedImageDataUrl by remember {
            mutableStateOf<String?>(null)
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
                sendQuestion(spoken)
            }
        }

        onVoiceError = {
            status = it
        }

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFFB9A5FF),
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

                Text(
                    "صديقي | SADEEQI AI",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )

                Text(
                    "مرحباً عزام",
                    color = Color(0xFFB9A5FF)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    status,
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {

                    items(messages) { (who, body) ->

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (who == "صديقي") {
                                    Color(0xFF25283A)
                                } else {
                                    Color(0xFF34304A)
                                }
                            )
                        ) {

                            Column(Modifier.padding(12.dp)) {

                                Text(
                                    who,
                                    color = Color(0xFFB9A5FF)
                                )

                                Text(
                                    body,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("اكتب أمرك بالعربية")
                    }
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Button(
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("📷 صورة")
                    }

                    Button(
                        onClick = {
                            onListen()
                            status = "استمع الآن عبر خدمة التعرف على الكلام"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🎙 استمع")
                    }

                    Button(
                        onClick = {
                            val question = input
                            input = ""
                            sendQuestion(question)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إرسال")
                    }
                }

                if (selectedImageDataUrl != null) {

                    Text(
                        "تم اختيار صورة — أرسل سؤالك لتحليلها",
                        color = Color(0xFFB9A5FF)
                    )
                }
            }
        }
    }
}
