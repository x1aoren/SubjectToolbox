package cn.stb.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.stb.core.data.AppVersion
import cn.stb.core.model.AppGraph
import cn.stb.core.model.ChangeKind
import cn.stb.core.model.Changelog
import cn.stb.core.model.ReleaseNote
import kotlinx.coroutines.launch

/**
 * 版本升级提示宿主。
 *
 * 放在 `AppNavHost` 的 `NavHost` 之后调用即可（它只往独立 Dialog 窗口里画内容，
 * 不参与导航与页面布局）。职责只有三件事：
 *
 * 1. 读一次 DataStore 里的 [cn.stb.core.model.AppSettings.lastSeenVersion]，
 *    等配置真正加载完成（`initialValue = null`）再判断，避免启动瞬间误判成「没看过」；
 * 2. 按规则决定要不要弹「已从vX更新至vY」：
 *    - 记录的版本与当前版本不同：弹窗，内容取 [Changelog.releasesNewerThan]；
 *    - 没有记录（首次安装、或从旧版本覆盖上来）：旧版本取 [Changelog.previousVersionName]，
 *      同样弹窗——宁可让用户看到更新内容，也不能因为拿不到旧版本号就静默；
 *    - 记录与当前版本相同：已经看过，不打扰；
 * 3. 用户关闭弹窗后把当前版本写回 DataStore，保证**每个版本只弹一次**。
 *
 * 状态用 `rememberSaveable` 保存：横竖屏切换、Activity 重建不会重复弹窗，
 * 也不会把已经弹出的提示弄丢。
 *
 * @param onOpenChangelog 点「查看完整更新日志」时调用（由导航层接到 changelog 路由）。
 */
@Composable
fun UpdateNotice(onOpenChangelog: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 运行时版本：标题里的「新版本」必须来自 PackageManager，不能写死
    val currentVersion = remember(context) { AppVersion.name(context) }

    // initialValue = null 用来区分「还没加载完」和「加载完了但是空配置」
    val settings by AppGraph.settings.settings.collectAsStateWithLifecycle(initialValue = null)

    // 判断只做一次；visible / oldVersion 一起保存，旋转屏幕后弹窗不会重复弹或消失
    var decided by rememberSaveable { mutableStateOf(false) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var oldVersion by rememberSaveable { mutableStateOf("") }

    fun markSeen() {
        scope.launch {
            AppGraph.settings.update { current -> current.copy(lastSeenVersion = currentVersion) }
        }
    }

    LaunchedEffect(settings) {
        val loaded = settings ?: return@LaunchedEffect
        if (decided) return@LaunchedEffect
        // 版本名读不到（几乎不会发生）时不做任何写入，下次启动再试
        if (currentVersion == AppVersion.UNKNOWN_NAME) return@LaunchedEffect
        decided = true

        val lastSeen = loaded.lastSeenVersion.trim()
        when {
            // 已看过当前版本：不再打扰
            lastSeen.isNotEmpty() && lastSeen == currentVersion -> Unit

            // 记录过旧版本：正常升级提示
            lastSeen.isNotEmpty() -> {
                oldVersion = lastSeen
                visible = true
            }

            // 没有记录：首次安装 / 从旧版本覆盖上来 / 数据被清过。
            // 旧版本号取最近一个历史发布版本，拿不到就按最老的 1.0.0 兜底——
            // 更新内容一定要让用户看到，不能因为查不到旧版本就静默跳过。
            else -> {
                oldVersion = Changelog.previousVersionName(currentVersion) ?: LEGACY_VERSION
                visible = true
            }
        }
    }

    if (visible) {
        UpdateNoticeDialog(
            oldVersion = oldVersion,
            currentVersion = currentVersion,
            releases = Changelog.releasesNewerThan(oldVersion),
            onConfirm = {
                visible = false
                markSeen()
            },
            onDismiss = {
                visible = false
                markSeen()
            },
            onOpenChangelog = {
                // 先关弹窗、记版本，再跳转：即使导航失败也不会下次再弹一次
                visible = false
                markSeen()
                onOpenChangelog()
            },
        )
    }
}

/** 没有 lastSeenVersion 记录的旧用户，按 1.0.0 起算更新内容。 */
private const val LEGACY_VERSION = "1.0.0"

/**
 * 「已从vX更新至vY」弹窗。
 *
 * 横屏平板限宽 520~560dp（这里取 540dp，窄窗口/分屏时自动收窄到可用宽度），
 * 内容超过屏幕高度 55% 时内部滚动，按钮始终可见。
 */
@Composable
private fun UpdateNoticeDialog(
    oldVersion: String,
    currentVersion: String,
    releases: List<ReleaseNote>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenChangelog: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    // 横屏平板 540dp；分屏等窄窗口下按「屏宽 - 48dp」收窄，避免溢出
    val dialogWidth = (configuration.screenWidthDp - 48).coerceIn(300, 540).dp
    val maxBodyHeight = (configuration.screenHeightDp * 0.55f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(dialogWidth),
        shape = RoundedCornerShape(18.dp),
        title = {
            // 标题格式有严格要求：无空格，v 前缀，例：已从v1.0.0更新至v1.1.0
            Text(text = "已从v${oldVersion}更新至v${currentVersion}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (releases.isEmpty()) {
                    Text(
                        text = "这个版本没有需要特别说明的变更，可以到「更新日志」查看完整记录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    releases.forEach { release ->
                        // 跨版本升级时先标出每个版本的标题，避免条目混在一起
                        if (releases.size > 1) {
                            Text(
                                text = "v${release.versionName} · ${release.date}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = release.headline,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        release.changes.forEach { change ->
                            Row(verticalAlignment = Alignment.Top) {
                                NoticeKindTag(kind = change.kind)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = change.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("知道了") }
        },
        dismissButton = {
            TextButton(onClick = onOpenChangelog) { Text("查看完整更新日志") }
        },
    )
}

/** 分类小标签，配色与 ChangelogScreen 保持一致（primary / secondary / tertiary 容器色）。 */
@Composable
private fun NoticeKindTag(kind: ChangeKind) {
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
