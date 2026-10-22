import Metal
import CoreGraphics

/// Manages double-buffered ping-pong textures and composite texture for feedback synth pipeline
public final class PingPongBuffer {
    private let device: MTLDevice
    private let commandQueue: MTLCommandQueue
    
    public private(set) var currentReadTexture: MTLTexture?
    public private(set) var currentWriteTexture: MTLTexture?
    public private(set) var currentCompositeTexture: MTLTexture?
    
    private var textureA: MTLTexture?
    private var textureB: MTLTexture?
    private var isUsingAAsRead: Bool = true
    
    public private(set) var width: Int = 0
    public private(set) var height: Int = 0
    
    public init(device: MTLDevice, commandQueue: MTLCommandQueue) {
        self.device = device
        self.commandQueue = commandQueue
    }
    
    /// Reallocate ping-pong textures and composite texture if dimensions changed
    public func updateDimensions(width: Int, height: Int) {
        guard width > 0 && height > 0 else { return }
        if self.width == width && self.height == height && textureA != nil {
            return
        }
        
        self.width = width
        self.height = height
        
        // Descriptor for feedback textures (Render target + Shader read)
        let fboDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: width,
            height: height,
            mipmapped: false
        )
        fboDescriptor.usage = [.renderTarget, .shaderRead]
        fboDescriptor.storageMode = .private
        
        textureA = device.makeTexture(descriptor: fboDescriptor)
        textureB = device.makeTexture(descriptor: fboDescriptor)
        
        // Descriptor for composite output (Used for screen blit and recorder)
        let compDescriptor = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: width,
            height: height,
            mipmapped: false
        )
        compDescriptor.usage = [.renderTarget, .shaderRead]
        compDescriptor.storageMode = .private
        
        currentCompositeTexture = device.makeTexture(descriptor: compDescriptor)
        
        clear()
    }
    
    /// Actually clears textures to solid black via GPU render pass
    public func clear() {
        isUsingAAsRead = true
        currentReadTexture = textureA
        currentWriteTexture = textureB
        
        guard let queue = commandQueue.makeCommandBuffer() else { return }
        
        let texturesToClear = [textureA, textureB, currentCompositeTexture].compactMap { $0 }
        for tex in texturesToClear {
            let pass = MTLRenderPassDescriptor()
            pass.colorAttachments[0].texture = tex
            pass.colorAttachments[0].loadAction = .clear
            pass.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 1)
            pass.colorAttachments[0].storeAction = .store
            if let encoder = queue.makeRenderCommandEncoder(descriptor: pass) {
                encoder.endEncoding()
            }
        }
        queue.commit()
    }
    
    /// Swaps read and write textures after each rendered frame
    public func swap() {
        isUsingAAsRead.toggle()
        currentReadTexture = isUsingAAsRead ? textureA : textureB
        currentWriteTexture = isUsingAAsRead ? textureB : textureA
    }
}
