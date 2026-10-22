#version 300 es
#extension GL_OES_EGL_image_external_essl3 : enable
#extension GL_OES_EGL_image_external : enable

precision highp float;

in vec2 vTexCoord;
in vec2 vSourceTexCoord;

// MRT Outputs:
// Target 0: outFeedback (pure synthesized trails stored into Ping-Pong FBO)
// Target 1: outComposite (blended with original source, for screen blit and recorder)
layout(location = 0) out vec4 outFeedback;
layout(location = 1) out vec4 outComposite;

// Source stream (CameraX or ExoPlayer via SurfaceTexture)
uniform samplerExternalOES uSourceTexture;

// Feedback history stream (2D Ping-Pong FBO)
uniform sampler2D uFeedbackTexture;

// Synthesis parameters
uniform float uThreshold;
uniform float uSoftness;
uniform float uDecay;
uniform float uTrailsIntensity;
uniform float uTime;
uniform float uPulseSpeed;

// Modes
uniform int uChannelMode;  // 0: LIGHT, 1: RGB_HALO, 2: EDGE_GLOW, 3: COLOR_SLICE
uniform int uColorMode;    // 0: PULSE, 1: RAINBOW, 2: WARM, 3: BW, 4: NEON, 5: STATIC
uniform int uBlendMode;    // 0: MIX, 1: NEG, 2: ADD, 3: INVERT, 4: SCREEN

// Geometric transforms from gestures
uniform vec2 uTrailOffset;
uniform float uZoom;
uniform float uRotation;
uniform vec2 uResolution;

