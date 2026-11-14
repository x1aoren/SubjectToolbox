package cn.stb.core.model

/**
 * 更新内容的分类。UI 层直接用 [label] 画小标签，避免各页面各写一份中文映射。
 */
enum class ChangeKind(val label: String) {
    /** 新功能 */
    NEW("新增"),

    /** 既有功能增强 */
    IMPROVE("优化"),

    /** 问题修复 */
    FIX("修复"),
}

/** 一条更新说明。 */
data class ChangeItem(
    val kind: ChangeKind,
    val text: String,
)

/**
 * 一个版本的更新日志。
 *
 * [versionName] 必须与 `app/build.gradle.kts` 里的 `versionName` 一字不差，
 * 更新提示的「已从 vX 更新至 vY」与「当前版本」标记都靠字符串相等来判定。
 */
data class ReleaseNote(
    val versionName: String,
    val versionCode: Int,
    /** 发布日期，形如 "2026-11-10"。写死而不是取当前时间：内容发布后不应随构建日期漂移。 */
    val date: String,
    val headline: String,
    val changes: List<ChangeItem>,
)

/**
 * 内置更新日志。
 *
 * 这是纯数据，不读网络、不读文件：发版时在 [releases] 最前面加一条即可，
 * 「设置 → 关于 → 更新日志」页与升级提示弹窗会自动带上它。
 *
 * 版本比较按数字段解析（"1.0.1" > "1.0.0"），不引入第三方版本号库——
 * 本项目的版本号由自己控制，格式固定为「主.次.修订」。
 */
object Changelog {

    /**
     * 全部版本的更新日志，**新 → 旧**排列。
     *
     * 顺序即时间线：第一条是当前版本，最后一个是最早的公开版本。
     */
    val releases: List<ReleaseNote> = listOf(
        ReleaseNote(
            versionName = "1.0.1",
            versionCode = 2,
            date = "2026-11-10",
            headline = "更新日志上线，函数编辑器大更新",
            changes = listOf(
                ChangeItem(
                    ChangeKind.NEW,
                    "新增「更新日志」页面：设置 → 关于 → 更新日志，随时回看每个版本改了什么。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "新增版本升级提示：升级或重新安装后首次打开都会展示本次更新内容，每个版本只提示一次。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "字号新增 0.85×「较小」档，和标准、较大、特大四档并列，同一行即可切换。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "函数编辑器新增函数定义域：可以分别限制曲线左右端点的取值范围，留空表示该侧无界。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "函数编辑器：显式 y=f(x) 与隐式 F(x,y)=0 改为手动切换，不再根据有没有等号去猜。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "参数滑块可以手动增删，并能为每个滑块单独设置最小值、最大值与步进；自动发现的参数滑块仍然保留。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "开方输入所见即所得：√ 键一步生成 sqrt()，按括号层级即时呈现根号与横线，不必再手写函数名。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "函数列表改为横向切换条，一眼看到每条曲线的模式与表达式，同时为键盘腾出完整空间。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "智能输入更贴近手写习惯：2x、3(x+1) 这类隐式乘号，以及常用函数名、常数都能直接输入。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "数学键盘重新排版：数字键大小统一、按网格对齐，0 与其它数字放在一起，键盘完整常驻显示。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "编辑乘方、开方时光标高度与右上角角标 / 根号内容区对齐；光标下方的手柄可在任意位置按住拖动，上标里也能正常移动。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "修复表达式编辑时的光标与角标交互问题：按键插入跟随光标位置，删除与选区替换不再错位。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "报错栏固定占位：表达式出错时错误提示不再把键盘和绘图区顶来顶去。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "设置页「更新日志」入口改为版本号旁边的紧凑小按钮，不再占满一整行。",
                ),
            ),
        ),
        ReleaseNote(
            versionName = "1.0.0",
            versionCode = 1,
            date = "2026-11-08",
            headline = "首个正式版本",
            changes = listOf(
                ChangeItem(
                    ChangeKind.NEW,
                    "语文·古诗词：61 篇新课标必背篇目，按册选择，带注释、赏析与背诵模式。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "数学·函数编辑器：最多 6 条曲线同坐标系，圆/椭圆等隐式方程，缩放平移与共享参数滑块。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "英语·词典：ECDICT 77 万词条离线查询，音标、考试标签、词形变化与本地生词本。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "化学·方程式：165 条方程式检索、化学式下标排版与自动配平，另有 118 元素周期表。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "生物 / 地理·交互示意图：细胞分裂与地球自转两组可交互的示意图，索引页可搜索。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "通用·公式大全：116 条数理化公式，KaTeX 排版与符号搜索；电子教材内嵌人教社官网。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "个性化：深浅色、字号、工具置顶、学科与工具显隐管理、最近使用，全部配置只存本机。",
                ),
            ),
        ),
    )

