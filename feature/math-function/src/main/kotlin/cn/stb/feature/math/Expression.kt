package cn.stb.feature.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 表达式 AST。
 *
 * 解析器是手写的递归下降：相比引入第三方求值库，手写能精确控制错误位置，
 * 也能让「隐式乘号」(2x、3(x+1)) 这种学生最常写的输入直接成立。
 */
sealed interface Expr {
    data class Num(val value: Double) : Expr
    data class Var(val name: String) : Expr
    data class Unary(val op: Char, val operand: Expr) : Expr
    data class Binary(val op: Char, val left: Expr, val right: Expr) : Expr
    data class Call(val name: String, val args: List<Expr>) : Expr
}

class ParseException(message: String, val position: Int) : Exception(message)

private sealed interface Token {
    data class Num(val value: Double) : Token
    data class Ident(val name: String) : Token
    data class Op(val symbol: Char) : Token
    data object LParen : Token
    data object RParen : Token
    data object Comma : Token
}

private val FUNCTIONS = setOf("sin", "cos", "tan", "ln", "lg", "log", "sqrt", "abs", "exp")

private fun tokenize(input: String): List<Token> {
    val out = mutableListOf<Token>()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c.isWhitespace() -> i++

            c.isDigit() || c == '.' -> {
                val start = i
                while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                val text = input.substring(start, i)
                val value = text.toDoubleOrNull()
                    ?: throw ParseException("数字写错了：$text", start)
                out += Token.Num(value)
            }

            c.isLetter() -> {
                val start = i
                while (i < input.length && input[i].isLetter()) i++
                out += Token.Ident(input.substring(start, i))
            }

            c in "+-*/^" -> { out += Token.Op(c); i++ }
            c == '×' || c == '·' -> { out += Token.Op('*'); i++ }
            c == '÷' -> { out += Token.Op('/'); i++ }
            c == '−' -> { out += Token.Op('-'); i++ }
            c == '(' -> { out += Token.LParen; i++ }
            c == ')' -> { out += Token.RParen; i++ }
            c == ',' -> { out += Token.Comma; i++ }

            else -> throw ParseException("看不懂这个符号：$c", i)
        }
    }
    return out
}

private class Parser(private val tokens: List<Token>) {
    private var pos = 0

    fun parse(): Expr {
        if (tokens.isEmpty()) throw ParseException("还没有输入表达式", 0)
        val result = expression()
        if (pos < tokens.size) throw ParseException("多余的内容", pos)
        return result
    }

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun expression(): Expr {
        var left = term()
        while (true) {
            val t = peek()
            if (t is Token.Op && (t.symbol == '+' || t.symbol == '-')) {
                pos++
                left = Expr.Binary(t.symbol, left, term())
            } else return left
        }
    }

    private fun term(): Expr {
        var left = unary()
        while (true) {
            val t = peek()
            when {
                t is Token.Op && (t.symbol == '*' || t.symbol == '/') -> {
                    pos++
                    left = Expr.Binary(t.symbol, left, unary())
                }
                // 隐式乘号：2x、3(x+1)、x sin(x)
                t is Token.Num || t is Token.Ident || t is Token.LParen -> {
                    left = Expr.Binary('*', left, unary())
                }
                else -> return left
            }
        }
    }

    private fun unary(): Expr {
        val t = peek()
        if (t is Token.Op && (t.symbol == '-' || t.symbol == '+')) {
            pos++
            return Expr.Unary(t.symbol, unary())
        }
        return power()
    }

    private fun power(): Expr {
        val base = primary()
        val t = peek()
        if (t is Token.Op && t.symbol == '^') {
            pos++
            // 右结合，且 -x^2 解析为 -(x^2)
            return Expr.Binary('^', base, unary())
        }
        return base
    }

    private fun primary(): Expr {
        val t = peek() ?: throw ParseException("表达式不完整", pos)
        return when (t) {
            is Token.Num -> { pos++; Expr.Num(t.value) }

            is Token.Ident -> {
                pos++
                val name = t.name
                if (peek() is Token.LParen && name in FUNCTIONS) {
                    pos++
                    val args = mutableListOf<Expr>()
                    if (peek() !is Token.RParen) {
                        args += expression()
                        while (peek() is Token.Comma) { pos++; args += expression() }
                    }
                    if (peek() !is Token.RParen) throw ParseException("括号没有闭合", pos)
                    pos++
                    Expr.Call(name, args)
                } else {
                    when (name) {
                        "pi", "π" -> Expr.Num(PI)
                        "e" -> Expr.Num(kotlin.math.E)
                        else -> Expr.Var(name)
                    }
                }
            }

            is Token.LParen -> {
                pos++
                val inner = expression()
                if (peek() !is Token.RParen) throw ParseException("括号没有闭合", pos)
                pos++
                inner
            }

            else -> throw ParseException("这里应该是一个数字或变量", pos)
        }
    }
}

