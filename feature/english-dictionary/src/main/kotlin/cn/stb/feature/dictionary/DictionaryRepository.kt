package cn.stb.feature.dictionary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/** 一个词条。字段对应 ECDICT 的 word / phonetic / translation / definition / tag / exchange。 */
data class DictEntry(
    val headword: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
    val tag: String,
    val exchange: String,
) {
    /** 考试标签：gk 高考、cet4/cet6、ky 考研…… */
    val tagLabels: List<String>
        get() = tag.split(' ', '\t')
            .mapNotNull { TAG_LABELS[it.trim().lowercase()] }
            .distinct()

    /** 词形变化，ECDICT 用 p:过去式/d:过去分词/i:现在分词/3:三单/s:复数/r:比较级/t:最高级 表示 */
    val exchangeLabels: List<Pair<String, String>>
        get() = exchange.split('/').mapNotNull { part ->
            val idx = part.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val label = EXCHANGE_LABELS[part.substring(0, idx).trim()]
            val value = part.substring(idx + 1).trim()
            if (label == null || value.isEmpty()) null else label to value
        }

    companion object {
        private val TAG_LABELS = mapOf(
            "zk" to "中考",
            "gk" to "高考",
            "cet4" to "四级",
            "cet6" to "六级",
            "ky" to "考研",
            "toefl" to "托福",
            "ielts" to "雅思",
            "gre" to "GRE",
        )

        private val EXCHANGE_LABELS = mapOf(
            "p" to "过去式",
            "d" to "过去分词",
            "i" to "现在分词",
            "3" to "第三人称单数",
            "s" to "复数",
            "r" to "比较级",
            "t" to "最高级",
            "0" to "原型",
            "1" to "变换",
        )
    }
}

/**
 * 离线词库。
 *
 * 数据来自 ECDICT（https://github.com/skywind3000/ECDICT，MIT），构建期用
 * `tools/dictionary/build_dict_db.py` 全量导入 77 万条（含 36.6 万条短语），
 * 只排除空词 / 超长词 / 重复词 / 无释义词，做成预置 SQLite 随包发布。
 * 早期版本按「考试标签 / 词频 / 柯林斯 / 牛津核心」只留 5.9 万条，
 * 而短语几乎都没有这些标记，结果只剩 887 条短语，常用短语大量查不到，故改为全量收录。
 * 词频（bnc/frq）与考试标签仍保留，只用来把常用词排在专业术语前面。
 *
 * 运行时只做两件事：首启（或词库版本变化时）把 assets 里的库复制到 databases/，之后直接查。
 * 不做全文索引：Android 系统 SQLite 根本没编译 FTS5，而 FTS4 在 77 万条规模上会让库再涨近一倍。
 * `word` 列带 COLLATE NOCASE，精确 / 前缀查询走 `idx_entry_word` 索引；
 * 中文反查只能走 LIKE 全表扫描，放在 IO 线程 + 输入防抖，避免阻塞输入。
 */
class DictionaryRepository(private val context: Context) {

    private val db: SQLiteDatabase? by lazy { openPrebuilt() }

