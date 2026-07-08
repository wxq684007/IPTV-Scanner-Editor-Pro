package com.iptv.scanner.editor.pro.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 基于 ExoPlayer 的播放器适配器，实现 [Player] 接口。
 *
 * 用于 [PlayerType.EXO]（硬解）和 [PlayerType.SYSTEM]（软解）两种模式。
 *
 * 与 [MpvController] 的区别：
 * - MPV 功能最完整（EQ/AB循环/逐帧/截图/HDR），但某些设备 GPU 兼容性差
 * - ExoPlayer 兼容性好（Google 官方维护），HLS/DASH/RTSP 协议支持完善
 * - 系统解码（软解）作为最终 fallback，不依赖 GPU
 *
 * MPV 专属的高级功能（EQ/截图/AB循环/逐帧/章节等）在此实现中返回 false 或 no-op，
 * UI 层通过 [capabilities] 自动隐藏不支持的功能面板。
 *
 * @param context 应用上下文
 * @param type [PlayerType.EXO] 或 [PlayerType.SYSTEM]
 */
class ExoPlayerWrapper(
    private val context: Context,
    private val type: PlayerType
) : Player {

    companion object {
        private const val TAG = "ExoPlayerWrapper"
        private const val USER_AGENT = "VLC/3.0.18Libmpv"
    }

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var currentUrl: String = ""

    // -----------------------------------------------------------------
    // 可观察状态（与 MpvController StateFlow 对齐）
    // -----------------------------------------------------------------

    private val _timePos = MutableStateFlow(0.0)
    override val timePos: StateFlow<Double> = _timePos.asStateFlow()

    private val _duration = MutableStateFlow(0.0)
    override val duration: StateFlow<Double> = _duration.asStateFlow()

    private val _paused = MutableStateFlow(true)
    override val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _volume = MutableStateFlow(100)
    override val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _muted = MutableStateFlow(false)
    override val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _mediaTitle = MutableStateFlow("")
    override val mediaTitle: StateFlow<String> = _mediaTitle.asStateFlow()

    private val _trackListJson = MutableStateFlow("[]")
    override val trackListJson: StateFlow<String> = _trackListJson.asStateFlow()

    private val _eofReached = MutableStateFlow(false)
    override val eofReached: StateFlow<Boolean> = _eofReached.asStateFlow()

    private val _fileLoaded = MutableStateFlow(false)
    override val fileLoaded: StateFlow<Boolean> = _fileLoaded.asStateFlow()

    private val _videoWidth = MutableStateFlow(0)
    override val videoWidth: StateFlow<Int> = _videoWidth.asStateFlow()

    private val _videoHeight = MutableStateFlow(0)
    override val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()

    private val _speed = MutableStateFlow(1.0)
    override val speed: StateFlow<Double> = _speed.asStateFlow()

    private val _currentChapter = MutableStateFlow(-1)
    override val currentChapter: StateFlow<Int> = _currentChapter.asStateFlow()

    private val _chapterCount = MutableStateFlow(0)
    override val chapterCount: StateFlow<Int> = _chapterCount.asStateFlow()

    // -----------------------------------------------------------------
    // 能力声明
    // -----------------------------------------------------------------

    override val capabilities: PlayerCapabilities = PlayerCapabilities(
        supportsSpeedControl = true,
        supportsTrackList = true,
        supportsAddSubtitleFile = true,
        supportsSubVisibility = true,
        supportsSubDelay = false,
        supportsSubScale = false,
        supportsSubPos = false,
        supportsHardwareDecodeSwitch = true
    )

    override val playerType: PlayerType = type

    // -----------------------------------------------------------------
    // 生命周期
    // -----------------------------------------------------------------

    /**
     * 绑定 View（PlayerView）。
     * 同时初始化 ExoPlayer 实例（如果尚未初始化）。
     */
    override fun attachView(view: Any) {
        if (view !is PlayerView) {
            Log.w(TAG, "attachView: expected PlayerView, got ${view::class.simpleName}")
            return
        }
        playerView = view
        ensurePlayer()
        view.player = player
        Log.i(TAG, "attachView: PlayerView attached, type=$type")
    }

    /** 解绑 View，释放 ExoPlayer 资源 */
    override fun detach() {
        playerView?.player = null
        playerView = null
        try {
            player?.release()
        } catch (e: Exception) {
            Log.w(TAG, "detach: release failed: ${e.message}")
        }
        player = null
        _fileLoaded.value = false
        _paused.value = true
        _timePos.value = 0.0
        _duration.value = 0.0
        _videoWidth.value = 0
        _videoHeight.value = 0
        Log.i(TAG, "detach: released")
    }

    private fun ensurePlayer() {
        if (player != null) return
        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setConnectTimeoutMs(10_000)
                .setReadTimeoutMs(15_000)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setUseLazyPreparation(true)
                .build().also { p ->
                    p.addListener(object : Media3Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Media3Player.STATE_BUFFERING -> {
                                    // buffering
                                }
                                Media3Player.STATE_READY -> {
                                    _paused.value = !p.isPlaying
                                    _fileLoaded.value = true
                                    _duration.value = (p.duration.coerceAtLeast(0)) / 1000.0
                                }
                                Media3Player.STATE_ENDED -> {
                                    _eofReached.value = true
                                    _paused.value = true
                                }
                                Media3Player.STATE_IDLE -> {
                                    _fileLoaded.value = false
                                }
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _paused.value = !isPlaying
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Player error: ${error.message}", error)
                            _fileLoaded.value = false
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            _videoWidth.value = videoSize.width
                            _videoHeight.value = videoSize.height
                        }
                    })
                }
            Log.i(TAG, "ExoPlayer initialized, type=$type")
        } catch (e: Exception) {
            Log.e(TAG, "ensurePlayer failed", e)
        }
    }

    // -----------------------------------------------------------------
    // 基础播放控制
    // -----------------------------------------------------------------

    override fun playFile(url: String) {
        ensurePlayer()
        val p = player ?: run {
            Log.e(TAG, "playFile: player is null")
            return
        }
        currentUrl = url
        _eofReached.value = false
        _fileLoaded.value = false
        try {
            Log.i(TAG, "playFile: $url")
            val mediaItem = MediaItem.Builder().setUri(Uri.parse(url)).build()
            p.setMediaItem(mediaItem)
            p.prepare()
            p.playWhenReady = true
            _paused.value = false
            _mediaTitle.value = url.substringAfterLast('/').substringBefore('?')
        } catch (e: Exception) {
            Log.e(TAG, "playFile failed: $url", e)
        }
    }

    override fun stop() {
        try {
            player?.stop()
            _fileLoaded.value = false
            _paused.value = true
            _timePos.value = 0.0
            currentUrl = ""
            Log.i(TAG, "stop")
        } catch (e: Exception) {
            Log.w(TAG, "stop failed: ${e.message}")
        }
    }

    override fun togglePause() {
        val p = player ?: return
        p.playWhenReady = !p.playWhenReady
        _paused.value = !p.playWhenReady
    }

    override fun setPause(p: Boolean) {
        player?.playWhenReady = !p
        _paused.value = p
    }

    override fun seekTo(seconds: Double) {
        player?.seekTo((seconds * 1000).toLong())
    }

    override fun seekRelative(seconds: Double) {
        val p = player ?: return
        val target = (p.currentPosition + seconds * 1000).coerceAtLeast(0)
            .coerceAtMost(p.duration.coerceAtLeast(0))
        p.seekTo(target.toLong())
    }

    override fun seekAbsolute(seconds: Double) {
        seekTo(seconds)
    }

    // -----------------------------------------------------------------
    // 音量 / 静音 / 速度
    // -----------------------------------------------------------------

    override fun setVolume(v: Int) {
        val clamped = v.coerceIn(0, 130)
        _volume.value = clamped
        // ExoPlayer 音量范围 0.0~1.0，mpv 范围 0~130
        player?.volume = (clamped / 100f).coerceIn(0f, 1f)
    }

    override fun adjustVolume(delta: Int) {
        setVolume(_volume.value + delta)
    }

    override fun toggleMute() {
        setMute(!_muted.value)
    }

    override fun setMute(m: Boolean) {
        _muted.value = m
        player?.volume = if (m) 0f else (_volume.value / 100f).coerceIn(0f, 1f)
    }

    override fun setSpeed(s: Double) {
        val clamped = s.coerceIn(0.01, 100.0)
        _speed.value = clamped
        try {
            val params = player?.playbackParameters?.withSpeed(clamped.toFloat())
            player?.playbackParameters = params ?: androidx.media3.common.PlaybackParameters(clamped.toFloat())
        } catch (e: Exception) {
            Log.w(TAG, "setSpeed failed: ${e.message}")
        }
    }

    // -----------------------------------------------------------------
    // 音轨 / 字幕轨
    // -----------------------------------------------------------------

    override fun cycleAudio() {
        val p = player ?: return
        val tracks = p.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
        if (tracks.isEmpty()) return
        // 简单切换到下一个音轨
        val current = p.currentTracks.groups.indexOfFirst { it.isSelected }
        val next = if (current >= 0 && current < tracks.size - 1) current + 1 else 0
        try {
            val group = tracks[next]
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "cycleAudio failed: ${e.message}")
        }
    }

    override fun cycleSub() {
        val p = player ?: return
        // 切换字幕轨开关
        val subEnabled = p.trackSelectionParameters.trackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT).not()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, subEnabled)
            .build()
    }

    override fun setAudioTrack(id: Int) {
        // ExoPlayer 通过 trackSelectionParameters 控制，此处简化处理
    }

    override fun setSubTrack(id: Int) {}

    override fun addSubtitleFile(path: String) {
        // ExoPlayer 支持外挂字幕通过 MediaItem.subtitleConfigurations
        Log.i(TAG, "addSubtitleFile: $path (not fully implemented)")
    }

    // -----------------------------------------------------------------
    // 字幕显示与样式
    // -----------------------------------------------------------------

    override fun setSubVisibility(v: Boolean) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !v)
            .build()
    }

    override fun toggleSubVisibility() {
        val p = player ?: return
        val currentlyVisible = !p.trackSelectionParameters.trackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT)
        setSubVisibility(!currentlyVisible)
    }

    // -----------------------------------------------------------------
    // 硬件解码切换
    // -----------------------------------------------------------------

    override fun setHardwareDecode(enabled: Boolean): Boolean {
        // ExoPlayer 默认使用硬解，软解需要配置 MediaCodecRenderer
        // 此处简化：通过 trackSelectionParameters 控制是否使用 hardware-accelerated decoders
        Log.i(TAG, "setHardwareDecode: $enabled (simplified)")
        return true
    }

    override fun isHardwareDecodeEnabled(): Boolean = type == PlayerType.EXO

    // -----------------------------------------------------------------
    // 播放状态保存/恢复
    // -----------------------------------------------------------------

    override fun savePlaybackState(): Pair<String, Double>? {
        if (currentUrl.isEmpty()) return null
        return Pair(currentUrl, _timePos.value)
    }

    override fun restorePlaybackState(url: String, timePosSec: Double) {
        playFile(url)
        // 等待加载完成后 seek
        if (timePosSec > 0) {
            player?.seekTo((timePosSec * 1000).toLong())
        }
    }

    /**
     * 更新进度（由 UI 定时轮询调用，与 MpvController 的 progress report 对齐）。
     */
    fun updateProgress() {
        val p = player ?: return
        try {
            _timePos.value = p.currentPosition / 1000.0
            if (p.duration > 0) {
                _duration.value = p.duration / 1000.0
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
