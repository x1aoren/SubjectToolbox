package cn.stb.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.stb.core.designsystem.SubjectToolboxTheme
import cn.stb.core.model.AppGraph
import cn.stb.core.model.AppSettings
import cn.stb.core.model.DarkMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by AppGraph.settings.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val dark = when (settings.theme.darkMode) {
                DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }

            // 系统状态栏的图标明暗跟着应用内的深浅色走，否则强制深色时状态栏会看不见
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            // 换主题只重建 ColorScheme，不重建导航栈，所以切换时不会闪一下
            SubjectToolboxTheme(settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(startToolId = intent?.getStringExtra("toolId"))
                }
            }
        }
    }
}
