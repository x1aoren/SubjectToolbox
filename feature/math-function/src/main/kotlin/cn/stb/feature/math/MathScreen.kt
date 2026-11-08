package cn.stb.feature.math

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.model.niceStep
import cn.stb.core.registry.ToolHost
import cn.stb.core.registry.ToolProvider
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

class MathToolProvider : ToolProvider {
    override val spec = ToolSpec(
        id = ToolId("math.function"),
        subjectId = SubjectId("math"),
        title = "函数编辑器",
        summary = "多曲线 · 圆与隐函数 · 上标显示",
        glyph = "函",
        keywords = listOf("函数", "图像", "绘图", "表达式", "坐标", "曲线", "圆", "方程", "隐函数"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        MathScreen(onBack = host::close)
    }
}

/** 一条曲线：表达式 + 颜色 + 是否显示。 */
internal data class PlotFn(
    val id: Int,
    val expr: String,
    val colorArgb: Long,
    val visible: Boolean = true,
)

private val curvePalette = listOf(
    0xFF34558B, // 靛蓝
    0xFFB4453A, // 赭红
    0xFF2E7D6F, // 青绿
    0xFF6B4A8F, // 绛紫
    0xFF9A6B1F, // 琥珀
    0xFF1F6F78, // 青碧
)

private const val MAX_CURVES = 6

/** 表达式可能含任意字符，用不可见分隔符打包，避免和输入内容冲突。 */
private const val SEP = "\u0001"

private val FnListSaver: Saver<List<PlotFn>, List<String>> = Saver(
    save = { list ->
        list.map { "${it.id}$SEP${it.colorArgb}$SEP${it.visible}$SEP${it.expr}" }
    },
    restore = { saved ->
        saved.mapNotNull { raw ->
            val parts = raw.split(SEP, limit = 4)
            if (parts.size < 4) null else PlotFn(
                id = parts[0].toIntOrNull() ?: 0,
                colorArgb = parts[1].toLongOrNull() ?: curvePalette[0],
                visible = parts[2].toBoolean(),
                expr = parts[3],
            )
        }
    },
)

internal data class Series(val colorArgb: Long, val segments: List<List<Pair<Double, Double>>>)

/** 图例条目带上原始序号，隐藏某条曲线后 f2 仍然叫 f2，不会错位。 */
private data class LegendItem(val colorArgb: Long, val expr: String, val number: Int)

internal data class PlotData(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val series: List<Series>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathScreen(onBack: () -> Unit) {
    var functions by rememberSaveable(stateSaver = FnListSaver) {
        mutableStateOf(listOf(PlotFn(1, "sin(x)", curvePalette[0])))
    }
    var selectedId by rememberSaveable { mutableIntStateOf(1) }
    var nextId by remember { mutableIntStateOf(2) }

    var xCenter by rememberSaveable { mutableStateOf(0.0) }
    var xSpan by rememberSaveable { mutableStateOf(20.0) }
    var yCenter by rememberSaveable { mutableStateOf(0.0) }
    var aspect by rememberSaveable { mutableStateOf(0.62f) }
    var params by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    val selected = functions.firstOrNull { it.id == selectedId } ?: functions.first()
    val selectedIndex = functions.indexOfFirst { it.id == selected.id }

    // 输入框自己持有光标位置；切到另一条曲线时重新装载
    var editor by remember(selected.id) {
        mutableStateOf(TextFieldValue(selected.expr, TextRange(selected.expr.length)))
    }

    fun update(id: Int, transform: (PlotFn) -> PlotFn) {
        functions = functions.map { if (it.id == id) transform(it) else it }
    }

    fun applyEditor(value: TextFieldValue) {
        editor = value
        update(selected.id) { it.copy(expr = value.text) }
    }

    // 键盘插入到光标处（有选区则替换选区），而不是永远追加到末尾
    fun insertAtCursor(text: String) {
        val source = editor.text
        val start = editor.selection.start.coerceIn(0, source.length)
        val end = editor.selection.end.coerceIn(0, source.length)
        val newText = source.substring(0, start) + text + source.substring(end)
        applyEditor(TextFieldValue(newText, TextRange(start + text.length)))
    }

    fun deleteAtCursor() {
        val source = editor.text
        val start = editor.selection.start.coerceIn(0, source.length)
        val end = editor.selection.end.coerceIn(0, source.length)
        if (start != end) {
            applyEditor(TextFieldValue(source.removeRange(start, end), TextRange(start)))
        } else if (start > 0) {
            applyEditor(TextFieldValue(source.removeRange(start - 1, start), TextRange(start - 1)))
        }
    }

    fun addFunction() {
        if (functions.size >= MAX_CURVES) return
        val id = nextId
        nextId += 1
        val seed = when (functions.size) {
            1 -> "cos(x)"
            2 -> "x^2"
            3 -> "x^2+y^2=25"
            else -> "x"
        }
        functions = functions + PlotFn(id, seed, curvePalette[functions.size % curvePalette.size])
        selectedId = id
    }

    fun removeFunction(id: Int) {
        if (functions.size <= 1) return
        val remaining = functions.filterNot { it.id == id }
        functions = remaining
        if (selectedId == id) selectedId = remaining.first().id
    }

    val active = remember(functions) {
        functions.filter { it.visible }.mapNotNull { fn ->
            runCatching { parseCurve(fn.expr) }.getOrNull()?.let { fn to it }
        }
    }

    val hasImplicit = remember(active) { active.any { it.second is CurveDef.Implicit } }

    val freeVars = remember(active) {
        active.flatMap { it.second.variables() }.filter { it != "x" && it != "y" }.distinct()
    }

    val legend = remember(functions) {
        functions.mapIndexedNotNull { index, fn ->
            if (!fn.visible) return@mapIndexedNotNull null
            if (runCatching { parseCurve(fn.expr) }.isFailure) null
            else LegendItem(fn.colorArgb, fn.expr, index + 1)
        }
    }

    LaunchedEffect(freeVars) {
        params = freeVars.take(4).associateWith { params[it] ?: 1.0 }
    }

    val plot = remember(functions, xCenter, xSpan, yCenter, params, aspect) {
        composePlot(functions, xCenter, xSpan, yCenter, params, aspect.toDouble())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("函数编辑器")
                        Text(
                            text = if (functions.size > 1) {
                                "${functions.size} 条曲线 · 同一坐标系"
                            } else {
                                "支持方程，如 x^2+y^2=25"
                            },
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 720.dp

            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    EditorPanel(
                        functions = functions,
                        selected = selected,
                        selectedIndex = selectedIndex,
                        editor = editor,
                        freeVars = freeVars,
                        params = params,
                        onEditorChange = ::applyEditor,
                        onSelect = { selectedId = it },
                        onInsert = ::insertAtCursor,
                        onBackspace = ::deleteAtCursor,
                        onClear = { applyEditor(TextFieldValue("")) },
                        onToggleVisible = { id -> update(id) { fn -> fn.copy(visible = !fn.visible) } },
                        onDelete = { removeFunction(it) },
                        onAdd = { addFunction() },
                        onParamChange = { name, value -> params = params + (name to value) },
                        modifier = Modifier.width(404.dp).fillMaxHeight(),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PlotPane(
                        plot = plot,
                        legend = legend,
                        onPan = { dx, dy, w, h ->
                            xCenter -= dx / w * xSpan
                            yCenter += dy / h * (xSpan * aspect)
                        },
                        onZoom = { zoom -> xSpan = (xSpan / zoom).coerceIn(0.05, 5000.0) },
                        onReset = { xCenter = 0.0; xSpan = 20.0; yCenter = 0.0 },
                        onAspect = { newAspect ->
                            if (abs(newAspect - aspect) > 0.01f) aspect = newAspect
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PlotPane(
                        plot = plot,
                        legend = legend,
                        onPan = { dx, dy, w, h ->
                            xCenter -= dx / w * xSpan
                            yCenter += dy / h * (xSpan * aspect)
                        },
                        onZoom = { zoom -> xSpan = (xSpan / zoom).coerceIn(0.05, 5000.0) },
                        onReset = { xCenter = 0.0; xSpan = 20.0; yCenter = 0.0 },
                        onAspect = { newAspect ->
                            if (abs(newAspect - aspect) > 0.01f) aspect = newAspect
                        },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                    EditorPanel(
                        functions = functions,
                        selected = selected,
                        selectedIndex = selectedIndex,
                        editor = editor,
                        freeVars = freeVars,
                        params = params,
                        onEditorChange = ::applyEditor,
                        onSelect = { selectedId = it },
                        onInsert = ::insertAtCursor,
                        onBackspace = ::deleteAtCursor,
                        onClear = { applyEditor(TextFieldValue("")) },
                        onToggleVisible = { id -> update(id) { fn -> fn.copy(visible = !fn.visible) } },
                        onDelete = { removeFunction(it) },
                        onAdd = { addFunction() },
                        onParamChange = { name, value -> params = params + (name to value) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPanel(
    functions: List<PlotFn>,
    selected: PlotFn,
    selectedIndex: Int,
    editor: TextFieldValue,
    freeVars: List<String>,
    params: Map<String, Double>,
    onEditorChange: (TextFieldValue) -> Unit,
    onSelect: (Int) -> Unit,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onToggleVisible: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onParamChange: (String, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = if (editor.text.isBlank()) null else
        runCatching { parseCurve(editor.text) }.exceptionOrNull()?.message

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "函数列表",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${functions.size}/$MAX_CURVES",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))

        // 固定高度 + 独立滚动：函数再多也不会把下面的键盘顶出屏幕
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 172.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(functions, key = { _, fn -> fn.id }) { index, fn ->
                FunctionRow(
                    fn = fn,
                    index = index,
                    selected = fn.id == selected.id,
                    hasError = fn.expr.isNotBlank() && runCatching { parseCurve(fn.expr) }.isFailure,
                    canDelete = functions.size > 1,
                    onSelect = { onSelect(fn.id) },
                    onToggleVisible = { onToggleVisible(fn.id) },
                    onDelete = { onDelete(fn.id) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onAdd,
            enabled = functions.size < MAX_CURVES,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (functions.size < MAX_CURVES) "添加函数" else "最多 $MAX_CURVES 条曲线")
        }

        Spacer(Modifier.height(14.dp))

        ExpressionField(
            value = editor,
            onValueChange = onEditorChange,
            label = if (selectedIndex == 0) "f(x) =" else "f${selectedIndex + 1}(x) =",
            isError = error != null,
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))
        Keypad(onKey = onInsert, onBackspace = onBackspace, onClear = onClear)

        if (freeVars.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "参数（对所有曲线生效）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            freeVars.take(4).forEach { name ->
                val value = params[name] ?: 1.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.width(22.dp),
                    )
                    Slider(
                        value = value.toFloat().coerceIn(-5f, 5f),
                        onValueChange = { onParamChange(name, it.toDouble()) },
                        valueRange = -5f..5f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", value),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.width(46.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * 表达式输入框。
 *
 * 没有用 OutlinedTextField，是因为要在文字下面自己描上标虚线框：
 * 显示层把 `x^2` 转成「x + 抬高的 2」，这里再按上标 span 的位置把框画出来。
 * 存储始终是 `x^2`，光标位置由 Transformation 的 OffsetMapping 映射回原文本。
 */
@Composable
private fun ExpressionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    isError: Boolean,
) {
    val transformation = remember { SuperscriptTransformation() }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }

    // 这个工具自带了数学键盘，系统输入法一弹出来就会把键盘和图像盖住，
    // 所以聚焦时直接收起 IME。外接实体键盘照样能打字（按键事件不依赖 IME 是否显示）。
    val keyboard = LocalSoftwareKeyboardController.current

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val slotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .drawBehind {
                        drawSuperscriptSlot(
                            layout = textLayout,
                            color = slotColor,
                            spanIndex = transformation.activeRunIndex(value.selection.end),
                        )
                    },
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        // 换行会把表达式拆成两行文本，直接吞掉（软键盘已隐藏，只有实体键盘会发 Enter）
                        if ('\n' in newValue.text) {
                            val cleaned = newValue.text.replace("\n", "")
                            val caret = newValue.selection.end.coerceIn(0, cleaned.length)
                            onValueChange(TextFieldValue(cleaned, TextRange(caret)))
                        } else {
                            onValueChange(newValue)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            focused = state.isFocused
                            if (state.isFocused) keyboard?.hide()
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    // 允许多行换行而不是横向滚动：横向滚动时上标虚线框会跟着跑到视野外
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = transformation,
                    onTextLayout = { textLayout = it },
                )
            }
        }
    }
}

/**
 * 只给光标所在的那个上标槽描虚线框。
 * 光标不在指数里时不画——输入完 x^2 继续往后打，表达式就恢复成普通上标小字。
 */
private fun DrawScope.drawSuperscriptSlot(
    layout: TextLayoutResult?,
    color: Color,
    spanIndex: Int?,
) {
    if (layout == null || spanIndex == null) return
    val text = layout.layoutInput.text
    val span = text.spanStyles
        .filter { it.item.baselineShift == BaselineShift.Superscript }
        .getOrNull(spanIndex) ?: return
    if (span.end <= span.start) return

    val line = layout.getLineForOffset(span.start)
    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)

    val left = (layout.getHorizontalPosition(span.start, true) - 2.dp.toPx()).coerceAtLeast(0f)
    val right = layout.getHorizontalPosition(span.end, true) + 2.dp.toPx()
    val width = (right - left).coerceAtLeast(14.dp.toPx())

    drawRoundRect(
        color = color,
        topLeft = Offset(left, lineTop + 1.dp.toPx()),
        size = Size(width, (lineBottom - lineTop) * 0.62f),
        cornerRadius = CornerRadius(3.dp.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        ),
    )
}

@Composable
private fun FunctionRow(
    fn: PlotFn,
    index: Int,
    selected: Boolean,
    hasError: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = Color(fn.colorArgb)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.10f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (fn.visible) color else Color.Transparent)
                    .border(1.5.dp, if (fn.visible) color else color.copy(alpha = 0.45f), CircleShape)
                    .clickable(onClickLabel = if (fn.visible) "隐藏该曲线" else "显示该曲线", onClick = onToggleVisible),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "f${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp),
            )
            Text(
                text = if (fn.expr.isBlank()) AnnotatedString("（空表达式）") else superscriptAnnotated(fn.expr),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (fn.visible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasError) {
                Text(
                    text = "语法错误",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除 f${index + 1}",
                    modifier = Modifier.size(15.dp),
                    tint = if (canDelete) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
}

@Composable
private fun Keypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // 数字与基本运算符（+ - × ÷ . =）：中性配色
        val numberRows = listOf(
            listOf("7", "8", "9", "÷", "(", ")"),
            listOf("4", "5", "6", "×", "=", "⌫"),
            listOf("1", "2", "3", "+", "-", "."),
        )
        numberRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        modifier = Modifier.weight(1f),
                        accent = false,
                        onClick = {
                            when (key) {
                                "×" -> onKey("*")
                                "÷" -> onKey("/")
                                "⌫" -> onBackspace()
                                else -> onKey(key)
                            }
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyButton(
                label = "0",
                modifier = Modifier.weight(2f),
                accent = false,
                onClick = { onKey("0") },
            )
            KeyButton(
                label = "清空",
                modifier = Modifier.weight(4f),
                accent = false,
                onClick = onClear,
            )
        }

        Spacer(Modifier.height(3.dp))

        // 函数与常数（x、y、三角/对数/指数）：强调配色，和数字键明确区分
        val functionRows = listOf(
            listOf("x", "y", "sin", "cos", "tan", "log", "lg", "ln"),
            listOf("a", "b", "c", "^", "√", "π", "e"),
        )
        functionRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { key ->
                    KeyButton(
                        label = key,
                        modifier = Modifier.weight(1f),
                        accent = true,
                        slot = key == "^",
                        onClick = {
                            when (key) {
                                "√" -> onKey("sqrt(")
                                "sin", "cos", "tan", "log", "lg", "ln" -> onKey("$key(")
                                else -> onKey(key)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    slot: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (accent) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(9.dp),
        color = if (accent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (slot) {
                SuperscriptSlotIcon(color = contentColor)
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
            }
        }
    }
}

/** ^ 键的图标：x 右上角一个空的上标槽，和输入框里按 ^ 之后长出来的框是同一个形象。 */
@Composable
private fun SuperscriptSlotIcon(color: Color) {
    Row(
        modifier = Modifier.semantics { contentDescription = "上标" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "x",
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
        Spacer(Modifier.width(1.dp))
        Box(
            modifier = Modifier
                .offset(y = (-6).dp)
                .size(11.dp)
                .drawBehind {
                    drawRoundRect(
                        color = color.copy(alpha = 0.75f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(2.dp.toPx(), 1.5.dp.toPx()),
                            ),
                        ),
                    )
                },
        )
    }
}

@Composable
private fun PlotPane(
    plot: PlotData?,
    legend: List<LegendItem>,
    onPan: (Float, Float, Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
    onAspect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val axisColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .onSizeChanged { size ->
                    if (size.width > 0) onAspect(size.height.toFloat() / size.width.toFloat())
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (pan.x != 0f || pan.y != 0f) {
                            onPan(pan.x, pan.y, size.width.toFloat(), size.height.toFloat())
                        }
                        if (zoom != 1f) onZoom(zoom)
                    }
                },
        ) {
            if (plot == null) return@Canvas
            drawPlot(plot, gridColor, axisColor, labelColor)
        }

        if (plot == null) {
            Text(
                text = if (legend.isEmpty()) "没有可绘制的函数" else "表达式还不能计算",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 图例：只列当前可见且能解析的曲线
        if (legend.isNotEmpty()) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    legend.forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(item.colorArgb)),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = superscriptAnnotated("f${item.number} = ${item.expr.ifBlank { "—" }}"),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index != legend.lastIndex) Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }

        Surface(
            onClick = onReset,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "复位视图",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp).size(18.dp),
            )
        }

        if (plot != null) {
            Text(
                text = String.format(
                    Locale.US, "x ∈ [%.2f, %.2f]　y ∈ [%.2f, %.2f]",
                    plot.xMin, plot.xMax, plot.yMin, plot.yMax,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

/**
 * 把若干条曲线合成一张图：共用同一个 x 窗口与 y 轴范围，各自采样。
 *
 * 显式函数按 x 采样，能正确处理渐近线；带等号的方程走 marching squares 提等值线。
 * 解析失败的曲线直接跳过——学生正打到一半的表达式不该让整张图消失。
 */
internal fun composePlot(
    curves: List<PlotFn>,
    xCenter: Double,
    xSpan: Double,
    yCenter: Double,
    params: Map<String, Double>,
    aspect: Double,
): PlotData? {
    val parsed = curves.filter { it.visible }.mapNotNull { fn ->
        runCatching { parseCurve(fn.expr) }.getOrNull()?.let { fn to it }
    }
    if (parsed.isEmpty()) return null

    val xMin = xCenter - xSpan / 2
    val xMax = xCenter + xSpan / 2

    // 视口完全由用户掌控（xCenter/xSpan/yCenter + 宽高比），不再按函数值自动缩放：
    // 拖动平移、修改表达式都不会让视野跳走。纵轴跨度 = 横轴跨度 × 宽高比，圆不会变形。
    val yHalf = (xSpan * aspect / 2).coerceAtLeast(1e-6)
    val yMin = yCenter - yHalf
    val yMax = yCenter + yHalf

    return PlotData(
        xMin = xMin,
        xMax = xMax,
        yMin = yMin,
        yMax = yMax,
        series = parsed.map { (fn, def) ->
            val segments = when (def) {
                is CurveDef.Explicit -> def.f.sampleSegments(xMin, xMax, 700, params, yMin, yMax)
                is CurveDef.Implicit -> sampleImplicit(
                    f = { x, y -> def.f.eval(x, params, y) },
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                )
            }
            Series(fn.colorArgb, segments)
        },
    )
}

private fun DrawScope.drawPlot(
    plot: PlotData,
    gridColor: Color,
    axisColor: Color,
    labelColor: Color,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val xRange = (plot.xMax - plot.xMin).coerceAtLeast(1e-9)
    val yRange = (plot.yMax - plot.yMin).coerceAtLeast(1e-9)

    fun px(x: Double): Float = ((x - plot.xMin) / xRange * w).toFloat()
    fun py(y: Double): Float = (h - (y - plot.yMin) / yRange * h).toFloat()

    val stepX = niceStep(xRange, 10)
    val stepY = niceStep(yRange, 8)

    var gx = ceil(plot.xMin / stepX) * stepX
    while (gx <= plot.xMax) {
        val x = px(gx)
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        gx += stepX
    }
    var gy = ceil(plot.yMin / stepY) * stepY
    while (gy <= plot.yMax) {
        val y = py(gy)
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        gy += stepY
    }

    if (0.0 in plot.yMin..plot.yMax) {
        val y = py(0.0)
        drawLine(axisColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.6f)
    }
    if (0.0 in plot.xMin..plot.xMax) {
        val x = px(0.0)
        drawLine(axisColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.6f)
    }

    plot.series.forEach { series ->
        val color = Color(series.colorArgb)
        series.segments.forEach { segment ->
            if (segment.size >= 2) {
                val path = Path()
                segment.forEachIndexed { index, (x, y) ->
                    val ox = px(x)
                    val oy = py(y)
                    if (index == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }

    val labelPx = 10.sp.toPx()
    val stepPx = 12.sp.toPx()

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = labelPx
            color = labelColor.toArgb()
        }

        var tx = ceil(plot.xMin / stepX) * stepX
        while (tx <= plot.xMax) {
            val label = formatTick(tx, stepX)
            val x = px(tx)
            val y = if (0.0 in plot.yMin..plot.yMax) py(0.0) + stepPx else h - 4f
            canvas.nativeCanvas.drawText(label, x - label.length * 2.6f, y.coerceIn(12f, h - 4f), paint)
            tx += stepX
        }

        var ty = ceil(plot.yMin / stepY) * stepY
        while (ty <= plot.yMax) {
            if (abs(ty) > 1e-9) {
                val label = formatTick(ty, stepY)
                val y = py(ty)
                val x = if (0.0 in plot.xMin..plot.xMax) px(0.0) - label.length * 5.4f - 4f else 4f
                canvas.nativeCanvas.drawText(label, x.coerceAtLeast(2f), y + 4f, paint)
            }
            ty += stepY
        }
    }
}

private fun formatTick(value: Double, step: Double): String {
    val decimals = when {
        step >= 1 -> 0
        step >= 0.1 -> 1
        step >= 0.01 -> 2
        else -> 3
    }
    val text = String.format(Locale.US, "%.${decimals}f", value)
    return if (text == "-0") "0" else text
}
