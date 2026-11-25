package com.azzam.sadeeqi.reminders

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReminderScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf(System.currentTimeMillis() + 60 * 60 * 1000L) }
    var reminders by remember { mutableStateOf(ReminderScheduler.pending(context)) }
    var status by remember { mutableStateOf("تذكيراتك تحفظ على هذا الجهاز وتعمل حتى بعد إغلاق التطبيق.") }
    var queuedTitle by remember { mutableStateOf<String?>(null) }
    var queuedDueAt by remember { mutableStateOf(0L) }

    fun scheduleReminder(reminderTitle: String, reminderTime: Long) {
        runCatching {
            ReminderScheduler.schedule(context, reminderTitle, reminderTime)
        }.onSuccess {
            reminders = ReminderScheduler.pending(context)
            title = ""
            status = "تم ضبط التذكير بنجاح."
        }.onFailure {
            status = it.message ?: "تعذر ضبط التذكير."
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val requestedTitle = queuedTitle
        if (granted && requestedTitle != null) {
            scheduleReminder(requestedTitle, queuedDueAt)
        } else if (!granted) {
            status = "يلزم السماح بالإشعارات من إعدادات الهاتف لتلقي التذكيرات."
        }
        queuedTitle = null
    }

    LaunchedEffect(Unit) {
        reminders = ReminderScheduler.pending(context)
    }

    val dateText = remember(dueAt) {
        SimpleDateFormat("EEEE، d MMM yyyy", Locale("ar")).format(Date(dueAt))
    }
    val timeText = remember(dueAt) {
        SimpleDateFormat("hh:mm a", Locale("ar")).format(Date(dueAt))
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("التذكيرات والمواعيد", color = Color(0xFFE6C77A), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("أنشئ تنبيهاً محلياً على هاتفك. لا يحتاج إلى اشتراك أو خدمة مدفوعة.", color = Color(0xFFD2D0DC))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(160) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("عنوان التذكير") },
            supportingText = { Text("حتى 120 حرفاً") }
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xE526293A))
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("موعد التنبيه", color = Color.White)
                Text("$dateText — $timeText", color = Color(0xFFE6C77A))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = dueAt
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                }
                                dueAt = updated.timeInMillis
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) { Text("اختيار التاريخ", color = Color(0xFFE6C77A)) }
                    TextButton(onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = dueAt }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = dueAt
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                dueAt = updated.timeInMillis
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                        ).show()
                    }) { Text("اختيار الوقت", color = Color(0xFFE6C77A)) }
                }
            }
        }
        Button(
            onClick = {
                val safeTitle = ReminderValidator.normalizeTitle(title)
                if (safeTitle == null) {
                    status = "اكتب عنواناً للتذكير لا يتجاوز 120 حرفاً."
                } else if (!ReminderValidator.isFuture(dueAt, System.currentTimeMillis())) {
                    status = "اختر وقتاً في المستقبل."
                } else if (
                    Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    queuedTitle = safeTitle
                    queuedDueAt = dueAt
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleReminder(safeTitle, dueAt)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("ضبط التذكير") }
        Text(status, color = Color(0xFFC7B8E8))
        Spacer(Modifier.height(4.dp))
        Text("التذكيرات القادمة (${reminders.size})", color = Color(0xFFE6C77A), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if (reminders.isEmpty()) {
            Text("لا توجد تذكيرات قادمة حالياً.", color = Color(0xFFD2D0DC))
        } else {
            reminders.forEach { reminder ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE526293A))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(reminder.title, color = Color.White)
                            Text(
                                SimpleDateFormat("d MMM yyyy • hh:mm a", Locale("ar")).format(Date(reminder.dueAtMillis)),
                                color = Color(0xFFC7B8E8),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            ReminderScheduler.cancel(context, reminder.id)
                            reminders = ReminderScheduler.pending(context)
                            status = "تم إلغاء التذكير."
                        }) { Text("إلغاء", color = Color(0xFFFFB4AB)) }
                    }
                }
            }
        }
    }
}
