package cn.stb.core.model

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 选一个「好看」的刻度间隔：1/2/5 × 10^n。
 *
 * 数学的坐标轴和地理的气温轴都要用，所以放在 core 里，避免两处各写一份。
 */
fun niceStep(range: Double, targetTicks: Int): Double {
    if (range <= 0 || targetTicks <= 0) return 1.0
    val rough = range / targetTicks
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    val step = when {
        normalized < 1.5 -> 1.0
        normalized < 3 -> 2.0
        normalized < 7 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}
