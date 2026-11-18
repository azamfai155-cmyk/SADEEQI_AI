package com.azzam.sadeeqi.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sceneview.ModelNode
import io.github.sceneview.SceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

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
    val character = rememberModelInstance(modelLoader, CHARACTER_MODEL_URL)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "شخصية صديقي ثلاثية الأبعاد",
            color = Color(0xFFE6C77A),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = if (character == null) "جاري تحميل نموذج الشخصية ثلاثي الأبعاد…" else "تم تحميل نموذج تجريبي متحرك",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        SceneView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            engine = engine,
            modelLoader = modelLoader
        ) {
            character?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 1.0f,
                    autoAnimate = true
                )
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
