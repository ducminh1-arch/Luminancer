/**
 * Video Sources for LumiSynth Web:
 * 1. DemoSource: Built-in animated glowing skull & laser light painting generator
 *    (Faithfully recreates Panel 1 Skull Halo Tunnel and Panel 2 Spiral Vortex)
 * 2. CameraSource: Live webcam stream via getUserMedia
 * 3. FileVideoSource: Local video file playback
 */

export class DemoSource {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.width = canvas.width;
    this.height = canvas.height;
  }

  update(time) {
    const ctx = this.ctx;
    const w = this.width;
    const h = this.height;
    const cx = w / 2;
    const cy = h / 2 + 15;

    // Deep dark chassis background
    ctx.fillStyle = '#06080d';
    ctx.fillRect(0, 0, w, h);

    // ==========================================
    // 1. Glowing Neon "LUMINANCER" Title (Matching Panel 1)
    // ==========================================
    ctx.save();
    ctx.font = '800 34px "JetBrains Mono", sans-serif';
    ctx.textAlign = 'center';
    ctx.fillStyle = '#ffee58';
    ctx.shadowColor = '#ffff00';
    ctx.shadowBlur = 35;
    ctx.fillText('LUMINANCER', cx, cy - 135);

    // Inner bright core
    ctx.fillStyle = '#ffffff';
    ctx.shadowBlur = 10;
    ctx.fillText('LUMINANCER', cx, cy - 135);
    ctx.restore();

    // ==========================================
    // 2. Glowing Neon Skull (Matching Panel 1)
    // ==========================================
    ctx.save();
    ctx.translate(cx, cy);
    
    // Subtle breathing pulse
    const breath = 1.0 + 0.02 * Math.sin(time * 2.0);
    ctx.scale(breath, breath);

    // Cranium
    ctx.beginPath();
    ctx.arc(0, -35, 68, Math.PI, 0, false);
    ctx.bezierCurveTo(68, 0, 48, 40, 36, 55);
    ctx.lineTo(36, 75);
    ctx.lineTo(-36, 75);
    ctx.lineTo(-36, 55);
    ctx.bezierCurveTo(-48, 40, -68, 0, -68, -35);
    ctx.closePath();

    // Radiant pink / magenta skull tone (matching Panel 1)
    ctx.fillStyle = '#ff80ab';
    ctx.shadowColor = '#ff4081';
    ctx.shadowBlur = 30;
    ctx.fill();

    // Eye Sockets (Dark hollows for dramatic high-contrast feedback thresholding)
    ctx.shadowBlur = 0;
    ctx.fillStyle = '#06080d';

    ctx.beginPath();
    ctx.ellipse(-26, -20, 18, 22, -0.15, 0, Math.PI * 2);
    ctx.fill();

    ctx.beginPath();
    ctx.ellipse(26, -20, 18, 22, 0.15, 0, Math.PI * 2);
    ctx.fill();

    // Nasal Cavity (Inverted heart)
    ctx.beginPath();
    ctx.moveTo(0, 10);
    ctx.lineTo(8, 28);
    ctx.lineTo(-8, 28);
    ctx.closePath();
    ctx.fill();

    // Teeth Lines
    ctx.strokeStyle = '#06080d';
    ctx.lineWidth = 4;
    for (let x = -24; x <= 24; x += 12) {
      ctx.beginPath();
      ctx.moveTo(x, 56);
      ctx.lineTo(x, 74);
      ctx.stroke();
    }
    ctx.restore();

    // ==============================================================
    // 3. Orbiting Laser Pointer (Matching Panel 2 Spiral Vortex Tunnel)
    // ==============================================================
    ctx.save();
    const wandSpeed = time * 1.8;
    const wandR = 145 + 35 * Math.sin(time * 2.5);
    const wx = cx + Math.cos(wandSpeed) * wandR;
    const wy = cy + Math.sin(wandSpeed * 0.9) * (wandR * 0.65);

    // Laser beam core
    ctx.beginPath();
    ctx.arc(wx, wy, 14, 0, Math.PI * 2);
    ctx.fillStyle = '#ffffff';
    ctx.shadowColor = '#00e5ff';
    ctx.shadowBlur = 35;
    ctx.fill();

    ctx.beginPath();
    ctx.arc(wx, wy, 26, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0, 229, 255, 0.6)';
    ctx.fill();
    ctx.restore();

    return this.canvas;
  }
}

export class CameraSource {
  constructor(videoElement) {
    this.video = videoElement;
    this.stream = null;
    this.facingMode = 'user';
    this.isActive = false;
  }

  async start() {
    this.stop();
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { ideal: 1280 },
          height: { ideal: 720 },
          facingMode: this.facingMode
        },
        audio: false
      });
      this.video.srcObject = this.stream;
      await this.video.play();
      this.isActive = true;
      return true;
    } catch (err) {
      console.warn('Camera access error:', err);
      this.isActive = false;
      return false;
    }
  }

  switchCamera() {
    this.facingMode = this.facingMode === 'user' ? 'environment' : 'user';
    return this.start();
  }

  stop() {
    if (this.stream) {
      this.stream.getTracks().forEach(t => t.stop());
      this.stream = null;
    }
    this.video.srcObject = null;
    this.isActive = false;
  }
}

export class FileVideoSource {
  constructor(videoElement) {
    this.video = videoElement;
    this.objectUrl = null;
    this.isPlaying = false;
    this.isMuted = true;
  }

  load(file) {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
    }
    this.objectUrl = URL.createObjectURL(file);
    this.video.src = this.objectUrl;
    this.video.loop = true;
    this.video.muted = this.isMuted;
    this.video.play().then(() => {
      this.isPlaying = true;
    }).catch(e => console.warn('Autoplay error:', e));
  }

  play() {
    this.video.play();
    this.isPlaying = true;
  }

  pause() {
    this.video.pause();
    this.isPlaying = false;
  }

  togglePlayPause() {
    if (this.video.paused) {
      this.play();
    } else {
      this.pause();
    }
    return !this.video.paused;
  }

  rewind() {
    this.video.currentTime = 0;
  }

  setMuted(muted) {
    this.isMuted = muted;
    this.video.muted = muted;
  }

  toggleMute() {
    this.setMuted(!this.video.muted);
    return this.video.muted;
  }

  destroy() {
    this.video.pause();
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
