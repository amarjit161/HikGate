package com.hikgate.app.ui.liveview

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.hikgate.app.databinding.ActivityLiveViewBinding

/**
 * Native, non-WebView live view. This is what stands in for the video part of
 * a legacy Hikvision web UI when that UI needs ActiveX: instead of pretending
 * a browser can play the stream, we talk to the camera directly over RTSP
 * (Hikvision's standard "/Streaming/Channels/101" path) using Media3's
 * ExoPlayer, which is a real, actively maintained, Android-native media
 * pipeline — not a browser plugin shim.
 *
 * NOTE: Media3's RTSP extension is functional but still marked unstable/
 * evolving upstream, and some older Hikvision firmware advertises RTSP
 * profiles ExoPlayer doesn't negotiate cleanly (odd SDP, TCP-only interleave
 * expectations, vendor-specific auth quirks). If playback fails here, the
 * troubleshooting section in the README covers fallback options (VLC-style
 * players, or channel's HTTP/MJPEG snapshot endpoint where available).
 */
class LiveViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveViewBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.btnClose.setOnClickListener { finish() }

        val rtspUrl = intent.getStringExtra(EXTRA_RTSP_URL)
        if (rtspUrl.isNullOrEmpty()) {
            binding.statusOverlay.text = "No RTSP URL provided."
            return
        }
        startPlayback(rtspUrl)
    }

    private fun startPlayback(rtspUrl: String) {
        binding.statusOverlay.visibility = View.VISIBLE
        binding.statusOverlay.text = "Connecting to camera..."

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        binding.playerView.player = exoPlayer

        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true) // more reliable through NAT / restrictive LAN Wi-Fi than UDP
            .createMediaSource(MediaItem.fromUri(rtspUrl))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    binding.statusOverlay.visibility = View.GONE
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                binding.statusOverlay.visibility = View.VISIBLE
                binding.statusOverlay.text = "Stream error: ${error.errorCodeName}\n" +
                    "Check RTSP port/credentials, or that this channel supports RTSP."
            }
        })
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_RTSP_URL = "extra_rtsp_url"
        const val EXTRA_TITLE = "extra_title"
    }
}
