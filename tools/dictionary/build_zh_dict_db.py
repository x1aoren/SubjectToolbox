#!/usr/bin/env python3
"""从 ModernChineseDict 的 dict.mdx.txt 生成应用内置的 zhdict.db。

数据源: https://github.com/pampas9000/ModernChineseDict
仓库提供《现代汉语词典》第 7 版的 mdx 资源。mdx 是二进制格式，不能直接读，
先用 mdict-utils 解包：

    pip install mdict-utils
    mdict -x dict.mdx -d ./mdx
    python tools/dictionary/build_zh_dict_db.py --source ./mdx/dict.mdx.txt

解包出来的 dict.mdx.txt 每三条一组：词头、HTML 正文、`</>`。
词头行有不少与正文标题不一致（正文标题带 ¹² 上标，或儿化词头被拆开），
因此正文标题优先，词头行只用来补充别名。

入库时把 HTML 摊平成带换行的纯文本：`<br>` 转换行、义项序号后补空格、
词性标记套上〔〕、注释块单独起行。表格保留为「单元格 + 空格」的文本行，
界面不需要再解析 HTML。

@@@LINK= 重定向条目（约 4700 条繁体/异体索引）不单独占词条，
而是写入 alias 表指向本体；正文里的繁体、异体、不推荐写法也一并进 alias，
所以查「醜」「夥」都能落到简体条目上。
"""
import argparse
import html
import os
import re
import sqlite3
import sys
import time
import unicodedata

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(
    ROOT, "app", "src", "main", "assets", "content", "chinese", "zhdict.db"
)
DB_VERSION = 1
BATCH = 2000

SUPERS = "¹²³⁴⁵⁶⁷⁸⁹⁰"
SUPER_MAP = dict(zip(SUPERS, "1234567890"))
CIRCLED = "➊➋➌➍➎➏➐➑➒➓"

TAG_RE = re.compile(r"<[^>]*>")
SPACE_RE = re.compile(r"[ \t\u00a0]+")


def strip_tags(fragment):
    return html.unescape(TAG_RE.sub("", fragment)).strip()


def balanced_element(fragment, start_tag, tag):
    """取出 start_tag 对应元素的内部 HTML 和结束位置，支持同名标签嵌套。"""
    start = fragment.find(start_tag)
    if start < 0:
        return None, -1
    content_start = start + len(start_tag)
    depth = 1
    pos = content_start
    open_tag = "<" + tag
    close_tag = "</" + tag + ">"
    while True:
        next_open = fragment.find(open_tag, pos)
        next_close = fragment.find(close_tag, pos)
        if next_close < 0:
            return None, -1
        if 0 <= next_open < next_close:
            depth += 1
            pos = next_open + len(open_tag)
        else:
            depth -= 1
            if depth == 0:
                return fragment[content_start:next_close], next_close + len(close_tag)
            pos = next_close + len(close_tag)


def meta_forms(metadata):
    """按 class 收集 metadata 里的繁体、异体、不推荐写法。"""
    groups = {"traditional": [], "variant": [], "unrecommended": []}
    pattern = r'<div class="(traditional|generalVariant|unRecommended)([^"]*)"[^>]*>(.*?)</div>'
    for m in re.finditer(pattern, metadata):
        kind, _, content = m.groups()
        value = clean_form(strip_tags(content))
        if not value:
            continue
        if kind == "traditional":
            groups["traditional"].append(value)
        elif kind == "generalVariant":
            groups["variant"].append(value)
        else:
            groups["unrecommended"].append(value)
    return groups


def clean_form(value):
    """去掉异体字后面的义项角标与连接符，如「夥➋-➎」「醜—」只留「夥」「醜」。"""
    value = re.sub(f"[{SUPERS}{CIRCLED}]", "", value)
    value = re.sub(r"[-–—]+\s*$", "", value)
    return value.strip()


def title_key(title):
    """检索键：去掉用于区分同音同形条目的上标数字，C⁴ISR 这类上标保留为数字。"""
    out = []
    for index, char in enumerate(title):
        if char in SUPERS:
            prev = title[index - 1] if index > 0 else ""
            if prev.isascii() and prev.isalnum():
                out.append(SUPER_MAP[char])
        elif char in CIRCLED:
            continue
        elif char in "“”‘’\"'":
            continue
        elif char.isspace():
            continue
        else:
            out.append(char)
    return "".join(out)


