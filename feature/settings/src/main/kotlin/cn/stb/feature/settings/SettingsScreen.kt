package cn.stb.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.stb.core.data.AppVersion
import cn.stb.core.data.AssetReader
import cn.stb.core.data.manifest
import cn.stb.core.model.AppGraph
import cn.stb.core.model.DarkMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDisplayManager: () -> Unit,
    onOpenChangelog: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val manifest = remember(context) { runCatching { AssetReader(context).manifest() }.getOrNull() }
    val catalog = AppGraph.catalog
    val cloud by AppGraph.cloud.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showContact by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }

    fun triggerSync() {
        if (syncing) return
        syncing = true
        syncMessage = ""
        scope.launch {
            val ok = AppGraph.cloud.syncNow(force = true)
            syncMessage = when {
                ok -> "同步完成"
                !AppGraph.cloud.isOnline() -> "当前没有网络，联网后再试"
                else -> "暂时无法同步，请稍后再试"
            }
            syncing = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        // 横屏平板上内容铺满全宽会显得松散，限定最大宽度并居中
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            // 「关于」放最前：一进设置就能看到软件信息与作者。
            item {
                SettingsGroup(title = "关于") {
                    Text("学科工具箱", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "SubjectToolbox",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "把语文、数学、英语、化学等学科的工具收进一个 App。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    val faintLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    Row {
                        Text("作者：2025届2班 ", style = MaterialTheme.typography.bodyMedium)
                        // 下划线用 drawBehind 单独画，能比正文更淡；点击弹出联系方式
                        Text(
                            text = "胡城玮",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { showContact = true }
                                .drawBehind {
                                    drawLine(
                                        color = faintLine,
                                        start = Offset(0f, size.height * 0.94f),
                                        end = Offset(size.width, size.height * 0.94f),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Logo 设计：2025届2班 杨朔懿 李睿之",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    // 版本号运行时从 PackageManager 读取，和 build.gradle.kts 永远一致；
                    // 「更新日志」做成版本行右侧的小胶囊，不再单独占一整行。
                    VersionRow(
                        version = AppVersion.name(context),
                        onOpenChangelog = onOpenChangelog,
                    )
                    InfoRow("内容版本", manifest?.contentVersion?.toString() ?: "未读取到")
                    InfoRow("学科 / 工具", "${catalog.subjects.size} / ${catalog.allTools.size}")
                    InfoRow("设备标识", AppGraph.cloud.deviceSn)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(
                        text = "特别鸣谢：陈子仪老师 郑晓宇老师",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsGroup(title = "深色模式") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DarkMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.theme.darkMode == mode,
                                onClick = { viewModel.setDarkMode(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            DarkMode.FOLLOW_SYSTEM -> "跟随系统"
                                            DarkMode.LIGHT -> "浅色"
                                            DarkMode.DARK -> "深色"
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "字号") {
                    // 0.85×「较小」是本次新增档位：标准仍嫌大的用户也有了选择；四档同一行。
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            0.85f to "较小",
                            1f to "标准",
                            1.15f to "较大",
                            1.3f to "特大",
                        ).forEach { (scale, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.theme.fontScale - scale) < 0.01f,
                                onClick = { viewModel.setFontScale(scale) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 预览文字随所选字号实时缩放：主题已按 fontScale 缩放整套 Typography。
                    Text(
                        text = "预览：把语文、数学、英语、化学等学科的工具收进一个平板 App。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "古诗文正文会随字号一起放大，行距保持 1.8 倍。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsGroup(title = "主页") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("显示「最近使用」", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "只在本机记录，不上传",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.home.showRecents,
                            onCheckedChange = { viewModel.setShowRecents(it) },
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "云端同步") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (cloud.loaded) "云端修订 #${cloud.revision ?: "—"}" else "尚未同步云端配置",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = relativeSyncTime(cloud.lastSyncAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = ::triggerSync, enabled = !syncing) {
                            Text(if (syncing) "同步中…" else "立即同步")
                        }
                    }
                    if (syncMessage.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = syncMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // 只统计主工具：子工具（示意图子项）与功能入口不参与主页工具计数，
                    // 否则后台托管 3 个条目后这里会凭空多出 3 个「工具」。
                    val mainToolIds = catalog.allTools.map { it.id.value }.toSet()
                    val enabledTools = cloud.tools.count { (id, state) -> id in mainToolIds && state.enabled }
                    val disabledTools = cloud.tools.count { (id, state) -> id in mainToolIds && !state.enabled }
                    Text(
                        text = when {
                            cloud.tools.isEmpty() -> "还没有从云端取到工具开关。"
                            else -> "可用工具 $enabledTools 个，停用 $disabledTools 个。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (disabledTools > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "停用的工具在主页仍会显示（置灰），点击可查看停用原因。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "数据与隐私") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("匿名使用统计", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "记录打开过哪些工具、版本与会话时长",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.telemetryEnabled,
                            onCheckedChange = { viewModel.setTelemetryEnabled(it) },
                        )
                    }
                }
            }

            item {
                Surface(
                    onClick = onOpenDisplayManager,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("显示管理", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            val hiddenCount =
                                settings.visibility.hiddenSubjectIds.size + settings.visibility.hiddenToolIds.size
                            Text(
                                text = if (hiddenCount == 0) "所有学科与工具都在显示" else "已隐藏 $hiddenCount 项",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            }
        }
    }

    if (showContact) {
        AlertDialog(
            onDismissRequest = { showContact = false },
            title = { Text("联系方式") },
            text = {
                Column {
                    Text("QQ：2314764772", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("微信：ys_xiaoren", style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                TextButton(onClick = { showContact = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), content = content)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 「版本」行：右侧跟一个紧凑的「更新日志 ›」小胶囊。
 *
 * 之前是一整行带图标、副标题的大卡片，在设置页里过于抢眼；改成贴在版本号旁边的小按钮，
 * 位置更合理（用户想看更新内容时自然会在版本附近找），也不打断「关于」信息的阅读节奏。
 */
@Composable
private fun VersionRow(version: String, onOpenChangelog: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("版本", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            version,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onOpenChangelog,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.height(28.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("更新日志", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(2.dp))
                Text("›", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun relativeSyncTime(lastSyncAt: Long?): String {
    if (lastSyncAt == null || lastSyncAt <= 0L) return "还没有同步过"
    val diff = System.currentTimeMillis() - lastSyncAt
    return when {
        diff < 60_000L -> "刚刚同步"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前同步"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前同步"
        else -> "${diff / 86_400_000L} 天前同步"
    }
}