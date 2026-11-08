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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.stb.core.model.AppGraph
import cn.stb.core.model.ToolId
import cn.stb.core.registry.ToolHost
import cn.stb.feature.home.HomeScreen
import cn.stb.feature.settings.DisplayManagerScreen
import cn.stb.feature.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DISPLAY = "display"
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
                navController.popBackStack()
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

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDisplayManager = { navController.navigate(Routes.DISPLAY) },
            )
        }

        composable(Routes.DISPLAY) {
            DisplayManagerScreen(onBack = { navController.popBackStack() })
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
                MissingToolScreen(onBack = { navController.popBackStack() })
            } else {
                provider.Content(host)
            }
        }
    }
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
