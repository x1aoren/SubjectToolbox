package cn.stb.core.model

import kotlinx.coroutines.flow.StateFlow

/**
 * 云端下发的单个工具状态。
 *
 * [source] 为 `device` 表示这台设备被单独设置过（后台可以按 SN 单独启停），
 * 为 `global` 表示沿用云端全局开关。
 */
data class CloudToolState(
    val enabled: Boolean,
    val message: String = "",
    val reason: String? = null,
    val source: String = "global",
    val note: String? = null,
)

data class CloudAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val level: String,
    val pinned: Boolean,
)

data class CloudRelease(
    val latestVersion: String,
    val minSupportedVersion: String,
    val forceUpdate: Boolean,
    val updateNotes: String,
    val updateAvailable: Boolean,
    val updateRequired: Boolean,
)

data class CloudPolicy(
    val configIntervalSeconds: Long = 6 * 60 * 60,
    val telemetryFlushSeconds: Long = 10 * 60,
    val telemetryBatchMax: Int = 200,
    val nextConfigAtMillis: Long = 0L,
)

/**
 * 客户端看到的云端状态。
 *
 * [loaded] 表示缓存或网络至少拿到过一次配置；离线启动时会先把缓存里的开关恢复出来，
 * 保证「管理员停用的工具」不会因为断网又冒出来。
 */
data class CloudSnapshot(
    val loaded: Boolean = false,
    val revision: Int? = null,
    val deviceSn: String = "",
    val syncing: Boolean = false,
    val lastSyncAtMillis: Long? = null,
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "",
    val tools: Map<String, CloudToolState> = emptyMap(),
    val announcements: List<CloudAnnouncement> = emptyList(),
    val release: CloudRelease? = null,
    val policy: CloudPolicy = CloudPolicy(),
) {
    fun isToolDisabled(toolId: String): Boolean = tools[toolId]?.enabled == false

    fun messageFor(toolId: String): String = tools[toolId]?.message.orEmpty()
}

data class ToolDescriptor(
    val id: String,
    val title: String,
    val subjectId: String,
)

data class FeedbackTicket(
    val id: String,
    val number: String,
    val status: String,
    val createdAt: String,
)

data class FeedbackMessage(
    val authorType: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
)

data class FeedbackThread(
    val id: String,
    val number: String,
    val type: String,
    val title: String,
    val content: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val messages: List<FeedbackMessage>,
)

/**
 * 云端控制能力。
 *
 * 放在 core:model 只定义契约，Android 实现（HTTP、文件队列、SN 读取）在 core:data，
 * 由 Application 装配进 [AppGraph]，feature 模块只依赖接口。
 */
interface CloudControl {
    val snapshot: StateFlow<CloudSnapshot>
    val deviceSn: String

    fun recordEvent(name: String, toolId: String? = null, props: Map<String, Any?> = emptyMap())

    /**
     * 崩溃兜底：在进程即将退出时同步落盘，下一次启动再批量上报。
     * 不走协程，避免进程结束时任务还没来得及执行。
     */
    fun recordCrash(signature: String, message: String, stack: String)

    fun onForeground()

    fun onBackground()

    /** 手动/启动同步；内部会按服务端下发的策略节流。返回是否成功完成一次网络同步。 */
    suspend fun syncNow(force: Boolean = false): Boolean

    suspend fun submitFeedback(
        type: String,
        title: String,
        content: String,
        contact: String,
    ): Result<FeedbackTicket>

    /** 按本机设备标识查询反馈与公开回复；30 秒内复用缓存，避免频繁请求。 */
    suspend fun listFeedback(force: Boolean = false): Result<List<FeedbackThread>>

    fun isOnline(): Boolean

    fun announcementDismissed(id: String): Boolean

    fun dismissAnnouncement(id: String)
}
