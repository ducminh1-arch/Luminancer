package com.sondeptrai.lumisynth.core.model

enum class SourceMode(val label: String) {
    CAMERA("CAM"),
    VIDEO("VID")
}

enum class ChannelMode(val label: String, val modeIndex: Int) {
    LIGHT("LIGHT", 0),
    RGB_HALO("RGB HALO", 1),
    EDGE_GLOW("EDGE GLOW", 2),
    COLOR_SLICE("SLICE", 3)
}

enum class ColorMode(val label: String, val modeIndex: Int) {
    PULSE("PULSE", 0),
    RAINBOW("RAINBOW", 1),
    WARM("WARM", 2),
    BW("B&W", 3),
    NEON("NEON", 4),
    STATIC("STATIC", 5)
}

enum class BlendMode(val label: String, val modeIndex: Int) {
    MIX("MIX", 0),
    NEG("NEG", 1),
    ADD("ADD", 2),
    INVERT("INVERT", 3),
    SCREEN("SCREEN", 4)
}

enum class AspectRatioMode(val label: String) {
    FILL("FILL"),
    FIT("FIT"),
    SQUARE("1:1"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_9_16("9:16")
}
