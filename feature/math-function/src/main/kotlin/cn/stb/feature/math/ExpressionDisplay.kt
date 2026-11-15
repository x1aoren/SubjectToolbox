package cn.stb.feature.math

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 数学显示层。
 *
 * 存储的始终是「朴素表达式」（`x^2+sqrt(x+1)`），渲染时再排版成学生看得懂的样子：
 * - `^` 隐藏，指数内容缩小、抬高，变成右上角角标；
 * - `sqrt(内容)` 渲染成「√ + 内容上方的横线」，`sqrt(` 与配对的 `)` 都不显示；
 * - 只隐藏符号、不隐藏内容，所以表达式解析器完全不用懂排版。
 *
 * 关键难点是光标位置：隐藏字符会让「原始文本偏移」和「显示文本偏移」不再一一对应。
 * 这里的策略是：
 * - 原始 → 显示：隐藏字符的位置统一落到它后面那个可见内容的起点；
 * - 显示 → 原始：同一个显示位置对应多个原始位置时，取**最靠后**的那个，
 *   这样在角标/根号后面点击，光标会落在结构之后，继续输入 `+` 就是「加在后面」。
 * 两个方向都单调，Compose 的原生光标拖动/选区才能正常工作。
 */

/** 右上角角标（乘方指数）在显示文本里的位置。 */
internal data class MathScriptSpan(
    val start: Int,
    val end: Int,
    val placeholder: Boolean,
    val origStart: Int,
    val origEnd: Int,
)

/**
 * 光标所处的编辑区域。
 *
 * 排版把 `^指数` 缩到右上角、把 `sqrt(…)` 画成根号之后，这三种位置在屏幕上挨得很近，
 * 拖光标时很难判断「现在到底在角标里还是在根号里」。整条编辑链路都用这个枚举说话：
 * 光标颜色、区域高亮、拖动时的浮标签，全都由它决定。
 */
internal enum class MathEditRegion(
    /** 拖动光标手柄时浮在手柄旁边的提示。 */
    val label: String,
) {
    /** 基线：普通表达式位置。 */
    BASE("基线"),

    /** 右上角角标（乘方的指数）。 */
    SCRIPT("上标"),

    /** 根号内部（被开方数）。 */
    RADICAL("根号内"),
}

/** 一根根号：√ 在显示文本中的下标、被开方内容范围，以及嵌套层级。 */
internal data class MathRadicalSpan(
    val sqrtIndex: Int,
    val start: Int,
    val end: Int,
    /** 被开方内容在原始文本里的范围（含两端）：判断光标是不是在根号里要用它。 */
    val origStart: Int,
    val origEnd: Int,
    val depth: Int,
    val raised: Boolean,
    val placeholder: Boolean,
)

/** 一次排版的结果：显示文本 + 角标/根号位置 + 双向光标映射。 */
internal class MathDisplay(
    val raw: String,
    val text: String,
    val annotated: AnnotatedString,
    val scripts: List<MathScriptSpan>,
    val radicals: List<MathRadicalSpan>,
    val mapping: OffsetMapping,
    private val origToTrans: IntArray,
) {
    fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, raw.length)]

    /**
     * 光标（原始文本偏移）落在哪个编辑区。
     *
     * 必须按原始偏移算，不能先映射到显示文本：根号收尾的 `)` 是隐藏的，
     * 「被开方数末尾」和「根号后面」在屏幕上同一个位置，映射会把两者都算到根号外面，
     * 于是刚在根号里打完字，光标反而显示成基线。
     *
     * 角标优先于根号：`sqrt(x)^2` 这种角标套在根号后面时，角标里才是更小的那一层。
     */
    fun regionAt(originalOffset: Int): MathEditRegion {
        val offset = originalOffset.coerceIn(0, raw.length)
        return when {
            scripts.any { offset >= it.origStart && offset <= it.origEnd } -> MathEditRegion.SCRIPT
            radicals.any { offset >= it.origStart && offset <= it.origEnd } -> MathEditRegion.RADICAL
            else -> MathEditRegion.BASE
        }
    }
}

/** 指数、根号内容等比缩小的字号：用 em，跟随设置里的字号缩放走。 */
private val SUPERSCRIPT_SIZE = 0.62.em

private val superscriptStyle = SpanStyle(
    baselineShift = BaselineShift.Superscript,
    fontSize = SUPERSCRIPT_SIZE,
)

