package com.azzam.sadeeqi.auth

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = SupabaseAuthClient(this)
        setContent {
            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var createAccount by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var message by remember {
                mutableStateOf(
                    if (client.isConfigured()) "سجّل الدخول أو أنشئ حساباً جديداً."
                    else "يلزم إعداد Supabase قبل استخدام تسجيل الدخول."
                )
            }
            val scope = rememberCoroutineScope()
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFE6C77A),
                background = Color(0xFF10111A),
                surface = Color(0xFF1B1D2A)
            )) {
                Surface(Modifier.fillMaxSize().background(Color(0xFF10111A)), color = Color(0xFF10111A)) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("صديقي", color = Color(0xFFE6C77A), style = MaterialTheme.typography.headlineLarge)
                        Text("حسابك الآمن", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("البريد الإلكتروني") }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("كلمة المرور") }
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (!client.isConfigured()) {
                                    message = "لم يتم ضبط رابط Supabase والمفتاح العام في إعدادات المشروع."
                                } else if (email.isBlank() || password.length < 6) {
                                    message = "أدخل بريداً صحيحاً وكلمة مرور من 6 أحرف على الأقل."
                                } else {
                                    busy = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                if (createAccount) client.signUp(email, password)
                                                else client.signIn(email, password)
                                            }
                                        }
                                        busy = false
                                        result.onSuccess { data ->
                                            if (data.has("access_token")) {
                                                setResult(Activity.RESULT_OK)
                                                finish()
                                            } else {
                                                message = "تم إنشاء الحساب. تحقق من بريدك لتأكيده ثم سجّل الدخول."
                                                createAccount = false
                                            }
                                        }.onFailure { message = it.message ?: "تعذر إكمال العملية." }
                                    }
                                }
                            },
                            enabled = !busy && client.isConfigured(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text(if (busy) "جارٍ التنفيذ…" else if (createAccount) "إنشاء حساب" else "تسجيل الدخول") }
                        TextButton(onClick = { createAccount = !createAccount }) {
                            Text(if (createAccount) "لديك حساب؟ سجّل الدخول" else "إنشاء حساب جديد")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = Color(0xFFD2D0DC), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
