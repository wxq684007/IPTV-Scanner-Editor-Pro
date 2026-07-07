package com.iptv.scanner.editor.pro.mpv

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import `is`.xyz.mpv.MPVLib
import com.iptv.scanner.editor.pro.data.UserPrefs

/**
 * 基于 SurfaceView 的 MPV 视频渲染视图。
 *
 * 生命周期策略（keep-alive）：
 * - MPVLib.create() 只在进程首次使用 MPV 时调用一次
 * - MPVLib.destroy() **永远不调用**（进程退出时 OS 自动回收）
 * - 切换播放器时：destroy() 只做 stop + playlist-clear + detachSurface
 * - 切回 MPV 时：initialize() 复用实例，surfaceCreated() 重新 attachSurface + 设置 vo
 *
 * 根因：MPVLib.destroy() 在部分设备（如华为 Kirin arm64）上会 SIGSEGV，
 * 即使已先调用 stop() + detachSurface()。mpv 内部线程的清理时机不可控，
 * destroy() 可能在线程仍持有锁/资源时释放底层结构，导致 use-after-free。
 * keep-alive 策略从根本上避免 destroy() 调用，以内存换稳定性。
 *
 * 状态重置（防止多次复用后状态累积残留）：
 * - destroy() 中：stop + playlist-clear + vo=null + force-window=no + detachSurface
 * - surfaceCreated() 中：attachSurface + force-window=yes + vo=voInUse
 * - playlist-clear 清除 mpv 内部播放列表，防止旧 path 残留导致双重 loadfile
 * - vo=null → vo=voInUse 的切换强制 vo 模块重新初始化，防止渲染状态累积
 */
