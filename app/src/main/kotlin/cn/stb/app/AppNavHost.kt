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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import cn.stb.core.model.AppGraph
import cn.stb.core.model.ToolId
import cn.stb.core.registry.ToolHost
import cn.stb.feature.home.HomeScreen
import cn.stb.feature.settings.ChangelogScreen
import cn.stb.feature.settings.DisplayManagerScreen
import cn.stb.feature.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DISPLAY = "display"
    const val CHANGELOG = "changelog"
    const val TOOL = "tool/{toolId}"
    fun tool(id: String) = "tool/$id"
}

@Composable
fun AppNavHost(startToolId: String? = null) {
    val navController = rememberNavController()

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
                onOpenDisplayManager = { navController.navigate(Routes.DISPLAY) },
            )
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

            LaunchedEffect(id) {
                if (provider != null) AppGraph.usage.recordOpen(id.value)
            }

            if (provider == null) {
                // 工具已下线，或者旧返回栈指向了不存在的 id——别崩，给个能回去的页面
                MissingToolScreen(onBack = { navController.popBackFrom(entry) })
            } else {
                provider.Content(host)
            }
        }
    }

    // 版本升级提示挂在 NavHost 之后：它只往独立的 Dialog 窗口画内容，
    // 不参与页面布局与导航栈；「查看完整更新日志」由这里接到 changelog 路由。
    UpdateNotice(onOpenChangelog = { navController.navigate(Routes.CHANGELOG) })
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