/** 末尾 `{0,10}` 这类定义域注解的字号：比正文小一点，但仍是可编辑的正文内容。 */
private val DOMAIN_SUFFIX_STYLE = SpanStyle(fontSize = 0.82.em)

/** 真正的 VisualTransformation 实现；同时把最近一次排版结果留给画布描根号横线。 */
internal class MathVisualTransformation : VisualTransformation {

    var display: MathDisplay? = null
        private set

    override fun filter(text: AnnotatedString): TransformedText {
        val d = buildMathDisplay(text.text)
        display = d
        return TransformedText(d.annotated, d.mapping)
    }
}

/** 只读场景（函数列表、图例）直接用富文本，和输入框显示一致。 */
internal fun mathAnnotated(raw: String): AnnotatedString = buildMathDisplay(raw).annotated

/**
 * 这个 `)` 是不是某个 `sqrt(` 的收尾括号。
 *
 * 根号的收尾括号在显示层是被隐藏的（学生只看到「√ + 被开方内容」），
 * 所以退格如果直接删掉它，界面上什么都不会变，紧接着还会满屏提示「括号没有闭合」。
 */
internal fun isRadicalCloseParen(text: String, index: Int): Boolean {
    if (index !in text.indices || text[index] != ')') return false
    var depth = 0
    for (i in index downTo 0) {
        when (text[i]) {
            ')' -> depth += 1
            '(' -> {
                depth -= 1
                if (depth == 0) {
                    // 和排版层一致：前面紧贴字母的 sqrt( 不算函数（如 asqrt(x)）
                    val start = i - 4
                    return start >= 0 &&
                        text.startsWith("sqrt(", start) &&
                        (start == 0 || !text[start - 1].isLetter())
                }
            }
        }
    }
    return false
}

/* ------------------------------------------------------------------
 * 排版实现
 * ---------------------------------------------------------------- */

internal fun buildMathDisplay(raw: String): MathDisplay = MathDisplayBuilder(raw).build()

private class MathDisplayBuilder(private val src: String) {

    private val out = StringBuilder()
    private val origToTrans = IntArray(src.length + 1) { -1 }
    private val styles = mutableListOf<Triple<Int, Int, SpanStyle>>()
    private val scripts = mutableListOf<MathScriptSpan>()
    private val radicals = mutableListOf<MathRadicalSpan>()
    private var t = 0

