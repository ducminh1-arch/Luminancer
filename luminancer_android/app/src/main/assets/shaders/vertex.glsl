#version 300 es
precision highp float;

layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uSTMatrix;
uniform vec2 uAspectScale;

out vec2 vTexCoord;
out vec2 vSourceTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoord;
    
    // Center-crop aspect ratio correction: scale around center (0.5, 0.5) to preserve 1:1 true physical proportions
    vec2 correctedCoord = vec2(0.5) + (aTexCoord - vec2(0.5)) * uAspectScale;
    
    // Transform by SurfaceTexture transformation matrix (handles camera orientation & mirror natively)
    vec4 transformed = uSTMatrix * vec4(correctedCoord.x, correctedCoord.y, 0.0, 1.0);
    vSourceTexCoord = transformed.xy;
}
