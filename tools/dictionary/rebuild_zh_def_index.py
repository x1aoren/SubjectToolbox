#!/usr/bin/env python3
"""给已有的 zhdict.db 补上释义反查索引（definition_main + def_gram）。

正常流程是从 mdx 源重新跑 build_zh_dict_db.py；这个脚本用于拿不到源文件、
手上只有上一版 zhdict.db 的场合：读出原有 entry / alias / wordbook，
按同一套规则重算释义主干与二元组倒排表，再写回同路径。

用法：
    python tools/dictionary/rebuild_zh_def_index.py --input <旧 zhdict.db> \
        [--output <新 zhdict.db>]

`--output` 省略时先写临时文件，成功后再替换 `--input`。
两个库的 schema 规则都来自 build_zh_dict_db.py，避免两处逻辑漂移。
"""
import argparse
import os
import sqlite3
import sys
import time

from build_zh_dict_db import (
    DB_VERSION,
    BATCH,
    cjk_bigrams,
    definition_main_text,
    encode_ids,
)

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_IN = os.path.join(
    ROOT, "app", "src", "main", "assets", "content", "chinese", "zhdict.db"
)


def read_source(path):
    conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    source = conn.cursor()
    entries = source.execute(
        "SELECT id,key,word,word_key,pinyin,pinyin_search,traditional,variants,definition "
        "FROM entry ORDER BY id"
    ).fetchall()
    aliases = source.execute("SELECT name,entry_id FROM alias").fetchall()
    wordbook = source.execute("SELECT key,added_at FROM wordbook").fetchall()
    conn.close()
    return entries, aliases, wordbook


def build(entries, aliases, wordbook, out):
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
          definition TEXT NOT NULL DEFAULT '',
          definition_main TEXT NOT NULL DEFAULT ''
        )
    """)
    cur.execute("""
        CREATE TABLE alias(
          name TEXT NOT NULL COLLATE NOCASE PRIMARY KEY,
          entry_id INTEGER NOT NULL
        )
    """)
    cur.execute("CREATE TABLE wordbook(key TEXT PRIMARY KEY, added_at INTEGER NOT NULL)")
    cur.execute("CREATE TABLE def_gram(gram TEXT PRIMARY KEY, ids BLOB NOT NULL) WITHOUT ROWID")

    gram_map = {}
    batch = []
    for row in entries:
        main_text = definition_main_text(row[8])
        for gram in cjk_bigrams(main_text):
            gram_map.setdefault(gram, []).append(row[0])
        batch.append(tuple(row) + (main_text,))
        if len(batch) >= BATCH:
            cur.executemany("INSERT INTO entry VALUES(?,?,?,?,?,?,?,?,?,?)", batch)
            batch.clear()
    if batch:
        cur.executemany("INSERT INTO entry VALUES(?,?,?,?,?,?,?,?,?,?)", batch)

    cur.executemany("INSERT INTO alias(name,entry_id) VALUES(?,?)", aliases)
    cur.executemany("INSERT INTO wordbook(key,added_at) VALUES(?,?)", wordbook)
    cur.executemany(
        "INSERT INTO def_gram(gram,ids) VALUES(?,?)",
        [(gram, encode_ids(ids)) for gram, ids in gram_map.items()],
    )
    cur.execute("CREATE INDEX idx_entry_word_key ON entry(word_key)")
    cur.execute("CREATE INDEX idx_entry_pinyin ON entry(pinyin_search)")
    cur.execute(f"PRAGMA user_version = {DB_VERSION}")
    conn.commit()
    cur.execute("VACUUM")
    conn.commit()
    conn.close()
    return gram_map


def main():
    parser = argparse.ArgumentParser(description="为 zhdict.db 补释义反查索引")
    parser.add_argument("--input", default=DEFAULT_IN, help="已有的 zhdict.db")
    parser.add_argument("--output", default="", help="输出路径，默认原地替换")
    args = parser.parse_args()
    if not os.path.isfile(args.input):
        print(f"找不到词库: {args.input}")
        return 1

    t0 = time.time()
    entries, aliases, wordbook = read_source(args.input)
    out = args.output or (args.input + ".new")
    gram_map = build(entries, aliases, wordbook, out)
    if not args.output:
        os.replace(out, args.input)
    size = os.path.getsize(args.input if not args.output else out) / 1024 / 1024
    print(f"处理 {len(entries)} 条词条 / {len(aliases)} 条别名 / {len(wordbook)} 条生词")
    print(f"释义反查：{len(gram_map)} 个二元组 / "
          f"{sum(len(ids) for ids in gram_map.values())} 条倒排记录")
    print(f"体积 {size:.1f} MB，用时 {time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
