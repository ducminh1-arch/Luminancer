import SwiftUI
import MetalKit

public struct MetalViewRepresentable: UIViewRepresentable {
    let renderer: MetalRenderer
    
    public init(renderer: MetalRenderer) {
        self.renderer = renderer
    }
    
    public func makeUIView(context: Context) -> MTKView {
        let mtkView = MTKView()
        mtkView.device = renderer.device
        mtkView.delegate = renderer
        mtkView.colorPixelFormat = .bgra8Unorm
        mtkView.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        mtkView.framebufferOnly = true
        mtkView.preferredFramesPerSecond = 60
        mtkView.enableSetNeedsDisplay = false
        mtkView.isPaused = false
        return mtkView
    }
    
    public func updateUIView(_ uiView: MTKView, context: Context) {}
}

public struct SynthView: View {
    @StateObject private var viewModel = SynthViewModel()
    
    public init() {}
    
    public var body: some View {
        ZStack {
            // Background
            Color.black.ignoresSafeArea()
            
            // MARK: - Metal Viewport & Gestures
            if let renderer = viewModel.metalRenderer {
                ZStack {
                    MetalViewRepresentable(renderer: renderer)
                        .ignoresSafeArea()
                    
                    // Gesture overlay capturing pan, pinch, rotate, double tap
                    GesturesView(viewModel: viewModel)
                        .ignoresSafeArea()
                }
            } else {
                Text("Metal GPU Initialisation Failed")
                    .font(.system(.body, design: .monospaced))
                    .foregroundColor(.red)
            }
            
            // MARK: - Synth Controls Deck
            ControlsView(viewModel: viewModel)
                .ignoresSafeArea()
            
            // MARK: - Toast Banner
            if let toast = viewModel.toastMessage {
                GeometryReader { geo in
                    VStack {
                        Spacer().frame(height: geo.safeAreaInsets.top + 80)
                        HStack {
                            Spacer()
                            Text(toast)
                                .font(.system(size: 12, weight: .bold, design: .monospaced))
                                .foregroundColor(.black)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(Color.cyan)
                                .cornerRadius(20)
                                .shadow(color: .cyan.opacity(0.6), radius: 10)
                                .transition(.scale.combined(with: .opacity))
                            Spacer()
                        }
                        Spacer()
                    }
                    .animation(.spring(), value: viewModel.toastMessage)
                }
                .ignoresSafeArea()
            }
            
            // MARK: - Camera Permission Warning
            if !viewModel.isCameraAuthorized {
                VStack(spacing: 12) {
                    Image(systemName: "camera.badge.ellipsis")
                        .font(.system(size: 40))
                        .foregroundColor(.yellow)
                    Text("CAMERA ACCESS REQUIRED")
                        .font(.system(size: 14, weight: .black, design: .monospaced))
                        .foregroundColor(.white)
                    Text("Please grant camera permission in iOS Settings to use live video synthesis.")
                        .font(.system(size: 12))
                        .multilineTextAlignment(.center)
                        .foregroundColor(.gray)
                        .padding(.horizontal, 40)
                    Button(action: {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        Text("OPEN SETTINGS")
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                            .foregroundColor(.black)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.yellow)
                            .cornerRadius(6)
                    }
                }
                .padding(24)
                .background(Color.black.opacity(0.9))
                .cornerRadius(12)
            }
            
            // MARK: - Help Modal Overlay
            if viewModel.showHelpOverlay {
                HelpOverlayView(isPresented: $viewModel.showHelpOverlay)
                    .transition(.opacity)
            }
        }
        .onAppear {
            viewModel.startServices()
        }
        .onDisappear {
            viewModel.stopServices()
        }
    }
}
