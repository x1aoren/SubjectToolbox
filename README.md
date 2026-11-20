# 学科工具箱 SubjectToolbox

> 作者：2025届2班 胡城玮

面向高中校园的安卓平板应用，把语文、数学、英语、化学、生物、地理和通用工具收进一个 App。
离线功能照常可用；只有「电子教材」必须联网访问人教社官网，遥测、云端开关与意见反馈是可选的联网能力（可在设置里关闭）。

![platform](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![compose](https://img.shields.io/badge/Compose-Material%203-4285F4)

***

## 已实现

| 学科 | 工具    | 能力                                                                                          |
| -- | ----- | ------------------------------------------------------------------------------------------- |
| 语文 | 古诗词   | **61 篇新课标必背**（含沁园春·长沙）、按册选择、目录-正文双栏（目录可收起）、注释与赏析、**背诵模式**（点句显示）                             |
| 语文 | 汉语词典  | **现代汉语词典第 7 版 7 万词条**、精确/前缀/拼音/释义检索、繁体与异体写法跳转、拼音与词性排版、**本地生词本**                          |
| 数学 | 函数编辑器 | **最多 6 条曲线同坐标系**、**显式 / 隐式方程手动切换**（圆、椭圆等如 `x^2+y^2=25`）、**每条曲线可单独设置定义域**、参数滑块可增删并设范围与步进、**开方所见即所得**、上标输入提示（输入指数时 x 右上角显示虚线框）、键盘按光标插入、报错栏固定占位、逐条配色与显隐、缩放平移 |
| 英语 | 词典    | **ECDICT 77 万词条（含 36.6 万短语）**、精确/前缀/中文反查、音标·考试标签·词形变化·英文释义、**本地生词本**  |
| 英语 | 语法    | **薄冰大学英语语法 49 篇**、24 类目录、标题 / 表格 / 例句**原生 Compose 排版**（非内嵌网页）、全文检索与命中高亮 |
| 化学 | 化学方程式 | **508 条方程式**、类型筛选检索、化学式下标排版、反应条件标在箭头 / 等号上方、**自动配平**（高斯消元）+ 一键复制                       |
| 化学 | 元素周期表 | **118 种元素**、18 列标准排布与镧系 / 锕系单列、分类配色、点按查看原子序数·相对原子质量·熔沸点·电子排布                       |
| 生物 | 交互示意图 | 索引页可搜索，**细胞分裂交互图**：有丝分裂 / 减数分裂对照、动物 / 植物细胞切换、阶段讲解、染色体与 DNA 数目统计、交叉互换                    |
| 地理 | 交互示意图 | 索引页可搜索，**地球自转交互图**：自转方向与地轴倾角、昼夜半球与太阳光线、角速度 / 线速度随纬度变化、标准 / 俯视 / 侧视切换                        |
| 通用 | 公式大全 | **281 条数理化公式**（数学 143 · 物理 99 · 化学 39）、按学科分栏并分初中 / 高中、**KaTeX 排版**、名称与公式符号搜索、变量说明 / 适用条件 / 备注          |
| 通用 | 电子教材 | 内嵌人教社电子课本官网、**60%–300% 页面缩放**、网页历史返回，**需联网**                                            |
| 通用 | 意见反馈 | 主页右上角独立入口；提交问题 / 建议 / 内容纠错，按设备标识查看公开回复；联网时发送，同一设备每天最多 5 条 |
| 通用 | 云端同步 | 设备标识（SN）、工具开关（含单设备停用与停用原因；停用不隐藏）、公告、版本提示、14 项受管控条目清单上报；无网络不发请求 |

**个性化**：深浅色、四档字号（0.85× 较小 / 1× 标准 / 1.15× 较大 / 1.3× 特大）、工具置顶、学科与工具显隐管理、最近使用（默认关闭）。
设置页首屏即「关于」：软件名称、简介、作者署名、版本信息与「更新日志」入口一眼可见。
**设置 → 关于 → 更新日志**可查看各版本更新内容；版本升级后首次启动会提示本次更新（每个版本只提示一次，全新安装不打扰）。

**主页**：我的常用 → 学科分区 → 最近使用，全部由注册表 + 配置实时计算。

***

## 技术栈

| 维度   | 选择                                                      |
| ---- | ------------------------------------------------------- |
| 语言   | Kotlin 2.0                                              |
| UI   | Jetpack Compose + Material 3，`GridCells.Adaptive` 平板自适应 |
| 架构   | 多模块 + 单向数据流（MVVM）+ 工具注册表（Tool Registry）                 |
| 配置   | Preferences DataStore                                   |
| 用户数据 | SQLite（`usage.db`）+ 预置词库（英汉 `dict.db`、汉语 `zhdict.db`）           |
| 内容   | `assets/content/`（JSON / SQLite 词库 / 交互页）                |
| 云端   | 可选遥测 / 云端配置 / 意见反馈；`core:data/cloud`，HTTP(S) + JSON，无第三方网络库 |
| 构建   | Gradle 8.9 + AGP 8.7.3 + JDK 17                         |
| 版本   | compileSdk 35 / targetSdk 34 / **minSdk 26**            |

***

## 构建

前置：JDK 17+、Android SDK（platform 35、build-tools 35.0.0）。在 `local.properties` 写：

```properties
sdk.dir=你的SDK路径
```

仓库没有提交 Gradle Wrapper（`gradlew`），用本机安装的 Gradle 8.9 或 IDE 自带版本执行：

```bash
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 58MB，含调试符号与压缩后的英汉、汉语词库）

gradle assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（约 47MB，已签名）
```

release 已开启 R8 代码压缩 + 资源收缩；当前包体积主体是两份词库（英汉 86.1MB、汉语 14.5MB SQLite，压缩后合计约 46MB）。
签名信息放在 `local.properties`（`stb.storeFile` / `stb.storePassword` / `stb.keyAlias` / `stb.keyPassword`），
密钥文件为 `keystore/subjecttoolbox-release.jks`；两者都已 gitignore，换机器时必须一起搬走，
否则新包无法覆盖安装。`dist/` 里保留可直接安装的 debug 包，release 包构建后同样归档到这里。

`minSdk 26` 是权衡结果：覆盖校园在役设备，同时避开 Android 7 的存储与后台限制差异。
注意它**和全文检索无关**——Android 系统 SQLite 只编译了 FTS3/FTS4，没有 FTS5，换版本号也换不来（在 API 35 上实测 `CREATE VIRTUAL TABLE ... USING fts5` 直接报 `no such module: fts5`）。词典因此干脆不用全文索引：`word` 列声明 `COLLATE NOCASE`，精确/前缀查询走 `idx_entry_word` 索引，中文反查走 LIKE 全表扫描（放在 IO 线程 + 300ms 输入防抖）。不做 FTS4 是因为全量 77 万条下它会让词库体积再涨近一倍。

**屏幕方向**：`sensorLandscape` 锁定横屏（支持左右两个横向姿态），符合平板在教室里的实际握持方式。
布局仍按 `BoxWithConstraints` 自适应，分屏或小窗口下会自动降级为上下堆叠。

***

## 云端接入

后台控制台在另一个仓库 `SubjectToolbox-dash`（部署见其 `docs/DEPLOYMENT.md`），客户端代码在 `core/data/.../cloud/`。
默认域名：

| 构建 | 地址 |
| --- | --- |
| debug | `http://127.0.0.1:8787`（真机调试先 `adb reverse tcp:8787 tcp:8787`；MuMu/Android 模拟器也可用 `10.0.2.2`） |
| release | `https://stb.mcobs.cn`（生产，直连 HTTPS；服务端仍保留 HTTP → HTTPS 跳转兜底） |

可在 `local.properties` 覆盖（都已给默认值，不填也能构建）：

```properties
# 客户端上报密钥；缺失时云端同步 / 遥测 / 反馈全部 401，构建阶段会警告
stb.apiKey=stb_live_xxxxxxxx
stb.debugApiBase=http://127.0.0.1:8787
stb.releaseApiBase=https://stb.mcobs.cn
```

**HTTPS**：生产地址直连 `https://stb.mcobs.cn`，请求不再有明文首跳，`X-STB-Key` 全程走 TLS。
`CloudApi` 仍保留手动跟随 301/302/303/307/308 的能力（服务端配置跳转时不会丢 POST 方法或请求体），
并禁止 HTTPS 降级到 HTTP。`network_security_config` 只对 `127.0.0.1`、`localhost`、`10.0.2.2` 和 `stb.mcobs.cn` 放行明文，
用于真机本地调试与临时跳转兜底，其余域名一律禁止明文。

**临时 IP 直连（应急，禁止对外分发）**：生产域名若在部分网络被按 SNI 拦截（TLS 握手被 RST），
可临时把 `stb.releaseApiBase` 改成 `https://<服务器 IP>`，并在 `local.properties` 里加
`stb.allowInsecureTls=true` 后重新打包：IP 访问不带 SNI，服务器在 IP 上出示的是自签证书，
客户端会跳过证书链与主机名校验（`CloudApi` 只在开关打开时生效，默认关闭）。
代价是失去对中间人的防护、`X-STB-Key` 可被冒充服务端骗取，仅用于内测/排障；
域名或备案问题解决后必须改回 `https://stb.mcobs.cn` 并删掉 `stb.allowInsecureTls`。

**设备 SN**：依次尝试 `Build.getSerial()`、系统属性 `ro.serialno`、`Settings.Secure.ANDROID_ID`（前缀 `STB-AID-`）、
首次随机生成并持久化。Android 10+ 普通应用读不到硬件序列号是系统限制；学校用 MDM 预置 SN 时可在
`DeviceIdentity` 里接入 `RestrictionsManager`。设置 → 关于 里可以直接看到当前「设备标识」。
持久化的 SN 存在 SharedPreferences `stb_device_identity` 中，并已从系统云备份 / 换机直传中排除，
避免换机恢复后新设备沿用旧 SN（后台以 SN 为主键，同号会串设备数据）。

**同步策略**（都在 `CloudControlImpl` 中，服务端也会下发同样的上限）：

- **前台短轮询**：App 在前台时每 30–60 秒（服务端下发的 `sync.revisionPollMinSeconds`–`revisionPollMaxSeconds`，每 tick 随机）调用 `GET /v1/config/revision` 只查修订号；修订号变化才拉完整配置，公告/工具开关/版本提示会随之刷新；退到后台立即停止，不建立 SSE/WebSocket 常驻连接；
- 完整配置（握手 / 工具清单 / 设备级开关与停用原因）仍最多 **6 小时**兜底同步一次；单设备开关在后台保存后，该设备下次轮询发现修订号不变（设备覆盖不发布修订）也会在下次完整同步/手动同步时生效；
- 遥测最多 **10 分钟**批量上报一次，单批最多 **200 条**；
- 没有可用网络时**不发起任何请求**，事件先落本地队列；
- 队列最多 **800 条 / 512 KB**，超出丢弃最旧；配置缓存只有几 KB，不下载内容包；
- 失败按 5 分钟起步指数退避，最多 1 小时；手动「立即同步」最短 30 秒一次；
- 请求超时 8 秒，全部在 IO 线程；网络判断需要 `ACCESS_NETWORK_STATE`，只判断不读取网络内容。

**客户端功能**：

- 主页展示云端公告与维护提示；被管理员停用的工具**不再从主页隐藏**（置灰 + 「已停用」徽章，点击弹原因，见下方「工具停用（不隐藏）」），直达链接会显示「工具暂不可用」；
- 未捕获异常会同步写入本地队列，下一次启动上报，后台按签名聚合到崩溃记录；
- 主页右上角新增「意见反馈」独立入口（问题反馈 / 功能建议 / 内容纠错 / 其他，提交时才联网，同一设备每天最多 5 条），
   按设备标识归集，可在同一页查看公开回复；打开页面查询一次，手动刷新有 30 秒缓存，不轮询；
- 交互示意图子项（细胞分裂交互图、地球自转交互图）与意见反馈入口也纳入后台开关：停用时卡片 / 图标仍显示，点击弹停用原因；
- 设置页保留「云端同步」（修订号、最近同步、可用工具数、立即同步）与「数据与隐私」（匿名使用统计开关，默认开启，关闭后清空本地队列）；
- 版本提示只展示「发现新版本 vX，请通过应用市场更新」和更新说明，不提供下载地址、不自动下载；
- 工具清单在握手时上报（11 个主工具 + 2 个示意图子项 + 意见反馈，共 14 项），后台「配置概览 → 工具清单同步」可看到覆盖率和标题差异。

**工具停用（不隐藏）**：

后台「云端控制 → 工具开关」和「设备详情 → 单设备功能控制」都可以停用某个工具 / 示意图子项 / 反馈入口，客户端的表现是：

- 卡片保留原位、降低不透明度并标注「已停用」；
- 点击不跳转，而是弹出停用原因。原因分两级：**单设备原因 > 全局原因**，都没填时用默认文案「学校管理员暂时关闭了这个工具；你的本地数据不会丢失。」；单设备原因会额外标注「该停用仅针对本设备」，全局原因标注「学校管理员设置」；
- 示意图索引页里的子项同样置灰并参与搜索；主页右上角反馈图标仍显示，停用时点击弹原因、不进入反馈页；
- 通过直达链接 / 历史记录进入已停用工具或反馈页时，展示「工具暂不可用」页而不是崩溃；
- 离线时使用本地缓存的开关状态（进入主页前已同步过一次），不会因为断网把已停用的工具又放出来。

隐私说明：遥测只包含 SN、机型/系统、版本、打开过哪些工具、会话时长与崩溃堆栈（不含姓名、学号、输入内容）；
意见反馈只有点提交时才发送，联系方式由用户主动填写。

***

## 模块结构

```
app/                      壳层：Application、MainActivity、NavHost、装配
core/model/               纯 Kotlin 模型与仓库契约（无 Android 依赖）
core/registry/            工具注册表、可见性解析、主页组装（纯逻辑）
core/designsystem/        主题推导、通用组件、交互图索引页与 WebView 容器
core/data/                DataStore 配置、使用记录、assets 读取
  cloud/                  SN、HTTP、本地队列、云端配置、意见反馈
feature/home/             主页
feature/settings/         设置 + 显示管理
feature/chinese-poetry/   语文·古诗词
feature/chinese-dictionary/ 语文·汉语词典
feature/math-function/    数学·函数编辑器
feature/english-dictionary/ 英语·词典
feature/english-grammar/  英语·语法
feature/chemistry-equation/ 化学·方程式
feature/periodic-table/   化学·元素周期表
feature/biology-diagram/  生物·交互示意图
feature/geography-diagram/ 地理·交互示意图
feature/electronic-textbook/ 通用·电子教材
feature/formula/          通用·公式大全
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
├── chinese/zhdict.db        汉语词库（现代汉语词典第 7 版，70,113 条，15.2MB，首启复制到 databases/）
├── english/dict.db          预置词库（ECDICT 全量派生，86.1MB，首启复制到 databases/）
├── english/grammar.json     语法讲义（49 篇，24 类，构建期由 Markdown 转换）
├── chemistry/equations.json 方程式（508 条，构建期逐条校验配平）
├── chemistry/elements.json  118 元素数据
├── biology/cell-division.html 细胞分裂交互图
├── geography/earth-rotation.html 地球自转交互图
└── formula/                  公式库（formulas.json 281 条 + KaTeX 渲染页与本地字体）
```

当前内置：**61 篇古诗文**（新课标必背 60 篇全部收录 + 沁园春·长沙，按统编版六册归类，含注释与赏析）、**现代汉语词典第 7 版 70,113 条**（繁体、异体与重定向别名共 4,686 条，15.2MB SQLite）、**ECDICT 77 万词条（含 36.6 万条短语，86.1MB SQLite，APK 内压缩约 40MB）**、**薄冰大学英语语法 50 篇专题**、**508 条方程式**（构建期逐条校验配平）、**118 元素周期表**、**281 条初高中数理化公式**（数学 143 · 物理 99 · 化学 39，逐条含变量说明与适用条件；基础数据整理自 HuiTurn/Formula，MIT License，补充条目参考 drhuang.com 公式大全）；「电子教材」在线访问人教社官网查看各科电子课本。
正式部署时替换为教研组校对/授权的内容即可，接口不变。

词库与语法讲义由脚本生成，不要手改 assets：

```bash
# 数据源：https://github.com/skywind3000/ECDICT 的 ecdict.csv（MIT License）
python tools/dictionary/build_dict_db.py --source H:/tools/ecdict/ecdict.csv
python tools/dictionary/verify_dict_db.py

# 数据源：https://github.com/pampas9000/ModernChineseDict 的 dict.mdx
# 先 `mdict -x dict.mdx -d ./mdx` 解包，再指向解包出来的文本
python tools/dictionary/build_zh_dict_db.py --source H:/tools/zhdict/mdx/dict.mdx.txt

# 数据源：https://github.com/oldwestenglish/grammar 的 docs 目录（docsify 站点）
python tools/grammar/build_grammar_json.py --source H:/tools/grammar/docs
```

收录规则是**全量**：只排除空词 / 超 60 字符 / 大小写重复 / 完全无释义的词，
不再按考试标签、词频、柯林斯星级筛「精选词」——短语几乎都没有这些标记，
早期 5.9 万条版本只剩 887 条短语，`look forward to`、`take part in` 这类常用短语全查不到。
词频和考试标签仍然入库，只用于把常用词排在专业术语前面。
词库版本变化时应用会重新复制 assets，并把旧库里的生词本自动迁移过来。

***

## 第三方资源

学科与工具图标取自 [Lucide](https://lucide.dev)（ISC License），16 枚矢量图放在
`core/designsystem/src/main/res/drawable/`，只做了 SVG → Android vector drawable 的等价转换，
单色描边、由 `Icon(tint = 学科色)` 染色，因此天然适配深浅色与字号档位。
汉语词库派生自 [pampas9000/ModernChineseDict](https://github.com/pampas9000/ModernChineseDict)，
源仓库注明仅供学习和交流、禁止商用，本项目仅在非商业校园场景使用。
语法讲义来自 [oldwestenglish/grammar](https://github.com/oldwestenglish/grammar)，
内容为《薄冰大学英语语法》的网络整理版，同样只在非商业校园场景使用。
完整清单与许可原文见 [`docs/THIRD_PARTY_LICENSES.md`](docs/THIRD_PARTY_LICENSES.md)。

***

## 两个刻意的取舍

**离线优先，但不再假装「永不联网」。** 只申请 `INTERNET` 与 `ACCESS_NETWORK_STATE` 两个普通权限；电子教材需要联网，遥测/云端配置/意见反馈是可选项，没有网络时一个请求都不发。不引入任何第三方统计/崩溃 SDK，也没有后台常驻接口或推送通道：**只在 App 前台**做 30–60 秒随机 revision 短轮询（变化才拉全量配置），完整配置 6 小时兜底同步、遥测 10 分钟一批、失败退避，设置里可以随时关闭匿名统计。代价是后台数据要靠客户端主动上报，做不到实时。

**地理模块不画地图，只做示意交互图。** 离线意味着腾讯/高德/百度/天地图的在线底图全部不可用；离线底图只能取自然资源部标准地图的静态成果并标注审图号，OSM/Mapbox/Google 等无资质数据源不得使用。目前地理只提供不涉及边界数据的地球自转示意图，底图接入位留空，等校方确定合规底图后再填。

***

## 已知限制

- 数据为示例规模，未做教材全量校对；
- 公式库 281 条，覆盖初高中主干与高中常考拓展，极限、微积分等超纲内容未收录；
- 电子教材是内嵌网页，无网络或人教社改版时不可用，页面行为不受本应用控制；
- 化学配平只处理分子方程式，不含离子方程式与氧化还原半反应；
- 未接入音频朗读、学生账号体系（设计上的非目标）；云同步仅限工具开关、公告与意见反馈；
- 云端内容包更新尚未实现，只有版本提示（方案见后台仓库 `docs/CONTENT_UPDATES.md`）；
- Android 10+ 读不到硬件序列号时会回退为 `STB-AID-…`，MDM 下发真实 SN 的适配尚未接入；
- release 已开 R8 + 资源收缩，使用项目内自签名密钥（`keystore/`）；上架前需补 Baseline Profile 并做一次真机回归；

