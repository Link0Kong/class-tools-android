package com.furina.classtools.data.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionLogicTest {

    // ------------------------------------------------------------ 单权限判定

    @Test
    fun `已授予时为 GRANTED`() {
        assertEquals(
            PermissionState.GRANTED,
            PermissionResolver.resolve(granted = true, requestedBefore = false, showRationale = false),
        )
        assertTrue(PermissionState.GRANTED.isGranted)
        assertFalse(PermissionState.GRANTED.canRequest)
    }

    @Test
    fun `从未请求时为 REQUESTABLE`() {
        assertEquals(
            PermissionState.REQUESTABLE,
            PermissionResolver.resolve(granted = false, requestedBefore = false, showRationale = false),
        )
        assertTrue(PermissionState.REQUESTABLE.canRequest)
        assertFalse(PermissionState.REQUESTABLE.needsSettings)
    }

    @Test
    fun `拒绝过但可解释理由时为 DENIED`() {
        assertEquals(
            PermissionState.DENIED,
            PermissionResolver.resolve(granted = false, requestedBefore = true, showRationale = true),
        )
        assertTrue(PermissionState.DENIED.canRequest)
    }

    @Test
    fun `拒绝且不再弹窗时为 PERMANENTLY_DENIED`() {
        assertEquals(
            PermissionState.PERMANENTLY_DENIED,
            PermissionResolver.resolve(granted = false, requestedBefore = true, showRationale = false),
        )
        assertTrue(PermissionState.PERMANENTLY_DENIED.needsSettings)
        assertFalse(PermissionState.PERMANENTLY_DENIED.canRequest)
    }

    // ------------------------------------------------------------ 多权限合并

    @Test
    fun `全部授予合并为 GRANTED`() {
        assertEquals(
            PermissionState.GRANTED,
            PermissionResolver.combine(listOf(PermissionState.GRANTED, PermissionState.GRANTED)),
        )
    }

    @Test
    fun `任一永久拒绝优先`() {
        assertEquals(
            PermissionState.PERMANENTLY_DENIED,
            PermissionResolver.combine(listOf(PermissionState.GRANTED, PermissionState.PERMANENTLY_DENIED)),
        )
        assertEquals(
            PermissionState.PERMANENTLY_DENIED,
            PermissionResolver.combine(listOf(PermissionState.DENIED, PermissionState.PERMANENTLY_DENIED)),
        )
    }

    @Test
    fun `任一需理由拒绝合并为 DENIED`() {
        assertEquals(
            PermissionState.DENIED,
            PermissionResolver.combine(listOf(PermissionState.GRANTED, PermissionState.DENIED)),
        )
        assertEquals(
            PermissionState.DENIED,
            PermissionResolver.combine(listOf(PermissionState.REQUESTABLE, PermissionState.DENIED)),
        )
    }

    @Test
    fun `全未请求合并为 REQUESTABLE 且空列表安全`() {
        assertEquals(
            PermissionState.REQUESTABLE,
            PermissionResolver.combine(listOf(PermissionState.REQUESTABLE, PermissionState.REQUESTABLE)),
        )
        assertEquals(PermissionState.REQUESTABLE, PermissionResolver.combine(emptyList()))
    }

    // ------------------------------------------------------------ 权限组/清单/快照

    @Test
    fun `权限组清单正确`() {
        assertEquals(1, PermissionType.NOTIFICATIONS.manifestPermissions.size)
        assertTrue(PermissionType.NOTIFICATIONS.manifestPermissions.single().endsWith("POST_NOTIFICATIONS"))
        assertEquals(2, PermissionType.CALENDAR.manifestPermissions.size)
        assertTrue(PermissionType.CALENDAR.manifestPermissions.any { it.endsWith("READ_CALENDAR") })
        assertTrue(PermissionType.CALENDAR.manifestPermissions.any { it.endsWith("WRITE_CALENDAR") })
        assertEquals("通知", PermissionType.NOTIFICATIONS.label)
        assertEquals("日历", PermissionType.CALENDAR.label)
    }

    @Test
    fun `快照按类型读取与就绪判断`() {
        val snapshot = PermissionSnapshot(
            notifications = PermissionState.GRANTED,
            calendar = PermissionState.REQUESTABLE,
        )
        assertEquals(PermissionState.GRANTED, snapshot.stateOf(PermissionType.NOTIFICATIONS))
        assertEquals(PermissionState.REQUESTABLE, snapshot.stateOf(PermissionType.CALENDAR))
        assertTrue(snapshot.notificationsReady)
        assertFalse(snapshot.calendarReady)
    }

    // ------------------------------------------------------------ 文案

    @Test
    fun `通知与日历文案完整可读`() {
        PermissionType.entries.forEach { type ->
            val copy = PermissionTexts.of(type)
            assertTrue(copy.title.isNotBlank())
            assertTrue(copy.message.isNotBlank())
            assertTrue(copy.rationale.isNotBlank())
            assertTrue(copy.settingsTitle.isNotBlank())
            assertTrue(copy.settingsMessage.isNotBlank())
            assertTrue(copy.confirmLabel.isNotBlank())
        }
        assertTrue(PermissionTexts.notifications.message.contains("提醒"))
        assertTrue(PermissionTexts.calendar.rationale.contains("日历"))
        assertEquals("暂不", PermissionTexts.notifications.cancelLabel)
    }

    @Test
    fun `通知渠道常量与文案一致`() {
        assertEquals("homework_due", PermissionTexts.CHANNEL_HOMEWORK_ID)
        assertEquals("class_soon", PermissionTexts.CHANNEL_CLASS_ID)
        assertTrue(PermissionTexts.CHANNEL_HOMEWORK_NAME.contains("作业"))
        assertTrue(PermissionTexts.CHANNEL_CLASS_NAME.contains("上课"))
        assertTrue(PermissionTexts.CHANNEL_HOMEWORK_DESC.isNotBlank())
        assertTrue(PermissionTexts.CHANNEL_CLASS_DESC.isNotBlank())
    }
}
