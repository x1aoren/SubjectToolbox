package cn.stb.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSnapshotTest {

    private fun snapshot(vararg states: Pair<String, CloudToolState>) = CloudSnapshot(
        loaded = true,
        tools = states.toMap(),
    )

    @Test
    fun `启用或未知的工具不返回停用原因`() {
        val cloud = snapshot(
            "other.textbook" to CloudToolState(enabled = true, disabledReason = "不该出现"),
        )

        assertEquals("", cloud.disabledReasonFor("other.textbook"))
        assertNull(cloud.disabledSourceFor("other.textbook"))
        assertEquals("", cloud.disabledReasonFor("other.missing"))
        assertNull(cloud.disabledSourceFor("other.missing"))
    }

    @Test
    fun `后台下发的停用原因原样返回并带来源`() {
        val cloud = snapshot(
            "other.textbook" to CloudToolState(
                enabled = false,
                disabledReason = "本周设备维护，暂停使用",
                disabledReasonSource = "device",
            ),
        )

        assertEquals("本周设备维护，暂停使用", cloud.disabledReasonFor("other.textbook"))
        assertEquals("device", cloud.disabledSourceFor("other.textbook"))
    }

    @Test
    fun `停用但没配置原因时回退默认文案`() {
        // 旧缓存里即使残留 message 也不会被解析（CloudControlImpl 已不读 message），
        // 因此等价于 disabledReason 为空，统一回退默认文案。
        val cloud = snapshot(
            "other.textbook" to CloudToolState(enabled = false),
        )

        assertEquals(CloudSnapshot.DEFAULT_DISABLED_TEXT, cloud.disabledReasonFor("other.textbook"))
        assertEquals("学校管理员暂时关闭了这个工具；你的本地数据不会丢失。", cloud.disabledReasonFor("other.textbook"))
        assertNull(cloud.disabledSourceFor("other.textbook"))
    }

    @Test
    fun `全局停用来源原样返回`() {
        val cloud = snapshot(
            "other.feedback" to CloudToolState(
                enabled = false,
                disabledReason = "暂时关闭反馈入口",
                disabledReasonSource = "global",
            ),
        )

        assertEquals("暂时关闭反馈入口", cloud.disabledReasonFor("other.feedback"))
        assertEquals("global", cloud.disabledSourceFor("other.feedback"))
    }
}
