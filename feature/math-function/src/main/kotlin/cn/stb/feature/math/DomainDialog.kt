package cn.stb.feature.math

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs

/* ------------------------------------------------------------------
 * 定义域对话框
 * ---------------------------------------------------------------- */

/**
 * 函数定义域设置对话框。
 *
 * 两端都允许留空表示无界；非空时按常数表达式解析，pi、1/2 这类写法直接能用
 * （和表达式输入框共用同一个解析器，学生不用记第二套规则）。实时校验：
 * 解析失败或左端点不小于右端点时不允许确定。
 *
 * @param initial 当前定义域，用来预填两个端点
 * @param onDismiss 取消（点遮罩或返回键也走这里）
 * @param onConfirm 确定；能走到这里就一定满足左 < 右，null 表示该侧无界
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DomainDialog(
    initial: Domain,
    onDismiss: () -> Unit,
    onConfirm: (Domain) -> Unit,
) {
    // 用文本而不是 Double 做状态：学生正在输入的半截表达式（如 "pi/"）也得留在框里
    var startText by rememberSaveable(initial.start) { mutableStateOf(domainBoundText(initial.start)) }
    var endText by rememberSaveable(initial.end) { mutableStateOf(domainBoundText(initial.end)) }

    val startResult = parseDomainBound(startText)
    val endResult = parseDomainBound(endText)
    val startValue = startResult.getOrNull()
    val endValue = endResult.getOrNull()
    val startError = if (startResult.isFailure) {
        startResult.exceptionOrNull()?.message ?: "看不懂这个端点"
    } else {
        null
    }
    val endError = if (endResult.isFailure) {
        endResult.exceptionOrNull()?.message ?: "看不懂这个端点"
    } else {
        null
    }
    val rangeError = if (
        startResult.isSuccess && endResult.isSuccess &&
        startValue != null && endValue != null && startValue >= endValue
    ) {
        "左端点必须小于右端点"
    } else {
        null
    }
    val canConfirm = startResult.isSuccess && endResult.isSuccess && rangeError == null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // 横屏平板上铺满会显得空，限宽居中；竖屏手机上自然占满可用宽度
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    // 软键盘弹出后可用高度变小，整块内容可滚，确定按钮仍然够得到
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "设置定义域",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "只在这个范围内绘制；留空表示无界，可输入 pi、1/2 这样的常数表达式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                DomainBoundField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = "左端点（空 = −∞）",
                    error = startError,
                )
                Spacer(Modifier.height(10.dp))
                DomainBoundField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = "右端点（空 = +∞）",
                    error = endError,
                )

                if (rangeError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = rangeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "快捷预设",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DOMAIN_PRESETS.forEach { preset ->
                        val selected = startResult.isSuccess && endResult.isSuccess &&
                            startValue == preset.start && endValue == preset.end
                        FilterChip(
                            selected = selected,
                            onClick = {
                                startText = preset.startText
                                endText = preset.endText
                            },
                            label = { Text(preset.label) },
                        )
                    }
                }

                if (canConfirm) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "将绘制：${formatDomainLabel(Domain(startValue, endValue))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { onConfirm(Domain(startValue, endValue)) },
                        enabled = canConfirm,
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/** 单个定义域端点输入框：框下面跟一行红字错误提示，比 floating label 更不容易被裁掉。 */
@Composable
private fun DomainBoundField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text("留空 = 无界") },
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(12.dp),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, top = 3.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------
 * 预设与端点回填
 * ---------------------------------------------------------------- */

/**
 * 一个定义域预设。
 *
 * 同时带显示文本和解析后的值：文本用于填进输入框（学生可以接着改），
 * 数值用于判断当前输入命中哪个快捷项。
 */
private data class DomainPreset(
    val label: String,
    val start: Double?,
    val end: Double?,
    val startText: String,
    val endText: String,
)

private val DOMAIN_PRESETS = listOf(
    DomainPreset("全体实数", null, null, "", ""),
    DomainPreset("[-5, 5]", -5.0, 5.0, "-5", "5"),
    DomainPreset("[0, 10]", 0.0, 10.0, "0", "10"),
    DomainPreset("[0, +∞)", 0.0, null, "0", ""),
    DomainPreset("(-∞, 0]", null, 0.0, "", "0"),
)

/**
 * 把已保存的端点回填成可读又能重新解析的文本。
 *
 * 空表示无界；π 的倍数写成 π，学生下次打开能一眼认出来；其余用最短的十进制写法，
 * 避免 Double.toString 那种又长又难读的尾巴。
 */
private fun domainBoundText(value: Double?): String {
    if (value == null || !value.isFinite()) return ""
    if (abs(value - PI) < 1e-9) return "π"
    if (abs(value + PI) < 1e-9) return "-π"
    if (abs(value) < 1e-12) return "0"
    if (abs(value) >= 1e12) return value.toString()
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
}
