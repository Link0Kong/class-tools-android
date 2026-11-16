package com.furina.classtools.data.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 活动登录 WebView `<input type=file>` 文件选择纯逻辑单测（2026-11-16 用户反馈：上传头像点击无反应）。
 *
 * 根因：WebView 默认不处理 file input，必须实现 `WebChromeClient.onShowFileChooser`
 * 并回传 `filePathCallback`（选中/取消都要回调）。这里锁定 accept 归一、多选判断、
 * 选择器 MIME 与 Activity 结果解析。
 */
class ActivityFileChooserLogicTest {

    @Test
    fun `MODE 常量与 WebChromeClient FileChooserParams 对齐`() {
        assertEquals(0, ActivityFileChooserLogic.MODE_OPEN)
        assertEquals(1, ActivityFileChooserLogic.MODE_OPEN_MULTIPLE)
        assertEquals(2, ActivityFileChooserLogic.MODE_SAVE)
    }

    @Test
    fun `accept 类型归一为空时回退通配`() {
        assertEquals(listOf("*/*"), ActivityFileChooserLogic.normalizeAcceptTypes(null))
        assertEquals(listOf("*/*"), ActivityFileChooserLogic.normalizeAcceptTypes(emptyList()))
        assertEquals(listOf("*/*"), ActivityFileChooserLogic.normalizeAcceptTypes(listOf("", "  ")))
    }

    @Test
    fun `accept 类型归一拆分逗号去重去空白`() {
        assertEquals(
            listOf("image/*", "image/png"),
            ActivityFileChooserLogic.normalizeAcceptTypes(listOf("image/*,image/png", " image/* ")),
        )
        assertEquals(
            listOf("image/*", "video/*"),
            ActivityFileChooserLogic.normalizeAcceptTypes(listOf("image/*", "video/*", "image/*")),
        )
    }

    @Test
    fun `多选模式判断`() {
        assertTrue(ActivityFileChooserLogic.isMultiple(ActivityFileChooserLogic.MODE_OPEN_MULTIPLE))
        assertFalse(ActivityFileChooserLogic.isMultiple(ActivityFileChooserLogic.MODE_OPEN))
        assertFalse(ActivityFileChooserLogic.isMultiple(ActivityFileChooserLogic.MODE_SAVE))
        assertFalse(ActivityFileChooserLogic.isMultiple(null))
    }

    @Test
    fun `选择器 MIME 优先图片通配`() {
        assertEquals("image/*", ActivityFileChooserLogic.primaryMimeType(listOf("image/*")))
        assertEquals(
            "image/*",
            ActivityFileChooserLogic.primaryMimeType(listOf("image/*", "image/png")),
        )
        assertEquals("video/*", ActivityFileChooserLogic.primaryMimeType(listOf("video/*")))
        // 空 accept（WebView 未给出）→ 通配任意文件
        assertEquals("*/*", ActivityFileChooserLogic.primaryMimeType(emptyList()))
        assertEquals("*/*", ActivityFileChooserLogic.primaryMimeType(listOf("application/pdf")))
        assertEquals("*/*", ActivityFileChooserLogic.primaryMimeType(listOf("image/*", "video/*")))
    }

    @Test
    fun `取消选择必须回传 null`() {
        assertNull(ActivityFileChooserLogic.urisFromResult(resultOk = false, singleUri = null, clipUris = null))
        // 取消时即使带了 data 也按取消处理（避免把脏 Uri 传给 filePathCallback）
        assertNull(
            ActivityFileChooserLogic.urisFromResult(
                resultOk = false,
                singleUri = "content://media/1",
                clipUris = listOf("content://media/2"),
            ),
        )
    }

    @Test
    fun `单选与多选结果解析`() {
        assertEquals(
            listOf("content://media/1"),
            ActivityFileChooserLogic.urisFromResult(true, "content://media/1", null),
        )
        assertEquals(
            listOf("content://media/1", "content://media/2"),
            ActivityFileChooserLogic.urisFromResult(
                true,
                "content://media/1",
                listOf("content://media/2", "content://media/1", "  "),
            ),
        )
        // 确认但无 Uri → null（同样必须回调，避免 input 卡死）
        assertNull(ActivityFileChooserLogic.urisFromResult(true, null, null))
        assertNull(ActivityFileChooserLogic.urisFromResult(true, "  ", emptyList()))
    }

    @Test
    fun `选择结果有效性判断`() {
        assertTrue(ActivityFileChooserLogic.isPicked(listOf("content://media/1")))
        assertFalse(ActivityFileChooserLogic.isPicked(null))
        assertFalse(ActivityFileChooserLogic.isPicked(emptyList()))
    }
}
