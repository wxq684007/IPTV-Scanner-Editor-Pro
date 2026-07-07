package com.iptv.scanner.editor.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.scanner.editor.pro.ui.theme.tvFocusBorder

/**
 * 多画面网格容器。
 *
 * 根据 [MultiViewState.layout] 渲染网格：
 * - DUAL：左右分屏（主画面左，副画面右）
 * - QUAD：2x2 网格（主画面左上，副画面右上/左下/右下）
 *
 * 主画面（index=0）由 [primaryContent] Composable 渲染（复用 MainPlayerScreen 现有播放器 View 逻辑）。
 * 副画面（index=1+）当前不可用（MPV 单例限制），显示占位提示。
 *
 * @param state 多画面状态
 * @param primaryContent 主画面内容 Composable
 * @param getSubPlayer 获取副画面 Player 实例（按 index）
 * @param onViewportClick 视口点击回调（TV 端焦点切换 / 手机端点击添加频道）
 * @param onViewportClose 视口关闭回调（副画面关闭按钮）
 * @param modifier Modifier
 */
@Composable
fun MultiViewOverlay(
    state: MultiViewState,
    primaryContent: @Composable BoxScope.() -> Unit,
    getSubPlayer: (Int) -> com.iptv.scanner.editor.pro.player.Player?,
    onViewportClick: (Int) -> Unit,
    onViewportClose: (Int) -> Unit,
    onToggleMute: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.active) return

    val focusedColor = MaterialTheme.colorScheme.primary
    val unfocusedColor = Color.White.copy(alpha = 0.2f)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (state.layout) {
            MultiViewLayout.DUAL -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 主画面（左）
                ViewportCell(
                    viewport = state.viewports.getOrNull(0),
                    isFocused = state.focusedIndex == 0,
                    focusedColor = focusedColor,
                    unfocusedColor = unfocusedColor,
                    onClick = { onViewportClick(0) },
                    onClose = null,  // 主画面不能关闭
                    onToggleMute = { onToggleMute(0) },
                    modifier = Modifier.weight(1f)
                ) {
                    primaryContent()
                }
                // 副画面（右）
                ViewportCell(
                    viewport = state.viewports.getOrNull(1),
                    isFocused = state.focusedIndex == 1,
                    focusedColor = focusedColor,
                    unfocusedColor = unfocusedColor,
                    onClick = { onViewportClick(1) },
                    onClose = { onViewportClose(1) },
                    onToggleMute = { onToggleMute(1) },
                    modifier = Modifier.weight(1f)
                ) {
                    SubViewportContent(
                        viewportIndex = 1,
                        viewport = state.viewports.getOrNull(1),
                        getSubPlayer = getSubPlayer
                    )
                }
            }
            MultiViewLayout.QUAD -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 上排
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 主画面（左上）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(0),
                        isFocused = state.focusedIndex == 0,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(0) },
                        onClose = null,
                        onToggleMute = { onToggleMute(0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        primaryContent()
                    }
                    // 副画面（右上）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(1),
                        isFocused = state.focusedIndex == 1,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(1) },
                        onClose = { onViewportClose(1) },
                        onToggleMute = { onToggleMute(1) },
                        modifier = Modifier.weight(1f)
                    ) {
                        SubViewportContent(
                            viewportIndex = 1,
                            viewport = state.viewports.getOrNull(1),
                            getSubPlayer = getSubPlayer
                        )
                    }
                }
                // 下排
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 副画面（左下）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(2),
                        isFocused = state.focusedIndex == 2,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(2) },
                        onClose = { onViewportClose(2) },
                        onToggleMute = { onToggleMute(2) },
                        modifier = Modifier.weight(1f)
                    ) {
                        SubViewportContent(
                            viewportIndex = 2,
                            viewport = state.viewports.getOrNull(2),
                            getSubPlayer = getSubPlayer
                        )
                    }
                    // 副画面（右下）
                    ViewportCell(
                        viewport = state.viewports.getOrNull(3),
                        isFocused = state.focusedIndex == 3,
                        focusedColor = focusedColor,
                        unfocusedColor = unfocusedColor,
                        onClick = { onViewportClick(3) },
                        onClose = { onViewportClose(3) },
                        onToggleMute = { onToggleMute(3) },
                        modifier = Modifier.weight(1f)
                    ) {
                        SubViewportContent(
                            viewportIndex = 3,
                            viewport = state.viewports.getOrNull(3),
                            getSubPlayer = getSubPlayer
                        )
                    }
                }
            }
            MultiViewLayout.NINE -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 3x3 网格：主画面在左上角 (0)，副画面 1-8 填充其余位置
                // Row 0: [0 主] [1 副] [2 副]
                // Row 1: [3 副] [4 副] [5 副]
                // Row 2: [6 副] [7 副] [8 副]
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            ViewportCell(
                                viewport = state.viewports.getOrNull(idx),
                                isFocused = state.focusedIndex == idx,
                                focusedColor = focusedColor,
                                unfocusedColor = unfocusedColor,
                                onClick = { onViewportClick(idx) },
                                onClose = if (idx == 0) null else { { onViewportClose(idx) } },
                                onToggleMute = { onToggleMute(idx) },
                                modifier = Modifier.weight(1f)
                            ) {
                                if (idx == 0) {
                                    primaryContent()
                                } else {
                                    SubViewportContent(
                                        viewportIndex = idx,
                                        viewport = state.viewports.getOrNull(idx),
                                        getSubPlayer = getSubPlayer
                                    )
                                }
                            }
                        }
                    }
                }
            }
            MultiViewLayout.SINGLE -> {
                // SINGLE 模式不应该进入多画面，这里兜底
                primaryContent()
            }
        }
    }
}