    fun build(): MathDisplay {
        renderRange(0, src.length, scriptDepth = 0, radicalDepth = 0)

        // 安全网：保证每个原始偏移都有映射且整体单调（理论上循环里都会写到）
        if (origToTrans[0] < 0) origToTrans[0] = 0
        var last = origToTrans[0]
        for (o in origToTrans.indices) {
            if (origToTrans[o] < 0) origToTrans[o] = last else last = origToTrans[o]
        }

        // 末尾的 {0,10} 只是定义域注解：比公式本体小一号，一眼能看出「这不是函数的一部分」
        if (DomainSuffix.parse(src).domain != null) {
            val open = src.lastIndexOf('{')
            val startT = origToTrans.getOrElse(open) { out.length }.coerceIn(0, out.length)
            if (out.length > startT) styles += Triple(startT, out.length, DOMAIN_SUFFIX_STYLE)
        }

        val text = out.toString()
        val annotated = buildAnnotatedString {
            append(text)
            styles.forEach { (s, e, style) -> if (e > s) addStyle(style, s, e) }
        }

        val transToOrig = buildTransToOrig(text.length)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                origToTrans[offset.coerceIn(0, src.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transToOrig[offset.coerceIn(0, text.length)]
        }

        return MathDisplay(src, text, annotated, scripts.toList(), radicals.toList(), mapping, origToTrans)
    }

    /** 显示位置 → 原始位置的映射：一个显示位置对应多个原始位置时取最靠后的那个。 */
    private fun buildTransToOrig(displayLength: Int): IntArray {
        val best = IntArray(displayLength + 1) { -1 }
        for (o in origToTrans.indices.reversed()) {
            val target = origToTrans[o].coerceIn(0, displayLength)
            if (best[target] < 0) best[target] = o
        }
        val result = IntArray(displayLength + 1)
        var last = 0
        for (i in result.indices) {
            if (best[i] >= 0) last = best[i]
            result[i] = last
        }
        return result
    }

    private fun mark(offset: Int) {
        if (offset in origToTrans.indices) origToTrans[offset] = t
    }

    private fun appendChunk(chunk: String, style: SpanStyle? = null) {
        if (chunk.isEmpty()) return
        val start = t
        out.append(chunk)
        t += chunk.length
        if (style != null && t > start) styles += Triple(start, t, style)
    }

    /**
     * 把一个原始文本区间渲染到显示文本里，同时记录每个原始偏移的新位置。
     * 递归只发生在根号内容与指数内容上。
     */
    private fun renderRange(start: Int, end: Int, scriptDepth: Int, radicalDepth: Int) {
        if (start >= end) return
        mark(start)
        var i = start
        while (i < end) {
            when {
                isSqrtCall(i, end) -> {
                    val close = findMatchingParen(i + 4, end)
                    renderRadical(i, close, end, scriptDepth, radicalDepth)
                    i = if (close != null) close + 1 else end
                }

                src[i] == '^' -> {
                    val exponentEnd = exponentEnd(i + 1, end)
                    renderPower(i, exponentEnd, scriptDepth, radicalDepth)
                    i = exponentEnd.coerceAtLeast(i + 1)
                }

                else -> {
                    appendChunk(src[i].toString())
                    i += 1
                    mark(i)
                }
            }
        }
    }

    /** 「sqrt(」开头且前面的字符不是字母（避免把 `asqrt(x)` 里的 sqrt 当函数）。 */
    private fun isSqrtCall(index: Int, end: Int): Boolean {
        if (index + 5 > end) return false
        if (!src.startsWith("sqrt(", index)) return false
        return index == 0 || !src[index - 1].isLetter()
    }

    /** 从开括号出发找配对的闭括号；找不到（还没输完）返回 null。 */
    private fun findMatchingParen(open: Int, end: Int): Int? {
        if (open >= end || src[open] != '(') return null
        var depth = 0
        var i = open
        while (i < end) {
            when (src[i]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return i
                }
            }
            i += 1
        }
        return null
    }

    /**
     * 一根根号：`sqrt(内容)` → 「√ + 内容」。
     * 括号内的内容递归排版，所以根号套根号、根号里的乘方都能正常显示。
     */
    private fun renderRadical(
        sqrtStart: Int,
        closeParen: Int?,
        end: Int,
        scriptDepth: Int,
        radicalDepth: Int,
    ) {
        mark(sqrtStart)
        appendChunk("√")

        // "sqrt(" 的剩下 4 个字符（"qrt("）都不显示：光标落进来就视作在根号内容里
        val contentStart = sqrtStart + 5
        for (o in sqrtStart + 1..contentStart) {
            if (o in origToTrans.indices) origToTrans[o] = t
        }

        val contentEnd = (closeParen ?: end).coerceIn(contentStart, end)
        val displayStart = t
        if (contentEnd > contentStart) {
            renderRange(contentStart, contentEnd, scriptDepth, radicalDepth + 1)
        } else {
            // 空的被开方数：一个空格撑出位置，横线由画布画
            appendChunk(" ")
        }
        val displayEnd = t

        radicals += MathRadicalSpan(
            sqrtIndex = displayStart - 1,
            start = displayStart,
            end = displayEnd,
            origStart = contentStart,
            origEnd = contentEnd,
            depth = radicalDepth,
            raised = scriptDepth > 0,
            placeholder = contentEnd == contentStart,
        )

        if (closeParen != null) {
            // 闭括号不显示；光标在它后面时视作在根号外，方便继续写 + × 等运算符
            mark(closeParen + 1)
        }
    }

    /**
     * 乘方：`^指数`。`^` 不显示，指数整体缩小抬高。
     * 指数内容允许：数字/小数、字母（可带函数调用）、带正负号、括号组；
     * 还没输完（比如只输了个负号）也按指数显示，避免中间态闪回 `x^-`。
     */
    private fun renderPower(
        caretIndex: Int,
        exponentEnd: Int,
        scriptDepth: Int,
        radicalDepth: Int,
    ) {
        mark(caretIndex)
        // ^ 前后的光标都落到角标内容起点，点一下就能进右上角编辑
        mark(caretIndex + 1)

        val contentStart = caretIndex + 1
        val contentEnd = exponentEnd.coerceIn(contentStart, src.length)
        val displayStart = t
        if (contentEnd > contentStart) {
            renderRange(contentStart, contentEnd, scriptDepth + 1, radicalDepth)
        } else {
            appendChunk(" ", superscriptStyle)
        }
        val displayEnd = t
        if (displayEnd > displayStart) {
            styles += Triple(displayStart, displayEnd, superscriptStyle)
        }

        scripts += MathScriptSpan(
            start = displayStart,
            end = displayEnd,
            placeholder = contentEnd == contentStart,
            origStart = contentStart,
            origEnd = contentEnd,
        )
    }

