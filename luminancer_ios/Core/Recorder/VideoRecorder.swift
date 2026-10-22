import AVFoundation
import Photos
import UIKit

public protocol VideoRecorderDelegate: AnyObject {
    func videoRecorderDidStartRecording(_ recorder: VideoRecorder)
    func videoRecorderDidFinishRecording(_ recorder: VideoRecorder, outputURL: URL?, error: Error?)
    func videoRecorderDurationUpdated(_ recorder: VideoRecorder, duration: TimeInterval)
}

public final class VideoRecorder: NSObject {
    public weak var delegate: VideoRecorderDelegate?
    
    private var assetWriter: AVAssetWriter?
    private var videoWriterInput: AVAssetWriterInput?
    private var audioWriterInput: AVAssetWriterInput?
    private var pixelBufferAdaptor: AVAssetWriterInputPixelBufferAdaptor?
    
    private let recordingQueue = DispatchQueue(label: "com.sondeptrai.lumisynth.recorderQueue", qos: .userInitiated)
    private var outputURL: URL?
    private var sourceImportedVideoURL: URL?
    private var isSourceMuted: Bool = false
    
    public private(set) var isRecording: Bool = false
    private var startTime: CMTime = .invalid
    private var durationTimer: Timer?
    private var recordingStartTimeInterval: CFTimeInterval = 0
    
    public override init() {
        super.init()
    }
    
