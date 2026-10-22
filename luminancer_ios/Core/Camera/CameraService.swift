import AVFoundation
import CoreVideo
import UIKit

public protocol CameraServiceDelegate: AnyObject {
    func cameraService(_ service: CameraService, didOutput pixelBuffer: CVPixelBuffer, presentationTime: CMTime)
    func cameraService(_ service: CameraService, didOutputAudioSampleBuffer sampleBuffer: CMSampleBuffer)
    func cameraServiceDidEncounterError(_ service: CameraService, error: Error)
}

public final class CameraService: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, AVCaptureAudioDataOutputSampleBufferDelegate {
    public weak var delegate: CameraServiceDelegate?
    
    private let captureSession = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let audioOutput = AVCaptureAudioDataOutput()
    private let sessionQueue = DispatchQueue(label: "com.sondeptrai.lumisynth.cameraQueue", qos: .userInitiated)
    
    private var currentCameraPosition: AVCaptureDevice.Position = .back
    private var activeDeviceInput: AVCaptureDeviceInput?
    private var activeAudioInput: AVCaptureDeviceInput?
    
    public private(set) var isRunning: Bool = false
    public private(set) var isAuthorized: Bool = false
    
    public override init() {
        super.init()
    }
    
    public func checkPermission(completion: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            self.isAuthorized = true
            completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                self.isAuthorized = granted
                DispatchQueue.main.async { completion(granted) }
            }
        case .denied, .restricted:
            self.isAuthorized = false
            completion(false)
        @unknown default:
            self.isAuthorized = false
            completion(false)
        }
    }
    
    public func setupSession(preferredPosition: AVCaptureDevice.Position = .back) {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.currentCameraPosition = preferredPosition
            
            // Configure AVAudioSession for video recording (Item 8)
            self.configureAudioSession()
            
            self.captureSession.beginConfiguration()
            if self.captureSession.canSetSessionPreset(.hd1920x1080) {
                self.captureSession.sessionPreset = .hd1920x1080
            } else {
                self.captureSession.sessionPreset = .high
            }
            
            self.configureDeviceInput(position: preferredPosition)
            self.configureAudioInput()
            self.configureOutputs()
            self.captureSession.commitConfiguration()
        }
    }
    
    // Item 8: Configure AVAudioSession properly for recording
    private func configureAudioSession() {
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.playAndRecord, mode: .videoRecording, options: [.defaultToSpeaker, .allowBluetooth])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("[CameraService] AVAudioSession configuration error: \(error.localizedDescription)")
        }
    }
    
    private func configureDeviceInput(position: AVCaptureDevice.Position) {
        if let existing = activeDeviceInput {
            captureSession.removeInput(existing)
            activeDeviceInput = nil
        }
        
        let discovery = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: position
        )
        
        guard let device = discovery.devices.first else { return }
        
        do {
            let input = try AVCaptureDeviceInput(device: device)
            if captureSession.canAddInput(input) {
                captureSession.addInput(input)
                self.activeDeviceInput = input
                self.currentCameraPosition = position
            }
        } catch {
            delegate?.cameraServiceDidEncounterError(self, error: error)
        }
    }
    
    private func configureAudioInput() {
        guard activeAudioInput == nil,
              let audioDevice = AVCaptureDevice.default(for: .audio) else { return }
        do {
            let audioInput = try AVCaptureDeviceInput(device: audioDevice)
            if captureSession.canAddInput(audioInput) {
                captureSession.addInput(audioInput)
                self.activeAudioInput = audioInput
            }
        } catch {
            print("[CameraService] Mic input error: \(error.localizedDescription)")
        }
    }
    
    private func configureOutputs() {
        if captureSession.canAddOutput(videoOutput) {
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
            ]
            videoOutput.setSampleBufferDelegate(self, queue: sessionQueue)
            captureSession.addOutput(videoOutput)
            
            if let connection = videoOutput.connection(with: .video) {
                if connection.isVideoOrientationSupported {
                    connection.videoOrientation = .portrait
                }
            }
        }
        
        if captureSession.canAddOutput(audioOutput) {
            audioOutput.setSampleBufferDelegate(self, queue: sessionQueue)
            captureSession.addOutput(audioOutput)
        }
    }
    
    public func start() {
        sessionQueue.async { [weak self] in
            guard let self = self, !self.captureSession.isRunning else { return }
            self.captureSession.startRunning()
            self.isRunning = self.captureSession.isRunning
        }
    }
    
    public func stop() {
        sessionQueue.async { [weak self] in
            guard let self = self, self.captureSession.isRunning else { return }
            self.captureSession.stopRunning()
            self.isRunning = false
        }
    }
    
    public func switchCamera() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            let nextPosition: AVCaptureDevice.Position = (self.currentCameraPosition == .back) ? .front : .back
            self.captureSession.beginConfiguration()
            self.configureDeviceInput(position: nextPosition)
            
            if let connection = self.videoOutput.connection(with: .video) {
                if connection.isVideoOrientationSupported {
                    connection.videoOrientation = .portrait
                }
                if connection.isVideoMirroringSupported {
                    connection.isVideoMirrored = (nextPosition == .front)
                }
            }
            self.captureSession.commitConfiguration()
        }
    }
    
    public func setTorch(enabled: Bool) {
        guard let device = activeDeviceInput?.device, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = enabled ? .on : .off
            device.unlockForConfiguration()
        } catch {
            print("[CameraService] Torch error: \(error.localizedDescription)")
        }
    }
    
    // MARK: - AVCaptureVideoDataOutputSampleBufferDelegate & Audio Delegate
    
    public func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        if output == videoOutput {
            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
            let presentationTime = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            delegate?.cameraService(self, didOutput: pixelBuffer, presentationTime: presentationTime)
        } else if output == audioOutput {
            delegate?.cameraService(self, didOutputAudioSampleBuffer: sampleBuffer)
        }
    }
}
