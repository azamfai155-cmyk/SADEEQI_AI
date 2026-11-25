package com.azzam.sadeeqi.auth

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PrivateFilesScreen() {
    val context = LocalContext.current
    val storage = remember { SupabaseStorageClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("ملفاتك خاصة ولا يستطيع مستخدم آخر الوصول إليها.") }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var replacingPath by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val array = storage.list()
                    (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
                }
            }
            result.onSuccess {
                files = it
                message = if (it.isEmpty()) "لا توجد ملفات محفوظة بعد." else "عدد الملفات: ${it.size}"
            }.onFailure { message = it.message ?: "تعذر تحميل الملفات." }
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            message = "جارٍ رفع الملف..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = context.contentResolver.getType(uri) ?: error("تعذر تحديد نوع الملف.")
                        require(mime in SupabaseStorageClient.ALLOWED_TYPES) { "نوع الملف غير مسموح." }
                        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                            ?: "upload"
                        val temp = File.createTempFile("sadeeqi-upload-", ".tmp", context.cacheDir)
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                temp.outputStream().use { output -> input.copyTo(output) }
                            } ?: error("تعذر قراءة الملف.")
                            require(temp.length() in 1..SupabaseStorageClient.MAX_FILE_BYTES) { "حجم الملف يجب ألا يتجاوز 10 MB." }
                            val target = replacingPath
                            replacingPath = null
                            if (target == null) storage.upload(temp, mime) else storage.update(target, temp, mime)
                        } finally {
                            temp.delete()
                        }
                    }
                }
                result.onSuccess {
                    message = if (replacingPath == null) "تم حفظ الملف بنجاح." else "تم استبدال الملف بنجاح."
                }.onFailure { message = it.message ?: "فشل رفع الملف." }
                busy = false
                if (result.isSuccess) refresh()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ملفاتي الخاصة", style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain")) }) {
                Text("رفع ملف")
            }
            OutlinedButton(enabled = !busy, onClick = { refresh() }) { Text("تحديث") }
        }
        downloadedFile?.let { file ->
            Text("حُفظت نسخة داخل مساحة التطبيق: ${file.name}", style = MaterialTheme.typography.bodySmall)
        }
        if (busy) CircularProgressIndicator()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.optString("name") }) { item ->
                val path = item.optString("name")
                val fileName = path.substringAfterLast('/')
                Card(colors = CardDefaults.cardColors()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(fileName, style = MaterialTheme.typography.titleMedium)
                        Text(path, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                replacingPath = path
                                picker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain"))
                            }) { Text("استبدال") }
                            OutlinedButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val bytes = storage.download(path)
                                            val directory = File(context.filesDir, "private-downloads").apply { mkdirs() }
                                            File(directory, fileName).apply { writeBytes(bytes) }
                                        }
                                    }
                                    result.onSuccess {
                                        downloadedFile = it
                                        message = "اكتمل التنزيل داخل مساحة التطبيق."
                                    }.onFailure { message = it.message ?: "فشل التنزيل." }
                                    busy = false
                                }
                            }) { Text("تنزيل") }
                            OutlinedButton(enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { storage.delete(path) } }
                                    result.onSuccess { files = files.filterNot { it.optString("name") == path }; message = "تم حذف الملف." }
                                        .onFailure { message = it.message ?: "تعذر حذف الملف." }
                                    busy = false
                                }
                            }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
