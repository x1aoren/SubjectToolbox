package cn.stb.feature.math

import androidx.compose.runtime.saveable.Saver
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.round

/* ------------------------------------------------------------------
 * 曲线模型
 * ---------------------------------------------------------------- */

/**
 * 函数模式：显式 y=f(x) 与隐式 F(x,y)=0。
 *
 * 由用户手动切换（点击函数行或编辑区前面的标签），不再根据「有没有等号」猜——
 * 猜的结果是学生想画圆却画出一条竖线，且没有明显的补救入口。
 */
internal enum class FnMode(internal val fnLabel: String, internal val fieldLabel: String) {
    EXPLICIT("f", "f(x) ="),
    IMPLICIT("F", "F(x,y) ="),
    ;

    fun toggled(): FnMode = if (this == EXPLICIT) IMPLICIT else EXPLICIT
}

/**
 * 定义域：左右端点，null 表示该侧无界。
 *
 * 显式函数按 x 范围裁剪采样，隐式方程按 x 范围裁剪网格，两者语义一致。
 */
internal data class Domain(val start: Double? = null, val end: Double? = null) {

    internal val isAll: Boolean get() = start == null && end == null
    internal val isInvalid: Boolean get() = start != null && end != null && start >= end

    internal fun contains(x: Double): Boolean =
        (start == null || x >= start) && (end == null || x <= end)

    internal fun xRange(outerStart: Double, outerEnd: Double): Pair<Double, Double> =
        (start ?: outerStart) to (end ?: outerEnd)
}

/** 一条曲线：表达式 + 颜色 + 是否显示 + 模式 + 定义域。 */
internal data class PlotFn(
    val id: Int,
    val expr: String,
    val colorArgb: Long,
    val visible: Boolean = true,
    val mode: FnMode = FnMode.EXPLICIT,
    val domain: Domain = Domain(),
)

/** 图例行号用真实序号：隐藏某条曲线后 f2 仍然叫 f2。 */
internal fun fnLabel(index: Int, mode: FnMode): String =
    if (mode == FnMode.EXPLICIT) "f${index + 1}(x)" else "F${index + 1}(x,y)"

internal data class LegendItem(val colorArgb: Long, val expr: String, val number: Int, val mode: FnMode)

/* ------------------------------------------------------------------
 * 参数滑块模型
 * ---------------------------------------------------------------- */

/**
 * 一个参数滑块。值域 = [min, max]，按 [step] 吸附。
 *
 * [auto] 标记它是不是由表达式里的自由变量自动发现的：自动滑块可以删，
 * 删掉之后不会被立刻重建（学生删掉它通常就是不想让它占地方）。
 */
internal data class ParamDef(
    val name: String,
    val value: Double,
    val min: Double = -5.0,
    val max: Double = 5.0,
    val step: Double = 0.1,
    val auto: Boolean = false,
) {
    internal fun clampedToRange(): ParamDef = copy(value = value.coerceIn(min, max))
}

/* ------------------------------------------------------------------
 * 纯逻辑工具（不依赖 Compose / Android，可 JVM 单测）
 * ---------------------------------------------------------------- */

/** 表达式解析器认识的函数名；滑块不能用这些名字，否则输入 sin(x) 会被当函数调用。 */
internal val RESERVED_PARAM_NAMES: Set<String> =
    setOf("x", "y", "pi", "π", "e", "sin", "cos", "tan", "ln", "lg", "log", "sqrt", "abs", "exp")

/** 数字显示：去掉多余的 0（3.00 → 3，0.50 → 0.5）。 */
internal fun formatMathNumber(value: Double, maxDecimals: Int = 4): String {
    if (!value.isFinite()) return "—"
    val absValue = abs(value)
    val text = when {
        absValue >= 1e6 -> String.format(Locale.US, "%.3e", value)
        else -> {
            val decimals = when {
                absValue >= 100 -> 1
                absValue >= 1 -> 2
                else -> maxDecimals
            }
            String.format(Locale.US, "%.${decimals}f", value)
        }
    }
    return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }.let { if (it == "-0") "0" else it }
}

/** 按步进的小数位数显示参数值：步进 0.05 → 两位小数。 */
internal fun formatParamValue(value: Double, step: Double): String {
    if (!value.isFinite()) return "—"
    val decimals = stepDecimals(step)
    val text = String.format(Locale.US, "%.${decimals}f", value)
    return if (text == "-0" || text == "-0.0") "0" else text
}

/** 步进对应的小数位数；步进 ≥1 时保留 0 位。 */
internal fun stepDecimals(step: Double): Int {
    if (!step.isFinite() || step <= 0) return 1
    if (step >= 1.0) return 0
    return ceil(-log10(step) - 1e-9).toInt().coerceIn(1, 4)
}

/** 把值吸附到以 min 为起点、step 为间隔的刻度上，并夹在 [min, max] 内。 */
internal fun snapToStep(value: Double, min: Double, max: Double, step: Double): Double {
    val lo = min.coerceAtMost(max)
    val hi = max.coerceAtLeast(min)
    if (!value.isFinite()) return lo.coerceIn(lo, hi)
    if (!step.isFinite() || step <= 0) return value.coerceIn(lo, hi)
    val snapped = lo + round((value - lo) / step) * step
    val awareLo = abs(snapped - lo) < step / 1000
    val awareHi = abs(snapped - hi) < step / 1000
    return when {
        awareLo -> lo
        awareHi -> hi
        else -> snapped.coerceIn(lo, hi)
    }
}

/** 滑块数量上限：再多就把编辑区挤没了。 */
internal const val MAX_SLIDERS = 6

