# 学科工具箱 SubjectToolbox

> 作者：2025届2班 胡城玮

面向高中校园的安卓平板应用，把语文、数学、英语、化学、生物等学科的工具收进一个 App。
除「电子教材」需联网访问人教社官网外，**其余功能完全离线可用**。

![platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![compose](https://img.shields.io/badge/Compose-Material%203-4285F4)

***

## 已实现

| 学科 | 工具    | 能力                                                                                          |
| -- | ----- | ------------------------------------------------------------------------------------------- |
| 语文 | 古诗词   | **61 篇新课标必背**（含沁园春·长沙）、按册选择、目录-正文双栏（目录可收起）、注释与赏析、**背诵模式**（点句显示）                             |
| 数学 | 函数编辑器 | **最多 6 条曲线同坐标系**、**圆/椭圆等隐式方程**（`x^2+y^2=25`）、上标输入提示（输入指数时 x 右上角显示虚线框）、键盘按光标插入、逐条配色与显隐、缩放平移、共享参数滑块 |
| 英语 | 词典    | **ECDICT 77 万词条（含 36.6 万短语）**、精确/前缀/中文反查、音标·考试标签·词形变化·英文释义、**本地生词本**  |
| 化学 | 化学方程式 | **165 条方程式**、类型筛选检索、化学式下标排版、**自动配平**（高斯消元）+ 一键复制、**118 元素周期表**（可点查详情）                       |
| 生物 | 交互示意图 | 索引页可搜索，**细胞分裂交互图**：有丝分裂 / 减数分裂对照、动物 / 植物细胞切换、阶段讲解、染色体与 DNA 数目统计、交叉互换                    |
| 地理 | 交互示意图 | 索引页可搜索，**地球自转交互图**：自转方向与地轴倾角、昼夜半球与太阳光线、角速度 / 线速度随纬度变化、标准 / 俯视 / 侧视切换                        |

**个性化**：深浅色、三档字号、工具置顶、学科与工具显隐管理、最近使用（默认关闭）。
设置页首屏即「关于」：软件名称、简介、作者署名与版本信息一眼可见。

**主页**：我的常用 → 学科分区 → 最近使用，全部由注册表 + 配置实时计算。

***

## 技术栈

| 维度   | 选择                                                      |
| ---- | ------------------------------------------------------- |
| 语言   | Kotlin 2.0                                              |
| UI   | Jetpack Compose + Material 3，`GridCells.Adaptive` 平板自适应 |
| 架构   | 多模块 + 单向数据流（MVVM）+ 工具注册表（Tool Registry）                 |
| 配置   | Preferences DataStore                                   |
| 用户数据 | SQLite（`usage.db`）+ 预置词库（`dict.db`，ECDICT 派生）           |
| 内容   | `assets/content/*.json`                                 |
| 构建   | Gradle 8.9 + AGP 8.7.3 + JDK 17                         |
| 版本   | compileSdk 35 / targetSdk 34 / **minSdk 26**            |

***

## 构建

前置：JDK 17+、Android SDK（platform 35、build-tools 35.0.0）。在 `local.properties` 写：

```properties
sdk.dir=你的SDK路径
```

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（56MB，含调试符号与压缩后约 40MB 的词库）

./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（约 40MB，已签名）
```

release 已开启 R8 代码压缩 + 资源收缩；当前包体积主体是词库（86.1MB SQLite，压缩后约 38MB）。
签名信息放在 `local.properties`（`stb.storeFile` / `stb.storePassword` / `stb.keyAlias` / `stb.keyPassword`），
密钥文件为 `keystore/subjecttoolbox-release.jks`；两者都已 gitignore，换机器时必须一起搬走，
否则新包无法覆盖安装。`dist/` 同目录保留可安装的 debug 包与已签名 release 包。

`minSdk 26` 是权衡结果：覆盖校园在役设备，同时避开 Android 7 的存储与后台限制差异。
注意它**和全文检索无关**——Android 系统 SQLite 只编译了 FTS3/FTS4，没有 FTS5，换版本号也换不来（在 API 35 上实测 `CREATE VIRTUAL TABLE ... USING fts5` 直接报 `no such module: fts5`）。词典因此干脆不用全文索引：`word` 列声明 `COLLATE NOCASE`，精确/前缀查询走 `idx_entry_word` 索引，中文反查走 LIKE 全表扫描（放在 IO 线程 + 300ms 输入防抖）。不做 FTS4 是因为全量 77 万条下它会让词库体积再涨近一倍。

**屏幕方向**：`sensorLandscape` 锁定横屏（支持左右两个横向姿态），符合平板在教室里的实际握持方式。
布局仍按 `BoxWithConstraints` 自适应，分屏或小窗口下会自动降级为上下堆叠。

***

## 模块结构

```
app/                      壳层：Application、MainActivity、NavHost、装配
core/model/               纯 Kotlin 模型与仓库契约（无 Android 依赖）
core/registry/            工具注册表、可见性解析、主页组装（纯逻辑）
core/designsystem/        主题推导、通用组件、交互图索引页与 WebView 容器
core/data/                DataStore 配置、使用记录、assets 读取
feature/home/             主页
feature/settings/         设置 + 显示管理
feature/chinese-poetry/   语文·古诗词
feature/math-function/    数学·函数编辑器
feature/english-dictionary/ 英语·词典
feature/chemistry-equation/ 化学·方程式
feature/biology-diagram/  生物·交互示意图
feature/geography-diagram/ 地理·交互示意图
```

依赖规则：`feature → core`，**feature 之间零依赖**。跨工具复用必须下沉到 core。

***

## 新增一个学科工具要改什么

只有三处，且都不涉及主页、设置或导航：

1. 新建 `feature:xxx` 模块，只依赖 `core`；
2. 实现 `ToolProvider`（给出 `ToolSpec` 与 `Content()`）；
3. 在 `app/.../ToolRegistrations.kt` 的 `tools` 列表里加一行。

主页、显示管理、搜索、导航会自动接纳它——因为它们读的是同一份 `ToolCatalog`。

***

## 替换内容

内容与代码分离，改教材版本不必改代码：

```
app/src/main/assets/content/
├── manifest.json            内容版本清单
├── chinese/poems.json       古诗词（按册组织）
├── english/dict.db          预置词库（ECDICT 全量派生，86.1MB，首启复制到 databases/）
├── chemistry/equations.json 方程式（165 条，构建期逐条校验配平）
├── chemistry/elements.json  118 元素数据
├── biology/cell-division.html 细胞分裂交互图
└── geography/earth-rotation.html 地球自转交互图
```

当前内置：**61 篇古诗文**（新课标必背 60 篇全部收录 + 沁园春·长沙，按统编版六册归类，含注释与赏析）、**ECDICT 77 万词条（含 36.6 万条短语，86.1MB SQLite，APK 内压缩约 40MB）**、**165 条方程式**（构建期逐条校验配平）、**118 元素周期表**；「电子教材」在线访问人教社官网查看各科电子课本。
正式部署时替换为教研组校对/授权的内容即可，接口不变。

词库由脚本生成，不要手改 assets：

```bash
# 数据源：https://github.com/skywind3000/ECDICT 的 ecdict.csv（MIT License）
python tools/dictionary/build_dict_db.py --source H:/tools/ecdict/ecdict.csv
python tools/dictionary/verify_dict_db.py
```

收录规则是**全量**：只排除空词 / 超 60 字符 / 大小写重复 / 完全无释义的词，
不再按考试标签、词频、柯林斯星级筛「精选词」——短语几乎都没有这些标记，
早期 5.9 万条版本只剩 887 条短语，`look forward to`、`take part in` 这类常用短语全查不到。
词频和考试标签仍然入库，只用于把常用词排在专业术语前面。
词库版本变化时应用会重新复制 assets，并把旧库里的生词本自动迁移过来。

***

## 两个刻意的取舍

**不申请** **`INTERNET`** **权限。** 这是「离线」唯一可验证的兑现方式——比在隐私政策里写一句承诺有用得多。代价是任何需要联网的 SDK 都进不来，包括崩溃上报。

**地理模块不画地图，只做示意交互图。** 完全离线意味着腾讯/高德/百度/天地图的在线底图全部不可用；离线底图只能取自然资源部标准地图的静态成果并标注审图号，OSM/Mapbox/Google 等无资质数据源不得使用。目前地理只提供不涉及边界数据的地球自转示意图，底图接入位留空，等校方确定合规底图后再填。

***

## 已知限制

- 数据为示例规模，未做教材全量校对；
- 化学配平只处理分子方程式，不含离子方程式与氧化还原半反应；
- 未接入音频朗读、云同步、账号体系（设计上的非目标）；
- release 已开 R8 + 资源收缩，使用项目内自签名密钥（`keystore/`）；上架前需补 Baseline Profile 并做一次真机回归；