/**
 * 单个视口 Cell（包含边框、静音/关闭按钮、内容）。
 */
@Composable
private fun ViewportCell(
    viewport: MultiViewport?,
    isFocused: Boolean,
    focusedColor: Color,
    unfocusedColor: Color,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    onToggleMute: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor = if (isFocused) focusedColor else unfocusedColor
    val borderWidth = if (isFocused) 3.dp else 1.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .background(Color.Black)
            .clickable { onClick() }
    ) {
        // 内容
        content()

        // 空画面提示
        if (viewport != null && viewport.isEmpty) {
            EmptyViewportHint(
                text = "点击添加频道",
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 错误画面提示
        if (viewport != null && viewport.isError) {
            ErrorViewportHint(
                channelName = viewport.channelName,
                errorMessage = viewport.errorMessage,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 频道名标签（左上角）
        if (viewport != null && !viewport.isEmpty) {
            ChannelLabel(
                text = viewport.channelName,
                isPrimary = viewport.isPrimary,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        // 右上角控制按钮（静音 + 关闭）
        if (viewport != null && !viewport.isEmpty) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 静音按钮
                if (onToggleMute != null) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(28.dp)
                            .tvFocusBorder()
                    ) {
                        Icon(
                            if (viewport.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (viewport.isMuted) "取消静音" else "静音",
                            tint = if (viewport.isMuted) Color(0xFFFF6B6B) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 关闭按钮（仅副画面）
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .tvFocusBorder()
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 副画面内容占位。
 *
 * MPV 在安卓端为单例，不支持多实例，副画面当前不可用。
 * 仅保留 UI 结构，显示"暂不可用"提示。
 */
@Composable
private fun SubViewportContent(
    viewportIndex: Int,
    viewport: MultiViewport?,
    getSubPlayer: (Int) -> com.iptv.scanner.editor.pro.player.Player?
) {
    // MPV 单例限制：副画面不可用，显示占位提示
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "副画面暂不可用",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
            Text(
                text = "仅支持 MPV 单画面",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EmptyViewportHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ErrorViewportHint(channelName: String, errorMessage: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = Color.Red.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = channelName,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = errorMessage,
            color = Color.Red.copy(alpha = 0.8f),
            fontSize = 10.sp,
            maxLines = 2
        )
    }
}

@Composable
private fun ChannelLabel(text: String, isPrimary: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isPrimary) Color(0xFF4A9EFF) else Color.Black.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isPrimary) "主: $text" else text,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
