package cn.stb.feature.math

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 把表达式里的 `x^2` 显示成「x 右上角带框的小 2」。
 *
 * 存储的仍然是没有排版的 `x^2`——解析器不用懂上标，光标坐标也按原文本算；
 * 渲染时去掉 `^`，把指数内容缩小、抬高。虚线框不在这里画：输入框只在光标真的
 * 落在某个上标槽里时才描框，平时就是普通的上标小字。
 *
 * 只处理一眼能看懂的指数：数字、字母、带正负号的数字、括号组。
 * `^` 后面跟别的东西（比如括号还没闭合）时保持原样，免得显示和解析对不上。
 */
internal class SuperscriptTransformation(
    private val superscriptSize: TextUnit = 11.sp,
) : VisualTransformation {

    private val superscriptStyle = SpanStyle(
        baselineShift = BaselineShift.Superscript,
        fontFamily = FontFamily.Monospace,
        fontSize = superscriptSize,
    )

    /** 每个上标槽在原始文本里的光标位置范围，和渲染出的上标 span 一一对应。 */
    private val slotRanges = mutableListOf<IntRange>()

    /**
     * 光标落在哪个上标槽里就返回哪个序号，否则 null。
     * 空槽（刚按下 ^、指数还没打）也算——那正是虚线框最该出现的时候。
     */
    fun activeRunIndex(caret: Int): Int? =
        slotRanges.indexOfFirst { caret in it }.takeIf { it >= 0 }

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        slotRanges.clear()

        // 原始光标位置 -> 转换后位置
        val originalToTransformed = IntArray(src.length + 1)
        val styles = mutableListOf<Triple<Int, Int, SpanStyle>>()
        val out = StringBuilder()

        var i = 0
        var transformed = 0
        originalToTransformed[0] = 0

        while (i < src.length) {
            if (src[i] == '^') {
                val end = exponentEnd(src, i + 1)
                if (end != null) {
                    val start = transformed

                    // 光标落在 ^ 之后时，视觉上应该停在上标槽里
                    originalToTransformed[i + 1] = start
                    slotRanges += (i + 1)..end

                    val count = end - i - 1
                    if (count > 0) {
                        out.append(src, i + 1, end)
                        for (k in 0 until count) {
                            originalToTransformed[i + 2 + k] = start + k + 1
                        }
                        styles += Triple(start, start + count, superscriptStyle)
                        transformed += count
                    } else {
                        // 空的上标槽：一个空格撑出位置，虚线框由输入框画
                        out.append(' ')
                        styles += Triple(start, start + 1, superscriptStyle)
                        transformed += 1
                    }
                    i = end
                } else {
                    out.append('^')
                    transformed += 1
                    originalToTransformed[i + 1] = transformed
                    i += 1
                }
            } else {
                out.append(src[i])
                transformed += 1
                originalToTransformed[i + 1] = transformed
                i += 1
            }
        }

        val annotated = buildAnnotatedString {
            append(out.toString())
            styles.forEach { (start, end, style) -> addStyle(style, start, end) }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, src.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val target = offset.coerceIn(0, transformed)
                var best = 0
                for (o in originalToTransformed.indices) {
                    if (originalToTransformed[o] <= target) best = o else break
                }
                return best
            }
        }

        return TransformedText(annotated, mapping)
    }
}

/**
 * 指数内容的结束下标（不含）。返回 null 表示这段不是能识别的指数，保持原样显示。
 * 开头允许一个正负号，内容可以是数字、字母（含 `sin(...)` 这样的函数调用）或括号组。
 */
private fun exponentEnd(src: String, start: Int): Int? {
    if (start >= src.length) return start

    var j = start
    if (src[j] == '+' || src[j] == '-') {
        j++
        if (j >= src.length) return null
    }

    val c = src[j]
    return when {
        c.isDigit() -> {
            while (j < src.length && src[j].isDigit()) j++
            j
        }
        c.isLetter() -> {
            while (j < src.length && src[j].isLetter()) j++
            if (j < src.length && src[j] == '(') parenEnd(src, j) ?: j else j
        }
        c == '(' -> parenEnd(src, j)
        else -> null
    }
}

/** 从开括号一路数到配对的闭括号，返回闭括号之后的下标。 */
private fun parenEnd(src: String, open: Int): Int? {
    var depth = 0
    var j = open
    while (j < src.length) {
        when (src[j]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return j + 1
            }
        }
        j++
    }
    return null
}

/** 只读场景（函数列表、图例）直接用转换后的富文本，和输入框显示一致。 */
internal fun superscriptAnnotated(raw: String): AnnotatedString =
    SuperscriptTransformation().filter(AnnotatedString(raw)).text
