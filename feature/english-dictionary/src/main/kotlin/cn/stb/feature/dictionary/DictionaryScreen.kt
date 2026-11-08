package cn.stb.feature.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.designsystem.EmptyState
import cn.stb.core.designsystem.SearchField
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class DictionaryToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("english.dictionary"),
        subjectId = SubjectId("english"),
        title = "词典",
        summary = "英汉释义 · 音标例句 · 本地生词本",
        glyph = "词",
        keywords = listOf("英语", "单词", "词汇", "英汉", "生词本"),
        defaultPinned = true,
    )

    @Composable
    override fun Content(host: ToolHost) {
        DictionaryScreen(onBack = host::close)
    }
}

private val DictAccent = Color(0xFF2E7D6F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { DictionaryRepository(context) }

    var query by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<DictEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<DictEntry?>(null) }
    var savedWords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(query, savedOnly) {
        loading = true
        // 全量 77 万条下，中文反查的兜底 LIKE 要扫全表；防抖 300ms 避免每敲一个字都触发一次。
        delay(300)
        val result = withContext(Dispatchers.IO) {
            val list = when {
                savedOnly && query.isBlank() -> repository.search("", savedOnly = true)
                query.isBlank() -> repository.browse()
                else -> repository.search(query, savedOnly)
            }
            Triple(list, repository.savedWords(), repository.totalCount())
        }
        entries = result.first
        savedWords = result.second
        total = result.third
        if (selected == null || entries.none { it.headword == selected?.headword }) {
            selected = entries.firstOrNull()
        }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("英语词典")
                        Text(
                            text = if (total > 0) "ECDICT 词库 · 共 $total 条" else "本地词库",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "输入英文单词或中文释义",
                    modifier = Modifier.fillMaxWidth(),
                    showClearButton = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = savedOnly,
                        onClick = { savedOnly = !savedOnly },
                        label = { Text("生词本 ${savedWords.size}") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (savedOnly) DictAccent else MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                    Text(
                        text = if (loading) "检索中…" else "命中 ${entries.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 760.dp
                if (entries.isEmpty() && !loading) {
                    EmptyState(
                        glyph = "词",
                        title = if (savedOnly) "生词本还是空的" else "没有找到这个词",
                        description = if (savedOnly) {
                            "在词条详情页点右上角星标，就能把它收进生词本。"
                        } else {
                            "换个拼写试试，或者只输入前几个字母做前缀匹配。"
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        EntryList(
                            entries = entries,
                            selected = selected,
                            savedWords = savedWords,
                            onSelect = { selected = it },
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                        )
                        androidx.compose.material3.VerticalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            selected?.let {
                                EntryDetail(
                                    entry = it,
                                    saved = it.headword in savedWords,
                                    onToggleSave = {
                                        val now = repository.toggleSaved(it.headword)
                                        savedWords = if (now) savedWords + it.headword else savedWords - it.headword
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                } else {
                    if (selected == null) {
                        EntryList(
                            entries = entries,
                            selected = null,
                            savedWords = savedWords,
                            onSelect = { selected = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            androidx.compose.material3.TextButton(
                                onClick = { selected = null },
                                modifier = Modifier.padding(start = 8.dp),
                            ) { Text("← 返回列表") }
                            EntryDetail(
                                entry = selected!!,
                                saved = selected!!.headword in savedWords,
                                onToggleSave = {
                                    val e = selected!!
                                    val now = repository.toggleSaved(e.headword)
                                    savedWords = if (now) savedWords + e.headword else savedWords - e.headword
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    entries: List<DictEntry>,
    selected: DictEntry?,
    savedWords: Set<String>,
    onSelect: (DictEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries, key = { it.headword }) { entry ->
            val active = entry.headword == selected?.headword
            Surface(
                onClick = { onSelect(entry) },
                shape = RoundedCornerShape(12.dp),
                color = if (active) DictAccent.copy(alpha = 0.10f) else Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.headword,
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                            color = if (active) DictAccent else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = entry.translation.lineSequence().firstOrNull { it.isNotBlank() } ?: entry.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (entry.headword in savedWords) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "已在生词本",
                            tint = DictAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryDetail(
    entry: DictEntry,
    saved: Boolean,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.headword,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleSave) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (saved) "移出生词本" else "加入生词本",
                    tint = if (saved) DictAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
            }
        }

        if (entry.phonetic.isNotBlank()) {
            Text(
                text = "/${entry.phonetic.trim('/')}/",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.tagLabels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.tagLabels.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = DictAccent,
            )
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = entry.translation.ifBlank { "（该词条暂无中文释义）" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 26.sp,
                )
            }
        }

        if (entry.definition.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "英文释义",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DictAccent.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = entry.definition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
        }

        if (entry.exchangeLabels.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "词形变化",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.exchangeLabels.joinToString("　·　") { "${it.first} ${it.second}" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            )
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "词库来源：ECDICT（MIT License），完整收录词条与短语。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))
    }
}
