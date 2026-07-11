package com.iptv.scanner.editor.pro.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.iptv.scanner.editor.pro.data.ReminderItem
import com.iptv.scanner.editor.pro.data.UserPrefs
import com.iptv.scanner.editor.pro.mpv.MPVView
import com.iptv.scanner.editor.pro.player.PlayerType
import androidx.media3.ui.PlayerView

/**
 * 主播放屏：MPVView + 透明控制层 + 面板抽屉 + OSD 浮层。
 *
 * 层次结构（从底到顶）：
 * 1. MPVView（全屏视频渲染，SurfaceView，默认 Z-order：渲染在普通 View 下方，通过透明 "打孔" 显示视频）
 * 2. 透明点击层（点击切换控制层显示/隐藏，仅当无面板打开时启用）
 * 3. 控制层（顶部侧边栏按钮 + 底部 ControlPanel，仅当 controlsVisible=true 且无面板打开时显示）
 * 4. 面板层（ChannelsPanel 右抽屉 / EpgPanel 左抽屉 / MainMenuPanel 全屏覆盖）
 * 5. OSD 浮层（顶部居中，3 秒自动隐藏，最顶层确保反馈可见）
 *
 * 与 PC 端 mobile/index.html 主框架对齐：
 * - 点击视频区域切换控制层
 * - 控制层显示时，顶部有 3 个面板入口（频道列表/EPG/菜单）
 * - 控制层底部是 ControlPanel（3 行布局）
 * - 面板打开时控制层自动隐藏
 */
