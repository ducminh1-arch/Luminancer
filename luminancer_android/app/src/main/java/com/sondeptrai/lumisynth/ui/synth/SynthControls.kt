package com.sondeptrai.lumisynth.ui.synth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.sondeptrai.lumisynth.core.model.EffectParams
import com.sondeptrai.lumisynth.core.model.SourceMode

@Composable
fun SynthControls(
    params: EffectParams,
    isRecording: Boolean,
    recordingDuration: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    onCycleChannel: () -> Unit,
    onCycleColor: () -> Unit,
    onCycleBlend: () -> Unit,
    onToggleSource: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleHelp: () -> Unit,
    onCycleMotionPreset: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onDecayChange: (Float) -> Unit,
    onTrailsChange: (Float) -> Unit,
    onResetGestures: () -> Unit,
    onSnapshot: () -> Unit,
    onToggleRecord: () -> Unit,
    onPickVideo: () -> Unit,
    onRewindVideo: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onPan: (Float, Float) -> Unit = { _, _ -> },
    onZoom: (Float) -> Unit = {},
    onRotate: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val topPadding = rememberUniversalTopPadding()
    val bottomPadding = rememberUniversalBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = topPadding,
                bottom = bottomPadding
            ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP SECTION: Fully responsive 2-tier layout that never gets occluded by notches
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: The 4 Core Synth Mode Tabs (Equal weight, responsive, no truncation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SynthTabButton("SRC", params.source.label, Color.Cyan, onToggleSource, Modifier.weight(1f))
                SynthTabButton("CHAN", params.channel.label, Color(0xFF00E5FF), onCycleChannel, Modifier.weight(1f))
                SynthTabButton("COLOR", params.colorMode.label, Color(0xFFE040FB), onCycleColor, Modifier.weight(1f))
                SynthTabButton("BLEND", params.blendMode.label, Color(0xFFFF9100), onCycleBlend, Modifier.weight(1f))
            }

            // Row 2: Status Visualizer HUD + Utilities (Torch, Camera Switch, Help)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Motion Visualizer HUD + Readout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeedbackVisualizerBox(
                        params = params,
                        onClick = onCycleMotionPreset
                    )

                    Column {
                        Text(
                            text = "${params.statusString} / -",
                            color = Color(0xFFFF4081),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = params.motionLabel,
                            color = Color.Cyan.copy(alpha = 0.75f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isRecording) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Text(
                                text = recordingDuration,
                                color = Color.Red,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Right: Utility icon buttons (Torch, Switch Camera, Help) - Compact squares with 8dp spacing to prevent overlap
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Torch
                    IconButton(
                        onClick = onToggleTorch,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (params.isTorchOn) Color(0xFF3B3512) else Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, if (params.isTorchOn) Color.Yellow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                    ) {
                        Icon(
                            if (params.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Torch",
                            tint = if (params.isTorchOn) Color.Yellow else Color.Gray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Camera Switch
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(5.dp))
                    ) {
                        Icon(
                            Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Help
                    IconButton(
                        onClick = onToggleHelp,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, Color.Cyan.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                    ) {
                        Text("?", color = Color.Cyan, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }

        // MIDDLE VIEWFINDER: Direct Gesture Surface (Multi-touch pinch-zoom, rotate, pan, double-tap reset)
        var lastTapTime by remember { mutableLongStateOf(0L) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        var rotation = 0f
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val zoomChange = event.calculateZoom()
                                val rotationChange = event.calculateRotation()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                    zoom *= zoomChange
                                    rotation += rotationChange
                                    pan += panChange

                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = kotlin.math.abs(1f - zoom) * centroidSize
                                    val rotationMotion = kotlin.math.abs(rotation * Math.PI.toFloat() * centroidSize / 180f)
                                    val panMotion = pan.getDistance()

                                    if (zoomMotion > touchSlop ||
                                        rotationMotion > touchSlop ||
                                        panMotion > touchSlop
                                    ) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        if (zoomChange != 1f) {
                                            onZoom(zoomChange)
                                        }
                                        if (rotationChange != 0f) {
                                            onRotate(rotationChange)
                                        }
                                    }
                                    if (panChange.x != 0f || panChange.y != 0f) {
                                        onPan(panChange.x, panChange.y)
                                    }
                                    event.changes.forEach {
                                        if (it.positionChanged()) {
                                            it.consume()
                                        }
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        if (!pastTouchSlop) {
                            val upTime = System.currentTimeMillis()
                            if (upTime - downTime < 300L) {
                                if (downTime - lastTapTime < 350L) {
                                    onResetGestures()
                                    lastTapTime = 0L
                                } else {
                                    lastTapTime = downTime
                                }
                            } else {
                                lastTapTime = 0L
                            }
                        } else {
                            lastTapTime = 0L
                        }
                    }
                }
        )

        // BOTTOM SECTION
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Video Transport Bar (only when video source is selected)
            AnimatedVisibility(visible = params.source == SourceMode.VIDEO) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRewindVideo) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Rewind", tint = Color.White)
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Cyan
                        )
                    }
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color.Gray else Color.White
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onPickVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IMPORT", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Bottom Sliders & Controls Deck
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181C).copy(alpha = 0.92f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Decay & Threshold sliders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // DECAY SLIDER
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("DECAY", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(String.format("%.2f", params.decay), color = Color.Cyan.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = params.decay,
                                onValueChange = onDecayChange,
                                valueRange = 0.0f..0.98f,
                                colors = SliderDefaults.colors(thumbColor = Color.Cyan, activeTrackColor = Color.Cyan)
                            )
                        }

                        // THRESHOLD SLIDER
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("THRESHOLD", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(String.format("%.2f", params.threshold), color = Color.Red.copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Slider(
                                value = params.threshold,
                                onValueChange = onThresholdChange,
                                valueRange = 0.0f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red)
                            )
                        }
                    }

                    // Row 2: Trails intensity slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TRAILS", color = Color(0xFFE040FB), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(50.dp))
                        Slider(
                            value = params.trails,
                            onValueChange = onTrailsChange,
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFE040FB), activeTrackColor = Color(0xFFE040FB)),
                            modifier = Modifier.weight(1f)
                        )
                        Text(String.format("%.2f", params.trails), color = Color(0xFFE040FB).copy(alpha = 0.7f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp))
                    }

                    // Action buttons row: Reset, Snap, REC, Load
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionButton(icon = Icons.Default.Refresh, label = "RESET", onClick = onResetGestures)
                        ActionButton(icon = Icons.Default.PhotoCamera, label = "SNAP", onClick = onSnapshot)

                        // Glowing circular REC button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .border(3.dp, if (isRecording) Color.Red else Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable { onToggleRecord() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isRecording) 24.dp else 42.dp)
                                    .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                                    .background(Color.Red)
                            )
                        }

                        ActionButton(icon = Icons.Default.Folder, label = "LOAD", onClick = onPickVideo)
                    }
                }
            }
        }
    }
}

