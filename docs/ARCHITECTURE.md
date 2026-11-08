# 学科工具箱 SubjectToolbox — 架构规划

> 版本 v0.1 · 目标平台：Android 平板（Android 14 为主，向后兼容至 Android 8.0）
> 使用场景：高中校园内部，学生自带或学校统一管理的平板

---

## 0. 一页速览

| 维度 | 决策 |
|---|---|
| 语言 | Kotlin 2.x（100%，无 Java） |
| UI | Jetpack Compose + Material 3，平板自适应布局 |
| 架构 | 多模块 + 单向数据流（MVVM）+ **工具注册表（Tool Registry）** |
| 依赖注入 | Hilt（KSP），用 `@IntoSet` 做工具注册 |
| 个性化配置 | Proto DataStore（主题 / 置顶 / 显隐） |
| 离线内容 | `assets` 内 JSON + 矢量资源 + 预置 SQLite（FTS4） |
| 用户数据 | Room（与内容库物理隔离） |
| 网络 | 仅「电子教材」申请 `INTERNET` 访问人教社官网；其余功能物理级离线 |
| 构建 | Gradle KTS + Version Catalog + `build-logic` 约定插件 |
| 版本 | compileSdk 35 / targetSdk 34 / **minSdk 26** |

三条贯穿全局的设计主线：

1. **内容与代码分离** — 教材版本、词库、地图都是「数据」，由独立的构建管线产出，升级内容不必改代码。
2. **工具即插件** — 每个学科工具是一个自包含模块，通过契约注册进注册表；主页、设置页、导航都不认识具体工具。
3. **配置只存意图** — 存储层只记录「用户置顶了谁、隐藏了谁、选了什么颜色」，所有呈现结果由解析器实时计算。

---

## 1. 目标、约束与非目标

### 1.1 目标

- 把语文 / 数学 / 英语 / 化学 / 地理五科工具收进一个 App，**全部功能离线可用**。
- 主页可定制：常用工具置顶、主题色可改、学科与工具可隐藏。
- 新增学科工具时，**不改动核心模块、不改导航、不改主页**。

### 1.2 硬约束

| 约束 | 架构含义 |
|---|---|
| 离线为主 | 仅「电子教材」联网（WebView 打开 jc.pep.com.cn），其余工具无任何网络依赖 |
| 校园内分发 | 单 APK（不用 Dynamic Feature / Asset Pack，离线设备下载不了） |
| 学生设备 | 零数据采集，无广告，无账号体系，最小权限（理想状态是零危险权限） |
| 平板形态 | **锁定横屏**（`sensorLandscape`，支持左右两个横向姿态）、10–13 英寸、常分屏；布局仍自适应，分屏时自动降级为上下堆叠 |

### 1.3 非目标（V1 明确不做）

- 账号、云同步、多端漫游
- 在线题库、AI 问答、拍照搜题
- 作业提交、班级管理、教师端
- 手机端专门设计（布局可自适应，但不做针对性打磨）
- 应用内购买、广告、埋点统计

把非目标写进文档，是为了挡住「顺手加个云同步」这类需求——它会瞬间摧毁离线保证。

---

## 2. 技术选型

### 2.1 语言与 UI

| 项 | 选择 | 理由 |
|---|---|---|
| 语言 | Kotlin | 官方主推；协程 / Flow / 密封类对状态建模友好；与 Compose 同源 |
| UI | Jetpack Compose + Material 3 | 声明式，主题换色只需换 `ColorScheme`；`WindowSizeClass` 原生支持平板适配；无 XML 主题体系的历史包袱 |
| 异步 | Coroutines + Flow | 内容加载、词典查询天然是流式/可取消的 |
| 序列化 | kotlinx.serialization | 编译期生成，无反射，R8 友好 |

**为什么不用 Flutter / React Native**：本项目没有跨平台需求，却对中文字体排版（古诗词行距、标点避头尾）、本地 SQLite 全文检索、表达式绘图性能有硬要求。跨平台方案在这三点上都要额外打补丁，收益为负。

**为什么不用 WebView 承载工具**：主题无法与原生一致、冷启动慢、内存占用高、离线资源路径易碎。地理模块如果未来需要复杂矢量交互，再单独评估 MapLibre（Apache-2.0，可离线），但底图数据合规性需另行确认（见 §6.5）。

### 2.2 版本矩阵

| 项 | 取值 | 说明 |
|---|---|---|
| `compileSdk` | 35 | 用最新 SDK 编译，拿到新 API 与工具链 |
| `targetSdk` | 34 | 对齐 Android 14 行为变更；设备群验证后再升 35/36 |
| `minSdk` | **26**（Android 8.0） | 关键理由见下 |
| JDK | 17 | AGP 8.x 要求 |
| Kotlin | 2.x | K2 编译器 |
| AGP | 8.x | 配 Version Catalog |

**为什么是 minSdk 26 而不是 24 或 29**：

