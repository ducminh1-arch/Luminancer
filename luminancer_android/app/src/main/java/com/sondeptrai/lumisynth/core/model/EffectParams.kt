package com.sondeptrai.lumisynth.core.model

/**
 * Single Source of Truth for all Synthesizer Parameters on Android
 */
data class EffectParams(
    val source: SourceMode = SourceMode.CAMERA,
    val channel: ChannelMode = ChannelMode.LIGHT,
    val colorMode: ColorMode = ColorMode.PULSE,
    val blendMode: BlendMode = BlendMode.MIX,
    val aspect: AspectRatioMode = AspectRatioMode.FILL,

    // Core sliders
    val threshold: Float = 0.5f,
    val decay: Float = 0.88f,
    val trails: Float = 0.80f,
    val softness: Float = 0.08f,
    val pulseSpeed: Float = 1.5f,

    // Feedback Motion & Gestures (Default zoom = 1.065f creates wide, dramatic expanding concentric halo rings like the skull)
    val zoom: Float = 1.065f,
    val rotation: Float = 0.0f,
    val spin: Float = 0.0f,
    val trailOffsetX: Float = 0.0f,
    val trailOffsetY: Float = 0.0f,
    val motionPresetIndex: Int = 0,

    // Hardware states
    val isFrontCamera: Boolean = false,
    val isTorchOn: Boolean = false,
    val isMuted: Boolean = false
) {
    fun resetGestures(): EffectParams {
        return copy(
            zoom = 1.0f,
            rotation = 0.0f,
            spin = 0.0f,
            trails = 0.75f,
            trailOffsetX = 0.0f,
            trailOffsetY = 0.0f,
            motionPresetIndex = 3
        )
    }

    fun nextMotionPreset(): EffectParams {
        return when ((motionPresetIndex + 1) % 4) {
            0 -> copy(zoom = 1.065f, rotation = 0.0f, spin = 0.0f, motionPresetIndex = 0)     // HALO TUNNEL (Concentric expanding rings)
            1 -> copy(zoom = 1.055f, rotation = 0.055f, spin = 0.35f, motionPresetIndex = 1)  // SPIRAL VORTEX (3D spiral tunnel)
            2 -> copy(zoom = 0.935f, rotation = -0.045f, spin = -0.3f, motionPresetIndex = 2) // BLACK HOLE (Contracting vortex)
            else -> copy(zoom = 1.0f, rotation = 0.0f, spin = 0.0f, motionPresetIndex = 3)    // NEUTRAL (Flat)
        }
    }

    val motionLabel: String
        get() = when {
            kotlin.math.abs(rotation) > 0.015f || kotlin.math.abs(spin) > 0.05f -> "SPIRAL VORTEX"
            zoom > 1.02f -> "HALO TUNNEL"
            zoom < 0.98f -> "BLACK HOLE"
            else -> "NEUTRAL"
        }

    val statusString: String
        get() = "${channel.label.lowercase()} / ${colorMode.label.lowercase()} / ${blendMode.label.lowercase()}"
}
