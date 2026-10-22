import AVFoundation
import CoreVideo
import QuartzCore

public protocol VideoPlayerServiceDelegate: AnyObject {
    func videoPlayer(_ player: VideoPlayerService, didOutput pixelBuffer: CVPixelBuffer, presentationTime: CMTime)
    func videoPlayerDidReachEnd(_ player: VideoPlayerService)
}

public final class VideoPlayerService: NSObject {
    public weak var delegate: VideoPlayerServiceDelegate?
    
    private var player: AVPlayer?
    private var playerItem: AVPlayerItem?
    private var videoOutput: AVPlayerItemVideoOutput?
    private var displayLink: CADisplayLink?
    
    public private(set) var isPlaying: Bool = false
    public private(set) var isMuted: Bool = false
    public private(set) var duration: CMTime = .zero
    public private(set) var currentURL: URL?
    
    public override init() {
        super.init()
    }
    
    public func loadVideo(url: URL) {
        stop()
        self.currentURL = url
        
        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        self.playerItem = item
        
        var pixelBufferAttributes: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
        ]
        
        // Spec item 4: Limit/downscale decode to max 1080p so 4K video plays smoothly without stutter
        if let videoTrack = asset.tracks(withMediaType: .video).first {
            let naturalSize = videoTrack.naturalSize.applying(videoTrack.preferredTransform)
            let width = abs(naturalSize.width)
            let height = abs(naturalSize.height)
            let maxDim: CGFloat = 1920.0
            
            if max(width, height) > maxDim {
                let scale = maxDim / max(width, height)
                let targetWidth = Int((width * scale / 2.0).rounded(.toNearestOrEven) * 2.0)
                let targetHeight = Int((height * scale / 2.0).rounded(.toNearestOrEven) * 2.0)
                pixelBufferAttributes[kCVPixelBufferWidthKey as String] = targetWidth
                pixelBufferAttributes[kCVPixelBufferHeightKey as String] = targetHeight
            }
        }
        
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: pixelBufferAttributes)
        item.add(output)
        self.videoOutput = output
        
        let avPlayer = AVPlayer(playerItem: item)
        avPlayer.actionAtItemEnd = .none
        self.player = avPlayer
        self.duration = asset.duration
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(itemDidPlayToEnd),
            name: .AVPlayerItemDidPlayToEndTime,
            object: item
        )
        
        setupDisplayLink()
        play()
    }
    
    private func setupDisplayLink() {
        displayLink?.invalidate()
        let link = CADisplayLink(target: self, selector: #selector(displayLinkDidFire))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 30, optimal: 60, maximum: 60)
        link.add(to: .main, forMode: .common)
        self.displayLink = link
    }
    
    @objc private func displayLinkDidFire() {
        guard let output = videoOutput,
              let item = playerItem,
              isPlaying else { return }
        
        let itemTime = output.itemTime(forHostTime: CACurrentMediaTime())
        if output.hasNewPixelBuffer(forItemTime: itemTime) {
            var presentationTime = CMTime.zero
            if let pixelBuffer = output.copyPixelBuffer(forItemTime: itemTime, itemTimeForDisplay: &presentationTime) {
                delegate?.videoPlayer(self, didOutput: pixelBuffer, presentationTime: presentationTime)
            }
        }
    }
    
    @objc private func itemDidPlayToEnd() {
        // Automatic loop
        seek(to: .zero)
        play()
        delegate?.videoPlayerDidReachEnd(self)
    }
    
    public func play() {
        player?.play()
        isPlaying = true
    }
    
    public func pause() {
        player?.pause()
        isPlaying = false
    }
    
    public func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }
    
    public func seek(to time: CMTime) {
        player?.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
    }
    
    public func rewind() {
        seek(to: .zero)
    }
    
    public func setMuted(_ muted: Bool) {
        self.isMuted = muted
        player?.isMuted = muted
    }
    
    public func stop() {
        pause()
        displayLink?.invalidate()
        displayLink = nil
        NotificationCenter.default.removeObserver(self)
        player = nil
        playerItem = nil
        videoOutput = nil
    }
    
    deinit {
        stop()
    }
}
