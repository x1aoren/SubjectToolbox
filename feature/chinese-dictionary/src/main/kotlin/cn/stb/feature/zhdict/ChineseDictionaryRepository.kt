package cn.stb.feature.zhdict

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.text.Normalizer

/** 一个词条。字段对应预置库 entry 表里界面需要的部分。 */
data class ChineseDictEntry(
    val key: String,
    val word: String,
    val pinyin: String,
    val traditional: String,
    val variants: String,
    val definition: String,
)

/**
 * 离线汉语词库。
 *
 * 数据来自 ModernChineseDict（https://github.com/pampas9000/ModernChineseDict）的
 * 《现代汉语词典》第 7 版 mdx 资源，构建期用 `tools/dictionary/build_zh_dict_db.py`
 * 把解包后的 7 万条词条摊平成预置 SQLite，随包发布。繁体、异体和 @@@LINK 重定向
 * 都收进 alias 表，查「醜」也能落到「丑²」。
 *
 * 查询顺序和英语词典一致：精确 → 前缀 → 拼音 → 包含。word_key 去掉了区分同形条目的
 * 上标数字并建了索引，前缀范围扫描不扫全表；释义与拼音的模糊匹配放在 IO 线程 + 输入防抖。
 */
class ChineseDictionaryRepository(private val context: Context) {

    private val db: SQLiteDatabase? by lazy { openPrebuilt() }

