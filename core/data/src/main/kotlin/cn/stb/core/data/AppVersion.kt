package cn.stb.core.data

import android.content.Context
import android.content.pm.PackageInfo

/**
 * 运行时读取本应用的版本信息。
 *
 * 版本号只有一个来源：`app/build.gradle.kts` 的 `defaultConfig.versionName / versionCode`。
 * 界面里任何地方展示版本、或者比较「当前版本与上次看过的版本」，都必须经过这里，
 * 不允许再出现硬编码的 "1.0.0"——否则发版时总有一处忘了改。
 */
object AppVersion {

    /** 全新安装判定窗口：首次安装时间与最后更新时间相差小于 60 秒，视为「刚装上」。 */
    private const val FRESH_INSTALL_WINDOW_MS = 60_000L

    /** 读不到包信息时的版本名兜底（正常情况下不会发生）。 */
    const val UNKNOWN_NAME = "未知"

    /** 当前版本名，如 "1.0.1"。 */
    fun name(context: Context): String =
        packageInfo(context)?.versionName?.takeIf { it.isNotBlank() } ?: UNKNOWN_NAME

    /**
     * 当前 versionCode，如 2。
     *
     * `PackageInfo.versionCode` 在 API 28 之后被 `longVersionCode` 取代，但前者在 minSdk 26
     * 上仍然可用，且本项目 versionCode 远小于 Int 上限，直接用旧字段最省事。
     */
    @Suppress("DEPRECATION")
    fun code(context: Context): Int = packageInfo(context)?.versionCode ?: 0

    /**
     * 是不是全新安装（App 第一次装到这台设备上）。
     *
     * 判定依据是系统记录的 [PackageInfo.firstInstallTime] 与 [PackageInfo.lastUpdateTime]：
     * 全新安装时两者几乎同时写入（相差 0），覆盖升级时 lastUpdateTime 会明显晚于 firstInstallTime。
     * 取 60 秒容差是为了躲开「安装器先写安装时间、再写更新时间」这类时钟粒度问题。
     *
     * 用途：首次安装不该看到「已从 vX 更新至 vY」的升级提示；
     * 而老用户从旧版本升级上来时，即使本地还没记录过 lastSeenVersion，也应该看到提示。
     */
    fun isFreshInstall(context: Context): Boolean {
        val info = packageInfo(context) ?: return false
        val elapsed = kotlin.math.abs(info.lastUpdateTime - info.firstInstallTime)
        return elapsed < FRESH_INSTALL_WINDOW_MS
    }

    private fun packageInfo(context: Context): PackageInfo? =
        runCatching {
            context.applicationContext.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
}
