package cn.stb.core.data.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

/**
 * 设备 SN。
 *
 * Android 10 起普通应用读不到硬件序列号（`Build.getSerial()` 会抛 SecurityException），
 * 所以按下面的顺序尽力取一个稳定标识：
 *
 * 1. `Build.getSerial()`（仅部分系统/旧版本可用）；
 * 2. 系统属性 `ro.serialno`（受隐藏 API 限制，失败就跳过）；
 * 3. `Settings.Secure.ANDROID_ID`（同一设备+签名+用户下稳定，前缀 STB-AID-）；
 * 4. 首次生成随机 UUID，并持久化，保证卸载前不变。
 *
 * 学校如果用 MDM 预置真实 SN，后续可以在这里接入 RestrictionsManager；
 * 现在不再向任何第三方发送原始 Android ID。
 */
class DeviceIdentity(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("stb_device_identity", Context.MODE_PRIVATE)

    /** 已经解析并持久化的 SN；同一安装周期内不会变化。 */
    val sn: String = resolve(context.applicationContext)

    private fun resolve(context: Context): String {
        prefs.getString(KEY_SN, null)?.takeIf { isValid(it) }?.let { return it }

        val candidates = listOfNotNull(
            hardwareSerial(),
            systemPropertySerial(),
            androidId(context),
            randomFallback(),
        )
        val resolved = candidates
            .map(::normalize)
            .firstOrNull(::isValid)
            ?: normalize(randomFallback())

        prefs.edit().putString(KEY_SN, resolved).apply()
        return resolved
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun hardwareSerial(): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial() else null
    }.getOrNull()

    @SuppressLint("PrivateApi")
    private fun systemPropertySerial(): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, "ro.serialno") as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    @SuppressLint("HardwareIds")
    private fun androidId(context: Context): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
        ?.let { "STB-AID-$it" }

    private fun randomFallback(): String =
        "STB-${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun normalize(raw: String): String =
        raw.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9._:-]"), "")
            .take(MAX_LENGTH)

    private fun isValid(value: String): Boolean =
        value.length in 6..MAX_LENGTH &&
            value !in INVALID_VALUES &&
            !value.all { it == '0' }

    private companion object {
        const val KEY_SN = "device_sn"
        const val MAX_LENGTH = 64
        val INVALID_VALUES = setOf(
            "UNKNOWN",
            "UNKNOWN_SERIAL",
            "0123456789ABCDEF",
            "000000000000000",
            "N/A",
            "NONE",
        )
    }
}
