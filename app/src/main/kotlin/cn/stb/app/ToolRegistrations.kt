package cn.stb.app

import cn.stb.core.model.Subject
import cn.stb.core.model.SubjectId
import cn.stb.core.registry.ToolCatalog
import cn.stb.core.registry.ToolProvider
import cn.stb.feature.biology.BiologyDiagramToolProvider
import cn.stb.feature.chemistry.ChemistryToolProvider
import cn.stb.feature.dictionary.DictionaryToolProvider
import cn.stb.feature.formula.FormulaToolProvider
import cn.stb.feature.geography.GeographyDiagramToolProvider
import cn.stb.feature.math.MathToolProvider
import cn.stb.feature.poetry.PoetryToolProvider
import cn.stb.feature.textbook.TextbookToolProvider

/**
 * 唯一的装配点。
 *
 * 新增一个工具 = 在 tools 里加一行，其余地方（主页、显示管理、搜索、导航）一行都不用改。
 * 这就是「工具即插件」在工程上的全部含义。
 */
object ToolRegistrations {

    val subjects: List<Subject> = listOf(
        Subject(SubjectId("chinese"), "语文", "语", 0xFF9C4438, 0),
        Subject(SubjectId("math"), "数学", "数", 0xFF34558B, 1),
        Subject(SubjectId("english"), "英语", "英", 0xFF2E7D6F, 2),
        Subject(SubjectId("chemistry"), "化学", "化", 0xFF6B4A8F, 3),
        Subject(SubjectId("biology"), "生物", "生", 0xFF2E7D32, 4),
        Subject(SubjectId("geography"), "地理", "地", 0xFF9A6B1F, 5),
        Subject(SubjectId("other"), "通用", "通", 0xFF546E7A, 6),
    )

    val tools: List<ToolProvider> = listOf(
        PoetryToolProvider(),
        MathToolProvider(),
        DictionaryToolProvider(),
        ChemistryToolProvider(),
        BiologyDiagramToolProvider(),
        GeographyDiagramToolProvider(),
        TextbookToolProvider(),
        FormulaToolProvider(),
    )
}

/** 持有注册表的具体类型，供导航层按 id 取到可渲染的 provider。 */
object AppContainer {
    lateinit var catalog: ToolCatalog
}
