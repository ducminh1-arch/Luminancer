package com.sondeptrai.lumisynth.core.recorder

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class VideoRecorder(private val context: Context) {

    private val TAG = "VideoRecorder"

    var isRecording: Boolean = false
        private set

    var inputSurface: Surface? = null
        private set

    var videoWidth: Int = 1920
        private set
    var videoHeight: Int = 1080
        private set

    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var audioRecord: AudioRecord? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var requireAudio = false

    private val isAudioRecording = AtomicBoolean(false)
    private var audioThread: Thread? = null

    // Shared clock for video and audio PTS (Item 3)
    @Volatile
    var baseTimeNs: Long = 0L
    @Volatile
    var isClockStarted: Boolean = false

    // Pending samples before muxer starts
    private data class PendingSample(val track: Int, val buffer: ByteBuffer, val info: MediaCodec.BufferInfo)
    private val pendingSamples = ConcurrentLinkedQueue<PendingSample>()

    private var recordUri: Uri? = null
    private var recordFile: File? = null

    private var recordingStartTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var durationCallback: ((String) -> Unit)? = null

    private val durationRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsedSecs = (SystemClock.uptimeMillis() - recordingStartTime) / 1000
            val mins = elapsedSecs / 60
            val secs = elapsedSecs % 60
            val formatted = String.format("%02d:%02d", mins, secs)
            durationCallback?.invoke(formatted)
            handler.postDelayed(this, 500)
        }
    }

    companion object {
        /**
         * Calculates safe encoder dimensions: standard 16:9 1080p or 720p (Item 5)
         */
        fun calculateEncoderDimensions(width: Int, height: Int): Pair<Int, Int> {
            val isPortrait = height > width
            var w = if (isPortrait) 1080 else 1920
            var h = if (isPortrait) 1920 else 1080

            val minDim = minOf(width, height)
            if (minDim < 900) {
                w = if (isPortrait) 720 else 1280
                h = if (isPortrait) 1280 else 720
            }

            return Pair(w, h)
        }
    }

    fun startRecording(
        rawWidth: Int,
        rawHeight: Int,
        onDurationUpdate: (String) -> Unit,
        onStarted: (Boolean) -> Unit
    ) {
        if (isRecording) {
            onStarted(false)
            return
        }

        try {
            // 1. Prepare Video Encoder (H.264 Surface input) with fallback to 720p
            var (w, h) = calculateEncoderDimensions(rawWidth, rawHeight)
            var encoder: MediaCodec? = null

            try {
                val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface = createInputSurface()
                    start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Standard 1080p video encoder failed ($w x $h), falling back to 720p: ${e.message}")
                w = if (h > w) 720 else 1280
                h = if (h > w) 1280 else 720
                val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                    configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface = createInputSurface()
                    start()
                }
            }
            videoWidth = w
            videoHeight = h
            videoEncoder = encoder

            // 2. Prepare Audio Encoder if mic permission is granted
            requireAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (requireAudio) {
                try {
                    val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 1).apply {
                        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                        setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
                        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
                    }
                    audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                        configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        start()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio encoder init failed, proceeding with video-only: ${e.message}")
                    requireAudio = false
                    audioEncoder = null
                }
            } else {
                audioEncoder = null
            }

            // 3. Prepare MediaMuxer writing to safe local cache file
            val tempFile = File(context.cacheDir, "luminancer_recording_temp.mp4")
            if (tempFile.exists()) tempFile.delete()
            recordFile = tempFile
            mediaMuxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoTrackIndex = -1
            audioTrackIndex = -1
            isMuxerStarted = false
            pendingSamples.clear()
            isClockStarted = false
            baseTimeNs = 0L

            // 4. Start Audio Record Thread if mic permission is granted and encoder ready
            if (requireAudio && audioEncoder != null) {
                startAudioRecordThread()
            }

            isRecording = true
            recordingStartTime = SystemClock.uptimeMillis()
            durationCallback = onDurationUpdate
            handler.post(durationRunnable)
            onStarted(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            releaseEncoders()
            onStarted(false)
        }
    }

    private fun startAudioRecordThread() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBufferSize.coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord not initialized, fallback to video-only")
                requireAudio = false
                audioRecord = null
                return
            }
            audioRecord?.startRecording()
            isAudioRecording.set(true)

            audioThread = Thread({
                val buffer = ByteArray(bufferSize)
                val encoder = audioEncoder ?: return@Thread

                while (isAudioRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0 && isClockStarted) {
                        val inputIndex = encoder.dequeueInputBuffer(0)
                        if (inputIndex >= 0) {
                            val inputBuffer = encoder.getInputBuffer(inputIndex)
                            inputBuffer?.clear()
                            inputBuffer?.put(buffer, 0, read)
                            // Item 3: Audio PTS synchronized to the exact same baseTime clock as video
                            val audioPtsUs = (System.nanoTime() - baseTimeNs) / 1000
                            encoder.queueInputBuffer(inputIndex, 0, read, audioPtsUs, 0)
                        }
                    }
                    drainAudioEncoder(false)
                }

                // Send EOS to audio encoder
                try {
                    val inputIndex = encoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val audioPtsUs = if (isClockStarted) (System.nanoTime() - baseTimeNs) / 1000 else 0L
                        encoder.queueInputBuffer(inputIndex, 0, 0, audioPtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                } catch (_: Exception) {}
                drainAudioEncoder(true)
            }, "AudioRecordThread").apply { start() }
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord init exception: ${e.message}, continuing video-only")
            requireAudio = false
            audioRecord = null
        }
    }

    /**
     * Item 2: Drain video encoder.
     * Uses timeout 0 (non-blocking) during GL thread rendering, 10ms only during EOS.
     */
    @Synchronized
    fun drainVideoEncoder(endOfStream: Boolean) {
        val encoder = videoEncoder ?: return
        val muxer = mediaMuxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = if (endOfStream) 10000L else 0L

        if (endOfStream) {
            try {
                encoder.signalEndOfInputStream()
            } catch (e: Exception) {
                Log.e(TAG, "signalEndOfInputStream error: ${e.message}")
            }
        }

        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    Log.e(TAG, "Video format changed after muxer started")
                } else {
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    checkStartMuxer()
                }
            } else if (status >= 0) {
                val outputBuffer = encoder.getOutputBuffer(status) ?: continue
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size > 0) {
                    if (isMuxerStarted && videoTrackIndex >= 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
                    } else {
                        // Buffer sample until muxer starts (Item 3)
                        val copyBuffer = ByteBuffer.allocate(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        copyBuffer.put(outputBuffer)
                        copyBuffer.flip()
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                        pendingSamples.add(PendingSample(0, copyBuffer, copyInfo))
                    }
                }

                encoder.releaseOutputBuffer(status, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    @Synchronized
    private fun drainAudioEncoder(endOfStream: Boolean) {
        val encoder = audioEncoder ?: return
        val muxer = mediaMuxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = if (endOfStream) 10000L else 0L

        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isMuxerStarted) {
                    Log.e(TAG, "Audio format changed after muxer started")
                } else {
                    val newFormat = encoder.outputFormat
                    audioTrackIndex = muxer.addTrack(newFormat)
                    checkStartMuxer()
                }
            } else if (status >= 0) {
                val outputBuffer = encoder.getOutputBuffer(status) ?: continue
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }

                if (bufferInfo.size > 0) {
                    if (isMuxerStarted && audioTrackIndex >= 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                    } else {
                        // Buffer sample until muxer starts (Item 3)
                        val copyBuffer = ByteBuffer.allocate(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        copyBuffer.put(outputBuffer)
                        copyBuffer.flip()
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                        pendingSamples.add(PendingSample(1, copyBuffer, copyInfo))
                    }
                }

                encoder.releaseOutputBuffer(status, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    /**
     * Item 3: Muxer starts ONLY when all required tracks have been added!
     */
    @Synchronized
    private fun checkStartMuxer() {
        val muxer = mediaMuxer ?: return
        if (isMuxerStarted) return

        val canStart = if (requireAudio) {
            videoTrackIndex >= 0 && audioTrackIndex >= 0
        } else {
            videoTrackIndex >= 0
        }

        if (canStart) {
            muxer.start()
            isMuxerStarted = true

            // Flush buffered samples
            while (pendingSamples.isNotEmpty()) {
                val sample = pendingSamples.poll() ?: break
                val trackIdx = if (sample.track == 0) videoTrackIndex else audioTrackIndex
                if (trackIdx >= 0) {
                    muxer.writeSampleData(trackIdx, sample.buffer, sample.info)
                }
            }
        }
    }

    /**
     * Item 1: Called AFTER the GL thread has completely detached and destroyed the EGL record surface.
     */
    fun finishAndReleaseRecording(onComplete: (Boolean, String) -> Unit) {
        if (!isRecording) {
            onComplete(false, "NOT RECORDING")
            return
        }

        isRecording = false
        handler.removeCallbacks(durationRunnable)

        Thread {
            try {
                // 1. Drain video with EOS
                drainVideoEncoder(true)

                // 2. Stop audio recording & drain audio with EOS
                if (requireAudio) {
                    isAudioRecording.set(false)
                    audioThread?.join(1500)
                    try {
                        audioRecord?.stop()
                        audioRecord?.release()
                    } catch (_: Exception) {}
                    audioRecord = null
                }

                // 3. Stop MediaMuxer
                if (isMuxerStarted) {
                    try {
                        mediaMuxer?.stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "Muxer stop error: ${e.message}")
                    }
                }
                try {
                    mediaMuxer?.release()
                } catch (_: Exception) {}
                mediaMuxer = null

                // 4. Export temporary recording file to MediaStore (Movies/LumiSynth)
                val temp = recordFile
                val exportSuccess = if (temp != null && temp.exists() && temp.length() > 0L) {
                    exportVideoToMediaStore(temp)
                } else {
                    false
                }

                releaseEncoders()

                handler.post {
                    if (exportSuccess) {
                        onComplete(true, "SAVED TO MOVIES/LUMINANCER")
                    } else {
                        onComplete(false, "SAVE RECORDING FAILED")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stop recording failed: ${e.message}", e)
                releaseEncoders()
                handler.post {
                    onComplete(false, "SAVE RECORDING FAILED")
                }
            }
        }.start()
    }

    private fun exportVideoToMediaStore(sourceFile: File): Boolean {
        val filename = "Luminancer_${System.currentTimeMillis()}.mp4"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Luminancer/")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                var uri = try {
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                } catch (_: Exception) {
                    null
                }
                if (uri == null) {
                    val fallbackValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, fallbackValues)
                }
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    val updateValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(uri, updateValues, null, null)
                    return true
                }
            } else {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val appDir = File(moviesDir, "Luminancer").apply { if (!exists()) mkdirs() }
                val targetFile = File(appDir, filename)
                sourceFile.copyTo(targetFile, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("video/mp4"), null)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export video error: ${e.message}", e)
        }

        // Final fallback: copy to app's external movies dir
        try {
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val fallbackFile = File(fallbackDir, filename)
            sourceFile.copyTo(fallbackFile, overwrite = true)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun releaseEncoders() {
        try {
            videoEncoder?.stop()
            videoEncoder?.release()
        } catch (_: Exception) {}
        videoEncoder = null

        try {
            audioEncoder?.stop()
            audioEncoder?.release()
        } catch (_: Exception) {}
        audioEncoder = null

        inputSurface?.release()
        inputSurface = null
        videoTrackIndex = -1
        audioTrackIndex = -1
        isMuxerStarted = false
        pendingSamples.clear()
        isClockStarted = false
    }

    fun saveSnapshot(bitmap: Bitmap, onResult: (Boolean, String) -> Unit) {
        Thread {
            val filename = "Luminancer_${System.currentTimeMillis()}.png"
            var success = false

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Luminancer/")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    var imageUri = try {
                        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    } catch (_: Exception) {
                        null
                    }
                    if (imageUri == null) {
                        val fallbackValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, fallbackValues)
                    }
                    if (imageUri != null) {
                        context.contentResolver.openOutputStream(imageUri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        val updateValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        context.contentResolver.update(imageUri, updateValues, null, null)
                        success = true
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(imagesDir, "Luminancer").apply { if (!exists()) mkdirs() }
                    val imageFile = File(appDir, filename)
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    MediaScannerConnection.scanFile(context, arrayOf(imageFile.absolutePath), arrayOf("image/png"), null)
                    success = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save snapshot error: ${e.message}", e)
            }

            if (!success) {
                try {
                    val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                    val fallbackFile = File(fallbackDir, filename)
                    FileOutputStream(fallbackFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    success = true
                } catch (_: Exception) {}
            }

            handler.post {
                if (success) {
                    onResult(true, "SNAPSHOT SAVED")
                } else {
                    onResult(false, "FAILED TO SAVE SNAPSHOT")
                }
            }
        }.start()
    }
}
