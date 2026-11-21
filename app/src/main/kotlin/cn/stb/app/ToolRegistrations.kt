package cn.stb.app

import cn.stb.core.model.ManagedToolIds
import cn.stb.core.model.Subject
import cn.stb.core.model.SubjectIcon
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolKind
import cn.stb.core.model.ToolSpec
import cn.stb.core.registry.ToolCatalog
import cn.stb.core.registry.ToolProvider
import cn.stb.feature.biology.BiologyDiagramToolProvider
import cn.stb.feature.chemistry.ChemistryToolProvider
import cn.stb.feature.dictionary.DictionaryToolProvider
import cn.stb.feature.formula.FormulaToolProvider
import cn.stb.feature.geography.GeographyDiagramToolProvider
import cn.stb.feature.grammar.GrammarToolProvider
import cn.stb.feature.math.MathToolProvider
import cn.stb.feature.periodic.PeriodicTableToolProvider
import cn.stb.feature.poetry.PoetryToolProvider
import cn.stb.feature.textbook.TextbookToolProvider
import cn.stb.feature.zhdict.ChineseDictionaryToolProvider

/**
 * 唯一的装配点。
 *
 * 新增一个工具 = 在 tools 里加一行，其余地方（主页、显示管理、搜索、导航）一行都不用改。
 * 这就是「工具即插件」在工程上的全部含义。
 */
object ToolRegistrations {

    val subjects: List<Subject> = listOf(
        Subject(SubjectId("chinese"), "语文", "语", 0xFF9C4438, 0, SubjectIcon.BOOK_OPEN),
        Subject(SubjectId("math"), "数学", "数", 0xFF34558B, 1, SubjectIcon.CHART_SPLINE),
        Subject(SubjectId("english"), "英语", "英", 0xFF2E7D6F, 2, SubjectIcon.LANGUAGES),
        Subject(SubjectId("chemistry"), "化学", "化", 0xFF6B4A8F, 3, SubjectIcon.FLASK),
        Subject(SubjectId("biology"), "生物", "生", 0xFF2E7D32, 4, SubjectIcon.DNA),
        Subject(SubjectId("geography"), "地理", "地", 0xFF9A6B1F, 5, SubjectIcon.EARTH),
        Subject(SubjectId("other"), "通用", "通", 0xFF546E7A, 6, SubjectIcon.GRID),
    )

    val tools: List<ToolProvider> = listOf(
        PoetryToolProvider(),
        ChineseDictionaryToolProvider(),
        MathToolProvider(),
        DictionaryToolProvider(),
        GrammarToolProvider(),
        ChemistryToolProvider(),
        PeriodicTableToolProvider(),
        BiologyDiagramToolProvider(),
        GeographyDiagramToolProvider(),
        TextbookToolProvider(),
        FormulaToolProvider(),
    )

    /**
     * 受后台单独管控、但不对应独立工具页的条目。
     *
     * id / kind / parentId 必须与《管控扩展-设计契约》第 1 节以及后台目录完全一致：
     * 它们不进入主页工具卡片（[ToolCatalog.allTools] 仍是 11 个主工具），
     * 只参与云端握手/上报（[ToolCatalog.everyEntry]），由对应页面按固定 id 取停用状态。
     */
    val managedEntries: List<ToolSpec> = listOf(
        ToolSpec(
            id = ManagedToolIds.biologyCellDivision,
            subjectId = SubjectId("biology"),
            title = "细胞分裂交互图",
            summary = "有丝分裂 / 减数分裂动画，染色体与 DNA 数目实时统计",
            glyph = "裂",
            keywords = listOf("生物", "细胞", "细胞分裂", "有丝分裂", "减数分裂"),
            kind = ToolKind.DIAGRAM,
            parentId = ToolId("biology.diagram"),
        ),
        ToolSpec(
            id = ManagedToolIds.geographyEarthRotation,
            subjectId = SubjectId("geography"),
            title = "地球自转交互图",
            summary = "自转方向、角速度与线速度随纬度变化",
            glyph = "转",
            keywords = listOf("地理", "地球", "自转", "昼夜交替"),
            kind = ToolKind.DIAGRAM,
            parentId = ToolId("geography.diagram"),
        ),
        ToolSpec(
            id = ManagedToolIds.feedback,
            subjectId = SubjectId("other"),
            title = "意见反馈",
            summary = "向学校反馈问题、建议与内容纠错",
            glyph = "反",
            keywords = listOf("反馈", "意见", "建议", "纠错"),
            kind = ToolKind.FEATURE,
        ),
    )
}

/** 持有注册表的具体类型，供导航层按 id 取到可渲染的 provider。 */
object AppContainer {
    lateinit var catalog: ToolCatalog
}