- **26 覆盖校园在役设备，同时避开 Android 7 的存储与后台限制差异**。注意这一条与全文检索无关：Android 系统 SQLite 只编译 FTS3/FTS4，不编译 FTS5，与版本号无关（实测见附录 C）。
- 26 覆盖了校园里几乎所有在役平板，同时避开了 Android 7 的存储与后台限制差异。
- 若最终确认校内在役设备全部 ≥ Android 10，可把 `minSdk` 提到 29，简化分区存储逻辑；这是唯一值得重估的参数。

### 2.3 依赖白名单原则

每个新依赖进门前问三个问题：会不会偷偷加 `INTERNET` 权限？有没有 native 体积税？许可证是否允许校园分发？

明确排除：Firebase、Crashlytics、任何广告/统计 SDK、任何需要联网初始化的 SDK。

---

## 3. 总体架构

三层 + 一个横切的注册表：

```
┌──────────────────────────────────────────────────────────┐
│ 表现层 Presentation                                       │
│  Compose Screen / ViewModel(StateFlow<UiState>) / 主题     │
├──────────────────────────────────────────────────────────┤
│ 领域层 Domain                                            │
│  ToolCatalog（注册表） · VisibilityResolver · HomeComposer │
│  纯 Kotlin，无 Android 依赖，可 JVM 单测                   │
├──────────────────────────────────────────────────────────┤
│ 数据层 Data                                              │
│  Repository · ContentSource(assets) · Room · DataStore    │
└──────────────────────────────────────────────────────────┘
```

依赖方向**严格单向向下**，`feature` 之间零依赖。跨工具复用一律下沉到 `core`。

### 3.1 关键决策：注册表是唯一真相源

主页、学科页、设置页、搜索、导航，**全部从 `ToolCatalog` 读取工具清单**，没有任何一处硬编码工具名。这带来三个直接好处：

- 新增工具 = 加一个模块 + 一行注册，其余零改动；
- 「隐藏/显示」天然生效，因为所有列表都过同一层可见性解析；
- 工具下线不会让旧导航栈崩溃（`ToolRoute` 解析失败时降级到占位页）。

### 3.2 状态管理约定

- 每个屏幕一个 `UiState`（`@Immutable` data class），由 `ViewModel` 用 `StateFlow` 暴露。
- 用户操作走 `sealed interface XxxEvent`，ViewModel 单入口 `onEvent()` 处理。
- 一次性事件（如「已保存」提示）用 `Channel`，不进 `UiState`。
- **不在 Composable 里做业务判断**，Composable 只负责把 state 画出来。

---

## 4. 模块划分

### 4.1 目录结构

```
SubjectToolbox/
├── app/                          # 壳：Application、MainActivity、主题装配、NavHost、Hilt 汇总
├── build-logic/convention/       # Gradle 约定插件（compose-feature / pure-kotlin / android-lib）
├── core/
│   ├── common/                   # 纯 Kotlin：Result、Dispatcher、文本工具
│   ├── model/                    # Subject / ToolId / ToolSpec 等纯模型（无 Android 依赖）
│   ├── designsystem/             # 颜色、字体、形状、通用组件（卡片/空状态/搜索框）
│   ├── ui/                       # 自适应布局、通用交互（列表-详情骨架、缩放容器）
│   ├── navigation/               # 路由契约、ToolHost、导航扩展函数
│   ├── toolregistry/             # 注册表 + 可见性解析 + 主页组装（纯逻辑，重点单测对象）
│   ├── datastore/                # AppSettings（Proto DataStore）
│   ├── database/                 # Room 用户库 + 预置内容库访问
│   └── content/                  # assets 读取、manifest 校验、内容版本
├── feature/
│   ├── home/                     # 主页：我的常用 / 学科分区 / 最近使用
│   ├── settings/                 # 设置：外观、显示管理、关于
│   ├── chinese-poetry/           # 语文 · 古诗词
│   ├── math-function/            # 数学 · 函数编辑器
│   ├── english-dictionary/       # 英语 · 词典
│   ├── chemistry-equation/       # 化学 · 方程式
│   └── geography-atlas/          # 地理 · 地图与图表
├── content/
│   ├── source/                   # 人可编辑的源数据（YAML / JSON / GeoJSON / CSV）
│   └── builder/                  # JVM CLI：校验 + 编译为 assets 与预置库
└── docs/
```

### 4.2 依赖规则

| 规则 | 校验方式 |
|---|---|
| `feature:*` 只能依赖 `core:*` | 约定插件限制 + Gradle 依赖分析插件 |
| `feature:*` 之间禁止互相依赖 | 同上；跨工具复用必须下沉到 `core` |
| `core:model` 不依赖任何其他模块 | 模块图检查 |
| `app` 依赖全部 feature（唯一装配点） | 人工约定 + 编译期自然约束 |
| `content:builder` 不参与 APK 打包 | 纯 JVM 模块 |

**为什么值得拆多模块**：一是把「工具之间不许耦合」从口头约定变成编译错误；二是增量编译只重编改动模块，十几个模块的项目收益明显；三是每个 feature 模块可以被单独交给一位同学开发而不冲突。

**不要过度拆分**：不为每个屏幕建模块，不建 `core:utils` 这种垃圾桶模块。模块数控制在 20 个以内。

---

## 5. 工具插件契约（扩展机制核心）

