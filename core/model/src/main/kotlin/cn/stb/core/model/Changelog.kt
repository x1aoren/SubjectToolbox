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
            versionName = "1.1.1",
            versionCode = 3,
            date = "2026-11-21",
            headline = "汉语词典搜索优化：例句不再算命中，释义相关结果单独收起",
            changes = listOf(
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "释义检索只匹配义项主干，例句里出现该词不再算命中（搜「苹果」不会再冒出「咬」「个」「蔫」这类字条）；释义反查改用词库自带的二元组索引，结果单独成组、默认收起，不再和词条命中混在一起。",
                ),
            ),
        ),
        ReleaseNote(
            versionName = "1.1.0",
            versionCode = 2,
            date = "2026-11-20",
            headline = "全年级公测 · 函数编辑器大更新，新增汉语词典与英语语法",
            changes = listOf(
                ChangeItem(
                    ChangeKind.NEW,
                    "增加远程遥测等云端功能。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "语文·汉语词典：收录《现代汉语词典》第 7 版 70,113 条词条，支持汉字、词语、拼音与释义检索；繁体和异体写法会自动跳到对应词条。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "汉语词条页展示拼音、繁体、异体和分段释义，义项序号与词性单独标色；本地生词本可收藏常查词条，词库升级时自动保留。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "英语·语法：薄冰大学英语语法 49 篇专题，按 24 类目录排列；标题、表格和例句全部原生 Compose 排版，不套网页，支持全文检索并高亮命中。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "「更新日志」页与版本升级提示上线：每个版本只提示一次，设置 → 关于可随时回看。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "函数定义域：表达式后面直接写 {0,10} 就能限定（一侧留空表示无界），也能用「定义域」对话框设置；输入框右端、函数列表和图例都会带上这个标记。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "字号新增 0.85×「较小」档，和标准、较大、特大同一行切换。",
                ),
                ChangeItem(
                    ChangeKind.NEW,
                    "双击表达式输入框可以唤起系统键盘：方便粘贴内容、输入键位上没有的符号；点一下空白处就退出。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "化学·方程式从 165 条扩到 508 条：按碱金属、常见金属、卤族、氮族、碳族、氧族与有机反应补齐，逐条校验原子守恒。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "方程式卡片的反应条件移到箭头（等号、可逆号）上方，化学式与箭头按基线对齐；搜索框和类型筛选条收窄，一屏能多放几条方程式。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "主页工具卡片只保留工具名，去掉下方的小字描述，卡片高度更紧凑，三栏学科区不再因为描述行数不同而高低不齐。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "主页「我的常用」首次启动不再预置古诗词、汉语词典与词典，改为空白，由用户自己点星标添加。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "函数编辑器整体翻新：显式 / 隐式手动切换、参数滑块可增删并单独设范围与步进、开方所见即所得、2x 这类隐式乘号直接认。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "光标落在上标还是根号里一眼可辨：所在区域整块高亮换色，拖动时手柄旁浮出「基线 / 上标 / 根号内」；点图像区或面板空白处即可收起光标，系统的选词放大镜和工具条也不再打扰。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "学科分区标题、工具卡片和显隐管理里的单字徽章换成 Lucide 图标，共 15 枚，随字号一起缩放。",
                ),
                ChangeItem(
                    ChangeKind.IMPROVE,
                    "公式大全从 116 条扩充到 281 条（数学 143 · 物理 99 · 化学 39），高中部分按教材章节补齐，每条都带变量说明、适用条件和备注。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "修复切换曲线后光标拖不动、点一下输入框还会把上一条曲线的表达式覆盖掉的问题。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "修复两条曲线偶尔共用同一个编号、改一条两条一起变；报错栏固定占位，键盘不再被顶来顶去。",
                ),
                ChangeItem(
                    ChangeKind.FIX,
                    "修复英语词典只输入一个汉字查不到内容的问题（如「水」）；列表也不再卡在 60 条，生词本全量展示，搜索结果可以「加载更多」。",
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

    /** 当前内置的最新版本名，如 "1.1.0"。 */
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
     * - 从 "1.0.0" 升级上来 → 只返回 1.1.0 一条；
     * - 跨版本升级（如 "0.9" → "1.1.0"）→ 返回中间所有版本，不遗漏；
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
