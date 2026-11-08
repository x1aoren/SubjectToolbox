package cn.stb.feature.chemistry

import cn.stb.core.data.AssetReader
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt

data class ChemEquation(
    val reactants: List<String>,
    val products: List<String>,
    val condition: String,
    val type: String,
    val note: String,
    val reversible: Boolean = false,
) {
    val reactantText: String get() = reactants.joinToString(" + ")
    val productText: String get() = products.joinToString(" + ")

    /**
     * 方程式用的连接符号：
     * - 有机反应用箭头
     * - 可逆反应用可逆号
     * - 其余（无机方程式）按中学书写习惯用等号
     */
    val arrow: String
        get() = when {
            type.startsWith("有机") -> "→"
            reversible -> "⇌"
            else -> "="
        }
}

class ChemistryRepository(private val assets: AssetReader) {

    private var cached: List<ChemEquation>? = null

    fun equations(): List<ChemEquation> = cached ?: load().also { cached = it }

    fun types(): List<String> = equations().map { it.type }.distinct()

    private fun load(): List<ChemEquation> {
        val root = assets.jsonObject("content/chemistry/equations.json")
        val array = root.optJSONArray("equations") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    ChemEquation(
                        reactants = o.optJSONArray("r").toStringList(),
                        products = o.optJSONArray("p").toStringList(),
                        condition = o.optString("c"),
                        type = o.optString("t"),
                        note = o.optString("n"),
                        reversible = o.optInt("rv") == 1,
                    )
                )
            }
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) add(getString(i)) }
}

/* ------------------------------------------------------------------
 * 化学式解析与配平
 * ---------------------------------------------------------------- */

/** 把 Ca(OH)2 解析成 {Ca:1, O:2, H:2}。 */
fun parseFormula(formula: String): Map<String, Int> {
    val stack = ArrayDeque<MutableMap<String, Int>>()
    stack.addLast(mutableMapOf())
    var i = 0

    fun readCount(from: Int): Pair<Int, Int> {
        var j = from
        while (j < formula.length && formula[j].isDigit()) j++
        val number = if (j == from) 1 else formula.substring(from, j).toIntOrNull() ?: 1
        return number to j
    }

    while (i < formula.length) {
        val c = formula[i]
        when {
            c == '(' -> {
                stack.addLast(mutableMapOf())
                i++
            }
            c == ')' -> {
                i++
                val (multiplier, next) = readCount(i)
                i = next
                if (stack.size > 1) {
                    val group = stack.removeLast()
                    val top = stack.last()
                    group.forEach { (element, count) -> top[element] = (top[element] ?: 0) + count * multiplier }
                }
            }
            c.isUpperCase() -> {
                var j = i + 1
                while (j < formula.length && formula[j].isLowerCase()) j++
                val element = formula.substring(i, j)
                val (count, next) = readCount(j)
                val top = stack.last()
                top[element] = (top[element] ?: 0) + count
                i = next
            }
            else -> i++
        }
    }
    return stack.last()
}

sealed interface BalanceResult {
    data class Success(
        val coefficients: List<Int>,
        val reactants: List<String>,
        val products: List<String>,
    ) : BalanceResult

    data class Failure(val message: String) : BalanceResult
}

object Balancer {

    fun balance(input: String): BalanceResult {
        val normalized = input
            .replace("⇌", "->")
            .replace("→", "->")
            .replace("＝", "->")
            .replace("=", "->")
        val sides = normalized.split("->")
        if (sides.size != 2) {
            return BalanceResult.Failure("请用 -> 分隔反应物和生成物，例如：H2 + O2 -> H2O")
        }
        val reactants = split(sides[0])
        val products = split(sides[1])
        if (reactants.isEmpty() || products.isEmpty()) {
            return BalanceResult.Failure("反应物和生成物都不能为空")
        }

        val counts = (reactants + products).map { parseFormula(it) }
        if (counts.any { it.isEmpty() }) {
            return BalanceResult.Failure("有化学式无法解析，检查大小写与括号，例如 Ca(OH)2")
        }

        val elements = counts.flatMap { it.keys }.distinct()
        val signs = List(reactants.size) { -1.0 } + List(products.size) { 1.0 }
        val matrix = Array(elements.size) { e ->
            DoubleArray(counts.size) { j ->
                (counts[j][elements[e]] ?: 0) * signs[j]
            }
        }

        val vector = nullVector(matrix)
            ?: return BalanceResult.Failure("这个反应配不平：可能方程式写错了，或需要拆成离子方程式")

        if (vector.any { it <= 1e-9 }) {
            return BalanceResult.Failure("解出来的系数不是全正数，检查反应物与生成物是否写反")
        }

        val integers = toSmallestIntegers(vector)
            ?: return BalanceResult.Failure("系数无法化为整数，检查是否漏写了生成物")

        return BalanceResult.Success(integers, reactants, products)
    }

    private fun split(side: String): List<String> =
        side.split("+")
            .map { it.trim().stripCoefficient() }
            .filter { it.isNotEmpty() }

    /**
     * 去掉手写的计量数（"2Fe2O3" -> "Fe2O3"）。
     *
     * 必须在解析前剥掉：配平器自己算系数，如果输入里残留计量数，
     * 结果又会把算出的系数拼到前面，一点配平计量数就翻倍累加。
     */
    private fun String.stripCoefficient(): String {
        var i = 0
        while (i < length && (this[i].isDigit() || this[i] == ' ')) i++
        val stripped = substring(i).trim()
        return if (stripped.isEmpty()) this.trim() else stripped
    }

    /** 高斯消元求零空间；只有唯一一组独立解时才接受。 */
    private fun nullVector(source: Array<DoubleArray>): DoubleArray? {
        if (source.isEmpty() || source[0].isEmpty()) return null
        val a = Array(source.size) { source[it].copyOf() }
        val rows = a.size
        val cols = a[0].size
        val pivots = mutableListOf<Int>()
        var lead = 0
        var row = 0

        while (row < rows && lead < cols) {
            var pivot = row
            while (pivot < rows && abs(a[pivot][lead]) < 1e-9) pivot++
            if (pivot == rows) {
                lead++
                continue
            }
            val tmp = a[pivot]; a[pivot] = a[row]; a[row] = tmp
            val divisor = a[row][lead]
            for (j in 0 until cols) a[row][j] /= divisor
            for (k in 0 until rows) {
                if (k != row) {
                    val factor = a[k][lead]
                    if (abs(factor) > 1e-12) {
                        for (j in 0 until cols) a[k][j] -= factor * a[row][j]
                    }
                }
            }
            pivots += lead
            row++
            lead++
        }

        val free = (0 until cols).filter { it !in pivots }
        if (free.size != 1) return null

        val freeCol = free.first()
        val x = DoubleArray(cols)
        x[freeCol] = 1.0
        pivots.forEachIndexed { index, col -> x[col] = -a[index][freeCol] }

        if (x.all { it <= 1e-9 }) return x.map { -it }.toDoubleArray()
        return x
    }

    private fun toSmallestIntegers(vector: DoubleArray): List<Int>? {
        val min = vector.minOrNull() ?: return null
        if (abs(min) < 1e-9) return null
        val normalized = vector.map { it / min }

        for (multiplier in 1..60) {
            val scaled = normalized.map { it * multiplier }
            if (scaled.all { abs(it - it.roundToInt()) < 1e-5 }) {
                val ints = scaled.map { it.roundToInt() }
                if (ints.all { it > 0 }) return ints
            }
        }
        return null
    }
}
