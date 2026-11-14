package cn.stb.feature.math

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
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
        summary = "多曲线 · 显隐式切换 · 滑块与定义域",
        glyph = "函",
        keywords = listOf("函数", "图像", "绘图", "表达式", "坐标", "曲线", "圆", "方程", "隐函数", "开方", "滑块", "定义域"),
    )

    @Composable
    override fun Content(host: ToolHost) {
        MathScreen(onBack = host::close)
    }
}

/** 一条曲线画出来的结果：颜色 + 若干段折线。 */
internal data class Series(val colorArgb: Long, val segments: List<List<Pair<Double, Double>>>)

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

    // 滑块与「用户主动删掉的变量」都跨旋转保存
    var params by rememberSaveable(stateSaver = ParamListSaver) { mutableStateOf(emptyList<ParamDef>()) }
    var dismissedVarsRaw by rememberSaveable { mutableStateOf("") }
    val dismissedVars = remember(dismissedVarsRaw) {
        dismissedVarsRaw.split(',').filter { it.isNotBlank() }.toSet()
    }

    var domainDialogFor by remember { mutableStateOf<Int?>(null) }
    var pendingParamDelete by remember { mutableStateOf<ParamDef?>(null) }

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

    fun applyEdit(edit: MathEdit.Edit) = applyEditor(edit.toTextFieldValue())

    fun deleteParam(def: ParamDef) {
        params = params.filterNot { it.name == def.name }
        dismissedVarsRaw = (dismissedVars + def.name).joinToString(",")
    }

    fun requestDeleteParam(def: ParamDef) {
        val used = functions.any { fn ->
            runCatching { parseCurve(fn.mode, fn.expr) }.getOrNull()
                ?.variables()?.contains(def.name) == true
        }
        if (used) pendingParamDelete = def else deleteParam(def)
    }

    fun addParam(def: ParamDef) {
        if (params.size >= MAX_SLIDERS || params.any { it.name == def.name }) return
        params = params + def
        dismissedVarsRaw = (dismissedVars - def.name).joinToString(",")
    }

    fun updateParam(def: ParamDef) {
        params = params.map { if (it.name == def.name) def else it }
    }

    // 表达式里新出现的自由变量自动生成滑块；用户删过的变量不再自动塞回来
    val activeCurves = remember(functions) {
        functions.filter { it.visible }.mapNotNull { fn ->
            runCatching { parseCurve(fn.mode, fn.expr) }.getOrNull()?.let { fn to it }
        }
    }
    val freeVars = remember(activeCurves) {
        activeCurves.flatMap { it.second.variables() }.filter { it != "x" && it != "y" }.distinct()
    }
    LaunchedEffect(freeVars, dismissedVars, params.size) {
        val missing = freeVars.filter { name ->
            params.none { it.name == name } && name !in dismissedVars && validateParamName(name, params) == null
        }
        if (missing.isNotEmpty()) {
            val room = MAX_SLIDERS - params.size
            if (room > 0) {
                params = params + missing.take(room).map { ParamDef(name = it, value = 1.0, auto = true) }
            }
        }
    }

    val paramsMap = remember(params) { params.associate { it.name to it.value } }

    val legend = remember(functions) {
        functions.mapIndexedNotNull { index, fn ->
            if (!fn.visible) return@mapIndexedNotNull null
            if (runCatching { parseCurve(fn.mode, fn.expr) }.isFailure) return@mapIndexedNotNull null
            LegendItem(fn.colorArgb, fn.expr, index + 1, fn.mode)
        }
    }

    val plot = remember(functions, xCenter, xSpan, yCenter, params, aspect) {
        composePlot(functions, xCenter, xSpan, yCenter, paramsMap, aspect.toDouble())
    }

    fun addFunction() {
        if (functions.size >= MAX_CURVES) return
        val id = nextId
        nextId += 1
        // 优先挑一个还没用过的颜色，删改之后也不会两条曲线撞色
        val usedColors = functions.map { it.colorArgb }.toSet()
        val color = curvePalette.firstOrNull { it !in usedColors }
            ?: curvePalette[functions.size.coerceAtMost(curvePalette.lastIndex)]
        val seed = when (functions.size) {
            1 -> PlotFn(id, "cos(x)", color)
            2 -> PlotFn(id, "x^2", color)
            3 -> PlotFn(id, "x^2+y^2=25", color, mode = FnMode.IMPLICIT)
            else -> PlotFn(id, "x", color)
        }
        functions = functions + seed
        selectedId = id
    }

    fun removeFunction(id: Int) {
        if (functions.size <= 1) return
        val remaining = functions.filterNot { it.id == id }
        functions = remaining
        if (selectedId == id) selectedId = remaining.first().id
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
                                "支持方程与开方，点 f(x) 可切换显隐式"
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
            val panelWidth = if (maxWidth >= 1160.dp) 468.dp else 412.dp

            val editorPanel: @Composable (Modifier) -> Unit = { panelModifier ->
                EditorPanel(
                    functions = functions,
                    selected = selected,
                    selectedIndex = selectedIndex,
                    editor = editor,
                    params = params,
                    onEditorChange = ::applyEditor,
                    onSelect = { selectedId = it },
                    onToggleVisible = { id -> update(id) { fn -> fn.copy(visible = !fn.visible) } },
                    onToggleMode = { id -> update(id) { fn -> fn.copy(mode = fn.mode.toggled()) } },
                    onDelete = ::removeFunction,
                    onAdd = ::addFunction,
                    onOpenDomain = { domainDialogFor = selected.id },
                    onChar = { token -> applyEdit(MathEdit.insert(editor.text, editor.selection, token)) },
                    onOperator = { op -> applyEdit(MathEdit.insertOperator(editor.text, editor.selection, op)) },
                    onFunction = { name -> applyEdit(MathEdit.insertFunction(editor.text, editor.selection, name)) },
                    onPower = { applyEdit(MathEdit.insertPower(editor.text, editor.selection)) },
                    onBackspace = { applyEdit(MathEdit.backspace(editor.text, editor.selection)) },
                    onClear = { applyEditor(TextFieldValue("", TextRange(0))) },
                    onParamAdd = ::addParam,
                    onParamUpdate = ::updateParam,
                    onParamDelete = ::requestDeleteParam,
                    modifier = panelModifier,
                )
            }

            val plotPane: @Composable (Modifier) -> Unit = { plotModifier ->
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
                    modifier = plotModifier,
                )
            }

            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    editorPanel(Modifier.width(panelWidth).fillMaxHeight())
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    plotPane(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    plotPane(Modifier.fillMaxWidth().height(240.dp))
                    editorPanel(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    // 定义域对话框
    domainDialogFor?.let { fnId ->
        val fn = functions.firstOrNull { it.id == fnId }
        if (fn != null) {
            DomainDialog(
                initial = fn.domain,
                onDismiss = { domainDialogFor = null },
                onConfirm = { domain ->
                    update(fnId) { it.copy(domain = domain) }
                    domainDialogFor = null
                },
            )
        }
    }

    // 删除仍被表达式使用的滑块前先确认
    pendingParamDelete?.let { def ->
        AlertDialog(
            onDismissRequest = { pendingParamDelete = null },
            title = { Text("删除滑块？") },
            text = {
                Text("「${def.name}」仍出现在表达式里，删除后相关曲线暂时画不出来，可以随时再加回来。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteParam(def)
                        pendingParamDelete = null
                    },
                ) { Text("仍要删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingParamDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EditorPanel(
    functions: List<PlotFn>,
    selected: PlotFn,
    selectedIndex: Int,
    editor: TextFieldValue,
    params: List<ParamDef>,
    onEditorChange: (TextFieldValue) -> Unit,
    onSelect: (Int) -> Unit,
    onToggleVisible: (Int) -> Unit,
    onToggleMode: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenDomain: () -> Unit,
    onChar: (String) -> Unit,
    onOperator: (String) -> Unit,
    onFunction: (String) -> Unit,
    onPower: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onParamAdd: (ParamDef) -> Unit,
    onParamUpdate: (ParamDef) -> Unit,
    onParamDelete: (ParamDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = if (editor.text.isBlank()) {
        null
    } else {
        runCatching { parseCurve(selected.mode, editor.text) }.exceptionOrNull()?.message
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // 函数切换条：横向一排胶囊，不再用纵向列表吃掉半屏高度
        FunctionStrip(
            functions = functions,
            selectedId = selected.id,
            onSelect = onSelect,
            onToggleVisible = onToggleVisible,
            onAdd = onAdd,
        )

        Spacer(Modifier.height(6.dp))

        // 表达式编辑卡：模式/定义域/显隐/删除 + 输入框 + 固定报错栏 + 键盘，整块常驻可见
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeChip(
                        mode = selected.mode,
                        index = selectedIndex,
                        onClick = { onToggleMode(selected.id) },
                    )
                    Spacer(Modifier.width(6.dp))
                    DomainChip(domain = selected.domain, onClick = onOpenDomain)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onToggleVisible(selected.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text(
                            text = if (selected.visible) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onDelete(selected.id) },
                        enabled = functions.size > 1,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除 ${fnLabel(selectedIndex, selected.mode)}",
                            modifier = Modifier.size(15.dp),
                            tint = if (functions.size > 1) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                ExpressionField(
                    value = editor,
                    onValueChange = onEditorChange,
                    label = if (selected.mode == FnMode.EXPLICIT) {
                        "f${selectedIndex + 1}(x) ="
                    } else {
                        "F${selectedIndex + 1}(x,y):"
                    },
                    // 隐式模式下如果学生已经写了完整方程（含等号），就不再补「= 0」
                    suffix = if (selected.mode == FnMode.IMPLICIT && '=' !in editor.text) {
                        "= 0"
                    } else {
                        null
                    },
                    isError = error != null,
                )

                // 报错栏固定占位：出错时只换内容，不改变下方键盘的位置
                ErrorBar(message = error, mode = selected.mode)

                Spacer(Modifier.height(2.dp))

                MathKeypad(
                    onChar = onChar,
                    onOperator = onOperator,
                    onFunction = onFunction,
                    onPower = onPower,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // 剩下的高度全部给滑块，独立滚动；键盘已经固定在上面的卡片里，永远不会被挤掉
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                ParamPanel(
                    params = params,
                    onAdd = onParamAdd,
                    onUpdate = onParamUpdate,
                    onDelete = onParamDelete,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** 函数切换条：一排横向胶囊，显示模式、表达式预览、显隐与语法错误。 */
@Composable
private fun FunctionStrip(
    functions: List<PlotFn>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onToggleVisible: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(functions, key = { _, fn -> fn.id }) { index, fn ->
                FunctionChip(
                    fn = fn,
                    index = index,
                    selected = fn.id == selectedId,
                    hasError = fn.expr.isNotBlank() &&
                        runCatching { parseCurve(fn.mode, fn.expr) }.isFailure,
                    onSelect = { onSelect(fn.id) },
                    onToggleVisible = { onToggleVisible(fn.id) },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${functions.size}/$MAX_CURVES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onAdd,
            enabled = functions.size < MAX_CURVES,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (functions.size < MAX_CURVES) 1f else 0.4f,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (functions.size < MAX_CURVES) {
                        "添加函数"
                    } else {
                        "最多 $MAX_CURVES 条曲线"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 一条曲线的胶囊：颜色点（可切换显隐）+ 模式标签 + 表达式预览 + 错误标记。 */
@Composable
private fun FunctionChip(
    fn: PlotFn,
    index: Int,
    selected: Boolean,
    hasError: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
) {
    val color = Color(fn.colorArgb)
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) color.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clickable(
                        onClickLabel = if (fn.visible) "隐藏该曲线" else "显示该曲线",
                        onClick = onToggleVisible,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (fn.visible) color else Color.Transparent)
                        .border(1.5.dp, if (fn.visible) color else color.copy(alpha = 0.45f), CircleShape),
                )
            }
            Text(
                text = fnLabel(index, fn.mode),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = mathAnnotated(if (fn.expr.isBlank()) "（空）" else fn.expr),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (fn.visible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 156.dp),
            )
            if (hasError) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "表达式有错误",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** 显式/隐式切换胶囊：点一下就在 f(x) 与 F(x,y) 之间切换。 */
@Composable
private fun ModeChip(
    mode: FnMode,
    index: Int,
    onClick: () -> Unit,
) {
    val label = fnLabel(index, mode)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.semantics {
            contentDescription = if (mode == FnMode.EXPLICIT) {
                "当前是显式函数，点击切换为隐式方程"
            } else {
                "当前是隐式方程，点击切换为显式函数"
            }
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "⇄",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 定义域入口：平时显示「全体实数」，设置过之后显示具体区间。 */
@Composable
private fun DomainChip(domain: Domain, onClick: () -> Unit) {
    val accent = if (domain.isAll) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.semantics { contentDescription = "设置函数定义域" },
    ) {
        Text(
            text = "定义域：${formatDomainLabel(domain)}",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
        )
    }
}

/**
 * 表达式输入框：所见即所得的数学排版（上标、根号横线）都在显示层完成，
 * 这里只负责把光标位置、聚焦状态和画好的装饰对齐。
 */
@Composable
private fun ExpressionField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    suffix: String?,
    isError: Boolean,
) {
    val transformation = remember { MathVisualTransformation() }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }

    // 拖动光标手柄时，手势协程运行期间值会不断变化，用 rememberUpdatedState 保证读到最新状态
    val latestValue by rememberUpdatedState(value)
    val latestLayout by rememberUpdatedState(textLayout)
    val latestFocused by rememberUpdatedState(focused)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    /**
     * 自绘光标手柄的中心位置（文本布局坐标系）。
     * 无论光标在基线、上标还是根号里，手柄都固定落在这行文字的下方，方便按住拖动。
     */
    fun caretHandleCenter(verticalOffsetPx: Float): Offset? {
        if (!latestFocused || !latestValue.selection.collapsed) return null
        val layout = latestLayout ?: return null
        val display = transformation.display ?: return null
        val textLength = layout.layoutInput.text.length
        if (layout.lineCount <= 0) return null
        val transformed = display.originalToTransformed(latestValue.selection.end)
            .coerceIn(0, textLength)
        val line = layout.getLineForOffset(transformed.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
        return Offset(
            x = layout.getHorizontalPosition(transformed, usePrimaryDirection = true),
            y = layout.getLineBottom(line) + verticalOffsetPx,
        )
    }

    // 原生光标整行高，会在角标/根号里显得太长；隐藏它，改用自绘光标（高度跟着编辑区走）
    val caretTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by caretTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )
    val caretColor = MaterialTheme.colorScheme.primary

    // 这个工具自带了数学键盘，系统输入法一弹出来就会把键盘和图像盖住，所以聚焦时直接收起 IME。
    val keyboard = LocalSoftwareKeyboardController.current

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val slotColor = MaterialTheme.colorScheme.outline

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        color = Color.Transparent,
    ) {
        Row(
            // 上下留白按「手柄区高度 + 行高」对称分配，让文字在圆角框里垂直居中；
            // 底部额外空间同时作为光标手柄的拖拽热区。
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
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
                    .pointerInput(Unit) {
                        // 原生手柄在上标区域不可靠，这里自己接管「按住手柄拖动」：
                        // 只有按在手柄附近才消费事件，其它位置的点击/长按仍然交给输入框原生处理。
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            val center = caretHandleCenter(verticalOffsetPx = 10.dp.toPx())
                                ?: return@awaitEachGesture
                            if ((down.position - center).getDistance() > 26.dp.toPx()) {
                                return@awaitEachGesture
                            }
                            down.consume()

                            fun moveCaretTo(position: Offset) {
                                val layout = latestLayout ?: return
                                val display = transformation.display ?: return
                                val transformed = layout.getOffsetForPosition(position)
                                val original = display.mapping.transformedToOriginal(transformed)
                                    .coerceIn(0, latestValue.text.length)
                                latestOnValueChange(latestValue.copy(selection = TextRange(original)))
                            }

                            moveCaretTo(down.position)
                            drag(down.id) { change ->
                                change.consume()
                                moveCaretTo(change.position)
                            }
                        }
                    }
                    .padding(bottom = 16.dp)
                    .drawBehind {
                        drawMathDecorations(
                            layout = textLayout,
                            display = transformation.display,
                            caret = value.selection.end,
                            lineColor = lineColor,
                            slotColor = slotColor,
                        )
                        if (focused && value.selection.collapsed) {
                            drawMathCaret(
                                layout = textLayout,
                                display = transformation.display,
                                originalCaret = value.selection.start,
                                color = caretColor,
                                alpha = caretAlpha,
                            )
                            // 手柄不跟着闪烁，始终可见、可按住拖动
                            drawMathCaretHandle(
                                layout = textLayout,
                                display = transformation.display,
                                originalCaret = value.selection.start,
                                color = caretColor.copy(alpha = 0.85f),
                            )
                        }
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
                    // 允许多行换行而不是横向滚动：横向滚动时上标/根号装饰会跑到视野外
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    // 光标由 drawMathCaret 自绘，这里保持透明
                    cursorBrush = SolidColor(Color.Transparent),
                    visualTransformation = transformation,
                    onTextLayout = { textLayout = it },
                )
            }
            if (suffix != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 报错栏：高度固定，出错时只替换文案。
 * 这样键盘区不会因为报错出现/消失而上下跳动，连续输入的手感才稳定。
 */
@Composable
private fun ErrorBar(message: String?, mode: FnMode) {
    val hint = when (mode) {
        FnMode.EXPLICIT -> "按 √ 开方、^ 乘方，点 f(x) 可切换为隐式方程"
        FnMode.IMPLICIT -> "隐式方程 F(x,y)=0，例如 x^2+y^2=25"
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (message != null) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "错误",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                            val prefix = if (item.mode == FnMode.EXPLICIT) {
                                "f${item.number}"
                            } else {
                                "F${item.number}"
                            }
                            Text(
                                text = mathAnnotated("$prefix = ${item.expr.ifBlank { "—" }}"),
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
 * 每条曲线的定义域在采样前裁剪，所以「只在某段画」不需要额外的遮罩。
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
        runCatching { parseCurve(fn.mode, fn.expr) }.getOrNull()?.let { fn to it }
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
            val (drawMin, drawMax) = fn.domain.xRange(xMin, xMax)
            val segments = if (drawMax <= drawMin) {
                emptyList()
            } else {
                when (def) {
                    is CurveDef.Explicit -> def.f.sampleSegments(drawMin, drawMax, 700, params, yMin, yMax)
                    is CurveDef.Implicit -> sampleImplicit(
                        f = { x, y -> def.f.eval(x, params, y) },
                        xMin = drawMin,
                        xMax = drawMax,
                        yMin = yMin,
                        yMax = yMax,
                    )
                }
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
