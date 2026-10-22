/**
 * Hardware-accelerated Video Recorder and Still Snapshot for LumiSynth Web
 */

export class VideoRecorder {
  constructor(canvas) {
    this.canvas = canvas;
    this.mediaRecorder = null;
    this.recordedChunks = [];
    this.isRecording = false;
    this.startTime = 0;
    this.timerInterval = null;
  }

  start(onTimerUpdate) {
    if (this.isRecording) return;
    this.recordedChunks = [];

    const stream = this.canvas.captureStream(60); // 60 FPS stream of processed canvas

    const mimeTypes = [
      'video/webm;codecs=vp9',
      'video/webm;codecs=vp8',
      'video/webm',
      'video/mp4'
    ];
    let selectedMime = mimeTypes.find(m => MediaRecorder.isTypeSupported(m)) || 'video/webm';

    this.mediaRecorder = new MediaRecorder(stream, {
      mimeType: selectedMime,
      videoBitsPerSecond: 8000000 // 8 Mbps high quality
    });

    this.mediaRecorder.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) {
        this.recordedChunks.push(e.data);
      }
    };

    this.mediaRecorder.onstop = () => {
      this.exportVideo();
    };

    this.mediaRecorder.start(250); // Slice every 250ms
    this.isRecording = true;
    this.startTime = Date.now();

    if (onTimerUpdate) {
      this.timerInterval = setInterval(() => {
        const elapsed = Math.floor((Date.now() - this.startTime) / 1000);
        const mins = String(Math.floor(elapsed / 60)).padStart(2, '0');
        const secs = String(elapsed % 60).padStart(2, '0');
        onTimerUpdate(`${mins}:${secs}`);
      }, 500);
    }
  }

  stop() {
    if (!this.isRecording) return;
    clearInterval(this.timerInterval);
    this.timerInterval = null;
    this.isRecording = false;
    this.mediaRecorder.stop();
  }

  exportVideo() {
    if (this.recordedChunks.length === 0) return;

    const blob = new Blob(this.recordedChunks, { type: 'video/webm' });
    const url = URL.createObjectURL(blob);
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const a = document.createElement('a');
    a.href = url;
    a.download = `LumiSynth_Video_${timestamp}.webm`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  captureSnapshot() {
    this.canvas.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      const a = document.createElement('a');
      a.href = url;
      a.download = `LumiSynth_Snapshot_${timestamp}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }, 'image/png');
  }
}
