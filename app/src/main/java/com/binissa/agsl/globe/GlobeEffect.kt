package com.binissa.agsl.globe

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import kotlin.math.min


@Composable
fun GlobeEffect(
    imageBitmap: ImageBitmap, modifier: Modifier = Modifier, state: GlobeEffectState
) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.animate, state.speed) {
        var lastFrameTime = System.nanoTime()
        while (state.animate) {
            withFrameNanos { currentTime ->
                val deltaNanos = currentTime - lastFrameTime
                lastFrameTime = currentTime

                val deltaSeconds = deltaNanos / 1_000_000_000f
                val adjustedSpeed = state.speed.coerceAtLeast(0.1f) // Avoid divide-by-zero

                // Increment shaderTime based on speed
                time += deltaSeconds * adjustedSpeed
            }
        }
    }


    val androidBitmap = remember(imageBitmap) {
        val buffer = IntArray(imageBitmap.width * imageBitmap.height)
        imageBitmap.readPixels(buffer)
        android.graphics.Bitmap.createBitmap(
            buffer, imageBitmap.width, imageBitmap.height, android.graphics.Bitmap.Config.ARGB_8888
        )
    }

    var lastUpdateTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val deltaTime = (now - lastUpdateTime) / 1000f
            lastUpdateTime = now

            // Cap delta time to avoid jumps
            val cappedDeltaTime = min(deltaTime, 0.05f)

            // Update tap ripple effect
            state.updateTapEffect(cappedDeltaTime)

            // Update drag physics with improved surface tension
            state.updateDragPhysics(cappedDeltaTime)

            delay(16) // ~60fps
        }
    }

    val interactionModifier = if (state.touchEnabled) {
        Modifier
            // Handle taps
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Convert tap position to normalized coordinates (-0.5 to 0.5)
                    val size = this.size.toSize()
                    val normalizedX = (offset.x / size.width) - 0.5f
                    val normalizedY = (offset.y / size.height) - 0.5f

                    state.onTap(Offset(normalizedX, normalizedY))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { offset ->
                    state.dragActive = true
                    state.dragVelocity = Offset.Zero

                    // Convert start position to normalized coordinates (-0.5 to 0.5)
                    val size = this.size.toSize()
                    val normalizedX = (offset.x / size.width) - 0.5f
                    val normalizedY = (offset.y / size.height) - 0.5f

                    // Save the exact start position
                    state.dragStartPosition = Offset(normalizedX, normalizedY)
                }, onDragEnd = {
                    state.dragActive = false
                    state.lastUpdateTime = System.currentTimeMillis()
                }, onDragCancel = {
                    state.dragActive = false
                    state.lastUpdateTime = System.currentTimeMillis()
                }, onDrag = { change, dragAmount ->
                    change.consume()

                    val size = this.size.toSize()

                    // Convert drag amount to normalized space
                    val dragX = dragAmount.x / size.width
                    val dragY = dragAmount.y / size.height

                    // Calculate normalized velocity for fluid physics
                    val dragDelta = Offset(dragX, dragY)
                    state.dragVelocity = dragDelta / 0.016f

                    // Increase follow coefficient for more responsive dragging
                    state.dragOffset += dragDelta * 0.9f

                    // Apply progressive tension limit - more resistance as it stretches
                    val maxDrag = 0.4f  // Increased maximum distance for more dramatic effect
                    val distance = state.dragOffset.getDistance()
                    if (distance > maxDrag) {
                        // Progressive non-linear tension creates realistic water stretching feel
                        val stretchFactor = (distance - maxDrag) / maxDrag
                        val tensionFactor =
                            1.0f / (1.0f + stretchFactor * 2f)  // Non-linear resistance
                        state.dragOffset = state.dragOffset * tensionFactor
                    }
                })
            }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(interactionModifier)) {
        // Create shader for this frame
        val runtimeShader = RuntimeShader(EFFECT_SHADER)

        // Set current time based on animation state
        val currentTime = if (state.animate) time else 0f

        // Set shader parameters
        runtimeShader.setFloatUniform("iResolution", size.width, size.height)
        runtimeShader.setFloatUniform(
            "iImageResolution", androidBitmap.width.toFloat(), androidBitmap.height.toFloat()
        )
        runtimeShader.setFloatUniform("iTime", currentTime)

        // Set all the standard parameters
        runtimeShader.setFloatUniform("strength", state.strength)
        runtimeShader.setFloatUniform("frequency", state.frequency)
        runtimeShader.setFloatUniform("noiseAmount", state.noise)
        runtimeShader.setFloatUniform("edgeStrength", state.edge)
        runtimeShader.setFloatUniform("glowStrength", state.glow)
        runtimeShader.setFloatUniform("lightStrength", state.light)
        runtimeShader.setFloatUniform("lensStrength", state.lens)
        runtimeShader.setFloatUniform("refractionEnabled", if (state.refraction) 1f else 0f)

        // Tap parameters
        runtimeShader.setFloatUniform("tapActive", if (state.tapActive) 1f else 0f)
        runtimeShader.setFloatUniform("tapX", state.tapPosition.x)
        runtimeShader.setFloatUniform("tapY", state.tapPosition.y)
        runtimeShader.setFloatUniform("tapTime", state.tapTime)
        runtimeShader.setFloatUniform("tapIntensity", state.tapIntensity)

        // Drag parameters
        runtimeShader.setFloatUniform("dragActive", if (state.dragActive) 1f else 0f)
        runtimeShader.setFloatUniform("dragX", state.dragOffset.x)
        runtimeShader.setFloatUniform("dragY", state.dragOffset.y)
        runtimeShader.setFloatUniform("dragStartX", state.dragStartPosition.x)
        runtimeShader.setFloatUniform("dragStartY", state.dragStartPosition.y)
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