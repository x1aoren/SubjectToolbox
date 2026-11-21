package cn.stb.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.stb.core.designsystem.EmptyState
import cn.stb.core.designsystem.SearchField
import cn.stb.core.designsystem.SectionHeader
import cn.stb.core.designsystem.ToolCard
import cn.stb.core.designsystem.ToolDisabledDialog
import cn.stb.core.model.AppGraph
import cn.stb.core.model.CloudAnnouncement
import cn.stb.core.model.HomeContent
import cn.stb.core.model.HomeSection
import cn.stb.core.model.ManagedToolIds
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec


/**
 * 公告浮窗：一组卡片贴在主页右下角，不参与内容区排版。
 *
 * 列表超出屏幕 60% 高度时内部滚动，卡片按公告等级取容器色，
 * 颜色和正文都跟着等级走，不会出现浅底浅字。
 */
@Composable
private fun AnnouncementFloatingPanel(
    announcements: List<CloudAnnouncement>,
    onAcknowledge: (CloudAnnouncement) -> Unit,
    onMute: (CloudAnnouncement) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (announcements.isEmpty()) return
    val configuration = LocalConfiguration.current
    val panelWidth = (configuration.screenWidthDp.dp * 0.4f).coerceIn(300.dp, 420.dp)
    val maxHeight = configuration.screenHeightDp.dp * 0.6f

    Column(
        modifier = modifier
            .width(panelWidth)
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        announcements.forEach { announcement ->
            AnnouncementFloatingCard(
                announcement = announcement,
                onAcknowledge = { onAcknowledge(announcement) },
                onMute = { onMute(announcement) },
            )
        }
    }
}