@Composable
fun MainPlayerScreen(viewModel: AppViewModel) {
    val uiMode by viewModel.uiMode.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val controlsVisible by viewModel.controlsVisible.collectAsState()
    val channelsPanelOpen by viewModel.channelsPanelOpen.collectAsState()
    val epgPanelOpen by viewModel.epgPanelOpen.collectAsState()
    val menuPanelOpen by viewModel.menuPanelOpen.collectAsState()
    val tvUnifiedPanelOpen by viewModel.tvUnifiedPanelOpen.collectAsState()
    val fileBrowserOpen by viewModel.fileBrowserOpen.collectAsState()
    val sourceManagerOpen by viewModel.sourceManagerOpen.collectAsState()
    val playerSettingsOpen by viewModel.playerSettingsOpen.collectAsState()
    val videoSettingsOpen by viewModel.videoSettingsOpen.collectAsState()
    val audioSettingsOpen by viewModel.audioSettingsOpen.collectAsState()
    val subtitleSettingsOpen by viewModel.subtitleSettingsOpen.collectAsState()
    val subtitleSearchOpen by viewModel.subtitleSearchOpen.collectAsState()
    val playbackPanelOpen by viewModel.playbackPanelOpen.collectAsState()
    val screenshotPanelOpen by viewModel.screenshotPanelOpen.collectAsState()
    val viewSettingsOpen by viewModel.viewSettingsOpen.collectAsState()
    val aboutPanelOpen by viewModel.aboutPanelOpen.collectAsState()
    val mappingPanelOpen by viewModel.mappingPanelOpen.collectAsState()
    val avSyncPanelOpen by viewModel.avSyncPanelOpen.collectAsState()
    val networkPanelOpen by viewModel.networkPanelOpen.collectAsState()
    val toolsPanelOpen by viewModel.toolsPanelOpen.collectAsState()
    val scanPanelOpen by viewModel.scanPanelOpen.collectAsState()
    val reminderPanelOpen by viewModel.reminderPanelOpen.collectAsState()
    val resumePanelOpen by viewModel.resumePanelOpen.collectAsState()
    val bookmarkPanelOpen by viewModel.bookmarkPanelOpen.collectAsState()
    val epgTimelineOpen by viewModel.epgTimelineOpen.collectAsState()
    val searchPanelOpen by viewModel.searchPanelOpen.collectAsState()
    val streamQualityPanelOpen by viewModel.streamQualityPanelOpen.collectAsState()
    val recentPanelOpen by viewModel.recentPanelOpen.collectAsState()
    val clipExportPanelOpen by viewModel.clipExportPanelOpen.collectAsState()
    val audioVisualizerOpen by viewModel.audioVisualizerOpen.collectAsState()
    val lyricsOpen by viewModel.lyricsOpen.collectAsState()
    val exitConfirmOpen by viewModel.exitConfirmOpen.collectAsState()
    val openUrlDialogOpen by viewModel.openUrlDialogOpen.collectAsState()
    val updateDialogOpen by viewModel.updateDialogOpen.collectAsState()
    val triggeredReminder by viewModel.triggeredReminder.collectAsState()
    val osd by viewModel.osd.collectAsState()

    val player = viewModel.mpv  // 当前 Player 实例（类型为 Player 接口）
    val paused by player.paused.collectAsState()
    val videoWidth by player.videoWidth.collectAsState()
    val videoHeight by player.videoHeight.collectAsState()
    val fileLoaded by player.fileLoaded.collectAsState()

    // 多画面状态
    val multiViewState by viewModel.multiViewState.collectAsState()

    // 文件加载完成时触发续播位置恢复（与 PC 端 _on_file_loaded 对齐）
    // 同时应用 HDR 配置（与 PC 端 _apply_hdr_on_file_loaded 对齐）
    LaunchedEffect(fileLoaded) {
        if (fileLoaded) {
            val url = viewModel.getCurrentPlaybackUrl()
            if (url.isNotEmpty()) {
                viewModel.onFileLoadedForResume(url)
            }
            // 应用 HDR 配置（检测视频是否 HDR 并按当前模式应用）
            viewModel.applyHdrOnFileLoaded()
        }
    }

    // 所有模式初始自动隐藏控制面板（几秒后自动隐藏，避免一直显示）
    LaunchedEffect(uiMode) {
        viewModel.showControlsAutoHide()
    }

    // 视频宽高比：用于 SurfaceView 比例保持（解决竖屏下视频被拉长铺满的问题）。
    // 根因：vo=mediacodec_embed 直接用 MediaCodec 渲染到 Surface buffer，不经过 GPU 渲染管线，
    // mpv 的 keepaspect/keepaspect-window 选项对 mediacodec_embed 不生效。
    // 如果 SurfaceView 全屏（fillMaxSize），Surface buffer 是全屏尺寸（如竖屏 1440x2984），
    // MediaCodec 会把视频帧拉伸到 buffer 尺寸，破坏 16:9 比例。
    // 用 aspectRatio modifier 限制 SurfaceView 尺寸，让 Surface buffer 匹配视频比例，
    // 视频会居中显示并保持比例（上下/左右黑边）。
    // vo=gpu 时也兼容（mpv 内部 keepaspect 已处理，aspectRatio 只影响 SurfaceView 外框，不影响渲染）。
    val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
        videoWidth.toFloat() / videoHeight.toFloat()
    } else {
        16f / 9f  // 默认 16:9（未加载时）
    }

    // 是否有任何面板或对话框打开（控制层在面板/对话框打开时自动隐藏）
    val anyPanelOpen = channelsPanelOpen || epgPanelOpen || menuPanelOpen ||
            tvUnifiedPanelOpen ||
            sourceManagerOpen || playerSettingsOpen ||
            videoSettingsOpen || audioSettingsOpen || subtitleSettingsOpen || subtitleSearchOpen ||
            playbackPanelOpen || screenshotPanelOpen || viewSettingsOpen || aboutPanelOpen ||
            mappingPanelOpen || avSyncPanelOpen || networkPanelOpen || toolsPanelOpen || scanPanelOpen ||
            reminderPanelOpen || resumePanelOpen || bookmarkPanelOpen ||
            epgTimelineOpen || searchPanelOpen || streamQualityPanelOpen ||
            recentPanelOpen || clipExportPanelOpen || audioVisualizerOpen || lyricsOpen ||
            exitConfirmOpen || openUrlDialogOpen || updateDialogOpen
    // 控制层是否应该显示
    val showControls = controlsVisible && !anyPanelOpen

    // PHONE 竖屏分屏：无全屏面板且非多画面时生效
    val anyFullScreenPanel = menuPanelOpen || sourceManagerOpen || playerSettingsOpen ||
            videoSettingsOpen || audioSettingsOpen || subtitleSettingsOpen || subtitleSearchOpen ||
            playbackPanelOpen || screenshotPanelOpen || viewSettingsOpen || aboutPanelOpen ||
            mappingPanelOpen || avSyncPanelOpen || networkPanelOpen || toolsPanelOpen || scanPanelOpen ||
            reminderPanelOpen || resumePanelOpen || bookmarkPanelOpen ||
            epgTimelineOpen || searchPanelOpen || streamQualityPanelOpen ||
            recentPanelOpen || clipExportPanelOpen || audioVisualizerOpen || lyricsOpen ||
            exitConfirmOpen || openUrlDialogOpen || updateDialogOpen
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // 竖屏 PHONE 模式：默认上下分屏（视频 16:9 + 频道列表）
    val portraitSplit = uiMode.isPhone && isPortrait && !multiViewState.active && !anyFullScreenPanel
    // 横屏 PHONE 模式：使用 compact 抽屉
    val landscapeCompact = uiMode.isPhone && !isPortrait

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 关键：不能设置不透明 background！
            // SurfaceView 默认 Z-order 在普通 View 后面，不透明 background 会遮挡视频画面
            // 黑色背景由 Activity window background + SurfaceView 自身提供
    ) {
        // -----------------------------------------------------------------
        // 1. 底层：播放器 View
        //
        // 根据 playerType 创建对应的 View：
        // - MPV：MPVView（libmpv JNI）
        // - EXO/SYSTEM：ExoPlayer 的 PlayerView（Google Media3）
        //
        // 单画面模式：用 aspectRatio 让 SurfaceView 尺寸匹配视频比例，居中显示。
        // 多画面模式：主画面用 fillMaxSize 填满网格 cell。
        // -----------------------------------------------------------------
        val createPlayerView: (android.content.Context) -> android.view.View = { ctx ->
            val pType = viewModel.playerType.value
            Log.i("MainPlayerScreen", "Creating player view, uiMode=$uiMode, type=$pType")
            when (pType) {
                PlayerType.MPV -> {
                    val mpvView = MPVView(ctx)
                    val configDir = ctx.getDir("mpv_config", Context.MODE_PRIVATE).absolutePath
                    val cacheDir = ctx.cacheDir.absolutePath
                    val userPrefs = UserPrefs.getInstance()
                    val vo = userPrefs.getVo()
                    val hwdec = userPrefs.getHwdec()
                    try {
                        mpvView.initialize(configDir, cacheDir, vo = vo, hwdec = hwdec)
                        player.attachView(mpvView)
                        Log.i("MainPlayerScreen", "MPVView initialized (vo=$vo, hwdec=$hwdec) + attached")
                    } catch (e: Throwable) {
                        Log.e("MainPlayerScreen", "MPVView initialize failed", e)
                    }
                    mpvView
                }
                PlayerType.EXO, PlayerType.SYSTEM -> {
                    val exoView = PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                    player.attachView(exoView)
                    Log.i("MainPlayerScreen", "PlayerView (ExoPlayer) attached, type=$pType")
                    exoView
                }
            }
        }
        val onReleasePlayer: (android.view.View) -> Unit = { view ->
            Log.i("MainPlayerScreen", "onRelease: destroying player view")
            when (view) {
                is MPVView -> view.destroy()
                is PlayerView -> {
                    view.player = null
                    viewModel.detachOldPlayer()
                }
                else -> viewModel.detachOldPlayer()
            }
        }

        // 用 movableContentOf 包装主画面 AndroidView，确保 multiViewState.active 变化时
        // AndroidView 在 Compose 树中移动而不销毁重建（避免 MPV 实例销毁导致播放中断）。
        val primaryPlayer = remember {
            movableContentOf {
                AndroidView(
                        factory = createPlayerView,
                        update = { /* 各 View 的 surfaceChanged 等回调内部已处理 */ },
                        onRelease = onReleasePlayer,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // -----------------------------------------------------------------
            // 布局：PHONE 竖屏 = 上下分屏 | PHONE 横屏 / TV = 全屏 + 抽屉
            //
            // 竖屏分屏：视频 16:9（fillMaxWidth + aspectRatio）+ 频道列表填满剩余
            // 横屏全屏：视频居中 + compact 抽屉（频道/EPG 宽度 1/4，无标题无搜索）
            // TV 全屏：视频居中 + 统一面板（DPAD 导航）
            // -----------------------------------------------------------------
            if (portraitSplit) {
                // ---- 竖屏分屏 ----
                Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    // 视频区域：16:9 比例，宽度填满（控制层自动隐藏 + 点击呼出）
                    SplitVideoArea(
                        primaryPlayer = primaryPlayer,
                        aspectRatio = aspectRatio,
                        showControls = showControls,
                        anyPanelOpen = anyPanelOpen,
                        paused = paused,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                    // 信息栏：频道名 + 台标 + 媒体信息 + EPG + EPG查看按钮
                    PortraitInfoBar(viewModel = viewModel)
                    // 频道列表区域：填满剩余空间（无搜索框）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        ChannelsPanel(viewModel = viewModel, inline = true, noSearch = true)
                    }
                }
                // EPG 节目单（竖屏分屏模式下作为全屏覆盖抽屉）
                if (epgPanelOpen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        EpgPanel(viewModel = viewModel)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0x88000000))
                                .clickable { viewModel.toggleEpgPanel() }
                        )
                    }
                }
            } else {
                // ---- 全屏模式（横屏 PHONE / TV / 多画面）----
                if (multiViewState.active) {
                    MultiViewOverlay(
                        state = multiViewState,
                        primaryContent = { primaryPlayer() },
                        getSubPlayer = { idx -> viewModel.getSubPlayer(idx) },
                        onViewportClick = { idx ->
                            viewModel.setFocusedViewport(idx)
                            val viewport = multiViewState.viewports.getOrNull(idx)
                            if (viewport != null && viewport.isEmpty) {
                                viewModel.showChannelsPanel()
                            }
                        },
                        onViewportClose = { idx -> viewModel.removeFromMultiView(idx) },
                        onToggleMute = { idx -> viewModel.toggleMultiViewMute(idx) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .aspectRatio(aspectRatio)
                    ) {
                        primaryPlayer()
                    }
                }

                // 透明点击层
                if (!anyPanelOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { viewModel.toggleControls() }
                    )
                }

                // 控制层
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().systemBarsPadding(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            TopBar(
                                channelName = currentChannel?.name ?: "未选择频道",
                                mode = if (uiMode.isTV) "TV" else "PHONE",
                                paused = paused,
                                isTV = uiMode.isTV,
                                onChannelsClick = { viewModel.showChannelsPanel() },
                                onEpgClick = { viewModel.showEpgPanel() },
                                onMenuClick = { viewModel.showMenuPanel() }
                            )
                            ControlPanel(viewModel = viewModel)
                        }
                    }
                }

                // 频道列表（右侧抽屉）— 横屏 PHONE 用 compact 模式
                if (channelsPanelOpen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0x88000000))
                                .clickable { viewModel.toggleChannelsPanel() }
                        )
                        ChannelsPanel(viewModel = viewModel, compact = landscapeCompact)
                    }
                }

                // EPG 节目单（左侧抽屉）— 横屏 PHONE 用 compact 模式
                if (epgPanelOpen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        EpgPanel(viewModel = viewModel, compact = landscapeCompact)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0x88000000))
                                .clickable { viewModel.toggleEpgPanel() }
                        )
                    }
                }
            }

        // 主菜单（全屏覆盖）— 仅 PHONE 模式使用
        if (menuPanelOpen && !uiMode.isTV) {
            MainMenuPanel(viewModel = viewModel)
        }

        // TV 端统一面板（三列：模式切换 + 频道列表/主菜单 + EPG 节目单）
        if (tvUnifiedPanelOpen) {
            TvUnifiedPanel(viewModel = viewModel)
        }

        // 文件浏览器（全屏覆盖，SAF 不可用时的替代方案）
        if (fileBrowserOpen) {
            FileBrowserPanel(viewModel = viewModel)
        }

        // 订阅源管理（全屏覆盖）
        if (sourceManagerOpen) {
            SourceManagerPanel(viewModel = viewModel)
        }

        // 播放器设置（全屏覆盖，主菜单 → 设置 → 播放器设置）
        if (playerSettingsOpen) {
            PlayerSettingsPanel(viewModel = viewModel)
        }

        // 视频设置（全屏覆盖，主菜单 → 播放 → 视频）
        if (videoSettingsOpen) {
            VideoSettingsPanel(viewModel = viewModel)
        }

        // 音频设置（全屏覆盖，主菜单 → 播放 → 音频）
        if (audioSettingsOpen) {
            AudioSettingsPanel(viewModel = viewModel)
        }

        // 字幕设置（全屏覆盖，含外挂字幕文件选择器）
        if (subtitleSettingsOpen) {
            SubtitleSettingsPanel(viewModel = viewModel)
        }
        if (subtitleSearchOpen) {
            SubtitleSearchPanel(viewModel = viewModel)
        }

        // 播放设置（全屏覆盖，主菜单 → 播放 → 播放）
        if (playbackPanelOpen) {
            PlaybackPanel(viewModel = viewModel)
        }

        // 截图（全屏覆盖，主菜单 → 播放 → 截图）
        if (screenshotPanelOpen) {
            ScreenshotPanel(viewModel = viewModel)
        }

        // 视图设置（全屏覆盖，主菜单 → 播放 → 视图）
        if (viewSettingsOpen) {
            ViewSettingsPanel(viewModel = viewModel)
        }

        // 关于（全屏覆盖，主菜单 → 播放 → 关于）
        if (aboutPanelOpen) {
            AboutPanel(viewModel = viewModel)
        }

        // 频道映射（全屏覆盖，主菜单 → 文件 → 频道映射）
        if (mappingPanelOpen) {
            MappingPanel(viewModel = viewModel)
        }

        // A/V 同步监控（全屏覆盖，主菜单 → 播放 → A/V 同步监控）
        if (avSyncPanelOpen) {
            AvSyncPanel(viewModel = viewModel)
        }

        // 网络增强（全屏覆盖，主菜单 → 播放 → 网络增强）
        if (networkPanelOpen) {
            NetworkPanel(viewModel = viewModel)
        }

        // 工具（全屏覆盖，主菜单 → 播放 → 工具）
        if (toolsPanelOpen) {
            ToolsPanel(viewModel = viewModel)
        }

        // URL 范围扫描（全屏覆盖，工具 → 扫描整理）
        if (scanPanelOpen) {
            ScanPanel(viewModel = viewModel)
        }

        // 节目提醒管理（全屏覆盖，工具 → 提醒管理）
        if (reminderPanelOpen) {
            ReminderPanel(viewModel = viewModel)
        }

        // 续播位置管理（全屏覆盖，工具 → 续播位置）
        if (resumePanelOpen) {
            ResumePanel(viewModel = viewModel)
        }

        // 书签管理（全屏覆盖，工具 → 书签）
        if (bookmarkPanelOpen) {
            BookmarkPanel(viewModel = viewModel)
        }

        // EPG 时间线视图（全屏覆盖，工具 → EPG 时间线）
        if (epgTimelineOpen) {
            EpgTimelinePanel(viewModel = viewModel)
        }

        // 全局搜索（全屏覆盖，工具 → 搜索）
        if (searchPanelOpen) {
            SearchPanel(viewModel = viewModel)
        }

        // 流质量检测（全屏覆盖，工具 → 流质量检测）
        if (streamQualityPanelOpen) {
            StreamQualityPanel(viewModel = viewModel)
        }

        // 最近打开（全屏覆盖）
        if (recentPanelOpen) {
            RecentFilesPanel(viewModel = viewModel)
        }

        // 切片导出（全屏覆盖）
        if (clipExportPanelOpen) {
            ClipExportPanel(viewModel = viewModel)
        }

        // 音频可视化（全屏覆盖）
        if (audioVisualizerOpen) {
            AudioVisualizerPanel(viewModel = viewModel)
        }

        // 歌词（全屏覆盖）
        if (lyricsOpen) {
            LyricsPanel(viewModel = viewModel)
        }

        // 提醒触发弹窗（节目即将开始时弹出，全屏遮罩）
        if (triggeredReminder != null) {
            ReminderPopup(
                reminder = triggeredReminder!!,
                onAccept = { viewModel.acceptTriggeredReminder() },
                onDismiss = { viewModel.dismissTriggeredReminder() }
            )
        }

        // 打开网络流 URL 对话框（AlertDialog，独立 window，自身控制可见性）
        OpenUrlDialog(viewModel = viewModel)

        // 新版本更新提示对话框（发现新版本时自动弹出，也可从"关于"面板手动触发）
        UpdateDialog(viewModel = viewModel)

        // 退出确认对话框（BACK 键退出时提示：立即退出 / 进入 PiP）
        ExitConfirmDialog(viewModel = viewModel)

        // -----------------------------------------------------------------
        // 5. OSD 浮层（顶部居中，最顶层）
        // -----------------------------------------------------------------
        AnimatedVisibility(
            visible = osd != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            osd?.let { info ->
                OsdView(
                    title = info.title,
                    subtitle = info.subtitle,
                    extra = info.extra
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// 顶部信息条 + 面板入口按钮
// -----------------------------------------------------------------

@Composable
private fun TopBar(
    channelName: String,
    mode: String,
    paused: Boolean,
    isTV: Boolean,
    onChannelsClick: () -> Unit,
    onEpgClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Surface(
        color = Color(0xCC000000),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：频道名 + 暂停状态
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = channelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (paused) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "（已暂停）",
                        color = Color(0xFFFFA500),
                        fontSize = 12.sp
                    )
                }
            }

            // 右侧：面板入口按钮（TV 模式下隐藏，由 MENU 键和方向键替代）
            if (!isTV) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onChannelsClick) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = "频道列表",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onEpgClick) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "EPG 节目单",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "主菜单",
                            tint = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = mode,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF888888)
            )
        }
    }
}

