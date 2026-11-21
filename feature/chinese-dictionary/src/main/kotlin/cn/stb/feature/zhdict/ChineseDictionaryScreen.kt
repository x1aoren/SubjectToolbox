package cn.stb.feature.zhdict

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.designsystem.EmptyState
import cn.stb.core.designsystem.SearchField
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolIcon
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ChineseDictionaryToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("chinese.dictionary"),
        subjectId = SubjectId("chinese"),
        title = "汉语词典",
        summary = "字词释义 · 拼音检索 · 本地生词本",
        glyph = "典",
        icon = ToolIcon.DICTIONARY,
        keywords = listOf("语文", "字典", "词典", "汉语", "汉字", "拼音", "生词本"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        ChineseDictionaryScreen(onBack = host::close)
    }
}

private val ZhAccent = Color(0xFF9C4438)

/** 每次「加载更多」多取的条数。前缀命中可能有上千条，一次全查出来会占住内存。 */
private const val PAGE_SIZE = 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChineseDictionaryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ChineseDictionaryRepository(context) }

    var query by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<ChineseDictEntry>>(emptyList()) }
    var related by remember { mutableStateOf<List<ChineseDictEntry>>(emptyList()) }
    // 释义反查结果默认收起：它们只是「某个字的释义里提过这个词」，不该和词条命中抢位置。
    var relatedOpen by remember(query) { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ChineseDictEntry?>(null) }
    var savedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var hasMore by remember { mutableStateOf(false) }
    var limit by remember(query, savedOnly) { mutableStateOf(PAGE_SIZE) }

    LaunchedEffect(query, savedOnly, limit) {
        loading = true
        // 释义反查走倒排索引，但拼音/前缀仍是全表范围扫描，防抖 300ms 避免每敲一个字都查一次。
        delay(300)
        val result = withContext(Dispatchers.IO) {
            val searched = when {
                query.isBlank() -> ChineseSearchResult(
                    entries = if (savedOnly) repository.search("", savedOnly = true).entries
                    else repository.browse(limit),
                )
                else -> repository.search(query, savedOnly, limit)
            }
            Triple(searched, repository.savedKeys(), repository.totalCount())
        }
        entries = result.first.entries
        related = result.first.related
        savedKeys = result.second
        total = result.third
        // 没有词条命中时，释义相关就是唯一结果，直接展开；有词条命中则保持收起，避免抢位置。
        if (entries.isEmpty() && related.isNotEmpty()) relatedOpen = true
        // 生词本不限条数；其余列表取满一页才露出「加载更多」。
        hasMore = !(savedOnly && query.isBlank()) && entries.size >= limit
        val visible = entries + related
        if (selected == null || visible.none { it.key == selected?.key }) {
            selected = entries.firstOrNull() ?: related.firstOrNull()
        }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("汉语词典")
                        Text(
                            text = if (total > 0) "现代汉语词典 · 共 $total 条" else "本地词库",
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
                    placeholder = "输入汉字、词语或拼音",
                    modifier = Modifier.fillMaxWidth(),
                    showClearButton = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = savedOnly,
                        onClick = { savedOnly = !savedOnly },
                        label = { Text("生词本 ${savedKeys.size}") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (savedOnly) ZhAccent else MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                    Text(
                        text = when {
                            loading -> "检索中…"
                            entries.isEmpty() && related.isNotEmpty() -> "释义相关 ${related.size} 条"
                            hasMore -> "已显示 ${entries.size} 条"
                            related.isNotEmpty() -> "命中 ${entries.size} 条 · 释义相关 ${related.size} 条"
                            else -> "命中 ${entries.size} 条"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 760.dp
                if (entries.isEmpty() && related.isEmpty() && !loading) {
                    EmptyState(
                        glyph = "典",
                        title = if (savedOnly) "生词本还是空的" else "没有找到这个词",
                        description = if (savedOnly) {
                            "在词条详情页点右上角星标，就能把它收进生词本。"
                        } else {
                            "换个写法试试，也可以直接输入拼音，比如 shui。"
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        EntryList(
                            entries = entries,
                            related = related,
                            relatedOpen = relatedOpen,
                            query = query.trim(),
                            selected = selected,
                            savedKeys = savedKeys,
                            hasMore = hasMore,
                            onLoadMore = { limit += PAGE_SIZE },
                            onToggleRelated = { relatedOpen = !relatedOpen },
                            onSelect = { selected = it },
                            modifier = Modifier.width(320.dp).fillMaxHeight(),
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            selected?.let {
                                EntryDetail(
                                    entry = it,
                                    saved = it.key in savedKeys,
                                    onToggleSave = {
                                        val now = repository.toggleSaved(it.key)
                                        savedKeys = if (now) savedKeys + it.key else savedKeys - it.key
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
                            related = related,
                            relatedOpen = relatedOpen,
                            query = query.trim(),
                            selected = null,
                            savedKeys = savedKeys,
                            hasMore = hasMore,
                            onLoadMore = { limit += PAGE_SIZE },
                            onToggleRelated = { relatedOpen = !relatedOpen },
                            onSelect = { selected = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(
                                onClick = { selected = null },
                                modifier = Modifier.padding(start = 8.dp),
                            ) { Text("← 返回列表") }
                            EntryDetail(
                                entry = selected!!,
                                saved = selected!!.key in savedKeys,
                                onToggleSave = {
                                    val entry = selected!!
                                    val now = repository.toggleSaved(entry.key)
                                    savedKeys = if (now) savedKeys + entry.key else savedKeys - entry.key
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
    entries: List<ChineseDictEntry>,
    related: List<ChineseDictEntry>,
    relatedOpen: Boolean,
    query: String,
    selected: ChineseDictEntry?,
    savedKeys: Set<String>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onToggleRelated: () -> Unit,
    onSelect: (ChineseDictEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries, key = { it.key }) { entry ->
            EntryRow(
                entry = entry,
                active = entry.key == selected?.key,
                saved = entry.key in savedKeys,
                onSelect = { onSelect(entry) },
            )
        }

        // 释义反查单独成组、默认收起：这些条目只是释义主干里提过查询词，不是词条命中。
        if (related.isNotEmpty()) {
            item(key = "related-header") {
                RelatedHeader(
                    query = query,
                    count = related.size,
                    expanded = relatedOpen,
                    onToggle = onToggleRelated,
                )
            }
            if (relatedOpen) {
                items(related, key = { "related:${it.key}" }) { entry ->
                    EntryRow(
                        entry = entry,
                        active = entry.key == selected?.key,
                        saved = entry.key in savedKeys,
                        onSelect = { onSelect(entry) },
                    )
                }
            }
        }

        if (hasMore) {
            item {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("加载更多") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RelatedHeader(
    query: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        color = ZhAccent.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "释义中提到「$query」的 $count 条",
                style = MaterialTheme.typography.bodySmall,
                color = ZhAccent.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: ChineseDictEntry,
    active: Boolean,
    saved: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (active) ZhAccent.copy(alpha = 0.10f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Serif),
                        color = if (active) ZhAccent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.pinyin.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = entry.pinyin,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = entry.definition.lineSequence().firstOrNull { it.isNotBlank() } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (saved) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "已在生词本",
                    tint = ZhAccent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun EntryDetail(
    entry: ChineseDictEntry,
    saved: Boolean,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.word,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleSave) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (saved) "移出生词本" else "加入生词本",
                    tint = if (saved) ZhAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
            }
        }

        if (entry.pinyin.isNotBlank()) {
            Text(
                text = entry.pinyin,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (entry.traditional.isNotBlank() || entry.variants.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            if (entry.traditional.isNotBlank()) {
                FormRow("繁体", entry.traditional)
            }
            if (entry.variants.isNotBlank()) {
                FormRow("异体", entry.variants)
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                DefinitionText(
                    text = entry.definition.ifBlank { "（该词条暂无释义）" },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            text = "词库来源：现代汉语词典（第 7 版）mdx 资源 · pampas9000/ModernChineseDict",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DefinitionText(text: String) {
    val styled = remember(text) { buildDefinitionText(text, ZhAccent) }
    Text(
        text = styled,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 27.sp,
    )
}

/**
 * 给义项序号、词性标记和「注意」块上色。
 *
 * 数据入库时已经是纯文本，这里只按字符形态上色，不重新解析 HTML：
 * ①-⑳、㉑-㉟、㊱-㊿ 是义项序号，〔名〕［书］这类方括号是词性，【】是编者注。
 */
private fun buildDefinitionText(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in DEFINITION_MARKER.findAll(text)) {
        if (match.range.first > last) append(text.substring(last, match.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
            append(match.value)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

private val DEFINITION_MARKER = Regex(
    "([\\u2460-\\u2473\\u3251-\\u325F\\u32B1-\\u32BF]|〔[^〕]{1,8}〕|【[^】]{1,12}】)",
)
