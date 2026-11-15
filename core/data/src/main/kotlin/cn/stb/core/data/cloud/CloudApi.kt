package cn.stb.core.data.cloud

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极小的 HTTP 客户端。
 *
 * 不引入 OkHttp：请求只有 6 个固定接口，HttpURLConnection 足够，也避免给离线应用
 * 再添一个第三方网络栈。所有调用都必须发生在 IO 线程。
 *
 * 默认地址可以是 HTTP；生产环境由服务端返回 301/302/307/308 跳到 HTTPS。
 * 这里手动处理重定向，避免 HttpURLConnection 对 POST 的默认行为（把 POST 变成 GET
 * 或丢掉请求体）。只允许 http/https，且禁止 HTTPS 降级到 HTTP。
 */
class CloudApi(
    baseUrl: String,
    private val apiKey: String,
    private val userAgent: String,
) {
    private val base = baseUrl.trimEnd('/')

    data class Response(val status: Int, val body: String) {
        val ok: Boolean get() = status in 200..299
    }

    fun get(path: String): Response = request("GET", path, null)

    fun post(path: String, body: JSONObject): Response = request("POST", path, body.toString())

    private fun request(method: String, path: String, body: String?): Response {
        var url = runCatching { URL(base + path) }.getOrNull()
            ?: return Response(0, "无效的请求地址")
        var redirects = 0

        while (true) {
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                    // 手动处理，保证 POST 跳转时不丢 body。
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", userAgent)
                    if (apiKey.isNotBlank()) setRequestProperty("X-STB-Key", apiKey)
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                }
                if (body != null) {
                    connection.outputStream.use { stream ->
                        stream.write(body.toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val location = connection.getHeaderField("Location")
                if (status in REDIRECT_CODES && !location.isNullOrBlank()) {
                    if (redirects >= MAX_REDIRECTS) {
                        return Response(0, "重定向次数过多")
                    }
                    val next = runCatching { URL(url, location) }.getOrNull()
                        ?: return Response(0, "服务端返回了无效的重定向地址")
                    if (next.protocol != "http" && next.protocol != "https") {
                        return Response(0, "不支持的重定向协议：${next.protocol}")
                    }
                    if (url.protocol == "https" && next.protocol != "https") {
                        return Response(0, "拒绝从 HTTPS 降级到 HTTP")
                    }
                    url = next
                    redirects += 1
                    continue
                }
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                return Response(status, text)
            } catch (error: IOException) {
                return Response(0, error.message.orEmpty())
            } catch (error: Exception) {
                return Response(0, error.message.orEmpty())
            } finally {
                connection?.disconnect()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
