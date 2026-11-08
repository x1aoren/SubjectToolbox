package cn.stb.feature.formula

import cn.stb.core.data.AssetReader

/**
 * 公式库由页面里的 KaTeX 直接渲染，Kotlin 侧只需要条数。
 * 数据格式见 assets/content/formula/formulas.json，由 HuiTurn/Formula 整理而来。
 */
class FormulaRepository(private val assets: AssetReader) {

    fun rawJson(): String = assets.rawText(PATH)

    fun count(): Int = assets.jsonObject(PATH).optJSONArray("formulas")?.length() ?: 0

    private companion object {
        const val PATH = "content/formula/formulas.json"
    }
}
