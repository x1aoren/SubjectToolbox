package cn.stb.feature.math

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/* ------------------------------------------------------------------
 * 参数滑块面板
 * ---------------------------------------------------------------- */

/**
 * 参数滑块面板。
 *
 * 面板只负责「怎么调参数」，参数列表本身由调用方持有（手动添加的和表达式里自动发现的
 * 自由变量都算参数）。添加/编辑对话框在内部管理：输入校验、非法值提示、确定按钮可用性
 * 全在这里处理完，只有合法的 [ParamDef] 才会通过 [onAdd] / [onUpdate] 回到调用方。
 *
 * @param params 当前参数列表，顺序即显示顺序
 * @param onAdd 添加一个滑块；只会传 auto = false 的手动参数
 * @param onUpdate 某个滑块的值或范围、步进变了
 * @param onDelete 删除某个滑块（自动发现的滑块也可以删）
 * @param modifier 外部布局修饰符
 */
@Composable
internal fun ParamPanel(
    params: List<ParamDef>,
    onAdd: (ParamDef) -> Unit,
    onUpdate: (ParamDef) -> Unit,
    onDelete: (ParamDef) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 对话框状态自己管：Boolean 能直接 rememberSaveable，旋转后对话框不会莫名消失；
    // 编辑目标只记变量名，参数被删掉时对话框自动收起。
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = params.firstOrNull { it.name == editingName }
    val canAdd = params.size < MAX_SLIDERS

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "滑块",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${params.size}/$MAX_SLIDERS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { adding = true },
                enabled = canAdd,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text("＋ 添加", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (!canAdd) {
            Text(
                text = "最多 $MAX_SLIDERS 个滑块，删除一个才能继续添加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (params.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = "表达式里输入 a、b、k 等字母会自动生成滑块，也可以手动添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                params.forEach { param ->
                    ParamSliderCard(
                        param = param,
                        onValueChange = { value -> onUpdate(param.copy(value = value)) },
                        onEdit = { editingName = param.name },
                        onDelete = { onDelete(param) },
                    )
                }
            }
        }
    }

    if (adding && canAdd) {
        AddParamDialog(
            existing = params,
            onDismiss = { adding = false },
            onConfirm = { param ->
                adding = false
                onAdd(param)
            },
        )
    }

    if (editing != null) {
        EditParamDialog(
            param = editing,
            onDismiss = { editingName = null },
            onConfirm = { updated ->
                editingName = null
                onUpdate(updated)
            },
        )
    }
}

/* ------------------------------------------------------------------
 * 单张卡片
 * ---------------------------------------------------------------- */

/**
 * 一张参数卡片：名字 + 当前值 + 设置/删除按钮 + 滑杆 + 两端范围。
 *
 * 当前值本身就是编辑入口（点一下就打开设置对话框）：调范围比拖滑杆更常用，
 * 而滑杆只能改值、改不了范围。
 */
