package cn.stb.feature.periodic

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicTableTest {

    private fun element(
        z: Int,
        symbol: String,
        x: Int,
        y: Int,
        cat: String = "nonmetal",
    ) = ChemElement(
        z = z,
        symbol = symbol,
        zh = symbol,
        en = symbol,
        mass = "1",
        cat = cat,
        group = 1,
        period = 1,
        x = x,
        y = y,
        phase = "固态",
        melt = null,
        boil = null,
        density = null,
        config = "",
    )

    @Test
    fun `分类名称覆盖全部类别`() {
        val labels = mapOf(
            "alkali" to "碱金属",
            "alkaline" to "碱土金属",
            "transition" to "过渡金属",
            "post" to "主族金属",
            "metalloid" to "类金属",
            "nonmetal" to "非金属",
            "noble" to "稀有气体",
            "lanthanide" to "镧系",
            "actinide" to "锕系",
        )
        labels.forEach { (cat, label) -> assertEquals(label, categoryLabel(cat)) }
        assertEquals("未知性质", categoryLabel("unknown"))
    }

    @Test
    fun `分类配色互不重复且未知分类有兜底色`() {
        val cats = listOf(
            "alkali", "alkaline", "transition", "post", "metalloid",
            "nonmetal", "noble", "lanthanide", "actinide",
        )
        assertEquals(cats.size, cats.map(::categoryColor).distinct().size)
        assertEquals(Color(0xFFDADADA), categoryColor("unknown"))
        assertNotEquals(categoryColor("unknown"), categoryColor("nonmetal"))
    }

    @Test
    fun `网格尺寸固定为 18 列 10 行`() {
        val grid = layoutElements(emptyList())
        assertEquals(ROWS + 1, grid.size)
        assertTrue(grid.all { it.size == COLS + 1 })
        assertNull(grid[1][1])
    }

    @Test
    fun `元素按坐标落位`() {
        val hydrogen = element(1, "H", x = 1, y = 1)
        val helium = element(2, "He", x = 18, y = 1)
        val radon = element(86, "Rn", x = 18, y = 6)

        val grid = layoutElements(listOf(hydrogen, helium, radon))

        assertEquals(hydrogen, grid[1][1])
        assertEquals(helium, grid[1][18])
        assertEquals(radon, grid[6][18])
        assertNull(grid[2][1])
    }

    @Test
    fun `镧系与锕系单独占第 9 和第 10 行`() {
        val lanthanum = element(57, "La", x = 3, y = 9, cat = "lanthanide")
        val lutetium = element(71, "Lu", x = 17, y = 9, cat = "lanthanide")
        val actinium = element(89, "Ac", x = 3, y = 10, cat = "actinide")
        val lawrencium = element(103, "Lr", x = 17, y = 10, cat = "actinide")

        val grid = layoutElements(listOf(lanthanum, lutetium, actinium, lawrencium))

        assertEquals(lanthanum, grid[9][3])
        assertEquals(lutetium, grid[9][17])
        assertEquals(actinium, grid[10][3])
        assertEquals(lawrencium, grid[10][17])
        // 主表第 6/7 周期第 3 族留空，由占位格提示系列被抽到下方
        assertNull(grid[6][3])
        assertNull(grid[7][3])
    }

    @Test
    fun `越界坐标被忽略`() {
        val grid = layoutElements(
            listOf(
                element(0, "Zero", x = 0, y = 0),
                element(999, "Far", x = COLS + 1, y = ROWS + 1),
            )
        )
        assertTrue(grid.all { row -> row.all { it == null } })
    }
}
