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

## 公式数据：HuiTurn/Formula（MIT License）

`app/src/main/assets/content/formula/formulas.json` 的数据整理自
[HuiTurn/Formula](https://github.com/HuiTurn/Formula)。

## KaTeX（MIT License）

公式排版的渲染页与字体随 `app/src/main/assets/content/formula/` 一同分发。
