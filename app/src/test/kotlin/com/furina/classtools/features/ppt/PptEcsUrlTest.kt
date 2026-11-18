package com.furina.classtools.features.ppt

import com.furina.classtools.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PPT 在线媒体 ECS `/pptfiles` 映射单测（2026-11-18 迁移优化，与网页 pptEcsUrl 同口径）。
 *
 * 覆盖：本桶 /ppt/ 资产映射 / 编码路径与 query 保留 / data:/file:/content:/其它域名/非 /ppt 路径原样 /
 * 大小写 scheme·host / 自定义 ECS 基址 / 回滚开关。
 */
class PptEcsUrlTest {

    private val oss = "https://tool-furina-fun.oss-cn-hongkong.aliyuncs.com"
    private val ecs = "https://tool.furina.fun/pptfiles"

    @Test
    fun `本桶 ppt 媒体映射到 ECS pptfiles（含子目录与文件名）`() {
        assertEquals(
            "$ecs/ppt/a/dmwalrdlgevz90l/c0e5834805/s1-1.webp",
            PptEcsUrl.map("$oss/ppt/a/dmwalrdlgevz90l/c0e5834805/s1-1.webp", ecs),
        )
        assertEquals(
            "$ecs/ppt/a/dmwalrdlgevz90l/c0e5834805/deck.mp4",
            PptEcsUrl.map("$oss/ppt/a/dmwalrdlgevz90l/c0e5834805/deck.mp4", ecs),
        )
        // 查询串原样保留（缓存/调试参数不丢）
        assertEquals(
            "$ecs/ppt/a/x/deck.mp4?from=web&v=2",
            PptEcsUrl.map("$oss/ppt/a/x/deck.mp4?from=web&v=2", ecs),
        )
        // 编码路径 / 中文路径原样保留（Caddy 解码后按 OSS pathname 命中缓存）
        val encoded = "/ppt/a/x/media/%E6%9C%AC%E8%8D%89_%E8%A7%86%E9%A2%911_%E7%AC%AC7%E9%A1%B5.mp4"
        assertEquals(ecs + encoded, PptEcsUrl.map(oss + encoded, ecs))
        assertEquals(
            "$ecs/ppt/a/x/media/视频1_第7页.mp4",
            PptEcsUrl.map("$oss/ppt/a/x/media/视频1_第7页.mp4", ecs),
        )
    }

    @Test
    fun `scheme 与 host 大小写不敏感；fragment 丢弃（与网页一致）`() {
        assertEquals(
            "$ecs/ppt/a/x.webp",
            PptEcsUrl.map("HTTPS://TOOL-FURINA-FUN.OSS-CN-HONGKONG.ALIYUNCS.COM/ppt/a/x.webp", ecs),
        )
        assertEquals(
            "$ecs/ppt/a/x.webp",
            PptEcsUrl.map("http://tool-furina-fun.oss-cn-hongkong.aliyuncs.com/ppt/a/x.webp#frag", ecs),
        )
    }

    @Test
    fun `不映射：data file content 本地路径 其它域名 非 ppt 路径 已映射地址`() {
        val unchanged = listOf(
            "data:image/webp;base64,AAAA",
            "file:///tmp/a.webp",
            "content://media/external/a.webp",
            "/local/tmp/a.webp",
            "wxfile://tmp/a.webp",
            "https://cdn.example.com/ppt/a/x.webp",
            "$oss/android/latest.json",
            "$oss/ppt-media/a.webp",
            "$oss/ppt",
            "$ecs/ppt/a/x.webp",
            "",
            "s1-1.webp",
        )
        unchanged.forEach { url ->
            assertEquals("不应映射：$url", url, PptEcsUrl.map(url, ecs))
        }
    }

    @Test
    fun `自定义 ECS 基址（debug http IP）与尾斜杠归一`() {
        val httpBase = "http://121.196.47.2/pptfiles/"
        assertEquals(
            "http://121.196.47.2/pptfiles/ppt/a/x.webp",
            PptEcsUrl.map("$oss/ppt/a/x.webp", httpBase),
        )
        // 空基址 = 不映射（防御）
        assertEquals("$oss/ppt/a/x.webp", PptEcsUrl.map("$oss/ppt/a/x.webp", ""))
    }

    @Test
    fun `回滚开关 enabled=false → 原样返回 OSS 直链`() {
        assertEquals(
            "$oss/ppt/a/x.webp",
            PptEcsUrl.map("$oss/ppt/a/x.webp", ecs, enabled = false),
        )
    }

    @Test
    fun `默认基址取自 BuildConfig WEB_BASE_URL（debug 单元测试 = http IP + pptfiles）`() {
        // testDebugUnitTest 编译 debug 变体：WEB_BASE_URL = http://121.196.47.2
        assertTrue("debug WEB_BASE_URL=" + BuildConfig.WEB_BASE_URL, BuildConfig.WEB_BASE_URL.startsWith("http://121.196.47.2"))
        assertEquals("http://121.196.47.2/pptfiles", PptEcsUrl.defaultBase())
        assertEquals(
            "http://121.196.47.2/pptfiles/ppt/a/x.webp",
            PptEcsUrl.map("$oss/ppt/a/x.webp"),
        )
    }
}
