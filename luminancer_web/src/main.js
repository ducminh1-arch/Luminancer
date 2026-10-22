import './style.css';
import { WebGLRenderer } from './synth/webgl-renderer.js';
import { DemoSource, CameraSource, FileVideoSource } from './synth/video-sources.js';
import { VideoRecorder } from './synth/recorder.js';

// ==========================================
// Synthesizer State (Single Source of Truth)
// ==========================================
const state = {
  sourceMode: 'DEMO', // 'DEMO' | 'CAMERA' | 'VIDEO'
  channelMode: 0,     // 0: LIGHT, 1: RGB_HALO, 2: EDGE_GLOW, 3: COLOR_SLICE
  colorMode: 0,       // 0: PULSE, 1: RAINBOW, 2: WARM, 3: BW, 4: NEON, 5: STATIC
  blendMode: 0,       // 0: MIX, 1: NEG, 2: ADD, 3: INVERT, 4: SCREEN
  
  threshold: 0.50,
  decay: 0.88,
  trails: 0.80,
  softness: 0.08,
  pulseSpeed: 1.5,

  // Feedback motion parameters
  zoom: 1.065,        // Default 1.065 creates wide expanding concentric halo rings like the skull
  rotation: 0.0,
  trailOffsetX: 0.0,
  trailOffsetY: 0.0,
  motionPresetIndex: 0
};

const CHANNEL_LABELS = ['LIGHT', 'RGB HALO', 'EDGE GLOW', 'SLICE'];
const COLOR_LABELS = ['PULSE', 'RAINBOW', 'WARM', 'B&W', 'NEON', 'STATIC'];
const BLEND_LABELS = ['MIX', 'NEG', 'ADD', 'INVERT', 'SCREEN'];

const MOTION_PRESETS = [
  { name: 'HALO TUNNEL', zoom: 1.065, rotation: 0.0 },
  { name: 'SPIRAL VORTEX', zoom: 1.055, rotation: 0.055 },
  { name: 'BLACK HOLE', zoom: 0.935, rotation: -0.045 },
  { name: 'NEUTRAL', zoom: 1.0, rotation: 0.0 }
];

// DOM Elements
const glCanvas = document.getElementById('gl-canvas');
const hudCanvas = document.getElementById('hud-canvas');
const hudCtx = hudCanvas.getContext('2d');
const gestureOverlay = document.getElementById('gesture-overlay');

const btnSrc = document.getElementById('btn-src');
const btnChan = document.getElementById('btn-chan');
const btnColor = document.getElementById('btn-color');
const btnBlend = document.getElementById('btn-blend');

const labelSrcVal = document.getElementById('label-src-val');
const labelChanVal = document.getElementById('label-chan-val');
const labelColorVal = document.getElementById('label-color-val');
const labelBlendVal = document.getElementById('label-blend-val');

const statusReadout = document.getElementById('status-readout');
const motionBadge = document.getElementById('motion-badge');
const hudBox = document.getElementById('hud-visualizer-box');

const sliderDecay = document.getElementById('slider-decay');
const sliderThreshold = document.getElementById('slider-threshold');
const sliderTrails = document.getElementById('slider-trails');
const valDecay = document.getElementById('val-decay');
const valThreshold = document.getElementById('val-threshold');
const valTrails = document.getElementById('val-trails');

const videoTransportBar = document.getElementById('video-transport-bar');
const btnRewind = document.getElementById('btn-rewind');
const btnPlayPause = document.getElementById('btn-play-pause');
const btnMute = document.getElementById('btn-mute');
const videoFileInput = document.getElementById('video-file-input');

const btnRecord = document.getElementById('btn-record');
const recordBtnText = document.getElementById('record-btn-text');
const recStatusBadge = document.getElementById('rec-status-badge');
const recTimer = document.getElementById('rec-timer');
const btnSnapshot = document.getElementById('btn-snapshot');
const btnCycleMotion = document.getElementById('btn-cycle-motion');
const btnResetFeedback = document.getElementById('btn-reset-feedback');

const btnSwitchCam = document.getElementById('btn-switch-cam');
const btnHelp = document.getElementById('btn-help');
const btnCloseHelp = document.getElementById('btn-close-help');
const helpModal = document.getElementById('help-modal');
const btnFullscreen = document.getElementById('btn-fullscreen');
const toastBanner = document.getElementById('toast-banner');

const sourceVideoEl = document.getElementById('source-video');
const demoCanvasEl = document.getElementById('demo-source-canvas');

