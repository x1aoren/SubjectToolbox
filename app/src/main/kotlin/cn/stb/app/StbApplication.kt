package cn.stb.app

import android.app.Application
import cn.stb.core.data.SettingsRepositoryImpl
import cn.stb.core.data.UsageRepositoryImpl
import cn.stb.core.model.AppGraph
import cn.stb.core.registry.ToolCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StbApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val settings = SettingsRepositoryImpl(this)
        val usage = UsageRepositoryImpl(this)
        val catalog = ToolCatalog(
            providers = ToolRegistrations.tools,
            subjectList = ToolRegistrations.subjects,
        )

        AppContainer.catalog = catalog
        AppGraph.install(catalog = catalog, settings = settings, usage = usage)

        seedDefaultPins()
    }

    /**
     * 首次启动把标了 defaultPinned 的工具放进「我的常用」，避免新用户打开就是一片空白。
     * 只播种一次：用户清空置顶后，不会在下次启动被重新填满。
     */
    private fun seedDefaultPins() {
        scope.launch {
            AppGraph.settings.update { current ->
                when {
                    current.seeded -> current
                    // 用户已经手动设过置顶，就别拿默认值盖掉他
                    current.home.pinnedToolIds.isNotEmpty() -> current.copy(seeded = true)
                    else -> current.copy(
                        seeded = true,
                        home = current.home.copy(
                            pinnedToolIds = AppGraph.catalog.allTools
                                .filter { it.defaultPinned }
                                .map { it.id.value },
                        ),
                    )
                }
            }
        }
    }
}
