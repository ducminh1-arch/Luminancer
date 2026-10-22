package com.sondeptrai.lumisynth.core.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

class VideoSource(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    private var activeSurface: Surface? = null

    var isPlaying: Boolean = false
        private set
    var isMuted: Boolean = false
        private set
    var onResolutionChanged: ((Int, Int) -> Unit)? = null

    private fun ensurePlayer(): ExoPlayer {
        return exoPlayer ?: run {
            // Spec item 4: Downscale / cap video decoding to max 1080p so 4K video plays smoothly without lag
            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setMaxVideoSize(1920, 1920)
                )
            }
            ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            this@VideoSource.isPlaying = playing
                        }
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                val w = if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                                    videoSize.height
                                } else {
                                    videoSize.width
                                }
                                val h = if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                                    videoSize.width
                                } else {
                                    videoSize.height
                                }
                                onResolutionChanged?.invoke(w, h)
                            }
                        }
                    })
                    volume = if (isMuted) 0f else 1f
                    exoPlayer = this
                }
        }
    }

    fun attachSurface(surfaceTexture: SurfaceTexture) {
        surfaceTexture.setDefaultBufferSize(1080, 1920)
        if (activeSurface == null || activeSurface?.isValid != true) {
            activeSurface?.release()
            activeSurface = Surface(surfaceTexture)
        }
        ensurePlayer().setVideoSurface(activeSurface)
    }

    fun detachSurface() {
        exoPlayer?.setVideoSurface(null)
        activeSurface?.release()
        activeSurface = null
    }

    fun loadUri(uri: Uri) {
        val player = ensurePlayer()
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun rewind() {
        exoPlayer?.seekTo(0)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        exoPlayer?.volume = if (muted) 0f else 1f
    }

    fun release() {
        detachSurface()
        exoPlayer?.release()
        exoPlayer = null
    }
}
