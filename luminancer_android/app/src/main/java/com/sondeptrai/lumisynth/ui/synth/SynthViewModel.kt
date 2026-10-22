package com.sondeptrai.lumisynth.ui.synth

import android.app.Application
import android.graphics.Bitmap
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sondeptrai.lumisynth.core.model.BlendMode
import com.sondeptrai.lumisynth.core.model.ChannelMode
import com.sondeptrai.lumisynth.core.model.ColorMode
import com.sondeptrai.lumisynth.core.model.EffectParams
import com.sondeptrai.lumisynth.core.model.SourceMode
import com.sondeptrai.lumisynth.core.recorder.VideoRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SynthViewModel(application: Application) : AndroidViewModel(application) {

    private val _params = MutableStateFlow(EffectParams())
    val params: StateFlow<EffectParams> = _params.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow("00:00")
    val recordingDuration: StateFlow<String> = _recordingDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _showHelpDialog = MutableStateFlow(false)
    val showHelpDialog: StateFlow<Boolean> = _showHelpDialog.asStateFlow()

    val videoRecorder = VideoRecorder(application)

    // Callbacks to communicate with GL thread
    var requestGlSnapshot: (((Bitmap?) -> Unit) -> Unit)? = null
    var onResetFeedbackBuffer: (() -> Unit)? = null
    var onSetRecordSurface: ((Surface?, VideoRecorder?) -> Unit)? = null
    var onDetachRecordSurface: (((() -> Unit) -> Unit))? = null

    fun cycleChannelMode() {
        val modes = ChannelMode.values()
        val next = modes[(_params.value.channel.ordinal + 1) % modes.size]
        _params.update { it.copy(channel = next) }
        showToast("CHAN: ${next.label}")
    }

    fun cycleColorMode() {
        val modes = ColorMode.values()
        val next = modes[(_params.value.colorMode.ordinal + 1) % modes.size]
        _params.update { it.copy(colorMode = next) }
        showToast("COLOR: ${next.label}")
    }

    fun cycleBlendMode() {
        val modes = BlendMode.values()
        val next = modes[(_params.value.blendMode.ordinal + 1) % modes.size]
        _params.update { it.copy(blendMode = next) }
        showToast("BLEND: ${next.label}")
    }

    fun toggleSourceMode() {
        val newSource = if (_params.value.source == SourceMode.CAMERA) SourceMode.VIDEO else SourceMode.CAMERA
        setSource(newSource)
    }

    fun setSource(mode: SourceMode) {
        _params.update { it.copy(source = mode) }
        showToast("SOURCE: ${mode.label}")
    }

    fun updateThreshold(value: Float) {
        _params.update { it.copy(threshold = value) }
    }

    fun updateDecay(value: Float) {
        _params.update { it.copy(decay = value) }
    }

    fun updateTrails(value: Float) {
        _params.update { it.copy(trails = value) }
    }

    // Gestures & Motion Presets
    fun cycleMotionPreset() {
        val next = _params.value.nextMotionPreset()
        _params.update { next }
        showToast("MOTION: ${next.motionLabel}")
    }

    fun onPan(deltaX: Float, deltaY: Float) {
        val sensitivity = 0.002f
        _params.update {
            val nx = (it.trailOffsetX + deltaX * sensitivity).coerceIn(-1.0f, 1.0f)
            val ny = (it.trailOffsetY - deltaY * sensitivity).coerceIn(-1.0f, 1.0f)
            it.copy(trailOffsetX = nx, trailOffsetY = ny)
        }
    }

    fun onZoom(zoomRatio: Float) {
        _params.update {
            val nz = (it.zoom * zoomRatio).coerceIn(0.5f, 4.0f)
            it.copy(zoom = nz)
        }
    }

    fun onRotate(rotationDegrees: Float) {
        val deltaRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
        _params.update {
            // Rotate 2 fingers = SPIN: continuous rotation velocity according to gesture
            val newSpin = (it.spin + deltaRad * 0.4f).coerceIn(-2.5f, 2.5f)
            val newRotation = it.rotation + deltaRad
            it.copy(rotation = newRotation, spin = newSpin)
        }
    }

    fun onDoubleTapReset() {
        _params.update { it.resetGestures() }
        onResetFeedbackBuffer?.invoke()
        showToast("RESET GESTURES & BUFFER")
    }

    fun toggleTorch() {
        val newTorch = !_params.value.isTorchOn
        _params.update { it.copy(isTorchOn = newTorch) }
        showToast(if (newTorch) "TORCH ON" else "TORCH OFF")
    }

    fun switchCamera() {
        val newFront = !_params.value.isFrontCamera
        _params.update { it.copy(isFrontCamera = newFront) }
        showToast(if (newFront) "FRONT CAM" else "BACK CAM")
    }

    fun toggleHelpDialog() {
        _showHelpDialog.update { !it }
    }

    fun togglePlayPause() {
        _isPlaying.update { !it }
    }

    fun toggleMute() {
        _isMuted.update { !it }
        showToast(if (_isMuted.value) "MUTED" else "UNMUTED")
    }

    /**
     * Item 1: Stop recording safely by first detaching EGL surface on GL thread, then finishing encoder.
     */
    fun toggleRecording(rawWidth: Int = 1920, rawHeight: Int = 1080) {
        if (_isRecording.value) {
            // First detach record surface on GL thread before signaling EOS & releasing encoder!
            val detachFunc = onDetachRecordSurface
            if (detachFunc != null) {
                detachFunc {
                    videoRecorder.finishAndReleaseRecording { _, msg ->
                        _isRecording.value = false
                        _recordingDuration.value = "00:00"
                        showToast(msg)
                    }
                }
            } else {
                onSetRecordSurface?.invoke(null, null)
                videoRecorder.finishAndReleaseRecording { _, msg ->
                    _isRecording.value = false
                    _recordingDuration.value = "00:00"
                    showToast(msg)
                }
            }
        } else {
            videoRecorder.startRecording(
                rawWidth = rawWidth,
                rawHeight = rawHeight,
                onDurationUpdate = { duration ->
                    _recordingDuration.value = duration
                },
                onStarted = { started ->
                    if (started) {
                        _isRecording.value = true
                        onSetRecordSurface?.invoke(videoRecorder.inputSurface, videoRecorder)
                        showToast("RECORDING STARTED")
                    } else {
                        showToast("FAILED TO START RECORDER")
                    }
                }
            )
        }
    }

    fun captureSnapshot() {
        requestGlSnapshot?.invoke { bitmap ->
            if (bitmap != null) {
                videoRecorder.saveSnapshot(bitmap) { _, msg ->
                    showToast(msg)
                }
            } else {
                showToast("SNAPSHOT FAILED")
            }
        }
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
        viewModelScope.launch {
            delay(2000)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }
}
