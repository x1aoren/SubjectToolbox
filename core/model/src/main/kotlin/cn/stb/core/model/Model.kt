package cn.stb.core.model

import kotlinx.coroutines.flow.Flow

/* ------------------------------------------------------------------
 * 标识
 * ---------------------------------------------------------------- */

@JvmInline
value class SubjectId(val value: String)

@JvmInline
value class ToolId(val value: String)

/* ------------------------------------------------------------------
 * 学科与工具元数据
 * ---------------------------------------------------------------- */

/**
 * 学科图标的语义标识。
 *
 * 只存语义、不存 drawable 资源 id：core:model 是纯 Kotlin 模块，不引 Android 依赖，
 * 具体映射到哪张图由 core:designsystem 决定（见 IconBadge）。
 */
enum class SubjectIcon {
    BOOK_OPEN, CHART_SPLINE, LANGUAGES, FLASK, DNA, EARTH, GRID,
}

/** 工具图标的语义标识，理由同 [SubjectIcon]。 */
enum class ToolIcon {
    SCROLL, FUNCTION, DICTIONARY, ATOM, MICROSCOPE, ORBIT, LIBRARY, SIGMA, GRAMMAR,
}

/**
 * 工具条目的类型。
 *
 * [TOOL] 是主页上的常规工具（[ToolCatalogView.allTools]）；
 * [DIAGRAM] 是工具内部的子条目（如生物交互示意图里的细胞分裂）；
 * [FEATURE] 是主页上的功能入口（如意见反馈）。
 * 后两类只参与云端管控，不作为独立工具卡片出现在主页。
 */
enum class ToolKind { TOOL, DIAGRAM, FEATURE }

/**
 * 固定 ID：受后台「工具开关 / 单设备开关」单独管控、但不对应独立工具页的条目。
 *
 * 三端（Android / 后台服务 / 控制台）必须完全一致，因此集中在这里，禁止各模块硬编码字符串。
 */
object ManagedToolIds {
    const val BIOLOGY_CELL_DIVISION = "biology.diagram.cell-division"
    const val GEOGRAPHY_EARTH_ROTATION = "geography.diagram.earth-rotation"
    const val FEEDBACK = "other.feedback"

    /** 字符串常量的强类型形式，供 [ToolSpec] 与本地路由使用。 */
    val biologyCellDivision = ToolId(BIOLOGY_CELL_DIVISION)
    val geographyEarthRotation = ToolId(GEOGRAPHY_EARTH_ROTATION)
    val feedback = ToolId(FEEDBACK)
}

/**
 * 一个学科。
 *
 * [short] 是单字缩写，图标化之后不再用于展示，保留给无障碍描述与图标缺失时的降级。
 * [accent] 为 ARGB 值，模型层不依赖 Compose，由 UI 层转成 Color。
 */
data class Subject(
    val id: SubjectId,
    val name: String,
    val short: String,
    val accent: Long,
    val order: Int,
    val icon: SubjectIcon? = null,
)

/**
 * 工具元数据。这是主页、设置页、搜索、导航认识一个工具的全部信息。
 * [id] 是持久化主键，一经发布不可更改。
 *
 * [kind] 与 [parentId] 描述条目在工具树里的位置：常规工具是 [ToolKind.TOOL]、
 * 无父级；交互示意图子项、功能入口由后台单独管控，[ToolCatalogView.everyEntry] 会带上它们。
 */
data class ToolSpec(
    val id: ToolId,
    val subjectId: SubjectId,
    val title: String,
    val summary: String,
    val glyph: String,
    val icon: ToolIcon? = null,
    val keywords: List<String> = emptyList(),
    /** 首次启动是否自动加入「我的常用」。当前没有工具声明它：新用户从空白常用开始，自己星标添加。 */
    val defaultPinned: Boolean = false,
    val kind: ToolKind = ToolKind.TOOL,
    val parentId: ToolId? = null,
)

/* ------------------------------------------------------------------
 * 个性化配置
 * ---------------------------------------------------------------- */

enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }

data class ThemePref(
    val seedArgb: Long = 0xFF3F6E5A,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val fontScale: Float = 1f,
)

data class HomePref(
    val pinnedToolIds: List<String> = emptyList(),
    val showRecents: Boolean = false,
)

data class VisibilityPref(
    val hiddenSubjectIds: Set<String> = emptySet(),
    val hiddenToolIds: Set<String> = emptySet(),
)

data class AppSettings(
    val theme: ThemePref = ThemePref(),
    val home: HomePref = HomePref(),
    val visibility: VisibilityPref = VisibilityPref(),
    val schemaVersion: Int = 1,
    /** 首次启动是否已完成默认置顶的播种，避免用户清空置顶后又被重新填满 */
    val seeded: Boolean = false,
    /**
     * 用户上次确认过更新提示的版本名（如 "1.0.0"），空串表示还没记录过。
     *
     * 只用于「每个版本只提示一次」：写入当前版本后，同一版本再次启动不再弹窗。
     * 是否全新安装由 core:data 的工具类按系统安装时间判断，不落盘——它属于系统事实，不是用户意图。
     */
    val lastSeenVersion: String = "",
    /** 匿名使用统计开关；关闭后不再记录和上报遥测事件（云端配置仍会同步）。 */
    val telemetryEnabled: Boolean = true,
    /** 已经提示过的云端最新版本，避免同一版本反复弹窗。 */
    val lastSeenRemoteVersion: String = "",
) {
    companion object {
        const val CURRENT_SCHEMA = 2
    }
}

/* ------------------------------------------------------------------
 * 主页呈现模型（由 HomeComposer 计算得出，不落盘）
 * ---------------------------------------------------------------- */

data class HomeSection(
    val subject: Subject,
    val tools: List<ToolSpec>,
)

data class HomeContent(
    val pinned: List<ToolSpec> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val recents: List<ToolSpec> = emptyList(),
    val hiddenPinnedCount: Int = 0,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && sections.isEmpty()
    val totalVisible: Int get() = sections.sumOf { it.tools.size }
}

/* ------------------------------------------------------------------
 * 仓库契约
 * ---------------------------------------------------------------- */

interface ToolCatalogView {
    val subjects: List<Subject>

    /** 主工具：主页卡片、显示管理、设置计数只认这一份（不含子工具与功能入口）。 */
    val allTools: List<ToolSpec>

    /** 受后台单独管控、但不在主页工具卡片里展示的条目（示意图子项、功能入口）。 */
    val managedEntries: List<ToolSpec> get() = emptyList()

    /** 主工具 + 受管控条目；握手/上报用这份全量清单。 */
    val everyEntry: List<ToolSpec> get() = allTools + managedEntries

    fun findTool(id: ToolId): ToolSpec?

    fun toolsOf(subjectId: SubjectId): List<ToolSpec>

    /** 在主工具与受管控条目里找；[findTool] 仍然只查主工具。 */
    fun findEntry(id: ToolId): ToolSpec? = everyEntry.firstOrNull { it.id == id }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface UsageRepository {
    val recents: Flow<List<String>>
    suspend fun recordOpen(toolId: String)
    suspend fun clearRecents()
}

/* ------------------------------------------------------------------
 * 装配入口
 *
 * 校园离线应用不需要复杂的 DI 框架：整个依赖图只有一个装配点（Application），
 * 由它把实现塞进这里。工具模块只面向接口，不关心实现从哪来。
 * ---------------------------------------------------------------- */

object AppGraph {
    lateinit var catalog: ToolCatalogView
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var usage: UsageRepository
        private set
    lateinit var cloud: CloudControl
        private set

    fun install(
        catalog: ToolCatalogView,
        settings: SettingsRepository,
        usage: UsageRepository,
        cloud: CloudControl,
    ) {
        this.catalog = catalog
        this.settings = settings
        this.usage = usage
        this.cloud = cloud
    }
}