    /** 当前内置的最新版本条目（[releases] 的第一条）。 */
    fun current(): ReleaseNote = releases.first()

    /** 当前内置的最新版本名，如 "1.0.2"。 */
    fun currentVersionName(): String = releases.first().versionName

    /**
     * 找出比 [versionName] 旧的最近一个发布版本。
     *
     * 没有「上次看到的版本」记录时（首次安装、或从很旧的版本直接覆盖上来），
     * 升级提示需要一个合理的旧版本号来拼标题，这里取最近的一个历史版本。
     * 找不到（比最老版本还旧、或版本名无法解析）返回 null。
     */
    fun previousVersionName(versionName: String): String? {
        if (parseVersionSegments(versionName) == null) return null
        return releases.firstOrNull { release ->
            val comparison = compareVersionNames(release.versionName, versionName)
            comparison != null && comparison < 0
        }?.versionName
    }

    /**
     * 按数字段比较两个版本名，返回 left 相对 right 的方向。
     *
     * 规则：
     * - 允许前导 `v` / `V` 与首尾空白；
     * - 按 `.` 分段后逐段转 Int 比较，缺的段按 0 补齐（因此 "1.0" 与 "1.0.0" 相等）；
     * - 任一段不是非负整数（含空串）即视为无法解析，返回 `null`，由调用方决定兜底策略。
     *
     * @return 负数表示 [left] 更旧，0 表示相同，正数表示 [left] 更新；无法解析返回 null。
     */
    fun compareVersionNames(left: String, right: String): Int? {
        val leftSegments = parseVersionSegments(left) ?: return null
        val rightSegments = parseVersionSegments(right) ?: return null
        val size = maxOf(leftSegments.size, rightSegments.size)
        for (index in 0 until size) {
            // 缺段补 0：1.0 与 1.0.0 视为同一版本
            val a = leftSegments.getOrElse(index) { 0 }
            val b = rightSegments.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    /**
     * 找出比 [oldVersionName] 更新的所有版本，仍然按新 → 旧排列。
     *
     * 这是升级提示弹窗的内容来源：
     * - 从 "1.0.0" 升级上来 → 只返回 1.0.1 一条；
     * - 跨版本升级（如 "0.9" → "1.0.1"）→ 返回中间所有版本，不遗漏；
     * - 与当前版本相同或比当前版本还新 → 返回空列表；
     * - [oldVersionName] 无法解析（空串、脏数据）→ 兜底返回全部，宁可多提示也不吞掉更新内容。
     */
    fun releasesNewerThan(oldVersionName: String): List<ReleaseNote> {
        if (parseVersionSegments(oldVersionName) == null) return releases
        return releases.filter { release ->
            val comparison = compareVersionNames(release.versionName, oldVersionName)
            // 内置版本名理论上都可解析；万一写错也按「更新」处理，避免新版本内容被静默吞掉
            comparison == null || comparison > 0
        }
    }

    /**
     * 把版本名拆成数字段，如 "v1.0.1" → [1, 0, 1]；无法解析返回 null。
     */
    private fun parseVersionSegments(raw: String): List<Int>? {
        val text = raw.trim().removePrefix("v").removePrefix("V")
        if (text.isEmpty()) return null
        val parts = text.split('.')
        if (parts.isEmpty()) return null
        return parts.map { part ->
            part.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }
    }
}
