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
 * 一次检索的结果。
 *
 * [entries] 是词条本身的命中（精确、别名、前缀、拼音），[related] 是释义主干里提到查询词的词条，
 * 单独放在一组、由界面折叠展示——搜「苹果」时「咬」「个」「蔫」只是例句里出现过苹果，
 * 不该和真正的词条混在一起占满列表。
 */
data class ChineseSearchResult(
    val entries: List<ChineseDictEntry> = emptyList(),
    val related: List<ChineseDictEntry> = emptyList(),
)

/**
 * 离线汉语词库。
 *
 * 数据来自 ModernChineseDict（https://github.com/pampas9000/ModernChineseDict）的
 * 《现代汉语词典》第 7 版 mdx 资源，构建期用 `tools/dictionary/build_zh_dict_db.py`
 * 把解包后的 7 万条词条摊平成预置 SQLite，随包发布。繁体、异体和 @@@LINK 重定向
 * 都收进 alias 表，查「醜」也能落到「丑²」。
 *
 * 查询顺序：精确 → 别名 → 前缀 → 拼音 → 释义反查。前四步都走索引；释义反查不再用
 * `LIKE '%词%'` 扫全表，而是用构建期生成的 `def_gram` 倒排表（汉字二元组 → 词条 id），
 * 取查询词各二元组的交集后回表校验，7 万条规模基本在 1–5ms。
 * 释义反查只匹配 `definition_main`（去掉「：」后面的例句），所以「搜苹果出来一堆造句」
 * 的情况不会再有；结果另作一组展示，不与词条命中混排。
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

    fun search(rawQuery: String, savedOnly: Boolean = false, limit: Int = 60): ChineseSearchResult {
        val database = db ?: return ChineseSearchResult()
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return ChineseSearchResult(
                entries = if (savedOnly) savedEntries() else browse(limit),
            )
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

        val entries = out.values.take(limit)
        // 5. 释义反查：只在词条命中不足一页时给补充结果，且单独成组、固定上限。
        //    走 def_gram 倒排表，不再全表 LIKE；例句已从 definition_main 里剥掉。
        val related = if (!savedOnly && entries.size < limit && wordKey.length >= 2) {
            definitionMatches(database, wordKey, RELATED_LIMIT)
        } else {
            emptyList()
        }

        return ChineseSearchResult(entries = entries, related = related)
    }

    /**
     * 释义主干里提到 [query] 的词条。
     *
     * 做法：把查询词切成汉字二元组，从 def_gram 取各自的倒排 id 列表求交集，
     * 再按 400 个一批回表用 instr 校验（三个字以上的查询，二元组交集可能命中
     * 相邻但不连续的文本，必须校验整串），最后按「词条越短越靠前、义项越多越靠前」排序。
     */
    private fun definitionMatches(
        database: SQLiteDatabase,
        query: String,
        limit: Int,
    ): List<ChineseDictEntry> {
        if (!query.all { isCjk(it) }) return emptyList()
        val grams = cjkBigrams(query)
        if (grams.isEmpty()) return emptyList()

        var candidates: Set<Int>? = null
        for (gram in grams) {
            val ids = runCatching {
                database.rawQuery("SELECT ids FROM def_gram WHERE gram = ?", arrayOf(gram))
                    .use { c -> if (c.moveToFirst()) decodeIds(c.getBlob(0)) else emptySet() }
            }.getOrDefault(emptySet())
            val next = candidates?.let { it intersect ids } ?: ids
            if (next.isEmpty()) return emptyList()
            candidates = next
        }
        val ids = candidates?.sorted() ?: return emptyList()

        val ranked = ArrayList<RankedEntry>(minOf(ids.size, limit * 4))
        for (chunk in ids.chunked(IN_CHUNK)) {
            val marks = chunk.joinToString(",") { "?" }
            val args = chunk.map { it.toString() } + query + limit.toString()
            runCatching {
                database.rawQuery(
                    """
                    SELECT id, key, word, pinyin, traditional, variants, definition,
                           length(word_key), length(definition)
                    FROM entry
                    WHERE id IN ($marks) AND instr(definition_main, ?) > 0
                    ORDER BY length(word_key), length(definition) DESC, word_key
                    LIMIT ?
                    """.trimIndent(),
                    args.toTypedArray(),
                ).use { c ->
                    while (c.moveToNext()) {
                        ranked += RankedEntry(c.entry(), c.getInt(7), c.getInt(8))
                    }
                }
            }
        }
        return ranked
            .sortedWith(compareBy({ it.wordKeyLength }, { -it.definitionLength }, { it.entry.key }))
            .take(limit)
            .map { it.entry }
    }

    private data class RankedEntry(
        val entry: ChineseDictEntry,
        val wordKeyLength: Int,
        val definitionLength: Int,
    )

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

    /** 只对汉字做二元组切分；含字母、数字或标点的查询不走释义反查。 */
    private fun cjkBigrams(text: String): List<String> {
        if (text.length < 2) return emptyList()
        val out = LinkedHashSet<String>()
        for (index in 0 until text.length - 1) {
            val first = text[index]
            val second = text[index + 1]
            if (isCjk(first) && isCjk(second)) out += "$first$second"
        }
        return out.toList()
    }

    private fun isCjk(char: Char): Boolean =
        char in '\u3400'..'\u4dbf' || char in '\u4e00'..'\u9fff' || char in '\uf900'..'\ufaff'

    /** def_gram.ids 是升序 delta-varint，解出词条 id。 */
    private fun decodeIds(blob: ByteArray): Set<Int> {
        val ids = LinkedHashSet<Int>(blob.size / 2 + 1)
        var value = 0
        var shift = 0
        var previous = 0
        for (byte in blob) {
            value = value or ((byte.toInt() and 0x7F) shl shift)
            if (byte.toInt() and 0x80 != 0) {
                shift += 7
            } else {
                previous += value
                ids += previous
                value = 0
                shift = 0
            }
        }
        return ids
    }

    private fun normalizePinyin(raw: String): String {
        val text = raw.replace('ɡ', 'g').replace('ü', 'v').replace('Ü', 'V')
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.filter { it in 'a'..'z' || it in 'A'..'Z' }.lowercase()
    }

    private companion object {
        const val TAG = "ChineseDictionaryRepo"
        const val DB_NAME = "zhdict.db"

        /** 词库 schema 版本：2 起带 definition_main 与 def_gram 倒排表，旧包需要重新复制 assets。 */
        const val ASSET_VERSION = "2"
        const val ENTRY_COLUMNS = "id, key, word, pinyin, traditional, variants, definition"

        /** 释义反查最多给几条：再多也只是重复「某字释义里提过这个词」，没有查阅价值。 */
        const val RELATED_LIMIT = 12

        /** 回表校验时单条 SQL 的 id 数量上限，避开 SQLite 的变量个数限制。 */
        const val IN_CHUNK = 400
    }
}
