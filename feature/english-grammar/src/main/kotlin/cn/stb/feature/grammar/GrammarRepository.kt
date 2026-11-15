package cn.stb.feature.grammar

import cn.stb.core.data.AssetReader
import org.json.JSONArray
import org.json.JSONObject

/**
 * 语法正文的块结构。
 *
 * 原稿是 Markdown，但这份讲义只用到标题、段落、引用、代码块和表格五种块，
 * 转换脚本见 tools/grammar/build_grammar_json.py，这里按块做原生排版。
 */
sealed interface GrammarBlock {
    data class Heading(val level: Int, val text: String) : GrammarBlock
    data class Paragraph(val text: String) : GrammarBlock
    data class Quote(val text: String) : GrammarBlock
    data class Code(val text: String) : GrammarBlock
    data class Table(val head: List<String>, val rows: List<List<String>>) : GrammarBlock
}

data class GrammarArticle(
    val id: String,
    val title: String,
    val navTitle: String,
    val category: String,
    val blocks: List<GrammarBlock>,
    /** 去掉标记后的全文，搜索和摘要都用它 */
    val bodyText: String,
)

data class GrammarCategory(
    val title: String,
    val articles: List<GrammarArticle>,
)

data class GrammarBook(
    val title: String,
    val categories: List<GrammarCategory>,
) {
    val articles: List<GrammarArticle> = categories.flatMap { it.articles }
}

data class GrammarHit(
    val article: GrammarArticle,
    val snippet: String,
)

class GrammarRepository(private val assets: AssetReader) {

    private var cached: GrammarBook? = null

    fun book(): GrammarBook = cached ?: load().also { cached = it }

    private fun load(): GrammarBook {
        val root = assets.jsonObject(PATH)
        val categoryArray = root.optJSONArray("categories") ?: JSONArray()
        val categories = buildList {
            for (i in 0 until categoryArray.length()) {
                val category = categoryArray.getJSONObject(i)
                val title = category.optString("title")
                val articleArray = category.optJSONArray("articles") ?: JSONArray()
                val articles = buildList {
                    for (j in 0 until articleArray.length()) {
                        add(articleArray.getJSONObject(j).toArticle(title))
                    }
                }
                add(GrammarCategory(title, articles))
            }
        }
        return GrammarBook(root.optString("title", "英语语法"), categories)
    }

    private companion object {
        const val PATH = "content/english/grammar.json"
    }
}

/** 按标题与正文检索，返回带上下文的摘要。 */
fun GrammarBook.search(raw: String): List<GrammarHit> {
    val query = raw.trim()
    if (query.isEmpty()) return emptyList()

    return articles.mapNotNull { article ->
        val inTitle = article.navTitle.contains(query, ignoreCase = true) ||
            article.title.contains(query, ignoreCase = true) ||
            article.category.contains(query, ignoreCase = true)
        val at = article.bodyText.indexOf(query, ignoreCase = true)
        if (!inTitle && at < 0) return@mapNotNull null

        val snippet = if (at >= 0) {
            article.bodyText.snippet(at, query.length)
        } else {
            article.bodyText.take(70)
        }
        GrammarHit(article, snippet)
    }.sortedBy { hit ->
        if (hit.article.navTitle.contains(query, ignoreCase = true)) 0 else 1
    }
}

private fun JSONObject.toArticle(category: String): GrammarArticle {
    val blockArray = optJSONArray("blocks") ?: JSONArray()
    val blocks = buildList {
        for (i in 0 until blockArray.length()) {
            val block = blockArray.getJSONObject(i)
            when (block.optString("type")) {
                "h" -> add(GrammarBlock.Heading(block.optInt("level", 2), block.optString("text")))
                "p" -> add(GrammarBlock.Paragraph(block.optString("text")))
                "quote" -> add(GrammarBlock.Quote(block.optString("text")))
                "code" -> add(GrammarBlock.Code(block.optString("text")))
                "table" -> add(
                    GrammarBlock.Table(
                        head = (block.optJSONArray("head") ?: JSONArray()).toStringList(),
                        rows = (block.optJSONArray("rows") ?: JSONArray()).let { rows ->
                            buildList {
                                for (r in 0 until rows.length()) {
                                    add((rows.optJSONArray(r) ?: JSONArray()).toStringList())
                                }
                            }
                        },
                    )
                )
            }
        }
    }

    val title = optString("title")
    return GrammarArticle(
        id = optString("id"),
        title = title,
        navTitle = optString("navTitle").ifBlank { title },
        category = category,
        blocks = blocks,
        bodyText = blocks.plainText(),
    )
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) add(optString(i))
}

private fun List<GrammarBlock>.plainText(): String {
    val text = joinToString("\n") { block ->
        when (block) {
            is GrammarBlock.Heading -> block.text
            is GrammarBlock.Paragraph -> block.text
            is GrammarBlock.Quote -> block.text
            is GrammarBlock.Code -> block.text
            is GrammarBlock.Table -> (block.head + block.rows.flatten()).joinToString("\n")
        }
    }
    return text.replace("**", "").replace(Regex("\\s+"), " ").trim()
}

private fun String.snippet(index: Int, length: Int): String {
    val start = (index - 28).coerceAtLeast(0)
    val end = (index + length + 48).coerceAtMost(this.length)
    val head = if (start > 0) "…" else ""
    val tail = if (end < this.length) "…" else ""
    return head + substring(start, end) + tail
}
