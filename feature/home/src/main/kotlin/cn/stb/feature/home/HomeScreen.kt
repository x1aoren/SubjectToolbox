package cn.stb.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.stb.core.designsystem.EmptyState
import cn.stb.core.designsystem.SearchField
import cn.stb.core.designsystem.SectionHeader
import cn.stb.core.designsystem.ToolCard
import cn.stb.core.model.AppGraph
import cn.stb.core.model.HomeContent
import cn.stb.core.model.HomeSection
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec

private const val SECTION_COLUMNS = 3

@Composable
fun HomeScreen(
    onOpenTool: (ToolId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDisplayManager: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val contentState by viewModel.content.collectAsStateWithLifecycle()
    val content = contentState ?: return
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(content, query) { content.filtered(query) }
    val catalog = AppGraph.catalog

    // 置顶/最近卡片与学科栏同宽：三栏布局下每栏 = (窗口宽 - 左右内边距 - 两个间隔)/3
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = ((windowWidth - 56.dp - 36.dp) / 3).coerceAtLeast(240.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "学科工具箱",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${catalog.subjects.size} 个学科 · ${content.totalVisible} 个工具",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        onOpenTool = onOpenTool,
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
                            onOpenTool = onOpenTool,
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
                        onOpenTool = onOpenTool,
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
}

@Composable
private fun SubjectBlock(
    section: HomeSection,
    viewModel: HomeViewModel,
    onOpenTool: (ToolId) -> Unit,
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
                onOpenTool = onOpenTool,
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
    onOpenTool: (ToolId) -> Unit,
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
        onClick = { onOpenTool(tool.id) },
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