@Composable
private fun SynthTabButton(
    label: String,
    value: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(48.dp, 44.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Luminancer Transformation Visualizer HUD Box (Concentric Nested Squares)
 * Visualizes feedback loop zoom, rotation angle, and drift offset in real-time.
 */
@Composable
fun FeedbackVisualizerBox(
    params: EffectParams,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayAngle = remember(params.rotation) {
        (params.rotation * 180f / Math.PI.toFloat())
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(
                size.width / 2f + (params.trailOffsetX * 12f).coerceIn(-10f, 10f),
                size.height / 2f + (params.trailOffsetY * 12f).coerceIn(-10f, 10f)
            )

            rotate(degrees = displayAngle, pivot = center) {
                val baseSizes = listOf(
                    34f to Color(0xFF00E5FF), // Cyan
                    26f to Color(0xFFE040FB), // Magenta
                    18f to Color(0xFFFFD600), // Amber
                    10f to Color(0xFFFF3D00)  // Red-Orange
                )

                val scaleMultiplier = ((params.zoom - 1.0f) * 10.0f + 1.0f).coerceIn(0.5f, 1.5f)

                for ((side, color) in baseSizes) {
                    val currentSide = side * scaleMultiplier
                    drawRect(
                        color = color,
                        topLeft = Offset(center.x - currentSide / 2f, center.y - currentSide / 2f),
                        size = androidx.compose.ui.geometry.Size(currentSide, currentSide),
                        style = Stroke(width = 2f)
                    )
                }

                drawCircle(
                    color = Color.Black,
                    radius = 3f,
                    center = center
                )
            }
        }
    }
}

/**
 * Universal safe top inset calculation that works across ALL Android devices,
 * including older Android 8.1 Oreo devices with vendor notches (e.g. OPPO A3s, Vivo, Xiaomi)
 * where platform DisplayCutout API does not exist and immersive mode reports 0 for WindowInsets.
 */
@Composable
private fun rememberUniversalTopPadding(): Dp {
    val density = LocalDensity.current
    val context = LocalContext.current

    // 1. Compose WindowInsets (if reported by system)
    val composeCutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val composeStatusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val composeSafeDrawing = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    // 2. Hardware status_bar_height resource (works on ALL Android versions 4.0 - 15+)
    val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val resStatusBarPx = if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    val resStatusBarDp = with(density) { resStatusBarPx.toDp() }

    // 3. Window decorView displayCutout (Android 9+ API 28+)
    val windowCutoutPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (context as? Activity)?.window?.decorView?.rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
    } else 0
    val windowCutoutDp = with(density) { windowCutoutPx.toDp() }

    // 4. Vendor-specific notch detection (e.g. ColorOS on OPPO A3s)
    val isOppoNotch = context.packageManager.hasSystemFeature("com.oppo.feature.screen.heteromorphism")
    val isNotchDevice = isOppoNotch || composeCutout > 0.dp || windowCutoutDp > 0.dp || resStatusBarDp > 28.dp

    val rawTop = listOf(composeCutout, composeStatusBar, composeSafeDrawing, resStatusBarDp, windowCutoutDp).maxOrNull() ?: 0.dp

    return if (isNotchDevice) {
        maxOf(rawTop, 40.dp) + 6.dp
    } else {
        maxOf(rawTop, 20.dp) + 4.dp
    }
}

/**
 * Universal safe bottom inset calculation for navigation bar and gesture pill.
 */
@Composable
private fun rememberUniversalBottomPadding(): Dp {
    val density = LocalDensity.current
    val context = LocalContext.current

    val composeNav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val composeSafe = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    val resNavPx = if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    val resNavDp = with(density) { resNavPx.toDp() }

    val rawBottom = listOf(composeNav, composeSafe).maxOrNull() ?: 0.dp
    return if (rawBottom > 0.dp) {
        rawBottom + 4.dp
    } else if (resNavDp > 0.dp) {
        10.dp
    } else {
        6.dp
    }
}
