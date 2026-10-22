package com.sondeptrai.lumisynth.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.SystemClock
import android.view.Surface
import com.sondeptrai.lumisynth.core.model.EffectParams
import com.sondeptrai.lumisynth.core.recorder.VideoRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer(
    private val context: Context,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer {

    // Shader Programs
    private var synthProgram: ShaderProgram? = null
    private var blitProgram: ShaderProgram? = null
    val pingPongFbo = PingPongFbo()

    // OES Texture for Camera / ExoPlayer
    private var oesTextureId: Int = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val stMatrix = FloatArray(16)

    // Full screen quad geometry
    private val quadVertices = floatArrayOf(
        -1.0f,  1.0f, 0.0f, 1.0f,
        -1.0f, -1.0f, 0.0f, 0.0f,
         1.0f,  1.0f, 1.0f, 1.0f,
         1.0f, -1.0f, 1.0f, 0.0f
    )
    private lateinit var vertexBuffer: FloatBuffer

    @Volatile
    var params: EffectParams = EffectParams()

    private var viewWidth: Int = 1080
    private var viewHeight: Int = 1920
    private var sourceWidth: Int = 1080
    private var sourceHeight: Int = 1920
    private var startTimeMillis: Long = SystemClock.uptimeMillis()
    private var lastFrameTimeMillis: Long = SystemClock.uptimeMillis()
    private var accumulatedSpin: Float = 0.0f

    @Volatile
    var snapshotCallback: ((Bitmap?) -> Unit)? = null

    // Video Recording integration (Items 1, 2, 3, 4, 5)
    @Volatile
    var activeRecorder: VideoRecorder? = null
    @Volatile
    var recordInputSurface: Surface? = null

    private var eglDisplay: android.opengl.EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: android.opengl.EGLContext = EGL14.EGL_NO_CONTEXT
    private var recordEglSurface: android.opengl.EGLSurface = EGL14.EGL_NO_SURFACE
    private var activeRecordSurfaceReference: Surface? = null

    init {
        Matrix.setIdentityM(stMatrix, 0)
        setupVertexBuffer()
    }

    private fun setupVertexBuffer() {
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        eglDisplay = EGL14.eglGetCurrentDisplay()
        eglContext = EGL14.eglGetCurrentContext()

        synthProgram = ShaderProgram(
            context = context,
            vertexAssetPath = "shaders/vertex.glsl",
            fragmentAssetPath = "shaders/fragment_main.glsl"
        )

        blitProgram = ShaderProgram(
            context = context,
            vertexAssetPath = "shaders/vertex_blit.glsl",
            fragmentAssetPath = "shaders/fragment_blit.glsl"
        )

        // Generate OES texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            onSurfaceTextureReady(this)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES30.glViewport(0, 0, width, height)
        pingPongFbo.updateDimensions(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val synth = synthProgram ?: return
        if (synth.programId == 0) return
        val blit = blitProgram ?: return
        if (blit.programId == 0) return

        if (viewWidth <= 0 || viewHeight <= 0 || pingPongFbo.width <= 0) return

        try {
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)
        } catch (_: Exception) {
            // Virtual camera on emulator or video frame not ready yet; safely continue
        }

        val now = SystemClock.uptimeMillis()
        val deltaTime = (now - lastFrameTimeMillis) / 1000f
        lastFrameTimeMillis = now

        if (kotlin.math.abs(params.spin) > 0.001f) {
            accumulatedSpin += params.spin * deltaTime * 2.0f
            if (accumulatedSpin > Math.PI * 2) accumulatedSpin -= (Math.PI * 2).toFloat()
            if (accumulatedSpin < -Math.PI * 2) accumulatedSpin += (Math.PI * 2).toFloat()
        }

        // Setup vertex attributes for quad
        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 4 * 4, vertexBuffer)

        vertexBuffer.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 4 * 4, vertexBuffer)

        // MARK: - Pass 1: MRT Synth Pass
        // Target 0: Feedback texture gets synthesizedOutput
        // Target 1: Composite texture gets blended finalRGB
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, pingPongFbo.currentWriteFboId)
        GLES30.glViewport(0, 0, pingPongFbo.width, pingPongFbo.height)

        // Calculate aspect ratio correction to preserve 1:1 true physical proportions without stretching
        val isRotated = kotlin.math.abs(stMatrix[1]) > 0.5f || kotlin.math.abs(stMatrix[4]) > 0.5f
        val srcW = if (isRotated) minOf(sourceWidth, sourceHeight) else sourceWidth
        val srcH = if (isRotated) maxOf(sourceWidth, sourceHeight) else sourceHeight
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()

        val scaleX: Float
        val scaleY: Float
        if (viewAspect < srcAspect) {
            // Viewport is taller/narrower than source (e.g. 19:9 phone vs 16:9 camera)
            scaleX = viewAspect / srcAspect
            scaleY = 1.0f
        } else {
            // Viewport is wider than source
            scaleX = 1.0f
            scaleY = srcAspect / viewAspect
        }

        synth.use()
        GLES30.glUniformMatrix4fv(synth.getUniformLocation("uSTMatrix"), 1, false, stMatrix, 0)
        GLES30.glUniform2f(synth.getUniformLocation("uAspectScale"), scaleX, scaleY)
        GLES30.glUniform1f(synth.getUniformLocation("uThreshold"), params.threshold)
        GLES30.glUniform1f(synth.getUniformLocation("uSoftness"), params.softness)
        GLES30.glUniform1f(synth.getUniformLocation("uDecay"), params.decay)
        GLES30.glUniform1f(synth.getUniformLocation("uTrailsIntensity"), params.trails)
        GLES30.glUniform1f(synth.getUniformLocation("uTime"), (now - startTimeMillis) / 1000f)
        GLES30.glUniform1f(synth.getUniformLocation("uPulseSpeed"), params.pulseSpeed)

        GLES30.glUniform1i(synth.getUniformLocation("uChannelMode"), params.channel.modeIndex)
        GLES30.glUniform1i(synth.getUniformLocation("uColorMode"), params.colorMode.modeIndex)
        GLES30.glUniform1i(synth.getUniformLocation("uBlendMode"), params.blendMode.modeIndex)

        GLES30.glUniform2f(synth.getUniformLocation("uTrailOffset"), params.trailOffsetX, params.trailOffsetY)
        GLES30.glUniform1f(synth.getUniformLocation("uZoom"), params.zoom)
        GLES30.glUniform1f(synth.getUniformLocation("uRotation"), params.rotation + accumulatedSpin)
        GLES30.glUniform2f(synth.getUniformLocation("uResolution"), viewWidth.toFloat(), viewHeight.toFloat())

        // Unit 0: Source Camera / Video
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES30.glUniform1i(synth.getUniformLocation("uSourceTexture"), 0)

        // Unit 1: Feedback Read Texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pingPongFbo.currentReadTextureId)
        GLES30.glUniform1i(synth.getUniformLocation("uFeedbackTexture"), 1)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // MARK: - Pass 2: Blit Composite Texture to Screen Framebuffer (FBO 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewWidth, viewHeight)

        blit.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pingPongFbo.compositeTextureId)
        GLES30.glUniform1i(blit.getUniformLocation("uTexture"), 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // MARK: - Pass 3: Blit to Hardware Recorder EGL Surface (Items 1, 2, 3, 4)
        renderToRecordingSurfaceIfActive()

        // Check for snapshot
        snapshotCallback?.let { callback ->
            snapshotCallback = null
            val bitmap = createBitmapFromGl(viewWidth, viewHeight)
            callback(bitmap)
        }

        // Swap ping-pong FBO for next frame
        pingPongFbo.swap()
    }

    private fun renderToRecordingSurfaceIfActive() {
        val targetSurface = recordInputSurface
        val recorder = activeRecorder
        if (targetSurface == null || recorder == null) {
            destroyRecordEglSurface()
            return
        }

        // Initialize EGL surface for recorder if needed using EGL_CONFIG_ID (Item 4)
        if (recordEglSurface == EGL14.EGL_NO_SURFACE || activeRecordSurfaceReference != targetSurface) {
            destroyRecordEglSurface()
            activeRecordSurfaceReference = targetSurface

            // Query exact EGL_CONFIG_ID of current context to prevent EGL_BAD_MATCH (Item 4)
            val configId = IntArray(1)
            EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONFIG_ID, configId, 0)
            val attribList = intArrayOf(
                EGL14.EGL_CONFIG_ID, configId[0],
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            val matchingConfig = configs[0]

            if (matchingConfig != null) {
                val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                recordEglSurface = EGL14.eglCreateWindowSurface(eglDisplay, matchingConfig, targetSurface, surfaceAttribs, 0)
            }
        }

        if (recordEglSurface != EGL14.EGL_NO_SURFACE) {
            val screenSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)

            // Switch to recorder EGL Surface
            EGL14.eglMakeCurrent(eglDisplay, recordEglSurface, recordEglSurface, eglContext)

            // Compute letterbox/crop scale to prevent aspect ratio distortion (spec item 2)
            val isSameOrientation = (viewWidth >= viewHeight) == (recorder.videoWidth >= recorder.videoHeight)
            val scale = if (isSameOrientation) {
                // Crop-fill to avoid black bars while maintaining exact aspect ratio
                maxOf(recorder.videoWidth.toFloat() / viewWidth, recorder.videoHeight.toFloat() / viewHeight)
            } else {
                // Letterbox to avoid cutting off majority of content when orientations differ
                minOf(recorder.videoWidth.toFloat() / viewWidth, recorder.videoHeight.toFloat() / viewHeight)
            }
            val drawW = (viewWidth * scale).toInt()
            val drawH = (viewHeight * scale).toInt()
            val vpX = (recorder.videoWidth - drawW) / 2
            val vpY = (recorder.videoHeight - drawH) / 2

            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glViewport(vpX, vpY, drawW, drawH)

            blitProgram?.use()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pingPongFbo.compositeTextureId)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

            // Synchronized presentation time (Item 3)
            if (!recorder.isClockStarted) {
                recorder.baseTimeNs = System.nanoTime()
                recorder.isClockStarted = true
            }
            val presentationTimeNs = System.nanoTime() - recorder.baseTimeNs
            EGLExt.eglPresentationTimeANDROID(eglDisplay, recordEglSurface, presentationTimeNs)
            EGL14.eglSwapBuffers(eglDisplay, recordEglSurface)

            // Item 2: Non-blocking drain on GL thread
            recorder.drainVideoEncoder(false)

            // Restore screen surface
            EGL14.eglMakeCurrent(eglDisplay, screenSurface, screenSurface, eglContext)
            GLES30.glViewport(0, 0, viewWidth, viewHeight)
        }
    }

    /**
     * Item 1: Detach and destroy EGL record surface on GL thread before encoder release!
     */
    fun detachAndDestroyRecordSurface() {
        destroyRecordEglSurface()
        recordInputSurface = null
        activeRecorder = null
    }

    private fun destroyRecordEglSurface() {
        if (recordEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, recordEglSurface)
            recordEglSurface = EGL14.EGL_NO_SURFACE
        }
        activeRecordSurfaceReference = null
    }

    private fun createBitmapFromGl(width: Int, height: Int): Bitmap? {
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
        buffer.order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    fun setSourceResolution(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            sourceWidth = width
            sourceHeight = height
        }
    }

    fun clearFeedback() {
        accumulatedSpin = 0.0f
        pingPongFbo.clear()
    }

    fun release() {
        detachAndDestroyRecordSurface()
        synthProgram?.release()
        blitProgram?.release()
        pingPongFbo.release()
        surfaceTexture?.release()
    }
}