### 5.1 模型定义

```kotlin
// core/model
@JvmInline value class SubjectId(val value: String)
@JvmInline value class ToolId(val value: String)

@Immutable
data class Subject(
    val id: SubjectId,
    val titleRes: Int,
    val accentSeed: Long,          // 学科主色，用于卡片与分区标题
    val icon: ImageVector,
    val order: Int,                // 主页默认排序
)

@Immutable
data class ToolSpec(
    val id: ToolId,                // 稳定标识，永不复用、永不改名
    val subjectId: SubjectId,
    val titleRes: Int,
    val summaryRes: Int,
    val icon: ImageVector,
    val keywordsRes: Int,          // 主页搜索用关键词
    val layout: ToolLayout = ToolLayout.Standard,
    val defaultPinned: Boolean = false,
)

enum class ToolLayout { Standard, FullBleed, SplitCapable }
```

### 5.2 工具实现接口

```kotlin
// core/toolregistry
interface ToolProvider {
    val spec: ToolSpec
    @Composable fun Content(host: ToolHost)
}

interface ToolHost {                 // 工具能对外做的事，刻意保持极窄
    fun openTool(id: ToolId)
    fun close()
    val windowSizeClass: WindowSizeClass
}
```

`ToolHost` 窄是有意的：工具只能「打开另一个工具」和「关闭自己」，不能直接操作导航栈、不能改全局主题、不能读别人的数据。

### 5.3 注册方式

```kotlin
// feature/chinese-poetry/di/PoetryToolModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PoetryToolModule {
    @Binds @IntoSet
    abstract fun bind(provider: PoetryToolProvider): ToolProvider
}
```

Hilt 的 `@IntoSet` 天然完成「多模块向同一集合贡献元素」，编译期校验，无反射，R8 安全。比 `ServiceLoader` / 反射扫描更可靠——后者在开启资源压缩后是经典事故源。

### 5.4 注册表与校验

```kotlin
@Singleton
class ToolCatalog @Inject constructor(
    providers: Set<ToolProvider>,
    subjects: Set<SubjectProvider>,
) {
    val subjects: List<Subject> = subjects.map { it.subject }.sortedBy { it.order }
    val tools: Map<ToolId, ToolProvider> = providers.associateBy { it.spec.id }

    init {
        // 启动即校验，失败在 debug 直接崩溃、release 记录并剔除
        require(tools.size == providers.size) { "ToolId 重复" }
        tools.values.forEach {
            require(it.spec.subjectId in subjects.map(Subject::id).toSet()) {
                "工具 ${it.spec.id.value} 指向不存在的学科"
            }
        }
    }
}
```

启动时校验而非运行时报错，是为了让「两个人各自加了 id 为 `math.calc` 的工具」这种冲突在开发第一天就暴露。

### 5.5 导航：一个目的地服务所有工具

```kotlin
@Serializable data object HomeRoute
@Serializable data class SubjectRoute(val subjectId: String)
@Serializable data class ToolRoute(val toolId: String)
@Serializable data object SettingsRoute
@Serializable data object DisplayManagerRoute
```

```kotlin
composable<ToolRoute> { entry ->
    val id = ToolId(entry.toRoute<ToolRoute>().toolId)
    val provider = catalog.tools[id]
    if (provider == null) ToolUnavailableScreen()   // 工具已下线，友好降级
    else provider.Content(host)
}
```

**只有这一个工具目的地**。新增工具不需要碰 `NavHost`，这是「零改动扩展」的关键一环。

### 5.6 新增一个学科工具的标准动作

1. 新建 `feature:physics-lab` 模块（套用约定插件，只依赖 `core`）。
2. 实现 `ToolProvider`，给出 `ToolSpec` 与 `Content()`。
3. 写一行 `@Binds @IntoSet` 注册。
4. 在 `content/source/` 补数据，跑一次内容构建。
5. 完成。主页、学科页、设置页的显示管理、搜索会自动出现它。

若是**全新学科**（如物理），额外加一个 `SubjectProvider` 提供学科名、主色、图标、排序。

---

## 6. 各学科工具职责边界

每个工具都按同一模板定义：**做什么 / 不做什么 / 数据形态 / 关键技术 / 平板布局**。把「不做什么」写清楚，比写「做什么」更能防止范围蔓延。

### 6.1 语文 · 古诗词

| 项 | 内容 |
|---|---|
| 做什么 | 教材/册次选择 → 篇目目录 → 正文 + 注释 + 译文 + 赏析；背诵模式（逐句/逐段遮罩）；按标题、作者、首句检索 |
| 不做什么 | 不做整本教材全文检索、不做朗读音频（无预置音频时）、不做笔记云同步 |
| 数据 | 每册一个 JSON，含 `book → poem → {title, author, dynasty, lines[], notes[], translation, appreciation}` |
| 关键点 | 中文排版：正文 20sp、行高 1.8、标点避头尾；篇目切换用横向 Pager；目录与正文**左右双栏**（平板最大价值点），目录可收起把宽度让给正文 |
| 边界 | 纯浏览 + 轻交互，不含编辑；背诵遮罩状态只存内存，退出即清 |

