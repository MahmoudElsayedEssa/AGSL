package com.binissa.agsl.globe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin


class GlobeEffectState {
    // Standard parameters
    var animate by mutableStateOf(true)
    var speed by mutableFloatStateOf(0.26f)
    var strength by mutableFloatStateOf(0.05f)
    var frequency by mutableFloatStateOf(0.24f)
    var noise by mutableFloatStateOf(0.5f)
    var refraction by mutableStateOf(true)
    var edge by mutableFloatStateOf(0.1f)
    var light by mutableFloatStateOf(0.25f)
    var glow by mutableFloatStateOf(0.5f)
    var lens by mutableFloatStateOf(0.1f)

    // Touch interaction
    var touchEnabled by mutableStateOf(true)

    // Tap parameters
    var tapActive by mutableStateOf(false)
    var tapPosition by mutableStateOf(Offset.Zero) // Normalized [-0.5,0.5]
    var tapTime by mutableFloatStateOf(0f) // Time since tap
    var tapIntensity by mutableFloatStateOf(0f) // Fades from 1.0 to 0.0

    // Drag parameters
    var dragActive by mutableStateOf(false)
    var dragOffset by mutableStateOf(Offset.Zero) // How far content is dragged
    var dragVelocity by mutableStateOf(Offset.Zero) // Velocity for fluid effects
    var dragStartPosition by mutableStateOf(Offset.Zero) // Where drag began

    // Physics parameters
    var lastUpdateTime by mutableLongStateOf(0L)

    fun reset() {
        // Reset standard parameters
        animate = true
        speed = 0.26f
        strength = 0.05f
        frequency = 0.24f
        noise = 0.5f
        refraction = true
        edge = 0.1f
        light = 0.25f
        glow = 0.5f
        lens = 0.1f

        // Reset interaction parameters
        touchEnabled = true
        tapActive = false
        tapPosition = Offset.Zero
        tapTime = 0f
        tapIntensity = 0f
        dragActive = false
        dragOffset = Offset.Zero
        dragVelocity = Offset.Zero
        dragStartPosition = Offset.Zero

    }

    // Handle tap effect - water drop ripples
    fun onTap(position: Offset) {
        tapActive = true
        tapPosition = position
        tapTime = 0f
        tapIntensity = 1f
    }

    fun updateTapEffect(deltaTime: Float) {
        if (tapActive) {
            tapTime += deltaTime

            // drop physics with multiple phases
            if (tapTime < 0.3f) {
                // Phase 1: Impact - rapid build-up with slight overshoot
                tapIntensity = min(1.0f, tapTime / 0.2f) // Quick ramp up

                // Add slight "splash" overshoot
                if (tapTime > 0.15f && tapTime < 0.3f) {
                    tapIntensity = 1.0f + sin((tapTime - 0.15f) * 15f) * 0.1f
                }
            } else if (tapTime < 1.2f) {
                // Phase 2: Primary ripples - maintain high intensity with subtle variation
                tapIntensity = 1.0f + sin(tapTime * 12f) * 0.05f
            } else {
                // Phase 3: Decay - non-linear fade-out
                val decayProgress = (tapTime - 1.2f) / 0.8f
                tapIntensity = 1.0f - (decayProgress * decayProgress)

                // Deactivate when done
                if (tapTime > 2.0f) {
                    tapActive = false
                    tapIntensity = 0f
                }
            }
        }
    }

    fun updateDragPhysics(deltaTime: Float) {
        if (!dragActive) {
            // When not dragging - improved water physics for return

            // Gradually increasing spring force for more natural return
            val distanceFactor = dragOffset.getDistance()
            val springConstant = 9f + 8f * distanceFactor  // Stronger pull when stretched further

            // Variable damping for more realistic water movement
            // Water has less damping when moving fast, more when slowing down
            val speedFactor = dragVelocity.getDistance()
            val dampingConstant = 2f + 6f * (1f - kotlin.math.min(1f, speedFactor))

            // Calculate forces
            val springForce = -dragOffset * springConstant
            val dampingForce = -dragVelocity * dampingConstant

            // Combined force with mass factor (water has momentum)
            val acceleration = (springForce + dampingForce) / 1.2f

            // Update physics
            dragVelocity += acceleration * deltaTime
            dragOffset += dragVelocity * deltaTime

            // Add realistic water oscillation effects
            if (dragOffset.getDistance() > 0.01f) {
                // Primary wobble (larger, slower)
                val wobbleFrequency1 = 12f
                val wobbleAmplitude1 = 0.0025f * dragOffset.getDistance()
                val wobblePhase1 =
                    (System.currentTimeMillis() % 1000) / 1000f * 2f * Math.PI.toFloat()

                // Secondary wobble (smaller, faster)
                val wobbleFrequency2 = 28f
                val wobbleAmplitude2 = 0.001f * dragOffset.getDistance()
                val wobblePhase2 =
                    (System.currentTimeMillis() % 500) / 500f * 2f * Math.PI.toFloat()

                // Combine wobbles
                val wobbleX =
                    wobbleAmplitude1 * kotlin.math.cos(wobblePhase1 + wobbleFrequency1) + wobbleAmplitude2 * kotlin.math.cos(
                        wobblePhase2 + wobbleFrequency2
                    )
                val wobbleY =
                    wobbleAmplitude1 * sin(wobblePhase1) + wobbleAmplitude2 * sin(wobblePhase2 + wobbleFrequency2)

                // Add wobble with distance-based decay
                val wobbleDecay = 1f - kotlin.math.min(1f, dragOffset.getDistance() * 5f)
                val wobbleOffset = Offset(wobbleX, wobbleY) * wobbleDecay

                dragOffset += wobbleOffset
            } else if (dragOffset.getDistance() < 0.003f && dragVelocity.getDistance() < 0.03f) {
                // Stop tiny movements for performance
                dragOffset = Offset.Zero
                dragVelocity = Offset.Zero
            }
        } else {
            // When actively dragging, apply realistic water viscosity

            // Water has variable viscosity depending on speed
            val speedFactor = dragVelocity.getDistance() * 3f
            val viscosity = 6f + 4f * speedFactor

            // Apply non-linear drag for more realistic fluid behavior
            dragVelocity *= exp(-deltaTime * viscosity)
        }
    }
}
