package cn.stb.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.stb.core.model.Subject
import cn.stb.core.model.SubjectIcon
import cn.stb.core.model.ToolIcon
import cn.stb.core.model.ToolSpec

/**
 * 用单个汉字做字形徽章。
 *
 * 学科与工具都有图标之后，它退居两处兜底：图标未配置时降级，以及「空状态」这类
 * 临时文案图。保留它也让整块 UI 在没有新资源时仍然可用。
 */
@Composable
fun GlyphBadge(
    glyph: String,
    accent: Color,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = accent,
            fontSize = (size.value * 0.40f).sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 图标徽章：与 [GlyphBadge] 同一套底板，图形换成矢量图标。
 *
 * 图标边长取徽章的 48%，和原来汉字 40% 字号加字面留白的观感相当；
 * 描边图标用 tint 染色，深浅色模式与学科色都能跟着走。
 */
@Composable
fun IconBadge(
    icon: Painter,
    accent: Color,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

/** 学科徽章：有图标用图标，没有就退回单字。 */
@Composable
fun SubjectBadge(
    subject: Subject,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    val accent = Color(subject.accent)
    val icon = subject.icon
    if (icon == null) {
        GlyphBadge(glyph = subject.short, accent = accent, size = size, modifier = modifier)
    } else {
        IconBadge(icon = painterResource(icon.resId()), accent = accent, size = size, modifier = modifier)
    }
}

/** 工具徽章：有图标用图标，没有就退回单字。 */
@Composable
fun ToolBadge(
    spec: ToolSpec,
    accent: Color,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier,
) {
    val icon = spec.icon
    if (icon == null) {
        GlyphBadge(glyph = spec.glyph, accent = accent, size = size, modifier = modifier)
    } else {
        IconBadge(icon = painterResource(icon.resId()), accent = accent, size = size, modifier = modifier)
    }
}

/**
 * 语义图标 → 资源。放在 designsystem 而不是 model，
 * 是为了让 core:model 保持纯 Kotlin（不引 R 与 Android）。
 */
private fun SubjectIcon.resId(): Int = when (this) {
    SubjectIcon.BOOK_OPEN -> R.drawable.ic_subject_chinese
    SubjectIcon.CHART_SPLINE -> R.drawable.ic_subject_math
    SubjectIcon.LANGUAGES -> R.drawable.ic_subject_english
    SubjectIcon.FLASK -> R.drawable.ic_subject_chemistry
    SubjectIcon.DNA -> R.drawable.ic_subject_biology
    SubjectIcon.EARTH -> R.drawable.ic_subject_geography
    SubjectIcon.GRID -> R.drawable.ic_subject_other
}

private fun ToolIcon.resId(): Int = when (this) {
    ToolIcon.SCROLL -> R.drawable.ic_tool_poetry
    ToolIcon.FUNCTION -> R.drawable.ic_tool_math_function
    ToolIcon.DICTIONARY -> R.drawable.ic_tool_dictionary
    ToolIcon.ATOM -> R.drawable.ic_tool_chemistry
    ToolIcon.MICROSCOPE -> R.drawable.ic_tool_biology
    ToolIcon.ORBIT -> R.drawable.ic_tool_geography
    ToolIcon.LIBRARY -> R.drawable.ic_tool_textbook
    ToolIcon.SIGMA -> R.drawable.ic_tool_formula
    ToolIcon.GRAMMAR -> R.drawable.ic_tool_grammar
}

@Composable
fun ToolCard(
    spec: ToolSpec,
    accent: Color,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolBadge(spec = spec, accent = accent, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = if (pinned) "取消置顶" else "置顶到我的常用",
                    tint = if (pinned) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: SubjectIcon? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // 图标尺寸从标题字号推导，这样「设置 → 字号」调到 1.3× 时图标跟着变大，
    // 不会出现文字放大、图标原地不动的错位。
    val titleSize = MaterialTheme.typography.titleMedium.fontSize
    val iconSize = if (titleSize.isSp) (titleSize.value * 1.05f).dp else 17.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        if (icon != null) {
            Icon(
                painter = painterResource(icon.resId()),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * 全局搜索输入框。
 *
 * 窗口变矮或者变窄时（手机横屏、分屏、小窗），整套尺寸自动收一档：
 * 高度、内边距、字号都缩，免得搜索框在本来就局促的屏幕上占掉太多地方。
 * 设置 dense 后再收一档，给下方的列表多留些可视区域。
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showClearButton: Boolean = false,
    dense: Boolean = false,
) {
    val config = LocalConfiguration.current
    val compact = config.screenHeightDp < 480 || config.screenWidthDp < 600

    val minHeight = when {
        dense -> 34.dp
        compact -> 42.dp
        else -> 54.dp
    }
    val horizontalPadding = when {
        dense -> 10.dp
        compact -> 12.dp
        else -> 16.dp
    }
    val iconSize = when {
        dense -> 15.dp
        compact -> 17.dp
        else -> 20.dp
    }
    val shape = RoundedCornerShape(
        when {
            dense -> 10.dp
            compact -> 12.dp
            else -> 14.dp
        },
    )
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = when {
            dense -> 13.sp
            compact -> 14.sp
            else -> 16.sp
        },
    )

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { focusRequester.requestFocus() }
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (showClearButton && value.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(
                        when {
                            dense -> 22.dp
                            compact -> 26.dp
                            else -> 30.dp
                        },
                    )
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(
                        when {
                            dense -> 12.dp
                            compact -> 14.dp
                            else -> 16.dp
                        },
                    ),
                )
            }
        }
    }
}

/**
 * 网页缩放条：给内嵌 WebView 用，避免整页内容在平板上过小或过大。
 */
@Composable
fun ZoomStepper(
    percent: Int,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomStepButton(label = "－", description = "缩小页面", onClick = onZoomOut)
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 42.dp),
        )
        ZoomStepButton(label = "＋", description = "放大页面", onClick = onZoomIn)
    }
}

@Composable
private fun ZoomStepButton(label: String, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun EmptyState(
    glyph: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphBadge(
            glyph = glyph,
            accent = MaterialTheme.colorScheme.primary,
            size = 60.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