### 6.2 数学 · 函数编辑器

| 项 | 内容 |
|---|---|
| 做什么 | 最多 6 条曲线同坐标系叠加，逐条配色 / 显隐 / 删除；显式 `y=f(x)` 按 x 采样，含等号的方程（圆、椭圆）用 marching squares 提等值线；键盘按光标插入；`x^2` 的指数渲染成 x 右上角虚线框里的小字；缩放/平移；参数滑块对所有曲线生效 |
| 不做什么 | V1 不做符号求导/积分、不做 3D、不做统计拟合（留接口不实现） |
| 数据 | 内置表达式仅作为示例；用户保存的函数存 Room |
| 关键技术 | ① 手写递归下降解析器（支持隐式乘法 `2x`、`sin`/`ln`/`sqrt`、`π`/`e`），产出不可变 AST；② 采样：每像素一列，检测到符号跳变且 `|Δy|` 超阈值时递归细分（最多 8 层），避免 `tan`/`1/x` 画出竖直假线；③ Compose `Canvas` 绘制，采样在 `Dispatchers.Default` 上做，视图只拿最终点列 |
| 平板布局 | 左：输入区 + 参数滑块；右：画布（占 65% 宽）。竖屏时上下堆叠 |
| 边界 | 纯本地计算，无任何网络；解析失败必须给出行列号级错误提示，不能只说「语法错误」 |

### 6.3 英语 · 词典

| 项 | 内容 |
|---|---|
| 做什么 | 英汉释义、音标、词形变化、例句；精确 → 前缀 → 模糊三级查询；历史记录；生词本（本地） |
| 不做什么 | 不做整句翻译、不做语音合成（可调用系统 TTS 但不内置音频）、不做在线更新、不做 AI 释义 |
| 数据 | 预置只读 SQLite，FTS4 建全文索引 |
| 关键性能 | 查询 p95 < 50ms；输入防抖 150ms；结果分页（每页 30 条） |
| 模糊匹配策略 | 精确匹配优先，其次 FTS4 前缀（`word*`），最后 `LIKE %..%` 兜底。不用 FTS5 的 `trigram`——Android 系统 SQLite 不编译 FTS5，且 trigram 还要求 SQLite ≥ 3.34 |
| 平板布局 | 左侧查询与结果列表，右侧词条详情（列表-详情范式） |
| 边界 | 只读数据源；生词本是唯一写入点，独立于内容库，内容升级不丢 |

### 6.4 化学 · 方程式

| 项 | 内容 |
|---|---|
| 做什么 | 按反应物/生成物/反应类型/条件检索方程式；配平校验；常见反应分类浏览；元素周期表与溶解性表（速查） |
| 不做什么 | V1 不做离子方程式与氧化还原配平、不做有机合成路线、不做结构式绘制（可另立工具） |
| 数据 | 预置 SQLite：`equation(id, reactants, products, condition, type)` + 元素倒排表 |
| 关键技术 | ① 化学式解析（`Ca(OH)2` → 元素计数）；② 配平：按元素守恒列线性方程组，高斯消元求零空间，取最小正整数解；无解时明确提示「该反应无法配平」 |
| 平板布局 | 左侧筛选条件面板，右侧结果卡片列表，方程式用等宽排版对齐 |
| 边界 | 检索 + 校验，不做反应预测；数据准确率由内容审核流程保证，代码不做化学推断 |

### 6.5 地理 · 地图与图表

| 项 | 内容 |
|---|---|
| 做什么 | 中国/世界政区、地形、气候、洋流、板块、人口与产业等专题图浏览；图层切换；缩放平移；气候直方图、人口金字塔等图表交互 |
| 不做什么 | **不做在线地图、不做定位、不做导航、不做路径规划、不做实时数据** |
| 数据 | 标准地图栅格（2x/3x）+ GeoJSON 专题图层 + 图表数据 JSON |
| 关键技术 | 栅格底图 + 矢量叠加层；`graphicsLayer` 做缩放平移；区域点选靠多边形命中测试；图表用 Compose `Canvas` 自绘（不引图表库，减少体积与主题冲突） |

**⚠️ 合规红线（必须前置处理，不是上线前再说）**

本应用完全离线且不申请网络权限，因此**不能使用任何在线底图 SDK**（腾讯、高德、百度、天地图均需联网与密钥）。这意味着地理模块的底图只能来自：

1. **自然资源部标准地图服务**（`bzdt.ch.mnr.gov.cn`）发布的静态成果，**必须标注审图号**；
2. 基于标准地图自绘的专题图，不得改动国界线、不得改变领土表示。

禁止事项：使用 Google Maps / OpenStreetMap / Mapbox / 境外 Bing 等无资质地图源；自行绘制或从非权威渠道获取国界数据；对标准地图进行裁切后遗漏南海诸岛、台湾、藏南等要素。

审图号、数据来源、制图单位需在「关于」页与应用内图例中明示。这一项建议在开发早期就提交学校/教研组审核，避免数据做完再返工。

### 6.6 边界总表

