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
 * 一个学科。[short] 用单个汉字做图标字形，避免为每个学科准备一套图标资源。
 * [accent] 为 ARGB 值，模型层不依赖 Compose，由 UI 层转成 Color。
 */
data class Subject(
    val id: SubjectId,
    val name: String,
    val short: String,
    val accent: Long,
    val order: Int,
)

/**
 * 工具元数据。这是主页、设置页、搜索、导航认识一个工具的全部信息。
 * [id] 是持久化主键，一经发布不可更改。
 */
data class ToolSpec(
    val id: ToolId,
    val subjectId: SubjectId,
    val title: String,
    val summary: String,
    val glyph: String,
    val keywords: List<String> = emptyList(),
    val defaultPinned: Boolean = false,
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
) {
    companion object {
        const val CURRENT_SCHEMA = 1
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
    val allTools: List<ToolSpec>
    fun findTool(id: ToolId): ToolSpec?
    fun toolsOf(subjectId: SubjectId): List<ToolSpec>
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

    fun install(
        catalog: ToolCatalogView,
        settings: SettingsRepository,
        usage: UsageRepository,
    ) {
        this.catalog = catalog
        this.settings = settings
        this.usage = usage
    }
}
