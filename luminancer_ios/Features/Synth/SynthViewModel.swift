import SwiftUI
import Combine
import MetalKit
import PhotosUI

public final class SynthViewModel: ObservableObject, CameraServiceDelegate, VideoPlayerServiceDelegate, VideoRecorderDelegate, MetalRendererDelegate {
    // MARK: - Published State
    @Published public var params: EffectParams = EffectParams()
    @Published public var isRecording: Bool = false
    @Published public var recordingDurationString: String = "00:00"
    @Published public var isPlaying: Bool = true
    @Published public var isMuted: Bool = false
    @Published public var toastMessage: String? = nil
    @Published public var showHelpOverlay: Bool = false
    @Published public var isCameraAuthorized: Bool = true
    
    // Services
    public let cameraService = CameraService()
    public let videoPlayerService = VideoPlayerService()
    public let videoRecorder = VideoRecorder()
    public var metalRenderer: MetalRenderer?
    
    private var cancellables = Set<AnyCancellable>()
    
    public init() {
        if let device = MTLCreateSystemDefaultDevice() {
            self.metalRenderer = MetalRenderer(device: device)
        }
        
        self.metalRenderer?.delegate = self
        self.cameraService.delegate = self
        self.videoPlayerService.delegate = self
        self.videoRecorder.delegate = self
        
        // Keep idle timer disabled while synth is running (live performance mode)
        UIApplication.shared.isIdleTimerDisabled = true
        
        // Sync params to metalRenderer whenever params change
        $params
            .sink { [weak self] newParams in
                self?.metalRenderer?.params = newParams
            }
            .store(in: &cancellables)
    }
    
    public func startServices() {
        cameraService.checkPermission { [weak self] granted in
            guard let self = self else { return }
            self.isCameraAuthorized = granted
            if granted {
                self.cameraService.setupSession()
                self.cameraService.start()
            }
        }
    }
    
    public func stopServices() {
        cameraService.stop()
        videoPlayerService.stop()
        if isRecording {
            stopRecording()
        }
    }
    
    // MARK: - Mode Switching
    
    public func cycleChannelMode() {
        let all = ChannelMode.allCases
        if let idx = all.firstIndex(of: params.channel) {
            params.channel = all[(idx + 1) % all.count]
            showToast("CHAN: \(params.channel.rawValue)")
        }
    }
    
    public func cycleColorMode() {
        let all = ColorMode.allCases
        if let idx = all.firstIndex(of: params.colorMode) {
            params.colorMode = all[(idx + 1) % all.count]
            showToast("COLOR: \(params.colorMode.rawValue)")
        }
    }
    
    public func cycleBlendMode() {
        let all = BlendMode.allCases
        if let idx = all.firstIndex(of: params.blendMode) {
            params.blendMode = all[(idx + 1) % all.count]
            showToast("BLEND: \(params.blendMode.rawValue)")
        }
    }
    
    public func toggleSourceMode() {
        if params.source == .camera {
            params.source = .video
            cameraService.stop()
            videoPlayerService.play()
            showToast("SOURCE: VIDEO")
        } else {
            params.source = .camera
            videoPlayerService.pause()
            cameraService.start()
            showToast("SOURCE: CAMERA")
        }
    }
    
    public func toggleTorch() {
        params.isTorchOn.toggle()
        cameraService.setTorch(enabled: params.isTorchOn)
        showToast(params.isTorchOn ? "TORCH ON" : "TORCH OFF")
    }
    
    public func switchCamera() {
        params.isFrontCamera.toggle()
        cameraService.switchCamera()
        showToast(params.isFrontCamera ? "FRONT CAM" : "BACK CAM")
    }
    
    // MARK: - Video Transport Controls
    
    public func togglePlayPause() {
        videoPlayerService.togglePlayPause()
        isPlaying = videoPlayerService.isPlaying
    }
    
    public func rewindVideo() {
        videoPlayerService.rewind()
        showToast("REWIND")
    }
    
    public func toggleMute() {
        isMuted.toggle()
        videoPlayerService.setMuted(isMuted)
        showToast(isMuted ? "MUTED" : "UNMUTED")
    }
    
    public func loadVideoURL(_ url: URL) {
        // Always switch source directly to video when loading (Item 10)
        params.source = .video
        cameraService.stop()
        videoPlayerService.loadVideo(url: url)
        isPlaying = true
        showToast("LOADED VIDEO")
    }
    
    // MARK: - Motion Presets & Gestures
    
    public func cycleMotionPreset() {
        params.nextMotionPreset()
        showToast("MOTION: \(params.motionLabel)")
    }
    
    public func onPanChanged(delta: CGSize) {
        let speed: Float = 0.002
        params.trailOffset.x += Float(delta.width) * speed
        params.trailOffset.y -= Float(delta.height) * speed
        params.trailOffset.x = min(max(params.trailOffset.x, -1.0), 1.0)
        params.trailOffset.y = min(max(params.trailOffset.y, -1.0), 1.0)
    }
    
