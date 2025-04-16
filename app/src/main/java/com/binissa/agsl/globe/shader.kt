package com.binissa.agsl.globe

import org.intellij.lang.annotations.Language



@Language("AGSL")
internal val GLOBE_SHADER = """
    uniform float2 iResolution;      // Canvas size
    uniform float2 iImageResolution; // Image size
    uniform float iTime;             // Time in seconds
    uniform shader iImage;

    // Motion parameters
    uniform float strength;          // Surface tension strength
    uniform float frequency;         // Speed control 
    uniform float noiseAmount;       // Flow turbulence

    // Visual parameters
    uniform float edgeStrength;      // Edge thickness/visibility
    uniform float glowStrength;      // Edge glow intensity
    uniform float lightStrength;     // Light/reflection intensity
    uniform float lensStrength;      // Lens distortion strength
    uniform float refractionEnabled; // Refraction toggle (1.0 = on, 0.0 = off)

    // Utility functions
    float hash(float2 p) {
        float h = dot(p, float2(127.1, 311.7));
        return fract(sin(h) * 43758.5453123);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);

        float a = hash(i);
        float b = hash(i + float2(1.0, 0.0));
        float c = hash(i + float2(0.0, 1.0));
        float d = hash(i + float2(1.0, 1.0));

        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    // Surface tension simulation with direct speed control
    float2 surfaceTension(float2 position, float time, float speed) {
        // Initialize force vector
        float2 force = float2(0.0);
        
        // ===== DIRECTIONAL FLOW =====
        // Create a continuous circular flow - speed controlled
        float flowSpeed = time * speed;
        float2 rotationalFlow = float2(-position.y, position.x) * 0.02 * 
                               (0.7 + 0.3 * sin(flowSpeed * 0.5));
        
        // ===== WAVE PROPAGATION =====
        // Surface tension waves with speed control
        for (int i = 0; i < 3; i++) {
            // Create wave sources that move around the bubble
            float sourceAngle = flowSpeed * 0.2 + float(i) * 2.09; // Speed-controlled movement
            float sourceRadius = 0.35;
            float2 waveSource = float2(cos(sourceAngle), sin(sourceAngle)) * sourceRadius;
            
            // Vector from wave source to current position
            float2 delta = position - waveSource;
            float dist = length(delta);
            
            // Ripple waves that propagate outward - speed controlled
            float wavePhase = dist * 15.0 - flowSpeed * 1.5;
            float wave = sin(wavePhase) * exp(-dist * 5.0); // Exponential decay with distance
            
            // Add force in the direction away from source
            force += normalize(delta) * wave * 0.03;
        }
        
        // ===== SURFACE RESTORATION =====
        // Surface tension tries to restore the bubble to a spherical shape
        float distFromCenter = length(position);
        float2 restorationForce = position * (0.5 - distFromCenter) * 0.05;
        
        // ===== OSCILLATIONS =====
        // Global oscillation modes with speed control
        float2 oscillation = float2(
            sin(flowSpeed * 1.2 + position.y * 3.0),
            cos(flowSpeed * 1.5 + position.x * 2.0)
        ) * 0.01;
        
        // Combine all forces
        return (rotationalFlow + force + restorationForce + oscillation) * strength;
    }

    // Main shader function
    half4 main(float2 fragCoord) {
        // Base coordinates
        float2 uv = fragCoord / iResolution;
        float2 center = float2(0.5, 0.5);
        float2 position = uv - center;
        float dist = length(position);
        float angle = atan(position.y, position.x);
        
        // Base bubble parameters
        float baseRadius = 0.45;
        float time = iTime;
        
        // Speed now directly controls all time-based movement
        float speed = frequency * 5.0; // Amplify frequency for more visible speed changes
        
        // ===== SURFACE TENSION SIMULATION =====
        // Only apply when animation is enabled
        float2 deformation = float2(0.0);
        if (iTime > 0.0) {
            // Calculate surface tension forces with speed control
            deformation = surfaceTension(position, time, speed);
        }
        
        // Apply deformation to position
        float2 deformedPosition = position + deformation;
        float deformedDist = length(deformedPosition);
        
        // Dynamic radius with subtle pulsation
        float dynamicRadius = baseRadius;
        if (iTime > 0.0) {
            dynamicRadius += 0.01 * sin(time * speed * 0.7);
        }
        
        // Distance to bubble edge
        float distToBubble = deformedDist - dynamicRadius;
        
        // ===== GLASS GLOBE EDGE PARAMETERS =====
        // Edge width and transition
        float edgeWidth = 0.04 * edgeStrength; 
        float outerEdge = 0.01;
        
        // Create edge mask - sharp outer edge, wider inner transition
        float maskBase = smoothstep(outerEdge, -0.01, distToBubble); // Base mask with sharp outer edge
        
        // Edge mask specifically for effects (refraction/glow)
        float edgeMask = smoothstep(edgeWidth, -0.002, distToBubble) * 
                         (1.0 - smoothstep(-0.005, -edgeWidth, distToBubble));
        
        // Enhanced edge gradient for better refraction near edges
        float refractEdgeGradient = smoothstep(edgeWidth * 2.0, 0.0, abs(distToBubble));
        
        // ===== DISTORTION =====
        // Calculate distorted UVs for the globe interior
        float2 distortedUV = uv;
        if (deformedDist < dynamicRadius + edgeWidth) {
            // Direction from center
            float2 dir = normalize(deformedPosition);
            
            // Lens distortion - stronger near edges
            float distFactor = smoothstep(0.0, dynamicRadius, deformedDist / dynamicRadius);
            float lensEffect = pow(distFactor, 1.5) * lensStrength * 0.3;
            distortedUV = uv - dir * lensEffect;
            
            if (iTime > 0.0) {
                // Add deformation to UVs - speed controlled
                distortedUV += deformation * (0.5 + lensStrength * 0.3);
                
                // Add flow-based distortion
                float flowNoise = noise(position * 5.0 + time * speed * float2(0.3, 0.4));
                distortedUV += dir * flowNoise * noiseAmount * 0.02 * distFactor;
            }
        }
        
        // ===== FLIPPED IMAGE REFRACTION (KEY TECHNIQUE) =====
        // Calculate UVs for the flipped image refraction effect
        float2 flippedUV = float2(1.0 - uv.x, uv.y); // Horizontal flip
        
        // Apply refraction to flipped image as well, but with opposite direction
        // This creates a more realistic glass effect
        if (dist < dynamicRadius + edgeWidth && refractionEnabled > 0.5) {
            float2 dir = normalize(position);
            float distFactor = smoothstep(0.0, dynamicRadius, dist / dynamicRadius);
            flippedUV += dir * distFactor * 0.15;
            
            if (iTime > 0.0) {
                flippedUV += deformation * 0.3;
            }
        }
        
        // ===== ENHANCED EDGE SAMPLING =====
        // Sample colors around the edge for the glow
        float2 edgePosition = normalize(position) * dynamicRadius;
        float2 edgeUV = edgePosition + center;
        
        // Additional offset for sampling refracted colors from just inside the edge
        float2 innerEdgeOffset = normalize(position) * (dynamicRadius - edgeWidth * 0.5);
        float2 innerEdgeUV = innerEdgeOffset + center;
        
        // Scale UVs to match image resolution
        float2 finalUV = distortedUV * iImageResolution;
        float2 flippedScaledUV = flippedUV * iImageResolution;
        float2 edgeScaledUV = edgeUV * iImageResolution;
        float2 innerEdgeScaledUV = innerEdgeUV * iImageResolution;
        
        // ===== IMAGE SAMPLING =====
        // Sample the image with distortion for the main content
        half4 color = iImage.eval(finalUV);
        
        // ===== FLIPPED IMAGE BLUR & REFRACTION =====
        half4 flippedColor = half4(0.0);
        
        // Create a heavily blurred version of the flipped image
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                float2 sampleOffset = float2(float(i), float(j)) * 4.0;
                flippedColor += iImage.eval(flippedScaledUV + sampleOffset);
            }
        }
        flippedColor /= 49.0; // Average samples
        
        // Use flipped & blurred image for refraction effect at edges
        float refractionStrength = refractionEnabled > 0.5 ? 0.8 : 0.3; // Some refraction even when disabled
        float edgeRefraction = refractEdgeGradient * refractionStrength * edgeStrength;
        color = mix(color, flippedColor, edgeRefraction); 
        
        // ===== CONTENT-BASED GLOW =====
        // Sample content colors for the glow - blend both the edge content and reflected content
        half4 edgeContentColor = half4(0.0);
        
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                float2 sampleOffset = float2(float(i), float(j)) * 2.0;
                // Sample near the edge for actual content colors
                edgeContentColor += iImage.eval(innerEdgeScaledUV + sampleOffset);
            }
        }
        edgeContentColor /= 25.0; // Average samples
        
        // Add the flipped colors for more richness in the glow
        half3 glowColor = mix(edgeContentColor.rgb, flippedColor.rgb, 0.5);
        
        // Enhance saturation and brightness for a more vibrant glow
        float luminance = (glowColor.r + glowColor.g + glowColor.b) / 3.0;
        glowColor = mix(half3(luminance), glowColor, 1.3); // Increase saturation
        glowColor = glowColor * 1.5; // Increase brightness for glow effect
        
        // ===== APPLY GLOW =====
        if (glowStrength > 0.0) {
            // Add the glow around the edge
            float glowFalloff = smoothstep(edgeWidth * 2.5, 0.0, abs(distToBubble));
            float glowIntensity = glowFalloff * glowStrength;
            
            // More pronounced at the edges
            color.rgb = mix(color.rgb, glowColor, glowIntensity * 0.5);
            
            // Add varying outer glow - stronger in the direction of light
            float lightAngle = 0.7; // Light coming from upper right
            float angleFactor = 0.5 + 0.5 * cos(angle - lightAngle);
            float outerGlow = smoothstep(outerEdge * 3.0, 0.0, max(0.0, distToBubble)) *
                              (1.0 - smoothstep(0.0, -outerEdge, distToBubble));
            outerGlow *= (0.7 + 0.3 * angleFactor); // Vary by angle
            
            // Apply to color with content-based glow colors
            color.rgb += glowColor * outerGlow * glowStrength * 0.7;
        }
        
        // ===== EDGE HIGHLIGHTS & REFLECTIONS =====
        if (lightStrength > 0.0) {
            // Add rim lighting effect
            float rimLight = smoothstep(0.03, 0.0, abs(distToBubble - 0.01));
            
            // Make rim color blend with content colors at the edge
            half3 rimColor = mix(half3(1.0), glowColor * 0.7 + half3(0.3), 0.3);
            color.rgb += rimColor * rimLight * lightStrength * 0.8;
            
            // Add specular highlight - primary
            float2 lightDir = normalize(float2(0.5, -0.7));
            float specularAngle = acos(dot(normalize(position), lightDir));
            float specular = pow(max(0.0, 1.0 - abs(specularAngle - 1.5) / 0.5), 8.0);
            color.rgb += half3(1.0) * specular * lightStrength * 0.6;
            
            // Add second smaller highlight
            float specular2 = pow(max(0.0, 1.0 - abs(dist - 0.3) / 0.05), 6.0);
            color.rgb += half3(1.0, 1.0, 1.0) * specular2 * lightStrength * 0.4;
        }
        
        // ===== COMPOSITE =====
        // Apply slight darkening at the very edge to emphasize the glass thickness
        float edgeDarkening = smoothstep(0.01, 0.0, abs(distToBubble)) * 0.3;
        color.rgb *= 1.0 - edgeDarkening;
        
        // Make the edges slightly transparent for realism
        float edgeTransparency = smoothstep(0.0, edgeWidth * 0.5, abs(distToBubble)) * 0.2;
        color.a *= 1.0 - edgeTransparency;
        
        // Apply the final mask with relatively sharp edge
        return color * maskBase;
    }
""".trimIndent()

