package com.sondeptrai.lumisynth.core.render

import android.opengl.GLES30
import java.nio.IntBuffer

/**
 * Manages double-buffered Framebuffer Objects (FBO) with MRT (Multiple Render Targets)
 * Attachment 0: Pure feedback synth trails (Ping-Pong read/write)
 * Attachment 1: Composite blended frame (for screen blit & hardware video recorder)
 */
class PingPongFbo {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private val fboIds = IntArray(2)
    private val feedbackTextureIds = IntArray(2)
    private val compositeTextureIds = IntArray(1)
    private var isUsingAAsRead = true

    val currentReadTextureId: Int
        get() = if (isUsingAAsRead) feedbackTextureIds[0] else feedbackTextureIds[1]

    val currentWriteFboId: Int
        get() = if (isUsingAAsRead) fboIds[1] else fboIds[0]

    val currentWriteTextureId: Int
        get() = if (isUsingAAsRead) feedbackTextureIds[1] else feedbackTextureIds[0]

    val compositeTextureId: Int
        get() = compositeTextureIds[0]

    fun updateDimensions(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        if (width == w && height == h && feedbackTextureIds[0] != 0) return

        release()

        width = w
        height = h

        GLES30.glGenFramebuffers(2, fboIds, 0)
        GLES30.glGenTextures(2, feedbackTextureIds, 0)
        GLES30.glGenTextures(1, compositeTextureIds, 0)

        // 1. Composite Texture (Attachment 1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, compositeTextureIds[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )
        setupTextureParams()

        // 2. Double-buffered Feedback Textures (Attachment 0)
        val drawBuffers = IntBuffer.wrap(intArrayOf(GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_COLOR_ATTACHMENT1))

        for (i in 0 until 2) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, feedbackTextureIds[i])
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA8,
                width,
                height,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )
            setupTextureParams()

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i])
            // Attach feedback texture to Attachment 0
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                feedbackTextureIds[i],
                0
            )
            // Attach composite texture to Attachment 1
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT1,
                GLES30.GL_TEXTURE_2D,
                compositeTextureIds[0],
                0
            )

            // Enable MRT writing to both attachments
            drawBuffers.position(0)
            GLES30.glDrawBuffers(2, drawBuffers)
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        clear()
    }

    private fun setupTextureParams() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    fun swap() {
        isUsingAAsRead = !isUsingAAsRead
    }

    fun clear() {
        for (i in 0 until 2) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i])
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        isUsingAAsRead = true
    }

    fun release() {
        if (fboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(2, fboIds, 0)
            fboIds[0] = 0
            fboIds[1] = 0
        }
        if (feedbackTextureIds[0] != 0) {
            GLES30.glDeleteTextures(2, feedbackTextureIds, 0)
            feedbackTextureIds[0] = 0
            feedbackTextureIds[1] = 0
        }
        if (compositeTextureIds[0] != 0) {
            GLES30.glDeleteTextures(1, compositeTextureIds, 0)
            compositeTextureIds[0] = 0
        }
        width = 0
        height = 0
    }
}