// Initialize WebGL2 Renderer
const renderer = new WebGLRenderer(glCanvas);

// Initialize Sources & Recorder
const demoSource = new DemoSource(demoCanvasEl);
const cameraSource = new CameraSource(sourceVideoEl);
const fileVideoSource = new FileVideoSource(sourceVideoEl);
const recorder = new VideoRecorder(glCanvas);

// Toast display
let toastTimeout = null;
function showToast(msg) {
  toastBanner.textContent = msg;
  toastBanner.classList.remove('hidden');
  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    toastBanner.classList.add('hidden');
  }, 1800);
}

// Update UI Labels
function updateUiLabels() {
  labelSrcVal.textContent = state.sourceMode;
  labelChanVal.textContent = CHANNEL_LABELS[state.channelMode];
  labelColorVal.textContent = COLOR_LABELS[state.colorMode];
  labelBlendVal.textContent = BLEND_LABELS[state.blendMode];

  const chanStr = CHANNEL_LABELS[state.channelMode].toLowerCase();
  const colorStr = COLOR_LABELS[state.colorMode].toLowerCase();
  const blendStr = BLEND_LABELS[state.blendMode].toLowerCase();
  statusReadout.textContent = `${chanStr} / ${colorStr} / ${blendStr} / -`;

  const preset = MOTION_PRESETS[state.motionPresetIndex];
  motionBadge.textContent = preset ? preset.name : 'CUSTOM';
}

// Cycle Motion Presets
function cycleMotionPreset() {
  state.motionPresetIndex = (state.motionPresetIndex + 1) % MOTION_PRESETS.length;
  const p = MOTION_PRESETS[state.motionPresetIndex];
  state.zoom = p.zoom;
  state.rotation = p.rotation;
  updateUiLabels();
  showToast(`MOTION: ${p.name}`);
}

// Draw the Signature Concentric Squares HUD Visualizer Box
function drawHudVisualizer() {
  const ctx = hudCtx;
  const w = hudCanvas.width;
  const h = hudCanvas.height;
  const cx = w / 2;
  const cy = h / 2;

  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = '#000000';
  ctx.fillRect(0, 0, w, h);

  ctx.save();
  const shiftX = Math.max(-8, Math.min(8, state.trailOffsetX * 10));
  const shiftY = Math.max(-8, Math.min(8, state.trailOffsetY * 10));
  ctx.translate(cx + shiftX, cy + shiftY);

  // Rotate according to current rotation
  const angleDeg = state.rotation * 500;
  ctx.rotate((angleDeg * Math.PI) / 180);

  const baseSizes = [
    { side: 32, color: '#00e5ff' }, // Cyan
    { side: 24, color: '#e040fb' }, // Magenta
    { side: 16, color: '#ffd600' }, // Amber
    { side: 8,  color: '#ff3d00' }  // Red-Orange
  ];

  const scaleMultiplier = Math.max(0.6, Math.min(1.4, (state.zoom - 1.0) * 10.0 + 1.0));

  baseSizes.forEach(({ side, color }) => {
    const s = side * scaleMultiplier;
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.strokeRect(-s / 2, -s / 2, s, s);
  });

  // Center core dot
  ctx.fillStyle = '#000000';
  ctx.beginPath();
  ctx.arc(0, 0, 2, 0, Math.PI * 2);
  ctx.fill();

  ctx.restore();
}

// Source Mode Switching
async function setSourceMode(mode) {
  state.sourceMode = mode;
  if (mode === 'DEMO') {
    cameraSource.stop();
    fileVideoSource.pause();
    btnSwitchCam.style.display = 'none';
    videoTransportBar.style.display = 'flex';
  } else if (mode === 'CAMERA') {
    fileVideoSource.pause();
    const success = await cameraSource.start();
    if (success) {
      btnSwitchCam.style.display = 'flex';
      videoTransportBar.style.display = 'none';
      showToast('CAMERA CONNECTED');
    } else {
      showToast('CAMERA DENIED -> DEMO MODE');
      setSourceMode('DEMO');
      return;
    }
  } else if (mode === 'VIDEO') {
    cameraSource.stop();
    btnSwitchCam.style.display = 'none';
    videoTransportBar.style.display = 'flex';
  }
  updateUiLabels();
}

// Event Listeners for Deck Tabs
btnSrc.addEventListener('click', () => {
  const modes = ['DEMO', 'CAMERA', 'VIDEO'];
  const next = modes[(modes.indexOf(state.sourceMode) + 1) % modes.length];
  setSourceMode(next);
});

