import Foundation
import CoreGraphics
import simd

/// Single Source of Truth for all Synthesizer Parameters
public struct EffectParams: Equatable {
    // Pipeline modes
    public var source: SourceMode = .camera
    public var channel: ChannelMode = .light
    public var colorMode: ColorMode = .pulse
    public var blendMode: BlendMode = .mix
    public var aspect: AspectRatioMode = .fill
    
    // Core synthesis sliders
    /// Luminance cutoff limit (0.0 to 1.0)
    public var threshold: Float = 0.5
    
    /// Feedback trail lingering decay multiplier (0.0 to 0.99)
    public var decay: Float = 0.88
    
    /// Feedback trail opacity/intensity (0.0 to 1.0)
    public var trails: Float = 0.80
    
    /// Threshold edge softness/smoothing window (0.01 to 0.5)
    public var softness: Float = 0.08
    
    /// Color strobe cycle rate / speed multiplier (0.1 to 5.0)
    public var pulseSpeed: Float = 1.5
    
    // Feedback loop geometric transforms (from gestures & motion presets)
    /// Scale factor for recursive feedback loop (default 1.065 creates wide expanding concentric halo rings like the skull)
    public var zoom: Float = 1.065
    
    /// Rotation angle in radians (-π to π)
    public var rotation: Float = 0.0
    
    /// Continuous angular spin speed per second (-1.0 to 1.0)
    public var spin: Float = 0.0
    
    /// Normalized 2D displacement offset from pan gesture (-1.0 to 1.0)
    public var trailOffset: SIMD2<Float> = SIMD2<Float>(0.0, 0.0)
    
    /// Motion preset index (0: Halo Tunnel, 1: Spiral Vortex, 2: Black Hole, 3: Neutral)
    public var motionPresetIndex: Int = 0
    
    // Toggles
    public var isMuted: Bool = false
    public var isFrontCamera: Bool = false
    public var isTorchOn: Bool = false
    public var invertLuminance: Bool = false
    
    public init() {}
    
    /// Reset gesture-based transformations back to unity/neutral
    public mutating func resetGestures() {
        zoom = 1.0
        rotation = 0.0
        spin = 0.0
        trails = 0.75
        trailOffset = SIMD2<Float>(0.0, 0.0)
        motionPresetIndex = 3
    }
    
    /// Cycle through signature Luminancer feedback motion presets
    public mutating func nextMotionPreset() {
        motionPresetIndex = (motionPresetIndex + 1) % 4
        switch motionPresetIndex {
        case 0: // HALO TUNNEL (Concentric expanding rings)
            zoom = 1.065
            rotation = 0.0
            spin = 0.0
        case 1: // SPIRAL VORTEX (3D spiral tunnel)
            zoom = 1.055
            rotation = 0.055
            spin = 0.35
        case 2: // BLACK HOLE (Contracting vortex)
            zoom = 0.935
            rotation = -0.045
            spin = -0.3
        default: // NEUTRAL (Flat)
            zoom = 1.0
            rotation = 0.0
            spin = 0.0
        }
    }
    
    public var motionLabel: String {
        if abs(rotation) > 0.015 || abs(spin) > 0.05 {
            return "SPIRAL VORTEX"
        } else if zoom > 1.02 {
            return "HALO TUNNEL"
        } else if zoom < 0.98 {
            return "BLACK HOLE"
        } else {
            return "NEUTRAL"
        }
    }
    
    /// Reset all synthesis parameters to factory default
    public mutating func resetToFactoryDefaults() {
        source = .camera
        channel = .light
        colorMode = .pulse
        blendMode = .mix
        aspect = .fill
        threshold = 0.5
        decay = 0.88
        trails = 0.80
        softness = 0.08
        pulseSpeed = 1.5
        resetGestures()
    }
    
    /// Formatted status readout for UI display (e.g. "light / pulse / mix")
    public var statusString: String {
        "\(channel.rawValue.lowercased()) / \(colorMode.rawValue.lowercased()) / \(blendMode.rawValue.lowercased())"
    }
}
