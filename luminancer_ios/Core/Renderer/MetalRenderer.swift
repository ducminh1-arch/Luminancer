import Metal
import MetalKit
import CoreVideo
import AVFoundation
import UIKit

public protocol MetalRendererDelegate: AnyObject {
    func rendererRequiresOutputPixelBuffer() -> CVPixelBuffer?
    func rendererDidFinishProcessedFrame(pixelBuffer: CVPixelBuffer, presentationTime: CMTime)
}

public final class MetalRenderer: NSObject, MTKViewDelegate {
    public let device: MTLDevice
    public let commandQueue: MTLCommandQueue
    
    // Pipelines
    private var mrtPipelineState: MTLRenderPipelineState?
    private var blitPipelineState: MTLRenderPipelineState?
    private var vertexBuffer: MTLBuffer?
    
    // Texture Cache for Zero-Copy CVPixelBuffer -> MTLTexture
    private var textureCache: CVMetalTextureCache?
    
    // Ping-Pong Double Buffering for Recursive Video Synth Trails
    public let pingPong: PingPongBuffer
    
    // Active Source Texture from Camera or Video Player
    private var sourceTexture: MTLTexture?
    private var activeSourceCVMetalTexture: CVMetalTexture?
    private var latestPresentationTime: CMTime = .zero
    
    // Staging texture with .storageMode = .shared for snapshots (fixes private getBytes crash)
    private var stagingTexture: MTLTexture?
    
    // State & Parameters
    public var params: EffectParams = EffectParams()
    public weak var delegate: MetalRendererDelegate?
    
    // Animation & Spin Tracking
    private var startTime: CFTimeInterval = CACurrentMediaTime()
    private var accumulatedSpin: Float = 0.0
    private var lastFrameTime: CFTimeInterval = CACurrentMediaTime()
    
    // Snapshot Request
    private var snapshotCompletion: ((UIImage?) -> Void)?
    
    public init?(device: MTLDevice) {
        self.device = device
        guard let queue = device.makeCommandQueue() else { return nil }
        self.commandQueue = queue
        self.pingPong = PingPongBuffer(device: device, commandQueue: queue)
        super.init()
        
        setupTextureCache()
        setupQuadVertexBuffer()
        setupRenderPipelines()
    }
    
    private func setupTextureCache() {
        CVMetalTextureCacheCreate(kCFAllocatorDefault, nil, device, nil, &textureCache)
    }
    
    private func setupQuadVertexBuffer() {
        let vertices: [Vertex] = [
            Vertex(position: SIMD2<Float>(-1.0,  1.0), texCoord: SIMD2<Float>(0.0, 0.0)),
            Vertex(position: SIMD2<Float>(-1.0, -1.0), texCoord: SIMD2<Float>(0.0, 1.0)),
            Vertex(position: SIMD2<Float>( 1.0,  1.0), texCoord: SIMD2<Float>(1.0, 0.0)),
            Vertex(position: SIMD2<Float>( 1.0, -1.0), texCoord: SIMD2<Float>(1.0, 1.0))
        ]
        
        vertexBuffer = device.makeBuffer(
            bytes: vertices,
            length: vertices.count * MemoryLayout<Vertex>.stride,
            options: .storageModeShared
        )
    }
    
    private func setupRenderPipelines() {
        guard let library = device.makeDefaultLibrary() else { return }
        
        // 1. MRT Synth Pipeline
        // Target 0: feedbackOutput (pingPong write texture)
        // Target 1: compositeOutput (blended with source for screen & recording)
        let mrtVertex = library.makeFunction(name: "synthVertexShader")
        let mrtFragment = library.makeFunction(name: "synthFragmentShader")
        
        let mrtDesc = MTLRenderPipelineDescriptor()
        mrtDesc.label = "LumiSynthMRTPipeline"
        mrtDesc.vertexFunction = mrtVertex
        mrtDesc.fragmentFunction = mrtFragment
        mrtDesc.colorAttachments[0].pixelFormat = .bgra8Unorm
        mrtDesc.colorAttachments[1].pixelFormat = .bgra8Unorm
        
        // 2. Fast Blit Pipeline (Passthrough to screen)
        let blitVertex = library.makeFunction(name: "blitVertexShader")
        let blitFragment = library.makeFunction(name: "blitFragmentShader")
        
        let blitDesc = MTLRenderPipelineDescriptor()
        blitDesc.label = "LumiSynthBlitPipeline"
        blitDesc.vertexFunction = blitVertex
        blitDesc.fragmentFunction = blitFragment
        blitDesc.colorAttachments[0].pixelFormat = .bgra8Unorm
        
        do {
            mrtPipelineState = try device.makeRenderPipelineState(descriptor: mrtDesc)
            blitPipelineState = try device.makeRenderPipelineState(descriptor: blitDesc)
        } catch {
            print("[MetalRenderer] Pipeline creation failed: \(error.localizedDescription)")
        }
    }
    
