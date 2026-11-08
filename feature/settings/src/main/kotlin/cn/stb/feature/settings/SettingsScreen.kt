package cn.stb.feature.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.stb.core.data.AssetReader
import cn.stb.core.data.manifest
import cn.stb.core.model.AppGraph
import cn.stb.core.model.DarkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDisplayManager: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val manifest = remember(context) { runCatching { AssetReader(context).manifest() }.getOrNull() }
    val catalog = AppGraph.catalog

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
                    Text("作者：2025届2班 胡城玮", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    InfoRow("版本", "1.0.0")
                    InfoRow("内容版本", manifest?.contentVersion?.toString() ?: "未读取到")
                    InfoRow("学科 / 工具", "${catalog.subjects.size} / ${catalog.allTools.size}")
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(
                        text = "特别鸣谢：陈子怡老师 郑晓宇老师",
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(1f to "标准", 1.15f to "较大", 1.3f to "特大").forEach { (scale, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.theme.fontScale - scale) < 0.01f,
                                onClick = { viewModel.setFontScale(scale) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
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
