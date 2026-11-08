package cn.stb.core.registry

import cn.stb.core.model.AppSettings
import cn.stb.core.model.HomePref
import cn.stb.core.model.Subject
import cn.stb.core.model.SubjectId
import cn.stb.core.model.ToolCatalogView
import cn.stb.core.model.ToolId
import cn.stb.core.model.ToolSpec
import cn.stb.core.model.VisibilityPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeComposerTest {

    private val chinese = Subject(SubjectId("chinese"), "语文", "语", 0xFF9C4438, 0)
    private val math = Subject(SubjectId("math"), "数学", "数", 0xFF34558B, 1)

    private val poetry = ToolSpec(
        id = ToolId("chinese.poetry"),
        subjectId = SubjectId("chinese"),
        title = "古诗词",
        summary = "必背篇目",
        glyph = "诗",
    )
    private val mathTool = ToolSpec(
        id = ToolId("math.function"),
        subjectId = SubjectId("math"),
        title = "函数编辑器",
        summary = "绘制图像",
        glyph = "函",
    )

    private val catalog = object : ToolCatalogView {
        override val subjects = listOf(chinese, math)
        override val allTools = listOf(poetry, mathTool)
        override fun findTool(id: ToolId) = allTools.firstOrNull { it.id == id }
        override fun toolsOf(subjectId: SubjectId) = allTools.filter { it.subjectId == subjectId }
    }

    @Test
    fun `默认状态按学科分组且顺序稳定`() {
        val content = HomeComposer.compose(catalog, AppSettings(), emptyList())

        assertEquals(2, content.sections.size)
        assertEquals("语文", content.sections[0].subject.name)
        assertEquals("数学", content.sections[1].subject.name)
        assertEquals(2, content.totalVisible)
        assertTrue(content.pinned.isEmpty())
    }

    @Test
    fun `置顶保留用户排序`() {
        val settings = AppSettings(
            home = HomePref(pinnedToolIds = listOf("math.function", "chinese.poetry")),
        )
        val content = HomeComposer.compose(catalog, settings, emptyList())

        assertEquals(listOf("math.function", "chinese.poetry"), content.pinned.map { it.id.value })
    }

    @Test
    fun `隐藏学科会连带隐藏工具但不改动单项隐藏集合`() {
        val settings = AppSettings(
            visibility = VisibilityPref(hiddenSubjectIds = setOf("chinese")),
        )
        val content = HomeComposer.compose(catalog, settings, emptyList())

        assertEquals(1, content.sections.size)
        assertEquals("数学", content.sections[0].subject.name)
        assertEquals(1, content.totalVisible)
        assertTrue(settings.visibility.hiddenToolIds.isEmpty())
    }

    @Test
    fun `隐藏优先于置顶并给出计数`() {
        val settings = AppSettings(
            home = HomePref(pinnedToolIds = listOf("chinese.poetry", "math.function")),
            visibility = VisibilityPref(hiddenToolIds = setOf("chinese.poetry")),
        )
        val content = HomeComposer.compose(catalog, settings, emptyList())

        assertEquals(listOf("math.function"), content.pinned.map { it.id.value })
        assertEquals(1, content.hiddenPinnedCount)
    }

    @Test
    fun `未知的工具 id 被忽略而不是报错`() {
        val settings = AppSettings(
            home = HomePref(pinnedToolIds = listOf("physics.lab", "chinese.poetry")),
        )
        val content = HomeComposer.compose(catalog, settings, emptyList())

        assertEquals(listOf("chinese.poetry"), content.pinned.map { it.id.value })
        assertEquals(1, content.hiddenPinnedCount)
    }

    @Test
    fun `最近使用排除已置顶项并遵守开关`() {
        val recents = listOf("math.function", "chinese.poetry")
        val settings = AppSettings(
            home = HomePref(pinnedToolIds = listOf("chinese.poetry"), showRecents = true),
        )

        val content = HomeComposer.compose(catalog, settings, recents)
        assertEquals(listOf("math.function"), content.recents.map { it.id.value })

        val off = HomeComposer.compose(catalog, settings.copy(home = settings.home.copy(showRecents = false)), recents)
        assertTrue(off.recents.isEmpty())
    }

    @Test
    fun `全部隐藏时主页为空`() {
        val settings = AppSettings(
            visibility = VisibilityPref(
                hiddenSubjectIds = setOf("chinese", "math"),
            ),
        )
        val content = HomeComposer.compose(catalog, settings, emptyList())

        assertTrue(content.isEmpty)
        assertFalse(content.totalVisible > 0)
    }
}
