package cn.stb.feature.grammar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.stb.core.data.AssetReader
import cn.stb.core.designsystem.EmptyState
import cn.stb.core.designsystem.SearchField
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolIcon
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider

class GrammarToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("english.grammar"),
        subjectId = SubjectId("english"),
        title = "语法",
        summary = "薄冰大学英语语法 · 49 篇专题 · 全文检索",
        glyph = "法",
        icon = ToolIcon.GRAMMAR,
        keywords = listOf("语法", "英语", "时态", "从句", "冠词", "虚拟语气", "薄冰", "grammar"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        GrammarScreen(onBack = host::close)
    }
}

private val GrammarAccent = Color(0xFF2E7D6F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val book = remember { GrammarRepository(AssetReader(context)).book() }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var listCollapsed by rememberSaveable { mutableStateOf(false) }

    val selected = book.articles.firstOrNull { it.id == selectedId }
    val hits = remember(query) { book.search(query) }
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("英语语法")
                            Text(
                                text = "${book.articles.size} 篇 · ${book.categories.size} 类 · 全文检索",
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
                    actions = {
                        if (wide) {
                            IconButton(onClick = { listCollapsed = !listCollapsed }) {
                                Icon(
                                    imageVector = if (listCollapsed) {
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                                    } else {
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                                    },
                                    contentDescription = if (listCollapsed) "展开目录" else "收起目录",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    if (!listCollapsed) {
                        DirectoryPane(
                            book = book,
                            query = query,
                            hits = hits,
                            selectedId = selectedId,
                            listState = listState,
                            onQueryChange = { query = it },
                            onSelect = { selectedId = it },
                            modifier = Modifier.width(330.dp).fillMaxHeight(),
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (selected == null) {
                            HintPane(
                                if (listCollapsed) "目录已收起，点击右上角「展开目录」" else "从左侧选一篇开始"
                            )
                        } else {
                            key(selected.id) {
                                ArticleDetail(
                                    article = selected,
                                    query = query,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                } else {
                    if (selected == null) {
                        DirectoryPane(
                            book = book,
                            query = query,
                            hits = hits,
                            selectedId = null,
                            listState = listState,
                            onQueryChange = { query = it },
                            onSelect = { selectedId = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(
                                onClick = { selectedId = null },
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text("← 返回目录")
                            }
                            key(selected.id) {
                                ArticleDetail(
                                    article = selected,
                                    query = query,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryPane(
    book: GrammarBook,
    query: String,
    hits: List<GrammarHit>,
    selectedId: String?,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 换关键词后回到结果顶部，避免停留在上一组的滚动位置
    LaunchedEffect(query) {
        if (query.isNotBlank()) listState.scrollToItem(0)
    }

    Column(modifier = modifier) {
        SearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索语法，如「从句」「时态」",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            showClearButton = true,
        )

        when {
            query.isBlank() -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            ) {
                book.categories.forEach { category ->
                    item(key = "cat-${category.title}") {
                        CategoryHeader(category)
                    }
                    items(category.articles, key = { it.id }) { article ->
                        ArticleRow(
                            article = article,
                            active = article.id == selectedId,
                            onClick = { onSelect(article.id) },
                        )
                    }
                }
            }

            hits.isEmpty() -> EmptyState(
                glyph = "语",
                title = "没有找到相关内容",
                description = "换个词试试，例如「时态」「定语从句」「虚拟」",
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            ) {
                item(key = "hit-count") {
                    Text(
                        text = "找到 ${hits.size} 篇",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                items(hits, key = { it.article.id }) { hit ->
                    SearchHitRow(
                        hit = hit,
                        query = query,
                        active = hit.article.id == selectedId,
                        onClick = { onSelect(hit.article.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: GrammarCategory) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GrammarAccent),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = category.title,
            style = MaterialTheme.typography.labelLarge,
            color = GrammarAccent,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = category.articles.size.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ArticleRow(article: GrammarArticle, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) GrammarAccent.copy(alpha = 0.10f) else Color.Transparent,
    ) {
        Text(
            text = article.navTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) GrammarAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun SearchHitRow(hit: GrammarHit, query: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) GrammarAccent.copy(alpha = 0.10f) else Color.Transparent,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = hit.article.navTitle,
                style = MaterialTheme.typography.titleSmall,
                color = if (active) GrammarAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hit.article.category,
                style = MaterialTheme.typography.labelSmall,
                color = GrammarAccent.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = marked(hit.snippet, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArticleDetail(article: GrammarArticle, query: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            Text(
                text = article.category,
                style = MaterialTheme.typography.labelMedium,
                color = GrammarAccent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                article.blocks.forEach { block ->
                    BlockView(block = block, query = query)
                }
            }

            Spacer(Modifier.height(30.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "内容摘自《薄冰大学英语语法》，仅供学习交流。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BlockView(block: GrammarBlock, query: String) {
    when (block) {
        is GrammarBlock.Heading -> HeadingView(block, query)
        is GrammarBlock.Paragraph -> Text(
            text = marked(block.text, query),
            style = readingStyle(),
            color = MaterialTheme.colorScheme.onSurface,
        )

        is GrammarBlock.Quote -> QuoteView(block, query)
        is GrammarBlock.Code -> CodeView(block)
        is GrammarBlock.Table -> TableView(block, query)
    }
}

@Composable
private fun HeadingView(block: GrammarBlock.Heading, query: String) {
    val level = block.level
    val style = when {
        level <= 2 -> MaterialTheme.typography.titleLarge
        level == 3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val color = if (level <= 2) GrammarAccent else MaterialTheme.colorScheme.onBackground
    val text = marked(block.text, query)

    if (level <= 3) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(top = if (level == 2) 14.dp else 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(GrammarAccent.copy(alpha = 0.55f)),
            )
            Spacer(Modifier.width(10.dp))
            Text(text = text, style = style, color = color)
        }
    } else {
        Text(
            text = text,
            style = style,
            color = color,
            modifier = Modifier.padding(start = 13.dp, top = 4.dp),
        )
    }
}

@Composable
private fun QuoteView(block: GrammarBlock.Quote, query: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GrammarAccent.copy(alpha = 0.08f),
    ) {
        Text(
            text = marked(block.text, query),
            style = MaterialTheme.typography.bodyMedium.copy(
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.6f,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun CodeView(block: GrammarBlock.Code) {
    val style = MaterialTheme.typography.bodyMedium
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = block.text,
            style = style.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = style.fontSize * 1.65f,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun TableView(table: GrammarBlock.Table, query: String) {
    val columns = table.head.size.coerceAtLeast(1)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidth = maxOf(maxWidth / columns.toFloat(), 132.dp)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .width(cellWidth * columns.toFloat()),
            ) {
                Row(modifier = Modifier.background(GrammarAccent.copy(alpha = 0.09f))) {
                    table.head.forEach { cell ->
                        TableCell(text = cell, header = true, width = cellWidth, query = query)
                    }
                }
                table.rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    }
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.forEach { cell ->
                            TableCell(text = cell, header = false, width = cellWidth, query = query)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, header: Boolean, width: Dp, query: String) {
    Text(
        text = marked(text, query),
        style = if (header) {
            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color = if (header) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.width(width).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun readingStyle(): TextStyle {
    val style = MaterialTheme.typography.bodyLarge
    return style.copy(lineHeight = style.fontSize * 1.65f)
}

@Composable
private fun marked(text: String, query: String): AnnotatedString {
    val highlight = GrammarAccent.copy(alpha = 0.20f)
    return remember(text, query, highlight) {
        buildAnnotatedString {
            val needle = query.trim()
            text.split("**").forEachIndexed { index, part ->
                val start = length
                if (index % 2 == 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) }
                } else {
                    append(part)
                }
                if (needle.isNotEmpty()) {
                    var from = 0
                    while (true) {
                        val at = part.indexOf(needle, from, ignoreCase = true)
                        if (at < 0) break
                        addStyle(
                            SpanStyle(background = highlight),
                            start + at,
                            start + at + needle.length,
                        )
                        from = at + needle.length
                    }
                }
            }
        }
    }
}

@Composable
private fun HintPane(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