| 工具 | 写数据 | 读数据 | 依赖其他工具 |
|---|---|---|---|
| 古诗词 | 无 | poems JSON | 否 |
| 函数编辑器 | Room（用户函数） | 无 | 否 |
| 词典 | Room（历史/生词本） | 预置 SQLite | 否 |
| 化学方程式 | 无（可收藏到 Room） | 预置 SQLite | 否 |
| 地理 | 无 | 栅格 + GeoJSON + JSON | 否 |

**零跨工具依赖**是刻意设计的：它让每个工具可以独立开发、独立测试、独立下线。

---

## 7. 离线数据与存储方案

### 7.1 四层存储

| 层 | 载体 | 内容 | 生命周期 |
|---|---|---|---|
| L1 只读资源 | `assets/content/` | JSON、GeoJSON、矢量图、图表数据 | 随 APK，升级即换 |
| L2 预置数据库 | `assets/db/*.db` → 复制到 `noBackupFilesDir` | 词典、化学方程式 | 随 APK，首启复制 |
| L3 用户数据库 | Room（`user.db`） | 收藏、生词本、历史、保存的函数、使用记录 | 用户数据，永不被内容升级覆盖 |
| L4 配置 | Proto DataStore（`settings.pb`） | 主题、置顶、显隐 | 用户数据 |

**L2 与 L3 物理分离**是核心原则：内容可以整包替换，用户数据一个字节都不碰。

### 7.2 内容清单

`assets/content/manifest.json` 是内容的唯一入口：

```json
{
  "contentVersion": 7,
  "builtAt": "2026-11-01T08:00:00+08:00",
  "datasets": [
    { "id": "chinese.poetry", "version": 3, "path": "content/chinese/poems.json",
      "sha256": "…", "bytes": 1834221 },
    { "id": "english.dict", "version": 5, "path": "db/dict.db",
      "sha256": "…", "bytes": 41231360 }
  ]
}
```

启动时校验 `sha256` 与 `bytes`；不匹配就走「内容损坏」降级路径（提示重新安装或导入内容包），而不是让用户看到半截数据。

### 7.3 各数据集形态与理由

| 数据集 | 形态 | 为什么 |
|---|---|---|
| 古诗词 | 按册 JSON | 单册 < 200KB，可整册进内存，切换册次只换一个对象；无需 SQL |
| 词典 | 预置 SQLite + FTS4 | 十万级词条必须走索引；FTS4 的前缀检索远好于应用层逐条过滤 |
| 化学方程式 | 预置 SQLite | 多条件组合查询（元素 + 类型 + 条件），SQL 表达力最合适 |
| 地理 | 栅格图 + GeoJSON | 底图只需显示；专题图层需要点选，用矢量多边形 |

### 7.4 内容构建管线

内容不许手改 `assets`，必须走管线：

```
content/source/            content/builder (JVM CLI)          app/src/main/assets/
  poems/*.yaml      ──▶   ① Schema 校验                      ──▶  content/…
  dict/source.csv         ② 引用完整性检查（书→篇→注）             db/dict.db
  chem/equations.csv       ③ 生成索引与邻域表
  geo/*.geojson            ④ 计算 sha256、写 manifest
                           ⑤ 体积报告
```

收益：内容可被非开发者维护（YAML 比 JSON 好写）、错误在 CI 就暴露、每次构建产出的哈希可追溯。管线本身也跑单测——用一个「故意写错的语料」验证校验器能拦住。

### 7.5 首启与升级

- **首启**：读取 manifest → 校验 → 后台线程把 `dict.db`、`chem.db` 复制到 `noBackupFilesDir/content/{version}/`（临时文件 + 原子 rename），期间主页可用，进入相关工具时才等待。避免启动白屏。
- **APK 升级**：`contentVersion` 变化才重新复制；旧版本目录在下次启动时清理。
- **滚动更新保护**：复制失败保留旧目录，回退到可用版本，不阻断整个 App。

### 7.6 体积预算

| 项 | 预算 |
|---|---|
| 代码（含 Compose） | ~12 MB |
| 词典库 | 25–40 MB |
| 古诗词 | 3–5 MB |
| 化学 | 2–4 MB |
| 地理资源 | 8–20 MB（取决于专题图数量与分辨率） |
| 合计 APK | **目标 ≤ 80 MB** |

超预算时的优先级：地理专题图降分辨率 > 词典裁剪生僻词 > 古诗词合并册次。**不要**用 Dynamic Feature 解决——离线设备装不了。

### 7.7 内容包导入（V2 可选）

无网络环境下，内容更新的唯一通道是重装 APK。若希望「换教材版本不用发版」，可加 `.stbpack` 机制：ZIP 内含 manifest + 数据 + Ed25519 签名，通过 SAF 从本地文件导入，用内置公钥验签，验签通过则原子替换 `content/{version}`。这是可选能力，V1 不做。

---

## 8. 个性化配置管理

### 8.1 数据模型

