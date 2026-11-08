package cn.stb.feature.math

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import cn.stb.core.model.niceStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionTest {

    private fun evalAt(input: String, x: Double, params: Map<String, Double> = emptyMap()): Double =
        parseExpression(input).eval(x, params)

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

    /* ---------------- 输入框里的上标显示 ---------------- */

    private fun transform(raw: String) = SuperscriptTransformation().filter(AnnotatedString(raw))

    @Test
    fun `上标显示去掉尖号并抬高指数`() {
        val out = transform("x^2+1")
        assertEquals("x2+1", out.text.text)

        val span = out.text.spanStyles.single()
        assertEquals(1, span.start)
        assertEquals(2, span.end)
        assertEquals(BaselineShift.Superscript, span.item.baselineShift)

        // 光标落在 ^ 之后时进到指数起点，后面的字符整体左移一位
        assertEquals(1, out.offsetMapping.originalToTransformed(2))
        assertEquals(2, out.offsetMapping.originalToTransformed(3))
        assertEquals(4, out.offsetMapping.originalToTransformed(5))
    }

    @Test
    fun `末尾的尖号留一个空的上标槽`() {
        val out = transform("x^")
        assertEquals("x ", out.text.text)

        val span = out.text.spanStyles.single()
        assertEquals(1, span.start)
        assertEquals(2, span.end)
        assertEquals(BaselineShift.Superscript, span.item.baselineShift)
        assertEquals(1, out.offsetMapping.originalToTransformed(2))
    }

    @Test
    fun `带符号和括号的指数整体抬高`() {
        assertEquals("x-2", transform("x^-2").text.text)
        assertEquals("x(a+1)", transform("x^(a+1)").text.text)
        assertEquals("xsin(x)", transform("x^sin(x)").text.text)
    }

    @Test
    fun `无法识别的指数保持原样`() {
        assertEquals("x^(a", transform("x^(a").text.text)
        assertEquals("x^)", transform("x^)").text.text)
    }
}
