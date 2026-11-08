package cn.stb.feature.poetry

import cn.stb.core.data.AssetReader
import org.json.JSONArray

data class PoemNote(val term: String, val explain: String)

data class Poem(
    val id: String,
    val title: String,
    val author: String,
    val dynasty: String,
    val lines: List<String>,
    val notes: List<PoemNote>,
    val appreciation: String,
) {
    val firstLine: String get() = lines.firstOrNull().orEmpty()
}

data class PoemBook(
    val id: String,
    val title: String,
    val poems: List<Poem>,
)

class PoetryRepository(private val assets: AssetReader) {

    private var cached: List<PoemBook>? = null

    fun books(): List<PoemBook> = cached ?: load().also { cached = it }

    private fun load(): List<PoemBook> {
        val root = assets.jsonObject("content/chinese/poems.json")
        val books = root.optJSONArray("books") ?: JSONArray()
        return buildList {
            for (i in 0 until books.length()) {
                val b = books.getJSONObject(i)
                val poems = b.optJSONArray("poems") ?: JSONArray()
                add(
                    PoemBook(
                        id = b.getString("id"),
                        title = b.getString("title"),
                        poems = buildList {
                            for (j in 0 until poems.length()) {
                                add(poems.getJSONObject(j).toPoem())
                            }
                        },
                    )
                )
            }
        }
    }
}

private fun org.json.JSONObject.toPoem(): Poem {
    val rawLines = optJSONArray("lines") ?: JSONArray()
    val lines = buildList { for (i in 0 until rawLines.length()) add(rawLines.getString(i)) }

    val rawNotes = optJSONArray("notes") ?: JSONArray()
    val notes = buildList {
        for (i in 0 until rawNotes.length()) {
            val n = rawNotes.getJSONObject(i)
            add(PoemNote(n.optString("term"), n.optString("explain")))
        }
    }

    return Poem(
        id = optString("id"),
        title = optString("title"),
        author = optString("author"),
        dynasty = optString("dynasty"),
        lines = lines,
        notes = notes,
        appreciation = optString("appreciation"),
    )
}
