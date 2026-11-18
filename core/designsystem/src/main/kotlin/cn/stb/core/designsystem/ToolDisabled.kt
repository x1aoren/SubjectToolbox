package cn.stb.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cn.stb.core.model.CloudSnapshot

/**
 * 停用来源小字：单设备停用要明确告诉用户「只影响这台设备」，
 * 全局停用则说明是学校统一设置；旧缓存/未知来源不显示。
 */
fun disabledSourceHint(source: String?): String? = when (source) {
    "device" -> "该停用仅针对本设备"
    "global" -> "学校管理员设置"
    else -> null
}

/**
 * 工具停用弹窗：原因取自云端（单设备原因 > 全局原因，优先级由服务端合并；都没配则用默认文案）。
 *
 * 主页工具卡片、示意图子项、反馈入口共用同一个弹窗，保证「停用不隐藏、点击看原因」
 * 在所有入口上的交互一致。
 */
@Composable
fun ToolDisabledDialog(
    title: String,
    reason: String,
    source: String? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title 已停用") },
        text = {
            Column {
                Text(
                    text = reason.ifBlank { CloudSnapshot.DEFAULT_DISABLED_TEXT },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val hint = disabledSourceHint(source)
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

/**
 * 「已停用」小徽章：工具卡片、示意图卡片的右上角标记。
 */
@Composable
fun DisabledBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "已停用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
