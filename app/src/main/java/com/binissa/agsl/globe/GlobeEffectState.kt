package com.binissa.agsl.globe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Add to GlobeEffectState.kt
class GlobeEffectState {
    // Existing parameters
    var animate by mutableStateOf(true)
    var speed by mutableFloatStateOf(0.26f)
    var strength by mutableFloatStateOf(0.05f)
    var frequency by mutableFloatStateOf(0.24f)
    var noise by mutableFloatStateOf(0.5f)
    var refraction by mutableStateOf(true)
    var edge by mutableFloatStateOf(0.1f)
    var light by mutableFloatStateOf(0.25f)
    var glow by mutableFloatStateOf(0.5f)
    var lens by mutableFloatStateOf(0.4f)


    fun reset() {
        // Reset existing parameters
        animate = true
        speed = 0.26f
        strength = 0.05f
        frequency = 0.24f
        noise = 0.5f
        refraction = true
        edge = 0.1f
        light = 0.25f
        glow = 0.5f
        lens = 0.4f

        // Reset new parameters
    }
}