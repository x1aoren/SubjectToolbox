#!/usr/bin/env python3
"""用与 DictionaryRepository 相同的 SQL 验证生成好的 dict.db（不需要 Android 设备）。

用法:
    python tools/dictionary/verify_dict_db.py
    python tools/dictionary/verify_dict_db.py --db /path/to/dict.db
"""
import argparse
import os
import sqlite3
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_DB = os.path.join(
    ROOT, "app", "src", "main", "assets", "content", "english", "dict.db"
)
COLUMNS = "SELECT word, phonetic, translation, definition, tag, exchange"
RANK = ("CASE WHEN bnc > 0 THEN bnc WHEN frq > 0 THEN frq + 100000 "
        "WHEN tag <> '' THEN 200000 ELSE 1000000 END")


def search(db, raw_query, limit=60):
    """复现 DictionaryRepository.search 的精确 -> 前缀 -> LIKE 兜底三步。"""
    query = raw_query.strip()
    key = query.lower()
    out = {}
    if not query:
        for row in db.execute(
            f"{COLUMNS} FROM entry WHERE bnc > 0 ORDER BY bnc, word LIMIT ?", (limit,)
        ):
            out[row[0]] = row
        return out
    for row in db.execute(f"{COLUMNS} FROM entry WHERE word = ? LIMIT 5", (key,)):
        out[row[0]] = row
    if len(out) < limit:
        for row in db.execute(
            f"{COLUMNS} FROM entry WHERE word >= ? AND word < ? "
            f"ORDER BY {RANK}, length(word), word LIMIT ?",
            (key, key + "\uffff", limit),
        ):
            out.setdefault(row[0], row)
    if len(out) < limit and len(query) >= 2:
        for row in db.execute(
            f"{COLUMNS} FROM entry WHERE word LIKE ? OR translation LIKE ? "
            f"ORDER BY {RANK}, length(word) LIMIT ?",
            (f"%{key}%", f"%{query}%", limit - len(out)),
        ):
            out.setdefault(row[0], row)
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--db", default=DEFAULT_DB)
    db_path = p.parse_args().db
    if not os.path.isfile(db_path):
        print(f"找不到词库: {db_path}")
        return 1

    db = sqlite3.connect(db_path)
    total = db.execute("SELECT count(*) FROM entry").fetchone()[0]
    phrases = db.execute("SELECT count(*) FROM entry WHERE word LIKE '% %'").fetchone()[0]
    with_def = db.execute("SELECT count(*) FROM entry WHERE definition <> ''").fetchone()[0]
    tagged = db.execute("SELECT count(*) FROM entry WHERE tag <> ''").fetchone()[0]
    print(f"词库 {os.path.getsize(db_path)/1024/1024:.1f} MB | 共 {total} 条 | "
          f"短语 {phrases} 条 | 英文释义 {with_def} 条 | 考试标签 {tagged} 条")

    failures = []

    def check(label, ok, detail=""):
        print(f"  [{'OK' if ok else 'FAIL'}] {label}{(' -> ' + detail) if detail else ''}")
        if not ok:
            failures.append(label)

    # 1. 之前被筛掉的常用短语，现在必须能精确命中
    for phrase in ("look forward to", "take part in", "be used to", "in front of",
                   "a lot of", "get along with", "make up", "put off"):
        row = db.execute(
            f"{COLUMNS} FROM entry WHERE word = ?", (phrase,)
        ).fetchone()
        check(f"短语精确命中 {phrase!r}", row is not None,
              (row[2].split("\n")[0][:40] if row else ""))

    # 2. 大小写不敏感（word 列 COLLATE NOCASE）
    check("大小写不敏感 APPLE -> apple",
          any(r[0].lower() == "apple" for r in db.execute(
              f"{COLUMNS} FROM entry WHERE word = ?", ("APPLE",))))

    # 3. 前缀范围扫描能带出短语，且走索引
    look = search(db, "look for", 60)
    check("前缀 'look for' 命中 look forward to", "look forward to" in look,
          f"{len(look)} 条")
    plan = db.execute(
        "EXPLAIN QUERY PLAN SELECT word FROM entry WHERE word >= ? AND word < ? "
        "ORDER BY word LIMIT 60", ("look", "look\uffff")
    ).fetchall()
    check("前缀查询走 idx_entry_word 索引",
          any("idx_entry_word" in str(r) for r in plan), str(plan))

    # 4. 中文反查
    cn = search(db, "光合作用", 60)
    check("中文反查 '光合作用' 含 photosynthesis", "photosynthesis" in cn,
          f"{len(cn)} 条")
    cn2 = search(db, "盼望", 60)
    check("中文反查 '盼望' 有结果", len(cn2) > 0, f"{len(cn2)} 条")

    # 5. 换行已还原，不是字面量 \n
    literal = db.execute(
        "SELECT count(*) FROM entry WHERE translation LIKE '%' || char(92) || 'n%'"
    ).fetchone()[0]
    check("释义里没有残留字面量 \\n", literal == 0, f"{literal} 条")

    # 6. 生词本 join（含大小写不同的 headword）
    db.execute("DELETE FROM wordbook")
    db.execute("INSERT INTO wordbook(headword, added_at) VALUES(?,?)", ("Look Forward To", 1))
    joined = db.execute(
        f"{COLUMNS} FROM wordbook w JOIN entry d "
        "ON d.word = w.headword COLLATE NOCASE ORDER BY w.added_at DESC LIMIT 10"
    ).fetchall()
    check("生词本 join 能取回短语", len(joined) == 1 and joined[0][0] == "look forward to",
          str([r[0] for r in joined]))
    db.rollback()

    # 7. 结果排序：常用词不应被长尾专业术语挤掉
    prefix = list(search(db, "take", 60).keys())
    check("前缀 'take' 首条是 take 或高频词", prefix[0].lower().startswith("take"),
          prefix[0])
    browse = list(search(db, "", 10).keys())
    check("首页浏览展示高频词", len(browse) == 10 and browse[0] in ("the", "be", "of", "and", "a"),
          str(browse[:5]))

    db.close()
    print(f"\n{'全部通过' if not failures else '失败: ' + ', '.join(failures)}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