// Color math helpers
float rgbToLuminance(vec3 rgb) {
    return dot(rgb, vec3(0.299, 0.587, 0.114));
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 uv = vTexCoord;
    vec4 srcColor = texture(uSourceTexture, vSourceTexCoord);
    vec3 srcRGB = srcColor.rgb;
    
    // 1. Calculate luminance
    float Y = rgbToLuminance(srcRGB);
    
    // 2. Luminance Threshold Soft Mask
    float halfSoft = max(0.005, uSoftness * 0.5);
    float mask = smoothstep(uThreshold - halfSoft, uThreshold + halfSoft, Y);
    
    // 3. Channel Light Mode Processing
    vec3 channelColor = srcRGB;
    vec2 pixelStep = 1.0 / max(uResolution, vec2(1.0));
    
    if (uChannelMode == 1) {
        // RGB Halo / Chromatic Aberration fringe on edges
        vec2 rUV = vSourceTexCoord + vec2(pixelStep.x * 3.0, 0.0);
        vec2 bUV = vSourceTexCoord - vec2(pixelStep.x * 3.0, 0.0);
        float r = texture(uSourceTexture, rUV).r;
        float g = srcColor.g;
        float b = texture(uSourceTexture, bUV).b;
        channelColor = vec3(r, g, b);
    } else if (uChannelMode == 2) {
        // Edge Glow (Sobel filter on luminance)
        float lumLeft  = rgbToLuminance(texture(uSourceTexture, vSourceTexCoord - vec2(pixelStep.x, 0.0)).rgb);
        float lumRight = rgbToLuminance(texture(uSourceTexture, vSourceTexCoord + vec2(pixelStep.x, 0.0)).rgb);
        float lumUp    = rgbToLuminance(texture(uSourceTexture, vSourceTexCoord - vec2(0.0, pixelStep.y)).rgb);
        float lumDown  = rgbToLuminance(texture(uSourceTexture, vSourceTexCoord + vec2(0.0, pixelStep.y)).rgb);
        float edge = clamp(length(vec2(lumRight - lumLeft, lumDown - lumUp)) * 4.0, 0.0, 1.0);
        mask = max(mask, edge);
    } else if (uChannelMode == 3) {
        // Quantized Color Slicing / Solarization
        channelColor = floor(srcRGB * 4.0) / 4.0;
        mask = smoothstep(uThreshold - halfSoft, uThreshold + halfSoft, rgbToLuminance(channelColor));
    }
    
    // 4. Color Synthesis FX
    vec3 fxColor = vec3(0.0);
    float t = uTime * uPulseSpeed;
    
    if (uColorMode == 0) { // PULSE
        float hue = fract(t * 0.2 + Y * 0.5);
        float pulseWave = 0.5 + 0.5 * sin(t * 4.0);
        fxColor = hsv2rgb(vec3(hue, 0.85, 0.9 + 0.1 * pulseWave)) * (mask * 1.4);
    } else if (uColorMode == 1) { // RAINBOW HALO
        float hue = fract(Y * 2.5 + t * 0.35 + length(uv - 0.5) * 1.5);
        fxColor = hsv2rgb(vec3(hue, 0.95, 1.0)) * mask;
    } else if (uColorMode == 2) { // WARM WHITE (Analog Phosphor)
        vec3 warmTint = vec3(1.0, 0.82, 0.55);
        float warmWave = 1.0 + 0.35 * sin(t * 3.5 + Y * 2.0);
        fxColor = warmTint * (mask * 1.2 * warmWave);
    } else if (uColorMode == 3) { // BLACK & WHITE
        float bwWave = 0.85 + 0.25 * sin(t * 4.0);
        float bw = step(0.5, mask);
        fxColor = vec3(bw * bwWave);
    } else if (uColorMode == 4) { // NEON PSYCHEDELIC
        float hue = fract(t * 0.15 + (uv.x + uv.y) * 0.8);
        vec3 neonBase = hsv2rgb(vec3(hue, 1.0, 1.0));
        fxColor = mix(neonBase, vec3(1.0, 0.1, 0.7), sin(t * 2.0) * 0.5 + 0.5) * mask;
    } else { // STATIC COLOR (Electric Teal)
        vec3 staticTint = vec3(0.1, 0.9, 0.8);
        float tealWave = 1.0 + 0.3 * sin(t * 3.0 + length(uv - 0.5) * 2.0);
        fxColor = staticTint * (mask * tealWave);
    }
    
    // 5. Feedback Trails & Geometric Distortion (Pinch Zoom, Rotation, Pan Offset)
    vec2 center = vec2(0.5, 0.5);
    vec2 feedbackUV = uv - center;
    
    // Apply pan offset
    feedbackUV -= uTrailOffset;
    
    // Apply rotation
    float cosAngle = cos(uRotation);
    float sinAngle = sin(uRotation);
    feedbackUV = vec2(
        feedbackUV.x * cosAngle - feedbackUV.y * sinAngle,
        feedbackUV.x * sinAngle + feedbackUV.y * cosAngle
    );
    
    // Apply recursive zoom scaling
    feedbackUV *= (1.0 / max(0.2, uZoom));
    feedbackUV += center;
    
    // Sample previous frame from feedback texture
    vec4 prevSample = texture(uFeedbackTexture, feedbackUV);
    
    // Clamp sample outside valid [0, 1] bounds
    if (feedbackUV.x < 0.0 || feedbackUV.x > 1.0 || feedbackUV.y < 0.0 || feedbackUV.y > 1.0) {
        prevSample = vec4(0.0);
    }
    
    // Apply decay multiplier
    vec3 prevDecayed = prevSample.rgb * uDecay;
    
    // Pure synthesized feedback trails (BEFORE blend)
    vec3 synthesizedOutput = max(fxColor, prevDecayed * uTrailsIntensity);
    
    // 6. Final Composite & Blend Pass with original source frame
    vec3 finalRGB = srcRGB;
    
    if (uBlendMode == 0) { // MIX
        float blendWeight = clamp(mask + length(synthesizedOutput) * 0.6, 0.0, 1.0);
        finalRGB = mix(srcRGB, synthesizedOutput, blendWeight);
    } else if (uBlendMode == 1) { // NEG
        finalRGB = abs(srcRGB - synthesizedOutput);
    } else if (uBlendMode == 2) { // ADD
        finalRGB = min(srcRGB + synthesizedOutput, vec3(1.0));
    } else if (uBlendMode == 3) { // INVERT
        vec3 invertedSrc = vec3(1.0) - srcRGB;
        finalRGB = mix(srcRGB, invertedSrc + synthesizedOutput, clamp(mask * 1.5, 0.0, 1.0));
    } else if (uBlendMode == 4) { // SCREEN
        finalRGB = vec3(1.0) - (vec3(1.0) - srcRGB) * (vec3(1.0) - synthesizedOutput);
    }
    
    // Target 0: Feedback texture gets synthesizedOutput
    outFeedback = vec4(clamp(synthesizedOutput, 0.0, 1.0), 1.0);
    // Target 1: Composite texture gets finalRGB (for screen & recording)
    outComposite = vec4(clamp(finalRGB, 0.0, 1.0), 1.0);
}