```protobuf
message AppSettings {
  ThemePref      theme      = 1;
  HomePref       home       = 2;
  VisibilityPref visibility = 3;
  int32          schemaVersion = 4;
}

message ThemePref {
  int64  seedArgb          = 1;   // 用户选的主题色
  DarkMode dark            = 2;   // FOLLOW_SYSTEM / LIGHT / DARK
  float  fontScale         = 3;   // 1.0 / 1.15 / 1.3 / 1.5
  bool   followSystemColor = 4;   // 是否走 Material You 取色（默认 false）
}

message HomePref {
  repeated string pinnedToolIds = 1;   // 顺序即用户排序
  bool            showRecents   = 2;
  int32           gridColumns   = 3;   // 0 = 自适应
}

message VisibilityPref {
  repeated string hiddenSubjectIds = 1;
  repeated string hiddenToolIds    = 2;
}
```

用 Proto 而非 Preferences 的理由：`repeated` 天然保序（置顶顺序就是用户拖拽的结果），字段增删有编号保证向后兼容，不需要自己拼 JSON 字符串。

### 8.2 解析链路

配置不直接驱动 UI，中间隔一层纯函数：

```
ToolCatalog（全部工具）
   │
   ├─ VisibilityResolver：剔除隐藏学科下的工具 + 显式隐藏的工具
   │
   ▼  可见工具集
   │
   ├─ HomeComposer：按 pinned 顺序取置顶 → 按 subject 分组 → 取最近使用
   │
   ▼
HomeUiState
```

这一层全是纯 Kotlin 函数，输入 `(catalog, settings, usage)`，输出 `HomeUiState`，可以用 JVM 单测穷举各种组合，不需要跑模拟器。

### 8.3 语义规则（必须写死，否则行为会前后矛盾）

| 场景 | 规则 |
|---|---|
| 隐藏学科 | 该学科下所有工具在主页、学科页、搜索中不出现；**不修改** `hiddenToolIds`，恢复学科后原有单项隐藏仍生效 |
| 隐藏 vs 置顶 | 隐藏优先。某置顶工具被隐藏后不显示，并在设置页给出提示「1 个置顶工具当前已隐藏」 |
| 工具下线 | 存储中的未知 id **保留不删**，渲染时忽略；工具回归后自动恢复原位置 |
| 全部隐藏 | 主页显示引导页 + 「恢复默认显示」按钮，防止用户把自己锁死 |
| 默认值 | 首次启动 `pinnedToolIds` 取各工具 `defaultPinned`，而非空（让新用户不至于面对空白主页） |

「保留未知 id」是容易被忽略但很关键的一条：否则一次工具重命名就会清空用户多年的置顶习惯。

### 8.4 写入与并发

- 所有写入走 `settingsRepository.update { it.copy(...) }`，DataStore 保证原子性与串行化。
- 一次用户操作 = 一次事务。拖动排序时不要每帧写一次，等 `onDragEnd` 再提交。
- 读取侧 `distinctUntilChanged()` + `stateIn(WhileSubscribed(5s))`，避免配置抖动导致整页重组。

### 8.5 导入导出（V2）

把 `AppSettings` 序列化为 JSON，经 SAF 导出/导入。用途：机房统一配置分发、换机迁移、故障排查。导出文件不含任何个人信息——本来就一个都没有。

---

## 9. UI 设计

### 9.1 信息架构

```
主页
├── 我的常用（置顶，横向或首屏网格）
├── 学科分区（语文 / 数学 / 英语 / 化学 / 地理）
│     └── 工具卡片 → 工具页
├── 最近使用（可关）
└── 设置入口
      ├── 外观（主题色 / 深色 / 字号）
      ├── 显示管理（学科与工具开关）
      └── 关于（版本 / 内容版本 / 审图号 / 开源许可）
```

只有三层：主页 → 工具，主页 → 设置。学科是**分区**而不是必须经过的一层页面——但在窄屏或工具较多的学科里，点学科标题可进入学科详情页（列表-详情）。

### 9.2 平板适配

| 宽度 | 断点 | 布局 |
|---|---|---|
| < 600dp | Compact | 底部导航 + 单列卡片（兼容小屏，非重点） |
| 600–840dp | Medium | 导航栏收窄为 rail，两列网格 |
| ≥ 840dp | Expanded | 左侧 `NavigationRail` 常驻，主页 3–4 列网格，工具页默认双栏 |

用 `WindowSizeClass` + `GridCells.Adaptive(minSize = 200.dp)`，不写死列数。分屏（多窗口）时窗口变窄会自动降级，不需要单独适配。

### 9.3 每个工具页的布局范式

不要做「手机页面拉宽」，每个工具应选一种平板范式：

| 工具 | 范式 |
|---|---|
| 古诗词 | 列表-详情：左目录（可收起），右正文 |
| 词典 | 列表-详情：左结果，右词条 |
| 函数编辑器 | 画布 + 控制面板：右画布 65%，左输入与滑块 |
| 化学方程式 | 筛选面板 + 结果流：左条件，右卡片 |
| 电子教材 | 内嵌 WebView 全屏浏览人教社官网，顶栏返回键优先网页后退 |

### 9.4 设计令牌

