package cn.stb.feature.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.stb.core.data.AppVersion
import cn.stb.core.model.ChangeItem
import cn.stb.core.model.ChangeKind
import cn.stb.core.model.Changelog
import cn.stb.core.model.ReleaseNote

/**
 * 更新日志页：设置 → 关于 → 更新日志。
 *
 * 内容全部来自 [Changelog]，按版本从新到旧排成时间线；当前运行版本打「当前版本」标记。
 * 纯本地数据，不联网、不读 assets——发版时只在 Changelog 里加一条即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 运行时版本号：与「关于」里显示的版本同源，避免两处对不上
    val currentVersion = remember(context) { AppVersion.name(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("更新日志") },
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
        // 横屏平板：内容限宽 860dp 居中，和设置页、显示管理页保持一致的版面
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = "每次升级后首次启动会提示本次更新内容；这里可以随时回看。" +
                            "当前版本 v${currentVersion}。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // releases 本身就是新 → 旧排列，直接当作时间线渲染
                items(Changelog.releases, key = { it.versionName }) { release ->
                    ReleaseCard(
                        release = release,
                        isCurrent = release.versionName == currentVersion,
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/** 一个版本的时间线卡片：版本号 + 日期 + 一句话概要 + 分类条目。 */
@Composable
private fun ReleaseCard(release: ReleaseNote, isCurrent: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        // 当前版本用主色描边强调，历史版本保持中性描边
        border = if (isCurrent) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${release.versionName}",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (isCurrent) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = release.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = release.headline,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                release.changes.forEach { change ->
                    ChangeRow(change = change)
                }
            }
        }
    }
}

/** 一条更新说明：左边分类小标签，右边正文。长文本自动换行。 */
@Composable
private fun ChangeRow(change: ChangeItem) {
    Row(verticalAlignment = Alignment.Top) {
        KindTag(kind = change.kind)
        Spacer(Modifier.width(10.dp))
        Text(
            text = change.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 分类标签：新增 / 优化 / 修复。
 *
 * 三个分类固定用 primary / secondary / tertiary 的容器色，深浅色模式都由主题色推导，
 * 不在这里写死颜色，换主题色时标签会自动跟着变。
 */
@Composable
private fun KindTag(kind: ChangeKind) {
    val background: Color
    val foreground: Color
    when (kind) {
        ChangeKind.NEW -> {
            background = MaterialTheme.colorScheme.primaryContainer
            foreground = MaterialTheme.colorScheme.onPrimaryContainer
        }
        ChangeKind.IMPROVE -> {
            background = MaterialTheme.colorScheme.secondaryContainer
            foreground = MaterialTheme.colorScheme.onSecondaryContainer
        }
        ChangeKind.FIX -> {
            background = MaterialTheme.colorScheme.tertiaryContainer
            foreground = MaterialTheme.colorScheme.onTertiaryContainer
        }
    }
    Surface(shape = RoundedCornerShape(6.dp), color = background, contentColor = foreground) {
        Text(
            text = kind.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}
