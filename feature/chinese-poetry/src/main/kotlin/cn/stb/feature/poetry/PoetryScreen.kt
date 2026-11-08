package cn.stb.feature.poetry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.data.AssetReader
import cn.stb.core.model.AppGraph
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider

class PoetryToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("chinese.poetry"),
        subjectId = SubjectId("chinese"),
        title = "古诗词",
        summary = "必背篇目 · 注释赏析 · 背诵模式",
        glyph = "诗",
        keywords = listOf("古诗", "词", "背诵", "文言文", "教材"),
        defaultPinned = true,
    )

    @Composable
    override fun Content(host: ToolHost) {
        PoetryScreen(onBack = host::close)
    }
}

private val PoemAccent = Color(0xFF9C4438)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoetryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { PoetryRepository(AssetReader(context)) }
    val books = remember { repository.books() }

    var bookIndex by rememberSaveable { mutableStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // 平板双栏时目录可以收起，把整个宽度让给正文；旋转/进程重建后保留折叠状态。
    var listCollapsed by rememberSaveable { mutableStateOf(false) }

    val book = books.getOrNull(bookIndex)
    val poems = book?.poems.orEmpty()
    val selected = poems.firstOrNull { it.id == selectedId }
    // 目录的滚动位置随折叠/展开保留，不因为列表被移除又重建而跳回顶部。
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("古诗词")
                            Text(
                                text = "必修 · 必背篇目",
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
                        // 单栏时目录本身就是整页，只有双栏才有「左侧目录」可折叠。
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
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(books) { b ->
                        FilterChip(
                            selected = b.id == book?.id,
                            onClick = {
                                bookIndex = books.indexOf(b)
                                selectedId = null
                            },
                            label = { Text(b.title) },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = !listCollapsed,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut(),
                        ) {
                            Row(modifier = Modifier.fillMaxHeight()) {
                                PoemList(
                                    poems = poems,
                                    selectedId = selectedId,
                                    onSelect = { selectedId = it },
                                    state = listState,
                                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (selected != null) {
                                PoemDetail(poem = selected, modifier = Modifier.fillMaxSize())
                            } else {
                                HintPane(
                                    if (listCollapsed) "目录已收起，点击右上角「展开目录」" else "从左侧选一篇开始"
                                )
                            }
                        }
                    }
                } else {
                    if (selected == null) {
                        PoemList(
                            poems = poems,
                            selectedId = null,
                            onSelect = { selectedId = it },
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(onClick = { selectedId = null }, modifier = Modifier.padding(start = 8.dp)) {
                                Text("← 返回目录")
                            }
                            PoemDetail(poem = selected, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoemList(
    poems: List<Poem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(poems, key = { it.id }) { poem ->
            val active = poem.id == selectedId
            Surface(
                onClick = { onSelect(poem.id) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) PoemAccent.copy(alpha = 0.10f) else Color.Transparent,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = poem.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active) PoemAccent else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${poem.dynasty} · ${poem.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PoemDetail(poem: Poem, modifier: Modifier = Modifier) {
    var memorize by rememberSaveable(poem.id) { mutableStateOf(false) }
    var revealed by remember(poem.id) { mutableStateOf(setOf<Int>()) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Text(
            text = poem.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${poem.dynasty} · ${poem.author}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("背诵模式", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = memorize,
                onCheckedChange = {
                    memorize = it
                    revealed = emptySet()
                },
            )
            Spacer(Modifier.weight(1f))
            if (memorize) {
                TextButton(onClick = { revealed = poem.lines.indices.toSet() }) { Text("全部显示") }
                TextButton(onClick = { revealed = emptySet() }) { Text("全部隐藏") }
            }
        }

        Spacer(Modifier.height(10.dp))
        poem.lines.forEachIndexed { index, line ->
            val hidden = memorize && index !in revealed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = memorize) {
                        revealed = if (index in revealed) revealed - index else revealed + index
                    }
                    .background(
                        if (hidden) PoemAccent.copy(alpha = 0.07f) else Color.Transparent
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = line,
                    color = if (hidden) Color.Transparent else MaterialTheme.colorScheme.onBackground,
                    fontSize = 19.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        if (poem.notes.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "注释",
                style = MaterialTheme.typography.titleSmall,
                color = PoemAccent,
            )
            Spacer(Modifier.height(8.dp))
            poem.notes.forEach { note ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        text = note.term,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "　${note.explain}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (poem.appreciation.isNotBlank()) {
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Text("赏析", style = MaterialTheme.typography.titleSmall, color = PoemAccent)
            Spacer(Modifier.height(8.dp))
            Text(
                text = poem.appreciation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }

        Spacer(Modifier.height(40.dp))
        Text(
            text = "以统编版教材为准",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun HintPane(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
