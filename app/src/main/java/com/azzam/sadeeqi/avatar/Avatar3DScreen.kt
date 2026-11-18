package com.azzam.sadeeqi.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.Scene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.model.model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer

/**
 * First integrated 3D character viewport.
 *
 * The externally hosted model is a licensed animated male sample used to validate
 * GLB loading and skeletal animation. It is not the final photorealistic custom avatar
 * and it does not include facial blend-shapes or speech lip-sync.
 */
@Composable
fun Avatar3DScreen(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val httpClient = remember { OkHttpClient() }
    var character by remember { mutableStateOf<com.google.android.filament.gltfio.FilamentInstance?>(null) }
    var loadMessage by remember { mutableStateOf("جاري تحميل نموذج الشخصية ثلاثي الأبعاد…") }

    LaunchedEffect(modelLoader) {
        try {
            val bytes = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(CHARACTER_MODEL_URL).build()
                httpClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    requireNotNull(response.body) { "ملف النموذج فارغ" }.bytes()
                }
            }
            require(bytes.isNotEmpty()) { "ملف النموذج فارغ" }
            // Filament model creation stays on the main thread.
            character = modelLoader.createModelInstance(ByteBuffer.wrap(bytes))
            loadMessage = "تم تحميل نموذج تجريبي متحرك"
        } catch (error: Exception) {
            loadMessage = "تعذر تحميل النموذج: ${error.message ?: "تحقق من الإنترنت"}"
        }
    }

    DisposableEffect(modelLoader, character) {
        onDispose {
            character?.let { instance -> runCatching { modelLoader.destroyModel(instance.model) } }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "شخصية صديقي ثلاثية الأبعاد",
            color = Color(0xFFE6C77A),
            style = MaterialTheme.typography.titleLarge
        )
        Text(text = loadMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Scene(
            modifier = Modifier.fillMaxWidth().weight(1f),
            engine = engine,
            modelLoader = modelLoader
        ) {
            character?.let { instance ->
                ModelNode(modelInstance = instance, scaleToUnits = 1.0f, autoAnimate = true)
            }
        }
        Text(
            text = "النموذج التجريبي: Cesium Man — CC BY 4.0 — Cesium. يلزم استبداله بشخصية رجل واقعية مخصصة وإضافة تعابير الوجه ومزامنة الشفاه قبل اعتماد النسخة النهائية.",
            color = Color(0xFFB9B4C7),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private const val CHARACTER_MODEL_URL =
    "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/main/Models/CesiumMan/glTF-Binary/CesiumMan.glb"