    private fun openPrebuilt(): SQLiteDatabase? {
        val target = context.getDatabasePath(DB_NAME)
        return runCatching {
            target.parentFile?.mkdirs()
            val stamp = File(target.parentFile, "$DB_NAME.version")
            val cached = if (stamp.exists()) stamp.readText().trim() else ""
            if (!target.exists() || cached != ASSET_VERSION) {
                // 生词本和预置库同库，覆盖前先读出来，复制完再写回。
                val saved = readWordbook(target)
                copyAsset(target)
                if (saved.isNotEmpty()) restoreWordbook(target, saved)
                stamp.writeText(ASSET_VERSION)
            }
            SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE)
        }.onFailure {
            Log.e(TAG, "预置汉语词库打开失败", it)
        }.getOrNull()
    }

    private fun copyAsset(target: File) {
        val tmp = File(target.parentFile, "$DB_NAME.tmp")
        context.assets.open("content/chinese/$DB_NAME").use { input ->
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
                old.rawQuery("SELECT key, added_at FROM wordbook", null).use { c ->
                    while (c.moveToNext()) out += c.getString(0) to c.getLong(1)
                }
            } finally {
                old.close()
            }
            out
        }.onFailure {
            Log.w(TAG, "旧版汉语生词本读取失败，跳过迁移", it)
        }.getOrDefault(emptyList())
    }

    private fun restoreWordbook(target: File, saved: List<Pair<String, Long>>) {
        runCatching {
            val database = SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                database.beginTransaction()
                try {
                    for ((key, addedAt) in saved) {
                        database.insertWithOnConflict(
                            "wordbook",
                            null,
                            ContentValues().apply {
                                put("key", key)
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
            Log.w(TAG, "汉语生词本迁移失败", it)
        }
    }

    fun search(rawQuery: String, savedOnly: Boolean = false, limit: Int = 60): List<ChineseDictEntry> {
        val database = db ?: return emptyList()
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return if (savedOnly) savedEntries() else browse(limit)
        }

        val wordKey = query.filterNot { it.isWhitespace() }
        val out = LinkedHashMap<Long, ChineseDictEntry>()

        // 1. 词形精确匹配。同形多音字按释义长度排，把义项多的常用读音放前面（打 dǎ 在 dá 前）
        runCatching {
            database.rawQuery(
                "SELECT $ENTRY_COLUMNS FROM entry WHERE word_key = ? " +
                    "ORDER BY length(definition) DESC, id LIMIT 20",
                arrayOf(wordKey),
            ).use { c -> c.collectInto(out) }
        }

        // 2. 繁体 / 异体 / 重定向别名
        if (out.size < limit) {
            runCatching {
                database.rawQuery(
                    """
                    SELECT e.id, e.key, e.word, e.pinyin, e.traditional, e.variants, e.definition
                    FROM alias a JOIN entry e ON e.id = a.entry_id
                    WHERE a.name = ? LIMIT 20
                    """.trimIndent(),
                    arrayOf(wordKey),
                ).use { c -> c.collectInto(out) }
            }
        }

        // 3. 前缀：word_key 有索引，范围扫描比 LIKE '%x%' 快一个数量级
        if (out.size < limit) {
            runCatching {
                database.rawQuery(
                    """
                    SELECT $ENTRY_COLUMNS FROM entry
                    WHERE word_key >= ? AND word_key < ?
                    ORDER BY length(word_key), length(definition) DESC, word_key
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(wordKey, wordKey + "\uFFFF", limit.toString()),
                ).use { c -> c.collectInto(out) }
            }
        }

        // 4. 拼音：shui → 水、睡、税；支持不带声调的整串前缀
        val pinyin = normalizePinyin(query)
        if (out.size < limit && pinyin.length >= 2) {
            runCatching {
                database.rawQuery(
                    """
                    SELECT $ENTRY_COLUMNS FROM entry
                    WHERE pinyin_search >= ? AND pinyin_search < ?
                    ORDER BY length(word_key), length(definition) DESC, word_key
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(pinyin, pinyin + "\uFFFF", (limit - out.size).toString()),
                ).use { c -> c.collectInto(out) }
            }
        }

        // 5. 兜底：词形或释义包含。单字查询前缀已覆盖，只有两个字符以上或含非 ASCII 时才扫。
        if (out.size < limit && (query.length >= 2 || query.any { it.code > 0x7F })) {
            runCatching {
                val pattern = likePattern(query)
                database.rawQuery(
                    """
                    SELECT $ENTRY_COLUMNS FROM entry
                    WHERE word_key LIKE ? ESCAPE '\' OR definition LIKE ? ESCAPE '\'
                    ORDER BY length(word_key), length(definition) DESC, word_key
                    LIMIT ?
                    """.trimIndent(),
                    arrayOf(pattern, pattern, (limit - out.size).toString()),
                ).use { c -> c.collectInto(out) }
            }
        }

        return out.values.take(limit)
    }

    /**
     * 空查询时的默认列表。
     *
     * 没有词频数据，用「释义长度」当常用度：单字词条里释义越长的义项越多，
     * 排出来正好是「把、下、的、就、是」这类常用字，比按 Unicode 顺序列生僻字合理。
     */
    fun browse(limit: Int = 60): List<ChineseDictEntry> = query(
        "SELECT $ENTRY_COLUMNS FROM entry WHERE length(word_key) = 1 " +
            "ORDER BY length(definition) DESC, word_key LIMIT ?",
        arrayOf(limit.toString()),
    )

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

    fun savedKeys(): Set<String> {
        val set = mutableSetOf<String>()
        runCatching {
            db?.rawQuery("SELECT key FROM wordbook", null)?.use { c ->
                while (c.moveToNext()) set += c.getString(0)
            }
        }
        return set
    }

    fun toggleSaved(key: String): Boolean {
        val database = db ?: return false
        val exists = runCatching {
            database.rawQuery("SELECT 1 FROM wordbook WHERE key = ?", arrayOf(key))
                .use { it.moveToFirst() }
        }.getOrDefault(false)

        return runCatching {
            if (exists) {
                database.delete("wordbook", "key = ?", arrayOf(key))
                false
            } else {
                database.insertWithOnConflict(
                    "wordbook",
                    null,
                    ContentValues().apply {
                        put("key", key)
                        put("added_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                true
            }
        }.getOrDefault(false)
    }

    /** 生词本是用户自己攒的，不设上限，全量列出。 */
    private fun savedEntries(): List<ChineseDictEntry> = query(
        """
        SELECT e.id, e.key, e.word, e.pinyin, e.traditional, e.variants, e.definition
        FROM wordbook w JOIN entry e ON e.key = w.key
        ORDER BY w.added_at DESC
        """.trimIndent(),
        emptyArray(),
    )

    private fun query(sql: String, args: Array<String>): List<ChineseDictEntry> {
        val database = db ?: return emptyList()
        val out = mutableListOf<ChineseDictEntry>()
        runCatching {
            database.rawQuery(sql, args).use { c -> while (c.moveToNext()) out += c.entry() }
        }.onFailure {
            Log.w(TAG, "汉语词库查询失败", it)
        }
        return out
    }

    private fun Cursor.collectInto(out: LinkedHashMap<Long, ChineseDictEntry>) {
        while (moveToNext()) {
            val entry = entry()
            out.getOrPut(getLong(0)) { entry }
        }
    }

    private fun Cursor.entry() = ChineseDictEntry(
        key = getString(1) ?: "",
        word = getString(2) ?: "",
        pinyin = getString(3) ?: "",
        traditional = getString(4) ?: "",
        variants = getString(5) ?: "",
        definition = getString(6) ?: "",
    )

    private fun likePattern(raw: String): String =
        "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun normalizePinyin(raw: String): String {
        val text = raw.replace('ɡ', 'g').replace('ü', 'v').replace('Ü', 'V')
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.filter { it in 'a'..'z' || it in 'A'..'Z' }.lowercase()
    }

    private companion object {
        const val TAG = "ChineseDictionaryRepo"
        const val DB_NAME = "zhdict.db"
        const val ASSET_VERSION = "1"
        const val ENTRY_COLUMNS = "id, key, word, pinyin, traditional, variants, definition"
    }
}
