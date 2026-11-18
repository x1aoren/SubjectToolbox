package cn.stb.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.stb.core.designsystem.disabledSourceHint
import cn.stb.core.model.AppGraph
import cn.stb.core.model.CloudSnapshot
import cn.stb.core.model.ManagedToolIds
import cn.stb.core.model.ToolId
import cn.stb.core.registry.ToolHost
import cn.stb.feature.home.HomeScreen
import cn.stb.feature.settings.ChangelogScreen
import cn.stb.feature.settings.DisplayManagerScreen
import cn.stb.feature.settings.FeedbackScreen
import cn.stb.feature.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val FEEDBACK = "feedback"
    const val DISPLAY = "display"
    const val CHANGELOG = "changelog"
    const val TOOL = "tool/{toolId}"
    fun tool(id: String) = "tool/$id"
}

@Composable
fun AppNavHost(startToolId: String? = null) {
    val navController = rememberNavController()
    val cloud by AppGraph.cloud.snapshot.collectAsStateWithLifecycle()

    val host = remember(navController) {
        object : ToolHost {
            override fun openTool(id: ToolId) {
                navController.navigate(Routes.tool(id.value))
            }

            override fun close() {
                // 只从工具页返回。连点返回时，旧页面的点击可能晚于新页面出现，
                // 此时栈顶已经不是工具页，再返回会把主页也弹掉，界面就空白了。
                if (navController.currentDestination?.route == Routes.TOOL) {
                    navController.navigateUp()
                }
            }
        }
    }

    // 支持从外部（adb / 快捷方式）直达工具：am start --es toolId other.textbook
    LaunchedEffect(startToolId) {
        if (!startToolId.isNullOrBlank()) {
            navController.navigate(Routes.tool(startToolId))
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenTool = { navController.navigate(Routes.tool(it.value)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenFeedback = { navController.navigate(Routes.FEEDBACK) },
                onOpenDisplayManager = { navController.navigate(Routes.DISPLAY) },
            )
        }

        composable(Routes.FEEDBACK) { entry ->
            // 反馈入口纳入后台管控：停用时仍可进入该路由，但只展示停用页（不崩、可返回）
            if (cloud.isToolDisabled(ManagedToolIds.FEEDBACK)) {
                ToolDisabledScreen(
                    title = AppContainer.catalog.findEntry(ManagedToolIds.feedback)?.title ?: "意见反馈",
                    reason = cloud.disabledReasonFor(ManagedToolIds.FEEDBACK),
                    source = cloud.disabledSourceFor(ManagedToolIds.FEEDBACK),
                    onBack = { navController.popBackFrom(entry) },
                )
            } else {
                FeedbackScreen(onBack = { navController.popBackFrom(entry) })
            }
        }

        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                onBack = { navController.popBackFrom(entry) },
                onOpenDisplayManager = { navController.navigate(Routes.DISPLAY) },
                onOpenChangelog = { navController.navigate(Routes.CHANGELOG) },
            )
        }

        composable(Routes.DISPLAY) { entry ->
            DisplayManagerScreen(onBack = { navController.popBackFrom(entry) })
        }

        composable(Routes.CHANGELOG) { entry ->
            ChangelogScreen(onBack = { navController.popBackFrom(entry) })
        }

        composable(
            route = Routes.TOOL,
            arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
        ) { entry ->
            val id = ToolId(entry.arguments?.getString("toolId").orEmpty())
            val provider = AppContainer.catalog.providerOf(id)
            val cloudDisabled = cloud.isToolDisabled(id.value)

            LaunchedEffect(id, cloudDisabled) {
                if (provider != null && !cloudDisabled) {
                    AppGraph.usage.recordOpen(id.value)
                    AppGraph.cloud.recordEvent("tool_open", id.value, mapOf("source" to "home"))
                }
            }
            DisposableEffect(id, cloudDisabled) {
                val startedAt = SystemClock.elapsedRealtime()
                onDispose {
                    if (provider != null && !cloudDisabled) {
                        AppGraph.cloud.recordEvent(
                            "tool_close",
                            id.value,
                            mapOf("durationMs" to (SystemClock.elapsedRealtime() - startedAt)),
                        )
                    }
                }
            }

            if (provider == null) {
                // 工具已下线，或者旧返回栈指向了不存在的 id——别崩，给个能回去的页面
                MissingToolScreen(onBack = { navController.popBackFrom(entry) })
            } else if (cloudDisabled) {
                ToolDisabledScreen(
                    title = provider.spec.title,
                    // 直达路由的停用文案与主页弹窗同一套解析：单设备原因 > 全局原因 > 默认文案
                    reason = cloud.disabledReasonFor(id.value),
                    source = cloud.disabledSourceFor(id.value),
                    onBack = { navController.popBackFrom(entry) },
                )
            } else {
                provider.Content(host)
            }
        }
    }

    // 版本升级提示挂在 NavHost 之后：它只往独立的 Dialog 窗口画内容，
    // 不参与页面布局与导航栈；「查看完整更新日志」由这里接到 changelog 路由。
    UpdateNotice(onOpenChangelog = { navController.navigate(Routes.CHANGELOG) })
    RemoteUpdateNotice()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingToolScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("工具不可用") },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp)) {
            Text("这个工具不在当前版本里", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "可能是应用升级后该工具被移除。返回主页即可，你的置顶与隐藏设置都还在。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolDisabledScreen(
    title: String,
    reason: String,
    source: String? = null,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("工具暂不可用") },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                text = reason.ifBlank { CloudSnapshot.DEFAULT_DISABLED_TEXT },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 单设备停用要明确「只影响本设备」，全局停用说明来源；未知来源时不显示
            val hint = disabledSourceHint(source)
            if (hint != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "联网后会自动同步最新开关；也可以稍后再试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
/**
 * 返回上一页，但只接受当前栈顶页面的返回请求。
 *
 * 返回动画期间旧页面的按钮仍然可能被点到：快速连点返回时，第一次返回已经把
 * 页面弹出栈，晚到的第二次点击若继续 pop，会把主页也一起弹掉，只剩空白背景。
 * 这里用 NavBackStackEntry 实例判断页面是否仍在栈顶，旧页面的点击直接忽略。
 */
private fun NavController.popBackFrom(entry: NavBackStackEntry) {
    if (currentBackStackEntry === entry) {
        navigateUp()
    }
}
