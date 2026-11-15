package cn.stb.core.data.cloud

import android.content.Context
import java.io.File

/**
 * 遥测事件的本地队列。
 *
 * 只保留有限大小：最多 800 条或 512 KB，超出后丢弃最旧的事件。
 * 所有写盘都发生在调用方指定的 IO 线程，队列本身用锁保证并发安全。
 */
class TelemetryQueue(context: Context) {

    private val directory = File(context.filesDir, "telemetry").apply { mkdirs() }
    private val file = File(directory, "queue.jsonl")
    private val lock = Any()

    fun append(line: String): Int = synchronized(lock) {
        if (file.length() > MAX_BYTES) trimLocked()
        val safe = if (line.length > MAX_LINE_CHARS) line.take(MAX_LINE_CHARS) else line
        file.appendText(safe + "\n", Charsets.UTF_8)
        trimLocked()
        countLocked()
    }

    fun peek(limit: Int): List<String> = synchronized(lock) {
        readLinesLocked().take(limit.coerceAtLeast(1))
    }

    fun removeFirst(count: Int): Unit = synchronized(lock) {
        if (count <= 0) return
        val remaining = readLinesLocked().drop(count)
        writeLocked(remaining)
    }

    fun size(): Int = synchronized(lock) { countLocked() }

    fun byteSize(): Long = synchronized(lock) { file.length() }

    fun clear(): Unit = synchronized(lock) {
        runCatching { file.delete() }
    }

    private fun countLocked(): Int = readLinesLocked().size

    private fun readLinesLocked(): List<String> =
        runCatching {
            if (!file.exists()) return emptyList()
            file.readLines(Charsets.UTF_8)
                .filter { it.startsWith("{") && it.endsWith("}") }
        }.getOrDefault(emptyList())

    private fun writeLocked(lines: List<String>) {
        runCatching {
            val text = if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
            val temp = File(directory, "queue.tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        }
    }

    private fun trimLocked() {
        val lines = readLinesLocked()
        if (lines.size <= MAX_EVENTS && file.length() <= MAX_BYTES) return

        var kept = lines.takeLast(TRIM_TO)
        var text = kept.joinToString("\n", postfix = "\n")
        var byteCount = text.toByteArray(Charsets.UTF_8).size
        while (byteCount > MAX_BYTES && kept.size > 50) {
            kept = kept.drop(50)
            text = kept.joinToString("\n", postfix = "\n")
            byteCount = text.toByteArray(Charsets.UTF_8).size
        }
        writeLocked(kept)
    }

    private companion object {
        const val MAX_EVENTS = 800
        const val TRIM_TO = 600
        const val MAX_BYTES = 512 * 1024L
        const val MAX_LINE_CHARS = 8_000
    }
}
