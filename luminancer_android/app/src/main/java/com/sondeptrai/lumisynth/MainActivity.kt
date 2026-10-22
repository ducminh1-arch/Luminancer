package com.sondeptrai.lumisynth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sondeptrai.lumisynth.core.camera.CameraController
import com.sondeptrai.lumisynth.ui.synth.SynthScreen
import com.sondeptrai.lumisynth.ui.synth.SynthViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SynthViewModel by viewModels()
    private lateinit var cameraController: CameraController

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            cameraController.onPermissionGranted()
        } else {
            viewModel.showToast("CAMERA PERMISSION REQUIRED")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraController = CameraController(this, this)

        // Keep screen on during live video performance
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Extend drawing area across display cutout (notch) on API 28+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Hide system bars for immersive analog synthesizer UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Check and request permissions
        checkAndRequestPermissions()

        setContent {
            SynthScreen(viewModel = viewModel, cameraController = cameraController)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.release()
    }
}
