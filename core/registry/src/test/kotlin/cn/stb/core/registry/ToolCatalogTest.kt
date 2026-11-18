package cn.stb.core.registry

import androidx.compose.runtime.Composable
import cn.stb.core.model.ManagedToolIds
import cn.stb.core.model.Subject
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolKind
import cn.stb.core.model.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogTest {

    private val biology = Subject(SubjectId("biology"), "生物", "生", 0xFF2E7D32, 0)
    private val other = Subject(SubjectId("other"), "通用", "通", 0xFF546E7A, 1)

    private val biologyDiagram = ToolSpec(
        id = ToolId("biology.diagram"),
        subjectId = SubjectId("biology"),
        title = "交互示意图",
        summary = "细胞分裂等交互动画",
        glyph = "图",
    )

    private val cellDivision = ToolSpec(
        id = ManagedToolIds.biologyCellDivision,
        subjectId = SubjectId("biology"),
        title = "细胞分裂交互图",
        summary = "有丝分裂 / 减数分裂动画",
        glyph = "裂",
        kind = ToolKind.DIAGRAM,
        parentId = biologyDiagram.id,
    )

    private val feedback = ToolSpec(
        id = ManagedToolIds.feedback,
        subjectId = SubjectId("other"),
        title = "意见反馈",
        summary = "向学校反馈问题与建议",
        glyph = "反",
        kind = ToolKind.FEATURE,
    )

    private class FakeProvider(override val spec: ToolSpec) : ToolProvider {
        @Composable
        override fun Content(host: ToolHost) = Unit
    }

    private fun catalog(vararg managed: ToolSpec) = ToolCatalog(
        providers = listOf(FakeProvider(biologyDiagram)),
        subjectList = listOf(biology, other),
        managedEntries = managed.toList(),
    )

    @Test
    fun `子工具不进入主工具列表只进入全量清单`() {
        val catalog = catalog(cellDivision, feedback)

        assertEquals(listOf("biology.diagram"), catalog.allTools.map { it.id.value })
        assertEquals(
            listOf(ManagedToolIds.BIOLOGY_CELL_DIVISION, ManagedToolIds.FEEDBACK),
            catalog.managedEntries.map { it.id.value },
        )
        assertEquals(3, catalog.everyEntry.size)
        assertNull(catalog.findTool(ManagedToolIds.biologyCellDivision))
        assertEquals("细胞分裂交互图", catalog.findEntry(ManagedToolIds.biologyCellDivision)?.title)
        assertEquals(ToolKind.DIAGRAM, catalog.findEntry(ManagedToolIds.biologyCellDivision)?.kind)
        assertEquals("other.feedback", catalog.findEntry(ManagedToolIds.feedback)?.id?.value)
    }

    @Test
    fun `未配置 managed 条目时全量清单等于主工具`() {
        val catalog = catalog()

        assertTrue(catalog.managedEntries.isEmpty())
        assertEquals(catalog.allTools, catalog.everyEntry)
    }

    @Test
    fun `托管条目与主工具 id 重复时直接报错`() {
        val duplicated = cellDivision.copy(id = biologyDiagram.id)

        val error = assertThrows(IllegalArgumentException::class.java) { catalog(duplicated) }
        assertTrue(error.message.orEmpty().contains("ToolId 重复注册"))
    }

    @Test
    fun `托管条目之间 id 重复时直接报错`() {
        assertThrows(IllegalArgumentException::class.java) { catalog(cellDivision, cellDivision) }
    }

    @Test
    fun `托管条目指向不存在的学科时直接报错`() {
        val dangling = feedback.copy(subjectId = SubjectId("history"))

        assertThrows(IllegalArgumentException::class.java) { catalog(dangling) }
    }
}
