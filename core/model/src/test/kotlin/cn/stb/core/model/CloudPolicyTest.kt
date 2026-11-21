package cn.stb.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CloudPolicyTest {

    @Test
    fun `默认策略的轮询间隔在 30 到 60 秒之间且为整秒`() {
        val policy = CloudPolicy()
        val random = Random(42)
        val intervals = List(200) { policy.revisionPollIntervalMillis(random) }

        intervals.forEach { millis ->
            assertTrue("$millis 应不小于 30 秒", millis >= 30_000L)
            assertTrue("$millis 应不大于 60 秒", millis <= 60_000L)
            assertEquals("必须是整秒", 0L, millis % 1_000L)
        }
        // 固定种子抽样应覆盖多个不同取值，证明是随机取值而不是常量
        assertTrue("应随机出多个不同间隔", intervals.distinct().size > 5)
        assertTrue("应能取到接近上界的值", intervals.any { it > 55_000L })
    }

    @Test
    fun `固定种子结果可复现`() {
        val policy = CloudPolicy()
        val first = List(20) { policy.revisionPollIntervalMillis(Random(2024)) }

        // 每次都用同一种子，序列应完全一致（顺带验证 random 参数确实被使用）
        repeat(3) {
            assertEquals(first, List(20) { policy.revisionPollIntervalMillis(Random(2024)) })
        }
    }

    @Test
    fun `min 小于 10 秒时收敛到 10 秒`() {
        val policy = CloudPolicy(revisionPollMinSeconds = 1, revisionPollMaxSeconds = 5)

        repeat(20) { seed ->
            assertEquals(10_000L, policy.revisionPollIntervalMillis(Random(seed)))
        }
    }

    @Test
    fun `max 大于 300 秒时收敛到 300 秒`() {
        val policy = CloudPolicy(revisionPollMinSeconds = 500, revisionPollMaxSeconds = 900)

        repeat(20) { seed ->
            assertEquals(300_000L, policy.revisionPollIntervalMillis(Random(seed)))
        }
    }

    @Test
    fun `max 小于 min 时退化为 min`() {
        val policy = CloudPolicy(revisionPollMinSeconds = 60, revisionPollMaxSeconds = 20)

        repeat(20) { seed ->
            assertEquals(60_000L, policy.revisionPollIntervalMillis(Random(seed)))
        }
    }

    @Test
    fun `负数配置收敛到下限 10 秒`() {
        val policy = CloudPolicy(revisionPollMinSeconds = -5, revisionPollMaxSeconds = -1)

        repeat(10) { seed ->
            assertEquals(10_000L, policy.revisionPollIntervalMillis(Random(seed)))
        }
    }

    @Test
    fun `收敛后的随机区间仍满足上下界`() {
        val policy = CloudPolicy(revisionPollMinSeconds = 5, revisionPollMaxSeconds = 120)
        val random = Random(7)

        repeat(200) {
            val millis = policy.revisionPollIntervalMillis(random)
            assertTrue("$millis 应不小于 10 秒", millis >= 10_000L)
            assertTrue("$millis 应不大于 120 秒", millis <= 120_000L)
        }
    }
}