    public func onPinchChanged(scale: CGFloat) {
        let newZoom = params.zoom * Float(scale)
        params.zoom = min(max(newZoom, 0.5), 4.0)
    }
    
    public func onRotateChanged(rotation: CGFloat) {
        let delta = Float(rotation) * 0.4
        // Spec item 1: Rotate 2 ngón = SPIN: continuous rotation velocity according to gesture, not clamped to ±0.35
        params.spin = min(max(params.spin + delta, -2.5), 2.5)
        params.rotation += delta
    }
    
    public func onDoubleTapReset() {
        params.resetGestures()
        metalRenderer?.clearFeedback()
        showToast("RESET GESTURES & BUFFER")
    }
    
    // MARK: - Recording & Snapshot
    
    public func toggleRecording() {
        if isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    private func startRecording() {
        guard let renderer = metalRenderer else { return }
        let width = renderer.pingPong.width > 0 ? renderer.pingPong.width : 1920
        let height = renderer.pingPong.height > 0 ? renderer.pingPong.height : 1080
        let importedURL = (params.source == .video) ? videoPlayerService.currentURL : nil
        videoRecorder.startRecording(
            width: width,
            height: height,
            importedVideoURL: importedURL,
            isMuted: isMuted
        )
    }
    
    private func stopRecording() {
        videoRecorder.stopRecording()
    }
    
    public func captureSnapshot() {
        metalRenderer?.requestSnapshot { [weak self] image in
            guard let self = self, let image = image else { return }
            self.videoRecorder.saveSnapshot(image) { success, _ in
                self.showToast(success ? "SNAPSHOT SAVED" : "SAVE FAILED")
            }
        }
    }
    
    // MARK: - Toast helper
    
    public func showToast(_ text: String) {
        toastMessage = text
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            if self?.toastMessage == text {
                self?.toastMessage = nil
            }
        }
    }
    
    // MARK: - Camera & Video Delegates
    
    public func cameraService(_ service: CameraService, didOutput pixelBuffer: CVPixelBuffer, presentationTime: CMTime) {
        if params.source == .camera {
            metalRenderer?.feedPixelBuffer(pixelBuffer, presentationTime: presentationTime)
        }
    }
    
    public func cameraService(_ service: CameraService, didOutputAudioSampleBuffer sampleBuffer: CMSampleBuffer) {
        if isRecording && params.source == .camera {
            videoRecorder.recordAudioSampleBuffer(sampleBuffer)
        }
    }
    
    public func cameraServiceDidEncounterError(_ service: CameraService, error: Error) {
        showToast("CAMERA ERROR: \(error.localizedDescription)")
    }
    
    public func videoPlayer(_ player: VideoPlayerService, didOutput pixelBuffer: CVPixelBuffer, presentationTime: CMTime) {
        if params.source == .video {
            metalRenderer?.feedPixelBuffer(pixelBuffer, presentationTime: presentationTime)
        }
    }
    
    public func videoPlayerDidReachEnd(_ player: VideoPlayerService) {}
    
    // Shared monotonic timeline clock for recording (Item 7)
    private var recordingBaseHostTime: CFTimeInterval? = nil
    
    // MARK: - Metal Renderer Delegate (Recording processed frames)
    
    public func rendererRequiresOutputPixelBuffer() -> CVPixelBuffer? {
        if isRecording {
            return videoRecorder.requestOutputPixelBuffer()
        }
        return nil
    }
    
    public func rendererDidFinishProcessedFrame(pixelBuffer: CVPixelBuffer, presentationTime: CMTime) {
        guard isRecording else { return }
        
        let finalPts: CMTime
        if params.source == .video {
            // Item 7: When source is looped video, synchronize to monotonic host clock so PTS never regresses
            let base = recordingBaseHostTime ?? CACurrentMediaTime()
            let elapsed = max(0, CACurrentMediaTime() - base)
            finalPts = CMTime(seconds: elapsed, preferredTimescale: 600)
        } else {
            finalPts = presentationTime
        }
        
        videoRecorder.recordProcessedVideoFrame(pixelBuffer: pixelBuffer, presentationTime: finalPts)
    }
    
    // MARK: - Video Recorder Delegate
    
    public func videoRecorderDidStartRecording(_ recorder: VideoRecorder) {
        isRecording = true
        recordingBaseHostTime = CACurrentMediaTime()
        showToast("RECORDING STARTED")
    }
    
    public func videoRecorderDidFinishRecording(_ recorder: VideoRecorder, outputURL: URL?, error: Error?) {
        isRecording = false
        recordingBaseHostTime = nil
        recordingDurationString = "00:00"
        if error == nil {
            showToast("VIDEO SAVED TO PHOTOS")
        } else {
            showToast("SAVE FAILED")
        }
    }
    
    public func videoRecorderDurationUpdated(_ recorder: VideoRecorder, duration: TimeInterval) {
        let mins = Int(duration) / 60
        let secs = Int(duration) % 60
        recordingDurationString = String(format: "%02d:%02d", mins, secs)
    }
    
    deinit {
        UIApplication.shared.isIdleTimerDisabled = false
        stopServices()
    }
}