| 项 | 值 |
|---|---|
| 间距 | 4dp 基准，常用 8 / 12 / 16 / 24 / 32 |
| 圆角 | 卡片 16dp，按钮 12dp，输入框 12dp |
| 触控目标 | ≥ 48dp |
| 正文 | 16sp / 行高 1.6 |
| 诗词正文 | 20sp / 行高 1.8 |
| 标题 | 页面 22sp，区块 18sp |
| 学科色 | 语文赭红 / 数学靛蓝 / 英语青绿 / 化学紫 / 地理墨绿（作为卡片强调色，不覆盖主色） |

### 9.5 主题系统

```kotlin
@Composable
fun SubjectToolboxTheme(pref: ThemePref, content: @Composable () -> Unit) {
    val scheme = remember(pref.seedArgb, pref.darkMode, pref.followSystemColor) {
        when {
            pref.followSystemColor -> dynamicDark/LightColorScheme(...)   // Android 12+
            else -> ColorScheme.fromSeed(Color(pref.seedArgb))
        }
    }
    MaterialTheme(colorScheme = scheme, typography = tabletTypography(pref.fontScale), content = content)
}
```

要点：

- **默认关闭**「跟随系统取色」。需求是「用户能改主题色」，Material You 会让用户选的颜色失效，两者冲突，必须以用户选择为准。
- 提供 8–12 个预设色 + 自定义取色；预设覆盖冷暖与低饱和，避免学生选到刺眼的荧光色。
- 深色模式单独可选（跟随系统 / 强制浅色 / 强制深色）。
- 学科色只用于图标底色、分区标题点缀，**不参与** `primary` 计算，否则换主题色时学科识别度会被破坏。
- 换主题只重建 `ColorScheme`，不重建导航栈，切换必须无闪烁。

### 9.6 可访问性

字号三档（1.0/1.15/1.3/1.5），跟随系统字体缩放并做上限保护；所有图标按钮有 `contentDescription`；正文对比度 ≥ 4.5:1；不使用纯颜色传达状态（配图标或文字）。

---

## 10. 导航与状态恢复

- 单 Activity + Compose Navigation，类型安全路由（`@Serializable`）。
- 工具内部状态用 `rememberSaveable` 保存，进程被杀后可恢复。
- `ViewModel` 作用域绑定 `NavBackStackEntry`，离开工具页即释放，避免多个工具同时持有大对象（词典结果、地图位图）。
- 返回栈语义：工具页返回 → 主页（或学科页），不保留工具内部的多级栈给系统返回键——由工具自己用 `BackHandler` 处理内部层级。

---

## 11. 质量保障

### 11.1 测试矩阵

| 层 | 手段 | 重点 |
|---|---|---|
| 领域层 | JVM 单测 | 可见性解析、主页组装、置顶顺序、未知 id 容错 |
| 算法 | 属性测试 | 表达式解析 round-trip；配平器对 200 条已知方程式的正确率 |
| 数据 | 内容校验单测 | Schema、引用完整性、哈希、体积预算 |
| UI | Compose UI Test | 平板 qualifier 下跑主页、设置、显隐开关 |
| 视觉 | 截图测试 | 3 种主题色 × 浅/深色 × 三种宽度 |

### 11.2 性能指标

| 指标 | 目标 |
|---|---|
| 冷启动到主页可交互 | < 1.5s（中端平板） |
| 主页滚动 | 稳定 60fps |
| 词典查询 p95 | < 50ms |
| 函数重绘（2000 采样点） | 单帧 < 16ms |
| 首启内容准备 | 不阻塞主页；进入工具前完成 |

手段：Baseline Profile、`assets` 延迟读取、大图按密度提供、`LazyColumn` 稳定 key。

### 11.3 CI 检查

1. 模块依赖规则（禁止 feature 互依赖）。
2. 合并后 Manifest 中 `INTERNET` 权限仅允许「电子教材」使用——若其他工具也要联网，先回答为什么。
3. 内容管线校验 + 体积报告。
4. 单元测试 + 截图测试。

---

## 12. 迭代路线

| 阶段 | 内容 | 目的 |
|---|---|---|
| P0 骨架 | core 全套 + 主页 + 设置 + 一个示例工具（古诗词最简版） | 打通注册表、主题、显隐链路 |
| P1 内容型 | 古诗词完整 + 词典 | 验证内容管线与预置库复制 |
| P2 计算型 | 函数编辑器 + 化学方程式 | 验证算法模块与画布性能 |
| P3 地理 | 地图与图表 + **合规审核并行** | 资产最重、外部依赖最多，单独排期 |
| P4 打磨 | 个性化完善、导入导出、无障碍、性能 | 交付 |

顺序原则：先打通「插件机制」再堆内容。P0 结束时若新增一个工具仍需改三处代码，说明抽象错了，此时返工成本最低。

---