btnChan.addEventListener('click', () => {
  state.channelMode = (state.channelMode + 1) % CHANNEL_LABELS.length;
  updateUiLabels();
  showToast(`CHAN: ${CHANNEL_LABELS[state.channelMode]}`);
});

btnColor.addEventListener('click', () => {
  state.colorMode = (state.colorMode + 1) % COLOR_LABELS.length;
  updateUiLabels();
  showToast(`COLOR: ${COLOR_LABELS[state.colorMode]}`);
});

btnBlend.addEventListener('click', () => {
  state.blendMode = (state.blendMode + 1) % BLEND_LABELS.length;
  updateUiLabels();
  showToast(`BLEND: ${BLEND_LABELS[state.blendMode]}`);
});

// HUD Box Click
hudBox.addEventListener('click', cycleMotionPreset);
btnCycleMotion.addEventListener('click', cycleMotionPreset);

// Sliders
sliderDecay?.addEventListener('input', (e) => {
  state.decay = parseFloat(e.target.value);
  if (valDecay) valDecay.textContent = state.decay.toFixed(2);
});

sliderThreshold?.addEventListener('input', (e) => {
  state.threshold = parseFloat(e.target.value);
  if (valThreshold) valThreshold.textContent = state.threshold.toFixed(2);
});

sliderTrails?.addEventListener('input', (e) => {
  state.trails = parseFloat(e.target.value);
  if (valTrails) valTrails.textContent = state.trails.toFixed(2);
});

// Video Transport
btnPlayPause?.addEventListener('click', () => {
  const isPlaying = fileVideoSource.togglePlayPause();
  btnPlayPause.textContent = isPlaying ? '⏸' : '▶';
});

btnRewind?.addEventListener('click', () => {
  fileVideoSource.rewind();
  showToast('REWIND');
});

btnMute?.addEventListener('click', () => {
  const isMuted = fileVideoSource.toggleMute();
  btnMute.textContent = isMuted ? '🔇' : '🔊';
});

videoFileInput?.addEventListener('change', (e) => {
  const file = e.target.files && e.target.files[0];
  if (file) {
    fileVideoSource.load(file);
    setSourceMode('VIDEO');
    showToast(`LOADED: ${file.name.substring(0, 16)}...`);
  }
});

// Camera Switch
btnSwitchCam?.addEventListener('click', () => {
  cameraSource.switchCamera();
  showToast('CAMERA FLIPPED');
});

// Reset Feedback & Gestures
btnResetFeedback?.addEventListener('click', () => {
  renderer.clearFeedback();
  state.zoom = 1.018;
  state.rotation = 0.0;
  state.trailOffsetX = 0.0;
  state.trailOffsetY = 0.0;
  state.motionPresetIndex = 0;
  updateUiLabels();
  showToast('FEEDBACK RESET');
});

// Recording & Snapshot
btnRecord?.addEventListener('click', () => {
  if (!recorder.isRecording) {
    recorder.start((timeStr) => {
      if (recTimer) recTimer.textContent = timeStr;
    });
    btnRecord.classList.add('recording');
    if (recordBtnText) recordBtnText.textContent = 'STOP';
    recStatusBadge?.classList.remove('hidden');
    showToast('RECORDING STARTED');
  } else {
    recorder.stop();
    btnRecord.classList.remove('recording');
    if (recordBtnText) recordBtnText.textContent = 'REC';
    recStatusBadge?.classList.add('hidden');
    showToast('VIDEO EXPORTED');
  }
});

btnSnapshot.addEventListener('click', () => {
  recorder.captureSnapshot();
  showToast('SNAPSHOT SAVED');
});

// Help Modal
btnHelp.addEventListener('click', () => helpModal.classList.remove('hidden'));
btnCloseHelp.addEventListener('click', () => helpModal.classList.add('hidden'));
helpModal.addEventListener('click', (e) => {
  if (e.target === helpModal) helpModal.classList.add('hidden');
});

// Fullscreen
btnFullscreen.addEventListener('click', () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen();
  } else {
    document.exitFullscreen();
  }
});

// ==========================================
// Mouse & Touch Gestures Interaction Layer
// ==========================================
let isDragging = false;
let startX = 0, startY = 0;

gestureOverlay.addEventListener('mousedown', (e) => {
  isDragging = true;
  startX = e.clientX;
  startY = e.clientY;
});