@Composable
private fun ParamSliderCard(
    param: ParamDef,
    onValueChange: (Double) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val (rangeStart, rangeEnd) = param.sliderBounds()
    // 脏数据（NaN）不应该让 Slider 崩掉，落在端点上也比不显示强
    val sliderValue = (if (param.value.isFinite()) param.value else rangeStart)
        .coerceIn(rangeStart, rangeEnd)
        .toFloat()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = param.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (param.auto) {
                    AutoBadge()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = formatParamValue(param.value, param.step),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClickLabel = "设置滑块 ${param.name}") { onEdit() }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "设置滑块 ${param.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除滑块 ${param.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Slider(
                value = sliderValue,
                onValueChange = { raw ->
                    onValueChange(snapToStep(raw.toDouble(), rangeStart, rangeEnd, param.step))
                },
                valueRange = rangeStart.toFloat()..rangeEnd.toFloat(),
                steps = param.sliderSteps(rangeStart, rangeEnd),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatMathNumber(rangeStart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "步进 ${formatMathNumber(param.step)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatMathNumber(rangeEnd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 「自动」小标签：说明这个滑块是表达式里的自由变量自动变出来的，不是手动添加的。 */
@Composable
private fun AutoBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "自动",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/**
 * 滑块实际可用的闭区间。
 *
 * 正常参数都满足 min < max（添加/编辑对话框校验过）。这里仍兜底一次：
 * Slider 不允许空区间，数据真坏了就退回默认 -5..5，至少界面不会崩。
 */
private fun ParamDef.sliderBounds(): Pair<Double, Double> {
    if (!min.isFinite() || !max.isFinite() || min >= max) return -5.0 to 5.0
    val start = min.toFloat()
    val end = max.toFloat()
    return if (start.isFinite() && end.isFinite() && start < end) min to max else -5.0 to 5.0
}

/**
 * Slider 的刻度数：区间按步进切成 n 格，中间可停靠的刻度是 n-1 个。
 *
 * 步进填得离谱小时不真画上万根刻度线——Material3 会为每个刻度绘制一根，滑一下界面就卡死，
 * 所以上限兜底到 1000。
 */
private fun ParamDef.sliderSteps(start: Double, end: Double): Int {
    if (!step.isFinite() || step <= 0) return 0
    val intervals = ((end - start) / step).roundToInt() - 1
    return intervals.coerceIn(0, 1000)
}

/* ------------------------------------------------------------------
 * 添加 / 编辑对话框
 * ---------------------------------------------------------------- */

/**
 * 添加滑块对话框。
 *
 * 五个字段全部手动填，名字用 [validateParamName]（重名/保留字/非字母）、数值用
 * [validateParamRange]（区间、步进、当前值）实时校验；有错时确定按钮点不动。
 */
@Composable
private fun AddParamDialog(
    existing: List<ParamDef>,
    onDismiss: () -> Unit,
    onConfirm: (ParamDef) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var valueText by rememberSaveable { mutableStateOf("1") }
    var minText by rememberSaveable { mutableStateOf("-5") }
    var maxText by rememberSaveable { mutableStateOf("5") }
    var stepText by rememberSaveable { mutableStateOf("0.1") }

    val nameError = validateParamName(name, existing)
    val value = parseParamNumber(valueText)
    val min = parseParamNumber(minText)
    val max = parseParamNumber(maxText)
    val step = parseParamNumber(stepText)
    val rangeError = when {
        min == null || max == null || step == null -> "最小值、最大值、步进都要填数字"
        value == null -> "初始值要填数字"
        else -> validateParamRange(min, max, step, value)
    }
    val canConfirm = nameError == null && rangeError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加滑块") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("变量名") },
                    placeholder = { Text("如 a、k") },
                    singleLine = true,
                    // 名字还空着时只禁用确定，不先甩一句红字打扰人
                    isError = name.isNotBlank() && nameError != null,
                    supportingText = if (name.isNotBlank() && nameError != null) {
                        { Text(text = nameError) }
                    } else {
                        null
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    shape = RoundedCornerShape(12.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamNumberField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = "最小值",
                        modifier = Modifier.weight(1f),
                        isError = min == null,
                    )
                    ParamNumberField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = "最大值",
                        modifier = Modifier.weight(1f),
                        isError = max == null,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamNumberField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = "初始值",
                        modifier = Modifier.weight(1f),
                        isError = value == null,
                    )
                    ParamNumberField(
                        value = stepText,
                        onValueChange = { stepText = it },
                        label = "步进",
                        modifier = Modifier.weight(1f),
                        isError = step == null,
                    )
                }

                if (rangeError != null) {
                    Text(
                        text = rangeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = parseParamNumber(valueText)
                    val lo = parseParamNumber(minText)
                    val hi = parseParamNumber(maxText)
                    val st = parseParamNumber(stepText)
                    if (v != null && lo != null && hi != null && st != null) {
                        onConfirm(
                            ParamDef(
                                name = name.trim(),
                                value = v,
                                min = lo,
                                max = hi,
                                step = st,
                                auto = false,
                            ),
                        )
                    }
                },
                enabled = canConfirm,
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 编辑滑块对话框。
 *
 * 变量名只读——改名等于换一个参数，已经写进表达式里的名字会对不上。
 * 区间、步进、当前值都可以改；确定前用 [ParamDef.clampedToRange] 把当前值收进新区间。
 */
@Composable
private fun EditParamDialog(
    param: ParamDef,
    onDismiss: () -> Unit,
    onConfirm: (ParamDef) -> Unit,
) {
    var valueText by rememberSaveable { mutableStateOf(formatParamValue(param.value, param.step)) }
    var minText by rememberSaveable { mutableStateOf(editNumberText(param.min)) }
    var maxText by rememberSaveable { mutableStateOf(editNumberText(param.max)) }
    var stepText by rememberSaveable { mutableStateOf(editNumberText(param.step)) }

    val value = parseParamNumber(valueText)
    val min = parseParamNumber(minText)
    val max = parseParamNumber(maxText)
    val step = parseParamNumber(stepText)
    val rangeError = when {
        min == null || max == null || step == null -> "最小值、最大值、步进都要填数字"
        value == null -> "当前值要填数字"
        else -> validateParamRange(min, max, step, value)
    }
    val canConfirm = rangeError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置滑块") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "变量名",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = param.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (param.auto) {
                            Spacer(Modifier.width(6.dp))
                            AutoBadge()
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamNumberField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = "最小值",
                        modifier = Modifier.weight(1f),
                        isError = min == null,
                    )
                    ParamNumberField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = "最大值",
                        modifier = Modifier.weight(1f),
                        isError = max == null,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamNumberField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = "当前值",
                        modifier = Modifier.weight(1f),
                        isError = value == null,
                    )
                    ParamNumberField(
                        value = stepText,
                        onValueChange = { stepText = it },
                        label = "步进",
                        modifier = Modifier.weight(1f),
                        isError = step == null,
                    )
                }

                if (rangeError != null) {
                    Text(
                        text = rangeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = parseParamNumber(valueText)
                    val lo = parseParamNumber(minText)
                    val hi = parseParamNumber(maxText)
                    val st = parseParamNumber(stepText)
                    if (v != null && lo != null && hi != null && st != null) {
                        // 当前值可能落在新的区间外：收敛进区间再回调，滑杆不会指到范围外
                        onConfirm(
                            param.copy(value = v, min = lo, max = hi, step = st).clampedToRange(),
                        )
                    }
                },
                enabled = canConfirm,
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 一个只认普通小数的窄输入框（最小值/最大值/当前值/步进共用）。 */
@Composable
private fun ParamNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        shape = RoundedCornerShape(12.dp),
    )
}

/** 参数对话框里的数字输入：只认普通小数，不支持 pi 之类的常数表达式——规则越简单学生越好填。 */
private fun parseParamNumber(raw: String): Double? = raw.trim().toDoubleOrNull()

/** 把参数回填到输入框：去掉 Double.toString 多余的 .0，同时保证文本能原样解析回来。 */
private fun editNumberText(value: Double): String {
    val text = value.toString()
    if ('e' in text || 'E' in text) return text
    return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
