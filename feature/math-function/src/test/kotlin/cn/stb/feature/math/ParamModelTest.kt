package cn.stb.feature.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * MathModel 里纯逻辑的单元测试。
 *
 * 这些函数同时被滑块面板、函数编辑器和绘图代码使用，又都不依赖 Compose / Android，
 * 所以可以放心在 JVM 上直接测：吸附、格式化、名字/区间校验、定义域标签与端点解析。
 */
class ParamModelTest {

    /* ---------------- 步进吸附 ---------------- */

    @Test
    fun `snapToStep 把值吸到最近的刻度`() {
        assertEquals(0.3, snapToStep(0.34, -5.0, 5.0, 0.1), 1e-9)
        assertEquals(-4.9, snapToStep(-4.86, -5.0, 5.0, 0.1), 1e-9)
        assertEquals(1.5, snapToStep(1.46, 0.0, 2.0, 0.5), 1e-9)
    }

    @Test
    fun `snapToStep 把越界值夹回区间`() {
        assertEquals(5.0, snapToStep(10.0, -5.0, 5.0, 0.1), 1e-9)
        assertEquals(-5.0, snapToStep(-99.0, -5.0, 5.0, 0.1), 1e-9)
        // 端点附近的浮点误差应当能对齐到端点本身，而不是差一个步进
        assertEquals(5.0, snapToStep(4.999999999, -5.0, 5.0, 0.1), 1e-9)
        assertEquals(0.0, snapToStep(4e-12, 0.0, 1.0, 0.1), 1e-9)
    }

    @Test
    fun `snapToStep 在步进非法时只夹紧不吸附`() {
        assertEquals(0.123, snapToStep(0.123, -1.0, 1.0, 0.0), 1e-9)
        assertEquals(0.123, snapToStep(0.123, -1.0, 1.0, Double.NaN), 1e-9)
        assertEquals(1.0, snapToStep(7.0, -1.0, 1.0, -0.5), 1e-9)
    }

    @Test
    fun `snapToStep 遇到 NaN 值落回最小值`() {
        assertEquals(-2.0, snapToStep(Double.NaN, -2.0, 3.0, 0.5), 1e-9)
    }

    @Test
    fun `snapToStep 容忍 min 与 max 反着填`() {
        // 参数范围本身有校验，这里只保证工具函数不崩
        assertEquals(2.0, snapToStep(2.0, 5.0, -5.0, 1.0), 1e-9)
    }

    /* ---------------- 数值格式化 ---------------- */

    @Test
    fun `stepDecimals 按步进取小数位`() {
        assertEquals(0, stepDecimals(1.0))
        assertEquals(1, stepDecimals(0.1))
        assertEquals(1, stepDecimals(0.5))
        assertEquals(2, stepDecimals(0.05))
        assertEquals(2, stepDecimals(0.01))
        assertEquals(4, stepDecimals(0.0001))
        // 最多保留 4 位，再小也不继续加
        assertEquals(4, stepDecimals(0.00001))
        // 非法步进兜底取 1 位
        assertEquals(1, stepDecimals(0.0))
        assertEquals(1, stepDecimals(Double.NaN))
    }

    @Test
    fun `formatParamValue 按步进保留小数并清理负零`() {
        assertEquals("1.0", formatParamValue(1.0, 0.1))
        assertEquals("0.3", formatParamValue(0.30000000000000004, 0.1))
        assertEquals("0.25", formatParamValue(0.25, 0.05))
        assertEquals("2", formatParamValue(2.0, 1.0))
        assertEquals("0", formatParamValue(-0.0, 0.1))
        assertEquals("—", formatParamValue(Double.NaN, 0.1))
    }

    @Test
    fun `formatMathNumber 去掉多余的零`() {
        assertEquals("3", formatMathNumber(3.0))
        assertEquals("0.5", formatMathNumber(0.5))
        assertEquals("-2", formatMathNumber(-2.0))
        assertEquals("0", formatMathNumber(-0.0))
        assertEquals("—", formatMathNumber(Double.POSITIVE_INFINITY))
        // 大数改用科学计数法，避免刻度标签长得离谱
        assertTrue(formatMathNumber(2.5e7).contains("e"))
    }

    /* ---------------- 变量名校验 ---------------- */