    /**
     * 指数内容的结束下标（不含）：尽量把学生正在输入的东西都算进角标里。
     * 后面不是合法的指数开头时返回 from，表示这是一个空的上标槽。
     */
    private fun exponentEnd(from: Int, end: Int): Int {
        if (from >= end) return from
        var j = from
        if (src[j] == '+' || src[j] == '-') {
            j += 1
            if (j >= end) return j
        }
        return when {
            src[j].isDigit() || src[j] == '.' -> {
                while (j < end && (src[j].isDigit() || src[j] == '.')) j += 1
                j
            }

            src[j].isLetter() -> {
                while (j < end && src[j].isLetter()) j += 1
                if (j < end && src[j] == '(') {
                    val close = findMatchingParen(j, end)
                    if (close != null) close + 1 else end
                } else {
                    j
                }
            }

            src[j] == '(' -> findMatchingParen(j, end)?.plus(1) ?: end

            else -> from
        }
    }
}

/* ------------------------------------------------------------------
 * 画布：根号横线 + 空角标的虚线框
 * ---------------------------------------------------------------- */

/**
 * 在表达式文字上方画根号横线和「空上标槽」的虚线框。
 *
 * 根号用文本里的 √ 字形做钩子，横线从 √ 右上角向右延伸盖住被开方内容，
 * 高度按行高估算；嵌套根号逐层下移一点，避免两条横线糊在一起。
 *
 * 光标落在角标或根号里时，还会把那一整块罩上一层淡色 + 细边框（见 [drawActiveRegion]），
 * 拖动光标时就能看清「现在编辑的是哪一层」。
 */
