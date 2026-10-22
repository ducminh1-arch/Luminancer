#ifndef ShaderTypes_h
#define ShaderTypes_h

#include <simd/simd.h>

typedef enum BufferIndices {
    BufferIndexMeshPositions = 0,
    BufferIndexMeshGenerics  = 1,
    BufferIndexUniforms      = 2
} BufferIndices;

typedef enum TextureIndices {
    TextureIndexSource      = 0,
    TextureIndexFeedback    = 1
} TextureIndices;

typedef struct {
    vector_float2 position;
    vector_float2 texCoord;
} Vertex;

typedef struct {
    // Transforms
    vector_float2 trailOffset;
    float zoom;
    float rotation;
    
    // Synthesis parameters
    float threshold;
    float softness;
    float decay;
    float trailsIntensity;
    float time;
    float pulseSpeed;
    
    // Modes (integers matching enums)
    int channelMode;   // 0: LIGHT, 1: RGB_HALO, 2: EDGE_GLOW, 3: COLOR_SLICE
    int colorMode;     // 0: PULSE, 1: RAINBOW, 2: WARM, 3: BW, 4: NEON, 5: STATIC
    int blendMode;     // 0: MIX, 1: NEG, 2: ADD, 3: INVERT, 4: SCREEN
    int isFrontCamera; // 0 or 1
    
    // Dimensions
    vector_float2 resolution;
    vector_float2 aspectScale;
} SynthUniforms;

#endif /* ShaderTypes_h */