// -----------------------------------------------------------------
// 分屏模式视频区域（独立 composable，避免 ColumnScope/RowScope 与 AnimatedVisibility 冲突）
// -----------------------------------------------------------------

@Composable
private fun SplitVideoArea(
    primaryPlayer: @Composable () -> Unit,
    aspectRatio: Float,
    showControls: Boolean,
    anyPanelOpen: Boolean,
    paused: Boolean,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val mpv = viewModel.mpv
    val muted by mpv.muted.collectAsState()
    val volume by mpv.volume.collectAsState()
    val showExitCatchup by viewModel.showExitCatchup.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        // 播放器（居中保持比例）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(aspectRatio)
        ) {
            primaryPlayer()
        }
        // 点击层：点击切换控制层显示/隐藏
        if (!anyPanelOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewModel.toggleControls() }
            )
        }
        // 控制层（简化版：右上角菜单按钮 + 底部播放控制）
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
            ) {
                // 右上角：仅菜单按钮
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    IconButton(
                        onClick = { viewModel.showMenuPanel() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "主菜单",
                            tint = Color.White
                        )
                    }
                    // 底部控制栏（仅按钮+进度条，无媒体信息和节目信息）
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        // 进度条
                        PortraitProgressBar(viewModel = viewModel)
                        // 控制按钮行
                        PortraitControlButtons(
                            paused = paused,
                            muted = muted,
                            volume = volume,
                            showExitCatchup = showExitCatchup,
                            playbackMode = playbackState.mode,
                            onPlayPause = { mpv.togglePause() },
                            onStop = { viewModel.stopPlay() },
                            onPrev = { viewModel.prevChannel() },
                            onNext = { viewModel.nextChannel() },
                            onMute = { mpv.toggleMute() },
                            onVolumeChange = { mpv.setVolume(it.toInt()) },
                            onExitCatchup = { viewModel.exitCatchup() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 竖屏信息栏：频道名 + 台标 + 媒体信息 + EPG + EPG查看按钮
 */
@Composable
private fun PortraitInfoBar(viewModel: AppViewModel) {
    val currentChannel by viewModel.currentChannel.collectAsState()
    val mpv = viewModel.mpv
    val videoWidth by mpv.videoWidth.collectAsState()
    val videoHeight by mpv.videoHeight.collectAsState()
    val fileLoaded by mpv.fileLoaded.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // 当前 EPG 节目（2秒刷新）
    var currentProgram by remember { mutableStateOf<com.iptv.scanner.editor.pro.data.IptvEpgProgram?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            currentProgram = viewModel.getCurrentProgram()
            delay(2_000L)
        }
    }

    // 媒体徽章数据（1秒刷新）
    var mediaTick by remember { mutableStateOf(0L) }
    LaunchedEffect(fileLoaded) {
        while (fileLoaded) {
            mediaTick = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Surface(
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // 第一行：台标 + 频道名 + EPG节目 + EPG查看按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 台标
                if (currentChannel != null && currentChannel!!.logo.isNotEmpty()) {
                    AsyncImage(
                        model = currentChannel!!.logo,
                        contentDescription = currentChannel!!.name,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                // 频道名 + EPG节目名
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentChannel?.name ?: "未选择频道",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentProgram != null && currentProgram!!.title.isNotEmpty()) {
                        Text(
                            text = currentProgram!!.title,
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (currentChannel != null && currentChannel!!.group.isNotEmpty()) {
                        Text(
                            text = currentChannel!!.group,
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 回看/时移指示器
                if (playbackState.mode.isCatchupOrTimeshift) {
                    val indicatorText = when (playbackState.mode) {
                        com.iptv.scanner.editor.pro.player.PlayMode.CATCHUP -> "回看"
                        com.iptv.scanner.editor.pro.player.PlayMode.TIMESHIFT -> "时移"
                        else -> ""
                    }
                    Surface(
                        color = Color(0xFFFFA500).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(end = 4.dp)
                    ) {
                        Text(
                            text = indicatorText,
                            color = Color(0xFFFFA500),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // EPG 查看按钮
                IconButton(
                    onClick = { viewModel.showEpgPanel() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "查看EPG",
                        tint = Color(0xFF4A9EFF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // 第二行：媒体信息徽章（紧凑显示）
            if (fileLoaded) {
                val mediaInfo = remember(mediaTick, videoWidth, videoHeight) {
                    buildPortraitMediaInfo(mpv, videoWidth, videoHeight)
                }
                if (mediaInfo.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        mediaInfo.take(3).forEach { info ->
                            Surface(
                                color = Color(0xFFBDBDBD).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = info,
                                    color = Color(0xFFBDBDBD),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 构建竖屏媒体信息（最多3条：分辨率+编解码、音频、网络） */
private fun buildPortraitMediaInfo(
    mpv: com.iptv.scanner.editor.pro.player.Player,
    videoWidth: Int,
    videoHeight: Int
): List<String> {
    val result = mutableListOf<String>()
    // 视频：编解码器 + 分辨率
    val vcodec = mpv.getPropertyString("video-codec") ?: ""
    val codecName = vcodec.split(" ").firstOrNull()?.takeIf { it.isNotEmpty() } ?: ""
    val resStr = if (videoWidth > 0 && videoHeight > 0) "${videoWidth}x${videoHeight}" else ""
    val videoInfo = listOfNotNull(
        codecName.takeIf { it.isNotEmpty() },
        resStr.takeIf { it.isNotEmpty() }
    ).joinToString(" ")
    if (videoInfo.isNotEmpty()) result.add(videoInfo)
    // 音频
    val acodec = mpv.getPropertyString("audio-codec") ?: ""
    if (acodec.isNotEmpty()) {
        val aName = acodec.split(" ").firstOrNull() ?: acodec
        result.add(aName)
    }
    // 网络/协议
    val protocol = mpv.getPropertyString("file-format") ?: mpv.getPropertyString("stream-open") ?: ""
    if (protocol.isNotEmpty()) {
        result.add(protocol.take(10))
    }
    return result
}

/** 竖屏进度条（精简版） */
@Composable
private fun PortraitProgressBar(viewModel: AppViewModel) {
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val progressInfo = remember(tick, viewModel.playbackState.value, viewModel.currentChannel.value, viewModel.currentEpg.value) {
        viewModel.computeProgress()
    }
    var dragging by remember { mutableStateOf(false) }
    var dragPercent by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = progressInfo.startLabel,
            color = Color(0xFFCCCCCC),
            fontSize = 10.sp
        )
        Slider(
            value = if (dragging) dragPercent else (progressInfo.percent / 100f),
            onValueChange = { dragging = true; dragPercent = it },
            onValueChangeFinished = {
                viewModel.seekProgress(dragPercent * 100f)
                dragging = false
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4A9EFF),
                activeTrackColor = Color(0xFF4A9EFF),
                inactiveTrackColor = Color(0xFF444444)
            )
        )
        Text(
            text = progressInfo.endLabel,
            color = Color(0xFFCCCCCC),
            fontSize = 10.sp
        )
    }
}

/** 竖屏控制按钮行 */
@Composable
private fun PortraitControlButtons(
    paused: Boolean,
    muted: Boolean,
    volume: Int,
    showExitCatchup: Boolean,
    playbackMode: com.iptv.scanner.editor.pro.player.PlayMode,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onExitCatchup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "上一频道", tint = Color.White)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (paused) "播放" else "暂停",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Stop, contentDescription = "停止", tint = Color.White)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一频道", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onMute, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (muted || volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "静音",
                tint = if (muted) Color(0xFFFFA500) else Color.White
            )
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = onVolumeChange,
            valueRange = 0f..130f,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4A9EFF),
                activeTrackColor = Color(0xFF4A9EFF),
                inactiveTrackColor = Color(0xFF444444)
            )
        )
        Text(
            text = volume.toString(),
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            modifier = Modifier.width(28.dp)
        )
        if (showExitCatchup) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onExitCatchup, modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFA500)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (playbackMode == com.iptv.scanner.editor.pro.player.PlayMode.TIMESHIFT) "时移" else "回看",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// OSD 浮层
// -----------------------------------------------------------------

@Composable
private fun OsdView(
    title: String,
    subtitle: String,
    extra: String
) {
    Surface(
        color = Color(0xE6000000),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 48.dp)
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp
                )
            }
            if (extra.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = extra,
                    color = Color(0xFF888888),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------
// 提醒触发弹窗（节目即将开始时弹出）
// 与 PC 端 ui/dialogs/reminder_popup.py 对齐
// -----------------------------------------------------------------

/**
 * 提醒弹窗：节目即将开始时弹出，提供"切换频道"和"稍后"两个选项。
 *
 * - 全屏半透明遮罩
 * - 中央卡片显示节目信息
 * - "切换频道"：切到目标频道并关闭弹窗
 * - "稍后"：仅关闭弹窗
 */
@Composable
private fun ReminderPopup(
    reminder: ReminderItem,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable(enabled = false) {},  // 阻断背景点击
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFF1F1F1F),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部图标
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 标题
                Text(
                    text = "节目即将开始",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 节目信息卡片
                Surface(
                    color = Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = reminder.programTitle.ifBlank { "（未命名节目）" },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )
                        if (reminder.channelName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "频道: ${reminder.channelName}",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                        if (reminder.startTs > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val timeStr = java.text.SimpleDateFormat(
                                "MM-dd HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(reminder.startTs))
                            Text(
                                text = "开始: $timeStr",
                                color = Color(0xFFAAAAAA),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("稍后", color = Color(0xFFCCCCCC))
                    }
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A9EFF)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("切换频道", color = Color.White)
                    }
                }
            }
        }
    }
}