class MPVView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    @JvmOverloads
    fun initialize(
        configDir: String,
        cacheDir: String,
        vo: String = DEFAULT_VO,
        hwdec: String = DEFAULT_HWDEC
    ) {
        voInUse = vo
        myGeneration = ++activeGeneration
        Log.i(TAG, "initialize: generation=$myGeneration, vo=$vo, hwdec=$hwdec")

        if (nativeInstanceCreated) {
            // 复用现有 native mpv 实例（不 destroy + create）
            Log.i(TAG, "initialize: reusing existing native mpv instance (generation=$myGeneration)")
            try {
                // 运行时更新 hwdec（vo 由 surfaceCreated 设置，此时 surface 还没创建）
                MPVLib.setPropertyString("hwdec", hwdec)
                // 更新日志等级（运行时可切换）
                updateLogLevel()
            } catch (e: Throwable) {
                Log.w(TAG, "initialize: reuse setPropertyString failed: ${e.message}")
            }
            // 清除 filePath，防止 surfaceCreated 走 filePath 分支加载旧路径
            filePath = null
            // 标记实例为活跃（surfaceDestroyed/destroy 会设为 false）
            nativeInstanceAlive = true
            holder.setFormat(PixelFormat.RGBA_8888)
            holder.addCallback(this)
            return
        }

        // 首次创建：MPVLib.create + 完整初始化
        Log.i(TAG, "initialize: creating new native mpv instance")
        MPVLib.create(context)

        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
            MPVLib.setOptionString(opt, cacheDir)

        MPVLib.setOptionString("vo", vo)
        MPVLib.setOptionString("hwdec", hwdec)
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("keepaspect", "yes")
        MPVLib.setOptionString("keepaspect-window", "no")
        // HDR 支持：使用 16-bit 浮点 FBO 格式，允许 HDR 内容以高色深渲染
        // 8-bit rgba8 会截断 HDR 信号为 SDR 导致色彩信息丢失
        MPVLib.setOptionString("fbo-format", "rgba16hf")
        // 启用 target-colorspace-hint 让 Android 系统自动切换 HDR 显示模式
        // mpv 通过 SurfaceView 传递 HDR 元数据给系统，系统自动切换到 HDR 显示
        MPVLib.setOptionString("target-colorspace-hint", "yes")

        MPVLib.setOptionString("framedrop", "all")
        MPVLib.setOptionString("video-sync", "audio")
        MPVLib.setOptionString("cache-pause-initial", "no")

        MPVLib.setOptionString("demuxer-max-bytes", "128MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", "64MiB")
        MPVLib.setOptionString("demuxer-readahead-secs", "10")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("force-seekable", "yes")
        // 不等待缓存填满就开始播放，实现秒开（与 PC 端 cache-pause-initial=no 配合）
        MPVLib.setOptionString("demuxer-cache-wait", "no")

        MPVLib.setOptionString("network-timeout", "30")
        MPVLib.setOptionString("source-timeout", "10")
        MPVLib.setOptionString("stream-open-timeout", "30")

        MPVLib.setOptionString("stream-lavf-o", "verify=1")
        MPVLib.setOptionString("tls-verify", "yes")

        val cpuCount = Runtime.getRuntime().availableProcessors()
        val threads = maxOf(2, cpuCount / 2)
        MPVLib.setOptionString("vd-lavc-threads", threads.toString())

        MPVLib.init()

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")

        updateLogLevel()

        nativeInstanceAlive = true
        nativeInstanceCreated = true

        holder.setFormat(PixelFormat.RGBA_8888)
        holder.addCallback(this)
        observeProperties()
    }

    /**
     * 更新 mpv 日志等级（运行时切换）。
     * 通过 setPropertyString("msg-level", ...) 实现，无需重启。
     */
    private fun updateLogLevel() {
        try {
            val logLevel = UserPrefs.getInstance().getLogLevel()
            val mpvMsgLevel = when (logLevel) {
                "debug" -> "all=trace"
                "info" -> "all=info"
                "warn" -> "all=warn"
                "error" -> "all=error"
                else -> "all=info"
            }
            MPVLib.setPropertyString("msg-level", mpvMsgLevel)
        } catch (e: Throwable) {
            Log.w(TAG, "updateLogLevel failed: ${e.message}")
        }
    }

    fun destroy() {
        holder.removeCallback(this)
        if (myGeneration != activeGeneration) {
            Log.i(TAG, "destroy: skipped (myGen=$myGeneration, activeGen=$activeGeneration)")
            return
        }
        // keep-alive：不调用 MPVLib.destroy()，只做状态重置
        if (nativeInstanceAlive) {
            try {
                MPVLib.command(arrayOf("stop"))
                // playlist-clear 清除 mpv 内部播放列表，防止下次复用时旧 path 残留
                MPVLib.command(arrayOf("playlist-clear"))
                MPVLib.setPropertyString("force-window", "no")
                // 设置 vo=null 释放 vo 模块资源（GPU/EGL 上下文），
                // surfaceCreated 时会重新设置 vo=voInUse 恢复渲染
                MPVLib.setPropertyString("vo", "null")
                MPVLib.detachSurface()
            } catch (e: Throwable) {
                Log.w(TAG, "destroy: reset failed: ${e.message}")
            }
            nativeInstanceAlive = false
            Log.i(TAG, "destroy: instance kept alive, state reset (gen=$myGeneration)")
        }
    }

    private fun observeProperties() {
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("volume", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("mute", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("media-title", MPVLib.MpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty("track-list", MPVLib.MpvFormat.MPV_FORMAT_NODE)
    }

    private var filePath: String? = null
    private var voInUse: String = DEFAULT_VO

    @Volatile
    private var myGeneration: Int = 0

    @Volatile
    var pendingResumePos: Double = -1.0

    fun setVoInUse(vo: String) {
        voInUse = vo
    }

    val isSurfaceValid: Boolean
        get() = holder.surface != null && holder.surface.isValid

    /**
     * 运行时强制重新初始化 VO 模块（detach → vo=null → reattach → vo=newVo）。
     *
     * 根因：仅 setPropertyString("vo", newVo) 在部分设备上不会真正释放旧 VO 模块
     * 并初始化新 VO。例如从 gpu（EGL）切换到 mediacodec_embed（Surface 直渲），
     * mpv 可能仍使用旧 gpu VO 导致黑屏。
     *
     * 本方法通过完整的 detach/reattach 循环强制 VO 模块重建：
     * 1. force-window=no + vo=null + detachSurface → 释放旧 VO 资源
     * 2. attachSurface + force-window=yes + vo=newVo → 初始化新 VO
     *
     * 调用后需重新 loadfile 触发新 VO 渲染。
     */
    fun reattachSurfaceWithVo(vo: String) {
        voInUse = vo
        Log.i(TAG, "reattachSurfaceWithVo: switching to vo=$vo")
        try {
            // Step 1: 释放当前 VO 模块
            MPVLib.setPropertyString("force-window", "no")
            MPVLib.setPropertyString("vo", "null")
            MPVLib.detachSurface()
            Log.i(TAG, "reattachSurfaceWithVo: detached old VO")

            // Step 2: 重新 attach surface 并设置新 VO
            val s = holder.surface
            if (s != null && s.isValid) {
                MPVLib.attachSurface(s)
                MPVLib.setOptionString("force-window", "yes")
                MPVLib.setPropertyString("vo", vo)
                Log.i(TAG, "reattachSurfaceWithVo: attached surface with vo=$vo")
            } else {
                // Surface 不可用：只设置 vo，等 surfaceCreated 回调时 attach
                MPVLib.setPropertyString("vo", vo)
                Log.w(TAG, "reattachSurfaceWithVo: surface not valid, vo set without reattach")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "reattachSurfaceWithVo failed", e)
        }
    }

    /**
     * 诊断信息：返回当前 VO、hwdec、surface 状态、视频尺寸等。
     * 用于日志输出和用户报告问题时的诊断。
     */
    fun getDiagnosticInfo(): String {
        val vo = try { MPVLib.getPropertyString("vo") ?: "unknown" } catch (_: Throwable) { "error" }
        val hwdec = try { MPVLib.getPropertyString("hwdec") ?: "unknown" } catch (_: Throwable) { "error" }
        val hwdecCurrent = try { MPVLib.getPropertyString("hwdec-current") ?: "none" } catch (_: Throwable) { "error" }
        val videoFormat = try { MPVLib.getPropertyString("video-format") ?: "none" } catch (_: Throwable) { "error" }
        val width = try { MPVLib.getPropertyInt("width") ?: 0 } catch (_: Throwable) { -1 }
        val height = try { MPVLib.getPropertyInt("height") ?: 0 } catch (_: Throwable) { -1 }
        val vfps = try { MPVLib.getPropertyDouble("estimated-vfps") ?: 0.0 } catch (_: Throwable) { -1.0 }
        val surfaceValid = holder.surface?.isValid ?: false
        return "vo=$vo, hwdec=$hwdec, hwdec-current=$hwdecCurrent, " +
            "video-format=$videoFormat, ${width}x${height}, vfps=$vfps, " +
            "surfaceValid=$surfaceValid, voInUse=$voInUse"
    }

    fun playFile(path: String) {
        val s = holder.surface
        if (s != null && s.isValid) {
            MPVLib.command(arrayOf("loadfile", path))
            MPVLib.setPropertyBoolean("pause", false)
        } else {
            filePath = path
        }
    }

    fun stop() {
        MPVLib.command(arrayOf("stop"))
    }

    // ---- SurfaceHolder.Callback ----

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceCreated: attaching surface (vo=$voInUse)")
        try {
            MPVLib.attachSurface(holder.surface)
            MPVLib.setOptionString("force-window", "yes")
            // 关键：surfaceCreated 必须总是把 vo 切回有效值。
            // destroy() 中会设置 vo=null 释放 vo 模块，这里恢复。
            MPVLib.setPropertyString("vo", voInUse)
            Log.i(TAG, "attachSurface OK (SurfaceView), vo=$voInUse")
        } catch (e: Throwable) {
            Log.e(TAG, "attachSurface FAILED", e)
        }

        if (filePath != null) {
            Log.i(TAG, "surfaceCreated: 播放缓存的 filePath=$filePath")
            MPVLib.command(arrayOf("loadfile", filePath as String))
            MPVLib.setPropertyBoolean("pause", false)
            filePath = null
        } else {
            // 正常播放中 Surface 重建：不主动 reload，让外部 playFile/restorePlaybackState 驱动
            // （复用实例时 destroy 已 playlist-clear，mpv 内部无 path 残留）
            Log.i(TAG, "surfaceCreated: no pending filePath, waiting for external playFile")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (!nativeInstanceAlive) {
            Log.i(TAG, "surfaceDestroyed: native instance not active, skipping")
            return
        }
        Log.i(TAG, "surfaceDestroyed: detaching surface")
        try {
            MPVLib.setPropertyString("force-window", "no")
            MPVLib.detachSurface()
        } catch (e: Throwable) {
            Log.w(TAG, "surfaceDestroyed: detachSurface failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "mpv"

        const val DEFAULT_VO = "gpu"
        const val DEFAULT_HWDEC = "auto-copy"

        /**
         * native mpv 实例是否处于活跃状态（有 MPVView 关联，可以接收命令）。
         * destroy() 设为 false，initialize() 设为 true。
         * surfaceDestroyed() 检查此标志，为 false 时跳过 native 调用。
         */
        @Volatile
        private var nativeInstanceAlive = false

        /**
         * native mpv 实例是否已创建（MPVLib.create 至少调用过一次）。
         * 一旦为 true 永远为 true（keep-alive 策略不 destroy）。
         * initialize() 用此标志判断是首次创建还是复用。
         */
        @Volatile
        private var nativeInstanceCreated = false

        @Volatile
        private var activeGeneration: Int = 0
    }
}
