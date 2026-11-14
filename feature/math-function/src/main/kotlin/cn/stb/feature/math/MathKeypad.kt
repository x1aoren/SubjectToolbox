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
 * 统一用 **7 列 × 5 行** 的网格排版：每行都是 7 个等宽键、每行间距一致，
 * 所以 1～9 与 0 的大小和列位置完全对齐（不再出现不同行按键宽度不一样、
 * 数字错位的问题）。第 1～4 行的前 3 列都是数字，第 4 列是四则运算符，
 * 其余位置放括号、清空、函数与常数。
 *
 * 按键分两类配色：数字/运算符用中性色，变量、函数、常数用强调色，
 * 和输入框里「普通文字 vs 数学符号」的视觉区分保持一致。
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
    // 7 列 × 5 行 = 35 个键，正好铺满、没有空格子
    val rows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("7", KeyAction.Char("7")),
            KeySpec("8", KeyAction.Char("8")),
            KeySpec("9", KeyAction.Char("9")),
            KeySpec("÷", KeyAction.Operator("/"), description = "除"),
            KeySpec("(", KeyAction.Char("("), description = "左括号"),
            KeySpec(")", KeyAction.Char(")"), description = "右括号"),
            KeySpec("⌫", KeyAction.Backspace, description = "退格"),
        ),
        listOf(
            KeySpec("4", KeyAction.Char("4")),
            KeySpec("5", KeyAction.Char("5")),
            KeySpec("6", KeyAction.Char("6")),
            KeySpec("×", KeyAction.Operator("*"), description = "乘"),
            KeySpec("^", KeyAction.Power, accent = true, slot = true, description = "乘方"),
            KeySpec("√", KeyAction.Function("sqrt"), accent = true, description = "开方"),
            KeySpec("π", KeyAction.Char("π"), accent = true, description = "圆周率"),
        ),
        listOf(
            KeySpec("1", KeyAction.Char("1")),
            KeySpec("2", KeyAction.Char("2")),
            KeySpec("3", KeyAction.Char("3")),
            KeySpec("-", KeyAction.Operator("-")),
            KeySpec("=", KeyAction.Operator("="), description = "等号"),
            KeySpec("e", KeyAction.Char("e"), accent = true),
            KeySpec("x", KeyAction.Char("x"), accent = true),
        ),
        listOf(
            KeySpec("0", KeyAction.Char("0")),
            KeySpec(".", KeyAction.Char(".")),
            KeySpec("y", KeyAction.Char("y"), accent = true),
            KeySpec("+", KeyAction.Operator("+")),
            KeySpec("a", KeyAction.Char("a"), accent = true),
            KeySpec("b", KeyAction.Char("b"), accent = true),
            KeySpec("c", KeyAction.Char("c"), accent = true),
        ),
        listOf(
            KeySpec("sin", KeyAction.Function("sin"), accent = true),
            KeySpec("cos", KeyAction.Function("cos"), accent = true),
            KeySpec("tan", KeyAction.Function("tan"), accent = true),
            KeySpec("log", KeyAction.Function("log"), accent = true),
            KeySpec("lg", KeyAction.Function("lg"), accent = true),
            KeySpec("ln", KeyAction.Function("ln"), accent = true),
            KeySpec("清空", KeyAction.Clear, description = "清空表达式"),
        ),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { spec ->
                    MathKeyButton(
                        spec = spec,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (val action = spec.action) {
                                is KeyAction.Char -> onChar(action.text)
                                is KeyAction.Operator -> onOperator(action.symbol)
                                is KeyAction.Function -> onFunction(action.name)
                                KeyAction.Power -> onPower()
                                KeyAction.Backspace -> onBackspace()
                                KeyAction.Clear -> onClear()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 一个键的内容与语义。 */
private data class KeySpec(
    val label: String,
    val action: KeyAction,
    val accent: Boolean = false,
    val slot: Boolean = false,
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
            .height(40.dp)
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
                spec.slot -> SuperscriptSlotIcon(color = contentColor)
                spec.label == "清空" -> Text(
                    text = spec.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                spec.action is KeyAction.Function -> Text(
                    text = spec.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = contentColor,
                    maxLines = 1,
                )
                else -> Text(
                    text = spec.label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = contentColor,
                    maxLines = 1,
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
