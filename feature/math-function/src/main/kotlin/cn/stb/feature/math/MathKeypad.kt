package cn.stb.feature.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 函数编辑器的数学键盘。
 *
 * 排版沿用 1.0.0 的键位（学生已经用惯这一套）：
 * - 前 3 行是 6 列的数字与四则运算，第 4 行「0」占两格、「清空」占四格；
 * - 空一行之后是变量 / 函数 / 常数两行，各 8 列；
 * - 数字与运算是中性色，变量与函数用强调色，和输入框里「普通文字 vs 数学符号」的区分一致。
 *
 * 每个键只表达「按下去插入什么」，具体插入规则在 [MathEdit] 里，键盘本身不碰文本。
 */
@Composable
internal fun MathKeypad(
    onChar: (String) -> Unit,
    onOperator: (String) -> Unit,
    onFunction: (String) -> Unit,
    onPower: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatch: (KeyAction) -> Unit = { action ->
        when (action) {
            is KeyAction.Char -> onChar(action.text)
            is KeyAction.Operator -> onOperator(action.symbol)
            is KeyAction.Function -> onFunction(action.name)
            KeyAction.Power -> onPower()
            KeyAction.Backspace -> onBackspace()
            KeyAction.Clear -> onClear()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NUMBER_ROWS.forEach { row -> KeyRow(keys = row, dispatch = dispatch) }
        // 0 占两格；后面三个是定义域用的符号（{0,10} 这种写法要能打得出来）
        KeyRow(
            keys = listOf(
                KeySpec("0", KeyAction.Char("0"), weight = 2f),
                KeySpec(",", KeyAction.Char(","), description = "逗号"),
                KeySpec("{", KeyAction.Char("{"), description = "定义域左花括号"),
                KeySpec("}", KeyAction.Char("}"), description = "定义域右花括号"),
                KeySpec("清空", KeyAction.Clear, description = "清空表达式"),
            ),
            dispatch = dispatch,
        )

        Spacer(Modifier.height(2.dp))

        FUNCTION_ROWS.forEach { row -> KeyRow(keys = row, dispatch = dispatch) }
    }
}

/** 数字与四则运算：6 列对齐，0 在左下角。 */
private val NUMBER_ROWS: List<List<KeySpec>> = listOf(
    listOf(
        KeySpec("7", KeyAction.Char("7")),
        KeySpec("8", KeyAction.Char("8")),
        KeySpec("9", KeyAction.Char("9")),
        KeySpec("÷", KeyAction.Operator("/"), description = "除"),
        KeySpec("(", KeyAction.Char("("), description = "左括号"),
        KeySpec(")", KeyAction.Char(")"), description = "右括号"),
    ),
    listOf(
        KeySpec("4", KeyAction.Char("4")),
        KeySpec("5", KeyAction.Char("5")),
        KeySpec("6", KeyAction.Char("6")),
        KeySpec("×", KeyAction.Operator("*"), description = "乘"),
        KeySpec("=", KeyAction.Operator("="), description = "等号"),
        KeySpec("⌫", KeyAction.Backspace, description = "退格"),
    ),
    listOf(
        KeySpec("1", KeyAction.Char("1")),
        KeySpec("2", KeyAction.Char("2")),
        KeySpec("3", KeyAction.Char("3")),
        KeySpec("+", KeyAction.Operator("+")),
        KeySpec("-", KeyAction.Operator("-")),
        KeySpec(".", KeyAction.Char(".")),
    ),
)

/** 变量、函数与常数：8 列 / 7 列，用强调色。 */
private val FUNCTION_ROWS: List<List<KeySpec>> = listOf(
    listOf(
        KeySpec("x", KeyAction.Char("x"), accent = true),
        KeySpec("y", KeyAction.Char("y"), accent = true),
        KeySpec("sin", KeyAction.Function("sin"), accent = true),
        KeySpec("cos", KeyAction.Function("cos"), accent = true),
        KeySpec("tan", KeyAction.Function("tan"), accent = true),
        KeySpec("log", KeyAction.Function("log"), accent = true),
        KeySpec("lg", KeyAction.Function("lg"), accent = true),
        KeySpec("ln", KeyAction.Function("ln"), accent = true),
    ),
    listOf(
        KeySpec("a", KeyAction.Char("a"), accent = true),
        KeySpec("b", KeyAction.Char("b"), accent = true),
        KeySpec("c", KeyAction.Char("c"), accent = true),
        // ^ 键画成「x + 右上角空上标槽」，和输入框里按完 ^ 长出来的虚线框是同一个形象
        KeySpec("^", KeyAction.Power, accent = true, slot = true, description = "乘方"),
        KeySpec("√", KeyAction.Function("sqrt"), accent = true, description = "开方"),
        // 绝对值键画成「| 方框 |」，和输入框里按完长出来的样子一致
        KeySpec("| |", KeyAction.Function("abs"), accent = true, absSlot = true, description = "绝对值"),
        KeySpec("π", KeyAction.Char("π"), accent = true, description = "圆周率"),
        KeySpec("e", KeyAction.Char("e"), accent = true),
    ),
)

/** 一行键：按 [KeySpec.weight] 分宽度，间距统一 4dp。 */
@Composable
private fun KeyRow(keys: List<KeySpec>, dispatch: (KeyAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { spec ->
            MathKeyButton(
                spec = spec,
                modifier = Modifier.weight(spec.weight),
                onClick = { dispatch(spec.action) },
            )
        }
    }
}

/** 一个键的内容与语义。 */
private data class KeySpec(
    val label: String,
    val action: KeyAction,
    val accent: Boolean = false,
    /** 相对宽度：0 占两格、清空占四格，其余都是一格。 */
    val weight: Float = 1f,
    /** 用「x + 空上标槽」图标代替文字（乘方键）。 */
    val slot: Boolean = false,
    /** 用「| 方框 |」图标代替文字（绝对值键）。 */
    val absSlot: Boolean = false,
    val description: String? = null,
)

private sealed interface KeyAction {
    data class Char(val text: String) : KeyAction
    data class Operator(val symbol: String) : KeyAction
    data class Function(val name: String) : KeyAction
    data object Power : KeyAction
    data object Backspace : KeyAction
    data object Clear : KeyAction
}

@Composable
private fun MathKeyButton(
    spec: KeySpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (spec.accent) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .then(
                if (spec.description != null) {
                    Modifier.semantics { contentDescription = spec.description }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(9.dp),
        color = if (spec.accent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                spec.absSlot -> AbsoluteSlotIcon(color = contentColor)
                spec.slot -> SuperscriptSlotIcon(color = contentColor)
                spec.label.length <= 2 -> Text(
                    text = spec.label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = contentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )

                else -> Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** ^ 键的图标：x 右上角一个空的上标槽，和输入框里按 ^ 之后长出来的框是同一个形象。 */
@Composable
private fun SuperscriptSlotIcon(color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "x",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
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

/** 绝对值键的图标：两边竖线，中间一个虚线方框。 */
@Composable
private fun AbsoluteSlotIcon(color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "|",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
        Spacer(Modifier.width(1.dp))
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 13.dp)
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
        Spacer(Modifier.width(1.dp))
        Text(
            text = "|",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}