def pinyin_key(pinyin):
    """把 shuǐ、ɡuānɡ、xià∥・qù 归一成 shui、guang、xiaqu，供拼音检索。"""
    text = pinyin.replace("ɡ", "g").replace("ü", "v").replace("Ü", "V")
    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-zA-Z]", "", text).lower()


def html_to_text(fragment):
    """HTML 正文转纯文本，保留义项分行与词性标记。"""
    text = re.sub(r"<style[^>]*>.*?</style>", "", fragment, flags=re.S | re.I)
    text = re.sub(r"<script[^>]*>.*?</script>", "", text, flags=re.S | re.I)
    text = re.sub(r"<br\s*/?>", "\n", text, flags=re.I)
    text = re.sub(r"</?t[dh][^>]*>", " ", text, flags=re.I)
    text = re.sub(r"</?(?:table|tr|p|div|html|head|body)[^>]*>", "\n", text, flags=re.I)
    text = re.sub(r'<span class="num">(.*?)</span>', r"\1 ", text, flags=re.S)
    text = re.sub(r'<span class="ly[a-e]">(.*?)</span>', r"〔\1〕", text, flags=re.S)
    text = re.sub(r'<span class="er">(.*?)</span>', r"\1", text, flags=re.S)
    text = re.sub(r'<span class="usage-t">(.*?)</span>', r"【\1】", text, flags=re.S)
    text = TAG_RE.sub("", text)
    text = html.unescape(text).replace("\u00a0", " ")

    lines = []
    for line in text.split("\n"):
        line = SPACE_RE.sub(" ", line).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


def parse_entry(headword, body):
    title_html, title_end = balanced_element(body, '<span class="title">', "span")
    if title_html is None:
        return None
    word = strip_tags(title_html)
    if not word:
        word = headword

    metadata, metadata_end = balanced_element(body, '<div class="metadata">', "div")
    pinyin = ""
    forms = {"traditional": [], "variant": [], "unrecommended": []}
    if metadata is not None:
        m = re.search(r'<div class="py">(.*?)</div>', metadata)
        if m:
            pinyin = strip_tags(m.group(1)).replace("ɡ", "g")
        forms = meta_forms(metadata)

    content_start = metadata_end if metadata is not None else title_end
    definition = html_to_text(body[content_start:])
    if not definition:
        return None

    return {
        "word": word,
        "word_key": title_key(word),
        "pinyin": pinyin,
        "pinyin_key": pinyin_key(pinyin),
        "traditional": "、".join(forms["traditional"]),
        "variants": "、".join(forms["variant"] + forms["unrecommended"]),
        "definition": definition,
        "headword_key": title_key(headword),
    }


def read_entries(source):
    entries = []
    links = []
    with open(source, encoding="utf-8") as f:
        lines = f.read().split("\n")
    index = 0
    while index + 2 < len(lines):
        headword = lines[index].strip()
        body = lines[index + 1].rstrip("\r")
        separator = lines[index + 2].strip()
        if not headword or separator != "</>":
            index += 1
            continue
        index += 3
        if body.startswith("@@@LINK="):
            links.append((headword, body[len("@@@LINK="):].strip()))
        elif "<main>" in body:
            entry = parse_entry(headword, body)
            if entry:
                entries.append(entry)
    return entries, links


