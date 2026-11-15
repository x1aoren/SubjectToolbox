#!/usr/bin/env python3
"""把《薄冰大学英语语法》的 Markdown 讲义转成应用内置的 grammar.json。

数据源：https://github.com/oldwestenglish/grammar 的 docs 目录（docsify 站点）。
页面本身是 Markdown，这里只做结构转换，不改写原意：

    python tools/grammar/build_grammar_json.py --source H:/tools/grammar/docs

目录顺序以 _sidebar.md 为准，文章里的第一个一级标题作为正文标题，
其余标题、表格、引用、代码块原样保留为块，交给 Compose 原生渲染。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

TITLE = "薄冰大学英语语法"
SOURCE_URL = "https://github.com/oldwestenglish/grammar"

# 不随讲义一起打包的篇目：这类附录只是罗列网站域名，早已失效，还会混进全文检索
EXCLUDED_ARTICLES = {"附录/英语学习网站资源域名.md"}

LINK_RE = re.compile(r"\[([^\]]*)\]\((<[^>]+>|[^)]+)\)")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")
SEPARATOR_RE = re.compile(r"^:?-{2,}:?$")
FENCE_RE = re.compile(r"^```")


def clean_inline(text: str) -> str:
    """去零宽字符、折叠空白、把链接还原成可读文本。"""
    text = text.replace("\u200b", "").replace("\ufeff", "")
    text = text.replace("\u00a0", " ").replace("\t", " ")

    def link(match: re.Match[str]) -> str:
        label = match.group(1).strip()
        url = match.group(2).strip().strip("<>").strip()
        if not label:
            return url
        # 附录里的链接大多「文字就是网址」，只差一个结尾斜杠，没必要重复一遍
        if label.rstrip("/") == url.rstrip("/"):
            return label
        return f"{label}（{url}）"

    text = LINK_RE.sub(link, text)
    return re.sub(r" {2,}", " ", text).strip()


def split_table_row(line: str) -> list[str]:
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]

    cells: list[str] = []
    buf: list[str] = []
    escaped = False
    for ch in s:
        if escaped:
            buf.append(ch)
            escaped = False
        elif ch == "\\":
            escaped = True
        elif ch == "|":
            cells.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    cells.append("".join(buf))
    return cells


def is_separator_row(line: str) -> bool:
    cells = [c.strip() for c in split_table_row(line) if c.strip()]
    return bool(cells) and all(SEPARATOR_RE.match(c) for c in cells)


def clean_cell(raw: str) -> str:
    # 表格里的换行全靠 <br/>，先换成分行，再逐行清理
    raw = re.sub(r"<br\s*/?>", "\n", raw, flags=re.IGNORECASE)
    lines = [clean_inline(part) for part in raw.split("\n")]
    return "\n".join(line for line in lines if line)


def starts_block(line: str) -> bool:
    s = line.strip()
    return (
        s.startswith("```")
        or s.startswith("|")
        or s.startswith(">")
        or HEADING_RE.match(s) is not None
    )


def parse_blocks(lines: list[str]) -> list[dict]:
    blocks: list[dict] = []
    i = 0
    total = len(lines)

    while i < total:
        current = lines[i].strip()
        if not current:
            i += 1
            continue

        if FENCE_RE.match(current):
            i += 1
            code: list[str] = []
            while i < total and not FENCE_RE.match(lines[i].strip()):
                code.append(lines[i].replace("\t", "    ").rstrip())
                i += 1
            if i >= total:
                raise ValueError("代码块没有闭合")
            i += 1
            text = "\n".join(code).strip("\n")
            if text.strip():
                blocks.append({"type": "code", "text": text})
            continue

        heading = HEADING_RE.match(current)
        if heading:
            blocks.append(
                {
                    "type": "h",
                    "level": len(heading.group(1)),
                    "text": clean_inline(heading.group(2)),
                }
            )
            i += 1
            continue

        if current.startswith("|"):
            rows: list[str] = []
            while i < total and lines[i].strip().startswith("|"):
                rows.append(lines[i])
                i += 1

            header = [clean_cell(c) for c in split_table_row(rows[0])]
            body: list[list[str]] = []
            for row in rows[1:]:
                if is_separator_row(row):
                    continue
                body.append([clean_cell(c) for c in split_table_row(row)])

            columns = max([len(header)] + [len(r) for r in body]) if header else 0
            if columns == 0:
                continue
            header += [""] * (columns - len(header))
            body = [r + [""] * (columns - len(r)) for r in body]
            blocks.append({"type": "table", "head": header, "rows": body})
            continue

        if current.startswith(">"):
            quote: list[str] = []
            while i < total and lines[i].strip().startswith(">"):
                content = lines[i].strip()[1:]
                if content.startswith(" "):
                    content = content[1:]
                quote.append(clean_inline(content))
                i += 1
            text = "\n".join(quote).strip()
            text = re.sub(r"\n{3,}", "\n\n", text)
            if text:
                blocks.append({"type": "quote", "text": text})
            continue

        paragraph: list[str] = []
        while i < total:
            s = lines[i].strip()
            if not s or starts_block(lines[i]):
                break
            paragraph.append(clean_inline(s))
            i += 1
        text = "\n".join(part for part in paragraph if part)
        if text:
            blocks.append({"type": "p", "text": text})

    return blocks


def parse_sidebar(path: Path) -> list[dict]:
    categories: list[dict] = []
    current: dict | None = None

    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip():
            continue
        match = re.match(r"^(\s*)-(?:\s*-\s*)?\s*(.*)$", raw)
        if not match:
            continue
        indent = len(match.group(1).replace("\t", "  "))
        rest = match.group(2).strip()
        link = LINK_RE.search(rest)
        if link:
            target = link.group(2).strip().strip("<>").strip()
            if target == "/" or current is None:
                continue
            current["articles"].append(
                {"nav": clean_inline(link.group(1)), "path": target.removeprefix("./")}
            )
        elif indent > 0:
            current = {"title": clean_inline(rest), "articles": []}
            categories.append(current)

    return [c for c in categories if c["articles"]]


def build(source: Path) -> tuple[dict, list[str]]:
    warnings: list[str] = []
    sidebar_path = source / "_sidebar.md"
    if not sidebar_path.is_file():
        raise SystemExit(f"找不到目录文件：{sidebar_path}")

    categories = parse_sidebar(sidebar_path)
    used: set[Path] = set()

    for category in categories:
        articles: list[dict] = []
        for entry in category["articles"]:
            article_path = (source / entry["path"]).resolve()
            if not article_path.is_file():
                raise SystemExit(f"目录里的文章不存在：{entry['path']}")
            used.add(article_path)
            if entry["path"] in EXCLUDED_ARTICLES:
                continue

            lines = article_path.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
            blocks = parse_blocks(lines)

            title = entry["nav"]
            if blocks and blocks[0]["type"] == "h" and blocks[0]["level"] == 1:
                title = blocks.pop(0)["text"]

            odd_bold = sum(1 for b in blocks if b["type"] != "table" and b["text"].count("**") % 2)
            if odd_bold:
                warnings.append(f"{entry['path']} 有 {odd_bold} 个块的 ** 没成对")

            articles.append(
                {
                    "id": entry["path"].removesuffix(".md"),
                    "title": title,
                    "navTitle": entry["nav"],
                    "blocks": blocks,
                }
            )
        category["articles"] = articles

    all_md = {
        p.resolve()
        for p in source.rglob("*.md")
        if p.name not in ("_sidebar.md", "README.md")
    }
    missing = sorted(all_md - used)
    if missing:
        warnings.append("目录未收录：" + "、".join(p.name for p in missing))

    book = {
        "title": TITLE,
        "source": SOURCE_URL,
        "categories": categories,
    }
    return book, warnings


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="生成英语语法内容 JSON")
    parser.add_argument("--source", required=True, help="grammar 仓库的 docs 目录")
    parser.add_argument(
        "--out",
        default=str(root / "app/src/main/assets/content/english/grammar.json"),
        help="输出路径",
    )
    args = parser.parse_args()

    source = Path(args.source).resolve()
    book, warnings = build(source)

    articles = [a for c in book["categories"] for a in c["articles"]]
    blocks = [b for a in articles for b in a["blocks"]]
    kinds: dict[str, int] = {}
    for block in blocks:
        kinds[block["type"]] = kinds.get(block["type"], 0) + 1

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(book, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    print(f"分类 {len(book['categories'])} 个，文章 {len(articles)} 篇，块 {len(blocks)} 个：{kinds}")
    print(f"输出 {out}（{out.stat().st_size / 1024:.1f} KB）")
    for warning in warnings:
        print("警告：" + warning)
    return 0


if __name__ == "__main__":
    sys.exit(main())
