package cn.stb.core.data.cloud

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.os.SystemClock
import cn.stb.core.model.AppSettings
import cn.stb.core.model.CloudAnnouncement
import cn.stb.core.model.CloudControl
import cn.stb.core.model.CloudPolicy
import cn.stb.core.model.CloudRelease
import cn.stb.core.model.CloudSnapshot
import cn.stb.core.model.CloudToolState
import cn.stb.core.model.FeedbackMessage
import cn.stb.core.model.FeedbackThread
import cn.stb.core.model.FeedbackTicket
import cn.stb.core.model.SettingsRepository
import cn.stb.core.model.ToolDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import kotlin.math.min

/**
 * 云端能力的 Android 实现。
 *
 * 设计原则（按学校场景的约束）：
 * - 没有网络时不发起请求，事件先落本地队列；
 * - 配置最多 6 小时同步一次，遥测最多 10 分钟上报一次，失败指数退避；
 * - 队列最多 800 条 / 512 KB，超出丢最旧，绝不会把平板磁盘写满；
 * - 配置缓存到 SharedPreferences，断网启动也能沿用上一次的设备级开关；
 * - 所有网络请求都在 IO 线程，超时 8 秒，不阻塞界面。
 */
class CloudControlImpl(
    context: Context,
    private val settings: SettingsRepository,
    baseUrl: String,
    apiKey: String,
    private val channel: String,
    private val toolsProvider: () -> List<ToolDescriptor>,
) : CloudControl {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences("stb_cloud", Context.MODE_PRIVATE)
    private val queue = TelemetryQueue(appContext)
    private val identity = DeviceIdentity(appContext)
    private val packageInfo: PackageInfo =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)
    private val appVersion: String = packageInfo.versionName ?: "0"
    private val versionCode: Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    private val api = CloudApi(
        baseUrl = baseUrl,
        apiKey = apiKey,
        userAgent = "SubjectToolbox/$appVersion (Android ${Build.VERSION.RELEASE})",
    )

    override val deviceSn: String = identity.sn

    private val _snapshot = MutableStateFlow(
        CloudSnapshot(deviceSn = deviceSn, policy = CloudPolicy()),
    )
    override val snapshot: StateFlow<CloudSnapshot> = _snapshot.asStateFlow()

    private val mutex = Mutex()
    @Volatile
    private var settingsState = AppSettings()

    private var started = false
    private var sessionId: String? = null
    private var sessionStartedElapsed = 0L
    private var lastAttemptAt = 0L

    private var lastConfigAt: Long = prefs.getLong(KEY_LAST_CONFIG, 0L)
    private var lastFlushAt: Long = prefs.getLong(KEY_LAST_FLUSH, 0L)
    private var nextConfigAt: Long = prefs.getLong(KEY_NEXT_CONFIG, 0L)
    private var feedbackFetchedAt = 0L
    private var feedbackCache: List<FeedbackThread> = emptyList()

    private val dismissedAnnouncements: MutableSet<String> =
        prefs.getStringSet(KEY_DISMISSED, emptySet())?.toMutableSet() ?: mutableSetOf()

    /** 由 Application 调用一次：恢复缓存、观察隐私开关、触发一次节流同步。 */
    fun start() {
        if (started) return
        started = true
        restoreCache()
        scope.launch {
            settings.settings.collectLatest { current ->
                val wasEnabled = settingsState.telemetryEnabled
                settingsState = current
                if (wasEnabled && !current.telemetryEnabled) {
                    queue.clear()
                }
            }
        }
        scope.launch {
            // 让首帧先渲染，避免启动瞬间抢 IO / 网络
            delay(1_500)
            if (NetworkState.isOnline(appContext)) syncNow(force = false)
        }
    }

    override fun onForeground() {
        if (sessionId == null) {
            val id = UUID.randomUUID().toString().take(8)
            sessionId = id
            sessionStartedElapsed = SystemClock.elapsedRealtime()
            scope.launch { appendEvent("session_start", null, mapOf("sessionId" to id)) }
        }
        scope.launch { syncNow(force = false) }
    }

    override fun onBackground() {
        val id = sessionId ?: return
        val duration = (SystemClock.elapsedRealtime() - sessionStartedElapsed).coerceAtLeast(0L)
        sessionId = null
        scope.launch {
            appendEvent(
                "session_end",
                null,
                mapOf("sessionId" to id, "durationMs" to duration),
            )
            if (NetworkState.isOnline(appContext)) flushTelemetry(force = false)
        }
    }

    override fun recordEvent(name: String, toolId: String?, props: Map<String, Any?>) {
        if (!settingsState.telemetryEnabled) return
        scope.launch { appendEvent(name, toolId, props) }
    }

    override fun recordCrash(signature: String, message: String, stack: String) {
        if (!settingsState.telemetryEnabled) return
        val event = JSONObject().apply {
            put("name", "error")
            put("occurredAt", Instant.now().toString())
            put(
                "props",
                JSONObject().apply {
                    put("signature", signature)
                    put("message", message.take(500))
                    put("stack", stack.take(8_000))
                },
            )
        }
        // 崩溃线程上同步写盘：只追加一行，几百毫秒内结束；离线也能留下记录。
        runCatching { queue.append(event.toString()) }
    }

    private suspend fun appendEvent(name: String, toolId: String?, props: Map<String, Any?>) {
        if (!settingsState.telemetryEnabled) return
        val event = JSONObject().apply {
            put("name", name)
            put("occurredAt", Instant.now().toString())
            if (!toolId.isNullOrBlank()) put("toolId", toolId)
            if (props.isNotEmpty()) put("props", JSONObject(props))
        }
        queue.append(event.toString())
    }

    override suspend fun syncNow(force: Boolean): Boolean = withContext(Dispatchers.IO) {
        syncNowOnIo(force)
    }

    private suspend fun syncNowOnIo(force: Boolean): Boolean = mutex.withLock {
        if (!NetworkState.isOnline(appContext)) return false
        val now = System.currentTimeMillis()
        if (force && now - lastAttemptAt < MIN_FORCE_INTERVAL_MS) return false

        val policy = _snapshot.value.policy
        val effectiveNextConfig = maxOf(nextConfigAt, policy.nextConfigAtMillis)
        val configDue = force || now >= effectiveNextConfig ||
            now - lastConfigAt >= policy.configIntervalSeconds * 1000
        val telemetryDue = settingsState.telemetryEnabled &&
            queue.size() > 0 &&
            (force || now - lastFlushAt >= policy.telemetryFlushSeconds * 1000)
        if (!configDue && !telemetryDue) return false

        lastAttemptAt = now
        _snapshot.update { it.copy(syncing = true) }
        var success = true

        if (configDue) {
            val configSuccess = runConfigSync()
            if (configSuccess) {
                lastConfigAt = now
                prefs.edit().putLong(KEY_LAST_CONFIG, now).putInt(KEY_CONFIG_FAILURES, 0).apply()
            } else {
                scheduleConfigBackoff(now)
                success = false
            }
        }

        if (settingsState.telemetryEnabled) {
            if (!flushTelemetry(force)) success = false
        } else {
            queue.clear()
        }

        _snapshot.update {
            it.copy(
                syncing = false,
                lastSyncAtMillis = if (success) now else it.lastSyncAtMillis,
            )
        }
        success
    }

    private suspend fun runConfigSync(): Boolean {
        val body = JSONObject().apply {
            put("sn", deviceSn)
            put("model", Build.MODEL.orEmpty())
            put("manufacturer", Build.MANUFACTURER.orEmpty())
            put("osVersion", "Android ${Build.VERSION.RELEASE}")
            put("sdkInt", Build.VERSION.SDK_INT)
            put("appVersion", appVersion)
            put("versionCode", versionCode)
            put("channel", channel)
            put("tools", JSONArray().apply {
                toolsProvider().forEach { tool ->
                    put(
                        JSONObject()
                            .put("id", tool.id)
                            .put("title", tool.title)
                            .put("subjectId", tool.subjectId),
                    )
                }
            })
        }

        val response = api.post("/v1/telemetry/handshake", body)
        if (!response.ok) return false
        return runCatching {
            val root = JSONObject(response.body)
            val now = System.currentTimeMillis()
            val parsed = parseSnapshot(root, now)
            _snapshot.value = parsed.copy(
                deviceSn = deviceSn,
                loaded = true,
                syncing = false,
                lastSyncAtMillis = now,
            )
            nextConfigAt = parsed.policy.nextConfigAtMillis
            prefs.edit()
                .putString(KEY_CACHE, root.toString())
                .putLong(KEY_LAST_CONFIG, now)
                .putLong(KEY_NEXT_CONFIG, nextConfigAt)
                .putInt(KEY_CONFIG_FAILURES, 0)
                .apply()
            true
        }.getOrDefault(false)
    }

    private suspend fun flushTelemetry(force: Boolean): Boolean {
        if (!settingsState.telemetryEnabled) {
            queue.clear()
            return true
        }
        if (!NetworkState.isOnline(appContext)) return false
        val now = System.currentTimeMillis()
        val policy = _snapshot.value.policy
        if (!force && now - lastFlushAt < policy.telemetryFlushSeconds * 1000) return true

        val batch = queue.peek(policy.telemetryBatchMax)
        if (batch.isEmpty()) return true

        val events = JSONArray().apply {
            batch.forEach { line ->
                runCatching { JSONObject(line) }.getOrNull()?.let { put(it) }
            }
        }
        val response = api.post(
            "/v1/telemetry/events",
            JSONObject().put("sn", deviceSn).put("events", events),
        )
        return if (response.ok) {
            queue.removeFirst(batch.size)
            lastFlushAt = now
            prefs.edit().putLong(KEY_LAST_FLUSH, now).putInt(KEY_TELEMETRY_FAILURES, 0).apply()
            true
        } else {
            val failures = prefs.getInt(KEY_TELEMETRY_FAILURES, 0) + 1
            lastFlushAt = now + backoffMillis(failures)
            prefs.edit()
                .putLong(KEY_LAST_FLUSH, lastFlushAt)
                .putInt(KEY_TELEMETRY_FAILURES, failures)
                .apply()
            false
        }
    }

    private fun scheduleConfigBackoff(now: Long) {
        val failures = min(6, prefs.getInt(KEY_CONFIG_FAILURES, 0) + 1)
        nextConfigAt = now + backoffMillis(failures)
        prefs.edit()
            .putLong(KEY_NEXT_CONFIG, nextConfigAt)
            .putInt(KEY_CONFIG_FAILURES, failures)
            .apply()
        _snapshot.update {
            it.copy(policy = it.policy.copy(nextConfigAtMillis = nextConfigAt))
        }
    }

    /** 5 分钟起步，最多 1 小时。 */
    private fun backoffMillis(failures: Int): Long {
        val factor = 1L shl (failures - 1).coerceIn(0, 4)
        return min(60 * 60_000L, 5 * 60_000L * factor)
    }

    override suspend fun submitFeedback(
        type: String,
        title: String,
        content: String,
        contact: String,
    ): Result<FeedbackTicket> = withContext(Dispatchers.IO) {
        submitFeedbackOnIo(type, title, content, contact)
    }

    private suspend fun submitFeedbackOnIo(
        type: String,
        title: String,
        content: String,
        contact: String,
    ): Result<FeedbackTicket> {
        if (!NetworkState.isOnline(appContext)) {
            return Result.failure(IllegalStateException("当前没有网络，连接网络后再提交"))
        }
        val body = JSONObject().apply {
            put("sn", deviceSn)
            put("type", type)
            put("title", title)
            put("content", content)
            if (contact.isNotBlank()) put("contact", contact)
        }
        val response = api.post("/v1/feedback", body)
        if (!response.ok) {
            val message = runCatching {
                JSONObject(response.body).optJSONObject("error")?.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            return Result.failure(
                IllegalStateException(
                    message ?: if (response.status == 0) "网络异常，请稍后再试" else "提交失败（${response.status}）",
                ),
            )
        }
        feedbackFetchedAt = 0L
        feedbackCache = emptyList()
        return runCatching {
            val feedback = JSONObject(response.body).optJSONObject("feedback")
            if (feedback == null) error("响应缺少反馈编号")
            FeedbackTicket(
                id = feedback.optString("id"),
                number = feedback.optString("number"),
                status = feedback.optString("status"),
                createdAt = feedback.optString("createdAt"),
            )
        }
    }


    override suspend fun listFeedback(force: Boolean): Result<List<FeedbackThread>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - feedbackFetchedAt in 0L..30_000L && feedbackCache.isNotEmpty()) {
            return@withContext Result.success(feedbackCache)
        }
        if (!NetworkState.isOnline(appContext)) {
            return@withContext Result.failure(IllegalStateException("当前没有网络，连接网络后再查看回复"))
        }
        val sn = URLEncoder.encode(deviceSn, "UTF-8")
        val response = api.get("/v1/feedback?sn=$sn")
        if (!response.ok) {
            val message = runCatching {
                JSONObject(response.body).optJSONObject("error")?.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            return@withContext Result.failure(
                IllegalStateException(
                    message ?: if (response.status == 0) "网络异常，请稍后再试" else "获取失败（${response.status}）",
                ),
            )
        }
        runCatching {
            val array = JSONObject(response.body).optJSONArray("feedback") ?: JSONArray()
            val threads = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val messages = mutableListOf<FeedbackMessage>()
                    val rawMessages = item.optJSONArray("messages")
                    if (rawMessages != null) {
                        for (messageIndex in 0 until rawMessages.length()) {
                            val message = rawMessages.optJSONObject(messageIndex) ?: continue
                            messages += FeedbackMessage(
                                authorType = message.optString("authorType", "admin"),
                                authorName = message.optString("authorName"),
                                body = message.optString("body"),
                                createdAt = message.optString("createdAt"),
                            )
                        }
                    }
                    add(
                        FeedbackThread(
                            id = item.optString("id"),
                            number = item.optString("number"),
                            type = item.optString("type", "other"),
                            title = item.optString("title"),
                            content = item.optString("content"),
                            status = item.optString("status", "pending"),
                            createdAt = item.optString("createdAt"),
                            updatedAt = item.optString("updatedAt"),
                            messages = messages,
                        ),
                    )
                }
            }
            feedbackFetchedAt = System.currentTimeMillis()
            feedbackCache = threads
            threads
        }
    }
    override fun isOnline(): Boolean = NetworkState.isOnline(appContext)

    override fun announcementDismissed(id: String): Boolean = id in dismissedAnnouncements

    override fun dismissAnnouncement(id: String) {
        dismissedAnnouncements += id
        prefs.edit().putStringSet(KEY_DISMISSED, dismissedAnnouncements).apply()
    }

    private fun restoreCache() {
        val cached = prefs.getString(KEY_CACHE, null)
        val cachedSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }
        val cachedNext = prefs.getLong(KEY_NEXT_CONFIG, 0L)
        if (cached.isNullOrBlank()) {
            _snapshot.value = CloudSnapshot(
                deviceSn = deviceSn,
                policy = CloudPolicy(nextConfigAtMillis = cachedNext),
            )
            return
        }
        runCatching {
            parseSnapshot(JSONObject(cached), cachedSyncAt ?: System.currentTimeMillis())
                .copy(deviceSn = deviceSn, loaded = true)
        }.onSuccess { _snapshot.value = it }
    }

    private fun parseSnapshot(root: JSONObject, syncedAt: Long): CloudSnapshot {
        val config = root.optJSONObject("config") ?: root
        val tools = mutableMapOf<String, CloudToolState>()
        config.optJSONObject("tools")?.let { toolObject ->
            for (key in toolObject.keys()) {
                val item = toolObject.optJSONObject(key) ?: continue
                tools[key] = CloudToolState(
                    enabled = item.optBoolean("enabled", true),
                    reason = item.nullableString("reason"),
                    source = item.optString("source", "global"),
                    note = item.nullableString("note"),
                    // 停用原因：后台下发的 disabledReason；旧缓存/旧服务端里的 message 字段直接忽略（1.1.0 未上线，不保留兼容）
                    disabledReason = item.nullableString("disabledReason").orEmpty(),
                    disabledReasonSource = item.nullableString("disabledReasonSource"),
                )
            }
        }

        val announcements = mutableListOf<CloudAnnouncement>()
        config.optJSONArray("announcements")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                announcements += CloudAnnouncement(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    body = item.optString("body"),
                    level = item.optString("level", "info"),
                    pinned = item.optBoolean("pinned", false),
                )
            }
        }

        val releaseObject = config.optJSONObject("release")
        val release = releaseObject?.let {
            CloudRelease(
                latestVersion = it.optString("latestVersion"),
                minSupportedVersion = it.optString("minSupportedVersion"),
                forceUpdate = it.optBoolean("forceUpdate", false),
                updateNotes = it.optString("updateNotes"),
                updateAvailable = it.optBoolean("updateAvailable", false),
                updateRequired = it.optBoolean("updateRequired", false),
            )
        }

        val sync = config.optJSONObject("sync")
        val configInterval = sync?.optLong("configIntervalSeconds", 6 * 60 * 60) ?: (6 * 60 * 60)
        val flushInterval = sync?.optLong("telemetryFlushSeconds", 10 * 60) ?: (10 * 60)
        val batchMax = sync?.optInt("telemetryBatchMax", 200) ?: 200
        val nextAt = sync?.optString("nextConfigAt")?.let(::parseInstant) ?: (syncedAt + configInterval * 1000)
        val policy = CloudPolicy(
            configIntervalSeconds = configInterval,
            telemetryFlushSeconds = flushInterval,
            telemetryBatchMax = batchMax.coerceIn(20, 300),
            nextConfigAtMillis = maxOf(nextAt, prefs.getLong(KEY_NEXT_CONFIG, 0L)),
        )

        val maintenance = config.optJSONObject("maintenance")
        val revision = root.optInt("revision", -1).takeIf { it >= 0 }

        return CloudSnapshot(
            loaded = true,
            revision = revision,
            deviceSn = deviceSn,
            lastSyncAtMillis = syncedAt,
            maintenance = maintenance?.optBoolean("enabled", false) ?: false,
            maintenanceMessage = maintenance?.optString("message").orEmpty(),
            tools = tools,
            announcements = announcements,
            release = release,
            policy = policy,
        )
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun parseInstant(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private companion object {
        const val KEY_CACHE = "config_cache"
        const val KEY_LAST_CONFIG = "last_config_at"
        const val KEY_LAST_FLUSH = "last_flush_at"
        const val KEY_NEXT_CONFIG = "next_config_at"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_CONFIG_FAILURES = "config_failures"
        const val KEY_TELEMETRY_FAILURES = "telemetry_failures"
        const val KEY_DISMISSED = "dismissed_announcements"
        const val MIN_FORCE_INTERVAL_MS = 30_000L
    }
}
