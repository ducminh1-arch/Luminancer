#include <metal_stdlib>
#include "ShaderTypes.h"

using namespace metal;

// MARK: - Vertex I/O

struct VertexIn {
    float2 position [[attribute(0)]];
    float2 texCoord [[attribute(1)]];
};

struct VertexOut {
    float4 position [[position]];
    float2 texCoord;
};

// MARK: - MRT Fragment Output
// Color Attachment 0: feedbackOutput (pure synth light trails, stored into ping-pong buffer)
// Color Attachment 1: compositeOutput (blended with camera/video frame, used for screen & recording)
struct SynthFragmentOutput {
    float4 feedbackOutput  [[color(0)]];
    float4 compositeOutput [[color(1)]];
};

// MARK: - Color Math Utilities

float rgbToLuminance(float3 rgb) {
    return dot(rgb, float3(0.299, 0.587, 0.114));
}

float3 hsv2rgb(float3 c) {
    float4 K = float4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    float3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

// MARK: - Vertex Shader

vertex VertexOut synthVertexShader(const device Vertex *vertices [[buffer(BufferIndexMeshPositions)]],
                                   constant SynthUniforms &uniforms [[buffer(BufferIndexUniforms)]],
                                   uint vid [[vertex_id]]) {
    VertexOut out;
    float2 pos = vertices[vid].position;
    out.position = float4(pos, 0.0, 1.0);
    // Mirroring is handled natively by AVFoundation isVideoMirrored, no double-flip
    out.texCoord = vertices[vid].texCoord;
    return out;
}

// MARK: - Synthesizer MRT Fragment Shader

fragment SynthFragmentOutput synthFragmentShader(VertexOut in [[stage_in]],
                                                 texture2d<float> sourceTexture [[texture(TextureIndexSource)]],
                                                 texture2d<float> feedbackTexture [[texture(TextureIndexFeedback)]],
                                                 constant SynthUniforms &uniforms [[buffer(BufferIndexUniforms)]]) {
    constexpr sampler linearSampler(coord::normalized,
                                    address::clamp_to_edge,
                                    filter::linear);
    
    float2 uv = in.texCoord;
    // Aspect-ratio correction to preserve 1:1 true physical proportions without stretching
    float2 sourceUV = float2(0.5, 0.5) + (uv - float2(0.5, 0.5)) * uniforms.aspectScale;
    float4 srcColor = sourceTexture.sample(linearSampler, sourceUV);
    float3 srcRGB = srcColor.rgb;
    
    // 1. Calculate luminance
    float Y = rgbToLuminance(srcRGB);
    
    // 2. Luminance Threshold Soft Mask
    float halfSoft = max(0.005, uniforms.softness * 0.5);
    float mask = smoothstep(uniforms.threshold - halfSoft, uniforms.threshold + halfSoft, Y);
    
    // 3. Channel Light Mode Processing
    float3 channelColor = srcRGB;
    float2 pixelStep = 1.0 / max(uniforms.resolution, float2(1.0));
    
    if (uniforms.channelMode == 1) {
        // RGB Halo / Chromatic Aberration fringe on edges
        float2 rUV = sourceUV + float2(pixelStep.x * 3.0, 0.0);
        float2 bUV = sourceUV - float2(pixelStep.x * 3.0, 0.0);
        float r = sourceTexture.sample(linearSampler, rUV).r;
        float g = srcColor.g;
        float b = sourceTexture.sample(linearSampler, bUV).b;
        channelColor = float3(r, g, b);
    } else if (uniforms.channelMode == 2) {
        // Edge Glow (Sobel filter on luminance)
        float lumLeft  = rgbToLuminance(sourceTexture.sample(linearSampler, sourceUV - float2(pixelStep.x, 0.0)).rgb);
        float lumRight = rgbToLuminance(sourceTexture.sample(linearSampler, sourceUV + float2(pixelStep.x, 0.0)).rgb);
        float lumUp    = rgbToLuminance(sourceTexture.sample(linearSampler, sourceUV - float2(0.0, pixelStep.y)).rgb);
        float lumDown  = rgbToLuminance(sourceTexture.sample(linearSampler, sourceUV + float2(0.0, pixelStep.y)).rgb);
        float edge = clamp(length(float2(lumRight - lumLeft, lumDown - lumUp)) * 4.0, 0.0, 1.0);
        mask = max(mask, edge);
    } else if (uniforms.channelMode == 3) {
        // Quantized Color Slicing / Solarization
        channelColor = floor(srcRGB * 4.0) / 4.0;
        mask = smoothstep(uniforms.threshold - halfSoft, uniforms.threshold + halfSoft, rgbToLuminance(channelColor));
    }
    
    // 4. Color Synthesis FX
    float3 fxColor = float3(0.0);
    float time = uniforms.time * uniforms.pulseSpeed;
    
    switch (uniforms.colorMode) {
        case 0: { // PULSE
            float hue = fract(time * 0.2 + Y * 0.5);
            float pulseWave = 0.5 + 0.5 * sin(time * 4.0);
            fxColor = hsv2rgb(float3(hue, 0.85, 0.9 + 0.1 * pulseWave)) * (mask * 1.4);
            break;
        }
        case 1: { // RAINBOW HALO
            float hue = fract(Y * 2.5 + time * 0.35 + length(uv - 0.5) * 1.5);
            fxColor = hsv2rgb(float3(hue, 0.95, 1.0)) * mask;
            break;
        }
        case 2: { // WARM WHITE (Analog Phosphor)
            float3 warmTint = float3(1.0, 0.82, 0.55);
            float warmWave = 1.0 + 0.35 * sin(time * 3.5 + Y * 2.0);
            fxColor = warmTint * (mask * 1.2 * warmWave);
            break;
        }
        case 3: { // BLACK & WHITE
            float bwWave = 0.85 + 0.25 * sin(time * 4.0);
            float bw = step(0.5, mask);
            fxColor = float3(bw * bwWave);
            break;
        }
        case 4: { // NEON PSYCHEDELIC
            float hue = fract(time * 0.15 + (uv.x + uv.y) * 0.8);
            float3 neonBase = hsv2rgb(float3(hue, 1.0, 1.0));
            fxColor = mix(neonBase, float3(1.0, 0.1, 0.7), sin(time * 2.0) * 0.5 + 0.5) * mask;
            break;
        }
        case 5: // STATIC COLOR
        default: {
            float3 staticTint = float3(0.1, 0.9, 0.8); // Electric teal
            float tealWave = 1.0 + 0.3 * sin(time * 3.0 + length(uv - 0.5) * 2.0);
            fxColor = staticTint * (mask * tealWave);
            break;
        }
    }
    
    // 5. Feedback Trails & Geometric Distortion (Pinch Zoom, Rotation, Pan Offset)
    float2 center = float2(0.5, 0.5);
    float2 feedbackUV = uv - center;
    
    // Apply pan offset
    feedbackUV -= uniforms.trailOffset;
    
    // Apply rotation
    float cosAngle = cos(uniforms.rotation);
    float sinAngle = sin(uniforms.rotation);
    feedbackUV = float2(
        feedbackUV.x * cosAngle - feedbackUV.y * sinAngle,
        feedbackUV.x * sinAngle + feedbackUV.y * cosAngle
    );
    
    // Apply recursive zoom scaling
    feedbackUV *= (1.0 / max(0.1, uniforms.zoom));
    feedbackUV += center;
    
    // Sample previous frame from feedback texture
    float4 prevSample = feedbackTexture.sample(linearSampler, feedbackUV);
    
    // Clamp sample outside valid [0, 1] range to avoid edge smear
    if (feedbackUV.x < 0.0 || feedbackUV.x > 1.0 || feedbackUV.y < 0.0 || feedbackUV.y > 1.0) {
        prevSample = float4(0.0);
    }
    
    // Apply Decay
    float3 prevDecayed = prevSample.rgb * uniforms.decay;
    
    // Synthesized pure light trails (BEFORE blend)
    float3 synthesizedOutput = max(fxColor, prevDecayed * uniforms.trailsIntensity);
    
    // 6. Final Composite & Blend Pass with original source frame (for screen & recording)
    float3 finalRGB = srcRGB;
    
    switch (uniforms.blendMode) {
        case 0: { // MIX (Crossfade)
            float blendWeight = clamp(mask + length(synthesizedOutput) * 0.6, 0.0, 1.0);
            finalRGB = mix(srcRGB, synthesizedOutput, blendWeight);
            break;
        }
        case 1: { // NEG (Negative Differential)
            finalRGB = abs(srcRGB - synthesizedOutput);
            break;
        }
        case 2: { // ADD (Additive Overload)
            finalRGB = min(srcRGB + synthesizedOutput, float3(1.0));
            break;
        }
        case 3: { // INVERT (Luminance Inversion)
            float3 invertedSrc = float3(1.0) - srcRGB;
            finalRGB = mix(srcRGB, invertedSrc + synthesizedOutput, clamp(mask * 1.5, 0.0, 1.0));
            break;
        }
        case 4: { // SCREEN
            finalRGB = float3(1.0) - (float3(1.0) - srcRGB) * (float3(1.0) - synthesizedOutput);
            break;
        }
        default: {
            finalRGB = mix(srcRGB, synthesizedOutput, 0.5);
            break;
        }
    }
    
    SynthFragmentOutput output;
    // Target 0: Pure synthesized trails -> goes to Ping-Pong feedback texture
    output.feedbackOutput = float4(clamp(synthesizedOutput, 0.0, 1.0), 1.0);
    // Target 1: Blended composite frame -> goes to Composite texture for screen & recorder
    output.compositeOutput = float4(clamp(finalRGB, 0.0, 1.0), 1.0);
    return output;
}

// MARK: - Fast Blit Shaders (Passthrough for screen & recording)

vertex VertexOut blitVertexShader(const device Vertex *vertices [[buffer(BufferIndexMeshPositions)]],
                                  uint vid [[vertex_id]]) {
    VertexOut out;
    out.position = float4(vertices[vid].position, 0.0, 1.0);
    out.texCoord = vertices[vid].texCoord;
    return out;
}

fragment float4 blitFragmentShader(VertexOut in [[stage_in]],
                                   texture2d<float> imageTexture [[texture(0)]]) {
    constexpr sampler linearSampler(coord::normalized,
                                    address::clamp_to_edge,
                                    filter::linear);
    return imageTexture.sample(linearSampler, in.texCoord);
}