//@Language("AGSL")
//internal val EFFECT_SHADER = """
//uniform float2 iResolution;      // Canvas size
//uniform float2 iImageResolution; // Image size
//uniform float iTime;             // Time in seconds
//uniform shader iImage;
//
//// Motion parameters
//uniform float strength;          // Motion distortion strength
//uniform float frequency;         // Noise frequency
//uniform float noiseAmount;       // Noise strength
//
//// Visual parameters
//uniform float edgeStrength;      // Edge effect strength
//uniform float glowStrength;      // Edge glow strength
//uniform float lightStrength;     // Light effect strength
//uniform float lensStrength;      // Lens distortion strength
//uniform float refractionEnabled; // Refraction toggle (1.0 = on, 0.0 = off)
//
//// Utility functions
//float hash(float2 p) {
//    float h = dot(p, float2(127.1, 311.7));
//    return fract(sin(h) * 43758.5453123);
//}
//
//float noise(float2 p) {
//    float2 i = floor(p);
//    float2 f = fract(p);
//    float2 u = f * f * (3.0 - 2.0 * f);
//
//    float a = hash(i);
//    float b = hash(i + float2(1.0, 0.0));
//    float c = hash(i + float2(0.0, 1.0));
//    float d = hash(i + float2(1.0, 1.0));
//
//    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
//}
//
//// Main
//half4 main(float2 fragCoord) {
//    // Base coordinates
//    float2 uv = fragCoord / iResolution; // normalized [0, 1]
//    float2 center = float2(0.5, 0.5);
//    float2 offset = uv - center;
//    float dist = length(offset);
//
//    // Globe parameters
//    float radius = 0.5;
//    float edgeWidth = 0.03 * edgeStrength;
//
//    // Edge masks for effects
//    float maskBase = smoothstep(radius, radius - 0.01, dist); // Base circular mask
//    float edgeMask = smoothstep(radius, radius - edgeWidth, dist) *
//                   smoothstep(radius - edgeWidth * 2.0, radius - edgeWidth, dist);
//
//    // Animate the edge using noise and time
//    float timeAngle = iTime * 3.14159 * 2.0 * frequency;
//    float edgeNoise = noise(float2(dist * 30.0, timeAngle)) * 0.5 + 0.5;
//    float animatedEdge = edgeMask * edgeNoise;
//
//    // Calculate distorted UVs for the globe interior
//    float2 distortedUV = uv;
//    if (dist < radius) {
//        // Direction from center
//        float2 dir = normalize(offset);
//
//        // Combine motion strength and lens strength
//        float combinedStrength = strength + lensStrength;
//
//        // Apply lens distortion - stronger near edges
//        float distFactor = smoothstep(0.0, radius, dist / radius);
//        float lensEffect = distFactor * combinedStrength * 0.2;
//        distortedUV = uv - dir * lensEffect;
//
//        // Add time-based motion
//        float wobbleAmount = sin(iTime * 6.28 * frequency) * strength * 0.03;
//        distortedUV += dir * wobbleAmount * distFactor;
//
//        // Add noise-based distortion
//        float n = noise(uv * frequency * 10.0 + iTime);
//        distortedUV += dir * (n - 0.5) * noiseAmount * 0.02 * distFactor;
//    }
//
//    // Calculate UVs for the refraction effect (flipped image)
//    float2 refractUV = float2(1.0 - uv.x, uv.y); // Horizontal flip
//
//    // Scale UVs to match image resolution
//    float2 finalUV = distortedUV * iImageResolution;
//    float2 refractScaledUV = refractUV * iImageResolution;
//
//    // Sample the image with distortion
//    half4 color = iImage.eval(finalUV);
//
//    // Create a simple blurred version of the flipped image for refraction
//    if (refractionEnabled > 0.5 && edgeMask > 0.1) {
//        half4 refractColor = half4(0.0);
//
//        // Multi-sample blur
//        for (int i = -2; i <= 2; i++) {
//            for (int j = -2; j <= 2; j++) {
//                float2 sampleOffset = float2(float(i), float(j)) * 2.0;
//                refractColor += iImage.eval(refractScaledUV + sampleOffset);
//            }
//        }
//        refractColor /= 25.0; // Average samples
//
//        // Mix with original based on edge proximity
//        color = mix(color, refractColor, edgeMask * edgeStrength * 0.7);
//    }
//
//    // Add edge glow (golden light)
//    half3 edgeColor = half3(1.0, 0.9, 0.7); // Warm golden color
//    color.rgb += edgeColor * animatedEdge * glowStrength * 1.5;
//
//    // Add highlight/light effect
//    float lightAngle = atan(offset.y, offset.x);
//    float lightPulse = 0.5 + 0.5 * sin(iTime * 3.0 + lightAngle * 4.0);
//    float lightEffect = pow(max(0.0, 1.0 - abs(dist - 0.48) / 0.02), 4.0) * lightPulse;
//    color.rgb += edgeColor * lightEffect * lightStrength * 2.0;
//
//    // Apply the final mask with soft edge
//    return color * maskBase;
//}
//"""