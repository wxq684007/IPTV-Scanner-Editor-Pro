package com.iptv.scanner.editor.pro.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import com.iptv.scanner.editor.pro.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * IJKPlayer 控制器：封装 tv.danmaku.ijk.media.player.IjkMediaPlayer，
 * 实现 [Player] 接口，与 MPV/ExoPlayer/VLC 实现统一 API。
 *
 * 设计要点：
 * 1. 持有一个持久 [ijkPlayer] 实例，[stop] 时 release + 重建，[playFile] 时 reset + 复用
 * 2. 用 [MutableStateFlow] 管理所有 14 个状态，与 MpvController 对齐
 * 3. 实现 IMediaPlayer 的 6 个回调（Prepared/Completion/Info/VideoSize/Error/SeekComplete）
 *    把 IJK 事件转发到 StateFlow，Compose 直接观察
 * 4. 用 [Handler] 轮询 currentPosition 更新 timePos（IJK 无属性观察机制）
 * 5. capabilities 仅声明 IJK 实际支持的功能（变速），
 *    其余高级功能（EQ/AB循环/逐帧/章节/截图/HDR/轨道列表 等）返回 false/no-op
 *
 * IJK 与 mpv 的差异：
 * - seekTo 用毫秒（mpv 用秒）
 * - setVolume 用 0.0~1.0 浮点（mpv 用 0~130 整数），内部做映射
 * - setSpeed 支持运行时变速（与 mpv 一致）
 * - 字幕：仅支持显示开关，不支持延迟/缩放/位置调整
 * - 外挂字幕：IJK 需在 setDataSource 之前设置，运行时不支持（capabilities 声明 false）
 * - 轨道切换：腾讯 IoT fork 不提供 TrackInfo/setAudioTrack/setSubtitleTrack，轨道切换不可用
 *
 * @see Player 接口定义
 * @see com.iptv.scanner.editor.pro.mpv.MpvController MPV 完整实现（参考模式）
 */
class IjkController(private val context: Context) : Player {

    // -----------------------------------------------------------------
    // IjkMediaPlayer 实例（持久，stop 时 release + 重建）
    // -----------------------------------------------------------------

    /**
     * IjkMediaPlayer 实例（可空）。
     *
     * 为可空的原因：腾讯 IoT 的 IJKPlayer fork 只提供 arm64 和 armv7a 两个 ABI 的 native 库，
     * 在 x86/x86_64 设备（主要是模拟器）上 IjkMediaPlayer 类初始化会因找不到 .so 而抛
     * UnsatisfiedLinkError / ExceptionInInitializerError。
     * 此时 [nativeAvailable] 为 false，[ijkPlayer] 为 null，所有方法安全降级。
     */
    @Volatile
    var ijkPlayer: IjkMediaPlayer? = null
        private set

    /**
     * native 库是否可用。
     *
     * false 的情况：x86/x86_64 设备上无 IJK native 库（.so 加载失败）。
     * 此时所有播放操作安全降级（返回 false / no-op / 空值），
     * UI 应提示用户切换到 MPV/ExoPlayer/VLC 播放器。
     */
    val nativeAvailable: Boolean

    /** 绑定的 IjkVideoView（由 attachView 设置） */
    @Volatile
    private var videoView: IjkVideoView? = null

    /** 当前 SurfaceHolder（由 IjkVideoView 回调同步） */
    @Volatile
    private var currentHolder: SurfaceHolder? = null

    /** 当前播放 URL（用于 savePlaybackState） */
    @Volatile
    private var currentUrl: String = ""

    /** 当前是否使用硬件解码（运行时可切换，影响 mediacodec option） */
    @Volatile
    private var hardwareDecode = true

    /** 挂起的 seek 位置（restorePlaybackState 设置，onPrepared 后执行） */
    @Volatile
    private var pendingSeek: Double? = null

    /** 字幕可见性（IJK 无查询 API，本地跟踪） */
    @Volatile
    private var subVisible = true

    /** 当前音轨 ID（1-based，用于 cycleAudio） */
    @Volatile
    private var currentAudioTrackId = 1

    /** 当前字幕轨 ID（1-based，用于 cycleSub） */
    @Volatile
    private var currentSubTrackId = 1

    /** 音轨总数（updateTrackInfo 时更新） */
    @Volatile
    private var audioTrackCount = 0

    /** 字幕轨总数 */
    @Volatile
    private var subTrackCount = 0

