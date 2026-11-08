package cn.stb.feature.chemistry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancerTest {

    private fun coefficients(input: String): List<Int> {
        val result = Balancer.balance(input)
        assertTrue("应当配平成功：$input，实际：$result", result is BalanceResult.Success)
        return (result as BalanceResult.Success).coefficients
    }

    @Test
    fun `水的合成`() {
        assertEquals(listOf(2, 1, 2), coefficients("H2 + O2 -> H2O"))
    }

    @Test
    fun `四氧化三铁的合成`() {
        assertEquals(listOf(3, 2, 1), coefficients("Fe + O2 -> Fe3O4"))
    }

    @Test
    fun `丙烷完全燃烧`() {
        assertEquals(listOf(1, 5, 3, 4), coefficients("C3H8 + O2 -> CO2 + H2O"))
    }

    @Test
    fun `氯酸钾分解`() {
        assertEquals(listOf(2, 2, 3), coefficients("KClO3 -> KCl + O2"))
    }

    @Test
    fun `电解水`() {
        assertEquals(listOf(2, 2, 1), coefficients("H2O -> H2 + O2"))
    }

    @Test
    fun `中和反应`() {
        assertEquals(listOf(1, 1, 1, 1), coefficients("NaOH + HCl -> NaCl + H2O"))
    }

    @Test
    fun `带括号的化学式`() {
        // Al2(SO4)3 的括号解析必须正确，否则铝原子数会算错
        assertEquals(listOf(2, 3, 1, 3), coefficients("Al + H2SO4 -> Al2(SO4)3 + H2"))
    }

    @Test
    fun `箭头写法兼容`() {
        assertEquals(listOf(2, 1, 2), coefficients("H2 + O2 → H2O"))
        assertEquals(listOf(2, 1, 2), coefficients("H2 + O2 = H2O"))
    }

    @Test
    fun `输入自带计量数也能配平且重复配平不累积`() {
        val first = Balancer.balance("4Fe + 3O2 -> 2Fe2O3")
        assertTrue(first is BalanceResult.Success)
        val r1 = first as BalanceResult.Success

        // 计量数应被剥掉，配平器自己算
        assertEquals(listOf(4, 3, 2), r1.coefficients)
        assertEquals(listOf("Fe", "O2"), r1.reactants)
        assertEquals(listOf("Fe2O3"), r1.products)

        // 把配平结果原样再配一次，系数必须一模一样（回归：曾经会把计量数越乘越大）
        val again = Balancer.balance("4Fe + 3O2 -> 2Fe2O3")
        assertEquals(r1.coefficients, (again as BalanceResult.Success).coefficients)
    }

    @Test
    fun `可逆号也能识别为分隔符`() {
        val result = Balancer.balance("N2 + 3H2 ⇌ 2NH3")
        assertTrue(result is BalanceResult.Success)
        assertEquals(listOf(1, 3, 2), (result as BalanceResult.Success).coefficients)
    }

    @Test
    fun `缺少生成物时给出失败而不是硬凑`() {
        val result = Balancer.balance("H2 + O2")
        assertTrue(result is BalanceResult.Failure)
    }

    @Test
    fun `元素不守恒时失败`() {
        val result = Balancer.balance("H2 + O2 -> Na")
        assertTrue(result is BalanceResult.Failure)
    }

    @Test
    fun `化学式解析计数`() {
        assertEquals(mapOf("Ca" to 1, "O" to 2, "H" to 2), parseFormula("Ca(OH)2"))
        assertEquals(mapOf("Al" to 2, "S" to 3, "O" to 12), parseFormula("Al2(SO4)3"))
        assertEquals(mapOf("Fe" to 3, "O" to 4), parseFormula("Fe3O4"))
    }
}
