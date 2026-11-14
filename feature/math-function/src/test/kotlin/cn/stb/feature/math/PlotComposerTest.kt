package cn.stb.feature.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PlotComposerTest {

    private fun plot(
        curves: List<PlotFn>,
        xCenter: Double = 0.0,
        xSpan: Double = 10.0,
        yCenter: Double = 0.0,
        aspect: Double = 0.6,
        params: Map<String, Double> = emptyMap(),
    ) = composePlot(curves, xCenter, xSpan, yCenter, params, aspect)

    @Test
    fun `两条曲线各自成序列并保留颜色`() {
        val result = plot(
            listOf(
                PlotFn(1, "sin(x)", 0xFF34558B),
                PlotFn(2, "cos(x)", 0xFFB4453A),
            ),
        )

        assertNotNull(result)
        assertEquals(2, result!!.series.size)
        assertEquals(0xFF34558B, result.series[0].colorArgb)
        assertEquals(0xFFB4453A, result.series[1].colorArgb)
    }

    @Test
    fun `隐藏的曲线不参与绘制`() {
        val result = plot(
            listOf(
                PlotFn(1, "sin(x)", 1L, visible = true),
                PlotFn(2, "cos(x)", 2L, visible = false),
            ),
        )

        assertEquals(1, result!!.series.size)
    }

    @Test
    fun `语法错误的曲线被跳过而不是让整张图失败`() {
        val result = plot(
            listOf(
                PlotFn(1, "sin(x)", 1L),
                PlotFn(2, "2+", 2L),
            ),
        )

        assertEquals(1, result!!.series.size)
    }

    @Test
    fun `全部不可用时返回 null`() {
        assertNull(plot(emptyList()))
        assertNull(plot(listOf(PlotFn(1, "", 1L))))
        assertNull(plot(listOf(PlotFn(1, "sin(x)", 1L, visible = false))))
    }

    @Test
    fun `视口不随函数值自动缩放`() {
        // 取消「跟随」后的契约：y 范围只由 yCenter 与宽高比决定，
        // 幅值再大的曲线也不会把视野拉走，用户平移到哪就显示哪。
        val result = plot(
            listOf(
                PlotFn(1, "sin(x)", 1L),
                PlotFn(2, "10*sin(x)", 2L),
            ),
            xSpan = 7.0,
        )

        assertNotNull(result)
        val expectedHalf = 7.0 * 0.6 / 2
        assertEquals(0.0 - expectedHalf, result!!.yMin, 1e-9)
        assertEquals(0.0 + expectedHalf, result.yMax, 1e-9)
    }

    @Test
    fun `参数变化不影响视口`() {
        val small = plot(listOf(PlotFn(1, "a*x", 1L)), xSpan = 2.0, params = mapOf("a" to 1.0))
        val large = plot(listOf(PlotFn(1, "a*x", 1L)), xSpan = 2.0, params = mapOf("a" to 4.0))

        assertEquals(small!!.yMax, large!!.yMax, 1e-9)
        assertEquals(small.yMin, large.yMin, 1e-9)
    }

    @Test
    fun `x 窗口随中心与跨度移动`() {
        val result = plot(listOf(PlotFn(1, "x", 1L)), xCenter = 5.0, xSpan = 4.0)

        assertEquals(3.0, result!!.xMin, 1e-9)
        assertEquals(7.0, result.xMax, 1e-9)
    }

    /* ---------------- 定义域 ---------------- */

    @Test
    fun `定义域裁剪显式函数的采样范围`() {
        val result = plot(
            listOf(PlotFn(1, "x", 1L, domain = Domain(-1.0, 1.0))),
            xSpan = 10.0,
        )

        val points = result!!.series.first().segments.flatten()
        assertTrue("应当有采样点", points.isNotEmpty())
        assertTrue("所有点的 x 都应在定义域内", points.all { it.first in -1.0..1.0 })
        assertEquals(-1.0, points.minOf { it.first }, 1e-9)
        assertEquals(1.0, points.maxOf { it.first }, 1e-9)
    }

    @Test
    fun `空的定义域不画线`() {
        val result = plot(listOf(PlotFn(1, "x", 1L, domain = Domain(-10.0, -20.0))), xSpan = 10.0)

        assertTrue(result!!.series.first().segments.isEmpty())
    }

    @Test
    fun `单边定义域只画半边`() {
        val result = plot(
            listOf(PlotFn(1, "sqrt(x)", 1L, domain = Domain(0.0, null))),
            xSpan = 10.0,
        )

        val points = result!!.series.first().segments.flatten()
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.first >= -1e-9 })
    }

    @Test
    fun `定义域也会裁剪隐式方程的网格`() {
        val full = plot(listOf(PlotFn(1, "x^2+y^2=25", 1L, mode = FnMode.IMPLICIT)), xSpan = 20.0)
        val half = plot(
            listOf(
                PlotFn(1, "x^2+y^2=25", 1L, mode = FnMode.IMPLICIT, domain = Domain(0.0, null)),
            ),
            xSpan = 20.0,
        )

        val fullPoints = full!!.series.first().segments.flatten()
        val halfPoints = half!!.series.first().segments.flatten()
        assertTrue("全圆应当覆盖左右两侧", fullPoints.any { it.first < -2 })
        assertTrue("裁剪后不应再出现负 x 的点", halfPoints.all { it.first >= -1e-6 })
        assertTrue("右半边仍应有足够多的点", halfPoints.size > 100)
    }

    /* ---------------- 隐函数 ---------------- */

    @Test
    fun `圆方程能画出闭合曲线`() {
        val result = plot(
            listOf(PlotFn(1, "x^2+y^2=25", 1L, mode = FnMode.IMPLICIT)),
            xSpan = 20.0,
        )

        assertNotNull(result)
        val points = result!!.series.first().segments.flatten()
        assertTrue("圆应该有足够多的采样点，实际 ${points.size}", points.size > 200)

        // 每个点都应落在半径 5 的圆周上（网格离散化允许少量误差）
        val maxError = points.maxOf { (x, y) -> abs(hypot(x, y) - 5.0) }
        assertTrue("点应贴近半径 5 的圆周，最大偏差 $maxError", maxError < 0.15)

        // 四个象限都要有点，否则说明只画了半圆
        assertTrue(points.any { it.first > 2 && it.second > 2 })
        assertTrue(points.any { it.first < -2 && it.second < -2 })
    }

    @Test
    fun `椭圆的半轴长度正确`() {
        // x^2/16 + y^2/4 = 1 → 半轴 4 与 2
        val result = plot(
            listOf(PlotFn(1, "x^2/16+y^2/4=1", 1L, mode = FnMode.IMPLICIT)),
            xSpan = 20.0,
        )

        val points = result!!.series.first().segments.flatten()
        assertEquals(4.0, points.maxOf { it.first }, 0.15)
        assertEquals(2.0, points.maxOf { it.second }, 0.15)
    }

    @Test
    fun `隐函数纵轴跟随绘图区宽高比`() {
        val result = plot(
            listOf(PlotFn(1, "x^2+y^2=25", 1L, mode = FnMode.IMPLICIT)),
            xSpan = 20.0,
            aspect = 0.6,
        )

        assertEquals(-6.0, result!!.yMin, 1e-9)
        assertEquals(6.0, result.yMax, 1e-9)
    }

    @Test
    fun `y 等于 sin x 仍按显式处理`() {
        val def = parseCurve(FnMode.EXPLICIT, "y=sin(x)")
        assertTrue("带 y= 前缀应走显式路径以保留渐近线处理", def is CurveDef.Explicit)

        val result = plot(listOf(PlotFn(1, "y=sin(x)", 1L)), xSpan = 7.0)
        assertTrue(result!!.yMax > 0.9)
    }

    @Test
    fun `显式模式下的表达式按显式绘制`() {
        val result = plot(listOf(PlotFn(1, "sin(x)", 1L, mode = FnMode.EXPLICIT)), xSpan = 7.0)
        assertEquals(1, result!!.series.size)
        assertTrue(result.series.first().segments.isNotEmpty())
    }

    @Test
    fun `显式与隐式可以同图共存`() {
        val result = plot(
            listOf(
                PlotFn(1, "sin(x)", 1L),
                PlotFn(2, "x^2+y^2=25", 2L, mode = FnMode.IMPLICIT),
            ),
            xSpan = 20.0,
            aspect = 0.6,
        )

        assertEquals(2, result!!.series.size)
        // 有隐函数时纵轴由宽高比决定，而不是由 sin 的幅值决定
        assertEquals(6.0, result.yMax, 1e-9)
    }

    @Test
    fun `同一条表达式在两种模式下结果不同`() {
        // 显式 sin(x) 是正弦曲线；隐式 sin(x)=0 是一组竖线
        val explicit = plot(listOf(PlotFn(1, "sin(x)", 1L)), xSpan = 7.0)!!
        val implicit = plot(
            listOf(PlotFn(1, "sin(x)", 1L, mode = FnMode.IMPLICIT)),
            xSpan = 7.0,
        )!!

        val explicitPoints = explicit.series.first().segments.flatten()
        assertTrue("显式应当是一条有波峰的曲线", explicitPoints.any { it.second > 0.9 })

        val implicitPoints = implicit.series.first().segments.flatten()
        assertTrue("隐式曲线应当有采样点", implicitPoints.isNotEmpty())
        assertTrue(
            "隐式 sin(x)=0 的曲线应该贴着竖直线 x=0",
            implicitPoints.any { abs(it.first) < 0.1 },
        )
        assertTrue(
            "隐式曲线的 y 分布应当覆盖很宽（竖线），而不是正弦波",
            implicitPoints.maxOf { it.second } - implicitPoints.minOf { it.second } > 3.0,
        )
    }
}