    /** 音轨 ID(1-based) → 轨道数组绝对索引 的映射（腾讯 IoT fork 无 TrackInfo，始终为空） */
    private val audioTrackIndices = mutableListOf<Int>()

    /** 字幕轨 ID(1-based) → 轨道数组绝对索引 的映射（腾讯 IoT fork 无 TrackInfo，始终为空） */
    private val subTrackIndices = mutableListOf<Int>()

    private val handler = Handler(Looper.getMainLooper())

    init {
        // 尝试创建 IjkMediaPlayer 实例。
        // 在 x86/x86_64 设备上，IjkMediaPlayer 类的 static 块加载 native 库时会抛
        // UnsatisfiedLinkError / ExceptionInInitializerError，此时 nativeAvailable=false。
        val (available, player) = tryCreatePlayer()
        nativeAvailable = available
        ijkPlayer = player
        if (!available) {
            Log.e(TAG, "IJK native library not available on this ABI (likely x86/x86_64). " +
                "IJK player will be disabled. Please use MPV/ExoPlayer/VLC instead.")
        }
    }

    /**
     * 尝试创建 IjkMediaPlayer 实例。
     * 返回 (nativeAvailable, player)：成功时 (true, 实例)，失败时 (false, null)。
     *
     * 捕获 Throwable（包括 RuntimeException、LinkageError 等），与 createPlayer 一致。
     * 注意：native SIGSEGV 无法被 Java try-catch 捕获，会直接导致进程闪退。
     * 对于 SIGSEGV 场景，通过 [markCrashed] 持久化标志位在下次启动时规避。
     */
    private fun tryCreatePlayer(): Pair<Boolean, IjkMediaPlayer?> {
        return try {
            val p = IjkMediaPlayer()
            setListeners(p)
            true to p
        } catch (e: Throwable) {
            Log.e(TAG, "tryCreatePlayer failed: ${e.javaClass.simpleName}: ${e.message}")
            false to null
        }
    }

    // -----------------------------------------------------------------
    // 14 个 StateFlow（与 MpvController 对齐）
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

    private val _trackListJson = MutableStateFlow("")
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

    /** 最近一次播放错误信息（UI 可监听并提示用户） */
    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    // -----------------------------------------------------------------
    // 能力声明
    // -----------------------------------------------------------------

    override val capabilities: PlayerCapabilities = PlayerCapabilities(
        supportsSpeedControl = true,
        supportsTrackList = false,
        supportsAddSubtitleFile = false,
        supportsHardwareDecodeSwitch = true
    )

    override val playerType: PlayerType = PlayerType.IJK

    // -----------------------------------------------------------------
    // 实例创建与监听器
    // -----------------------------------------------------------------

    /** 创建新的 IjkMediaPlayer 实例并设置所有回调（native 不可用时返回 null） */
    private fun createPlayer(): IjkMediaPlayer? {
        return try {
            val p = IjkMediaPlayer()
            setListeners(p)
            p
        } catch (e: Throwable) {
            Log.e(TAG, "createPlayer failed: ${e.message}")
            null
        }
    }

    /** 注册 6 个回调监听器（SAM 转换） */
    private fun setListeners(p: IjkMediaPlayer) {
        p.setOnPreparedListener { mp -> onPrepared(mp) }
        p.setOnCompletionListener { mp -> onCompletion(mp) }
        // 腾讯 IoT fork 的 OnInfoListener 接口扩展了两个方法：
        // - onInfoSEI(mp, what, extra, sei): SEI 信息回调（直播场景的 SEI 数据）
        // - onInfoAudioPcmData(mp, data, size): 音频 PCM 数据回调
        // 必须实现这两个方法，否则编译报错 "does not implement abstract member"
        p.setOnInfoListener(object : IMediaPlayer.OnInfoListener {
            override fun onInfo(mp: IMediaPlayer?, what: Int, extra: Int): Boolean {
                // 必须用 this@IjkController 限定，否则递归调用本匿名对象的 onInfo（自身）
                // 导致 StackOverflowError
                this@IjkController.onInfo(mp, what, extra)
                return true
            }
            override fun onInfoSEI(mp: IMediaPlayer?, what: Int, extra: Int, sei: String?): Boolean {
                // SEI 信息回调，暂不处理
                return false
            }
            override fun onInfoAudioPcmData(mp: IMediaPlayer?, data: ByteArray?, size: Int) {
                // 音频 PCM 数据回调，暂不处理
            }
        })
        p.setOnVideoSizeChangedListener { mp, width, height, _, _ ->
            onVideoSizeChanged(mp, width, height)
        }
        p.setOnErrorListener { mp, what, extra ->
            onError(mp, what, extra)
            true
        }
        p.setOnSeekCompleteListener { mp -> onSeekComplete(mp) }
    }

