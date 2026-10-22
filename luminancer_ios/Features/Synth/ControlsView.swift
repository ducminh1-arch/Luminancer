import SwiftUI
import PhotosUI

public struct ControlsView: View {
    @ObservedObject var viewModel: SynthViewModel
    @State private var selectedPickerItem: PhotosPickerItem? = nil
    
    public init(viewModel: SynthViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        GeometryReader { geo in
            let topInset = max(geo.safeAreaInsets.top, 20) + 6
            let bottomInset = max(geo.safeAreaInsets.bottom, 6)
            
            VStack(spacing: 8) {
                // MARK: - Top Header Analog Tabs
                topHeaderBar
                
                // MARK: - Status Oscilloscope Line
                statusReadoutLine
                
                Spacer()
                
                // MARK: - Video Controls (if video source)
                if viewModel.params.source == .video {
                    videoTransportBar
                }
                
                // MARK: - Bottom Sliders & Action Panel
                bottomDeckPanel
            }
            .padding(.horizontal, 12)
            .padding(.top, topInset)
            .padding(.bottom, bottomInset)
        }
    }
    
    // MARK: - Top Bar: 4 Core Synth Mode Tabs (Equal width, fully responsive)
    private var topHeaderBar: some View {
        HStack(spacing: 6) {
            Button(action: { viewModel.toggleSourceMode() }) {
                SynthTabButton(label: "SRC", value: viewModel.params.source.rawValue, color: .cyan)
            }
            .frame(maxWidth: .infinity)
            
            Button(action: { viewModel.cycleChannelMode() }) {
                SynthTabButton(label: "CHAN", value: viewModel.params.channel.rawValue, color: .teal)
            }
            .frame(maxWidth: .infinity)
            
            Button(action: { viewModel.cycleColorMode() }) {
                SynthTabButton(label: "COLOR", value: viewModel.params.colorMode.rawValue, color: .purple)
            }
            .frame(maxWidth: .infinity)
            
            Button(action: { viewModel.cycleBlendMode() }) {
                SynthTabButton(label: "BLEND", value: viewModel.params.blendMode.rawValue, color: .orange)
            }
            .frame(maxWidth: .infinity)
        }
    }
    
    // MARK: - Status Readout with Feedback Visualizer Box & Quick Utilities
    private var statusReadoutLine: some View {
        HStack(spacing: 8) {
            // Left: Motion Visualizer HUD + Mode readout
            FeedbackVisualizerBox(
                rotation: viewModel.params.rotation,
                zoom: viewModel.params.zoom,
                offsetX: viewModel.params.trailOffset.x,
                offsetY: viewModel.params.trailOffset.y,
                onTap: { viewModel.cycleMotionPreset() }
            )
            
            VStack(alignment: .leading, spacing: 1) {
                Text("\(viewModel.params.statusString) / -")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(Color(red: 1.0, green: 0.25, blue: 0.5))
                    .lineLimit(1)
                
                Text(viewModel.params.motionLabel)
                    .font(.system(size: 9, weight: .medium, design: .monospaced))
                    .foregroundColor(.cyan.opacity(0.75))
                    .lineLimit(1)
            }
            
            if viewModel.isRecording {
                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 8, height: 8)
                    Text(viewModel.recordingDurationString)
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(.red)
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.black.opacity(0.7))
                .cornerRadius(4)
            }
            
            Spacer()
            
            // Right: Utility icons (Torch, Switch Camera, Help) - Compact squares with 8pt spacing
            HStack(spacing: 8) {
                // Torch / Light
                Button(action: { viewModel.toggleTorch() }) {
                    Image(systemName: viewModel.params.isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(viewModel.params.isTorchOn ? .yellow : .gray)
                        .frame(width: 28, height: 28)
                        .background(viewModel.params.isTorchOn ? Color(red: 0.23, green: 0.21, blue: 0.07) : Color.black.opacity(0.75))
                        .cornerRadius(5)
                        .overlay(RoundedRectangle(cornerRadius: 5).stroke(viewModel.params.isTorchOn ? Color.yellow.opacity(0.8) : Color.white.opacity(0.25), lineWidth: 1))
                }
                
                // Camera Flip
                Button(action: { viewModel.switchCamera() }) {
                    Image(systemName: "camera.rotate.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 28, height: 28)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(5)
                        .overlay(RoundedRectangle(cornerRadius: 5).stroke(Color.white.opacity(0.25), lineWidth: 1))
                }
                
                // Help Button
                Button(action: { viewModel.showHelpOverlay.toggle() }) {
                    Text("?")
                        .font(.system(size: 13, weight: .black, design: .monospaced))
                        .foregroundColor(.cyan)
                        .frame(width: 28, height: 28)
                        .background(Color.black.opacity(0.75))
                        .cornerRadius(5)
                        .overlay(RoundedRectangle(cornerRadius: 5).stroke(Color.cyan.opacity(0.5), lineWidth: 1))
                }
            }
            .padding(.horizontal, 4)
        }
    }
    
