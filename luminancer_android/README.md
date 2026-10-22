# LumiSynth (Luminancer Clone for Android)

LumiSynth is a real-time analog video synthesizer and light painting visual effects application for Android, built with Kotlin, Jetpack Compose, CameraX, Media3 ExoPlayer, and OpenGL ES 3.0.

## Features
- **Analog Synth Simulation**: Real-time feedback loops, color strobe pulses, and luminance thresholding.
- **OpenGL ES 3.0 GPU Pipeline**: Hardware-accelerated 30/60 FPS 1080p rendering with OES external texture streaming.
- **Ping-Pong Feedback Trails**: Double-buffered Framebuffer Objects (FBO) for displaced, scaled, and rotated recursive feedback trails with analog decay.
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
  - Live capture via CameraX with Front/Back camera and torch support
  - Import videos up to 1080p from Photos library with loop and playback controls via Media3 ExoPlayer
- **Recording & Snapshot**:
  - Snapshot capture directly from OpenGL framebuffer to PNG/JPEG
  - Saves directly to device MediaStore (Pictures/Movies)

## Requirements
- Android SDK 26+ (Android 8.0 Oreo or newer)
- Target SDK 34+
- OpenGL ES 3.0 support
- Kotlin 1.9.24+ / Gradle 8.4+
- Android Studio Iguana / Jellyfish / Koala or newer

## Project Structure
```
luminancer_android/
├── app/
│   ├── src/main/
│   │   ├── java/com/sondeptrai/lumisynth/
│   │   │   ├── MainActivity.kt
│   │   │   ├── core/
│   │   │   │   ├── model/ (EffectParams.kt, SynthEnums.kt)
│   │   │   │   ├── camera/ (CameraController.kt)
│   │   │   │   ├── video/ (VideoSource.kt)
│   │   │   │   ├── render/ (GLRenderer.kt, PingPongFbo.kt, ShaderProgram.kt)
│   │   │   │   └── recorder/ (VideoRecorder.kt)
│   │   │   └── ui/synth/ (SynthScreen.kt, SynthViewModel.kt, SynthControls.kt, HelpDialog.kt)
│   │   ├── assets/shaders/
│   │   │   ├── vertex.glsl
│   │   │   └── fragment_main.glsl
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## How to Build & Run
1. Open the project directory `luminancer_android` in **Android Studio**.
2. Allow Gradle sync to complete and install required Android SDK components (API 34).
3. Connect an Android device with USB debugging enabled.
4. Click **Run 'app'** or execute:
   ```bash
   ./gradlew assembleDebug
   ```
