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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolIcon
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
        icon = ToolIcon.FUNCTION,
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

    // 输入框此刻是否持有焦点；切换曲线时据此决定要不要把焦点交给新曲线的输入框，
    // 否则一切换光标就「消失」，得再点一下才能继续拖。
    var editorFocused by remember { mutableStateOf(false) }
    var focusOnSwitch by remember { mutableStateOf(false) }

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
        // 表达式里若还带着 {…}（老状态或粘贴进来的），这里先摘掉：定义域单独用尾部小字显示
        val base = DomainSuffix.strip(selected.expr)
        mutableStateOf(TextFieldValue(base, TextRange(base.length)))
    }

    fun update(id: Int, transform: (PlotFn) -> PlotFn) {
        functions = functions.map { if (it.id == id) transform(it) else it }
    }

    /**
     * 写入某条曲线的表达式。
     *
     * [id] 是「这个输入框创建时属于哪条曲线」，必须显式带上：切换曲线的那一刻，
     * 旧输入框还可能回调一次（点击、松开手柄都会触发），只认当前 selectedId 的话，
     * 就会把上一条曲线的文字写进新选中的曲线——看起来就像「函数被换成了另一个函数」。
     */
    fun applyEditor(id: Int, value: TextFieldValue) {
        if (id != selectedId) return
        editor = value
        // 只是挪光标、文字没变时不要动模型：否则每拖一下光标都要重算整张图。
        // 这里读 functions 里的当前值（而不是本次组合捕获的 selected），不会因为回调晚到而误判。
        val current = functions.firstOrNull { it.id == id } ?: return
        // 在末尾写完 {0,10} 就当场「提交」：从正文里摘掉，存进 domain，用尾部小字显示。
        // 留在正文里会有两个毛病：公式里夹着一串花括号，退格时还容易连带删掉。
        val suffix = DomainSuffix.parse(value.text)
        if (suffix.domain != null && !suffix.domain.isAll && value.selection.end == value.text.length) {
            editor = TextFieldValue(suffix.expr, TextRange(suffix.expr.length))
            update(id) { it.copy(expr = suffix.expr, domain = suffix.domain) }
            return
        }
        if (current.expr != value.text) {
            update(id) { it.copy(expr = value.text) }
        }
    }

    fun applyEdit(edit: MathEdit.Edit) = applyEditor(selected.id, edit.toTextFieldValue())

    /** 切换曲线：记住「刚才是不是正在输入」，新曲线的输入框据此决定要不要接住焦点。 */
    fun selectFunction(id: Int) {
        if (id == selectedId) return
        focusOnSwitch = editorFocused
        selectedId = id
    }

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

    // 表达式里新出现的自由变量自动生成滑块；用户删过的变量不再自动塞回来。
    // 反过来，自动滑块对应的字母已经从所有可见曲线里消失时也一并销毁。
    // 判断用原始文本而不是解析结果：输入到一半的「a*」解析会失败，但字母 a 还在，
    // 这时不能把滑块删掉，否则重新输入完整表达式后滑块的取值和范围全丢了。
    // 手动添加的滑块不动，它们可能是为还没输完的表达式预留的。
    val activeCurves = remember(functions) {
        functions.filter { it.visible }.mapNotNull { fn ->
            runCatching { parseCurve(fn.mode, fn.expr) }.getOrNull()?.let { fn to it }
        }
    }
    val freeVars = remember(activeCurves) {
        activeCurves.flatMap { it.second.variables() }.filter { it != "x" && it != "y" }.distinct()
    }
    val visibleExprs = remember(functions) {
        functions.filter { it.visible }.map { DomainSuffix.strip(it.expr) }
    }
    LaunchedEffect(freeVars, visibleExprs, dismissedVars, params.size) {
        val kept = params.filterNot { param ->
            param.auto && visibleExprs.none { expr -> containsVariable(expr, param.name) }
        }
        val missing = freeVars.filter { name ->
            kept.none { it.name == name } && name !in dismissedVars && validateParamName(name, kept) == null
        }
        val room = MAX_SLIDERS - kept.size
        val added = if (missing.isNotEmpty() && room > 0) {
            missing.take(room).map { ParamDef(name = it, value = 1.0, auto = true) }
        } else {
            emptyList()
        }
        val next = kept + added
        if (next != params) params = next
    }

    val paramsMap = remember(params) { params.associate { it.name to it.value } }

    val legend = remember(functions) {
        functions.mapIndexedNotNull { index, fn ->
            if (!fn.visible) return@mapIndexedNotNull null
            if (runCatching { parseCurve(fn.mode, fn.expr) }.isFailure) return@mapIndexedNotNull null
            LegendItem(fn.colorArgb, DomainSuffix.strip(fn.expr), index + 1, fn.mode, fn.effectiveDomain())
        }
    }

    val plot = remember(functions, xCenter, xSpan, yCenter, params, aspect) {
        composePlot(functions, xCenter, xSpan, yCenter, paramsMap, aspect.toDouble())
    }

    fun addFunction() {
        if (functions.size >= MAX_CURVES) return
        // 用现有最大 id + 1：nextId 只活在内存里，进程被回收后从 0 重来，
        // 会和恢复出来的曲线撞 id，撞了就会「改一条、两条一起变」。
        val id = (functions.maxOfOrNull { it.id } ?: 0) + 1
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
        selectFunction(id)
    }

    fun removeFunction(id: Int) {
        if (functions.size <= 1) return
        val remaining = functions.filterNot { it.id == id }
        functions = remaining
        if (selectedId == id) selectFunction(remaining.first().id)
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
        val focusManager = LocalFocusManager.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 点空白处（图像区、面板空地）就退出编辑：光标和手柄一起收起来。
                // 键盘、胶囊、滑块这些可点组件会消费事件，所以不会误触发。
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        editorFocused = false
                    })
                },
        ) {
            val wide = maxWidth >= 720.dp
            val panelWidth = if (maxWidth >= 1160.dp) 468.dp else 412.dp

            val editorPanel: @Composable (Modifier) -> Unit = { panelModifier ->
                EditorPanel(
                    functions = functions,
                    selected = selected,
                    selectedIndex = selectedIndex,
                    editor = editor,
                    params = params,
                    editing = editorFocused,
                    autoFocus = focusOnSwitch,
                    onEditorChange = { value -> applyEditor(selected.id, value) },
                    onEditingChange = { editorFocused = it },
                    onSelect = ::selectFunction,
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
                    onClear = { applyEditor(selected.id, TextFieldValue("", TextRange(0))) },
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
                initial = fn.effectiveDomain(),
                onDismiss = { domainDialogFor = null },
                onConfirm = { domain ->
                    // 定义域只是模型里的一个字段，不再写进表达式正文（正文里保持干净公式）
                    val newExpr = DomainSuffix.strip(fn.expr)
                    update(fnId) { it.copy(expr = newExpr, domain = domain) }
                    // 正在编辑的就是这条曲线：把输入框里的文字一起换掉，否则下次敲键盘会用旧文字覆盖
                    if (fnId == selectedId) {
                        editor = TextFieldValue(newExpr, TextRange(newExpr.length))
                    }
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
    editing: Boolean,
    autoFocus: Boolean,
    onEditorChange: (TextFieldValue) -> Unit,
    onEditingChange: (Boolean) -> Unit,
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
            .fillMaxSize()
            // 整块面板可滚动：曲线多了、窗口矮了都能滑到底，键盘不会被裁掉
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // 函数列表：和 1.0.0 一样竖着排，一条曲线一行
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

        Spacer(Modifier.height(2.dp))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            functions.forEachIndexed { index, fn ->
                FunctionRow(
                    fn = fn,
                    index = index,
                    selected = fn.id == selected.id,
                    hasError = fn.expr.isNotBlank() &&
                        runCatching { parseCurve(fn.mode, fn.expr) }.isFailure,
                    canDelete = functions.size > 1,
                    onSelect = { onSelect(fn.id) },
                    onToggleVisible = { onToggleVisible(fn.id) },
                    onDelete = { onDelete(fn.id) },
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        OutlinedButton(
            onClick = onAdd,
            enabled = functions.size < MAX_CURVES,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (functions.size < MAX_CURVES) "添加函数" else "最多 $MAX_CURVES 条曲线",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(Modifier.height(8.dp))

        // 当前曲线的编辑卡：模式/定义域/显隐/删除 + 输入框 + 固定报错栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModeChip(
                        mode = selected.mode,
                        index = selectedIndex,
                        onClick = { onToggleMode(selected.id) },
                    )
                    Spacer(Modifier.width(6.dp))
                    DomainChip(domain = selected.effectiveDomain(), onClick = onOpenDomain)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onToggleVisible(selected.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp),
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
                        modifier = Modifier.size(26.dp),
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

                Spacer(Modifier.height(2.dp))

                KeyedExpressionField(
                    fnId = selected.id,
                    value = editor,
                    onValueChange = onEditorChange,
                    label = if (selected.mode == FnMode.EXPLICIT) {
                        "f${selectedIndex + 1}(x) ="
                    } else {
                        "F${selectedIndex + 1}(x,y):"
                    },
                    // 尾部装饰：定义域不是全体实数时，在输入框右端显示 {0,10} 这样的标记。
                    // （正文里不再夹着花括号，公式保持干净，定义域也不会被误删改）
                    suffix = selected.effectiveDomain()
                        .takeIf { !it.isAll }
                        ?.let { DomainSuffix.label(it) },
                    isError = error != null,
                    editing = editing,
                    autoFocus = autoFocus,
                    onEditingChange = onEditingChange,
                )

                // 报错栏固定占位：出错时只换内容，不改变下方键盘的位置
                ErrorBar(message = error, mode = selected.mode)
            }
        }

        Spacer(Modifier.height(6.dp))

        // 键盘和 1.0.0 一样直接铺在面板上，不塞进卡片里
        MathKeypad(
            onChar = onChar,
            onOperator = onOperator,
            onFunction = onFunction,
            onPower = onPower,
            onBackspace = onBackspace,
            onClear = onClear,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

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

/** 光标手柄的命中半径：比画出来的水滴（5dp）稍大一点，手指按住不容易滑掉。 */
private val handleHitRadius = 12.dp

/**
 * 一条曲线占一行：颜色点（点一下切换显隐）+ 编号 + 表达式预览 + 定义域小字 + 错误标记 + 删除。
 */
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(
                        onClickLabel = if (fn.visible) "隐藏该曲线" else "显示该曲线",
                        onClick = onToggleVisible,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (fn.visible) color else Color.Transparent)
                        .border(1.5.dp, if (fn.visible) color else color.copy(alpha = 0.45f), CircleShape),
                )
            }
            Text(
                text = fnLabel(index, fn.mode),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = mathAnnotated(
                    if (fn.expr.isBlank()) "（空）" else DomainSuffix.strip(fn.expr),
                ),
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
            // 定义域不是全体实数时，跟在表达式后面用 {…} 标出来（对话框设的、自己输入的都算）
            if (!fn.effectiveDomain().isAll) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = DomainSuffix.label(fn.effectiveDomain()),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (hasError) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "表达式有错误",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除 ${fnLabel(index, fn.mode)}",
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
 * 给输入框套一层 [key]：换曲线就整块重建。
 *
 * BasicTextField 内部还存着一份自己的文本、光标、输入法会话和手势节点，光把外面的
 * TextFieldValue 换掉是换不干净的：切换曲线后旧的光标位置、旧的手势回调会留下来，
 * 表现就是「切完曲线，点输入框光标不动，还会把另一条曲线的表达式写坏」。
 * 按曲线 id 重建是最省事也最彻底的做法——新曲线永远是一个干净的输入框。
 */
@Composable
private fun KeyedExpressionField(
    fnId: Int,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    suffix: String?,
    isError: Boolean,
    editing: Boolean,
    autoFocus: Boolean,
    onEditingChange: (Boolean) -> Unit,
) {
    key(fnId) {
        // 把系统原生的选区高亮和光标手柄设成透明：它们比自绘的大一圈，
        // 会盖住角标/根号，圆点还会伸出输入框边框。拖动光标统一用自绘的那个。
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Color.Transparent,
                backgroundColor = Color.Transparent,
            ),
        ) {
            ExpressionField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                suffix = suffix,
                isError = isError,
                editing = editing,
                autoFocus = autoFocus,
                onEditingChange = onEditingChange,
            )
        }
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
    editing: Boolean,
    autoFocus: Boolean,
    onEditingChange: (Boolean) -> Unit,
) {
    val transformation = remember { MathVisualTransformation() }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    // 双击输入框 = 唤起系统键盘（粘贴、输入键位上没有的符号）；点空白处退出编辑时一并收回。
    // 平时字段是禁用的（原生光标手柄/放大镜/工具条都不出现），只有 IME 模式才临时启用它。
    var imeMode by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(imeMode) {
        if (imeMode) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    // 退出编辑（点空白、切走）时把系统键盘一并收回
    LaunchedEffect(editing) {
        if (!editing && imeMode) {
            imeMode = false
            keyboard?.hide()
        }
    }

    // 切换曲线时输入框是新建的，编辑状态本来会掉；等这一帧排完版再接回来，
    // 光标就一直停在正在编辑的那条曲线上，不用每次切换都点一下输入框。
    LaunchedEffect(Unit) {
        if (autoFocus) {
            withFrameNanos { }
            onEditingChange(true)
        }
    }

    // 拖动光标手柄时，手势协程运行期间值会不断变化，用 rememberUpdatedState 保证读到最新状态
    val latestValue by rememberUpdatedState(value)
    val latestLayout by rememberUpdatedState(textLayout)
    val latestEditing by rememberUpdatedState(editing)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    /**
     * 光标手柄圆心（输入框坐标系），与 drawMathCaretHandle 画圆用的坐标保持一致：
     * 光标 x、行底 + 10dp。手势命中判定以此为中心，热区半径取 handleTouchSize 的一半。
     *
     * 读的是 rememberUpdatedState 的最新值：pointerInput(Unit) 的协程不会随重组重启，
     * 直接捕获 composed 的 value / focused / textLayout 会一直停在首帧。
     * 没聚焦、有选区、还没排版时返回 null。
     */
    fun currentHandleCenter(): Offset? {
        if (!latestEditing || !latestValue.selection.collapsed) return null
        val layout = latestLayout ?: return null
        val display = transformation.display ?: return null
        val textLength = layout.layoutInput.text.length
        if (layout.lineCount <= 0) return null
        val transformed = display.originalToTransformed(latestValue.selection.end)
            .coerceIn(0, textLength)
        val line = layout.getLineForOffset(transformed.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
        return Offset(
            x = layout.getHorizontalPosition(transformed, usePrimaryDirection = true),
            y = layout.getLineBottom(line) + with(density) { 10.dp.toPx() },
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

    // 四个编辑区各一套颜色：光标、手柄、区域高亮都用同一色，看到颜色就知道在哪一层。
    // 基线用中性色（和正文同色，最不抢眼），上标用 tertiary、根号内用 primary、绝对值用 secondary。
    val caretColor = MaterialTheme.colorScheme.onSurface
    val scriptColor = MaterialTheme.colorScheme.tertiary
    val radicalColor = MaterialTheme.colorScheme.primary
    val absColor = MaterialTheme.colorScheme.secondary
    val labelSurface = MaterialTheme.colorScheme.surface
    val labelMeasurer = rememberTextMeasurer()

    // 是否正在按住手柄拖动：只在拖动时浮出「基线 / 上标 / 根号内 / 绝对值」提示
    var draggingHandle by remember { mutableStateOf(false) }

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        editing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val slotColor = MaterialTheme.colorScheme.outline

    // 这里故意不用 Surface：Surface 会按圆角把内容裁掉，光标手柄就只能憋在框里、
    // 被输入框的高度牵着走。改成手动画一圈边框，内容不裁剪——手柄可以「浮」在框外，
    // 大小和位置只由文字行高决定。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 边框自己画，不用 Modifier.border：那是画在内容「之上」的，
            // 手柄探出框外时会被这条线横切一刀。
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    ) {
        Row(
            // 上下留白对称：上面 11dp、下面 11dp + 2dp，公式在框里垂直居中
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        // 输入框自己接管全部触摸：文本字段收不到手势，就不会长按选词、弹系统放大镜和
                        // 浮动工具条（在数学排版里它们只会盖住角标和根号）。点哪里就把光标放哪里。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            onEditingChange(true)

                            val handle = currentHandleCenter()
                            val onHandle = handle != null &&
                                (down.position - handle).getDistance() <= handleHitRadius.toPx()
                            draggingHandle = onHandle
                            // 双击（不要按在手柄上）= 唤起/收回系统键盘
                            if (!onHandle) {
                                val now = System.currentTimeMillis()
                                if (now - lastTapAt < 400 && (down.position - lastTapPos).getDistance() <= 40.dp.toPx()) {
                                    imeMode = !imeMode
                                    if (!imeMode) keyboard?.hide()
                                }
                                lastTapAt = now
                                lastTapPos = down.position
                            }

                            fun moveCaretTo(position: Offset) {
                                val layout = latestLayout ?: return
                                val display = transformation.display ?: return
                                val transformed = layout.getOffsetForPosition(position)
                                val original = display.mapping.transformedToOriginal(transformed)
                                    .coerceIn(0, latestValue.text.length)
                                latestOnValueChange(latestValue.copy(selection = TextRange(original)))
                            }

                            try {
                                // 按在手柄上属于「拖光标」，不要把光标先跳到手指位置
                                if (!onHandle) moveCaretTo(down.position)
                                drag(down.id) { change ->
                                    change.consume()
                                    moveCaretTo(change.position)
                                }
                            } finally {
                                draggingHandle = false
                            }
                        }
                    }
                    .padding(bottom = 11.dp)
                    .drawBehind {
                        val display = transformation.display
                        // 没聚焦（或选了区间）时不摆出区域高亮：那是「光标在哪」的提示，没有光标就没有意义
                        val region = if (editing && value.selection.collapsed) {
                            display?.regionAt(value.selection.end) ?: MathEditRegion.BASE
                        } else {
                            MathEditRegion.BASE
                        }
                        val regionColor = when (region) {
                            MathEditRegion.BASE -> caretColor
                            MathEditRegion.SCRIPT -> scriptColor
                            MathEditRegion.RADICAL -> radicalColor
                            MathEditRegion.ABS -> absColor
                        }

                        drawMathDecorations(
                            layout = textLayout,
                            display = display,
                            caret = value.selection.end,
                            region = region,
                            regionColor = regionColor,
                            lineColor = lineColor,
                            slotColor = slotColor,
                        )
                        if (editing && value.selection.collapsed) {
                            drawMathCaret(
                                layout = textLayout,
                                display = display,
                                originalCaret = value.selection.start,
                                region = region,
                                color = regionColor,
                                alpha = caretAlpha,
                            )
                            // 手柄不跟着闪烁，始终可见、可按住拖动
                            drawMathCaretHandle(
                                layout = textLayout,
                                display = display,
                                originalCaret = value.selection.start,
                                color = regionColor.copy(alpha = 0.85f),
                            )
                        }
                    }
                    // 拖动时才浮出的「基线 / 上标 / 根号内」提示，必须画在文字上面
                    .drawWithContent {
                        drawContent()
                        if (!draggingHandle || !editing || !value.selection.collapsed) return@drawWithContent
                        val display = transformation.display ?: return@drawWithContent
                        val region = display.regionAt(value.selection.end)
                        drawMathDragLabel(
                            layout = textLayout,
                            display = display,
                            originalCaret = value.selection.start,
                            region = region,
                            color = when (region) {
                                MathEditRegion.BASE -> caretColor
                                MathEditRegion.SCRIPT -> scriptColor
                                MathEditRegion.RADICAL -> radicalColor
                                MathEditRegion.ABS -> absColor
                            },
                            surfaceColor = labelSurface,
                            measurer = labelMeasurer,
                        )
                    },
            ) {
                BasicTextField(
                    value = value,
                    // 平时字段禁用：触摸与输入全部由外层接管（数学键盘 + 自绘光标），
                    // 原生光标手柄/放大镜/浮动工具条都不会出现；
                    // 双击进入 IME 模式后才临时启用，让系统键盘能往里打字。
                    enabled = imeMode,
                    onValueChange = { newValue ->
                        // 换行会把表达式拆成两行文本，直接吞掉（软键盘已隐藏，只有实体键盘会发 Enter）
                        val text = if ('\n' in newValue.text) newValue.text.replace("\n", "") else newValue.text
                        val caret = newValue.selection.end.coerceIn(0, text.length)
                        // 关掉系统原生的选区拖动：它的高亮块和两个大圆手柄会把角标、根号盖住，
                        // 而且数学表达式里选一段再整体替换的用处不大。光标一律收成一点，
                        // 想移动就拖我们自绘的手柄。
                        onValueChange(TextFieldValue(text, TextRange(caret), newValue.composition))
                    },
                    modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
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
        FnMode.EXPLICIT -> "按 √ 开方、^ 乘方，双击输入框可唤起系统键盘，点 f(x) 可切换为隐式方程"
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
                // 画布自己裁剪：曲线超出 y 范围（tan、1/x 这类）时坐标会跑到画布外，
                // 不裁剪就会盖上顶部标题栏、编辑面板（分屏、平板布局里尤其明显）。
                .clipToBounds()
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
                            // 定义域跟着函数一起显示，和函数列表、输入框里的 {…} 是同一份信息
                            if (!item.domain.isAll) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = DomainSuffix.label(item.domain),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                )
                            }
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
 * 每条曲线只在「定义域 ∩ 当前 x 窗口」里采样：定义域落在视野之外时一个点都不算，
 * 否则会得到一串画布外的坐标，曲线跑到绘图区外面去。
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
            val (drawMin, drawMax) = fn.effectiveDomain().xRange(xMin, xMax)
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

/** 表达式文本里是否还有名为 [name] 的独立标识符，用来判断自动滑块对应的字母是否已经删掉。 */
private fun containsVariable(expr: String, name: String): Boolean {
    if (name.isEmpty()) return false
    val pattern = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(name)}(?![\\p{L}\\p{N}_])")
    return pattern.containsMatchIn(expr)
}
