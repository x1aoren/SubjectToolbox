package cn.stb.feature.math

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.BaselineShift
import cn.stb.core.model.niceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionTest {

    private fun evalAt(input: String, x: Double, params: Map<String, Double> = emptyMap()): Double =
        parseExpression(input).eval(x, params)

    /* ---------------- 解析器 ---------------- */

    @Test
    fun `隐式乘号 - 2x`() {
        assertEquals(6.0, evalAt("2x", 3.0), 1e-9)
    }

    @Test
    fun `隐式乘号 - 3(x+1)`() {
        assertEquals(9.0, evalAt("3(x+1)", 2.0), 1e-9)
    }

    @Test
    fun `负号与幂的优先级 - -x^2 是 -(x^2)`() {
        assertEquals(-4.0, evalAt("-x^2", 2.0), 1e-9)
    }

    @Test
    fun `幂是右结合 - 2^3^2 = 512`() {
        assertEquals(512.0, evalAt("2^3^2", 0.0), 1e-9)
    }

    @Test
    fun `三角函数与常数`() {
        assertEquals(0.0, evalAt("sin(0)", 0.0), 1e-9)
        assertEquals(0.0, evalAt("cos(pi/2)", 0.0), 1e-9)
        assertEquals(2.0, evalAt("sqrt(4)", 0.0), 1e-9)
    }

    @Test
    fun `参数代入`() {
        assertEquals(7.0, evalAt("a*x+b", 1.0, mapOf("a" to 2.0, "b" to 5.0)), 1e-9)
    }

    @Test
    fun `自由变量识别`() {
        assertEquals(listOf("a", "x", "b"), parseExpression("a*x+b").variables())
        assertEquals(emptyList<String>(), parseExpression("x^2+1").variables().filter { it != "x" })
    }

    @Test(expected = ParseException::class)
    fun `不完整的表达式抛错`() {
        parseExpression("2+")
    }

    @Test(expected = ParseException::class)
    fun `括号不闭合抛错`() {
        parseExpression("sin(x")
    }

    @Test
    fun `1-x 在渐近线处断开成两段`() {
        val expr = parseExpression("1/x")
        val segments = expr.sampleSegments(
            xMin = -1.0,
            xMax = 1.0,
            count = 400,
            params = emptyMap(),
            yMin = -5.0,
            yMax = 5.0,
        )
        assertTrue("应当至少两段，实际 ${segments.size}", segments.size >= 2)
    }

    @Test
    fun `连续函数只有一段`() {
        val expr = parseExpression("x^2")
        val segments = expr.sampleSegments(-3.0, 3.0, 300, emptyMap(), -2.0, 10.0)
        assertEquals(1, segments.size)
    }

    @Test
    fun `刻度间隔取 1-2-5 序列`() {
        assertEquals(1.0, niceStep(10.0, 10), 1e-9)
        assertEquals(2.0, niceStep(20.0, 10), 1e-9)
        assertEquals(5.0, niceStep(50.0, 10), 1e-9)
    }

    @Test
    fun `lg 是常用对数`() {
        assertEquals(2.0, evalAt("lg(100)", 0.0), 1e-9)
    }

    /* ---------------- 显式 / 隐式解析 ---------------- */

    @Test
    fun `显式模式接受 y 等于前缀`() {
        assertTrue(parseCurve(FnMode.EXPLICIT, "y=sin(x)") is CurveDef.Explicit)
    }

    @Test
    fun `显式模式拒绝含别的等号并提示切换`() {
        val error = runCatching { parseCurve(FnMode.EXPLICIT, "x^2+y^2=25") }.exceptionOrNull()
        assertTrue("应当抛 ParseException，实际 $error", error is ParseException)
        assertTrue("错误信息应提示切换隐式：${error?.message}", error?.message?.contains("隐式") == true)
    }

    @Test
    fun `显式模式拒绝只有 y 的表达式并提示切换`() {
        val error = runCatching { parseCurve(FnMode.EXPLICIT, "y^2") }.exceptionOrNull()
        assertTrue("显式函数里出现 y 应当报错，实际 $error", error is ParseException)
        assertTrue("错误信息应提示切换隐式：${error?.message}", error?.message?.contains("隐式") == true)
    }

    @Test
    fun `隐式模式把方程移项成 F 等于 0`() {
        assertTrue(parseCurve(FnMode.IMPLICIT, "x^2+y^2=25") is CurveDef.Implicit)
        assertTrue(parseCurve(FnMode.IMPLICIT, "sin(x)") is CurveDef.Implicit)
    }

    /* ---------------- 显示层：上标 ---------------- */

    private fun display(raw: String) = buildMathDisplay(raw)

    @Test
    fun `上标显示去掉尖号并抬高指数`() {
        val d = display("x^2+1")
        assertEquals("x2+1", d.text)

        val span = d.annotated.spanStyles.single()
        assertEquals(1, span.start)
        assertEquals(2, span.end)
        assertEquals(BaselineShift.Superscript, span.item.baselineShift)

        // 光标落在 ^ 之后时进到指数起点；指数之后的位置可以正常继续输入
        assertEquals(1, d.mapping.originalToTransformed(2))
        assertEquals(2, d.mapping.originalToTransformed(3))
        assertEquals(4, d.mapping.originalToTransformed(5))
        // 从显示位置反推：指数末尾之后 = 原始文本指数之后（不是指数里面）
        assertEquals(3, d.mapping.transformedToOriginal(2))
    }

    @Test
    fun `末尾的尖号留一个紧凑的空上标槽`() {
        val d = display("x^")
        assertEquals("x ", d.text)

        val script = d.scripts.single()
        assertTrue("空槽应该标记为 placeholder", script.placeholder)
        assertEquals(1, d.mapping.originalToTransformed(2))
        // 点一下空槽，光标在槽里（原始位置在 ^ 之后），输入数字就是填进指数
        assertEquals(2, d.mapping.transformedToOriginal(1))
    }

    @Test
    fun `带符号和括号的指数整体抬高`() {
        assertEquals("x-2", display("x^-2").text)
        assertEquals("x(a+1)", display("x^(a+1)").text)
        assertEquals("xsin(x)", display("x^sin(x)").text)
    }

    @Test
    fun `只输了符号的指数也保持在角标里`() {
        // 中间态 x^- 不能闪回普通文本，否则学生没法接着输入 -2
        val d = display("x^-")
        assertEquals("x-", d.text)
        val script = d.scripts.single()
        assertTrue("有内容的槽不算 placeholder", !script.placeholder)
    }

    /* ---------------- 显示层：开方 ---------------- */

    @Test
    fun `开方显示成根号并隐藏 sqrt 与右括号`() {
        val d = display("sqrt(x+1)")
        assertEquals("√x+1", d.text)

        val rad = d.radicals.single()
        assertEquals(0, rad.sqrtIndex)
        assertEquals(1, rad.start)
        assertEquals(4, rad.end)
        assertTrue("内容非空", !rad.placeholder)

        // 被开方内容里的光标：原始 5（x 前）、6（+ 前）、8（右括号前）
        assertEquals(1, d.mapping.originalToTransformed(5))
        assertEquals(2, d.mapping.originalToTransformed(6))
        // 点击根号后面应该落在根号外，方便接着写 + 号
        assertEquals(4, d.mapping.originalToTransformed(9))
        assertEquals(9, d.mapping.transformedToOriginal(4))
    }

    @Test
    fun `空根号留一个占位槽`() {
        val d = display("sqrt()")
        assertEquals("√ ", d.text)
        assertTrue(d.radicals.single().placeholder)
        // 光标在括号里：原始 5 是 ( 与 ) 之间，落在根号内容起点
        assertEquals(1, d.mapping.originalToTransformed(5))
        // 点击根号后面 = 右括号之后
        assertEquals(6, d.mapping.transformedToOriginal(2))
    }

    @Test
    fun `嵌套根号按层级记录`() {
        val d = display("sqrt(sqrt(x))")
        assertEquals("√√x", d.text)
        assertEquals(2, d.radicals.size)
        // 内层 depth 1，外层 depth 0
        assertEquals(listOf(1, 0), d.radicals.map { it.depth })
    }

    @Test
    fun `根号里的乘方仍然抬高`() {
        val d = display("sqrt(x^2)")
        assertEquals("√x2", d.text)
        assertEquals(1, d.scripts.size)
        assertEquals(1, d.radicals.size)
    }

    @Test
    fun `根号与后面的加法可以共存`() {
        val d = display("sqrt(x)+1")
        assertEquals("√x+1", d.text)
        // 显示位置 2 = 根号内容之后 + 号之前；反推原始位置应落在右括号之后（根号外）
        assertEquals(7, d.mapping.transformedToOriginal(2))
    }

    /* ---------------- 显示层：绝对值 ---------------- */

    @Test
    fun `绝对值显示成两根竖线并隐藏 abs`() {
        val d = display("abs(x+1)")
        assertEquals("|x+1|", d.text)

        val span = d.absolutes.single()
        assertEquals(1, span.start)
        assertEquals(4, span.end)
        assertTrue("内容非空", !span.placeholder)

        // 原始 4 是内容起点，落在左竖线后面
        assertEquals(1, d.mapping.originalToTransformed(4))
        // 右括号之后是整个绝对值之后
        assertEquals(5, d.mapping.originalToTransformed(8))
        assertEquals(8, d.mapping.transformedToOriginal(5))

        assertEquals(MathEditRegion.ABS, d.regionAt(4))
        assertEquals(MathEditRegion.BASE, d.regionAt(0))
        assertEquals(MathEditRegion.BASE, d.regionAt(8))
    }

    @Test
    fun `空绝对值留一个方框且光标在框里`() {
        val d = display("abs()")
        assertEquals("| |", d.text)
        assertTrue(d.absolutes.single().placeholder)

        // 光标在括号里（原始 4）落在方框起点
        assertEquals(1, d.mapping.originalToTransformed(4))
        assertEquals(4, d.mapping.transformedToOriginal(1))
        assertEquals(MathEditRegion.ABS, d.regionAt(4))
    }

    @Test
    fun `绝对值与根号互相嵌套时按最内层算`() {
        val absInSqrt = display("sqrt(abs(x))")
        assertEquals("√|x|", absInSqrt.text)
        assertEquals(2, absInSqrt.radicals.size + absInSqrt.absolutes.size)
        assertEquals(MathEditRegion.ABS, absInSqrt.regionAt(9))

        val sqrtInAbs = display("abs(sqrt(x))")
        assertEquals("|√x|", sqrtInAbs.text)
        assertEquals(MathEditRegion.RADICAL, sqrtInAbs.regionAt(9))
        assertEquals(MathEditRegion.ABS, sqrtInAbs.regionAt(4))
    }

    /* ---------------- 输入逻辑 ---------------- */

    @Test
    fun `开方键插入成对括号且光标在根号内`() {
        val edit = MathEdit.insertFunction("x+1", TextRange(3), "sqrt")
        assertEquals("x+1sqrt()", edit.text)
        assertEquals(8, edit.caret)
    }

    @Test
    fun `函数键包裹选区`() {
        val edit = MathEdit.insertFunction("x+1", TextRange(0, 3), "sin")
        assertEquals("sin(x+1)", edit.text)
        assertEquals(8, edit.caret)
    }

    @Test
    fun `绝对值键插入空结构并能包裹选区`() {
        val empty = MathEdit.insertFunction("x+1", TextRange(3), "abs")
        assertEquals("x+1abs()", empty.text)
        assertEquals(7, empty.caret)
        assertEquals(MathEditRegion.ABS, buildMathDisplay(empty.text).regionAt(empty.caret))

        val wrapped = MathEdit.insertFunction("x+1", TextRange(0, 3), "abs")
        assertEquals("abs(x+1)", wrapped.text)
        assertEquals(8, wrapped.caret)
        assertEquals("|x+1|", buildMathDisplay(wrapped.text).text)
    }

    @Test
    fun `乘方键包裹选区当底数`() {
        val edit = MathEdit.insertPower("x+1", TextRange(0, 3))
        assertEquals("(x+1)^", edit.text)
        assertEquals(6, edit.caret)
    }

    @Test
    fun `指数为空时按运算符会清掉多余的尖号`() {
        val edit = MathEdit.insertOperator("x^", TextRange(2), "+")
        assertEquals("x+", edit.text)
        assertEquals(2, edit.caret)
    }

    @Test
    fun `指数写完按加号落在角标后面`() {
        val edit = MathEdit.insertOperator("x^2", TextRange(3), "+")
        assertEquals("x^2+", edit.text)
        assertEquals(4, edit.caret)
    }

    @Test
    fun `再按右括号会跳过自动补的那个括号`() {
        // sqrt() 里光标停在右括号前，按 ) 只移动光标，不插入新括号
        val skip = MathEdit.insert("sqrt()", TextRange(5), ")")
        assertEquals("sqrt()", skip.text)
        assertEquals(6, skip.caret)
        // 光标后面没有右括号时正常插入
        val normal = MathEdit.insert("x", TextRange(1), ")")
        assertEquals("x)", normal.text)
        assertEquals(2, normal.caret)
    }

    @Test
    fun `退格删除前一个字符`() {
        val edit = MathEdit.backspace("x^2", TextRange(3))
        assertEquals("x^", edit.text)
        assertEquals(2, edit.caret)
    }

    @Test
    fun `退格删掉空的函数括号整段`() {
        // 刚按了 √ 还没输入内容就反悔：一次退格应该整段删掉，而不是留下 sqrt)
        val edit = MathEdit.backspace("sqrt()", TextRange(5))
        assertEquals("", edit.text)
        assertEquals(0, edit.caret)

        val wrapped = MathEdit.backspace("2sqrt()", TextRange(6))
        assertEquals("2", wrapped.text)
        assertEquals(1, wrapped.caret)
    }

    @Test
    fun `退格不吃根号那个看不见的收尾括号`() {
        // sqrt(6986) 显示成 √6986，收尾的 ) 是隐藏的：退格应该吃掉 6，而不是删掉一个看不见的字符、
        // 留下 sqrt(6986 然后提示括号没有闭合
        val radicand = MathEdit.backspace("sqrt(6986)", TextRange(10))
        assertEquals("sqrt(698)", radicand.text)
        // 光标停在被开方内容里（收尾括号之前），接着输入还在根号内
        assertEquals(8, radicand.caret)

        // 光标落在收尾括号后面、根号里又已经空了：整段 √ 一起退掉
        val empty = MathEdit.backspace("sqrt()", TextRange(6))
        assertEquals("", empty.text)
        assertEquals(0, empty.caret)

        // 根号后面还有东西时，只吃掉根号内容，闭括号保留
        val trailing = MathEdit.backspace("sqrt(x)+1", TextRange(7))
        assertEquals("sqrt()+1", trailing.text)
        assertEquals(5, trailing.caret)

        // 嵌套根号：连着跳过两层隐藏括号，先删里面的 6
        val nested = MathEdit.backspace("sqrt(sqrt(6))", TextRange(13))
        assertEquals("sqrt(sqrt())", nested.text)
        assertEquals(10, nested.caret)

        // 可见的右括号照常删掉：sin(x) 的括号、以及 asqrt(1) 这种不算根号的写法
        val visible = MathEdit.backspace("sin(x)", TextRange(6))
        assertEquals("sin(x", visible.text)
        assertEquals(5, visible.caret)

        val notRadical = MathEdit.backspace("asqrt(1)", TextRange(8))
        assertEquals("asqrt(1", notRadical.text)
        assertEquals(7, notRadical.caret)
    }

    @Test
    fun `花括号键成对补全并能跳过右花括号`() {
        // 按一下 { ：自动补 {}，光标落在里面
        val opened = MathEdit.insert("sin(x)", TextRange(6), "{")
        assertEquals("sin(x){}", opened.text)
        assertEquals(7, opened.caret)

        // 在 {} 里打完 0,10 后按 }：跳过已有的右花括号，不会变成 }}
        val typed = MathEdit.insert("sin(x){0,10}", TextRange(11), "}")
        assertEquals("sin(x){0,10}", typed.text)
        assertEquals(12, typed.caret)
    }

    @Test
    fun `光标所在编辑区按原始偏移判断`() {
        // 根号：被开方数两端都算「根号内」，根号后面才是基线。
        // 这里必须按原始文本算——收尾括号是隐藏的，映射到显示文本会把末尾算成根号外。
        val radical = buildMathDisplay("sqrt(1234)")
        assertEquals(MathEditRegion.RADICAL, radical.regionAt(5))
        assertEquals(MathEditRegion.RADICAL, radical.regionAt(9))
        assertEquals(MathEditRegion.BASE, radical.regionAt(10))

        // 上标：指数内容两端都是上标，^ 之前是基线
        val script = buildMathDisplay("x^2")
        assertEquals(MathEditRegion.SCRIPT, script.regionAt(2))
        assertEquals(MathEditRegion.SCRIPT, script.regionAt(3))
        assertEquals(MathEditRegion.BASE, script.regionAt(0))

        // 刚按下 ^ 还没输指数：空槽也算上标
        assertEquals(MathEditRegion.SCRIPT, buildMathDisplay("x^").regionAt(2))

        // 角标套在根号后面：角标优先，根号里仍是根号
        val mixed = buildMathDisplay("sqrt(2)^2")
        assertEquals(MathEditRegion.RADICAL, mixed.regionAt(5))
        assertEquals(MathEditRegion.SCRIPT, mixed.regionAt(8))
    }

    @Test
    fun `定义域端点只接受常数表达式`() {
        assertTrue("x 这种变量不能当端点", parseDomainBound("x").isFailure)
        assertTrue("1/0 无意义", parseDomainBound("1/0").isFailure)
        assertTrue(parseDomainBound("1/2").isSuccess)
        assertEquals(0.5, parseDomainBound("1/2").getOrNull()!!, 1e-9)
        assertNull(parseDomainBound("  ").getOrNull())
    }

    @Test
    fun `方向键移动不越界`() {
        assertEquals(0, MathEdit.moveCaret("x^2", 0, -1))
        assertEquals(3, MathEdit.moveCaret("x^2", 3, 1))
        assertEquals(2, MathEdit.moveCaret("x^2", 2, 0))
    }

    @Test
    fun `没有参数的显示层不产生多余装饰`() {
        val d = display("sin(x)")
        assertEquals("sin(x)", d.text)
        assertTrue(d.scripts.isEmpty())
        assertTrue(d.radicals.isEmpty())
        assertNull(d.annotated.spanStyles.firstOrNull())
        assertNotNull(AnnotatedString(d.text))
    }
}
