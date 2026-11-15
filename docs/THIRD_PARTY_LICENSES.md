# 第三方资源与许可

本仓库包含的第三方内容及其许可协议。修改或再分发时请一并保留本文件。

## Lucide 图标（ISC License）

学科与工具图标取自 [Lucide](https://lucide.dev)，共 15 枚，全部位于
`core/designsystem/src/main/res/drawable/`，只做了 SVG → Android vector drawable 的等价转换
（`<circle>`/`<rect>` 改写为等效 `pathData`），未改动图形本身。

| 文件 | Lucide 图标名 | 用途 |
| --- | --- | --- |
| `ic_subject_chinese.xml` | `book-open` | 学科：语文 |
| `ic_subject_math.xml` | `chart-spline` | 学科：数学 |
| `ic_subject_english.xml` | `languages` | 学科：英语 |
| `ic_subject_chemistry.xml` | `flask-conical` | 学科：化学 |
| `ic_subject_biology.xml` | `dna` | 学科：生物 |
| `ic_subject_geography.xml` | `earth` | 学科：地理 |
| `ic_subject_other.xml` | `layout-grid` | 学科：通用 |
| `ic_tool_poetry.xml` | `scroll-text` | 工具：古诗词 |
| `ic_tool_math_function.xml` | `square-function` | 工具：函数编辑器 |
| `ic_tool_dictionary.xml` | `book-a` | 工具：词典 |
| `ic_tool_chemistry.xml` | `atom` | 工具：化学方程式 |
| `ic_tool_biology.xml` | `microscope` | 工具：生物·交互示意图 |
| `ic_tool_geography.xml` | `orbit` | 工具：地理·交互示意图 |
| `ic_tool_textbook.xml` | `library` | 工具：电子教材 |
| `ic_tool_formula.xml` | `sigma` | 工具：公式大全 |

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

## 词库：ECDICT（MIT License）

`app/src/main/assets/content/english/dict.db` 由 [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT)
的 `ecdict.csv` 派生，生成脚本见 `tools/dictionary/`。

## 汉语词库：ModernChineseDict（仅供学习交流，禁止商用）

`app/src/main/assets/content/chinese/zhdict.db` 由
[pampas9000/ModernChineseDict](https://github.com/pampas9000/ModernChineseDict)
的 `dict.mdx` 解包文本派生，生成脚本见 `tools/dictionary/build_zh_dict_db.py`。

该仓库是《现代汉语词典》第 7 版的 mdx 重制资源，README 明确写明「此资源仅供学习和交流，
禁止商用，请务必于下载试用后 24 小时内删除」。本项目只在非商业校园场景随包分发；
若要商用、公开上架或对外发行，必须先替换为已获得授权的词库，代码与数据集接口不需要改。

## 化学方程式内容

`app/src/main/assets/content/chemistry/equations.json` 的扩充条目参考
[ChemSVG 方程库](https://chemsvg.cn/templates) 与
[《最全高中化学方程式分类汇总》](https://www.hxzxs.cn/uploads/soft/%E6%9C%80%E5%85%A8%E9%AB%98%E4%B8%AD%E5%8C%96%E5%AD%A6%E6%96%B9%E7%A8%8B%E5%BC%8F%E5%88%86%E7%B1%BB%E6%B1%87%E6%80%BB.pdf)
整理，只保留分子方程式，并逐条校验原子守恒。两个来源均未声明可再分发许可，
公开分发前请替换为教研组校对或已获授权的数据。

## 公式数据：HuiTurn/Formula（MIT License）与 drhuang.com 公式大全

`app/src/main/assets/content/formula/formulas.json` 的基础数据整理自
[HuiTurn/Formula](https://github.com/HuiTurn/Formula)。

高中数学、物理、化学的补充条目参考
[drhuang.com 数理化公式大全](https://drhuang.com/chinese/science/%E5%88%9D%E4%B8%AD%E9%AB%98%E4%B8%AD%E6%95%B0%E7%90%86%E5%8C%96%E5%85%AC%E5%BC%8F%E5%A4%A7%E5%85%A8/)
的章节结构，公式文本按教材通用形式重新录入。该站点未声明开源许可，仅作目录与选条参考。

## KaTeX（MIT License）

公式排版的渲染页与字体随 `app/src/main/assets/content/formula/` 一同分发。
