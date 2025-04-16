package com.binissa.agsl.globe

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas

// State holder for water drop effect

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun GlobeEffect(
    imageBitmap: ImageBitmap,
    modifier: Modifier = Modifier,
    state: GlobeEffectState
) {
    // Use Compose's animation system for smooth water movement
    val infiniteTransition = rememberInfiniteTransition(label = "WaterAnimation")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2000 / state.speed).toInt(),
                easing = LinearEasing
            )
        ),
        label = "time"
    )

    // Cache the Android bitmap for performance
    val androidBitmap = remember(imageBitmap) {
        val buffer = IntArray(imageBitmap.width * imageBitmap.height)
        imageBitmap.readPixels(buffer)
        android.graphics.Bitmap.createBitmap(
            buffer, imageBitmap.width, imageBitmap.height, android.graphics.Bitmap.Config.ARGB_8888
        )
    }

    Canvas(modifier = modifier) {
        // Create shader for this frame
        val runtimeShader = RuntimeShader(EFFECT_SHADER)

        // Set current time based on animation state
        val currentTime = if (state.animate) time else 0f

        // Set shader parameters
        runtimeShader.setFloatUniform("iResolution", size.width, size.height)
        runtimeShader.setFloatUniform(
            "iImageResolution",
            androidBitmap.width.toFloat(), androidBitmap.height.toFloat()
        )
        runtimeShader.setFloatUniform("iTime", currentTime)

        // Set all the parameters
        runtimeShader.setFloatUniform("strength", state.strength)
        runtimeShader.setFloatUniform("frequency", state.frequency)
        runtimeShader.setFloatUniform("noiseAmount", state.noise)
        runtimeShader.setFloatUniform("edgeStrength", state.edge)
        runtimeShader.setFloatUniform("glowStrength", state.glow)
        runtimeShader.setFloatUniform("lightStrength", state.light)
        runtimeShader.setFloatUniform("lensStrength", state.lens)
        runtimeShader.setFloatUniform("refractionEnabled", if (state.refraction) 1f else 0f)


        // Create bitmap shader for the image
        val bitmapShader = android.graphics.BitmapShader(
            androidBitmap,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP
        )

        // Connect the shaders
        runtimeShader.setInputShader("iImage", bitmapShader)

        // Draw with the shader
        val paint = Paint().asFrameworkPaint()
        paint.shader = runtimeShader
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}
