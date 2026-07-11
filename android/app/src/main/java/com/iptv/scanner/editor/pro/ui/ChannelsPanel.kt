package com.iptv.scanner.editor.pro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.scanner.editor.pro.data.IptvChannel
import com.iptv.scanner.editor.pro.player.ProgressHelper
import com.iptv.scanner.editor.pro.ui.AppViewModel.ChannelTab
import com.iptv.scanner.editor.pro.ui.theme.tvFocusBorder
import com.iptv.scanner.editor.pro.ui.theme.tvTextField

/**
 * 频道列表面板：5 个 tab + 搜索 + 分组过滤 + LazyColumn。
 *
 * 与 PC 端 mobile/index.html panelChannels 对齐：
 * - 5 个 tab：订阅/本地/收藏/历史/队列
 * - 搜索框：输入即过滤（name/group 包含关键字）
 * - 分组过滤器（仅 SUB/LOCAL tab 显示）
 * - 列表项：圆点 + 名称 + 分组 meta + 当前播放高亮
 * - 点击列表项 → playChannel(idx)
 *
 * 布局：右侧抽屉（380dp 宽），双列（左分组 + 右频道）。
 * TV 模式也可从统一面板菜单进入此面板（DPAD 焦点导航）。
 */
@Composable
fun ChannelsPanel(viewModel: AppViewModel, inline: Boolean = false) {
    val tab by viewModel.channelsTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val allGroups by viewModel.groups.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val currentIdx by viewModel.currentIdx.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val history by viewModel.history.collectAsState()
    val queue by viewModel.queue.collectAsState()

    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        kotlin.runCatching { closeFocusRequester.requestFocus() }
    }

    // 根据 tab 过滤分组（与 TvUnifiedPanel 对齐）：
    // - LOCAL tab：只显示本地频道的分组，避免显示订阅分组误导用户
    // - SUB tab：显示所有分组
    val groups = remember(allGroups, channels, tab) {
        if (tab == ChannelTab.LOCAL) {
            channels
                .filter { it.source.isEmpty() || ProgressHelper.isLocalFile(it.url) }
                .map { it.group }
                .filter { it.isNotEmpty() }
                .distinct()
        } else {
            allGroups
        }
    }

    val showGroups = (tab == ChannelTab.SUB || tab == ChannelTab.LOCAL) && groups.isNotEmpty()

    val filteredChannels = remember(tab, searchQuery, selectedGroup, channels, favorites, history, queue) {
        viewModel.getFilteredChannels()
    }

    val surfaceModifier = if (inline) {
        // 内联模式：填满父容器，无宽度限制
        Modifier.fillMaxSize()
    } else {
        // 抽屉模式：右侧 92% 宽，最大 380dp
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.92f)
            .widthIn(max = 380.dp)
    }

    Surface(
        color = Color(0xF0161616),
        modifier = surfaceModifier
    ) {
        Column(modifier = Modifier.fillMaxSize().then(if (inline) Modifier else Modifier.systemBarsPadding())) {
            // -----------------------------------------------------------------
            // 标题栏（内联模式不显示关闭按钮）
            // -----------------------------------------------------------------
            if (inline) {
                // 内联模式：简洁标题栏（无关闭按钮）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "频道列表",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${channels.size} 个频道",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )
                }
            } else {
                PanelHeader(
                    title = "频道列表",
                    subtitle = "${channels.size} 个频道",
                    onClose = { viewModel.toggleChannelsPanel() },
                    closeFocusRequester = closeFocusRequester
                )
            }

            // -----------------------------------------------------------------
            // 5 个 Tab
            // -----------------------------------------------------------------
            ChannelTabsRow(
                currentTab = tab,
                onTabSelected = { viewModel.setChannelsTab(it) }
            )

            // -----------------------------------------------------------------
            // 搜索框
            // -----------------------------------------------------------------
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("搜索频道...", color = Color(0xFF888888), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF888888)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.tvFocusBorder()) {
                            Icon(Icons.Default.Close, contentDescription = "清空", tint = Color(0xFF888888))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .tvTextField(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Divider(color = Color(0xFF2A2A2A))

            // -----------------------------------------------------------------
            // 主体区域：左列分组 + 右列频道（双列布局）
            // 收藏/历史/队列 tab 无分组数据，直接全宽显示频道列表
            // -----------------------------------------------------------------
            if (showGroups) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左列：分组列表
                    GroupListColumn(
                        groups = groups,
                        selectedGroup = selectedGroup,
                        onGroupSelected = { viewModel.setSelectedGroup(it) },
                        modifier = Modifier.weight(0.38f)
                    )

                    // 分隔线
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFF2A2A2A))
                    )

                    // 右列：频道列表
                    ChannelListContent(
                        filteredChannels = filteredChannels,
                        currentIdx = currentIdx,
                        favorites = favorites,
                        tab = tab,
                        onPlay = { viewModel.playChannel(it) },
                        modifier = Modifier.weight(0.62f)
                    )
                }
            } else {
                ChannelListContent(
                    filteredChannels = filteredChannels,
                    currentIdx = currentIdx,
                    favorites = favorites,
                    tab = tab,
                    onPlay = { viewModel.playChannel(it) }
                )
            }
        }
    }
}