## 13. 主要风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 地图合规 | 无法过审、必须返工 | P0 阶段就确定标准地图清单与审图号；不做在线地图；早期提交教研组审核 |
| 词库版权 | 不能分发 | 只用明确许可的词库（如 ECDICT、Wiktionary 衍生数据），逐一核对条款并在「关于」页署名 |
| 古诗文版本差异 | 内容争议 | 明确以人教版/统编版某一版为准，标注版本号；错字走内容管线修正，不靠热修 |
| 配平算法边界 | 化学老师不信任 | V1 明确只承诺分子方程式；对无解/多解给出可解释提示，不硬凑答案 |
| 平板碎片化 | 布局错乱 | 只用 `WindowSizeClass` + 自适应网格；在 3 种分辨率与横竖屏下跑截图测试 |
| APK 体积失控 | 安装困难 | 内容管线输出体积报告，超预算即 CI 失败 |
| 抽象过度 | 开发变慢 | P0 只做一个工具验证契约，跑通后再复制 |

---

## 附录 A：新增工具的检查清单

- [ ] 新模块只依赖 `core:*`
- [ ] `ToolId` 全局唯一、语义稳定（`subject.tool` 命名）
- [ ] 提供 `ToolSpec`（标题、摘要、图标、关键词）
- [ ] 实现 `Content(host)`，内部状态可恢复
- [ ] `@Binds @IntoSet` 注册
- [ ] 内容走管线，不手改 assets
- [ ] 补领域层单测 + 一张截图基线
- [ ] 确认 `INTERNET` 权限只被「电子教材」使用

## 附录 B：核心接口一览

| 接口 | 所在模块 | 职责 |
|---|---|---|
| `SubjectProvider` | `core:model` | 贡献一个学科 |
| `ToolProvider` | `core:toolregistry` | 贡献一个工具（元数据 + UI 入口） |
| `ToolCatalog` | `core:toolregistry` | 汇总、校验、按 id 查找 |
| `VisibilityResolver` | `core:toolregistry` | 应用显隐配置 |
| `HomeComposer` | `core:toolregistry` | 组装主页状态 |
| `SettingsRepository` | `core:datastore` | 配置读写与迁移 |
| `ContentRepository` | `core:content` | manifest、资源读取、完整性校验 |
| `UsageRepository` | `core:database` | 最近使用、打开频次 |

---

## 附录 C：落地实现与本文档的差异

实现过程中对三个技术点做了降级替换，理由都是「减少构建风险与运行时不稳定性」，接口契约未变，日后可平滑换回：

| 文档原方案 | 实际实现 | 原因 | 换回成本 |
|---|---|---|---|
| Hilt `@IntoSet` 注册 | `app/ToolRegistrations.kt` 里的显式列表 | 省掉 KSP 注解处理，首次构建快约 40s；工具数量是个位数量级，编译期列表足够 | 低：每个 Provider 加一个 `@Binds @IntoSet` 即可，`ToolProvider` 契约不变 |
| Room | `SQLiteOpenHelper`（`usage.db`） | 用户数据只有「最近使用」与「生词本」两张小表，Room 的收益不抵 KSP 的构建开销 | 低：DAO 接口已按仓库模式隔离 |
| 词库运行时建库 | 构建期预生成 `dict.db`，首启从 assets 复制 | 77 万条首启灌库要几秒到几十秒，学生第一次打开就要等；预生成后首启只是一次文件复制（全量 86MB，约 1s）。覆盖前会把旧库生词本读出、复制后写回 | 低 |
| 词库：JSON + FTS4 | **ECDICT 全量派生预置 SQLite，不做全文索引** | Android 系统 SQLite 根本没编译 FTS5；FTS4 又把 77MB 的数据撑到 131MB。最终用 `word COLLATE NOCASE` + 索引做精确/前缀范围扫描，中文反查走 LIKE 全表（IO 线程 + 300ms 输入防抖）。早期曾按考试标签/词频/柯林斯/牛津核心只收 5.9 万条精选词，但短语几乎都没有这些标记，结果只剩 887 条短语，故改为全量收录 77 万条（含 36.6 万短语）；词频与标签保留，只用于结果排序 | 低：`DictionaryRepository` + `tools/dictionary/build_dict_db.py` |
| 只画 `y=f(x)` | 增加隐式方程（marching squares） | 圆、椭圆「一个 x 对应两个 y」，逐点采样根本画不出来。改为在网格上求 `F(x,y)=0` 的等值线，180×180 网格够光滑；有隐函数时纵轴按绘图区宽高比跟随横轴，否则圆会被压成椭圆 | 低：`composePlot` 内按 `CurveDef` 分支 |
| 输入框直接显示 `^` | 用 `VisualTransformation` 渲染上标 + 虚线框 | 学生看到 `x²` 未必知道那是上标。存储仍是 `x^2`（解析器不需要懂排版），渲染层把 `^指数` 缩小抬高，输入框再按 span 位置画虚线框；`^` 后面空着时留一个空槽，括号组 `x^(n+1)` 也整体抬高，光标坐标由 `OffsetMapping` 维持 | 低：两个文件 `ExpressionDisplay.kt` + `MathScreen.kt` |

另外两处调整：

- **主题配色**用 HSL 手算而非 material-color-utilities，保证同一 seed 在任意系统版本得到完全一致的配色，也避免引入额外依赖。
- **地理模块只做统计图表**。完全离线 + 不申请网络权限，意味着任何在线底图 SDK 都不可用；离线底图必须取自然资源部标准地图并标注审图号。与其画一张边界不准的示意图，不如把地图位留空并写明接入条件。

