# Luminancer for iOS

Luminancer is a real-time analog video synthesizer and light painting visual effects application for iOS (iPhone & iPad), built with Swift, SwiftUI, AVFoundation, and Metal.

## Features
- **Analog Synth Simulation**: Real-time feedback loops, color strobe pulses, and luminance thresholding.
- **Metal GPU Pipeline**: High performance 30/60 FPS 1080p rendering with zero-copy texture streaming (`CVMetalTextureCache`).
- **Ping-Pong Feedback Trails**: Displaced, scaled, and rotated recursive feedback trails with analog decay.
- **Color FX & Modes**:
  - Thresholding with soft edge luminance detection
  - Color Modes: Pulse, Rainbow Halo, Warm White, B&W, Neon Psychedelic, Static
  - Channel Modes: Light, RGB Halo, Edge Glow, Color Slice
  - Blend Modes: Mix, Neg, Add, Invert, Screen
- **Gesture Control**:
  - 1-Finger Pan: Trail offset and drift direction
  - Pinch: Feedback loop zoom
  - Rotate: Feedback loop rotation and spin
  - Double Tap: Reset transforms to default
- **Live Camera & Video Import**:
  - Live capture with Front/Back camera and torch support
  - Import videos up to 1080p from Photos library with loop and playback controls
- **Recording & Snapshot**:
  - Hardware accelerated H.264 video recording with `AVAssetWriter`
  - High resolution photo snapshots
  - Direct export to Photos library

## Requirements
- iOS 15.0+ (iPhone & iPad)
- Xcode 15.0+ / Swift 5.9+
- Physical iOS device recommended for live camera and Metal GPU shaders

## Project Structure
```
luminancer_ios/
├── App/
│   ├── LumiSynthApp.swift
│   └── Info.plist
├── Core/
│   ├── Models/
│   │   ├── EffectParams.swift
│   │   └── SynthEnums.swift
│   ├── Camera/
│   │   └── CameraService.swift
│   ├── VideoImport/
│   │   └── VideoPlayerService.swift
│   ├── Renderer/
│   │   ├── MetalRenderer.swift
│   │   ├── PingPongBuffer.swift
│   │   ├── ShaderTypes.h
│   │   └── Shaders.metal
│   └── Recorder/
│       └── VideoRecorder.swift
├── Features/
│   └── Synth/
│       ├── SynthViewModel.swift
│       ├── SynthView.swift
│       ├── ControlsView.swift
│       ├── GesturesView.swift
│       └── HelpOverlayView.swift
└── Resources/
    └── Assets.xcassets
```
