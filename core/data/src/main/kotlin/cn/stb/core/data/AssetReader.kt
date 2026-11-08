package cn.stb.core.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 离线内容入口。所有教材、词库、方程式、图表数据都从这里读，进程内做一层缓存。
 */
class AssetReader(context: Context) {

    private val assets = context.applicationContext.assets
    private val cache = mutableMapOf<String, String>()

    fun rawText(path: String): String = cache.getOrPut(path) {
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun jsonObject(path: String): JSONObject = JSONObject(rawText(path))

    fun jsonArray(path: String): JSONArray = JSONArray(rawText(path))
}

/** 内容清单：版本号与各数据集的形态，供「关于」页与完整性提示使用。 */
data class ContentManifest(
    val contentVersion: Int,
    val builtAt: String,
    val datasets: List<Dataset>,
) {
    data class Dataset(val id: String, val version: Int, val path: String, val bytes: Long)
}

fun AssetReader.manifest(): ContentManifest {
    val root = jsonObject("content/manifest.json")
    val array = root.optJSONArray("datasets") ?: JSONArray()
    val list = buildList {
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            add(
                ContentManifest.Dataset(
                    id = o.getString("id"),
                    version = o.optInt("version", 1),
                    path = o.getString("path"),
                    bytes = o.optLong("bytes", 0),
                )
            )
        }
    }
    return ContentManifest(
        contentVersion = root.optInt("contentVersion", 1),
        builtAt = root.optString("builtAt", "—"),
        datasets = list,
    )
}