    @Test
    fun `validateParamName 接受合法名字`() {
        assertNull(validateParamName("a", emptyList()))
        assertNull(validateParamName("k", listOf(ParamDef("b", 1.0))))
        // 首尾空白会被裁掉，和解析器看到的名字一致
        assertNull(validateParamName(" t ", emptyList()))
    }

    @Test
    fun `validateParamName 拒绝空名和超长名`() {
        assertEquals("请输入变量名", validateParamName("", emptyList()))
        assertEquals("请输入变量名", validateParamName("   ", emptyList()))
        val tooLong = validateParamName("abcdefghij", emptyList())
        assertNotNull(tooLong)
        assertTrue(tooLong!!.contains("8"))
    }

    @Test
    fun `validateParamName 拒绝重复名`() {
        val existing = listOf(ParamDef("a", 1.0), ParamDef("k", 2.0))
        val duplicateA = validateParamName("a", existing)
        assertNotNull(duplicateA)
        assertTrue(duplicateA!!.contains("a"))
        assertNotNull(validateParamName("k", existing))
    }

    @Test
    fun `validateParamName 拒绝保留字`() {
        // x、y 是坐标，pi/e 是常数，函数名会被解析成调用，都不能当滑块变量
        listOf("x", "y", "pi", "π", "e", "sin", "sqrt").forEach { reserved ->
            assertNotNull("$reserved 应当被拒绝", validateParamName(reserved, emptyList()))
        }
    }

    @Test
    fun `validateParamName 拒绝非字母`() {
        listOf("a1", "a b", "a-b", "@").forEach { bad ->
            val message = validateParamName(bad, emptyList())
            assertNotNull("$bad 应当被拒绝", message)
            assertTrue(message!!.contains("字母"))
        }
    }

    /* ---------------- 范围校验 ---------------- */

    @Test
    fun `validateParamRange 接受合法配置`() {
        assertNull(validateParamRange(-5.0, 5.0, 0.1, 1.0))
        // 步进等于整个区间、当前值正好落在端点上都很正常
        assertNull(validateParamRange(0.0, 1.0, 1.0, 0.0))
        assertNull(validateParamRange(0.0, 1.0, 1.0, 1.0))
        // value = null 表示只校验区间与步进
        assertNull(validateParamRange(-1.0, 1.0, 0.05))
    }

    @Test
    fun `validateParamRange 拒绝反区间`() {
        assertEquals("最小值必须小于最大值", validateParamRange(5.0, 5.0, 0.1))
        assertEquals("最小值必须小于最大值", validateParamRange(6.0, 5.0, 0.1))
    }

    @Test
    fun `validateParamRange 拒绝非法步进`() {
        assertEquals("步进必须大于 0", validateParamRange(-5.0, 5.0, 0.0))
        assertEquals("步进必须大于 0", validateParamRange(-5.0, 5.0, -1.0))
        assertEquals("步进不能大于取值范围", validateParamRange(0.0, 1.0, 2.0))
    }

    @Test
    fun `validateParamRange 拒绝超出范围的值`() {
        assertEquals("当前值不能大于最大值", validateParamRange(-5.0, 5.0, 0.1, 7.0))
        assertEquals("当前值不能小于最小值", validateParamRange(-5.0, 5.0, 0.1, -7.0))
    }

    @Test
    fun `validateParamRange 拒绝非数字`() {
        assertNotNull(validateParamRange(Double.NaN, 5.0, 0.1))
        assertNotNull(validateParamRange(-5.0, Double.NaN, 0.1))
        assertNotNull(validateParamRange(-5.0, 5.0, 0.1, Double.POSITIVE_INFINITY))
    }

    /* ---------------- 参数模型 ---------------- */

    @Test
    fun `ParamDef 收敛当前值到区间内`() {
        assertEquals(5.0, ParamDef("a", 7.0).clampedToRange().value, 1e-9)
        assertEquals(-5.0, ParamDef("a", -7.0).clampedToRange().value, 1e-9)
        assertEquals(0.3, ParamDef("a", 0.3).clampedToRange().value, 1e-9)
    }

    @Test
    fun `滑块与曲线数量上限`() {
        assertEquals(6, MAX_SLIDERS)
        assertEquals(6, MAX_CURVES)
    }