/** 曲线数量上限（与原实现一致）。 */
internal const val MAX_CURVES = 6

/**
 * 校验滑块变量名。返回 null 表示合法，否则返回给用户看的中文错误。
 * 名字会直接进表达式解析器，所以必须是纯字母、且不与 x/y/常数/函数名冲突。
 */
internal fun validateParamName(raw: String, existing: Collection<ParamDef>): String? {
    val name = raw.trim()
    return when {
        name.isEmpty() -> "请输入变量名"
        name.length > 8 -> "变量名最多 8 个字母"
        !name.all { it.isLetter() } -> "变量名只能包含字母"
        name in RESERVED_PARAM_NAMES -> "x、y、π、e 和函数名不能用作滑块变量"
        existing.any { it.name == name } -> "已经有一条叫 $name 的滑块了"
        else -> null
    }
}

/**
 * 校验滑块的数值配置。返回 null 表示合法；[value] 传 null 表示只校验区间与步进。
 */
internal fun validateParamRange(
    min: Double,
    max: Double,
    step: Double,
    value: Double? = null,
): String? = when {
    !min.isFinite() || !max.isFinite() -> "最小值和最大值必须是有效数字"
    min >= max -> "最小值必须小于最大值"
    !step.isFinite() || step <= 0 -> "步进必须大于 0"
    step > (max - min) -> "步进不能大于取值范围"
    value != null && !value.isFinite() -> "当前值必须是有效数字"
    value != null && value < min - 1e-9 -> "当前值不能小于最小值"
    value != null && value > max + 1e-9 -> "当前值不能大于最大值"
    else -> null
}

/** 定义域给人看的标签，编辑器与函数列表共用。 */
internal fun formatDomainLabel(domain: Domain): String {
    val start = domain.start
    val end = domain.end
    return when {
        start == null && end == null -> "全体实数"
        start != null && end == null -> "x ≥ ${formatMathNumber(start)}"
        start == null && end != null -> "x ≤ ${formatMathNumber(end)}"
        start != null && end != null -> "x ∈ [${formatMathNumber(start)}, ${formatMathNumber(end)}]"
        else -> "全体实数"
    }
}

/**
 * 解析定义域端点输入：允许空（无界）、数字或简单常数表达式（pi、1/2、-3）。
 * 返回 Result：成功时 null 表示无界。
 */
internal fun parseDomainBound(raw: String): Result<Double?> {
    val text = raw.trim()
    if (text.isEmpty()) return Result.success(null)
    return runCatching {
        val expression = parseExpression(text)
        require(expression.variables().isEmpty()) { "端点不能含变量，请填一个常数" }
        val value = expression.eval(0.0, emptyMap())
        require(value.isFinite()) { "这里需要一个确定的数值" }
        value
    }
}

/* ------------------------------------------------------------------
 * 状态保存（进程重建 / 旋转后不丢函数、模式、定义域与滑块）
 * ---------------------------------------------------------------- */

/** 表达式可能含任意字符，用不可见分隔符打包，避免和输入内容冲突。 */
private const val SEP = "\u0001"

internal val FnListSaver: Saver<List<PlotFn>, List<String>> = Saver(
    save = { list ->
        list.map { fn ->
            listOf(
                fn.id.toString(),
                fn.colorArgb.toString(),
                fn.visible.toString(),
                fn.mode.name,
                fn.domain.start?.toString() ?: "",
                fn.domain.end?.toString() ?: "",
                fn.expr,
            ).joinToString(SEP)
        }
    },
    restore = { saved ->
        saved.mapNotNull { raw ->
            val parts = raw.split(SEP, limit = 7)
            if (parts.size < 7) return@mapNotNull null
            PlotFn(
                id = parts[0].toIntOrNull() ?: return@mapNotNull null,
                colorArgb = parts[1].toLongOrNull() ?: curvePalette[0],
                visible = parts[2].toBoolean(),
                mode = runCatching { FnMode.valueOf(parts[3]) }.getOrDefault(FnMode.EXPLICIT),
                domain = Domain(
                    start = parts[4].takeIf { it.isNotBlank() }?.toDoubleOrNull(),
                    end = parts[5].takeIf { it.isNotBlank() }?.toDoubleOrNull(),
                ),
                expr = parts[6],
            )
        }
    },
)

internal val ParamListSaver: Saver<List<ParamDef>, List<String>> = Saver(
    save = { list ->
        list.map { p ->
            listOf(
                p.name,
                p.value.toString(),
                p.min.toString(),
                p.max.toString(),
                p.step.toString(),
                p.auto.toString(),
            ).joinToString(SEP)
        }
    },
    restore = { saved ->
        saved.mapNotNull { raw ->
            val parts = raw.split(SEP, limit = 6)
            if (parts.size < 6) return@mapNotNull null
            val name = parts[0].ifBlank { return@mapNotNull null }
            ParamDef(
                name = name,
                value = parts[1].toDoubleOrNull() ?: 1.0,
                min = parts[2].toDoubleOrNull() ?: -5.0,
                max = parts[3].toDoubleOrNull() ?: 5.0,
                step = parts[4].toDoubleOrNull() ?: 0.1,
                auto = parts[5].toBoolean(),
            )
        }
    },
)

/** 曲线配色：六条以内，同一坐标系里也能分清。 */
internal val curvePalette = listOf(
    0xFF34558B, // 靛蓝
    0xFFB4453A, // 赭红
    0xFF2E7D6F, // 青绿
    0xFF6B4A8F, // 绛紫
    0xFF9A6B1F, // 琥珀
    0xFF1F6F78, // 青碧
)
