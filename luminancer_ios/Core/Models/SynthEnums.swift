import Foundation

/// Video feed source: live camera or imported video file
public enum SourceMode: String, CaseIterable, Identifiable {
    case camera = "CAM"
    case video = "VID"
    
    public var id: String { rawValue }
    
    public var title: String {
        switch self {
        case .camera: return "CAMERA"
        case .video: return "VIDEO"
        }
    }
}

/// Luminance channel processing modes (>= 4 modes)
public enum ChannelMode: String, CaseIterable, Identifiable {
    case light = "LIGHT"
    case rgbHalo = "RGB HALO"
    case edgeGlow = "EDGE GLOW"
    case colorSlice = "SLICE"
    
    public var id: String { rawValue }
    
    public var description: String {
        switch self {
        case .light: return "Threshold Light Overload"
        case .rgbHalo: return "Chromatic Halo Rings"
        case .edgeGlow: return "High-Pass Edge Glow"
        case .colorSlice: return "Quantized Color Slicing"
        }
    }
}

/// Color synthesiser modes (>= 4 modes)
public enum ColorMode: String, CaseIterable, Identifiable {
    case pulse = "PULSE"
    case rainbow = "RAINBOW"
    case warmWhite = "WARM"
    case blackWhite = "B&W"
    case neon = "NEON"
    case staticColor = "STATIC"
    
    public var id: String { rawValue }
    
    public var description: String {
        switch self {
        case .pulse: return "Sine Wave Color Strobe"
        case .rainbow: return "Cycling Spectrum Prism"
        case .warmWhite: return "Warm Analog Phosphor"
        case .blackWhite: return "Monochrome Cathode"
        case .neon: return "Vibrant Cyber Neon"
        case .staticColor: return "Selected Solid Tint"
        }
    }
}

/// Frame composite & blend modes (>= 4 modes)
public enum BlendMode: String, CaseIterable, Identifiable {
    case mix = "MIX"
    case neg = "NEG"
    case add = "ADD"
    case invert = "INVERT"
    case screen = "SCREEN"
    
    public var id: String { rawValue }
    
    public var description: String {
        switch self {
        case .mix: return "Alpha Crossfade"
        case .neg: return "Negative Differential"
        case .add: return "Additive Light Accumulation"
        case .invert: return "Luminance Inversion"
        case .screen: return "Screen Glow Composite"
        }
    }
}

/// Aspect ratio framing modes
public enum AspectRatioMode: String, CaseIterable, Identifiable {
    case fill = "FILL"
    case fit = "FIT"
    case square1x1 = "1:1"
    case ratio16x9 = "16:9"
    case ratio4x3 = "4:3"
    case ratio9x16 = "9:16"
    
    public var id: String { rawValue }
}