    // MARK: - PixelBuffer Ingestion
    
    public func feedPixelBuffer(_ pixelBuffer: CVPixelBuffer, presentationTime: CMTime = .zero) {
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        
        pingPong.updateDimensions(width: width, height: height)
        updateStagingTexture(width: width, height: height)
        
        guard let textureCache = textureCache else { return }
        
        var cvMetalTexture: CVMetalTexture?
        let status = CVMetalTextureCacheCreateTextureFromImage(
            kCFAllocatorDefault,
            textureCache,
            pixelBuffer,
            nil,
            .bgra8Unorm,
            width,
            height,
            0,
            &cvMetalTexture
        )
        
        if status == kCVReturnSuccess, let cvMetalTexture = cvMetalTexture {
            // Keep CVMetalTexture alive until GPU finishes
            self.activeSourceCVMetalTexture = cvMetalTexture
            self.sourceTexture = CVMetalTextureGetTexture(cvMetalTexture)
            self.latestPresentationTime = presentationTime
        }
    }
    
    private func updateStagingTexture(width: Int, height: Int) {
        if stagingTexture?.width == width && stagingTexture?.height == height { return }
        let desc = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: width,
            height: height,
            mipmapped: false
        )
        desc.usage = [.shaderRead]
        desc.storageMode = .shared
        stagingTexture = device.makeTexture(descriptor: desc)
    }
    
    // MARK: - MTKViewDelegate
    
    public func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}
    
    public func draw(in view: MTKView) {
        guard let mrtPipeline = mrtPipelineState,
              let blitPipeline = blitPipelineState,
              let vertexBuffer = vertexBuffer,
              let sourceTexture = sourceTexture,
              let commandBuffer = commandQueue.makeCommandBuffer() else {
            return
        }
        
        // Retain source CVMetalTexture until GPU finishes (Item 7)
        let retainedSourceCVTex = activeSourceCVMetalTexture
        
        let currentTime = CACurrentMediaTime()
        let deltaTime = Float(currentTime - lastFrameTime)
        lastFrameTime = currentTime
        
        if abs(params.spin) > 0.001 {
            accumulatedSpin += params.spin * deltaTime * 2.0
            if accumulatedSpin > Float.pi * 2.0 { accumulatedSpin -= Float.pi * 2.0 }
            if accumulatedSpin < -Float.pi * 2.0 { accumulatedSpin += Float.pi * 2.0 }
        }
        
        // Fill uniforms
        var uniforms = SynthUniforms()
        uniforms.trailOffset = params.trailOffset
        uniforms.zoom = params.zoom
        uniforms.rotation = params.rotation + accumulatedSpin
        uniforms.threshold = params.threshold
        uniforms.softness = params.softness
        uniforms.decay = params.decay
        uniforms.trailsIntensity = params.trails
        uniforms.time = Float(currentTime - startTime)
        uniforms.pulseSpeed = params.pulseSpeed
        
        switch params.channel {
        case .light: uniforms.channelMode = 0
        case .rgbHalo: uniforms.channelMode = 1
        case .edgeGlow: uniforms.channelMode = 2
        case .colorSlice: uniforms.channelMode = 3
        }
        
        switch params.colorMode {
        case .pulse: uniforms.colorMode = 0
        case .rainbow: uniforms.colorMode = 1
        case .warmWhite: uniforms.colorMode = 2
        case .blackWhite: uniforms.colorMode = 3
        case .neon: uniforms.colorMode = 4
        case .staticColor: uniforms.colorMode = 5
        }
        
        switch params.blendMode {
        case .mix: uniforms.blendMode = 0
        case .neg: uniforms.blendMode = 1
        case .add: uniforms.blendMode = 2
        case .invert: uniforms.blendMode = 3
        case .screen: uniforms.blendMode = 4
        }
        
        uniforms.isFrontCamera = 0 // Orientation & mirror handled by AVFoundation
        uniforms.resolution = SIMD2<Float>(Float(sourceTexture.width), Float(sourceTexture.height))
        
        let viewW = max(Float(view.drawableSize.width), 1.0)
        let viewH = max(Float(view.drawableSize.height), 1.0)
        let srcW = max(Float(sourceTexture.width), 1.0)
        let srcH = max(Float(sourceTexture.height), 1.0)
        let viewAspect = viewW / viewH
        let srcAspect = srcW / srcH
        var aspectScale = SIMD2<Float>(1.0, 1.0)
        if viewAspect < srcAspect {
            aspectScale = SIMD2<Float>(viewAspect / srcAspect, 1.0)
        } else {
            aspectScale = SIMD2<Float>(1.0, srcAspect / viewAspect)
        }
        uniforms.aspectScale = aspectScale
        
        guard let feedbackReadTex = pingPong.currentReadTexture ?? sourceTexture,
              let feedbackWriteTex = pingPong.currentWriteTexture,
              let compositeWriteTex = pingPong.currentCompositeTexture else {
            return
        }
        
        // MARK: - Pass 1: MRT Synth Pass
        // Target 0 -> feedbackWriteTex (pure synthesized trails)
        // Target 1 -> compositeWriteTex (blended frame)
        let mrtPassDesc = MTLRenderPassDescriptor()
        mrtPassDesc.colorAttachments[0].texture = feedbackWriteTex
        mrtPassDesc.colorAttachments[0].loadAction = .clear
        mrtPassDesc.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        mrtPassDesc.colorAttachments[0].storeAction = .store
        
        mrtPassDesc.colorAttachments[1].texture = compositeWriteTex
        mrtPassDesc.colorAttachments[1].loadAction = .clear
        mrtPassDesc.colorAttachments[1].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
        mrtPassDesc.colorAttachments[1].storeAction = .store
        
        if let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: mrtPassDesc) {
            encoder.setRenderPipelineState(mrtPipeline)
            encoder.setVertexBuffer(vertexBuffer, offset: 0, index: Int(BufferIndexMeshPositions.rawValue))
            encoder.setVertexBytes(&uniforms, length: MemoryLayout<SynthUniforms>.stride, index: Int(BufferIndexUniforms.rawValue))
            encoder.setFragmentTexture(sourceTexture, index: Int(TextureIndexSource.rawValue))
            encoder.setFragmentTexture(feedbackReadTex, index: Int(TextureIndexFeedback.rawValue))
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<SynthUniforms>.stride, index: Int(BufferIndexUniforms.rawValue))
            encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
            encoder.endEncoding()
        }
        
        // MARK: - Pass 2: Screen Blit (Fast passthrough of composite texture)
        if let drawable = view.currentDrawable,
           let viewPassDescriptor = view.currentRenderPassDescriptor {
            if let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: viewPassDescriptor) {
                encoder.setRenderPipelineState(blitPipeline)
                encoder.setVertexBuffer(vertexBuffer, offset: 0, index: Int(BufferIndexMeshPositions.rawValue))
                encoder.setFragmentTexture(compositeWriteTex, index: 0)
                encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
                encoder.endEncoding()
            }
            commandBuffer.present(drawable)
        }
        
        // MARK: - Output to Recorder (Render PROCESSED composite frame into output CVPixelBuffer)
        var retainedOutputCVTex: CVMetalTexture?
        var outputPixelBufferForRecorder: CVPixelBuffer?
        if let delegate = delegate,
           let destPixelBuffer = delegate.rendererRequiresOutputPixelBuffer(),
           let textureCache = textureCache {
            
            let destW = CVPixelBufferGetWidth(destPixelBuffer)
            let destH = CVPixelBufferGetHeight(destPixelBuffer)
            var destCVTexture: CVMetalTexture?
            let cvStatus = CVMetalTextureCacheCreateTextureFromImage(
                kCFAllocatorDefault,
                textureCache,
                destPixelBuffer,
                nil,
                .bgra8Unorm,
                destW,
                destH,
                0,
                &destCVTexture
            )
            
            if cvStatus == kCVReturnSuccess,
               let destCVTexture = destCVTexture,
               let destMTLTexture = CVMetalTextureGetTexture(destCVTexture) {
                
                retainedOutputCVTex = destCVTexture
                outputPixelBufferForRecorder = destPixelBuffer
                
                let recordPass = MTLRenderPassDescriptor()
                recordPass.colorAttachments[0].texture = destMTLTexture
                recordPass.colorAttachments[0].loadAction = .dontCare
                recordPass.colorAttachments[0].storeAction = .store
                
                if let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: recordPass) {
                    encoder.setRenderPipelineState(blitPipeline)
                    encoder.setVertexBuffer(vertexBuffer, offset: 0, index: Int(BufferIndexMeshPositions.rawValue))
                    encoder.setFragmentTexture(compositeWriteTex, index: 0)
                    encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
                    encoder.endEncoding()
                }
            }
        }
        
        // MARK: - Snapshot via Blit to Staging Shared Texture (Item 6)
        var requestedSnapshotCompletion: ((UIImage?) -> Void)?
        if let completion = snapshotCompletion, let staging = stagingTexture {
            requestedSnapshotCompletion = completion
            snapshotCompletion = nil
            
            if let blitEncoder = commandBuffer.makeBlitCommandEncoder() {
                let size = MTLSizeMake(compositeWriteTex.width, compositeWriteTex.height, 1)
                let origin = MTLOriginMake(0, 0, 0)
                blitEncoder.copy(from: compositeWriteTex, sourceSlice: 0, sourceLevel: 0, sourceOrigin: origin, sourceSize: size, to: staging, destinationSlice: 0, destinationLevel: 0, destinationOrigin: origin)
                blitEncoder.endEncoding()
            }
        }
        
        // Completion Handler to keep CVMetalTextures alive and finish recording/snapshot
        let presTime = latestPresentationTime
        commandBuffer.addCompletedHandler { [retainedSourceCVTex, retainedOutputCVTex] _ in
            _ = retainedSourceCVTex
            _ = retainedOutputCVTex
            
            // Deliver processed pixel buffer to recorder
            if let outPB = outputPixelBufferForRecorder {
                self.delegate?.rendererDidFinishProcessedFrame(pixelBuffer: outPB, presentationTime: presTime)
            }
            
            // Deliver snapshot
            if let completion = requestedSnapshotCompletion, let staging = self.stagingTexture {
                let image = self.convertSharedTextureToUIImage(texture: staging)
                DispatchQueue.main.async { completion(image) }
            }
        }
        
        commandBuffer.commit()
        
        // Cycle feedback buffer textures for next frame
        pingPong.swap()
    }
    
    // MARK: - Snapshot Helper (Reading from .shared texture)
    
    public func requestSnapshot(completion: @escaping (UIImage?) -> Void) {
        self.snapshotCompletion = completion
    }
    
    private func convertSharedTextureToUIImage(texture: MTLTexture) -> UIImage? {
        let width = texture.width
        let height = texture.height
        let rowBytes = width * 4
        var rawData = [UInt8](repeating: 0, count: width * height * 4)
        
        let region = MTLRegionMake2D(0, 0, width, height)
        texture.getBytes(&rawData, bytesPerRow: rowBytes, from: region, mipmapLevel: 0)
        
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        
        guard let context = CGContext(
            data: &rawData,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: rowBytes,
            space: colorSpace,
            bitmapInfo: bitmapInfo.rawValue
        ), let cgImage = context.makeImage() else {
            return nil
        }
        
        return UIImage(cgImage: cgImage)
    }
    
    /// Reset accumulated continuous spin velocity and clear recursive feedback buffers
    public func clearFeedback() {
        accumulatedSpin = 0.0
        pingPong.clear()
    }
}