fun parseExpression(input: String): Expr = Parser(tokenize(input.replace(" ", ""))).parse()

fun Expr.variables(): List<String> {
    val found = linkedSetOf<String>()
    fun walk(e: Expr) {
        when (e) {
            is Expr.Num -> Unit
            is Expr.Var -> found += e.name
            is Expr.Unary -> walk(e.operand)
            is Expr.Binary -> { walk(e.left); walk(e.right) }
            is Expr.Call -> e.args.forEach(::walk)
        }
    }
    walk(this)
    return found.toList()
}

fun Expr.eval(x: Double, params: Map<String, Double> = emptyMap(), y: Double = Double.NaN): Double = when (this) {
    is Expr.Num -> value
    is Expr.Var -> when (name) {
        "x" -> x
        "y" -> y
        else -> params[name] ?: Double.NaN
    }
    is Expr.Unary -> {
        val v = operand.eval(x, params, y)
        if (op == '-') -v else v
    }
    is Expr.Binary -> {
        val l = left.eval(x, params, y)
        val r = right.eval(x, params, y)
        when (op) {
            '+' -> l + r
            '-' -> l - r
            '*' -> l * r
            '/' -> l / r
            '^' -> {
                // 负底数的分数次幂在实数域无意义，直接给 NaN 而不是 NaN 崩溃
                if (l < 0 && r != r.toInt().toDouble()) Double.NaN else Math.pow(l, r)
            }
            else -> Double.NaN
        }
    }
    is Expr.Call -> {
        val a = args.firstOrNull()?.eval(x, params, y) ?: Double.NaN
        when (name) {
            "sin" -> sin(a)
            "cos" -> cos(a)
            "tan" -> tan(a)
            "ln" -> ln(a)
            "lg", "log" -> log10(a)
            "sqrt" -> if (a < 0) Double.NaN else sqrt(a)
            "abs" -> abs(a)
            "exp" -> exp(a)
            else -> Double.NaN
        }
    }
}

/* ------------------------------------------------------------------
 * 曲线定义：显式 y=f(x) 与隐式 F(x,y)=0
 * ---------------------------------------------------------------- */

sealed interface CurveDef {
    /** y = f(x)，逐点采样，能正确处理渐近线断开 */
    data class Explicit(val f: Expr) : CurveDef

    /** F(x,y) = 0，圆、椭圆这类一个 x 对应两个 y 的曲线只能这么画 */
    data class Implicit(val f: Expr) : CurveDef
}

/**
 * 解析一行输入。
 *
 * - 没有等号 → 当作 y = f(x)
 * - 「y = ...」→ 仍按显式处理，享受渐近线断开的画质
 * - 其余含等号的 → 移项成 F = 左 − 右 = 0
 */
fun parseCurve(raw: String): CurveDef {
    val text = raw.trim()
    val eq = text.indexOf('=')
    if (eq < 0) return CurveDef.Explicit(parseExpression(text))

    val lhs = text.substring(0, eq).trim()
    val rhs = text.substring(eq + 1).trim()
    if (lhs.isEmpty()) throw ParseException("等号左边是空的", eq)
    if (rhs.isEmpty()) throw ParseException("等号右边是空的", eq)
    if (lhs == "y") return CurveDef.Explicit(parseExpression(rhs))

    return CurveDef.Implicit(Expr.Binary('-', parseExpression(lhs), parseExpression(rhs)))
}

fun CurveDef.variables(): List<String> = when (this) {
    is CurveDef.Explicit -> f.variables()
    is CurveDef.Implicit -> f.variables()
}

/**
 * 用 marching squares 提取 F(x,y)=0 的等值线。
 *
 * 显式采样对「一个 x 两个 y」的曲线无能为力（圆、椭圆、双曲线），
 * 所以在网格上求值、按符号变化插值出线段。每个格子独立出一小段，
 * 相邻线段端点重合，圆头线帽会把它们连成光滑曲线。
 */
