import { vertexShaderSource, fragmentSynthSource, fragmentBlitSource } from './shaders.js';

export class WebGLRenderer {
  constructor(canvas) {
    this.canvas = canvas;
    this.gl = canvas.getContext('webgl2', {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      preserveDrawingBuffer: true,
      powerPreference: 'high-performance'
    });

    if (!this.gl) {
      throw new Error('WebGL2 is not supported on this browser/hardware.');
    }

    const gl = this.gl;
    // Enable color buffer float if available
    gl.getExtension('EXT_color_buffer_float');

    this.width = canvas.width || 1280;
    this.height = canvas.height || 720;

    this.initGeometry();
    this.initShaders();
    this.initSourceTexture();
    this.initPingPongFbo(this.width, this.height);
  }

  initGeometry() {
    const gl = this.gl;
    // Full screen quad
    const quadVertices = new Float32Array([
      // pos: x, y    uv: u, v (WebGL texture coords: 0,0 is bottom-left, flip Y for video)
      -1.0,  1.0,    0.0, 0.0,
      -1.0, -1.0,    0.0, 1.0,
       1.0,  1.0,    1.0, 0.0,
       1.0, -1.0,    1.0, 1.0
    ]);

    this.quadVbo = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.quadVbo);
    gl.bufferData(gl.ARRAY_BUFFER, quadVertices, gl.STATIC_DRAW);
  }

  createShader(type, source) {
    const gl = this.gl;
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);

    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const info = gl.getShaderInfoLog(shader);
      gl.deleteShader(shader);
      throw new Error(`Shader compile error: ${info}`);
    }
    return shader;
  }

  createProgram(vSource, fSource) {
    const gl = this.gl;
    const vs = this.createShader(gl.VERTEX_SHADER, vSource);
    const fs = this.createShader(gl.FRAGMENT_SHADER, fSource);

    const program = gl.createProgram();
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);

    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      const info = gl.getProgramInfoLog(program);
      gl.deleteProgram(program);
      throw new Error(`Program link error: ${info}`);
    }

    gl.deleteShader(vs);
    gl.deleteShader(fs);
    return program;
  }

  initShaders() {
    const gl = this.gl;

    // 1. Synth MRT Program
    this.synthProgram = this.createProgram(vertexShaderSource, fragmentSynthSource);
    this.synthUniforms = {
      uSourceTexture: gl.getUniformLocation(this.synthProgram, 'uSourceTexture'),
      uFeedbackTexture: gl.getUniformLocation(this.synthProgram, 'uFeedbackTexture'),
      uThreshold: gl.getUniformLocation(this.synthProgram, 'uThreshold'),
      uSoftness: gl.getUniformLocation(this.synthProgram, 'uSoftness'),
      uDecay: gl.getUniformLocation(this.synthProgram, 'uDecay'),
      uTrailsIntensity: gl.getUniformLocation(this.synthProgram, 'uTrailsIntensity'),
      uTime: gl.getUniformLocation(this.synthProgram, 'uTime'),
      uPulseSpeed: gl.getUniformLocation(this.synthProgram, 'uPulseSpeed'),
      uChannelMode: gl.getUniformLocation(this.synthProgram, 'uChannelMode'),
      uColorMode: gl.getUniformLocation(this.synthProgram, 'uColorMode'),
      uBlendMode: gl.getUniformLocation(this.synthProgram, 'uBlendMode'),
      uTrailOffset: gl.getUniformLocation(this.synthProgram, 'uTrailOffset'),
      uZoom: gl.getUniformLocation(this.synthProgram, 'uZoom'),
      uRotation: gl.getUniformLocation(this.synthProgram, 'uRotation'),
      uResolution: gl.getUniformLocation(this.synthProgram, 'uResolution')
    };

    // 2. Blit Program
    this.blitProgram = this.createProgram(vertexShaderSource, fragmentBlitSource);
    this.blitUniforms = {
      uTexture: gl.getUniformLocation(this.blitProgram, 'uTexture')
    };
  }

  initSourceTexture() {
    const gl = this.gl;
    this.sourceTexture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, this.sourceTexture);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  }

  initPingPongFbo(width, height) {
    const gl = this.gl;
    this.width = width;
    this.height = height;

    this.fboA = gl.createFramebuffer();
    this.fboB = gl.createFramebuffer();

    this.texFeedbackA = this.createTexture(width, height);
    this.texFeedbackB = this.createTexture(width, height);
    this.texComposite = this.createTexture(width, height);

    // Setup FBO A (Writes Feedback to A, Composite to TexComposite)
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.fboA);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, this.texFeedbackA, 0);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT1, gl.TEXTURE_2D, this.texComposite, 0);
    gl.drawBuffers([gl.COLOR_ATTACHMENT0, gl.COLOR_ATTACHMENT1]);

    // Setup FBO B (Writes Feedback to B, Composite to TexComposite)
    gl.bindFramebuffer(gl.FRAMEBUFFER, this.fboB);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, this.texFeedbackB, 0);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT1, gl.TEXTURE_2D, this.texComposite, 0);
    gl.drawBuffers([gl.COLOR_ATTACHMENT0, gl.COLOR_ATTACHMENT1]);

    gl.bindFramebuffer(gl.FRAMEBUFFER, null);

    this.isUsingAAsRead = true;
    this.clearFeedback();
  }

  createTexture(w, h) {
    const gl = this.gl;
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return tex;
  }

  resize(width, height) {
    if (this.width === width && this.height === height) return;
    this.canvas.width = width;
    this.canvas.height = height;
    this.initPingPongFbo(width, height);
  }

  clearFeedback() {
    const gl = this.gl;
    [this.fboA, this.fboB].forEach(fbo => {
      gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
      gl.clearColor(0, 0, 0, 1);
      gl.clear(gl.COLOR_BUFFER_BIT);
    });
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    this.isUsingAAsRead = true;
  }

  render(sourceElement, params, timeSeconds) {
    const gl = this.gl;
    if (!sourceElement) return;

    // Upload current source frame to texture
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.sourceTexture);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, sourceElement);

    const readFeedbackTex = this.isUsingAAsRead ? this.texFeedbackA : this.texFeedbackB;
    const writeFbo = this.isUsingAAsRead ? this.fboB : this.fboA;

    // Bind Vertex Array / Buffer
    gl.bindBuffer(gl.ARRAY_BUFFER, this.quadVbo);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 16, 0);
    gl.enableVertexAttribArray(1);
    gl.vertexAttribPointer(1, 2, gl.FLOAT, false, 16, 8);

    // ==========================================
    // PASS 1: MRT Synth Pass (Ping-Pong FBO)
    // ==========================================
    gl.bindFramebuffer(gl.FRAMEBUFFER, writeFbo);
    gl.viewport(0, 0, this.width, this.height);

    gl.useProgram(this.synthProgram);

    // Texture Unit 0: Source Camera / Video
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.sourceTexture);
    gl.uniform1i(this.synthUniforms.uSourceTexture, 0);

    // Texture Unit 1: Feedback Previous Frame
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, readFeedbackTex);
    gl.uniform1i(this.synthUniforms.uFeedbackTexture, 1);

    // Upload Uniforms
    gl.uniform1f(this.synthUniforms.uThreshold, params.threshold);
    gl.uniform1f(this.synthUniforms.uSoftness, params.softness || 0.08);
    gl.uniform1f(this.synthUniforms.uDecay, params.decay);
    gl.uniform1f(this.synthUniforms.uTrailsIntensity, params.trails);
    gl.uniform1f(this.synthUniforms.uTime, timeSeconds);
    gl.uniform1f(this.synthUniforms.uPulseSpeed, params.pulseSpeed || 1.5);

    gl.uniform1i(this.synthUniforms.uChannelMode, params.channelMode || 0);
    gl.uniform1i(this.synthUniforms.uColorMode, params.colorMode || 0);
    gl.uniform1i(this.synthUniforms.uBlendMode, params.blendMode || 0);

    gl.uniform2f(this.synthUniforms.uTrailOffset, params.trailOffsetX || 0, params.trailOffsetY || 0);
    gl.uniform1f(this.synthUniforms.uZoom, params.zoom || 1.018);
    gl.uniform1f(this.synthUniforms.uRotation, params.rotation || 0);
    gl.uniform2f(this.synthUniforms.uResolution, this.width, this.height);

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

    // ==========================================
    // PASS 2: Blit Composite to Canvas Framebuffer 0
    // ==========================================
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);

    gl.useProgram(this.blitProgram);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.texComposite);
    gl.uniform1i(this.blitUniforms.uTexture, 0);

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);

    // Swap ping-pong read/write
    this.isUsingAAsRead = !this.isUsingAAsRead;
  }
}