/**
 * 频道列表内容（空态 + LazyColumn）。
 */
@Composable
private fun ChannelListContent(
    filteredChannels: List<Pair<IptvChannel, Int>>,
    currentIdx: Int,
    favorites: Set<Int>,
    tab: ChannelTab,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (filteredChannels.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (tab) {
                    ChannelTab.SUB -> "暂无频道\n请在主菜单 > 文件 > 订阅源管理 添加"
                    ChannelTab.LOCAL -> "暂无本地频道"
                    ChannelTab.FAV -> "暂无收藏\n点击频道右侧星标添加"
                    ChannelTab.HIST -> "暂无历史\n播放后会自动记录"
                    ChannelTab.QUEUE -> "暂无队列\n长按频道可加入队列"
                },
                color = Color(0xFF888888),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(
                items = filteredChannels,
                key = { (channel, idx) -> idx }
            ) { (channel, idx) ->
                ChannelListItem(
                    channel = channel,
                    isPlaying = idx == currentIdx,
                    isFavorite = favorites.contains(idx),
                    showGroupMeta = tab == ChannelTab.SUB || tab == ChannelTab.LOCAL,
                    onClick = { onPlay(idx) }
                )
            }
        }
    }
}

/**
 * 左列：分组纵向滚动列表。
 * 使用 tvFocusBorder 确保 TV 模式下 DPAD 焦点可见。
 */
@Composable
private fun GroupListColumn(
    groups: List<String>,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allItems = remember(groups) { listOf("") + groups }

    Surface(
        color = Color(0xFF121212),
        modifier = modifier.fillMaxHeight()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(allItems, key = { it }) { group ->
                val isSelected = selectedGroup == group
                val label = if (group.isEmpty()) "全部" else group
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusBorder()
                        .clickable { onGroupSelected(group) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧选中指示条
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) Color(0xFF4A9EFF) else Color.Transparent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = if (isSelected) Color(0xFF6A9EFF) else Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 面板标题栏（统一组件）。
 *
 * @param closeFocusRequester 可选的 FocusRequester，绑定到关闭按钮。
 *        TV 模式下面板打开时需要初始焦点，否则 DPAD 无法操作。
 *        传入时关闭按钮会绑定此 requester，调用方用 LaunchedEffect 请求焦点。
 */
@Composable
fun PanelHeader(
    title: String,
    subtitle: String = "",
    onClose: () -> Unit,
    closeFocusRequester: FocusRequester? = null
) {
    Surface(
        color = Color(0xFF1F1F1F),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .tvFocusBorder()
                    .then(
                        if (closeFocusRequester != null) Modifier.focusRequester(closeFocusRequester)
                        else Modifier
                    )
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

/**
 * 5 个 Tab 行（订阅/本地/收藏/历史/队列）。
 */
@Composable
private fun ChannelTabsRow(
    currentTab: ChannelTab,
    onTabSelected: (ChannelTab) -> Unit
) {
    val tabs = listOf(
        ChannelTab.SUB to "订阅",
        ChannelTab.LOCAL to "本地",
        ChannelTab.FAV to "收藏",
        ChannelTab.HIST to "历史",
        ChannelTab.QUEUE to "队列"
    )
    Surface(
        color = Color(0xFF1A1A1A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { (tab, label) ->
                val isSelected = currentTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4A9EFF),
                        selectedLabelColor = Color.White,
                        containerColor = Color.Transparent,
                        labelColor = Color(0xFFCCCCCC)
                    )
                )
            }
        }
    }
}

/**
 * 频道列表项。
 */
@Composable
private fun ChannelListItem(
    channel: IptvChannel,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showGroupMeta: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusBorder()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆点（playing 时高亮蓝色，否则灰色）
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isPlaying) Color(0xFF4A9EFF) else Color(0xFF444444))
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 频道名 + 分组 meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isPlaying) Color(0xFF6A9EFF) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showGroupMeta && channel.group.isNotEmpty()) {
                Text(
                    text = channel.group,
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 收藏标记
        if (isFavorite) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("★", color = Color(0xFFFFC107), fontSize = 12.sp)
        }
    }
}