    // MARK: - Video Transport
    private var videoTransportBar: some View {
        HStack(spacing: 16) {
            Button(action: { viewModel.rewindVideo() }) {
                Image(systemName: "backward.end.fill")
                    .font(.system(size: 16))
                    .foregroundColor(.white)
            }
            
            Button(action: { viewModel.togglePlayPause() }) {
                Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.cyan)
            }
            
            Button(action: { viewModel.toggleMute() }) {
                Image(systemName: viewModel.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                    .font(.system(size: 16))
                    .foregroundColor(viewModel.isMuted ? .gray : .white)
            }
            
            Spacer()
            
            // Video Picker
            PhotosPicker(selection: $selectedPickerItem, matching: .videos) {
                HStack(spacing: 4) {
                    Image(systemName: "photo.on.rectangle.angled")
                    Text("IMPORT")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.15))
                .foregroundColor(.white)
                .cornerRadius(6)
            }
            .onChange(of: selectedPickerItem) { newItem in
                guard let newItem = newItem else { return }
                Task {
                    if let movie = try? await newItem.loadTransferable(type: MovieTransferable.self) {
                        DispatchQueue.main.async {
                            viewModel.loadVideoURL(movie.url)
                        }
                    }
                }
            }
        }
        .padding(8)
        .background(Color.black.opacity(0.65))
        .cornerRadius(8)
    }
    
    // MARK: - Bottom Sliders & Action Row
    private var bottomDeckPanel: some View {
        VStack(spacing: 10) {
            // Sliders: Decay & Threshold
            VStack(spacing: 6) {
                HStack(spacing: 12) {
                    // DECAY SLIDER
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text("DECAY")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundColor(.cyan)
                            Spacer()
                            Text(String(format: "%.2f", viewModel.params.decay))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(.cyan.opacity(0.7))
                        }
                        Slider(value: $viewModel.params.decay, in: 0.0...0.98)
                            .accentColor(.cyan)
                    }
                    
                    // THRESHOLD SLIDER
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text("THRESHOLD")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundColor(.red)
                            Spacer()
                            Text(String(format: "%.2f", viewModel.params.threshold))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundColor(.red.opacity(0.7))
                        }
                        Slider(value: $viewModel.params.threshold, in: 0.0...1.0)
                            .accentColor(.red)
                    }
                }
                
                // TRAILS INTENSITY SLIDER
                HStack {
                    Text("TRAILS")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(.purple)
                    Slider(value: $viewModel.params.trails, in: 0.0...1.0)
                        .accentColor(.purple)
                    Text(String(format: "%.2f", viewModel.params.trails))
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.purple.opacity(0.7))
                }
            }
            
            // Bottom Action Deck: Reset, Snapshot, REC, Video Picker
            HStack(spacing: 20) {
                // Double tap reset trigger
                Button(action: { viewModel.onDoubleTapReset() }) {
                    VStack(spacing: 2) {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 14))
                        Text("RESET")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                    }
                    .foregroundColor(.white.opacity(0.8))
                    .frame(width: 48, height: 44)
                    .background(Color.black.opacity(0.4))
                    .cornerRadius(8)
                }
                
                // Snapshot button
                Button(action: { viewModel.captureSnapshot() }) {
                    VStack(spacing: 2) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 14))
                        Text("SNAP")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                    }
                    .foregroundColor(.white)
                    .frame(width: 48, height: 44)
                    .background(Color.black.opacity(0.4))
                    .cornerRadius(8)
                }
                
                Spacer()
                
                // REC BUTTON (Glowing analog circular button)
                Button(action: { viewModel.toggleRecording() }) {
                    ZStack {
                        Circle()
                            .stroke(viewModel.isRecording ? Color.red : Color.white.opacity(0.4), lineWidth: 3)
                            .frame(width: 58, height: 58)
                            .shadow(color: viewModel.isRecording ? Color.red.opacity(0.8) : Color.clear, radius: 8)
                        
                        Circle()
                            .fill(Color.red)
                            .frame(width: viewModel.isRecording ? 24 : 44, height: viewModel.isRecording ? 24 : 44)
                            .cornerRadius(viewModel.isRecording ? 6 : 22)
                            .animation(.easeInOut(duration: 0.2), value: viewModel.isRecording)
                    }
                }
                
                Spacer()
                
                // Video Picker
                PhotosPicker(selection: $selectedPickerItem, matching: .videos) {
                    VStack(spacing: 2) {
                        Image(systemName: "folder.fill")
                            .font(.system(size: 14))
                        Text("LOAD")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                    }
                    .foregroundColor(.white.opacity(0.8))
                    .frame(width: 48, height: 44)
                    .background(Color.black.opacity(0.4))
                    .cornerRadius(8)
                }
                .onChange(of: selectedPickerItem) { newItem in
                    guard let newItem = newItem else { return }
                    Task {
                        if let movie = try? await newItem.loadTransferable(type: MovieTransferable.self) {
                            DispatchQueue.main.async {
                                viewModel.loadVideoURL(movie.url)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 8)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color(white: 0.12).opacity(0.92))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )
        )
    }
}

