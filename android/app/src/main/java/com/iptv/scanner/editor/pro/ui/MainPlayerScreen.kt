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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.iptv.scanner.editor.pro.data.IptvChannel
import com.iptv.scanner.editor.pro.mpv.MPVView
import com.iptv.scanner.editor.pro.mpv.MPVTextureView
import com.iptv.scanner.editor.pro.mpv.MPVViewLike
import com.iptv.scanner.editor.pro.player.PlayerType
import com.iptv.scanner.editor.pro.player.ProgressHelper
import com.iptv.scanner.editor.pro.ui.AppViewModel.ChannelTab
import com.iptv.scanner.editor.pro.ui.theme.PlayerOverlayColors
import com.iptv.scanner.editor.pro.ui.theme.rememberPlayerOverlayColors
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.PaddingValues

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
    val channelInfoOpen by viewModel.channelInfoOpen.collectAsState()
    val openUrlDialogOpen by viewModel.openUrlDialogOpen.collectAsState()
    val updateDialogOpen by viewModel.updateDialogOpen.collectAsState()
    val triggeredReminder by viewModel.triggeredReminder.collectAsState()
    val osd by viewModel.osd.collectAsState()

    val player = viewModel.mpv  // 当前 Player 实例（类型为 Player 接口）
    val paused by player.paused.collectAsState()
    val videoWidth by player.videoWidth.collectAsState()
    val videoHeight by player.videoHeight.collectAsState()
    val fileLoaded by player.fileLoaded.collectAsState()

    // 主题自适应覆盖颜色
    val oc = rememberPlayerOverlayColors()

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
            exitConfirmOpen || openUrlDialogOpen || updateDialogOpen ||
            channelInfoOpen
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
            exitConfirmOpen || openUrlDialogOpen || updateDialogOpen ||
            channelInfoOpen
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // 竖屏 PHONE 模式：默认上下分屏（视频 16:9 + 频道列表）
    val portraitSplit = uiMode.isPhone && isPortrait && !multiViewState.active && !anyFullScreenPanel
    Log.e("MainPlayerScreen", "portraitSplit=$portraitSplit uiMode=$uiMode isPortrait=$isPortrait multiView=${multiViewState.active} anyFullScreenPanel=$anyFullScreenPanel")
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
            Log.i("MainPlayerScreen", "Creating player view, uiMode=$uiMode, type=$pType, portraitSplit=$portraitSplit")
            when (pType) {
                PlayerType.MPV -> {
                    // 竖屏用 TextureView（避免 SurfaceView “打孔”覆盖信息栏），
                    // 横屏/TV 用 SurfaceView（性能更好）
                    val mpvView: MPVViewLike = if (portraitSplit) {
                        MPVTextureView(ctx)
                    } else {
                        MPVView(ctx)
                    }
                    val configDir = ctx.getDir("mpv_config", Context.MODE_PRIVATE).absolutePath
                    val cacheDir = ctx.cacheDir.absolutePath
                    val userPrefs = UserPrefs.getInstance()
                    val vo = userPrefs.getVo()
                    val hwdec = userPrefs.getHwdec()
                    try {
                        mpvView.initialize(configDir, cacheDir, vo = vo, hwdec = hwdec)
                        player.attachView(mpvView)
                        Log.i("MainPlayerScreen", "${mpvView.asView().javaClass.simpleName} initialized (vo=$vo, hwdec=$hwdec) + attached")
                    } catch (e: Throwable) {
                        Log.e("MainPlayerScreen", "MPVView initialize failed", e)
                    }
                    mpvView.asView()
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
            Log.i("MainPlayerScreen", "onRelease: destroying player view (${view.javaClass.simpleName})")
            when (view) {
                is MPVViewLike -> view.destroy()
                is PlayerView -> {
                    view.player = null
                    viewModel.detachOldPlayer()
                }
                else -> viewModel.detachOldPlayer()
            }
        }

        // 用 movableContentOf 包装主画面 AndroidView，确保 multiViewState.active 变化时
        // AndroidView 在 Compose 树中移动而不销毁重建（避免 MPV 实例销毁导致播放中断）。
        // key=portraitSplit：横竖屏切换时重建 View（SurfaceView ↔ TextureView），
        // mpv 实例通过 keep-alive 策略自动复用，不会中断播放。
        val primaryPlayer = remember(portraitSplit) {
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
                // ---- 竖屏新布局 V2：信息栏→视频→播放控制→动态内容→底部Tab ----
                Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                    // 1. 固定信息栏（台标+频道名+分类，右侧收藏+信息按钮）
                    PortraitInfoBarV2(viewModel = viewModel)
                    // 2. 固定视频区域 (16:9)
                    PortraitVideoArea(
                        primaryPlayer = primaryPlayer,
                        aspectRatio = aspectRatio,
                        anyPanelOpen = anyPanelOpen,
                        viewModel = viewModel
                    )
                    // 3. 固定播放控制栏（播放/停止 + 圆点进度条）
                    PortraitControlsV2(viewModel = viewModel)
                    // 4. 动态内容区域 (根据底部 Tab 切换)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        PortraitDynamicContent(viewModel = viewModel)
                    }
                    // 5. 固定底部 Tab 栏
                    PortraitBottomTabBar(viewModel = viewModel)
                }
                // EPG 节目单（全屏覆盖抽屉）
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
                            .background(oc.scrim)
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
                            // 快捷工具栏（横屏模式）
                            if (!uiMode.isTV) {
                                QuickActionBar(
                                    viewModel = viewModel,
                                    orientation = QuickActionBarOrientation.Landscape
                                )
                            }
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

        // 频道信息详情对话框（信息栏"信息"按钮触发）
        if (channelInfoOpen) {
            ChannelInfoDialog(viewModel = viewModel)
        }

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
    val oc = rememberPlayerOverlayColors()
    Surface(
        color = oc.topBarBg,
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
                    color = oc.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (paused) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "（已暂停）",
                        color = oc.iconTintActive,
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
                            tint = oc.iconTint
                        )
                    }
                    IconButton(onClick = onEpgClick) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "EPG 节目单",
                            tint = oc.iconTint
                        )
                    }
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "主菜单",
                            tint = oc.iconTint
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = mode,
                style = MaterialTheme.typography.labelSmall,
                color = oc.textSecondary
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
    val oc = rememberPlayerOverlayColors()

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
        // 控制层（简化版：右上角菜单按钮 + QuickActionBar + 底部播放控制）
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(oc.scrim)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // 右上角：菜单按钮
                    IconButton(
                        onClick = { viewModel.showMenuPanel() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "主菜单",
                            tint = oc.iconTint
                        )
                    }
                    // 底部控制栏：QuickActionBar + 进度条 + 控制按钮
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        // 快捷工具栏
                        QuickActionBar(
                            viewModel = viewModel,
                            orientation = QuickActionBarOrientation.Portrait
                        )
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
    val oc = rememberPlayerOverlayColors()

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
        color = oc.infoBarBg,
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
                        color = oc.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentProgram != null && currentProgram!!.title.isNotEmpty()) {
                        Text(
                            text = currentProgram!!.title,
                            color = oc.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (currentChannel != null && currentChannel!!.group.isNotEmpty()) {
                        Text(
                            text = currentChannel!!.group,
                            color = oc.textSecondary,
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
                        color = oc.iconTintActive.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).padding(end = 4.dp)
                    ) {
                        Text(
                            text = indicatorText,
                            color = oc.iconTintActive,
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
                        tint = oc.accent,
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
                                color = oc.badgeBg,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = info,
                                    color = oc.badgeText,
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
    val oc = rememberPlayerOverlayColors()
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
            color = oc.textSecondary,
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
                thumbColor = oc.accent,
                activeTrackColor = oc.accent,
                inactiveTrackColor = oc.trackInactive
            )
        )
        Text(
            text = progressInfo.endLabel,
            color = oc.textSecondary,
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
    val oc = rememberPlayerOverlayColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "上一频道", tint = oc.iconTint)
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (paused) "播放" else "暂停",
                tint = oc.iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Stop, contentDescription = "停止", tint = oc.iconTint)
        }
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一频道", tint = oc.iconTint)
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onMute, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (muted || volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "静音",
                tint = if (muted) oc.iconTintActive else oc.iconTint
            )
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = onVolumeChange,
            valueRange = 0f..130f,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = oc.accent,
                activeTrackColor = oc.accent,
                inactiveTrackColor = oc.trackInactive
            )
        )
        Text(
            text = volume.toString(),
            color = oc.textSecondary,
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
                        .background(oc.iconTintActive),
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

// -----------------------------------------------------------------
// 快捷工具栏 (QuickActionBar)
//
// 在视频控制层中提供常用功能的快捷入口，避免每次都要打开主菜单。
// 图标颜色随深色/浅色主题自适应。
// -----------------------------------------------------------------

/** QuickActionBar 朝向 */
enum class QuickActionBarOrientation { Portrait, Landscape }

/**
 * 快捷工具栏：截图 / 比例 / 锁定 / 画中画 / 音频可视化 / 歌词 / 主题切换
 *
 * - 深色主题：白色图标，激活态橙色
 * - 浅色主题：深色图标，激活态橙色
 * 所有颜色通过 [rememberPlayerOverlayColors] 自动适配。
 */
@Composable
private fun QuickActionBar(
    viewModel: AppViewModel,
    orientation: QuickActionBarOrientation
) {
    val oc = rememberPlayerOverlayColors()
    val controlsPinned by viewModel.controlsPinned.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val audioVisualizerOpen by viewModel.audioVisualizerOpen.collectAsState()
    val lyricsOpen by viewModel.lyricsOpen.collectAsState()
    val aspectRatioIdx by viewModel.aspectRatioIdx.collectAsState()

    val aspectLabels = listOf("默认", "16:9", "4:3", "拉伸")

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (orientation == QuickActionBarOrientation.Landscape) 16.dp else 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(if (orientation == QuickActionBarOrientation.Landscape) 6.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 截图
            QuickActionItem(
                icon = Icons.Default.CropFree,
                label = "截图",
                tint = oc.iconTint,
                onClick = { viewModel.takeScreenshot("video") }
            )
            // 画面比例
            QuickActionItem(
                icon = Icons.Default.AspectRatio,
                label = aspectLabels.getOrElse(aspectRatioIdx) { "比例" },
                tint = if (aspectRatioIdx > 0) oc.accent else oc.iconTint,
                onClick = { viewModel.cycleAspectRatio() }
            )
            // 锁定控制层
            QuickActionItem(
                icon = if (controlsPinned) Icons.Default.Lock else Icons.Default.LockOpen,
                label = if (controlsPinned) "已锁" else "锁定",
                tint = if (controlsPinned) oc.accent else oc.iconTint,
                onClick = { viewModel.toggleControlsPinned() }
            )
            // 画中画
            QuickActionItem(
                icon = Icons.Default.PictureInPicture,
                label = "PiP",
                tint = oc.iconTint,
                onClick = { viewModel.enterPip() }
            )
            // 音频可视化
            QuickActionItem(
                icon = Icons.Default.MusicNote,
                label = "频谱",
                tint = if (audioVisualizerOpen) oc.accent else oc.iconTint,
                onClick = { viewModel.toggleAudioVisualizer() }
            )
            // 歌词
            QuickActionItem(
                icon = Icons.Default.MusicNote,
                label = "歌词",
                tint = if (lyricsOpen) oc.accent else oc.iconTint,
                onClick = { viewModel.toggleLyricsPanel() }
            )
            Spacer(modifier = Modifier.weight(1f))
            // 主题切换（深色↔浅色快速切换）
            QuickActionItem(
                icon = Icons.Default.Brightness4,
                label = when (themeMode) { "light" -> "浅色"; "system" -> "系统"; else -> "深色" },
                tint = oc.iconTint,
                onClick = {
                    val next = when (themeMode) {
                        "dark" -> "light"
                        "light" -> "system"
                        else -> "dark"
                    }
                    viewModel.setThemeMode(next)
                }
            )
        }
    }
}

