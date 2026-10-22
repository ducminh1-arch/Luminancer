package com.sondeptrai.lumisynth.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var currentCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var activeSurfaceTexture: SurfaceTexture? = null
    private var activeSurface: Surface? = null

    var isFrontCamera: Boolean = false
        private set
    var isTorchEnabled: Boolean = false
        private set
    var onResolutionChanged: ((Int, Int) -> Unit)? = null

    fun startCamera(surfaceTexture: SurfaceTexture) {
        this.activeSurfaceTexture = surfaceTexture

        // Check camera permission before attempting to bind (Item 9)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        bindProvider()
    }

    fun onPermissionGranted() {
        if (activeSurfaceTexture != null) {
            bindProvider()
        }
    }

    private fun bindProvider() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val st = activeSurfaceTexture ?: return

        if (activeSurface == null || activeSurface?.isValid != true) {
            activeSurface?.release()
            activeSurface = Surface(st)
        }
        val surface = activeSurface ?: return

        provider.unbindAll()

        // Fallback for emulators that only have front or back camera
        val hasSelectedCamera = try {
            provider.hasCamera(currentCameraSelector)
        } catch (_: Exception) {
            false
        }

        val selector = if (hasSelectedCamera) {
            currentCameraSelector
        } else if (try { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) } catch (_: Exception) { false }) {
            isFrontCamera = true
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else if (try { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } catch (_: Exception) { false }) {
            isFrontCamera = false
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            // Emulators with no virtual camera device configured
            return
        }
        currentCameraSelector = selector

        val previewBuilder = Preview.Builder()
        try {
            previewBuilder.setTargetAspectRatio(AspectRatio.RATIO_16_9)
        } catch (_: Exception) {}

        preview = previewBuilder.build()
            .apply {
                setSurfaceProvider { request ->
                    st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    onResolutionChanged?.invoke(request.resolution.width, request.resolution.height)
                    request.provideSurface(surface, cameraExecutor) {
                        // Surface request completed
                    }
                }
            }

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector,
                preview
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        currentCameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        bindCameraUseCases()
    }

    fun setTorch(enabled: Boolean) {
        isTorchEnabled = enabled
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        activeSurface?.release()
        activeSurface = null
    }

    fun release() {
        stopCamera()
        activeSurface?.release()
        activeSurface = null
        activeSurfaceTexture = null
        cameraExecutor.shutdown()
    }
}