@Composable
private fun AnnouncementFloatingCard(
    announcement: CloudAnnouncement,
    onAcknowledge: () -> Unit,
    onMute: () -> Unit,
) {
    val container: Color
    val onContainer: Color
    when (announcement.level) {
        "critical" -> {
            container = MaterialTheme.colorScheme.errorContainer
            onContainer = MaterialTheme.colorScheme.onErrorContainer
        }
        "warning" -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            onContainer = MaterialTheme.colorScheme.onSecondaryContainer
        }
        else -> {
            container = MaterialTheme.colorScheme.primaryContainer
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = onContainer,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Text(text = announcement.title, style = MaterialTheme.typography.titleSmall)
            if (announcement.body.isNotBlank()) {
                Text(
                    text = announcement.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onMute,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        text = "不再提示",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer.copy(alpha = 0.72f),
                    )
                }
                TextButton(
                    onClick = onAcknowledge,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Text(
                        text = "知道了",
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun MaintenanceBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("服务维护中", style = MaterialTheme.typography.titleSmall)
            Text(
                text = message.ifBlank { "部分在线功能可能暂时不可用，离线工具不受影响。" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
private const val SECTION_COLUMNS = 3

@Composable
fun HomeScreen(
    onOpenTool: (ToolId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenDisplayManager: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val contentState by viewModel.content.collectAsStateWithLifecycle()
    val content = contentState ?: return
    val cloud by AppGraph.cloud.snapshot.collectAsStateWithLifecycle()

    // 本次运行里点过「知道了」的公告。只放内存，下次启动还会出现；
    // 点「不再提示」才写进 CloudControl，之后永久过滤掉。
    var acknowledgedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val announcements = cloud.announcements.filter { announcement ->
        announcement.id !in acknowledgedIds &&
            !AppGraph.cloud.announcementDismissed(announcement.id)
    }

    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(content, query) { content.filtered(query) }
    val catalog = AppGraph.catalog

    // 被云端停用的工具不再从主页消失：置灰 + 「已停用」徽章，点击弹出停用原因。
    // 所有入口（置顶/学科分区/最近使用/搜索结果/右上角反馈）共用同一套判定与弹窗。
    var disabledNotice by remember { mutableStateOf<DisabledToolNotice?>(null) }

    fun isToolEnabled(tool: ToolSpec): Boolean = cloud.tools[tool.id.value]?.enabled != false

    fun openTool(tool: ToolSpec) {
        if (isToolEnabled(tool)) {
            onOpenTool(tool.id)
        } else {
            disabledNotice = DisabledToolNotice(
                title = tool.title,
                reason = cloud.disabledReasonFor(tool.id.value),
                source = cloud.disabledSourceFor(tool.id.value),
            )
        }
    }

    val feedbackDisabled = cloud.isToolDisabled(ManagedToolIds.FEEDBACK)
    val feedbackTitle = catalog.findEntry(ManagedToolIds.feedback)?.title ?: "意见反馈"

    // 顶栏小字里的「N 个已停用」只统计当前实际展示的主工具，避免把子工具算进去
    val shownDisabledCount = remember(shown, cloud) {
        (shown.pinned + shown.sections.flatMap { it.tools } + shown.recents)
            .distinctBy { it.id.value }
            .count { cloud.tools[it.id.value]?.enabled == false }
    }

    // 置顶/最近卡片与学科栏同宽：三栏布局下每栏 = (窗口宽 - 左右内边距 - 两个间隔)/3
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = ((windowWidth - 56.dp - 36.dp) / 3).coerceAtLeast(240.dp)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "学科工具箱",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("${catalog.subjects.size} 个学科 · ${content.totalVisible} 个工具")
                            if (shownDisabledCount > 0) append("（其中 $shownDisabledCount 个已停用）")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 意见反馈入口始终显示：被云端停用时点击弹原因，而不是把入口藏起来
                IconButton(
                    onClick = {
                        if (feedbackDisabled) {
                            disabledNotice = DisabledToolNotice(
                                title = feedbackTitle,
                                reason = cloud.disabledReasonFor(ManagedToolIds.FEEDBACK),
                                source = cloud.disabledSourceFor(ManagedToolIds.FEEDBACK),
                            )
                        } else {
                            onOpenFeedback()
                        }
                    },
                ) {
                    Icon(
                        Icons.Filled.Email,
                        contentDescription = "意见反馈",
                        tint = if (feedbackDisabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (cloud.maintenance) {
                Spacer(Modifier.height(12.dp))
                MaintenanceBanner(cloud.maintenanceMessage)
            }

            Spacer(Modifier.height(14.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索工具，如「函数」「词」",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(22.dp))

            if (content.isEmpty) {
                EmptyState(
                    glyph = "空",
                    title = "所有工具都被隐藏了",
                    description = "在显示管理里把想要的学科或工具打开，主页就会重新出现它们。",
                    actionLabel = "去显示管理",
                    onAction = onOpenDisplayManager,
                )
                return@Column
            }

            if (shown.pinned.isNotEmpty()) {
                SectionHeader(
                    title = "我的常用",
                    accent = MaterialTheme.colorScheme.primary,
                    count = shown.pinned.size,
                    trailing = if (content.hiddenPinnedCount > 0) {
                        {
                            Text(
                                text = "${content.hiddenPinnedCount} 个已隐藏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else null,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    shown.pinned.forEach { tool ->
                        ToolRow(
                            tool = tool,
                            viewModel = viewModel,
                            enabled = isToolEnabled(tool),
                            onClick = { openTool(tool) },
                            isPinned = true,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // 按行铺开学科：同一行的学科标题从同一条基线开始，避免三列各自堆叠后错位。
            val pinnedIds = content.pinned.map { it.id }.toSet()
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                shown.sections.chunked(SECTION_COLUMNS).forEach { rowSections ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowSections.forEach { section ->
                            SubjectBlock(
                                section = section,
                                viewModel = viewModel,
                                isToolEnabled = { isToolEnabled(it) },
                                onOpen = { openTool(it) },
                                pinnedIds = pinnedIds,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 最后一行不足三栏时补空位，保持每栏等宽，不把剩下的学科拉宽
                        repeat(SECTION_COLUMNS - rowSections.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (shown.recents.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionHeader(
                    title = "最近使用",
                    accent = MaterialTheme.colorScheme.outline,
                    count = shown.recents.size,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    shown.recents.forEach { tool ->
                        ToolRow(
                            tool = tool,
                            viewModel = viewModel,
                            enabled = isToolEnabled(tool),
                            onClick = { openTool(tool) },
                            isPinned = content.pinned.any { it.id == tool.id },
                            dimmed = true,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "点工具卡片右上角的星标，就能把它置顶到「我的常用」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(16.dp))
        }

        // 维护提示留在内容区，普通公告一律走右下角浮窗，不再挤占主页布局。
        AnnouncementFloatingPanel(
            announcements = if (cloud.maintenance) emptyList() else announcements,
            onAcknowledge = { acknowledgedIds = acknowledgedIds + it.id },
            onMute = {
                AppGraph.cloud.dismissAnnouncement(it.id)
                acknowledgedIds = acknowledgedIds + it.id
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 20.dp),
        )
    }

    disabledNotice?.let { notice ->
        ToolDisabledDialog(
            title = notice.title,
            reason = notice.reason,
            source = notice.source,
            onDismiss = { disabledNotice = null },
        )
    }
}

/** 一次停用提示：标题 + 原因 + 来源；主页所有入口（工具卡片、反馈图标）共用。 */
private data class DisabledToolNotice(
    val title: String,
    val reason: String,
    val source: String?,
)

@Composable
private fun SubjectBlock(
    section: HomeSection,
    viewModel: HomeViewModel,
    isToolEnabled: (ToolSpec) -> Boolean,
    onOpen: (ToolSpec) -> Unit,
    pinnedIds: Set<ToolId>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = section.subject.name,
            accent = Color(section.subject.accent),
            count = section.tools.size,
            icon = section.subject.icon,
        )
        section.tools.forEach { tool ->
            ToolRow(
                tool = tool,
                viewModel = viewModel,
                enabled = isToolEnabled(tool),
                onClick = { onOpen(tool) },
                isPinned = tool.id in pinnedIds,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToolRow(
    tool: ToolSpec,
    viewModel: HomeViewModel,
    enabled: Boolean,
    onClick: () -> Unit,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val subject = AppGraph.catalog.subjects.firstOrNull { it.id == tool.subjectId }
    val accent = Color(subject?.accent ?: 0xFF666666)
    ToolCard(
        spec = tool,
        accent = if (dimmed) accent.copy(alpha = 0.75f) else accent,
        pinned = isPinned,
        enabled = enabled,
        onClick = onClick,
        onTogglePin = { viewModel.togglePin(tool.id) },
        modifier = modifier,
    )
}

private fun HomeContent.filtered(query: String): HomeContent {
    if (query.isBlank()) return this
    val key = query.trim().lowercase()

    fun ToolSpec.matches(): Boolean =
        title.lowercase().contains(key) ||
            summary.lowercase().contains(key) ||
            keywords.any { it.lowercase().contains(key) }

    return copy(
        pinned = pinned.filter { it.matches() },
        sections = sections.mapNotNull { section ->
            val tools = section.tools.filter { it.matches() }
            if (tools.isEmpty()) null else section.copy(tools = tools)
        },
        recents = recents.filter { it.matches() },
    )
}