internal fun DrawScope.drawMathDecorations(
    layout: TextLayoutResult?,
    display: MathDisplay?,
    caret: Int,
    region: MathEditRegion,
    regionColor: Color,
    lineColor: Color,
    slotColor: Color,
) {
    if (layout == null || display == null) return
    val textLength = layout.layoutInput.text.length
    if (textLength <= 0) return

    // 先铺区域底色，再画根号横线：横线不会被底色盖住
    drawActiveRegion(layout, display, caret, region, regionColor, textLength)

    val fontPx = layout.layoutInput.style.fontSize.let {
        if (it.isSp) it.toPx() else 16.sp.toPx()
    }

    display.radicals.forEach { radical ->
        if (radical.start > textLength || radical.sqrtIndex >= textLength) return@forEach
        val line = layout.getLineForOffset(radical.sqrtIndex.coerceIn(0, textLength - 1))
        val contentOffset = (radical.end - 1).coerceIn(0, textLength - 1)
        if (layout.getLineForOffset(contentOffset) != line) return@forEach

        val lineTop = layout.getLineTop(line)
        val lineHeight = (layout.getLineBottom(line) - lineTop).coerceAtLeast(1f)
        var barY = lineTop + lineHeight * 0.10f
        if (radical.raised) barY -= fontPx * 0.34f
        barY += radical.depth * 4.dp.toPx()

        val startX = layout.getHorizontalPosition(radical.sqrtIndex, usePrimaryDirection = false)
        val glyphRight = startX + (layout.getHorizontalPosition(radical.sqrtIndex, true) - startX)
        val left = if (glyphRight > startX) {
            // √ 字符的右边界：下一字符的起点
            layout.getHorizontalPosition(radical.sqrtIndex + 1, usePrimaryDirection = false)
        } else {
            startX + fontPx * 0.6f
        }
        val right = layout.getHorizontalPosition(radical.end, usePrimaryDirection = true)
        val end = (right + 1.5.dp.toPx()).coerceAtMost(size.width)
        val begin = left.coerceIn(0f, end)
        if (end - begin < 2.dp.toPx()) return@forEach
        drawLine(
            color = lineColor,
            start = Offset(begin, barY),
            end = Offset(end, barY),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // 空的上标槽：光标真的落在里面时才画一个紧凑的虚线框（不是长条）
    val activeScript = display.scripts.firstOrNull { caret >= it.origStart && caret <= it.origEnd }
    if (activeScript != null && activeScript.placeholder && activeScript.start < textLength) {
        val line = layout.getLineForOffset(activeScript.start)
        val lineTop = layout.getLineTop(line)
        val lineHeight = (layout.getLineBottom(line) - lineTop).coerceAtLeast(1f)
        val startX = layout.getHorizontalPosition(activeScript.start, usePrimaryDirection = false)
        val endX = layout.getHorizontalPosition(
            (activeScript.start + 1).coerceAtMost(textLength),
            usePrimaryDirection = true,
        )
        val width = (endX - startX).coerceAtLeast(8.dp.toPx())
        val height = (lineHeight * 0.46f).coerceAtLeast(10.dp.toPx())
        drawRoundRect(
            color = slotColor,
            topLeft = Offset(startX, lineTop + lineHeight * 0.14f),
            size = Size(width, height),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            ),
        )
    }
}

/**
 * 把光标所在的编辑区整块罩一层淡色。
 *
 * 这是「上标 / 根号内 / 基线」最直观的区分：上标是一小块抬高的框，根号内是横线底下的整块，
 * 形状和位置本来就不同，再配上各自的颜色，扫一眼就知道光标在哪一层。
 * 空的上标槽不画（它已经有虚线框），基线也不画（整行罩色没有信息量）。
 */
private fun DrawScope.drawActiveRegion(
    layout: TextLayoutResult,
    display: MathDisplay,
    caret: Int,
    region: MathEditRegion,
    color: Color,
    textLength: Int,
) {
    if (region == MathEditRegion.BASE) return

    val spanStart: Int
    val spanEnd: Int
    val topFactor: Float
    val bottomFactor: Float
    when (region) {
        MathEditRegion.SCRIPT -> {
            val script = display.scripts
                .firstOrNull { caret >= it.origStart && caret <= it.origEnd }
                ?: return
            if (script.placeholder) return
            spanStart = script.start
            spanEnd = script.end
            topFactor = 0.10f
            bottomFactor = 0.56f
        }

        MathEditRegion.RADICAL -> {
            val radical = display.radicals
                .firstOrNull { caret >= it.origStart && caret <= it.origEnd }
                ?: return
            spanStart = radical.start
            spanEnd = radical.end
            // 从根号横线下面一点开始，到基线附近收尾
            topFactor = 0.16f
            bottomFactor = 0.94f
        }

        else -> return
    }

    if (spanEnd <= spanStart) return
    val line = layout.getLineForOffset(spanStart.coerceIn(0, textLength - 1))
    if (layout.getLineForOffset(spanEnd.coerceIn(0, textLength - 1)) != line) return

    val lineTop = layout.getLineTop(line)
    val lineHeight = (layout.getLineBottom(line) - lineTop).coerceAtLeast(1f)
    val left = (layout.getHorizontalPosition(spanStart, usePrimaryDirection = false) - 2.dp.toPx())
        .coerceAtLeast(0f)
    val right = (layout.getHorizontalPosition(spanEnd, usePrimaryDirection = true) + 2.dp.toPx())
        .coerceAtMost(size.width)
    val top = lineTop + lineHeight * topFactor
    val bottom = lineTop + lineHeight * bottomFactor
    if (right - left < 2.dp.toPx() || bottom - top < 2.dp.toPx()) return

    val corner = CornerRadius(4.dp.toPx())
    val boxSize = Size(right - left, bottom - top)
    drawRoundRect(
        color = color.copy(alpha = 0.13f),
        topLeft = Offset(left, top),
        size = boxSize,
        cornerRadius = corner,
    )
    drawRoundRect(
        color = color.copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = boxSize,
        cornerRadius = corner,
        style = Stroke(1.dp.toPx()),
    )
}

/**
 * 自绘光标。
 *
 * 原生 BasicTextField 的光标总是整行高，在右上角角标 / 根号内容里编辑时会明显偏高、
 * 压到根号横线或超出角标范围。这里把原生光标设成透明，自己按「光标所在的编辑区域」
 * 画一根高度合适的光标：
 * - 指数里 → 只占上标那一小条；
 * - 根号内容里 → 从根号横线下方到基线，不越过横线；
 * - 普通位置 → 正常文字高度。
 *
 * @param originalCaret 原始文本（未排版）里的光标偏移
 * @param region 光标所在编辑区，决定光标高度与颜色（颜色由调用方按区域传入）
 * @param alpha 闪烁透明度，由调用方的动画驱动
 */
internal fun DrawScope.drawMathCaret(
    layout: TextLayoutResult?,
    display: MathDisplay?,
    originalCaret: Int,
    region: MathEditRegion,
    color: Color,
    alpha: Float,
) {
    if (layout == null || display == null) return
    val textLength = layout.layoutInput.text.length
    if (layout.lineCount <= 0) return

    val displayCaret = display.originalToTransformed(originalCaret).coerceIn(0, textLength)
    val lineIndex = layout.getLineForOffset(displayCaret.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
    val lineTop = layout.getLineTop(lineIndex)
    val lineHeight = (layout.getLineBottom(lineIndex) - lineTop).coerceAtLeast(1f)
    val x = layout.getHorizontalPosition(displayCaret, usePrimaryDirection = true)

    val top: Float
    val height: Float
    when (region) {
        MathEditRegion.SCRIPT -> {
            top = lineTop + lineHeight * 0.12f
            height = lineHeight * 0.44f
        }

        MathEditRegion.RADICAL -> {
            // 根号横线画在 lineTop + 10% 行高的位置，光标从它下面开始
            top = lineTop + lineHeight * 0.20f
            height = lineHeight * 0.60f
        }

        MathEditRegion.BASE -> {
            top = lineTop + lineHeight * 0.08f
            height = lineHeight * 0.84f
        }
    }

    val caretWidth = 1.6.dp.toPx()
    val radius = CornerRadius(caretWidth / 2f)
    drawRoundRect(
        color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
        topLeft = Offset(x - caretWidth / 2f, top),
        size = Size(caretWidth, height),
        cornerRadius = radius,
    )
}

/**
 * 自绘的光标手柄（水滴形），画在光标正下方。
 *
 * 系统原生手柄在「右上角上标」「根号内容」这类被排版抬高/缩小的区域里经常不出现，
 * 或者出现后拖不动。这里自己画一个位置固定的手柄：无论光标在基线、上标还是根号里，
 * 手柄都落在同一行的下方，配合 [MathScreen] 里的拖动逻辑就能一直按住拖动光标。
 *
 * 拖动时手柄旁边还会浮出一个小标签（基线 / 上标 / 根号内），
 * 手指压着手柄也能一眼看出这一次会落在哪一层——那个标签由 [drawMathDragLabel] 画在最上层。
 */
internal fun DrawScope.drawMathCaretHandle(
    layout: TextLayoutResult?,
    display: MathDisplay?,
    originalCaret: Int,
    color: Color,
) {
    if (layout == null || display == null) return
    val textLength = layout.layoutInput.text.length
    if (layout.lineCount <= 0) return

    val displayCaret = display.originalToTransformed(originalCaret).coerceIn(0, textLength)
    val lineIndex = layout.getLineForOffset(displayCaret.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
    val x = layout.getHorizontalPosition(displayCaret, usePrimaryDirection = true)
    val topY = layout.getLineBottom(lineIndex) + 1.dp.toPx()

    val radius = 5.dp.toPx()
    val stem = Path().apply {
        moveTo(x, topY)
        lineTo(x - radius * 0.62f, topY + radius * 1.05f)
        lineTo(x + radius * 0.62f, topY + radius * 1.05f)
        close()
    }
    drawPath(stem, color)
    // 手柄画在输入框「之上」：外框已经不裁剪内容，圆点可以正常探出边框，不必再缩小抬高
    drawCircle(color = color, radius = radius, center = Offset(x, topY + radius * 1.75f))
}

/**
 * 拖动光标时浮在手柄旁边的胶囊：「基线 / 上标 / 根号内」。
 *
 * 必须画在文字**上面**（调用方用 `drawWithContent` + `drawContent()` 之后画），
 * 否则会被表达式盖住、糊成一片。
 */
internal fun DrawScope.drawMathDragLabel(
    layout: TextLayoutResult?,
    display: MathDisplay?,
    originalCaret: Int,
    region: MathEditRegion,
    color: Color,
    surfaceColor: Color,
    measurer: TextMeasurer,
) {
    if (layout == null || display == null) return
    val textLength = layout.layoutInput.text.length
    if (layout.lineCount <= 0) return

    val displayCaret = display.originalToTransformed(originalCaret).coerceIn(0, textLength)
    val lineIndex = layout.getLineForOffset(displayCaret.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
    val x = layout.getHorizontalPosition(displayCaret, usePrimaryDirection = true)
    val anchor = Offset(x, layout.getLineBottom(lineIndex) + 1.dp.toPx() + 5.dp.toPx() * 1.75f)

    val textLayout = measurer.measure(
        text = AnnotatedString(region.label),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color),
    )
    val padH = 7.dp.toPx()
    val padV = 3.dp.toPx()
    val width = textLayout.size.width + padH * 2
    val height = textLayout.size.height + padV * 2

    // 默认放右边，右边放不下就翻到左边，再不行就贴边
    var left = anchor.x + 14.dp.toPx()
    if (left + width > size.width - 2.dp.toPx()) {
        left = (anchor.x - 14.dp.toPx() - width).coerceAtLeast(2.dp.toPx())
    }
    val top = (anchor.y - height / 2f).coerceIn(0f, (size.height - height).coerceAtLeast(0f))

    val corner = CornerRadius(height / 2f)
    val boxSize = Size(width, height)
    drawRoundRect(color = surfaceColor, topLeft = Offset(left, top), size = boxSize, cornerRadius = corner)
    drawRoundRect(
        color = color.copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = boxSize,
        cornerRadius = corner,
        style = Stroke(1.dp.toPx()),
    )
    drawText(textLayout, topLeft = Offset(left + padH, top + padV))
}

/* ------------------------------------------------------------------
 * 输入编辑逻辑
 *
 * 全部是纯函数：给定「文本 + 选区」算出新的「文本 + 选区」，方便单测。
 * 交互规则对齐常见数学输入器（Desmos 一类）：
 * - 函数键插入成对的括号并把光标放在里面；有选区则把选区包进去；
 * - 指数里的内容输完，直接按 + − × ÷ 会自动退出角标，运算符落在基线；
 * - 指数还是空的时候按运算符，把没用的 ^ 一并清掉（不会留下 `x^+`）。
 * ---------------------------------------------------------------- */

internal object MathEdit {

    data class Edit(val text: String, val caret: Int) {
        fun toTextFieldValue(): TextFieldValue = TextFieldValue(text, TextRange(caret))
    }

    private fun normalize(selection: TextRange, length: Int): IntRange {
        val a = selection.min.coerceIn(0, length)
        val b = selection.max.coerceIn(0, length)
        return a..b
    }

    /** 普通插入：有选区则替换选区，光标落在插入内容之后。 */
    fun insert(text: String, selection: TextRange, token: String): Edit {
        val range = normalize(selection, text.length)
        // 右括号 / 右花括号：函数键和 { 键已经自动补了配对，学生再按一次应该「跳过」，
        // 而不是又插入一个，变成 sqrt()) / {0,10}} 这种多一层的样子。
        if (range.first == range.last && range.first < text.length) {
            val next = text[range.first]
            if ((token == ")" && next == ')') || (token == "}" && next == '}')) {
                return Edit(text, range.first + 1)
            }
        }
        // 左花括号成对补上，光标落在里面：{0,10} 只要按「{ 0 , 10 }」五下
        if (token == "{") {
            val newText = text.substring(0, range.first) + "{}" + text.substring(range.last)
            return Edit(newText, range.first + 1)
        }
        val newText = text.substring(0, range.first) + token + text.substring(range.last)
        return Edit(newText, range.first + token.length)
    }

    /**
     * 函数键：sin/cos/ln/√ 之类。
     * 没有选区 → 插入「名字()」并把光标放括号里，省掉学生漏闭括号；
     * 有选区 → 把选区包进括号，光标停在闭括号后面，方便继续写。
     */
    fun insertFunction(text: String, selection: TextRange, name: String): Edit {
        val range = normalize(selection, text.length)
        val open = if (name == "sqrt") "sqrt(" else "$name("
        return if (range.first == range.last) {
            val newText = text.substring(0, range.first) + open + ")" + text.substring(range.first)
            Edit(newText, range.first + open.length)
        } else {
            val chosen = text.substring(range.first, range.last)
            val newText = text.substring(0, range.first) + open + chosen + ")" + text.substring(range.last)
            Edit(newText, range.first + open.length + chosen.length + 1)
        }
    }

    /**
     * 乘方键：
     * - 没有选区 → 插入 `^`，光标进入右上角；
     * - 有选区 → 把选区当底数括起来再上标：`(选区)^`，光标停在指数槽里。
     */
    fun insertPower(text: String, selection: TextRange): Edit {
        val range = normalize(selection, text.length)
        return if (range.first == range.last) {
            val newText = text.substring(0, range.first) + "^" + text.substring(range.first)
            Edit(newText, range.first + 1)
        } else {
            val chosen = text.substring(range.first, range.last)
            val newText = text.substring(0, range.first) + "(" + chosen + ")^" + text.substring(range.last)
            Edit(newText, range.first + chosen.length + 3)
        }
    }

    /**
     * 运算符键：+ − × ÷ = 等。
     *
     * 唯一需要「智能处理」的情况：光标停在一个**空的指数槽**上
     * （原文形如 `x^`，光标在末尾）。这时按运算符说明学生其实想写基线运算，
     * 于是把多余的 `^` 删掉再插入，避免出现 `x^+` 这种残废状态。
     */
    fun insertOperator(text: String, selection: TextRange, op: String): Edit {
        val range = normalize(selection, text.length)
        if (range.first != range.last) {
            val newText = text.substring(0, range.first) + op + text.substring(range.last)
            return Edit(newText, range.first + op.length)
        }
        val caret = range.first
        if (caret > 0 && caret == text.length && text[caret - 1] == '^') {
            val newText = text.substring(0, caret - 1) + op
            return Edit(newText, caret - 1 + op.length)
        }
        val newText = text.substring(0, caret) + op + text.substring(caret)
        return Edit(newText, caret + op.length)
    }

    /** 退格：有选区删选区；否则删前一个字符（退掉 ^ 时自然回到基线）。 */
    fun backspace(text: String, selection: TextRange): Edit {
        val range = normalize(selection, text.length)
        if (range.first != range.last) {
            return Edit(text.substring(0, range.first) + text.substring(range.last), range.first)
        }
        val caret = range.first
        if (caret <= 0) return Edit(text, 0)

        // 光标在「函数()」里、括号又是空的：整段删掉，别留下 sqrt) 这种残废中间态
        if (caret >= 2 && caret < text.length && text[caret - 1] == '(' && text[caret] == ')') {
            var nameStart = caret - 1
            while (nameStart > 0 && text[nameStart - 1].isLetter()) nameStart -= 1
            if (nameStart < caret - 1) {
                val name = text.substring(nameStart, caret - 1)
                if (name in FUNCTION_NAMES) {
                    return Edit(text.removeRange(nameStart, caret + 1), nameStart)
                }
            }
        }

        // 光标停在根号的收尾括号后面：这个 ')' 屏幕上不显示，直接删它等于「点了没反应」，
        // 还会立刻报「括号没有闭合」。跳过它，去删前面那个看得见的字符；
        // 连着的多层根号一起跳过（sqrt(sqrt(6)) 退格应该先吃掉 6）。
        var cut = caret
        while (cut > 0 && isRadicalCloseParen(text, cut - 1)) cut -= 1
        if (cut < caret) {
            val openIndex = cut - 1
            if (openIndex >= 0 && text[openIndex] == '(') {
                // 空的被开方数：整段 sqrt() 一起删掉，不留孤零零的 sqrt(
                val callStart = openIndex - 4
                return if (callStart >= 0 && text.startsWith("sqrt(", callStart)) {
                    Edit(text.removeRange(callStart, cut + 1), callStart)
                } else {
                    Edit(text.substring(0, openIndex) + text.substring(cut), openIndex)
                }
            }
            return Edit(text.substring(0, cut - 1) + text.substring(cut), cut - 1)
        }

        return Edit(text.substring(0, caret - 1) + text.substring(caret), caret - 1)
    }

    /** 方向键移动光标：在原始文本里走一格，跨过隐藏字符也只需按一次。 */
    fun moveCaret(text: String, caret: Int, delta: Int): Int =
        (caret + delta).coerceIn(0, text.length)
}
