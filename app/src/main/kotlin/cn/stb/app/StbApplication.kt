package cn.stb.app

import android.app.Application
import cn.stb.core.data.SettingsRepositoryImpl
import cn.stb.core.data.UsageRepositoryImpl
import cn.stb.core.data.cloud.CloudControlImpl
import cn.stb.core.model.AppGraph
import cn.stb.core.model.ToolDescriptor
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
            managedEntries = ToolRegistrations.managedEntries,
        )

        val cloud = CloudControlImpl(
            context = this,
            settings = settings,
            baseUrl = BuildConfig.STB_API_BASE,
            apiKey = BuildConfig.STB_API_KEY,
            channel = if (BuildConfig.DEBUG) "beta" else "stable",
            toolsProvider = {
                // 握手要上报「主工具 + 受管控条目」全量清单，后台工具清单同步才覆盖 100%
                catalog.everyEntry.map { tool ->
                    ToolDescriptor(tool.id.value, tool.title, tool.subjectId.value)
                }
            },
        )

        AppContainer.catalog = catalog
        AppGraph.install(catalog = catalog, settings = settings, usage = usage, cloud = cloud)
        installCrashHandler()
        cloud.start()

        seedDefaultPins()
    }

    /**
     * 只记录事件，不改变系统原有的崩溃处理：写一行到本地队列，下一次启动上报。
     * 没有网络时也能留下记录；队列有 800 条 / 512 KB 上限。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val frame = throwable.stackTrace.firstOrNull()
                val signature = buildString {
                    append(throwable.javaClass.name)
                    if (frame != null) append("@${frame.fileName ?: "?"}:${frame.lineNumber}")
                }
                AppGraph.cloud.recordCrash(
                    signature = signature,
                    message = throwable.message.orEmpty(),
                    stack = throwable.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
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