/** QuickActionBar 单个快捷按钮 */
@Composable
private fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 9.sp,
            maxLines = 1
        )
    }
}

// ============================================================
// 竖屏新布局组件
// ============================================================

/**
 * 竖屏视频区域（固定 16:9，纯视频无覆盖层）
 */
@Composable
private fun PortraitVideoArea(
    primaryPlayer: @Composable () -> Unit,
    aspectRatio: Float,
    anyPanelOpen: Boolean,
    viewModel: AppViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
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
    }
}

/**
 * 竖屏固定工具栏：上一频道/播放暂停/下一频道/静音/音量/截图/比例/画中画
 */
@Composable
private fun PortraitToolbar(viewModel: AppViewModel) {
    val mpv = viewModel.mpv
    val paused by mpv.paused.collectAsState()
    val muted by mpv.muted.collectAsState()
    val volume by mpv.volume.collectAsState()
    val showExitCatchup by viewModel.showExitCatchup.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val aspectRatioIdx by viewModel.aspectRatioIdx.collectAsState()
    val oc = rememberPlayerOverlayColors()

    Surface(
        color = oc.infoBarBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 上一频道
            IconButton(onClick = { viewModel.prevChannel() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一频道", tint = oc.iconTint, modifier = Modifier.size(22.dp))
            }
            // 播放/暂停
            IconButton(onClick = { mpv.togglePause() }, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (paused) "播放" else "暂停",
                    tint = oc.iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
            // 下一频道
            IconButton(onClick = { viewModel.nextChannel() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一频道", tint = oc.iconTint, modifier = Modifier.size(22.dp))
            }
            // 静音
            IconButton(onClick = { mpv.toggleMute() }, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (muted || volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "静音",
                    tint = if (muted) oc.iconTintActive else oc.iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            // 音量滑块
            Slider(
                value = volume.toFloat(),
                onValueChange = { mpv.setVolume(it.toInt()) },
                valueRange = 0f..130f,
                modifier = Modifier.weight(1f).height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = oc.accent,
                    activeTrackColor = oc.accent,
                    inactiveTrackColor = oc.trackInactive
                )
            )
            // 截图
            IconButton(onClick = { viewModel.takeScreenshot("video") }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.CropFree, contentDescription = "截图", tint = oc.iconTint, modifier = Modifier.size(20.dp))
            }
            // 画面比例
            IconButton(onClick = { viewModel.cycleAspectRatio() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.AspectRatio, contentDescription = "画面比例", tint = if (aspectRatioIdx > 0) oc.accent else oc.iconTint, modifier = Modifier.size(20.dp))
            }
            // 画中画
            IconButton(onClick = { viewModel.enterPip() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.PictureInPicture, contentDescription = "画中画", tint = oc.iconTint, modifier = Modifier.size(20.dp))
            }
            // 回看/时移退出
            if (showExitCatchup) {
                IconButton(onClick = { viewModel.exitCatchup() }, modifier = Modifier.size(34.dp)) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(oc.iconTintActive),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (playbackState.mode == com.iptv.scanner.editor.pro.player.PlayMode.TIMESHIFT) "时移" else "回看",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 竖屏动态内容区域：根据底部 Tab 切换内容
 */
@Composable
private fun PortraitDynamicContent(viewModel: AppViewModel) {
    val portraitTab by viewModel.portraitTab.collectAsState()
    when (portraitTab) {
        AppViewModel.PortraitTab.CHANNELS -> {
            PortraitChannelList(viewModel = viewModel, showFavoritesOnly = false)
        }
        AppViewModel.PortraitTab.FAV -> {
            PortraitChannelList(viewModel = viewModel, showFavoritesOnly = true)
        }
        AppViewModel.PortraitTab.TOOLS -> {
            PortraitToolsContent(viewModel = viewModel)
        }
        AppViewModel.PortraitTab.SETTINGS -> {
            PortraitSettingsContent(viewModel = viewModel)
        }
    }
}

// -----------------------------------------------------------------
// EPG 节目单内容（竖屏 Tab 内简化版）
// -----------------------------------------------------------------

@Composable
private fun PortraitEpgContent(viewModel: AppViewModel) {
    val epg by viewModel.currentEpg.collectAsState()
    val loading by viewModel.epgLoading.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val oc = rememberPlayerOverlayColors()

    // 每秒刷新当前时间（用于高亮当前节目）
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 标题栏
        Surface(color = oc.infoBarBg, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "节目单",
                    color = oc.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentChannel?.name ?: "未选择频道",
                    color = oc.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 内容
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中...", color = oc.textSecondary, fontSize = 13.sp)
                }
            }
            epg.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无节目信息", color = oc.textSecondary, fontSize = 13.sp)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(epg) { program ->
                        val isCurrent = portraitIsCurrentProgram(program, now)
                        val isPast = portraitIsPastProgram(program, now)
                        PortraitEpgItem(
                            program = program,
                            isCurrent = isCurrent,
                            isPast = isPast,
                            oc = oc,
                            onClick = {
                                if (isPast && !isCurrent) {
                                    viewModel.startCatchup(program)
                                } else {
                                    viewModel.toggleReminder(program, currentChannel)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitEpgItem(
    program: com.iptv.scanner.editor.pro.data.IptvEpgProgram,
    isCurrent: Boolean,
    isPast: Boolean,
    oc: PlayerOverlayColors,
    onClick: () -> Unit
) {
    val bg = if (isCurrent) oc.accent.copy(alpha = 0.15f) else Color.Transparent
    val alpha = if (isPast && !isCurrent) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 时间
        val timeText = buildString {
            append(portraitFormatTime(program.start))
            if (program.stop.isNotEmpty() || program.end.isNotEmpty()) {
                append(" - ")
                append(portraitFormatTime(program.stop.ifEmpty { program.end }))
            }
        }
        Text(
            text = timeText,
            color = if (isCurrent) oc.accent else oc.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(90.dp)
        )
        // 节目标题
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program.title,
                color = if (isCurrent) oc.textPrimary else oc.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isCurrent) {
                Surface(
                    color = oc.accent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "正在播出",
                        color = oc.accent,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// EPG 时间解析/格式化辅助
private fun portraitParseTimeMs(iso: String, ts: Long): Long {
    if (ts > 0) return ts * 1000L
    if (iso.isEmpty()) return 0
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm"
    )
    for (pattern in patterns) {
        try {
            return java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(iso)?.time ?: continue
        } catch (_: Exception) {}
    }
    return 0
}

private fun portraitFormatTime(iso: String): String {
    if (iso.isEmpty()) return ""
    val ms = portraitParseTimeMs(iso, 0)
    if (ms <= 0) return iso
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(java.util.Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}

private fun portraitIsCurrentProgram(program: com.iptv.scanner.editor.pro.data.IptvEpgProgram, nowMs: Long): Boolean {
    val startMs = portraitParseTimeMs(program.start, program.startTs)
    val endMs = portraitParseTimeMs(program.end.ifEmpty { program.stop }, program.stopTs)
    return startMs > 0 && endMs > startMs && nowMs >= startMs && nowMs < endMs
}

private fun portraitIsPastProgram(program: com.iptv.scanner.editor.pro.data.IptvEpgProgram, nowMs: Long): Boolean {
    val endMs = portraitParseTimeMs(program.end.ifEmpty { program.stop }, program.stopTs)
    return endMs > 0 && nowMs >= endMs
}

// -----------------------------------------------------------------
// 工具内容（竖屏 Tab）
// -----------------------------------------------------------------

@Composable
private fun PortraitToolsContent(viewModel: AppViewModel) {
    val oc = rememberPlayerOverlayColors()
    val controlsPinned by viewModel.controlsPinned.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val audioVisualizerOpen by viewModel.audioVisualizerOpen.collectAsState()
    val lyricsOpen by viewModel.lyricsOpen.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            PortraitSectionHeader("工具", oc)
        }
        item {
            PortraitListRow("截图", "截取当前画面", oc) { viewModel.takeScreenshot("video") }
        }
        item {
            PortraitListRow("切片导出", "导出视频片段", oc) { viewModel.toggleClipExportPanel() }
        }
        item {
            PortraitListRow("音频可视化", "频谱波形显示", oc, active = audioVisualizerOpen) { viewModel.toggleAudioVisualizer() }
        }
        item {
            PortraitListRow("歌词", "加载/显示歌词", oc, active = lyricsOpen) { viewModel.toggleLyricsPanel() }
        }
        item {
            PortraitListRow("锁定控制层", "防止误触", oc, active = controlsPinned) { viewModel.toggleControlsPinned() }
        }
        item {
            PortraitListRow("刷新", "重新加载频道/EPG", oc) { viewModel.refreshUi() }
        }
        item {
            PortraitListRow("另存为 M3U", "导出频道列表到下载目录", oc) { viewModel.saveAsM3u() }
        }
        item {
            PortraitListRow("最近打开", "最近播放的文件", oc) { viewModel.toggleRecentPanel() }
        }
        item {
            val themeLabel = when (themeMode) { "light" -> "浅色"; "system" -> "跟随系统"; else -> "深色" }
            PortraitListRow("主题切换", "当前: $themeLabel", oc) {
                val next = when (themeMode) { "dark" -> "light"; "light" -> "system"; else -> "dark" }
                viewModel.setThemeMode(next)
            }
        }
        item {
            PortraitSectionHeader("高级工具", oc)
        }
        item {
            PortraitListRow("EPG 时间线", "多频道节目时间线", oc) { viewModel.toggleEpgTimelinePanel() }
        }
        item {
            PortraitListRow("全局搜索", "搜索频道/节目", oc) { viewModel.toggleSearchPanel() }
        }
        item {
            PortraitListRow("流质量检测", "检测流质量", oc) { viewModel.toggleStreamQualityPanel() }
        }
        item {
            PortraitListRow("URL 范围扫描", "扫描整理 URL", oc) { viewModel.toggleScanPanel() }
        }
        item {
            PortraitListRow("节目提醒", "管理提醒", oc) { viewModel.toggleReminderPanel() }
        }
        item {
            PortraitListRow("续播位置", "管理续播", oc) { viewModel.toggleResumePanel() }
        }
        item {
            PortraitListRow("书签管理", "管理书签", oc) { viewModel.toggleBookmarkPanel() }
        }
        item {
            PortraitListRow("A/V 同步监控", "音视频同步", oc) { viewModel.toggleAvSyncPanel() }
        }
    }
}

// -----------------------------------------------------------------
// 设置内容（竖屏 Tab）
// -----------------------------------------------------------------

@Composable
private fun PortraitSettingsContent(viewModel: AppViewModel) {
    val oc = rememberPlayerOverlayColors()
    val themeMode by viewModel.themeMode.collectAsState()
    val autoResume by viewModel.autoResume.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PortraitSectionHeader("播放", oc) }
        item { PortraitListRow("播放器设置", "MPV/ExoPlayer 配置", oc) { viewModel.togglePlayerSettings() } }
        item { PortraitListRow("视频设置", "画面/解码/比例", oc) { viewModel.toggleVideoSettings() } }
        item { PortraitListRow("音频设置", "音轨/音量/均衡器", oc) { viewModel.toggleAudioSettings() } }
        item { PortraitListRow("字幕设置", "字幕/外挂字幕", oc) { viewModel.toggleSubtitleSettings() } }
        item { PortraitListRow("播放设置", "速度/循环/续播", oc) { viewModel.togglePlaybackPanel() } }
        item { PortraitListRow("截图设置", "截图格式/路径", oc) { viewModel.toggleScreenshotPanel() } }
        item { PortraitListRow("视图设置", "界面/缩放/旋转", oc) { viewModel.toggleViewSettings() } }

        item { PortraitSectionHeader("频道与源", oc) }
        item { PortraitListRow("订阅源管理", "添加/删除订阅源", oc) { viewModel.toggleSourceManager() } }
        item { PortraitListRow("频道映射", "频道号映射", oc) { viewModel.toggleMappingPanel() } }
        item { PortraitListRow("网络增强", "缓存/重连/代理", oc) { viewModel.toggleNetworkPanel() } }

        item { PortraitSectionHeader("通用", oc) }
        item {
            val themeLabel = when (themeMode) { "light" -> "浅色"; "system" -> "跟随系统"; else -> "深色" }
            PortraitListRow("主题模式", "当前: $themeLabel", oc) {
                val next = when (themeMode) { "dark" -> "light"; "light" -> "system"; else -> "dark" }
                viewModel.setThemeMode(next)
            }
        }
        item {
            PortraitListRow("启动自动续播", if (autoResume) "已开启" else "已关闭", oc, active = autoResume) {
                viewModel.setAutoResume(!autoResume)
            }
        }
        item { PortraitListRow("关于", "版本/更新信息", oc) { viewModel.toggleAboutPanel() } }
    }
}

// -----------------------------------------------------------------
// 竖屏底部 Tab 栏
// -----------------------------------------------------------------

@Composable
private fun PortraitBottomTabBar(viewModel: AppViewModel) {
    val portraitTab by viewModel.portraitTab.collectAsState()
    val oc = rememberPlayerOverlayColors()

    Surface(
        color = oc.topBarBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PortraitTabItem(
                icon = Icons.Default.VideoLibrary,
                label = "频道",
                isSelected = portraitTab == AppViewModel.PortraitTab.CHANNELS,
                oc = oc,
                onClick = { viewModel.setPortraitTab(AppViewModel.PortraitTab.CHANNELS) }
            )
            PortraitTabItem(
                icon = Icons.Default.Favorite,
                label = "收藏",
                isSelected = portraitTab == AppViewModel.PortraitTab.FAV,
                oc = oc,
                onClick = { viewModel.setPortraitTab(AppViewModel.PortraitTab.FAV) }
            )
            PortraitTabItem(
                icon = Icons.Default.Build,
                label = "工具",
                isSelected = portraitTab == AppViewModel.PortraitTab.TOOLS,
                oc = oc,
                onClick = { viewModel.setPortraitTab(AppViewModel.PortraitTab.TOOLS) }
            )
            PortraitTabItem(
                icon = Icons.Default.Settings,
                label = "设置",
                isSelected = portraitTab == AppViewModel.PortraitTab.SETTINGS,
                oc = oc,
                onClick = { viewModel.setPortraitTab(AppViewModel.PortraitTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun PortraitTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    oc: PlayerOverlayColors,
    onClick: () -> Unit
) {
    val tint = if (isSelected) oc.accent else oc.iconTint
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

/** 竖屏列表行（工具/设置项通用） */
@Composable
private fun PortraitListRow(
    title: String,
    subtitle: String,
    oc: PlayerOverlayColors,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (active) oc.accent else oc.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = oc.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (active) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(oc.accent)
            )
        }
    }
}

/** 竖屏分组标题 */
@Composable
private fun PortraitSectionHeader(
    title: String,
    oc: PlayerOverlayColors
) {
    Text(
        text = title,
        color = oc.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .background(oc.badgeBg)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

// ============================================================
// 竖屏新布局 V2 组件
// ============================================================

/**
 * 第一排：信息栏
 * 居中显示台标+频道名+分类，右侧收藏和信息按钮
 */
@Composable
private fun PortraitInfoBarV2(viewModel: AppViewModel) {
    val currentChannel by viewModel.currentChannel.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val currentIdx by viewModel.currentIdx.collectAsState()
    val oc = rememberPlayerOverlayColors()

    Surface(
        color = oc.topBarBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 居中：台标 + 频道名 + 分类
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                // 台标
                if (currentChannel != null && currentChannel!!.logo.isNotEmpty()) {
                    AsyncImage(
                        model = currentChannel!!.logo,
                        contentDescription = currentChannel!!.name,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                // 频道名
                Text(
                    text = currentChannel?.name ?: "未选择频道",
                    color = oc.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 分类
                if (currentChannel != null && currentChannel!!.group.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = oc.badgeBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = currentChannel!!.group,
                            color = oc.badgeText,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }
            // 右侧：收藏 + 信息按钮
            IconButton(
                onClick = { viewModel.toggleFavorite() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (favorites.contains(currentIdx)) Icons.Default.Star else Icons.Default.Star,
                    contentDescription = "收藏",
                    tint = if (favorites.contains(currentIdx)) Color(0xFFFFC107) else oc.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { viewModel.toggleChannelInfo() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "频道信息",
                    tint = oc.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 第三排：播放控制栏
 * 播放/停止按钮 + 圆点进度条
 */
@Composable
private fun PortraitControlsV2(viewModel: AppViewModel) {
    val mpv = viewModel.mpv
    val paused by mpv.paused.collectAsState()
    val oc = rememberPlayerOverlayColors()

    // 进度数据（1秒刷新）
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

    Surface(
        color = oc.infoBarBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放/暂停按钮
            IconButton(
                onClick = { mpv.togglePause() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (paused) "播放" else "暂停",
                    tint = oc.iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            // 停止按钮
            IconButton(
                onClick = { viewModel.stopPlay() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "停止",
                    tint = oc.iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            // 时间标签 - 开始
            Text(
                text = progressInfo.startLabel,
                color = oc.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            // 圆点进度条
            DotProgressBar(
                progress = if (dragging) dragPercent else (progressInfo.percent / 100f),
                onSeek = { percent ->
                    dragPercent = percent
                    dragging = true
                },
                onSeekEnd = {
                    viewModel.seekProgress(dragPercent * 100f)
                    dragging = false
                },
                accentColor = oc.accent,
                trackColor = oc.trackInactive,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(24.dp)
            )
            // 时间标签 - 结束
            Text(
                text = progressInfo.endLabel,
                color = oc.textSecondary,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * 圆点进度条：用 Canvas 绘制轨道 + 圆点指示器
 */
@Composable
private fun DotProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackHeight = with(density) { 3.dp.toPx() }
    val dotRadius = with(density) { 6.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width.toFloat()
                        if (w > 0) {
                            val percent = (offset.x / w).coerceIn(0f, 1f)
                            onSeek(percent)
                            onSeekEnd()
                        }
                    }
                )
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height
        val centerY = canvasH / 2f
        val progressW = canvasW * progress

        // 背景轨道
        drawRoundRect(
            color = trackColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
            size = androidx.compose.ui.geometry.Size(canvasW, trackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2, trackHeight / 2)
        )
        // 已播放轨道
        if (progressW > 0) {
            drawRoundRect(
                color = accentColor,
                topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - trackHeight / 2),
                size = androidx.compose.ui.geometry.Size(progressW, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2, trackHeight / 2)
            )
        }
        // 圆点指示器
        drawCircle(
            color = accentColor,
            radius = dotRadius,
            center = androidx.compose.ui.geometry.Offset(progressW.coerceIn(0f, canvasW), centerY)
        )
    }
}

/**
 * 竖屏频道列表（频道Tab和收藏Tab共用）
 * 左列分组 + 右列频道（双列布局），无搜索框
 * 每项：台标 + 频道名(+回看标识) + 当前节目名 + 节目单按钮
 */
@Composable
private fun PortraitChannelList(
    viewModel: AppViewModel,
    showFavoritesOnly: Boolean
) {
    val channels by viewModel.channels.collectAsState()
    val currentIdx by viewModel.currentIdx.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val allGroups by viewModel.groups.collectAsState()
    val channelsTab by viewModel.channelsTab.collectAsState()
    val oc = rememberPlayerOverlayColors()

    // 分组列表
    val groups = remember(allGroups, channels, showFavoritesOnly) {
        if (showFavoritesOnly) {
            channels.mapIndexed { idx, c -> c to idx }
                .filter { (c, idx) -> favorites.contains(idx) }
                .map { it.first.group }
                .filter { it.isNotEmpty() }
                .distinct()
        } else if (channelsTab == ChannelTab.LOCAL) {
            channels
                .filter { it.source.isEmpty() || ProgressHelper.isLocalFile(it.url) }
                .map { it.group }
                .filter { it.isNotEmpty() }
                .distinct()
        } else {
            allGroups
        }
    }
    val showGroups = groups.isNotEmpty()

    // 根据是否收藏模式过滤频道
    val filteredChannels = remember(channels, favorites, showFavoritesOnly, selectedGroup, channelsTab) {
        val all = channels.mapIndexed { idx, c -> c to idx }
        val filtered = if (showFavoritesOnly) {
            all.filter { (c, idx) -> favorites.contains(idx) }
        } else if (channelsTab == ChannelTab.LOCAL) {
            all.filter { (c, _) -> c.source.isEmpty() || ProgressHelper.isLocalFile(c.url) }
        } else {
            all
        }
        filtered.filter { (c, _) ->
            val groupMatch = selectedGroup.isEmpty() || c.group == selectedGroup
            groupMatch
        }
    }

    if (filteredChannels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (showFavoritesOnly) "暂无收藏\n点击信息栏星标添加" else "暂无频道",
                color = oc.textSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        if (showGroups) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 左列：分组列表
                PortraitGroupList(
                    groups = groups,
                    selectedGroup = selectedGroup,
                    onGroupSelected = { viewModel.setSelectedGroup(it) },
                    oc = oc,
                    modifier = Modifier.weight(0.35f)
                )
                // 分隔线
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(oc.trackInactive)
                )
                // 右列：频道列表
                LazyColumn(
                    modifier = Modifier.weight(0.65f).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = filteredChannels,
                        key = { (channel, idx) -> idx }
                    ) { (channel, idx) ->
                        PortraitChannelListItem(
                            channel = channel,
                            channelIdx = idx,
                            isPlaying = idx == currentIdx,
                            oc = oc,
                            viewModel = viewModel,
                            onPlay = { viewModel.playChannel(idx) },
                            onEpg = { viewModel.playChannelAndShowEpg(idx) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = filteredChannels,
                    key = { (channel, idx) -> idx }
                ) { (channel, idx) ->
                    PortraitChannelListItem(
                        channel = channel,
                        channelIdx = idx,
                        isPlaying = idx == currentIdx,
                        oc = oc,
                        viewModel = viewModel,
                        onPlay = { viewModel.playChannel(idx) },
                        onEpg = { viewModel.playChannelAndShowEpg(idx) }
                    )
                }
            }
        }
    }
}

/**
 * 竖屏分组列表（左列）
 */
@Composable
private fun PortraitGroupList(
    groups: List<String>,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    oc: PlayerOverlayColors,
    modifier: Modifier = Modifier
) {
    val allItems = remember(groups) { listOf("") + groups }
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(allItems, key = { it }) { group ->
            val isSelected = selectedGroup == group
            val label = if (group.isEmpty()) "全部" else group
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGroupSelected(group) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) oc.accent else Color.Transparent)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    color = if (isSelected) oc.accent else oc.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 竖屏频道列表项
 * 台标 + 频道名(+回看标识) + 当前节目名 + 节目单按钮
 */
@Composable
private fun PortraitChannelListItem(
    channel: IptvChannel,
    channelIdx: Int,
    isPlaying: Boolean,
    oc: PlayerOverlayColors,
    viewModel: AppViewModel,
    onPlay: () -> Unit,
    onEpg: () -> Unit
) {
    // 获取缓存的当前节目
    var currentProgram by remember { mutableStateOf<com.iptv.scanner.editor.pro.data.IptvEpgProgram?>(null) }
    LaunchedEffect(channelIdx) {
        while (true) {
            currentProgram = viewModel.getCachedCurrentProgram(channelIdx)
            delay(3_000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 台标
        if (channel.logo.isNotEmpty()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            // 无台标占位
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(oc.badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(1),
                    color = oc.textSecondary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 频道名 + 节目名
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    color = if (isPlaying) oc.accent else oc.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 回看标识
                if (channel.catchup.isNotEmpty() && channel.catchup != "none") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = oc.accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "回看",
                            color = oc.accent,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            // 当前节目名
            if (currentProgram != null && currentProgram!!.title.isNotEmpty()) {
                Text(
                    text = currentProgram!!.title,
                    color = oc.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "精彩节目",
                    color = oc.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 节目单按钮
        IconButton(
            onClick = onEpg,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "节目单",
                tint = oc.iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 频道信息详情对话框
 */
@Composable
private fun ChannelInfoDialog(viewModel: AppViewModel) {
    val currentChannel by viewModel.currentChannel.collectAsState()
    val mpv = viewModel.mpv
    val videoWidth by mpv.videoWidth.collectAsState()
    val videoHeight by mpv.videoHeight.collectAsState()
    val currentProgram = viewModel.getCurrentProgram()
    val oc = rememberPlayerOverlayColors()

    AlertDialog(
        onDismissRequest = { viewModel.toggleChannelInfo() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentChannel != null && currentChannel!!.logo.isNotEmpty()) {
                    AsyncImage(
                        model = currentChannel!!.logo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = currentChannel?.name ?: "频道信息",
                    color = oc.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                currentChannel?.let { ch ->
                    InfoRow("频道名", ch.name, oc)
                    if (ch.group.isNotEmpty()) InfoRow("分类", ch.group, oc)
                    if (ch.tvgId.isNotEmpty()) InfoRow("TVG-ID", ch.tvgId, oc)
                    if (ch.tvgName.isNotEmpty()) InfoRow("TVG-Name", ch.tvgName, oc)
                    if (ch.tvgChno.isNotEmpty()) InfoRow("频道号", ch.tvgChno, oc)
                    if (ch.resolution.isNotEmpty()) InfoRow("分辨率", ch.resolution, oc)
                    if (videoWidth > 0 && videoHeight > 0) {
                        InfoRow("视频尺寸", "${videoWidth}x${videoHeight}", oc)
                    }
                    if (ch.catchup.isNotEmpty() && ch.catchup != "none") {
                        InfoRow("回看", "支持 (${ch.catchup})", oc)
                        if (ch.catchupDays.isNotEmpty()) InfoRow("回看天数", ch.catchupDays, oc)
                    }
                    if (ch.source.isNotEmpty()) InfoRow("来源", ch.source, oc)
                    if (ch.status.isNotEmpty()) InfoRow("状态", ch.status, oc)
                    InfoRow("URL", ch.url, oc)
                    // 当前节目
                    if (currentProgram != null && currentProgram.title.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当前节目",
                            color = oc.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = currentProgram.title,
                            color = oc.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                } ?: run {
                    Text(
                        text = "未选择频道",
                        color = oc.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.toggleChannelInfo() },
                colors = ButtonDefaults.buttonColors(containerColor = oc.accent)
            ) {
                Text("关闭", color = Color.White)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, oc: PlayerOverlayColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            color = oc.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = value,
            color = oc.textPrimary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}