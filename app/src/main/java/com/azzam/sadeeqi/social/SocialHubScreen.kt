package com.azzam.sadeeqi.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.azzam.sadeeqi.BuildConfig

@Composable
fun SocialHubScreen(modifier: Modifier = Modifier) {
    var providers by remember { mutableStateOf<List<SocialProvider>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("يتم جلب حالة تكاملات المنصات من الخادم.") }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            SocialHubRepository().loadProviders()
        }.onSuccess { result ->
            providers = result.providers
            notice = result.notice
            error = null
        }.onFailure {
            error = it.message ?: "تعذر الاتصال بخدمة Social Hub."
        }
        loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "SADEEQI SOCIAL HUB",
                color = Color(0xFFE6C77A),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "مركز إدارة حسابات التواصل الاجتماعي",
                color = Color(0xFFD2D0DC),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252333)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("حالة الربط", color = Color(0xFFE6C77A), style = MaterialTheme.typography.titleMedium)
                    Text("الحسابات المتصلة فعلياً: 0", color = Color.White)
                    Text(
                        "لا يوجد أي حساب موصول حالياً. لن نعرض اتصالاً وهمياً أو نطلب كلمة مرور حسابك.",
                        color = Color(0xFFD2D0DC),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Text(
                "المنصات المدعومة مبدئياً",
                color = Color(0xFFE6C77A),
                style = MaterialTheme.typography.titleMedium
            )
            Text(notice, color = Color(0xFFB9B4C7), style = MaterialTheme.typography.bodySmall)
        }
        if (loading) {
            item { CircularProgressIndicator(color = Color(0xFFE6C77A)) }
        }
        error?.let { message ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF48282D))) {
                    Text(message, color = Color.White, modifier = Modifier.padding(14.dp))
                }
            }
        }
        items(providers, key = { it.id }) { provider ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2030)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row {
                        Text(provider.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (provider.configured) "إعداد المطور موجود" else "يتطلب إعداد المطور",
                            color = if (provider.configured) Color(0xFFE6C77A) else Color(0xFFFFC58A),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(provider.auth, color = Color(0xFFC7B8E8), style = MaterialTheme.typography.labelSmall)
                    Text(provider.note, color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (provider.connected) "متصل" else "غير مربوط",
                        color = Color(0xFFFFC58A),
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(onClick = { /* OAuth stays disabled until secure user sessions and provider approval are configured. */ }, enabled = false) {
                        Text("ربط الحساب — غير مفعّل بعد")
                    }
                }
            }
        }
        item {
            Text(
                "الأمان: كلمات مرور حساباتك لا تُطلب ولا تُخزّن. تفعيل الربط يتطلب جلسة مستخدم آمنة، وتخزين رموز OAuth مشفراً على الخادم، ومفاتيح تطبيق معتمدة لكل منصة.",
                color = Color(0xFFB9B4C7),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

data class SocialProvider(
    val id: String,
    val name: String,
    val family: String,
    val auth: String,
    val note: String,
    val configured: Boolean,
    val connected: Boolean
)

data class SocialProviderResponse(
    val providers: List<SocialProvider>,
    val notice: String
)
