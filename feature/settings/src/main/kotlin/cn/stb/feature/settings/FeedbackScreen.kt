package cn.stb.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.stb.core.model.AppGraph
import cn.stb.core.model.FeedbackThread
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TYPE_LABELS = mapOf(
    "bug" to "问题反馈",
    "feature" to "功能建议",
    "content" to "内容纠错",
    "other" to "其他",
)

private fun statusLabel(status: String): String = when (status) {
    "pending" -> "待处理"
    "in_progress" -> "处理中"
    "resolved" -> "已解决"
    "closed" -> "已关闭"
    else -> status
}

private fun shortTime(value: String): String =
    runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }.getOrDefault(value.take(16).replace('T', ' '))

/**
 * 独立意见反馈页：提交入口从设置里移出，放到主页右上角。
 *
 * 回复按本机设备标识归集，不需要登录；打开页面时查询一次，手动刷新有 30 秒缓存，
 * 不轮询、不在后台请求。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf("other") }
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var formMessage by remember { mutableStateOf("") }
    var threads by remember { mutableStateOf<List<FeedbackThread>>(emptyList()) }
    var loadingThreads by remember { mutableStateOf(false) }
    var threadMessage by remember { mutableStateOf("") }

    suspend fun loadThreads(force: Boolean = false) {
        if (loadingThreads) return
        loadingThreads = true
        threadMessage = ""
        AppGraph.cloud.listFeedback(force = force)
            .onSuccess { threads = it }
            .onFailure { threadMessage = it.message ?: "暂时无法获取回复" }
        loadingThreads = false
    }

    LaunchedEffect(Unit) { loadThreads() }

    fun submit() {
        if (submitting) return
        if (title.trim().length < 4 || content.trim().length < 4) {
            formMessage = "标题和描述至少各 4 个字"
            return
        }
        if (!AppGraph.cloud.isOnline()) {
            formMessage = "当前没有网络，联网后再提交"
            return
        }
        formMessage = ""
        submitting = true
        scope.launch {
            AppGraph.cloud.submitFeedback(
                type = type,
                title = title.trim(),
                content = content.trim(),
                contact = contact.trim(),
            ).onSuccess { ticket ->
                formMessage = "已提交，编号 ${ticket.number}；回复会出现在下方。"
                title = ""
                content = ""
                contact = ""
                loadThreads(true)
            }.onFailure { error ->
                formMessage = error.message ?: "提交失败，请稍后再试"
            }
            submitting = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("意见反馈") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { loadThreads(true) } }, enabled = !loadingThreads) {
                        Text(if (loadingThreads) "刷新中…" else "刷新回复")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "反馈按本机设备标识归集，不需要登录。",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "设备标识：${AppGraph.cloud.deviceSn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "遇到问题、发现内容错误，或者想加一个工具，都可以直接告诉我们；回复会显示在页面下方。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    FeedbackGroup(title = "写一条反馈") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            TYPE_LABELS.forEach { (value, label) ->
                                FilterChip(
                                    selected = type == value,
                                    onClick = { type = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(80) },
                            label = { Text("标题") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it.take(2000) },
                            label = { Text("详细描述") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contact,
                            onValueChange = { contact = it.take(80) },
                            label = { Text("联系方式（选填）") },
                            placeholder = { Text("QQ / 邮箱，方便回复你") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = ::submit,
                                enabled = !submitting && title.isNotBlank() && content.isNotBlank(),
                            ) {
                                Text(if (submitting) "提交中…" else "提交反馈")
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "同一设备每天最多 5 条，提交时才联网。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (formMessage.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                formMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "我的反馈",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (threads.isEmpty()) "暂无记录" else "${threads.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                if (loadingThreads && threads.isEmpty()) {
                    item {
                        FeedbackGroup {
                            Text(
                                "正在获取回复…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (threads.isEmpty()) {
                    item {
                        FeedbackGroup {
                            Text(
                                threadMessage.ifBlank { "还没有提交过反馈；提交后可以在这里看到处理进度和回复。" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (threadMessage.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                } else {
                    items(threads, key = { it.id }) { thread ->
                        FeedbackThreadCard(thread)
                    }
                }

                if (threadMessage.isNotBlank() && threads.isNotEmpty()) {
                    item {
                        Text(
                            threadMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun FeedbackThreadCard(thread: FeedbackThread) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${thread.number}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(thread.status)
                Spacer(Modifier.weight(1f))
                Text(
                    shortTime(thread.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                thread.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${TYPE_LABELS[thread.type] ?: thread.type} · ${thread.content}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (thread.messages.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                thread.messages.forEach { message ->
                    val fromAdmin = message.authorType != "user"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (fromAdmin) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row {
                                Text(
                                    message.authorName.ifBlank { if (fromAdmin) "客服" else "我" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    shortTime(message.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                message.body,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "还没有回复，处理后会显示在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "resolved" -> MaterialTheme.colorScheme.primary
        "closed" -> MaterialTheme.colorScheme.outline
        "in_progress" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            statusLabel(status),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
