
# Luminancer

A real-time analog video synthesizer and light painting visual effects app, built natively for **Android**, **iOS** and the **Web**.

Point the camera (or import a video) and shape it live with luminance thresholding, feedback trails, color effects and touch gestures.

## Repository structure

| Folder | Platform | Tech stack |
| --- | --- | --- |
| [`luminancer_android`](luminancer_android) | Android 8.0+ | Kotlin, Jetpack Compose, CameraX, Media3 ExoPlayer, OpenGL ES 3.0 |
| [`luminancer_ios`](luminancer_ios) | iOS 15+ (iPhone & iPad) | Swift, SwiftUI, AVFoundation, Metal |
| [`luminancer_web`](luminancer_web) | Browser | JavaScript, WebGL, Vite |

## Features

- **Analog synth simulation**: real-time feedback loops, color strobe pulses and luminance thresholding
- **GPU pipeline**: 30/60 FPS 1080p rendering (OpenGL ES on Android, Metal on iOS, WebGL on Web)
- **Ping-pong feedback trails**: displaced, scaled and rotated recursive trails with analog decay
- **Color FX & modes**
  - Color: Pulse, Rainbow Halo, Warm White, B&W, Neon Psychedelic, Static
  - Channel: Light, RGB Halo, Edge Glow, Color Slice
  - Blend: Mix, Neg, Add, Invert, Screen
- **Gesture control**
  - 1-finger pan: trail offset and drift direction
  - Pinch: feedback loop zoom
  - Rotate: feedback loop rotation and spin
  - Double tap: reset transforms
- **Live camera & video import**: front/back camera, torch, import videos up to 1080p with loop and playback controls
- **Recording & snapshot**: save photos and videos directly to the device

## Download

A debug build of the Android app is included in this repository:

[`luminancer_android/app/build/outputs/apk/debug/app-debug.apk`](luminancer_android/app/build/outputs/apk/debug/app-debug.apk)

Enable "Install unknown apps" on your device to install it.

## Build & run

### Android
Requirements: Android Studio (Iguana or newer), Android SDK 34, Gradle 8.4+, a device with OpenGL ES 3.0.

```bash
cd luminancer_android
./gradlew assembleDebug
```

Or open the folder in Android Studio and click **Run 'app'**.

### iOS
Requirements: Xcode 15+, Swift 5.9+. A physical device is recommended for the camera and Metal shaders.

Open `luminancer_ios/LumiSynth.xcodeproj` in Xcode, choose your device and run.

### Web
Requirements: Node.js.

```bash
cd luminancer_web
npm install
npm run dev
```

Production build: `npm run build`.

## More details

See the README in each platform folder for the full project structure:

- [Android README](luminancer_android/README.md)
- [iOS README](luminancer_ios/README.md)