// MARK: - Mini Synth Tab Button
private struct SynthTabButton: View {
    let label: String
    let value: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 1) {
            Text(label)
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(color)
                .lineLimit(1)
            Text(value)
                .font(.system(size: 11, weight: .black, design: .monospaced))
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 4)
        .padding(.vertical, 5)
        .background(Color.black.opacity(0.65))
        .cornerRadius(6)
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(color.opacity(0.45), lineWidth: 1)
        )
    }
}

// MARK: - Movie Transferable for PhotosPicker
struct MovieTransferable: Transferable {
    let url: URL
    
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let tempDir = FileManager.default.temporaryDirectory
            let targetURL = tempDir.appendingPathComponent(received.file.lastPathComponent)
            try? FileManager.default.removeItem(at: targetURL)
            try FileManager.default.copyItem(at: received.file, to: targetURL)
            return MovieTransferable(url: targetURL)
        }
    }
}

/// Luminancer Transformation Visualizer HUD Box (Concentric Nested Squares)
/// Visualizes feedback loop zoom, rotation angle, and drift offset in real-time.
public struct FeedbackVisualizerBox: View {
    let rotation: Float
    let zoom: Float
    let offsetX: Float
    let offsetY: Float
    let onTap: () -> Void
    
    public var body: some View {
        Button(action: onTap) {
            ZStack {
                Color.black
                
                let scaleMultiplier = CGFloat(min(max((zoom - 1.0) * 10.0 + 1.0, 0.5), 1.5))
                
                Group {
                    // Outer Cyan
                    Rectangle()
                        .stroke(Color(red: 0.0, green: 0.9, blue: 1.0), lineWidth: 2)
                        .frame(width: 34 * scaleMultiplier, height: 34 * scaleMultiplier)
                    
                    // Middle Magenta
                    Rectangle()
                        .stroke(Color(red: 0.88, green: 0.25, blue: 0.98), lineWidth: 2)
                        .frame(width: 26 * scaleMultiplier, height: 26 * scaleMultiplier)
                    
                    // Inner Amber
                    Rectangle()
                        .stroke(Color(red: 1.0, green: 0.84, blue: 0.0), lineWidth: 2)
                        .frame(width: 18 * scaleMultiplier, height: 18 * scaleMultiplier)
                    
                    // Core Red-Orange
                    Rectangle()
                        .stroke(Color(red: 1.0, green: 0.24, blue: 0.0), lineWidth: 2)
                        .frame(width: 10 * scaleMultiplier, height: 10 * scaleMultiplier)
                    
                    // Core dot
                    Circle()
                        .fill(Color.black)
                        .frame(width: 4, height: 4)
                }
                .rotationEffect(.degrees(Double(rotation) * 180.0 / .pi))
                .offset(
                    x: CGFloat(min(max(offsetX * 12, -10), 10)),
                    y: CGFloat(min(max(offsetY * 12, -10), 10))
                )
            }
            .frame(width: 46, height: 46)
            .cornerRadius(4)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.85), lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
    }
}
