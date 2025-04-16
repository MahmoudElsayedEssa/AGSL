package com.binissa.agsl.globe

import org.intellij.lang.annotations.Language



@Language("AGSL")
internal val EFFECT_SHADER = """
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

// Interaction parameters
uniform float tapActive;         // Is tap ripple active (1.0 = yes)
uniform float tapX;              // X position of tap (-0.5 to 0.5)
uniform float tapY;              // Y position of tap (-0.5 to 0.5)
uniform float tapTime;           // Time since tap started
uniform float tapIntensity;      // Tap intensity (starts at 1.0, fades to 0)

// Drag parameters
uniform float dragActive;        // Is being dragged (1.0 = yes)
uniform float dragX;             // X offset of drag (-0.5 to 0.5)
uniform float dragY;             // Y offset of drag (-0.5 to 0.5)
uniform float dragStartX;    // Starting X position of drag (-0.5 to 0.5)
uniform float dragStartY; 

// Utility functions
float hash(float2 p) {
    float h = dot(p, float2(127.1, 311.7));
    return fract(sin(h) * 43758.5453123);
}

float2 rotate(float2 v, float angle) {
    return float2(
        v.x * cos(angle) - v.y * sin(angle),
        v.x * sin(angle) + v.y * cos(angle)
    );
}
float smoothClamp(float value, float edge) {
    return 1.0 - pow(1.0 - clamp(value / edge, 0.0, 1.0), 2.0);
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

// Original surface tension simulation from the first shader
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

// water drop effect for more realistic water behavior
float2 waterDropEffect(float2 position, float2 dropPos, float time, float intensity) {
    if (intensity <= 0.0) return float2(0.0);
    
    float2 delta = position - dropPos;
    float dist = length(delta);
    
    // Enhanced parameters for more realistic water drops
    float speed = 1.5;
    float maxDist = 0.7;
    
    // Initial splash phase (0-0.3 seconds)
    float splashPhase = smoothstep(0.3, 0.0, time) * intensity;
    
    // Ripple phase (0.1 seconds onward)
    float ripplePhase = smoothstep(0.1, 0.2, time) * intensity;
    
    // Combined effect
    float2 displacement = float2(0.0);
    
    // 1. Initial crown splash effect
    if (splashPhase > 0.01) {
        float crownRadius = 0.05 + time * 0.2;
        float crownWidth = 0.02 + time * 0.05;
        
        // Crown shape with sharp peak
        float crownShape = exp(-pow((dist - crownRadius)/crownWidth, 2.0)) * 
                           pow(crownWidth, -1.0) * splashPhase;
        
        // Crown displacement is outward with upward component
        float2 crownDir = dist > 0.001 ? normalize(delta) : float2(0.0);
        
        // Add crown displacement - stronger at start, quickly fading
        displacement += crownDir * crownShape * 0.08 * exp(-time * 5.0);
        
        // Add central "bounce" - water column rising then falling
        float centralColumn = exp(-dist * 30.0) * sin(time * 20.0) * exp(-time * 3.0) * 0.1;
        displacement += float2(0.0, -1.0) * centralColumn * splashPhase;
    }
    
    // 2. Propagating ripple waves
    if (ripplePhase > 0.01) {
        float ripple = 0.0;
        
        // Multiple ripple layers with different characteristics
        for (int i = 0; i < 5; i++) {
            float waveSpeed = speed * (1.0 - float(i) * 0.05);
            float phase = float(i) * 0.2;
            float radius = (time - 0.1) * waveSpeed - phase;
            
            if (radius > 0.0) {
                // Dynamic ripple width - starts narrow, widens as it travels
                float rippleWidth = 0.01 + radius * 0.07;
                
                // Gaussian wave profile for realistic water waves
                float waveShape = exp(-pow((dist - radius)/rippleWidth, 2.0));
                
                // Amplitude decreases with distance and wave number
                float amplitude = exp(-radius * 1.5) * (1.0 - float(i) * 0.15);
                
                // Add some subtle oscillation for more water-like behavior
                float oscillation = sin(dist * 50.0 - time * 15.0) * 0.1;
                
                // Add to total ripple effect
                ripple += waveShape * amplitude * (1.0 + oscillation);
            }
        }
        
        // Direction with random perturbation for natural water movement
        float2 direction = dist > 0.001 ? normalize(delta) : float2(0.0);
        float perturbation = noise(position * 60.0 + time * 0.5) * 0.4;
        direction = rotate(direction, perturbation);
        
        // Add ripple displacement
        displacement += direction * ripple * 0.05 * ripplePhase;
    }
    
    // 3. Add subtle surface tension restoration at the center
    float centerRestorationForce = exp(-dist * 15.0) * exp(-time * 2.0) * 0.02;
    displacement -= normalize(delta) * centerRestorationForce;
    
    return displacement * intensity;
}

// water droplet drag effect with better visual quality
float2 surfaceTensionDrag(float2 position, float2 dragOffset, float dragActive, float2 dragStartPos) {
    if (length(dragOffset) < 0.001) return float2(0.0);
    
    // Use the exact position where the user started dragging
    float2 dragOrigin = dragStartPos;
    float2 dragVector = dragOffset;
    float2 dragCurrentPos = dragOrigin + dragVector;
    
    // Calculate distance to current drag point (where user's finger is now)
    float2 toDragPoint = position - dragCurrentPos;
    float distToDragPoint = length(toDragPoint);
    
    // Calculate distance to the drag origin (where user first touched)
    float2 toOrigin = position - dragOrigin;
    float distToOrigin = length(toOrigin);
    
    // Water droplet parameters - tuned for better visual effect
    float dropletRadius = 0.06; // Smaller core for more precise control
    float influenceRadius = 0.12; // Slightly tighter influence for more focused effect
    float tensionFactor = 0.8; // Controls how "stretchy" the water appears
    
    // Distance-based scaling for natural falloff
    float distanceScale = 1.0 - smoothstep(0.0, influenceRadius * 1.5, distToDragPoint);
    
    float2 displacement = float2(0.0);
    
    if (dragActive > 0.5) {
        // ===== ACTIVE DRAGGING - IMPROVED WATER DROPLET =====
        
        // 1. Main droplet body - higher quality water-like shape
        float dropletCore = smoothstep(dropletRadius, 0.0, distToDragPoint);
        dropletCore = pow(dropletCore, 1.3); // Sharper edge profile
        
        // Water has higher surface tension at the center of the droplet
        float surfaceTension = 0.8 + 0.2 * dropletCore;
        
        // Primary displacement - direct follow with natural falloff
        displacement = dragVector * dropletCore * surfaceTension;
        
        // 2. Extended influence with smoother transition
        float extendedArea = smoothstep(influenceRadius, dropletRadius * 0.9, distToDragPoint);
        extendedArea *= (1.0 - dropletCore); // Only in transition region
        
        // Extended area follows with less intensity
        displacement += dragVector * extendedArea * 0.6 * tensionFactor;
        
        // 3. Realistic water bulge effects based on speed and direction
        float dragLength = length(dragVector);
        float dragSpeed = min(dragLength * 10.0, 2.0); // Normalize speed effect
        
        // Direction of drag
        float2 dragDir = normalize(dragVector);
        
        // Create perpendicular direction for bulging
        float2 perpDir = float2(-dragDir.y, dragDir.x);
        
        // Bulge parameters - varies based on speed and direction
        float bulgeWidth = 0.06 + 0.04 * dragSpeed;
        float bulgeDistance = dropletRadius * (1.0 + 0.5 * dragSpeed);
        
        // Create realistic bulge profile
        float bulgeFactor = exp(-pow((distToDragPoint - bulgeDistance) / bulgeWidth, 2.0));
        bulgeFactor *= extendedArea * dragSpeed * 0.15;
        
        // Add noise for organic, water-like variation
        float bulgeVariation = noise(position * 30.0 + dragCurrentPos * 8.0) * 0.5 + 0.5;
        
        // Apply bulge perpendicular to movement direction
        displacement += perpDir * bulgeFactor * bulgeVariation * tensionFactor;
        
        // 4. Leading edge compression (water piles up in front of movement)
        float frontFactor = smoothstep(-0.2, 0.7, dot(normalize(toDragPoint), dragDir));
        float frontCompress = exp(-pow(distToDragPoint / (dropletRadius * 0.7), 2.0)) * 
                             frontFactor * dragSpeed * 0.03;
        
        // Apply compression at the front edge
        displacement += dragDir * frontCompress;
        
        // 5. Trailing thin water connection between origin and current position
        if (dragLength > dropletRadius * 2.0) {
            // Calculate how close the current point is to the line between origin and current position
            float2 dragLine = normalize(dragVector);
            float projectedDist = dot(toOrigin, dragLine);
            float2 closestPoint = dragOrigin + dragLine * projectedDist;
            float distToLine = length(position - closestPoint);
            
            // Only apply trail effect near the line connecting origin and current
            float trailWidth = 0.03 * (1.0 + 0.5 * dragSpeed);
            float trailFactor = exp(-pow(distToLine / trailWidth, 2.0));
            
            // Trail only exists between origin and current position
            float trailSegment = smoothstep(-0.01, 0.0, projectedDist) * 
                                 smoothstep(dragLength + 0.01, dragLength, projectedDist);
            
            // Create stretchy connection with organic variation
            float trailStrength = trailFactor * trailSegment * 0.5 * tensionFactor;
            trailStrength *= (0.7 + 0.3 * noise(position * 25.0 + projectedDist));
            
            // Apply trail displacement toward the drag line
            float2 toLineDir = normalize(closestPoint - position);
            displacement += dragLine * trailStrength * dragLength * 0.3;
            displacement += toLineDir * trailStrength * distToLine * 2.0;
        }
    } else {
        // ===== RETURNING TO REST - ENHANCED WATER PHYSICS =====
        
        // Calculate return vector
        float2 returnVector = -dragVector;
        float returnSpeed = length(dragVector) * 0.8;
        
        // Return is strongest at current position and origin
        float currentPosReturnFactor = exp(-pow(distToDragPoint / (dropletRadius * 1.5), 2.0));
        float originReturnFactor = exp(-pow(distToOrigin / (dropletRadius * 1.2), 2.0));
        
        // Combined return factor with more emphasis on current position
        float returnFactor = currentPosReturnFactor * 0.7 + originReturnFactor * 0.3;
        returnFactor *= distanceScale; // Limit effect range
        
        // Calculate return force - stronger with distance
        float returnStrength = returnSpeed * returnFactor * tensionFactor;
        
        // 1. Primary return movement
        displacement = normalize(returnVector) * returnStrength;
        
        // 2. Enhanced wobble animation for realistic water jiggle
        float wobbleFreq = 20.0 + returnSpeed * 10.0;
        float wobblePhase = (distToDragPoint + distToOrigin) * wobbleFreq - returnSpeed * 5.0;
        float wobbleDecay = exp(-returnSpeed * 0.5);
        float wobbleStrength = sin(wobblePhase) * exp(-returnSpeed) * 0.2 * returnFactor;
        
        // Apply wobble in multiple directions for more realistic water movement
        displacement += normalize(toDragPoint) * wobbleStrength;
        displacement += float2(-toDragPoint.y, toDragPoint.x) * wobbleStrength * 0.7;
    }
    
    // Apply final distance-based falloff for localized effect
    return displacement * distanceScale;
}

// Main function
half4 main(float2 fragCoord) {
    // Base coordinates
    float2 uv = fragCoord / iResolution;
    float2 center = float2(0.5, 0.5);
    float2 position = uv - center; // Position from center (-0.5 to 0.5)
    float baseDist = length(position);
    float angle = atan(position.y, position.x);
    
    // Base bubble parameters
    float baseRadius = 0.45;
    float time = iTime;
    
    // Speed now directly controls all time-based movement
    float speed = frequency * 5.0; // Amplify frequency for more visible speed changes
    
    // ===== CALCULATE DEFORMATIONS =====
    float2 deformation = float2(0.0);
    
    // 1. Original surface tension animation - always active for fluid look
    if (iTime > 0.0) {
        deformation += surfaceTension(position, time, speed);
    }
    
    // 2. Water drop effect from tap
    if (tapActive > 0.5) {
        float2 tapPos = float2(tapX, tapY);
        float2 tapDeform = waterDropEffect(position, tapPos, tapTime, tapIntensity);
        deformation += tapDeform;
    }
    
    // 3. Drag effect with surface tension physics
    float2 dragOffset = float2(dragX, dragY);
    if (length(dragOffset) > 0.001) {
        float2 dragStartPos = float2(dragStartX, dragStartY);
        float2 dragDeform = surfaceTensionDrag(position, float2(dragX, dragY), dragActive, dragStartPos);
        deformation += dragDeform;
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
    if (baseDist < dynamicRadius + edgeWidth && refractionEnabled > 0.5) {
        float2 dir = normalize(position);
        float bendFactor = smoothstep(0.0, dynamicRadius, baseDist / dynamicRadius);
        flippedUV += dir * bendFactor * 0.15;
        
        if (iTime > 0.0) {
            flippedUV += deformation * 0.3;
        }
    }
    
    // ===== ENHANCED EDGE SAMPLING =====
    // Sample colors around the edge for the glow
    float2 innerEdgeOffset = normalize(position) * (dynamicRadius - edgeWidth * 0.5);
    float2 innerEdgeUV = innerEdgeOffset + center;
    
    // Scale UVs to match image resolution
    float2 finalUV = distortedUV * iImageResolution;
    float2 flippedScaledUV = flippedUV * iImageResolution;
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
    // Sample content colors for the glow
    half4 edgeContentColor = half4(0.0);
    for (int i = -2; i <= 2; i++) {
        for (int j = -2; j <= 2; j++) {
            float2 sampleOffset = float2(float(i), float(j)) * 2.0;
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
        float highlight2 = pow(max(0.0, 1.0 - abs(baseDist - 0.3) / 0.05), 6.0);
        color.rgb += half3(1.0, 1.0, 1.0) * highlight2 * lightStrength * 0.4;
    }
    
    // ===== COMPOSITE =====
    // Apply slight darkening at the very edge to emphasize the glass thickness
    float edgeDarkening = smoothstep(0.01, 0.0, abs(distToBubble)) * 0.3;
    color.rgb *= 1.0 - edgeDarkening;
    
    // Apply the final mask with relatively sharp edge
    return color * maskBase;
}
""".trimIndent()
