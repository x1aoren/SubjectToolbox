package cn.stb.core.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import cn.stb.core.model.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class UsageDb(context: Context) :
    SQLiteOpenHelper(context, "usage.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE usage(
                tool_id TEXT PRIMARY KEY,
                last_open INTEGER NOT NULL,
                open_count INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * 最近使用记录。上限 50 条，读取时只取前若干条。
 *
 * 与内容库完全无关：即使把教材数据整包替换，学习痕迹也不会丢。
 */
class UsageRepositoryImpl(context: Context) : UsageRepository {

    private val helper = UsageDb(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _recents = MutableStateFlow<List<String>>(emptyList())
    override val recents: StateFlow<List<String>> = _recents.asStateFlow()

    init {
        scope.launch { _recents.value = query() }
    }

    override suspend fun recordOpen(toolId: String) {
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            val now = System.currentTimeMillis()
            val updated = db.compileStatement(
                "UPDATE usage SET last_open = ?, open_count = open_count + 1 WHERE tool_id = ?"
            ).use { st ->
                st.bindLong(1, now)
                st.bindString(2, toolId)
                st.executeUpdateDelete()
            }
            if (updated == 0) {
                db.insertWithOnConflict(
                    "usage",
                    null,
                    ContentValues().apply {
                        put("tool_id", toolId)
                        put("last_open", now)
                        put("open_count", 1)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            _recents.value = query()
        }
    }

    override suspend fun clearRecents() {
        withContext(Dispatchers.IO) {
            helper.writableDatabase.delete("usage", null, null)
            _recents.value = emptyList()
        }
    }

    private fun query(): List<String> {
        val result = mutableListOf<String>()
        helper.readableDatabase.rawQuery(
            "SELECT tool_id FROM usage ORDER BY last_open DESC LIMIT 50",
            null,
        ).use { c ->
            while (c.moveToNext()) result += c.getString(0)
        }
        return result
    }
}
