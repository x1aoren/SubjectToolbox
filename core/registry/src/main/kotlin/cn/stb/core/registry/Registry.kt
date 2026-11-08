package cn.stb.core.registry

import androidx.compose.runtime.Composable
import cn.stb.core.model.AppSettings
import cn.stb.core.model.HomeContent
import cn.stb.core.model.HomeSection
import cn.stb.core.model.Subject
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolCatalogView
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec

/**
 * 一个工具对外的全部能力。
 *
 * 工具只能通过 [ToolHost] 做两件事：打开另一个工具、关闭自己。
 * 这个接口刻意保持狭窄——工具不该有能力操作全局导航栈、改主题或读别人的数据。
 */
interface ToolProvider {
    val spec: ToolSpec

    @Composable
    fun Content(host: ToolHost)
}

interface ToolHost {
    fun openTool(id: ToolId)
    fun close()
}

/**
 * 注册表：把散落在各 feature 模块里的工具汇总成一份目录。
 *
 * 主页、学科页、显示管理、搜索都只读这里，没有任何一处硬编码工具名，
 * 因此新增工具不需要改动它们中的任何一行。
 */
class ToolCatalog(
    providers: List<ToolProvider>,
    subjectList: List<Subject>,
) : ToolCatalogView {

    private val providerById: Map<ToolId, ToolProvider> = providers.associateBy { it.spec.id }

    override val subjects: List<Subject> = subjectList.sortedBy { it.order }

    override val allTools: List<ToolSpec> = providers.map { it.spec }

    init {
        // 开发期就把重复 id、悬空学科这类错误暴露出来，而不是等用户点开空白页
        val duplicated = providers.groupBy { it.spec.id.value }.filterValues { it.size > 1 }.keys
        require(duplicated.isEmpty()) { "ToolId 重复注册: $duplicated" }

        val knownSubjects = subjects.map { it.id }.toSet()
        providers.map { it.spec }.forEach { spec ->
            require(spec.subjectId in knownSubjects) {
                "工具 ${spec.id.value} 指向不存在的学科 ${spec.subjectId.value}"
            }
        }
    }

    override fun findTool(id: ToolId): ToolSpec? = providerById[id]?.spec

    override fun toolsOf(subjectId: SubjectId): List<ToolSpec> =
        allTools.filter { it.subjectId == subjectId }

    fun providerOf(id: ToolId): ToolProvider? = providerById[id]
}

/* ------------------------------------------------------------------
 * 可见性与主页组装：纯函数，无 Android 依赖，可被 JVM 单测穷举
 * ---------------------------------------------------------------- */

object VisibilityResolver {

    fun isToolVisible(spec: ToolSpec, settings: AppSettings): Boolean {
        val v = settings.visibility
        return spec.id.value !in v.hiddenToolIds && spec.subjectId.value !in v.hiddenSubjectIds
    }

    fun visibleTools(catalog: ToolCatalogView, settings: AppSettings): List<ToolSpec> =
        catalog.allTools.filter { isToolVisible(it, settings) }

    fun visibleSubjects(catalog: ToolCatalogView, settings: AppSettings): List<Subject> {
        val visible = visibleTools(catalog, settings)
        val used = visible.map { it.subjectId }.toSet()
        return catalog.subjects.filter { it.id in used }
    }
}

object HomeComposer {

    private const val MAX_RECENTS = 6

    fun compose(
        catalog: ToolCatalogView,
        settings: AppSettings,
        recents: List<String>,
    ): HomeContent {
        val visible = VisibilityResolver.visibleTools(catalog, settings)
        val visibleById = visible.associateBy { it.id.value }

        // 置顶顺序就是用户拖拽出的顺序；被隐藏或被下线的 id 会被静默跳过，但保留在存储中
        val pinned = settings.home.pinnedToolIds.mapNotNull { visibleById[it] }
        val hiddenPinned = settings.home.pinnedToolIds.count { it !in visibleById }

        val sections = VisibilityResolver.visibleSubjects(catalog, settings).mapNotNull { subject ->
            val tools = visible.filter { it.subjectId == subject.id }
            if (tools.isEmpty()) null else HomeSection(subject, tools)
        }

        val recentTools = if (settings.home.showRecents) {
            recents.mapNotNull { visibleById[it] }
                .filter { tool -> pinned.none { it.id == tool.id } }
                .take(MAX_RECENTS)
        } else {
            emptyList()
        }

        return HomeContent(
            pinned = pinned,
            sections = sections,
            recents = recentTools,
            hiddenPinnedCount = hiddenPinned,
        )
    }
}
