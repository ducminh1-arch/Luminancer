package com.sondeptrai.lumisynth.ui.synth

import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sondeptrai.lumisynth.core.camera.CameraController
import com.sondeptrai.lumisynth.core.model.SourceMode
import com.sondeptrai.lumisynth.core.render.GLRenderer
import com.sondeptrai.lumisynth.core.video.VideoSource

@Composable
fun SynthScreen(
    viewModel: SynthViewModel,
    cameraController: CameraController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val params by viewModel.params.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val showHelpDialog by viewModel.showHelpDialog.collectAsState()

    val videoSource = remember { VideoSource(context) }
    var currentSurfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
    var glRendererInstance by remember { mutableStateOf<GLRenderer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            videoSource.release()
        }
    }

    // Video Picker Launcher (Item 10: Always sets source = VIDEO, no toggle)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            videoSource.loadUri(it)
            viewModel.setSource(SourceMode.VIDEO)
        }
    }

    // Keep GLRenderer params in sync
    LaunchedEffect(params) {
        glRendererInstance?.params = params
    }

    // Handle source mode transitions without dual-producer contention on SurfaceTexture
    LaunchedEffect(params.source, currentSurfaceTexture) {
        val st = currentSurfaceTexture ?: return@LaunchedEffect
        if (params.source == SourceMode.CAMERA) {
            videoSource.pause()
            videoSource.detachSurface()
            cameraController.startCamera(st)
        } else {
            cameraController.stopCamera()
            videoSource.attachSurface(st)
            videoSource.play()
        }
    }

    // Handle video transport play/pause & mute
    LaunchedEffect(isPlaying) {
        if (params.source == SourceMode.VIDEO) {
            if (isPlaying) videoSource.play() else videoSource.pause()
        }
    }
    LaunchedEffect(isMuted) {
        videoSource.setMuted(isMuted)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. OpenGL ES 3.0 Viewport
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    val renderer = GLRenderer(ctx) { surfaceTexture ->
                        currentSurfaceTexture = surfaceTexture
                    }
                    cameraController.onResolutionChanged = { w, h ->
                        renderer.setSourceResolution(w, h)
                    }
                    videoSource.onResolutionChanged = { w, h ->
                        renderer.setSourceResolution(w, h)
                    }
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                    glSurfaceViewRef = this
                    glRendererInstance = renderer

                    viewModel.requestGlSnapshot = { callback ->
                        renderer.snapshotCallback = callback
                    }
                    viewModel.onResetFeedbackBuffer = {
                        renderer.clearFeedback()
                    }
                    // Item 1: Surface setup and safe detachment on GL thread
                    viewModel.onSetRecordSurface = { surface, recorder ->
                        queueEvent {
                            renderer.recordInputSurface = surface
                            renderer.activeRecorder = recorder
                        }
                    }
                    viewModel.onDetachRecordSurface = { onComplete ->
                        queueEvent {
                            renderer.detachAndDestroyRecordSurface()
                            onComplete()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Synth Controls Deck & Viewfinder Gestures
        SynthControls(
            params = params,
            isRecording = isRecording,
            recordingDuration = recordingDuration,
            isPlaying = isPlaying,
            isMuted = isMuted,
            onCycleChannel = { viewModel.cycleChannelMode() },
            onCycleColor = { viewModel.cycleColorMode() },
            onCycleBlend = { viewModel.cycleBlendMode() },
            onToggleSource = { viewModel.toggleSourceMode() },
            onToggleTorch = {
                viewModel.toggleTorch()
                cameraController.setTorch(!params.isTorchOn)
            },
            onSwitchCamera = {
                viewModel.switchCamera()
                cameraController.switchCamera()
            },
            onToggleHelp = { viewModel.toggleHelpDialog() },
            onCycleMotionPreset = { viewModel.cycleMotionPreset() },
            onThresholdChange = { viewModel.updateThreshold(it) },
            onDecayChange = { viewModel.updateDecay(it) },
            onTrailsChange = { viewModel.updateTrails(it) },
            onResetGestures = { viewModel.onDoubleTapReset() },
            onSnapshot = { viewModel.captureSnapshot() },
            onToggleRecord = {
                val w = glRendererInstance?.pingPongFbo?.width ?: 1920
                val h = glRendererInstance?.pingPongFbo?.height ?: 1080
                viewModel.toggleRecording(rawWidth = w, rawHeight = h)
            },
            onPickVideo = {
                videoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onRewindVideo = {
                videoSource.rewind()
                viewModel.showToast("REWIND")
            },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onToggleMute = { viewModel.toggleMute() },
            onPan = { dx, dy -> viewModel.onPan(dx, dy) },
            onZoom = { z -> viewModel.onZoom(z) },
            onRotate = { r -> viewModel.onRotate(r) }
        )

        // 4. Toast Banner
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 80.dp)
        ) {
            toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .background(Color.Cyan, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = msg,
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 5. Help Dialog
        if (showHelpDialog) {
            HelpDialog(onDismiss = { viewModel.toggleHelpDialog() })
        }
    }
}
