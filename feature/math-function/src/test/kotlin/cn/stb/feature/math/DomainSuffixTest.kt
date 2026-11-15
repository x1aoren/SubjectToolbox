package cn.stb.feature.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表达式末尾 `{0,10}` 定义域后缀的纯逻辑测试。
 *
 * 这一块同时被「输入框里的文字」和「对话框里的两个端点」两个入口改写，
 * 任何一边写歪了都会出现「框里写着 {0,10}、实际按别的区间画」。
 */
class DomainSuffixTest {

    @Test
    fun `花括号解析成两端定义域`() {
        val both = DomainSuffix.parse("sin(x){0,10}")
        assertEquals("sin(x)", both.expr)
        assertEquals(0.0, both.domain!!.start!!, 1e-9)
        assertEquals(10.0, both.domain.end!!, 1e-9)
        assertNull(both.error)

        // 一侧留空 = 该侧无界
        val rightOpen = DomainSuffix.parse("sqrt(x){0,}")
        assertEquals(0.0, rightOpen.domain!!.start!!, 1e-9)
        assertNull(rightOpen.domain.end)

        val leftOpen = DomainSuffix.parse("1/x{,0}")
        assertNull(leftOpen.domain!!.start)
        assertEquals(0.0, leftOpen.domain.end!!, 1e-9)

        // 端点可以是常数表达式，中间空格随便写
        val constant = DomainSuffix.parse("sin(x){ -1/2 , 1/2 }")
        assertEquals(-0.5, constant.domain!!.start!!, 1e-9)
        assertEquals(0.5, constant.domain.end!!, 1e-9)
    }

    @Test
    fun `还没写完时不打断输入`() {
        // 只打了左花括号（或还没补右括号）：先当没写定义域，不报错也不能乱裁剪
        val pending = DomainSuffix.parse("sin(x){0,")
        assertEquals("sin(x)", pending.expr)
        assertNull(pending.domain)
        assertNull(pending.error)

        assertNull(DomainSuffix.parse("sin(x){").domain)
    }

    @Test
    fun `写坏的定义域会报错`() {
        assertNotNull("左端不能大于右端", DomainSuffix.parse("sin(x){5,1}").error)
        assertNotNull("端点不能含变量", DomainSuffix.parse("sin(x){0,x}").error)
        assertNotNull("最多两个端点", DomainSuffix.parse("sin(x){0,1,2}").error)
        assertNotNull("花括号前面得有表达式", DomainSuffix.parse("{0,10}").error)

        // 花括号后面还有内容：不当定义域，交给表达式解析器报错
        val trailing = DomainSuffix.parse("sin(x){0,10}+1")
        assertNull(trailing.domain)
        assertNull(trailing.error)
        assertEquals("sin(x){0,10}+1", trailing.expr)
    }

    @Test
    fun `定义域显示成花括号小字`() {
        assertEquals("{0,10}", DomainSuffix.label(Domain(0.0, 10.0)))
        assertEquals("{0,}", DomainSuffix.label(Domain(0.0, null)))
        assertEquals("{,0}", DomainSuffix.label(Domain(null, 0.0)))
        assertEquals("", DomainSuffix.label(Domain()))

        // 正文里的 {…} 摘干净；没有后缀时原样返回
        assertEquals("sin(x)", DomainSuffix.strip("sin(x){0,10}"))
        assertEquals("sin(x)", DomainSuffix.strip("sin(x)"))
    }

    @Test
    fun `带后缀的表达式照常解析`() {
        val explicit = parseCurve(FnMode.EXPLICIT, "sqrt(x){0,4}")
        assertTrue(explicit is CurveDef.Explicit)

        val implicit = parseCurve(FnMode.IMPLICIT, "x^2+y^2=25{0,3}")
        assertTrue(implicit is CurveDef.Implicit)

        // 后缀写坏了，解析直接失败（界面上就是报错栏那行字）
        assertTrue(runCatching { parseCurve(FnMode.EXPLICIT, "sin(x){9,1}") }.isFailure)
    }

    @Test
    fun `花括号里的定义域会裁剪绘图范围`() {
        val result = composePlot(
            curves = listOf(PlotFn(1, "x{0,2}", 0xFF34558B)),
            xCenter = 0.0,
            xSpan = 10.0,
            yCenter = 0.0,
            params = emptyMap(),
            aspect = 0.6,
        )
        val points = result!!.series.first().segments.flatten()
        assertTrue("应该有画出来的点", points.isNotEmpty())
        // 裁剪到 [0,2] 之后，所有采样点都落在区间里
        assertTrue(points.all { it.first in -1e-9..2.0 + 1e-9 })
        assertTrue(points.any { it.first > 1.9 })
    }
}
