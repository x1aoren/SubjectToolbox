package cn.stb.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import cn.stb.core.model.DarkMode
import cn.stb.core.model.ThemePref

/**
 * HSL → RGB。
 *
 * 不用 android.graphics.Color 的版本：一是省掉一个 Android 依赖（本模块只应依赖 Compose），
 * 二是自己算能保证同一个种子色在任意系统版本上得到完全相同的配色。
 */
private fun hueTone(hue: Float, saturation: Float, lightness: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}

/** ARGB → HSL。 */
private fun seedHsl(argb: Long): Triple<Float, Float, Float> {
    val r = ((argb shr 16) and 0xFF).toFloat() / 255f
    val g = ((argb shr 8) and 0xFF).toFloat() / 255f
    val b = (argb and 0xFF).toFloat() / 255f

    val max = kotlin.math.max(r, kotlin.math.max(g, b))
    val min = kotlin.math.min(r, kotlin.math.min(g, b))
    val l = (max + min) / 2f
    val d = max - min
    val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    return Triple(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
}

/**
 * 从一个种子色推导整套配色。
 *
 * 不引入 material-color-utilities：那套库体积不小，而本项目只需要一组稳定的
 * 「主色 + 容器色 + 中性色」，用 HSL 调音足够，且完全可控。
 */
fun schemeFromSeed(seedArgb: Long, dark: Boolean): ColorScheme {
    val (h, s0, _) = seedHsl(seedArgb)
    val s = s0.coerceIn(0.15f, 0.85f)

    return if (dark) {
        darkColorScheme(
            primary = hueTone(h, s * 0.75f, 0.78f),
            onPrimary = hueTone(h, s * 0.6f, 0.16f),
            primaryContainer = hueTone(h, s * 0.55f, 0.30f),
            onPrimaryContainer = hueTone(h, s * 0.5f, 0.92f),
            secondary = hueTone(h, s * 0.35f, 0.72f),
            onSecondary = hueTone(h, s * 0.3f, 0.18f),
            secondaryContainer = hueTone(h, s * 0.3f, 0.28f),
            onSecondaryContainer = hueTone(h, s * 0.25f, 0.90f),
            tertiary = hueTone((h + 40f) % 360f, s * 0.4f, 0.72f),
            background = Color(0xFF131412),
            onBackground = Color(0xFFE6E5DF),
            surface = Color(0xFF1A1B18),
            onSurface = Color(0xFFE6E5DF),
            surfaceVariant = Color(0xFF2A2B27),
            onSurfaceVariant = Color(0xFFC6C5BC),
            outline = Color(0xFF8E8F86),
            outlineVariant = Color(0xFF3B3C36),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
        )
    } else {
        lightColorScheme(
            primary = hueTone(h, s * 0.85f, 0.34f),
            onPrimary = Color.White,
            primaryContainer = hueTone(h, s * 0.45f, 0.92f),
            onPrimaryContainer = hueTone(h, s * 0.7f, 0.18f),
            secondary = hueTone(h, s * 0.45f, 0.44f),
            onSecondary = Color.White,
            secondaryContainer = hueTone(h, s * 0.30f, 0.93f),
            onSecondaryContainer = hueTone(h, s * 0.5f, 0.20f),
            tertiary = hueTone((h + 40f) % 360f, s * 0.5f, 0.40f),
            background = Color(0xFFFAFAF7),
            onBackground = Color(0xFF1B1C19),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1B1C19),
            surfaceVariant = Color(0xFFF2F1EA),
            onSurfaceVariant = Color(0xFF5D5E57),
            outline = Color(0xFF8C8D85),
            outlineVariant = Color(0xFFDFDED6),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        )
    }
}

private fun TextStyle.scaled(scale: Float): TextStyle {
    if (scale == 1f) return this
    val line = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * scale
    val size = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * scale
    return copy(fontSize = size, lineHeight = line)
}

private fun Typography.scaled(scale: Float): Typography = Typography(
    displayLarge = displayLarge.scaled(scale),
    displayMedium = displayMedium.scaled(scale),
    displaySmall = displaySmall.scaled(scale),
    headlineLarge = headlineLarge.scaled(scale),
    headlineMedium = headlineMedium.scaled(scale),
    headlineSmall = headlineSmall.scaled(scale),
    titleLarge = titleLarge.scaled(scale),
    titleMedium = titleMedium.scaled(scale),
    titleSmall = titleSmall.scaled(scale),
    bodyLarge = bodyLarge.scaled(scale),
    bodyMedium = bodyMedium.scaled(scale),
    bodySmall = bodySmall.scaled(scale),
    labelLarge = labelLarge.scaled(scale),
    labelMedium = labelMedium.scaled(scale),
    labelSmall = labelSmall.scaled(scale),
)

@Composable
fun SubjectToolboxTheme(theme: ThemePref, content: @Composable () -> Unit) {
    val dark = when (theme.darkMode) {
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val scheme = remember(theme.seedArgb, dark) { schemeFromSeed(theme.seedArgb, dark) }
    val typography = remember(theme.fontScale) { Typography().scaled(theme.fontScale) }

    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
