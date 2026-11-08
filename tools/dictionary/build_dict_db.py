#!/usr/bin/env python3
"""从 ECDICT 的 ecdict.csv 生成应用内置的 dict.db。

数据源: https://github.com/skywind3000/ECDICT (MIT License)，ecdict.csv 约 77 万条。

用法:
    python tools/dictionary/build_dict_db.py --source H:/tools/ecdict/ecdict.csv
    python tools/dictionary/build_dict_db.py --source ... --output /tmp/dict.db --no-definition

收录策略：全量收录，只排除空词 / 超长词 / 大小写重复词 / 完全没有释义的词。
早期版本按「考试标签 / 词频 / 柯林斯 / 牛津核心」只留 5.9 万条精选词，
而 ECDICT 的 36.6 万条短语几乎没有这些标记，结果只留下 887 条短语，
look forward to、take part in、be used to 之类的常用短语全部查不到。
词频与考试标签仍然随词条入库，供搜索结果排序使用，只是不再当作收录门槛。

schema 说明：word 列声明 COLLATE NOCASE，配合 idx_entry_word 做大小写不敏感的
精确/前缀查询，因此不需要再存一份冗余的 word_lower 列（全量规模下约省 12MB）。
"""
import argparse
import csv
import os
import sqlite3
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_OUT = os.path.join(
    ROOT, "app", "src", "main", "assets", "content", "english", "dict.db"
)
DEFAULT_SRC = r"H:\tools\ecdict\ecdict.csv"
DB_VERSION = 4
MAX_WORD_LEN = 60
BATCH = 5000


def num(s):
    try:
        return int(s)
    except (TypeError, ValueError):
        return 0


def clean(s):
    """ECDICT 把换行存成字面量反斜杠 n，入库前还原成真换行，否则界面会原样显示 \\n。"""
    if not s:
        return ""
    return s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n").strip()


def parse_args():
    p = argparse.ArgumentParser(description="生成 SubjectToolbox 预置英汉词库")
    p.add_argument("--source", default=DEFAULT_SRC, help="ECDICT ecdict.csv 路径")
    p.add_argument("--output", default=DEFAULT_OUT, help="输出 dict.db 路径")
    p.add_argument(
        "--no-definition", action="store_true",
        help="不导入英文释义（仅用于体积对比，正式构建不要加）",
    )
    return p.parse_args()


def main():
    args = parse_args()
    src, out = args.source, args.output
    if not os.path.isfile(src):
        print(f"找不到 ECDICT 数据文件: {src}")
        print("请从 https://github.com/skywind3000/ECDICT 下载 ecdict.csv 后用 --source 指定路径。")
        return 1

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)

    t0 = time.time()
    conn = sqlite3.connect(out)
    cur = conn.cursor()
    cur.execute("PRAGMA journal_mode=OFF")
    cur.execute("PRAGMA synchronous=OFF")
    cur.execute("PRAGMA temp_store=MEMORY")
    cur.execute("""
        CREATE TABLE entry(
          word TEXT NOT NULL COLLATE NOCASE,
          phonetic TEXT NOT NULL DEFAULT '',
          translation TEXT NOT NULL DEFAULT '',
          definition TEXT NOT NULL DEFAULT '',
          tag TEXT NOT NULL DEFAULT '',
          bnc INTEGER NOT NULL DEFAULT 0,
          frq INTEGER NOT NULL DEFAULT 0,
          exchange TEXT NOT NULL DEFAULT ''
        )
    """)
    sql = ("INSERT INTO entry"
           "(word,phonetic,translation,definition,tag,bnc,frq,exchange) "
           "VALUES(?,?,?,?,?,?,?,?)")

    seen = set()
    batch = []
    stat = dict(total=0, phrase=0, definition=0, tagged=0, duplicate=0, invalid=0)
    with open(src, encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            word = (r.get("word") or "").strip()
            if not word or len(word) > MAX_WORD_LEN:
                stat["invalid"] += 1
                continue
            key = word.lower()
            if key in seen:
                stat["duplicate"] += 1
                continue
            translation = clean(r.get("translation"))
            definition = "" if args.no_definition else clean(r.get("definition"))
            if not translation and not definition:
                stat["invalid"] += 1
                continue
            seen.add(key)
            tag = (r.get("tag") or "").strip()
            batch.append((
                word,
                (r.get("phonetic") or "").strip(),
                translation,
                definition,
                tag,
                num(r.get("bnc")),
                num(r.get("frq")),
                (r.get("exchange") or "").strip(),
            ))
            stat["total"] += 1
            if " " in word:
                stat["phrase"] += 1
            if definition:
                stat["definition"] += 1
            if tag:
                stat["tagged"] += 1
            if len(batch) >= BATCH:
                cur.executemany(sql, batch)
                batch.clear()
    if batch:
        cur.executemany(sql, batch)

    cur.execute("CREATE INDEX idx_entry_word ON entry(word)")
    cur.execute("CREATE INDEX idx_entry_bnc ON entry(bnc)")
    cur.execute("CREATE TABLE wordbook(headword TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
    cur.execute(f"PRAGMA user_version = {DB_VERSION}")
    conn.commit()
    cur.execute("VACUUM")
    conn.commit()
    conn.close()

    size = os.path.getsize(out) / 1024 / 1024
    print(f"输出 {out}")
    print(f"收录 {stat['total']} 条（短语 {stat['phrase']} 条 / 带英文释义 {stat['definition']} 条"
          f" / 带考试标签 {stat['tagged']} 条）")
    print(f"排除：重复词 {stat['duplicate']} 条，空词或超长或无释义 {stat['invalid']} 条")
    print(f"体积 {size:.1f} MB，用时 {time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