def build(source, out):
    t0 = time.time()
    entries, links = read_entries(source)
    if not entries:
        print("没有解析到词条，检查 --source 是否指向 mdx/dict.mdx.txt")
        return 1

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)

    conn = sqlite3.connect(out)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    cur.execute("PRAGMA temp_store=MEMORY")
    cur.execute("""
        CREATE TABLE entry(
          id INTEGER PRIMARY KEY,
          key TEXT NOT NULL UNIQUE,
          word TEXT NOT NULL,
          word_key TEXT NOT NULL COLLATE NOCASE,
          pinyin TEXT NOT NULL DEFAULT '',
          pinyin_search TEXT NOT NULL DEFAULT '',
          traditional TEXT NOT NULL DEFAULT '',
          variants TEXT NOT NULL DEFAULT '',
          definition TEXT NOT NULL DEFAULT ''
        )
    """)
    cur.execute("""
        CREATE TABLE alias(
          name TEXT NOT NULL COLLATE NOCASE PRIMARY KEY,
          entry_id INTEGER NOT NULL
        )
    """)
    cur.execute("CREATE TABLE wordbook(key TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")

    key_ids = {}
    used_keys = {}
    batch = []
    entry_id = 0
    for entry in entries:
        base = entry["word_key"] + "\u0001" + entry["pinyin_key"]
        repeat = used_keys.get(base, 0) + 1
        used_keys[base] = repeat
        key = base if repeat == 1 else f"{base}\u0001#{repeat}"
        entry["key"] = key
        entry_id += 1
        entry["id"] = entry_id
        key_ids.setdefault(entry["word_key"], []).append(entry_id)
        batch.append((
            key,
            entry["word"],
            entry["word_key"],
            entry["pinyin"],
            entry["pinyin_key"],
            entry["traditional"],
            entry["variants"],
            entry["definition"],
        ))
        if len(batch) >= BATCH:
            cur.executemany(
                "INSERT INTO entry(key,word,word_key,pinyin,pinyin_search,"
                "traditional,variants,definition) VALUES(?,?,?,?,?,?,?,?)",
                batch,
            )
            batch.clear()
    if batch:
        cur.executemany(
            "INSERT INTO entry(key,word,word_key,pinyin,pinyin_search,"
            "traditional,variants,definition) VALUES(?,?,?,?,?,?,?,?)",
            batch,
        )

    aliases = {}

    def add_alias(name, entry_id):
        name = strip_tags(name).strip()
        if not name or name == entries[entry_id - 1]["word_key"]:
            return False
        if name in aliases:
            return False
        aliases[name] = entry_id
        return True

    for entry in entries:
        for form in entry["traditional"].split("、") + entry["variants"].split("、"):
            add_alias(form, entry["id"])
        if entry["headword_key"] and entry["headword_key"] != entry["word_key"]:
            add_alias(entry["headword_key"], entry["id"])

    redirect_hits = 0
    for headword, target in links:
        target_key = title_key(target)
        ids = key_ids.get(target_key)
        if ids and add_alias(title_key(headword), ids[0]):
            redirect_hits += 1

    cur.executemany(
        "INSERT OR IGNORE INTO alias(name,entry_id) VALUES(?,?)",
        list(aliases.items()),
    )
    cur.execute("CREATE INDEX idx_entry_word_key ON entry(word_key)")
    cur.execute("CREATE INDEX idx_entry_pinyin ON entry(pinyin_search)")
    cur.execute(f"PRAGMA user_version = {DB_VERSION}")
    conn.commit()
    cur.execute("VACUUM")
    conn.commit()
    conn.close()

    size = os.path.getsize(out) / 1024 / 1024
    traditional = sum(1 for e in entries if e["traditional"])
    with_pinyin = sum(1 for e in entries if e["pinyin"])
    print(f"输出 {out}")
    print(f"收录 {len(entries)} 条（繁体 {traditional} 条 / 有拼音 {with_pinyin} 条）")
    print(f"别名 {len(aliases)} 条（mdx 重定向新录入 {redirect_hits} 条，链接表共 {len(links)} 条）")
    print(f"体积 {size:.1f} MB，用时 {time.time() - t0:.1f}s")
    return 0


def main():
    parser = argparse.ArgumentParser(description="生成 SubjectToolbox 预置汉语词库")
    parser.add_argument("--source", required=True, help="解包后的 mdx/dict.mdx.txt 路径")
    parser.add_argument("--output", default=DEFAULT_OUT, help="输出 zhdict.db 路径")
    args = parser.parse_args()
    if not os.path.isfile(args.source):
        print(f"找不到数据文件: {args.source}")
        print("请先从 https://github.com/pampas9000/ModernChineseDict 下载 dict.mdx，")
        print("用 `mdict -x dict.mdx -d ./mdx` 解包，再把 --source 指向 mdx/dict.mdx.txt。")
        return 1
    return build(args.source, args.output)


if __name__ == "__main__":
    sys.exit(main())