    private fun openPrebuilt(): SQLiteDatabase? {
        val target = context.getDatabasePath(DB_NAME)
        return runCatching {
            target.parentFile?.mkdirs()
            val stamp = File(target.parentFile, "$DB_NAME.version")
            val cached = if (stamp.exists()) stamp.readText().trim() else ""
            if (!target.exists() || cached != ASSET_VERSION) {
                // 生词本与预置库同库，覆盖前先捞出旧数据，复制完再写回，否则升级词库会清空生词本。
                val saved = readWordbook(target)
                copyAsset(target)
                if (saved.isNotEmpty()) restoreWordbook(target, saved)
                stamp.writeText(ASSET_VERSION)
            }
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE)
        }.onFailure {
            Log.e(TAG, "预置词库打开失败", it)
        }.getOrNull()
    }

    private fun copyAsset(target: File) {
        val tmp = File(target.parentFile, "$DB_NAME.tmp")
        context.assets.open("content/english/$DB_NAME").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            tmp.delete()
        }
    }

    private fun readWordbook(target: File): List<Pair<String, Long>> {
        if (!target.exists()) return emptyList()
        return runCatching {
            val out = mutableListOf<Pair<String, Long>>()
            val old = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                old.rawQuery("SELECT headword, added_at FROM wordbook", null).use { c ->
                    while (c.moveToNext()) out += c.getString(0) to c.getLong(1)
                }
            } finally {
                old.close()
            }
            out
        }.onFailure {
            Log.w(TAG, "旧版生词本读取失败，跳过迁移", it)
        }.getOrDefault(emptyList())
    }

    private fun restoreWordbook(target: File, saved: List<Pair<String, Long>>) {
        runCatching {
            val database = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                database.beginTransaction()
                try {
                    for ((headword, addedAt) in saved) {
                        database.insertWithOnConflict(
                            "wordbook",
                            null,
                            ContentValues().apply {
                                put("headword", headword)
                                put("added_at", addedAt)
                            },
                            SQLiteDatabase.CONFLICT_IGNORE,
                        )
                    }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
            } finally {
                database.close()
            }
        }.onFailure {
            Log.w(TAG, "生词本迁移失败", it)
        }
    }

    fun search(rawQuery: String, savedOnly: Boolean = false, limit: Int = 60): List<DictEntry> {
        val database = db ?: return emptyList()
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return if (savedOnly) savedEntries(limit) else browse(limit)
        }

        val key = query.lowercase()
        val out = LinkedHashMap<String, DictEntry>()

        // 1. 完全匹配
        runCatching {
            database.rawQuery(
                "$COLUMNS FROM entry WHERE word = ? LIMIT 5",
                arrayOf(key),
            ).use { c ->
                while (c.moveToNext()) {
                    val entry = c.entry()
                    // 键统一转小写去重：Minecraft 这类大写词在精确和前缀两步会各命中一次
                    out[entry.headword.lowercase()] = entry
                }
            }
        }

        // 2. 前缀：用 word 的 NOCASE 索引做范围扫描，比 LIKE '%x%' 快一个数量级
        if (out.size < limit) {
            runCatching {
                database.rawQuery(
                    """
                    $COLUMNS FROM entry
                    WHERE word >= ? AND word < ?
                    ORDER BY $RANK, length(word), word
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(key, key + "\uFFFF", limit.toString()),
                ).use { c -> while (c.moveToNext()) out.getOrPut(c.getString(0).lowercase()) { c.entry() } }
            }
        }

        // 3. 兜底：包含匹配，兼顾中文反查（"光合作用" → photosynthesis）
        if (out.size < limit && query.length >= 2) {
            runCatching {
                database.rawQuery(
                    """
                    $COLUMNS FROM entry
                    WHERE word LIKE ? OR translation LIKE ?
                    ORDER BY $RANK, length(word)
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf("%$key%", "%$query%", (limit - out.size).toString()),
                ).use { c -> while (c.moveToNext()) out.getOrPut(c.getString(0).lowercase()) { c.entry() } }
            }
        }

        return out.values.take(limit)
    }

    /**
     * 空查询时的默认列表。全量库若按字母序排，首页会是 `'hood`、`-ability` 这类符号开头的长尾词，
     * 所以改成按 BNC 词频排名展示常用词；`bnc` 有索引，`bnc > 0` 走范围扫描，取前 60 条即可。
     */
    fun browse(limit: Int = 60): List<DictEntry> = query(
        "$COLUMNS FROM entry WHERE bnc > 0 ORDER BY bnc, word LIMIT ?",
        arrayOf(limit.toString()),
    )

    // 全量 77 万条下 count(*) 也要扫一遍索引，缓存起来，避免每次输入都重算。
    @Volatile
    private var cachedTotal = -1

    fun totalCount(): Int {
        if (cachedTotal >= 0) return cachedTotal
        val count = runCatching {
            db?.rawQuery("SELECT count(*) FROM entry", null)?.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            } ?: 0
        }.getOrDefault(0)
        if (count > 0) cachedTotal = count
        return count
    }

    fun savedWords(): Set<String> {
        val set = mutableSetOf<String>()
        runCatching {
            db?.rawQuery("SELECT headword FROM wordbook", null)?.use { c ->
                while (c.moveToNext()) set += c.getString(0)
            }
        }
        return set
    }

    fun toggleSaved(headword: String): Boolean {
        val database = db ?: return false
        val exists = runCatching {
            database.rawQuery("SELECT 1 FROM wordbook WHERE headword = ?", arrayOf(headword))
                .use { it.moveToFirst() }
        }.getOrDefault(false)

        return runCatching {
            if (exists) {
                database.delete("wordbook", "headword = ?", arrayOf(headword))
                false
            } else {
                database.insertWithOnConflict(
                    "wordbook",
                    null,
                    ContentValues().apply {
                        put("headword", headword)
                        put("added_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                true
            }
        }.getOrDefault(false)
    }

    private fun savedEntries(limit: Int): List<DictEntry> = query(
        """
        SELECT d.word, d.phonetic, d.translation, d.definition, d.tag, d.exchange
        FROM wordbook w JOIN entry d ON d.word = w.headword COLLATE NOCASE
        ORDER BY w.added_at DESC LIMIT ?
        """.trimIndent(),
        arrayOf(limit.toString()),
    )

    private fun query(sql: String, args: Array<String>): List<DictEntry> {
        val database = db ?: return emptyList()
        val out = mutableListOf<DictEntry>()
        runCatching {
            database.rawQuery(sql, args).use { c -> while (c.moveToNext()) out += c.entry() }
        }
        return out
    }

    private fun Cursor.entry() = DictEntry(
        headword = getString(0) ?: "",
        phonetic = getString(1) ?: "",
        translation = getString(2) ?: "",
        definition = getString(3) ?: "",
        tag = getString(4) ?: "",
        exchange = getString(5) ?: "",
    )

    private companion object {
        const val TAG = "DictionaryRepository"
        const val DB_NAME = "dict.db"
        const val ASSET_VERSION = "4"
        const val COLUMNS = "SELECT word, phonetic, translation, definition, tag, exchange"
        /** 有词频/考试标签的词优先，其余（短语、专业术语）按词长排，避免长尾条目淹没常用词。 */
        const val RANK = ("CASE WHEN bnc > 0 THEN bnc WHEN frq > 0 THEN frq + 100000 " +
            "WHEN tag <> '' THEN 200000 ELSE 1000000 END")
    }
}
