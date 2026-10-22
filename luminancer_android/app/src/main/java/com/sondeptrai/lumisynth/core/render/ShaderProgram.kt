package com.sondeptrai.lumisynth.core.render

import android.content.Context
import android.opengl.GLES30
import android.util.Log

class ShaderProgram(
    context: Context,
    vertexAssetPath: String,
    fragmentAssetPath: String
) {
    var programId: Int = 0
        private set

    init {
        val vertexSource = context.assets.open(vertexAssetPath).bufferedReader().use { it.readText() }
        val fragmentSource = context.assets.open(fragmentAssetPath).bufferedReader().use { it.readText() }

        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(programId)
            Log.e("ShaderProgram", "Program linking failed: $error")
            GLES30.glDeleteProgram(programId)
            programId = 0
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shaderId = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shaderId, source)
        GLES30.glCompileShader(shaderId)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shaderId)
            Log.e("ShaderProgram", "Shader compilation error: $error")
            GLES30.glDeleteShader(shaderId)
            return 0
        }
        return shaderId
    }

    fun use() {
        if (programId != 0) {
            GLES30.glUseProgram(programId)
        }
    }

    fun getUniformLocation(name: String): Int {
        return GLES30.glGetUniformLocation(programId, name)
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}