    /** 应用默认解码/网络选项（每次 playFile 时在 reset 后调用） */
    private fun applyDefaultOptions(p: IjkMediaPlayer?) {
        if (p == null) return
        // OPT_CATEGORY_PLAYER：硬件解码 + RTSP over TCP
        // mediacodec：1=硬解，0=软解（根据 hardwareDecode 状态切换）
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", if (hardwareDecode) 1L else 0L)
        if (hardwareDecode) {
            p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
        }
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "rtsp-tcp", 1L)
        // 音频输出：使用 AudioTrack（而非 OpenSL ES）。
        // 根因：部分 Android TV（如 MTK 平台）的音频 HAL 使用 PCM_32_BIT 格式，
        // OpenSL ES 与该格式不兼容导致静默输出（dumpsys media.audio_flinger 显示 No output streams）。
        // AudioTrack 直接使用 Android 音频框架，兼容性更好，能自动处理格式转换。
        // 之前用 opensles=1 在某些设备上正常，但在 PCM_32_BIT HAL 设备上无声音，
        // 统一改为 AudioTrack 以确保最大兼容性。
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)
        // 禁用 SoundTouch 音频变速处理：部分流上 SoundTouch 会引入音频丢失/静音问题。
        // 禁用后 IJK 用原生 sample rate 输出 PCM，避免变速处理导致的兼容性问题。
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 0L)

        /* 低延迟/快速切台选项（与 FCC 配合）：
         *
         * IJK 默认 min-frames=50000（需解码 5 万帧才开始播放），导致切台极慢。
         * 以下选项让 IJK 在收到少量帧后立即开始渲染，与 VLC/MPV 的快速起播对齐：
         *
         * - start-on-prepared=1：prepared 后立即开始播放（不等待额外缓冲）
         * - min-frames=5：仅需 5 帧解码即可开始渲染（默认 50000，差距巨大）
         * - first-packet-timeout=3000：首个包超时 3 秒（避免无效源长时间等待）
         * - rendered-trigger=1：渲染触发条件设为 1（最早可能的渲染时机）
         *
         * 配合 FCC JOIN 通知（在 playFile 之前发送），IJK 可在 FCC 代理转发新流后
         * 快速接收并渲染首帧，大幅减少切台等待时间。
         */
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 5L)
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "first-packet-timeout", 3000L)
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "rendered-trigger", 1L)

        // OPT_CATEGORY_FORMAT：缓冲与封包
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 5121024L)
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "infbuf", 1L)
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "packet-buffering", 0L)
        // OPT_CATEGORY_CODEC：解码循环过滤（8 = skip all，提升性能）
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 8L)
        // 音频解码选项：启用音频重采样，确保 PCM 输出格式被 AudioTrack 接受
        // 部分流的音频 sample rate（如 22050）或 channel count 不被设备 AudioTrack 直接支持，
        // 启用 swresample 强制重采样到设备支持的格式。
        p.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "swresample", 1L)
        Log.i(TAG, "applyDefaultOptions: hardwareDecode=$hardwareDecode, opensles=0(AudioTrack), soundtouch=0, swresample=1, min-frames=5, start-on-prepared=1")
    }

    // -----------------------------------------------------------------
    // IMediaPlayer 回调处理
    // -----------------------------------------------------------------

    private fun onPrepared(mp: IMediaPlayer) {
        Log.i(TAG, "onPrepared")
        // onPrepared 触发说明 IJK native 正常工作，清除启动崩溃保护标志
        UserPrefs.getInstance().clearIjkTesting()
        _fileLoaded.value = true
        _eofReached.value = false
        _paused.value = false
        // 获取时长（IJK 在 prepared 后才能取到）
        try {
            val durMs = mp.duration
            if (durMs > 0) _duration.value = durMs / 1000.0
        } catch (e: Exception) {
            Log.w(TAG, "onPrepared getDuration failed: ${e.message}")
        }
        // 音频诊断：IJK 无声音问题排查。
        // getMediaInfo 返回 IjkMediaInfo 包含 mAudioDecoder（音频解码器名称），
        // 若 mAudioDecoder 为空说明音频流未被识别或解码器未加载。
        try {
            val mi = (mp as? IjkMediaPlayer)?.mediaInfo
            Log.i(TAG, "onPrepared: audioDecoder=${mi?.mAudioDecoder}, videoDecoder=${mi?.mVideoDecoder}")
        } catch (e: Exception) {
            Log.w(TAG, "onPrepared: getMediaInfo failed: ${e.message}")
        }
        // 视频尺寸（用于判断流是否被正确解析）
        try {
            Log.i(TAG, "onPrepared: videoWidth=${mp.videoWidth}, videoHeight=${mp.videoHeight}")
        } catch (e: Exception) { }
        // 解析轨道信息（音轨/字幕轨）
        updateTrackInfo()
        // 执行挂起的 seek（restorePlaybackState 设置的恢复位置）
        pendingSeek?.let { seek ->
            pendingSeek = null
            try {
                mp.seekTo((seek * 1000).toLong())
            } catch (e: Exception) {
                Log.w(TAG, "onPrepared pending seek failed: ${e.message}")
            }
        }
        // prepareAsync 只准备不播放，需显式 start
        try {
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "onPrepared start failed: ${e.message}")
        }
        // 恢复音量/速度到之前的设置（reset 会清除）
        applyVolume()
        if (_speed.value != 1.0) {
            try {
                (mp as? IjkMediaPlayer)?.setSpeed(_speed.value.toFloat())
            } catch (e: Exception) { }
        }
        startPolling()
    }

    private fun onCompletion(mp: IMediaPlayer) {
        Log.i(TAG, "onCompletion")
        _eofReached.value = true
        _paused.value = true
        stopPolling()
    }

    private fun onInfo(mp: IMediaPlayer?, what: Int, extra: Int) {
        when (what) {
            IMediaPlayer.MEDIA_INFO_BUFFERING_START ->
                Log.d(TAG, "info: buffering start")
            IMediaPlayer.MEDIA_INFO_BUFFERING_END ->
                Log.d(TAG, "info: buffering end")
            IMediaPlayer.MEDIA_INFO_METADATA_UPDATE -> {
                // 元数据更新时刷新轨道列表
                updateTrackInfo()
            }
            // 音频诊断：记录音频渲染开始和错误事件，排查无声音问题
            // 腾讯 IoT fork 扩展的 info 码：
            // 10010 = AUDIO_RENDERING_START（音频开始渲染，若未触发说明音频输出未启动）
            // 10012 = AUDIO_DECODE_ERROR（音频解码错误）
            // 10013 = AUDIO_START_ERROR（音频启动错误，如 OpenSL ES 初始化失败）
            10010 -> Log.i(TAG, "info: AUDIO_RENDERING_START (audio output started)")
            10012 -> Log.e(TAG, "info: AUDIO_DECODE_ERROR (extra=$extra)")
            10013 -> Log.e(TAG, "info: AUDIO_START_ERROR (extra=$extra)")
            else -> Log.d(TAG, "info: what=$what extra=$extra")
        }
    }

    private fun onVideoSizeChanged(mp: IMediaPlayer, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            _videoWidth.value = width
            _videoHeight.value = height
        }
    }

    private fun onError(mp: IMediaPlayer, what: Int, extra: Int) {
        Log.e(TAG, "onError: what=$what extra=$extra")
        _fileLoaded.value = false
        // 错误时必须更新 _paused：之前只更新 _fileLoaded，
        // 导致 _paused 保持 false（onPrepared 已设置）但实际播放已停止，UI 状态不一致。
        // 切换播放器后若 onPrepared 未触发（URL 不支持/解码失败），_paused 保持初始值 true，UI 显示"已暂停"。
        _paused.value = true
        // 记录错误信息供 UI 显示
        _lastError.value = when (what) {
            IMediaPlayer.MEDIA_ERROR_UNKNOWN -> when (extra) {
                IMediaPlayer.MEDIA_ERROR_IO -> "IO 错误（网络或文件读取失败）"
                IMediaPlayer.MEDIA_ERROR_MALFORMED -> "媒体数据格式错误"
                IMediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "媒体格式不支持"
                IMediaPlayer.MEDIA_ERROR_TIMED_OUT -> "操作超时"
                else -> "未知错误 ($what/$extra)"
            }
            IMediaPlayer.MEDIA_ERROR_SERVER_DIED -> "媒体服务已停止"
            else -> "播放错误 ($what/$extra)"
        }
    }

    private fun onSeekComplete(mp: IMediaPlayer) {
        try {
            _timePos.value = mp.currentPosition / 1000.0
        } catch (e: Exception) {
            Log.w(TAG, "onSeekComplete currentPosition failed: ${e.message}")
        }
    }

    // -----------------------------------------------------------------
    // 位置轮询（Handler.postDelayed，间隔 500ms）
    // -----------------------------------------------------------------

    private val positionPollRunnable = object : Runnable {
        override fun run() {
            try {
                val p = ijkPlayer
                if (p != null && !_paused.value && _fileLoaded.value) {
                    val posMs = p.currentPosition
                    if (posMs >= 0) _timePos.value = posMs / 1000.0
                }
            } catch (e: Exception) {
                Log.w(TAG, "poll currentPosition failed: ${e.message}")
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun startPolling() {
        handler.removeCallbacks(positionPollRunnable)
        handler.postDelayed(positionPollRunnable, POLL_INTERVAL_MS)
    }

    private fun stopPolling() {
        handler.removeCallbacks(positionPollRunnable)
    }

    // -----------------------------------------------------------------
    // 生命周期
    // -----------------------------------------------------------------

    override fun attachView(view: Any) {
        if (view is IjkVideoView) {
            videoView = view
            view.attachController(this)
            Log.i(TAG, "attachView: IjkVideoView attached")
        } else {
            Log.w(TAG, "attachView: unsupported view type ${view.javaClass.name}")
        }
    }

    override fun detach() {
        stopPolling()
        // 切换播放器时先 stop() 确保播放完全停止，再解绑 Surface。
        // 根因：IjkMediaPlayer.release() 会释放内部 native 渲染资源（如 OpenGL 纹理），
        // 但若此时仍在播放，RenderThread 下一帧渲染会访问已释放资源导致 SIGSEGV（fault addr 0x8）。
        // stop() 让 IjkMediaPlayer 进入 Stopped 状态，停止解码和渲染线程，
        // 随后 setDisplay(null) 解绑 Surface。
        try {
            ijkPlayer?.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "detach stop failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        // setDisplay(null) 让 IjkMediaPlayer 停止渲染到 Surface，RenderThread 不再访问。
        try {
            ijkPlayer?.setDisplay(null)
        } catch (e: Throwable) {
            Log.w(TAG, "detach setDisplay(null) failed: ${e.javaClass.simpleName}: ${e.message}")
        }
        videoView = null
        currentHolder = null
        // 正常 detach 也清除 IJK 测试标志：说明用户主动切走（不是崩溃），
        // 下次启动不会因 isIjkTesting=true 误判为崩溃。
        UserPrefs.getInstance().clearIjkTesting()
        // 关键修复：延迟 release() 到主线程 postDelayed(200ms) 后执行。
        // 根因：SurfaceView 的 surfaceDestroyed 是异步的，onRelease 后 Surface 可能仍有效，
        // stop()/setDisplay(null) 也是异步的，native 渲染线程可能还在运行。
        // 立即 release() 释放 native 资源后，RenderThread 仍渲染 → SIGSEGV @ 0x8。
        // postDelayed(200ms) 让 RenderThread 有时间处理完最后一帧并停止。
        // 将 ijkPlayer 保存到局部变量，实例字段立即置 null：
        // - 避免 detach 后 attachSurface 访问已 release 的实例
        // - 若用户快速切换播放器，新 IjkController 会创建新实例，不影响旧实例的延迟 release
        val playerToRelease = ijkPlayer
        ijkPlayer = null
        currentUrl = ""
        // 重置状态（避免 Compose 用旧值）
        _fileLoaded.value = false
        _eofReached.value = false
        _timePos.value = 0.0
        _duration.value = 0.0
        _paused.value = true
        _trackListJson.value = ""
        _mediaTitle.value = ""
        _videoWidth.value = 0
        _videoHeight.value = 0
        Log.i(TAG, "IjkController detached, release deferred 200ms")
        handler.postDelayed({
            try {
                playerToRelease?.release()
            } catch (e: Throwable) {
                Log.w(TAG, "deferred release failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }, RELEASE_DELAY_MS)
    }

    /**
     * Surface 回调（由 IjkVideoView 调用）：
     * 更新 currentHolder 并绑定到 ijkPlayer.setDisplay。
     */
    fun attachSurface(holder: SurfaceHolder?) {
        currentHolder = holder
        try {
            ijkPlayer?.setDisplay(holder)
        } catch (e: Exception) {
            Log.w(TAG, "attachSurface setDisplay failed: ${e.message}")
        }
    }

    // -----------------------------------------------------------------
    // 基础播放控制
    // -----------------------------------------------------------------

    override fun playFile(url: String) {
        Log.i(TAG, "playFile: $url")
        if (!nativeAvailable) {
            Log.e(TAG, "playFile: IJK native library not available on this device")
            _lastError.value = "IJK native 库不可用（需 ARM 架构设备）"
            return
        }
        currentUrl = url
        _eofReached.value = false
        _fileLoaded.value = false
        _lastError.value = ""
        _timePos.value = 0.0
        _duration.value = 0.0
        _trackListJson.value = ""
        _videoWidth.value = 0
        _videoHeight.value = 0
        audioTrackIndices.clear()
        subTrackIndices.clear()
        audioTrackCount = 0
        subTrackCount = 0

        // reset 可能因实例已 release 而抛异常，此时重建实例
        try {
            ijkPlayer?.reset()
        } catch (e: Exception) {
            Log.w(TAG, "playFile: reset failed, recreating player: ${e.message}")
            ijkPlayer = createPlayer()
        }

        val p = ijkPlayer
        if (p == null) {
            Log.e(TAG, "playFile: IjkMediaPlayer instance is null")
            _lastError.value = "IJK 播放器实例不可用"
            return
        }

        applyDefaultOptions(p)

        // 设置数据源（content:// URI 需用 Context+Uri 重载）
        try {
            if (url.startsWith("content://")) {
                p.setDataSource(context, Uri.parse(url))
            } else {
                p.setDataSource(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "playFile: setDataSource failed", e)
            _lastError.value = "数据源设置失败: ${e.message ?: "未知错误"}"
            return
        }

        // 绑定 Surface（如果已就绪）
        currentHolder?.let { holder ->
            try {
                p.setDisplay(holder)
            } catch (e: Exception) {
                Log.w(TAG, "playFile: setDisplay failed: ${e.message}")
            }
        }

        // 媒体标题（从 URL 提取文件名，去 query 参数）
        val title = url.substringAfterLast('/').substringBefore('?')
        _mediaTitle.value = title.ifEmpty { url }

        try {
            p.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "playFile: prepareAsync failed", e)
            _lastError.value = "准备播放失败: ${e.message ?: "未知错误"}"
        }
    }

    override fun stop() {
        Log.i(TAG, "stop")
        stopPolling()
        _fileLoaded.value = false
        _eofReached.value = false
        _timePos.value = 0.0
        _duration.value = 0.0
        _trackListJson.value = ""
        audioTrackIndices.clear()
        subTrackIndices.clear()
        audioTrackCount = 0
        subTrackCount = 0
        try {
            ijkPlayer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop: stop failed: ${e.message}")
        }
        try {
            ijkPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "stop: release failed: ${e.message}")
        }
        // 重新创建实例供下次播放使用（native 不可用时为 null）
        ijkPlayer = createPlayer()
    }

    override fun togglePause() {
        setPause(!_paused.value)
    }

    override fun setPause(p: Boolean) {
        _paused.value = p
        try {
            if (p) ijkPlayer?.pause() else ijkPlayer?.start()
        } catch (e: Exception) {
            Log.w(TAG, "setPause($p) failed: ${e.message}")
        }
    }

    override fun seekTo(seconds: Double) {
        try {
            ijkPlayer?.seekTo((seconds * 1000).toLong())
            _timePos.value = seconds
        } catch (e: Exception) {
            Log.w(TAG, "seekTo($seconds) failed: ${e.message}")
        }
    }

    override fun seekRelative(seconds: Double) {
        seekTo(_timePos.value + seconds)
    }

    override fun seekAbsolute(seconds: Double) {
        seekTo(seconds)
    }

    // -----------------------------------------------------------------
    // 音量 / 静音 / 速度
    // -----------------------------------------------------------------

    override fun setVolume(v: Int) {
        _volume.value = v.coerceIn(0, 130)
        applyVolume()
    }

    override fun adjustVolume(delta: Int) {
        setVolume(_volume.value + delta)
    }

    override fun toggleMute() {
        setMute(!_muted.value)
    }

    override fun setMute(m: Boolean) {
        _muted.value = m
        applyVolume()
    }

    /**
     * 把当前音量/静音状态应用到 IjkMediaPlayer。
     * IJK setVolume 接受 0.0~1.0 浮点，mpv 用 0~130 整数，映射：ijkVol = mpvVol / 130。
     */
    private fun applyVolume() {
        val vol = if (_muted.value) 0f else _volume.value / 130f
        try {
            ijkPlayer?.setVolume(vol, vol)
        } catch (e: Exception) {
            Log.w(TAG, "applyVolume failed: ${e.message}")
        }
    }

    override fun setSpeed(s: Double) {
        val sp = s.coerceIn(0.25, 4.0)
        _speed.value = sp
        try {
            ijkPlayer?.setSpeed(sp.toFloat())
        } catch (e: Exception) {
            Log.w(TAG, "setSpeed($sp) failed: ${e.message}")
        }
    }

    // -----------------------------------------------------------------
    // 音轨 / 字幕轨
    // -----------------------------------------------------------------

    override fun cycleAudio() {
        if (audioTrackCount <= 0) return
        currentAudioTrackId = if (currentAudioTrackId >= audioTrackCount) 1 else currentAudioTrackId + 1
        setAudioTrack(currentAudioTrackId)
    }

    override fun cycleSub() {
        if (subTrackCount <= 0) return
        currentSubTrackId = if (currentSubTrackId >= subTrackCount) 1 else currentSubTrackId + 1
        setSubTrack(currentSubTrackId)
    }

    /**
     * 设置音轨。
     * 腾讯 IoT IJKPlayer fork 不提供 setAudioTrack 方法，此方法为 no-op。
     */
    override fun setAudioTrack(id: Int) {
        Log.w(TAG, "setAudioTrack: not supported by this IJK fork")
    }

    /**
     * 设置字幕轨。
     * 腾讯 IoT IJKPlayer fork 不提供 setSubtitleTrack 方法，此方法为 no-op。
     */
    override fun setSubTrack(id: Int) {
        Log.w(TAG, "setSubTrack: not supported by this IJK fork")
    }

    /**
     * 加载外挂字幕文件。
     * IJK 需要在 setDataSource 之前设置字幕路径，运行时不支持（声明 capabilities=false）。
     */
    override fun addSubtitleFile(path: String) {
        Log.w(TAG, "addSubtitleFile not supported by IJK (requires setting before setDataSource)")
    }

    // -----------------------------------------------------------------
    // 字幕显示与样式
    // -----------------------------------------------------------------

    override fun setSubVisibility(v: Boolean) {
        subVisible = v
        try {
            ijkPlayer?.setOption(
                IjkMediaPlayer.OPT_CATEGORY_PLAYER,
                "subtitle",
                if (v) 1L else 0L
            )
        } catch (e: Exception) {
            Log.w(TAG, "setSubVisibility($v) failed: ${e.message}")
        }
    }

    override fun toggleSubVisibility() {
        setSubVisibility(!subVisible)
    }

    /** IJK 不支持运行时字幕延迟 */
    override fun setSubDelay(delaySec: Double) {
        Log.d(TAG, "setSubDelay: not supported by IJK")
    }

    override fun adjustSubDelay(delta: Double) {
        Log.d(TAG, "adjustSubDelay: not supported by IJK")
    }

    /** IJK 不支持字幕缩放 */
    override fun setSubScale(scale: Double) {
        Log.d(TAG, "setSubScale: not supported by IJK")
    }

    /** IJK 不支持字幕位置调整 */
    override fun setSubPos(pos: Int) {
        Log.d(TAG, "setSubPos: not supported by IJK")
    }

    // -----------------------------------------------------------------
    // 轨道列表与媒体信息
    // -----------------------------------------------------------------

    /**
     * 更新轨道列表。
     * 腾讯 IoT IJKPlayer fork 不包含 TrackInfo 类，轨道列表不可用，此方法为空实现。
     */
    private fun updateTrackInfo() {
        _trackListJson.value = ""
        audioTrackCount = 0
        subTrackCount = 0
    }

    /**
     * 获取媒体信息（从 IjkMediaPlayer.getMediaInfo 提取）。
     * UI 的 MediaBadgesRow 主要用 getPropertyX（IJK 返回 null 显示 N/A），
     * 此方法作为接口契约的补充实现。
     */
    override fun getMediaInfo(): Map<String, String?> {
        return try {
            val mi = ijkPlayer?.mediaInfo ?: return emptyMap()
            buildMap {
                put("player", mi.mMediaPlayerName)
                put("video-decoder", mi.mVideoDecoder)
                put("audio-decoder", mi.mAudioDecoder)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getMediaInfo failed: ${e.message}")
            emptyMap()
        }
    }

    // -----------------------------------------------------------------
    // mpv 兼容属性读取（让 StreamQualityPanel 能显示 IJK 的媒体信息）
    //
    // IJK 的 API 较有限，主要提供分辨率、时长、解码器名称。
    // 编解码器、帧率、码率等需要从 IjkMediaPlayer.getMediaInfo 解析。
    // -----------------------------------------------------------------

    override fun getPropertyString(name: String): String? = when (name) {
        "vo" -> "ijk"
        "hwdec-current" -> if (hardwareDecode) "mediacodec" else "ffmpeg"
        "video-codec", "video-format" -> {
            // 从 mediaInfo.mVideoDecoder 获取（如 "mediacodec" 或 "ffmpeg"）
            try { ijkPlayer?.mediaInfo?.mVideoDecoder } catch (e: Exception) { null }
        }
        "audio-codec", "audio-codec-name" -> {
            try { ijkPlayer?.mediaInfo?.mAudioDecoder } catch (e: Exception) { null }
        }
        "file-format", "demuxer" -> {
            val url = currentUrl.lowercase()
            when {
                url.contains(".m3u8") -> "hls"
                url.contains(".ts") -> "mpegts"
                url.startsWith("rtsp://") -> "rtsp"
                else -> null
            }
        }
        "protocol" -> {
            val url = currentUrl.lowercase()
            when {
                url.startsWith("https://") -> "https"
                url.startsWith("http://") -> "http"
                url.startsWith("rtsp://") -> "rtsp"
                url.startsWith("udp://") -> "udp"
                url.startsWith("rtp://") -> "rtp"
                else -> null
            }
        }
        else -> null
    }

    override fun getPropertyInt(name: String): Int? = when (name) {
        "width" -> _videoWidth.value.takeIf { it > 0 }
        "height" -> _videoHeight.value.takeIf { it > 0 }
        "dwidth" -> _videoWidth.value.takeIf { it > 0 }
        "dheight" -> _videoHeight.value.takeIf { it > 0 }
        else -> null
    }

    // -----------------------------------------------------------------
    // 硬件解码切换
    // -----------------------------------------------------------------

    /**
     * 切换硬件/软件解码。
     *
     * IJK 通过 `mediacodec` option 控制（1=硬解，0=软解），
     * 该 option 在每次 playFile 的 reset 后应用，所以切换后需重新播放当前 URL。
     */
    override fun setHardwareDecode(enabled: Boolean): Boolean {
        if (hardwareDecode == enabled) return true
        hardwareDecode = enabled
        Log.i(TAG, "setHardwareDecode: enabled=$enabled")
        if (!nativeAvailable) return false
        // 重新播放当前 URL 以应用新 mediacodec option
        if (currentUrl.isNotEmpty() && _fileLoaded.value) {
            val pos = _timePos.value
            playFile(currentUrl)
            if (pos > 0) {
                pendingSeek = pos
            }
        }
        return true
    }

    override fun isHardwareDecodeEnabled(): Boolean = hardwareDecode

    // -----------------------------------------------------------------
    // 播放状态保存/恢复（用于切换播放器时保持连续性）
    // -----------------------------------------------------------------

    override fun savePlaybackState(): Pair<String, Double>? {
        if (currentUrl.isEmpty() || !_fileLoaded.value) return null
        return currentUrl to _timePos.value
    }

    override fun restorePlaybackState(url: String, timePosSec: Double) {
        // 记录挂起 seek，onPrepared 后执行
        pendingSeek = if (timePosSec > 0) timePosSec else null
        playFile(url)
    }

    companion object {
        private const val TAG = "IjkController"
        private const val POLL_INTERVAL_MS = 500L
        /** detach 后延迟 release 的时间（毫秒），让 RenderThread 停止渲染避免 SIGSEGV */
        private const val RELEASE_DELAY_MS = 200L
    }
}