window.addEventListener('mousemove', (e) => {
  if (!isDragging) return;
  const dx = e.clientX - startX;
  const dy = e.clientY - startY;
  startX = e.clientX;
  startY = e.clientY;

  if (e.shiftKey) {
    // Shift + drag rotates the 3D spiral vortex
    state.rotation += dx * 0.0003;
    state.rotation = Math.max(-0.06, Math.min(0.06, state.rotation));
  } else {
    // Drag offsets the feedback trails
    state.trailOffsetX += dx * 0.0015;
    state.trailOffsetY -= dy * 0.0015;
    state.trailOffsetX = Math.max(-0.5, Math.min(0.5, state.trailOffsetX));
    state.trailOffsetY = Math.max(-0.5, Math.min(0.5, state.trailOffsetY));
  }
});

window.addEventListener('mouseup', () => { isDragging = false; });

gestureOverlay.addEventListener('wheel', (e) => {
  e.preventDefault();
  // Scroll wheel zooms feedback loop
  const delta = -e.deltaY * 0.0008;
  state.zoom = Math.max(0.70, Math.min(1.45, state.zoom + delta));
}, { passive: false });

gestureOverlay.addEventListener('dblclick', () => {
  state.zoom = 1.065;
  state.rotation = 0.0;
  state.trailOffsetX = 0.0;
  state.trailOffsetY = 0.0;
  state.motionPresetIndex = 0;
  renderer.clearFeedback();
  updateUiLabels();
  showToast('GESTURES RESET');
});

// Touch Gestures for Mobile & Tablets
let touchDist = 0;
gestureOverlay.addEventListener('touchstart', (e) => {
  if (e.touches.length === 1) {
    isDragging = true;
    startX = e.touches[0].clientX;
    startY = e.touches[0].clientY;
  } else if (e.touches.length === 2) {
    isDragging = false;
    const dx = e.touches[0].clientX - e.touches[1].clientX;
    const dy = e.touches[0].clientY - e.touches[1].clientY;
    touchDist = Math.hypot(dx, dy);
  }
});

gestureOverlay.addEventListener('touchmove', (e) => {
  if (e.touches.length === 1 && isDragging) {
    const dx = e.touches[0].clientX - startX;
    const dy = e.touches[0].clientY - startY;
    startX = e.touches[0].clientX;
    startY = e.touches[0].clientY;

    state.trailOffsetX = Math.max(-0.6, Math.min(0.6, state.trailOffsetX + dx * 0.0015));
    state.trailOffsetY = Math.max(-0.6, Math.min(0.6, state.trailOffsetY - dy * 0.0015));
  } else if (e.touches.length === 2) {
    const dx = e.touches[0].clientX - e.touches[1].clientX;
    const dy = e.touches[0].clientY - e.touches[1].clientY;
    const newDist = Math.hypot(dx, dy);
    if (touchDist > 0) {
      const ratio = newDist / touchDist;
      state.zoom = Math.max(0.70, Math.min(1.45, state.zoom * ratio));
    }
    touchDist = newDist;
  }
}, { passive: true });

gestureOverlay.addEventListener('touchend', () => { isDragging = false; });

// Resize Handler
function handleResize() {
  const rect = glCanvas.getBoundingClientRect();
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const w = Math.floor(rect.width * dpr);
  const h = Math.floor(rect.height * dpr);

  if (w > 0 && h > 0 && (glCanvas.width !== w || glCanvas.height !== h)) {
    renderer.resize(w, h);
  }
}
window.addEventListener('resize', handleResize);

// ==========================================
// Main Real-Time Render Loop
// ==========================================
let startTime = performance.now();

function animate() {
  handleResize();

  const now = performance.now();
  const elapsed = (now - startTime) / 1000;

  // Determine active source element
  let sourceEl = null;
  if (state.sourceMode === 'DEMO') {
    sourceEl = demoSource.update(elapsed);
  } else if (state.sourceMode === 'CAMERA') {
    if (sourceVideoEl.readyState >= 2) {
      sourceEl = sourceVideoEl;
    }
  } else if (state.sourceMode === 'VIDEO') {
    if (sourceVideoEl.readyState >= 2) {
      sourceEl = sourceVideoEl;
    }
  }

  // Render WebGL Synthesizer
  if (sourceEl) {
    renderer.render(sourceEl, state, elapsed);
  }

  // Draw HUD Visualizer Concentric Squares Box
  drawHudVisualizer();

  requestAnimationFrame(animate);
}

// Initial Setup
updateUiLabels();
handleResize();
requestAnimationFrame(animate);
