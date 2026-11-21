package cn.stb.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新日志的纯 JVM 单测。
 *
 * 这里只覆盖两件事：版本比较的边界，以及「从旧版本升级上来该看到哪些条目」。
 * 两者决定升级提示弹窗弹不弹、弹什么，错了用户就会漏看或反复看到更新提示。
 */
class ChangelogTest {

    @Test
    fun `版本比较按数字段逐段进行`() {
        assertTrue(Changelog.compareVersionNames("1.0.1", "1.0.0")!! > 0)
        assertTrue(Changelog.compareVersionNames("1.0.0", "1.0.1")!! < 0)
        assertTrue(Changelog.compareVersionNames("1.0.1", "1.0.1") == 0)

        // 逐段按数字比，不能按字符串比：1.10 比 1.9 新
        assertTrue(Changelog.compareVersionNames("1.10.0", "1.9.9")!! > 0)
        assertTrue(Changelog.compareVersionNames("2", "1.99")!! > 0)
        assertTrue(Changelog.compareVersionNames("1.0.0.1", "1.0.0")!! > 0)
    }

    @Test
    fun `缺失的版本段按零补齐`() {
        assertTrue(Changelog.compareVersionNames("1.0", "1.0.0") == 0)
        assertTrue(Changelog.compareVersionNames("v1.0.0", "1.0") == 0)
        assertTrue(Changelog.compareVersionNames("1.0.1", "1.0")!! > 0)
    }

    @Test
    fun `无法解析的版本名返回 null`() {
        assertNull(Changelog.compareVersionNames("abc", "1.0.0"))
        assertNull(Changelog.compareVersionNames("1.x.0", "1.0.0"))
        assertNull(Changelog.compareVersionNames("", "1.0.0"))
        assertNull(Changelog.compareVersionNames("1.0.0", "   "))
        assertNull(Changelog.compareVersionNames("1.-1.0", "1.0.0"))
    }

    @Test
    fun `内置日志按新到旧排列`() {
        assertEquals(listOf("1.1.0", "1.0.0"), Changelog.releases.map { it.versionName })
        assertEquals(2, Changelog.releases.size)

        val current = Changelog.current()
        assertEquals("1.1.0", current.versionName)
        assertEquals(2, current.versionCode)
        assertEquals("2026-11-20", current.date)
        assertEquals("全年级公测 · 函数编辑器大更新，新增汉语词典与英语语法", current.headline)
        assertEquals("1.1.0", Changelog.currentVersionName())

        val oldest = Changelog.releases.last()
        assertEquals("1.0.0", oldest.versionName)
        assertEquals(1, oldest.versionCode)
        assertEquals("2026-11-08", oldest.date)
    }

    @Test
    fun `上一版版本号用于没有记录时的升级提示`() {
        assertEquals("1.0.0", Changelog.previousVersionName("1.1.0"))
        assertNull("最老的版本没有更早的版本", Changelog.previousVersionName("1.0.0"))
        assertNull("比最老版本还旧也找不到", Changelog.previousVersionName("0.9"))
        assertNull("脏数据返回 null", Changelog.previousVersionName("未知"))
    }

    @Test
    fun `升级提示只包含比旧版本新的版本`() {
        // 1.0.0 → 1.1.0：只提示 1.1.0 的内容
        val newer = Changelog.releasesNewerThan("1.0.0")
        assertEquals(1, newer.size)
        assertEquals("1.1.0", newer.first().versionName)

        // 学校版装着 1.0.1 的设备升级上来，同样只看到 1.1.0 的内容
        assertEquals(1, Changelog.releasesNewerThan("1.0.1").size)

        // 同版本、更高版本、以及补齐 0 的等价写法：都没有新内容
        assertTrue(Changelog.releasesNewerThan("1.1.0").isEmpty())
        assertTrue(Changelog.releasesNewerThan("v1.1.0").isEmpty())
        assertTrue(Changelog.releasesNewerThan("1.1.1").isEmpty())
        assertEquals(1, Changelog.releasesNewerThan("1.0.0.0").size)

        // 跨版本升级不遗漏中间版本
        assertEquals(2, Changelog.releasesNewerThan("0.9").size)
        assertEquals(1, Changelog.releasesNewerThan("1.0").size)
    }

    @Test
    fun `未知版本兜底返回全部更新`() {
        assertEquals(Changelog.releases, Changelog.releasesNewerThan(""))
        assertEquals(Changelog.releases, Changelog.releasesNewerThan("未知"))
        assertEquals(Changelog.releases, Changelog.releasesNewerThan("1.0.x"))
    }

    @Test
    fun `当前版本日志覆盖三类变更且包含汉语词典`() {
        val changes = Changelog.current().changes
        assertTrue(changes.isNotEmpty())
        assertEquals(
            setOf(ChangeKind.NEW, ChangeKind.IMPROVE, ChangeKind.FIX),
            changes.map { it.kind }.toSet(),
        )
        assertTrue(
            "1.1.0 更新日志应包含汉语词典",
            changes.any { it.kind == ChangeKind.NEW && it.text.contains("汉语词典") },
        )
        assertTrue(
            "1.1.0 更新日志应包含主页卡片调整",
            changes.any { it.kind == ChangeKind.IMPROVE && it.text.contains("工具卡片") },
        )
        assertTrue(changes.all { it.text.isNotBlank() })
        assertTrue(Changelog.releases.all { release -> release.changes.isNotEmpty() })

        // 原有 1.1.0 条目仍然保留
        val v110 = Changelog.releases.first { it.versionName == "1.1.0" }
        assertTrue(
            "1.1.0 更新日志应包含键盘排版修复",
            v110.changes.any { it.text.contains("键盘") },
        )
        assertTrue(
            "1.1.0 更新日志应包含更新提示",
            v110.changes.any { it.text.contains("更新提示") || it.text.contains("升级提示") },
        )
        assertTrue(
            "1.1.0 更新日志应记录切换曲线的修复",
            v110.changes.any { it.kind == ChangeKind.FIX && it.text.contains("切换") },
        )
    }
}