    @Test
    fun `函数模式可以来回切换`() {
        assertEquals(FnMode.IMPLICIT, FnMode.EXPLICIT.toggled())
        assertEquals(FnMode.EXPLICIT, FnMode.IMPLICIT.toggled())
        assertEquals("f1(x)", fnLabel(0, FnMode.EXPLICIT))
        assertEquals("F2(x,y)", fnLabel(1, FnMode.IMPLICIT))
    }

    /* ---------------- 定义域标签 ---------------- */

    @Test
    fun `formatDomainLabel 覆盖全体单边与区间`() {
        assertEquals("全体实数", formatDomainLabel(Domain()))
        assertEquals("x ≥ 0", formatDomainLabel(Domain(start = 0.0)))
        assertEquals("x ≤ -3", formatDomainLabel(Domain(end = -3.0)))
        assertEquals("x ∈ [-2, 5]", formatDomainLabel(Domain(start = -2.0, end = 5.0)))
        assertEquals("x ∈ [0.5, 1.5]", formatDomainLabel(Domain(start = 0.5, end = 1.5)))
    }

    /* ---------------- 定义域端点解析 ---------------- */

    @Test
    fun `parseDomainBound 空输入表示无界`() {
        val empty = parseDomainBound("")
        assertTrue(empty.isSuccess)
        assertNull(empty.getOrNull())

        val blank = parseDomainBound("   ")
        assertTrue(blank.isSuccess)
        assertNull(blank.getOrNull())
    }

    @Test
    fun `parseDomainBound 支持常数表达式`() {
        assertEquals(PI, parseDomainBound("pi").getOrThrow()!!, 1e-12)
        assertEquals(PI, parseDomainBound("π").getOrThrow()!!, 1e-12)
        assertEquals(0.5, parseDomainBound("1/2").getOrThrow()!!, 1e-12)
        assertEquals(-3.0, parseDomainBound("-3").getOrThrow()!!, 1e-12)
        assertEquals(2 * PI, parseDomainBound("2*pi").getOrThrow()!!, 1e-12)
        assertEquals(PI / 2, parseDomainBound("pi/2").getOrThrow()!!, 1e-12)
    }

    @Test
    fun `parseDomainBound 非法输入返回 failure`() {
        assertTrue(parseDomainBound("abc").isFailure)
        assertTrue(parseDomainBound("1+").isFailure)
        // 无穷大、NaN 都不是合法端点，definition 必须是确定的数
        assertTrue(parseDomainBound("1/0").isFailure)
        assertTrue(parseDomainBound("sqrt(-1)").isFailure)
    }

    /* ---------------- 定义域包含判断 ---------------- */

    @Test
    fun `Domain 的包含判断`() {
        val all = Domain()
        assertTrue(all.contains(-1e9))
        assertTrue(all.contains(1e9))
        assertFalse(all.isInvalid)

        val closed = Domain(start = 0.0, end = 10.0)
        assertTrue(closed.contains(0.0))
        assertTrue(closed.contains(5.0))
        assertTrue(closed.contains(10.0))
        assertFalse(closed.contains(-0.1))
        assertFalse(closed.contains(10.1))

        assertTrue(Domain(start = 0.0).contains(100.0))
        assertFalse(Domain(start = 0.0).contains(-0.001))
        assertTrue(Domain(end = 5.0).contains(-100.0))
        assertTrue(Domain(end = 5.0).contains(5.0))
        assertFalse(Domain(end = 5.0).contains(5.001))
    }

    @Test
    fun `Domain 的非法区间判断`() {
        assertFalse(Domain().isInvalid)
        assertFalse(Domain(start = 1.0).isInvalid)
        assertFalse(Domain(end = 1.0).isInvalid)
        assertTrue(Domain(start = 5.0, end = 5.0).isInvalid)
        assertTrue(Domain(start = 10.0, end = 0.0).isInvalid)
    }

    @Test
    fun `Domain 按外框裁剪 x 范围`() {
        assertEquals(-10.0 to 10.0, Domain().xRange(-10.0, 10.0))
        assertEquals(0.0 to 5.0, Domain(0.0, 5.0).xRange(-10.0, 10.0))
        assertEquals(0.0 to 10.0, Domain(start = 0.0).xRange(-10.0, 10.0))
        assertEquals(-10.0 to 5.0, Domain(end = 5.0).xRange(-10.0, 10.0))
    }
}