fun sampleImplicit(
    f: (Double, Double) -> Double,
    xMin: Double,
    xMax: Double,
    yMin: Double,
    yMax: Double,
    cols: Int = 180,
    rows: Int = 180,
): List<List<Pair<Double, Double>>> {
    if (cols <= 0 || rows <= 0 || xMax <= xMin || yMax <= yMin) return emptyList()

    val segments = ArrayList<List<Pair<Double, Double>>>(cols * 4)
    val dx = (xMax - xMin) / cols
    val dy = (yMax - yMin) / rows

    var lower = DoubleArray(cols + 1) { c -> f(xMin + c * dx, yMin) }

    for (r in 0 until rows) {
        val y0 = yMin + r * dy
        val y1 = y0 + dy
        val upper = DoubleArray(cols + 1) { c -> f(xMin + c * dx, y1) }

        for (c in 0 until cols) {
            val x0 = xMin + c * dx
            val x1 = x0 + dx

            val v0 = lower[c]        // 左下
            val v1 = lower[c + 1]    // 右下
            val v2 = upper[c + 1]    // 右上
            val v3 = upper[c]        // 左上

            // 任何一角未定义就整格跳过，避免在渐近线附近画出假线
            if (v0.isNaN() || v1.isNaN() || v2.isNaN() || v3.isNaN()) continue

            var idx = 0
            if (v0 > 0) idx = idx or 1
            if (v1 > 0) idx = idx or 2
            if (v2 > 0) idx = idx or 4
            if (v3 > 0) idx = idx or 8
            if (idx == 0 || idx == 15) continue

            // 四条边上的零点位置
            val bx = x0 + dx * fraction(v0, v1); val by = y0
            val rx = x1; val ry = y0 + dy * fraction(v1, v2)
            val tx = x0 + dx * fraction(v3, v2); val ty = y1
            val lx = x0; val ly = y0 + dy * fraction(v0, v3)

            fun add(ax: Double, ay: Double, cx: Double, cy: Double) {
                segments += listOf(ax to ay, cx to cy)
            }

            when (idx) {
                1, 14 -> add(lx, ly, bx, by)
                2, 13 -> add(bx, by, rx, ry)
                3, 12 -> add(lx, ly, rx, ry)
                4, 11 -> add(rx, ry, tx, ty)
                6, 9 -> add(bx, by, tx, ty)
                7, 8 -> add(lx, ly, tx, ty)
                // 5 和 10 是鞍点，两种连法都合法，取一种保证不断线
                5 -> { add(lx, ly, tx, ty); add(bx, by, rx, ry) }
                10 -> { add(lx, ly, bx, by); add(rx, ry, tx, ty) }
            }
        }
        lower = upper
    }
    return segments
}

/** 在 a→b 之间按符号变化线性插值，返回 0..1 的比例。 */
private fun fraction(a: Double, b: Double): Double {
    val d = a - b
    return if (abs(d) < 1e-12) 0.5 else (a / d).coerceIn(0.0, 1.0)
}

/**
 * 采样成若干段折线。
 *
 * 关键在断点处理：`tan(x)`、`1/x` 这类函数如果直接逐点连线，会在渐近线处画出一条
 * 竖直假线。这里的判定是「相邻两点都在可视区外，且跳到另一侧」就断开。
 */
fun Expr.sampleSegments(
    xMin: Double,
    xMax: Double,
    count: Int,
    params: Map<String, Double>,
    yMin: Double,
    yMax: Double,
): List<List<Pair<Double, Double>>> {
    val segments = mutableListOf<List<Pair<Double, Double>>>()
    var current = mutableListOf<Pair<Double, Double>>()
    val span = (yMax - yMin).coerceAtLeast(1e-9)
    var previous: Double? = null

    for (i in 0..count) {
        val x = xMin + (xMax - xMin) * i / count
        val y = eval(x, params)

        if (!y.isFinite()) {
            if (current.size > 1) segments += current
            current = mutableListOf()
            previous = null
            continue
        }

        // 远到没边的点不画，避免把纵轴压扁
        if (abs(y) > span * 50) {
            if (current.size > 1) segments += current
            current = mutableListOf()
            previous = y
            continue
        }

        val p = previous
        if (p != null && abs(y - p) > span * 2 && (y < yMin || y > yMax) && (p < yMin || p > yMax)) {
            // 跨越渐近线，断开
            if (current.size > 1) segments += current
            current = mutableListOf()
        }

        current += x to y
        previous = y
    }
    if (current.size > 1) segments += current
    return segments
}