    public func startRecording(width: Int, height: Int, importedVideoURL: URL? = nil, isMuted: Bool = false) {
        guard !isRecording else { return }
        self.sourceImportedVideoURL = importedVideoURL
        self.isSourceMuted = isMuted
        
        recordingQueue.async { [weak self] in
            guard let self = self else { return }
            
            let tempDir = FileManager.default.temporaryDirectory
            let fileName = "Luminancer_\(Date().timeIntervalSince1970).mp4"
            let fileURL = tempDir.appendingPathComponent(fileName)
            self.outputURL = fileURL
            
            try? FileManager.default.removeItem(at: fileURL)
            
            do {
                let writer = try AVAssetWriter(outputURL: fileURL, fileType: .mp4)
                
                // 1. Video Input (H.264, 1080p, 12 Mbps)
                let videoSettings: [String: Any] = [
                    AVVideoCodecKey: AVVideoCodecType.h264,
                    AVVideoWidthKey: width,
                    AVVideoHeightKey: height,
                    AVVideoCompressionPropertiesKey: [
                        AVVideoAverageBitRateKey: 12_000_000,
                        AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                        AVVideoExpectedSourceFrameRateKey: 30
                    ]
                ]
                
                let videoInput = AVAssetWriterInput(mediaType: .video, outputSettings: videoSettings)
                videoInput.expectsMediaDataInRealTime = true
                
                let sourcePixelBufferAttributes: [String: Any] = [
                    kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA),
                    kCVPixelBufferWidthKey as String: width,
                    kCVPixelBufferHeightKey as String: height,
                    kCVPixelBufferMetalCompatibilityKey as String: true
                ]
                
                let adaptor = AVAssetWriterInputPixelBufferAdaptor(
                    assetWriterInput: videoInput,
                    sourcePixelBufferAttributes: sourcePixelBufferAttributes
                )
                
                if writer.canAdd(videoInput) {
                    writer.add(videoInput)
                }
                
                // 2. Audio Input (Only for live camera source)
                // Spec item 3: When source is imported video, omit mic to avoid thousands-of-seconds PTS divergence;
                // imported video's audio track will be cleanly muxed on the identical timeline upon saving.
                if importedVideoURL == nil {
                    let audioSettings: [String: Any] = [
                        AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                        AVSampleRateKey: 44100.0,
                        AVNumberOfChannelsKey: 2,
                        AVEncoderBitRateKey: 128_000
                    ]
                    let audioInput = AVAssetWriterInput(mediaType: .audio, outputSettings: audioSettings)
                    audioInput.expectsMediaDataInRealTime = true
                    
                    if writer.canAdd(audioInput) {
                        writer.add(audioInput)
                    }
                    self.audioWriterInput = audioInput
                } else {
                    self.audioWriterInput = nil
                }
                
                writer.startWriting()
                
                self.assetWriter = writer
                self.videoWriterInput = videoInput
                self.pixelBufferAdaptor = adaptor
                self.startTime = .invalid
                self.isRecording = true
                self.recordingStartTimeInterval = CACurrentMediaTime()
                
                DispatchQueue.main.async {
                    self.startTimer()
                    self.delegate?.videoRecorderDidStartRecording(self)
                }
            } catch {
                DispatchQueue.main.async {
                    self.delegate?.videoRecorderDidFinishRecording(self, outputURL: nil, error: error)
                }
            }
        }
    }
    
    /// Requests a destination CVPixelBuffer from the adaptor's pixel buffer pool (Item 4)
    public func requestOutputPixelBuffer() -> CVPixelBuffer? {
        guard isRecording, let pool = pixelBufferAdaptor?.pixelBufferPool else { return nil }
        var pixelBuffer: CVPixelBuffer?
        let status = CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pool, &pixelBuffer)
        return status == kCVReturnSuccess ? pixelBuffer : nil
    }
    
    /// Appends the processed video frame rendered by Metal (Item 4)
    public func recordProcessedVideoFrame(pixelBuffer: CVPixelBuffer, presentationTime: CMTime) {
        guard isRecording else { return }
        
        recordingQueue.async { [weak self] in
            guard let self = self,
                  let writer = self.assetWriter,
                  let writerInput = self.videoWriterInput,
                  let adaptor = self.pixelBufferAdaptor,
                  writer.status == .writing,
                  writerInput.isReadyForMoreMediaData else {
                return
            }
            
            if self.startTime == .invalid {
                self.startTime = presentationTime
                writer.startSession(atSourceTime: presentationTime)
            }
            
            adaptor.append(pixelBuffer, withPresentationTime: presentationTime)
        }
    }
    
    /// Records audio sample buffer from microphone
    public func recordAudioSampleBuffer(_ sampleBuffer: CMSampleBuffer) {
        guard isRecording else { return }
        
        recordingQueue.async { [weak self] in
            guard let self = self,
                  let writer = self.assetWriter,
                  let audioInput = self.audioWriterInput,
                  writer.status == .writing,
                  audioInput.isReadyForMoreMediaData else {
                return
            }
            
            let pts = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            guard self.startTime != .invalid, pts >= self.startTime else {
                // Drop audio buffer if session has not started or PTS is earlier than startTime
                return
            }
            audioInput.append(sampleBuffer)
        }
    }
    
    public func stopRecording() {
        guard isRecording else { return }
        stopTimer()
        
        recordingQueue.async { [weak self] in
            guard let self = self else { return }
            self.isRecording = false
            
            guard let writer = self.assetWriter else {
                DispatchQueue.main.async {
                    self.delegate?.videoRecorderDidFinishRecording(self, outputURL: nil, error: nil)
                }
                return
            }
            
            self.videoWriterInput?.markAsFinished()
            self.audioWriterInput?.markAsFinished()
            
            writer.finishWriting { [weak self] in
                guard let self = self else { return }
                let url = self.outputURL
                
                if let url = url, writer.status == .completed {
                    if let importURL = self.sourceImportedVideoURL, !self.isSourceMuted {
                        self.muxImportedAudio(from: importURL, withVideo: url) { finalURL in
                            self.saveVideoToPhotoLibrary(videoURL: finalURL) { success, error in
                                DispatchQueue.main.async {
                                    self.delegate?.videoRecorderDidFinishRecording(self, outputURL: finalURL, error: error)
                                }
                            }
                        }
                    } else {
                        self.saveVideoToPhotoLibrary(videoURL: url) { success, error in
                            DispatchQueue.main.async {
                                self.delegate?.videoRecorderDidFinishRecording(self, outputURL: url, error: error)
                            }
                        }
                    }
                } else {
                    DispatchQueue.main.async {
                        self.delegate?.videoRecorderDidFinishRecording(self, outputURL: nil, error: writer.error)
                    }
                }
                
                self.assetWriter = nil
                self.videoWriterInput = nil
                self.audioWriterInput = nil
                self.pixelBufferAdaptor = nil
                self.sourceImportedVideoURL = nil
            }
        }
    }
    
    private func muxImportedAudio(from audioSourceURL: URL, withVideo videoURL: URL, completion: @escaping (URL) -> Void) {
        let composition = AVMutableComposition()
        let videoAsset = AVURLAsset(url: videoURL)
        let audioAsset = AVURLAsset(url: audioSourceURL)
        
        guard let compVideoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
              let assetVideoTrack = videoAsset.tracks(withMediaType: .video).first else {
            completion(videoURL)
            return
        }
        
        let videoDuration = videoAsset.duration
        let videoTimeRange = CMTimeRange(start: .zero, duration: videoDuration)
        
        do {
            try compVideoTrack.insertTimeRange(videoTimeRange, of: assetVideoTrack, at: .zero)
            
            if let assetAudioTrack = audioAsset.tracks(withMediaType: .audio).first,
               let compAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
                // Audio and video start from the exact same time origin (.zero)
                let audioDuration = min(videoDuration, audioAsset.duration)
                let audioTimeRange = CMTimeRange(start: .zero, duration: audioDuration)
                try compAudioTrack.insertTimeRange(audioTimeRange, of: assetAudioTrack, at: .zero)
            }
            
            let muxedURL = FileManager.default.temporaryDirectory.appendingPathComponent("Luminancer_Muxed_\(Date().timeIntervalSince1970).mp4")
            try? FileManager.default.removeItem(at: muxedURL)
            
            guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough) else {
                completion(videoURL)
                return
            }
            exportSession.outputURL = muxedURL
            exportSession.outputFileType = .mp4
            exportSession.exportAsynchronously {
                if exportSession.status == .completed {
                    completion(muxedURL)
                } else {
                    completion(videoURL)
                }
            }
        } catch {
            completion(videoURL)
        }
    }
    
    private func saveVideoToPhotoLibrary(videoURL: URL, completion: @escaping (Bool, Error?) -> Void) {
        PHPhotoLibrary.shared().performChanges({
            PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
        }) { success, error in
            completion(success, error)
        }
    }
    
    public func saveSnapshot(_ image: UIImage, completion: @escaping (Bool, Error?) -> Void) {
        PHPhotoLibrary.shared().performChanges({
            PHAssetChangeRequest.creationRequestForAsset(from: image)
        }) { success, error in
            DispatchQueue.main.async {
                completion(success, error)
            }
        }
    }
    
    private func startTimer() {
        durationTimer?.invalidate()
        durationTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self = self, self.isRecording else { return }
            let duration = CACurrentMediaTime() - self.recordingStartTimeInterval
            self.delegate?.videoRecorderDurationUpdated(self, duration: duration)
        }
    }
    
    private func stopTimer() {
        durationTimer?.invalidate()
        durationTimer = nil
    }
}
