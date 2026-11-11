package com.furina.classtools.data.reminder

import com.furina.classtools.data.permission.PermissionGuidance
import com.furina.classtools.data.permission.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v3.7 通知渠道与权限引导测试：渠道规格（重要性/震动/锁屏）、渠道映射、
 * 重要性文案、四态权限引导、电池优化/自启动/精确闹钟说明（纯逻辑）。
 */
class ReminderChannelsTest {

    // ------------------------------------------------------------ 渠道规格

    @Test
    fun `全部渠道id唯一且包含新通知渠道`() {
        val ids = ReminderChannels.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains(ReminderChannels.NEW_NOTICE_ID))
        assertTrue(ids.contains(ReminderChannels.HOMEWORK_ID))
        assertTrue(ids.contains(ReminderChannels.CLASS_ID))
        assertTrue(ids.contains(ReminderChannels.NOTICE_DUE_ID))
    }

    @Test
    fun `新通知渠道为高重要性且震动加锁屏可见`() {
        val spec = ReminderChannels.NEW_NOTICE
        assertTrue(spec.highImportance)
        assertTrue(spec.vibration)
        assertTrue(spec.lockscreenPublic)
        assertEquals("新通知提醒", spec.name)
        assertTrue(spec.description.contains("15"))
    }

    @Test
    fun `提醒类型映射到既有渠道`() {
        assertEquals(ReminderChannels.HOMEWORK_ID, ReminderChannels.specFor(ReminderKind.HOMEWORK_DIGEST).id)
        assertEquals(ReminderChannels.HOMEWORK_ID, ReminderChannels.specFor(ReminderKind.HOMEWORK_DUE).id)
        assertEquals(ReminderChannels.CLASS_ID, ReminderChannels.specFor(ReminderKind.COURSE_SOON).id)
        assertEquals(ReminderChannels.NOTICE_DUE_ID, ReminderChannels.specFor(ReminderKind.NOTICE_DIGEST).id)
        assertEquals(ReminderChannels.NOTICE_DUE_ID, ReminderChannels.specFor(ReminderKind.NOTICE_DUE).id)
    }

    @Test
    fun `事件渠道覆盖优先于类型渠道`() {
        val base = ReminderEvent(
            kind = ReminderKind.NOTICE_DUE,
            key = "nt-new:g1",
            triggerAtMillis = 1L,
            title = "新通知",
            body = "x",
        )
        assertEquals(ReminderChannels.NOTICE_DUE_ID, ReminderChannels.channelIdFor(base))
        val overridden = base.copy(channelIdOverride = ReminderChannels.NEW_NOTICE_ID)
        assertEquals(ReminderChannels.NEW_NOTICE_ID, ReminderChannels.channelIdFor(overridden))
        // 空串覆盖视为未指定（防御脏数据）
        assertEquals(
            ReminderChannels.NOTICE_DUE_ID,
            ReminderChannels.channelIdFor(base.copy(channelIdOverride = "  ")),
        )
    }

    @Test
    fun `渠道规格查询`() {
        assertEquals(ReminderChannels.NEW_NOTICE, ReminderChannels.specOf(ReminderChannels.NEW_NOTICE_ID))
        assertNull(ReminderChannels.specOf("not-exist"))
    }

    // ------------------------------------------------------------ 重要性文案

    @Test
    fun `重要性文案映射`() {
        assertEquals("未创建", ReminderChannels.importanceLabel(null))
        assertEquals("高（横幅/声音/震动）", ReminderChannels.importanceLabel(ReminderChannels.IMPORTANCE_HIGH))
        assertEquals("普通（无横幅）", ReminderChannels.importanceLabel(ReminderChannels.IMPORTANCE_DEFAULT))
        assertEquals("低（无声音）", ReminderChannels.importanceLabel(ReminderChannels.IMPORTANCE_LOW))
        assertEquals("最低", ReminderChannels.importanceLabel(ReminderChannels.IMPORTANCE_MIN))
        assertEquals("已关闭", ReminderChannels.importanceLabel(ReminderChannels.IMPORTANCE_NONE))
        assertTrue(ReminderChannels.importanceLabel(99).contains("99"))
    }

    @Test
    fun `渠道状态行`() {
        val line = ReminderChannels.channelStatusLine(
            ReminderChannels.NEW_NOTICE,
            ReminderChannels.IMPORTANCE_HIGH,
        )
        assertTrue(line.contains("新通知提醒"))
        assertTrue(line.contains("高"))
    }

    // ------------------------------------------------------------ 权限四态引导

    @Test
    fun `已授权四态引导无需动作`() {
        val guide = PermissionGuidance.notification(PermissionState.GRANTED)
        assertEquals("已开启", guide.statusLabel)
        assertNull(guide.actionLabel)
        assertFalse(guide.canRequest)
        assertFalse(guide.needsSettings)
        assertTrue(guide.message.contains("15"))
    }

    @Test
    fun `未请求过可弹系统授权框`() {
        val guide = PermissionGuidance.notification(PermissionState.REQUESTABLE)
        assertEquals("未开启", guide.statusLabel)
        assertEquals("开启通知权限", guide.actionLabel)
        assertTrue(guide.canRequest)
        assertFalse(guide.needsSettings)
    }

    @Test
    fun `拒绝过仍可再次请求`() {
        val guide = PermissionGuidance.notification(PermissionState.DENIED)
        assertEquals("重新授权", guide.actionLabel)
        assertTrue(guide.canRequest)
        assertFalse(guide.needsSettings)
    }

    @Test
    fun `永久拒绝只能去系统设置`() {
        val guide = PermissionGuidance.notification(PermissionState.PERMANENTLY_DENIED)
        assertEquals("需系统设置", guide.statusLabel)
        assertEquals("去系统设置开启", guide.actionLabel)
        assertFalse(guide.canRequest)
        assertTrue(guide.needsSettings)
        assertTrue(guide.message.contains("系统设置"))
    }

    // ------------------------------------------------------------ 电池优化/自启动/精确闹钟说明

    @Test
    fun `电池优化说明区分两种状态`() {
        val limited = PermissionGuidance.batteryOptimization(false)
        assertTrue(limited.contains("仍受系统省电限制"))
        assertTrue(limited.contains("电池优化"))
        val ignored = PermissionGuidance.batteryOptimization(true)
        assertTrue(ignored.contains("已允许后台运行"))
    }

    @Test
    fun `自启动提示覆盖常见厂商`() {
        val hint = PermissionGuidance.autoStartHint()
        assertTrue(hint.contains("自启动"))
        assertTrue(hint.contains("小米"))
        assertTrue(hint.contains("华为"))
        assertTrue(hint.contains("OPPO"))
    }

    @Test
    fun `精确闹钟说明区分授权状态`() {
        assertTrue(PermissionGuidance.exactAlarm(true).contains("已授权"))
        val notAllowed = PermissionGuidance.exactAlarm(false)
        assertTrue(notAllowed.contains("未授权"))
        assertTrue(notAllowed.contains("WorkManager"))
    }

    @Test
    fun `后台硬限制如实说明`() {
        val text = PermissionGuidance.backgroundLimits()
        assertTrue(text.contains("15 分钟"))
        assertTrue(text.contains("无法做到实时秒达"))
        assertTrue(text.contains("FCM"))
        assertEquals(15L, PermissionGuidance.BACKGROUND_INTERVAL_MINUTES)
    }
}
