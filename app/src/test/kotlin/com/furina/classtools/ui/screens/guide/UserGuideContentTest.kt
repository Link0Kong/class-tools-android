package com.furina.classtools.ui.screens.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 使用说明卡片化内容单测（契约 §5.3）。
 *
 * 只断言纯 Kotlin 数据（[UserGuideContent] 不依赖 Compose / Android），JVM 单测直接跑。
 * 覆盖：27 张卡、id 唯一、分类合法、标题 / 作用 / 步骤 / 提示非空且符合 §0 长度硬规则、
 * VERSION 已更新、必含 Android 独有能力（提醒 / 写日历 / 权限管理）、scene / tone 合法、
 * 查找函数与分类首张下标。
 */
class UserGuideContentTest {

    private val cards = UserGuideContent.cards

    // ---- 卡片清单 -----------------------------------------------------------

    @Test
    fun `27 张卡片且顺序与契约一致`() {
        assertEquals(27, cards.size)
        assertEquals(
            listOf(
                "identity", "home", "draw", "group", "roster", "hw", "nt", "tt", "fit", "jw", "chsi", "map",
                "power", "water", "express", "vote", "activity", "file", "today", "ppt", "reminder", "calendar", "permissions", "sort",
                "privacy", "feedback", "faq",
            ),
            cards.map { it.id },
        )
    }

    @Test
    fun `卡片 id 唯一且没有任何空卡`() {
        assertEquals(cards.size, cards.map { it.id }.distinct().size)
        cards.forEach { card ->
            assertTrue("空 id", card.id.isNotBlank())
            assertTrue("空标题：${card.id}", card.title.isNotBlank())
            assertTrue("空一句话作用：${card.id}", card.subtitle.isNotBlank())
            assertTrue("空提示：${card.id}", card.tip.isNotBlank())
        }
    }

    @Test
    fun `分类合法且五类齐全`() {
        assertEquals(
            listOf("start", "study", "life", "team", "more"),
            GuideCategory.entries.map { it.id },
        )
        assertEquals(
            listOf("入门", "学习", "生活", "协作", "更多"),
            GuideCategory.entries.map { it.label },
        )
        cards.forEach { card ->
            assertTrue("非法分类：${card.id}", card.category in GuideCategory.entries)
        }
        assertEquals(2, UserGuideContent.countOf(GuideCategory.START))
        assertEquals(10, UserGuideContent.countOf(GuideCategory.STUDY))
        assertEquals(3, UserGuideContent.countOf(GuideCategory.LIFE))
        assertEquals(5, UserGuideContent.countOf(GuideCategory.TEAM))
        assertEquals(7, UserGuideContent.countOf(GuideCategory.MORE))
    }

    // ---- 文案硬规则（契约 §0）----------------------------------------------

    @Test
    fun `标题不超过 8 字`() {
        cards.forEach { card ->
            assertTrue(
                "标题超长（${card.title.length} > 8）：${card.id} / ${card.title}",
                card.title.length <= 8,
            )
        }
    }

    @Test
    fun `一句话作用不超过 20 字`() {
        cards.forEach { card ->
            assertTrue(
                "一句话作用超长（${card.subtitle.length} > 20）：${card.id} / ${card.subtitle}",
                card.subtitle.length <= 20,
            )
        }
    }

    @Test
    fun `每卡 3 条步骤且每条不超过 24 字`() {
        cards.forEach { card ->
            assertEquals("${card.id} 的步骤数", 3, card.steps.size)
            card.steps.forEachIndexed { index, step ->
                assertTrue("${card.id} 第 ${index + 1} 条步骤为空", step.isNotBlank())
                assertTrue(
                    "步骤超长（${step.length} > 24）：${card.id} / $step",
                    step.length <= 24,
                )
                assertFalse(
                    "步骤不应以标点开头：${card.id} / $step",
                    step.first() in "，。、；：「（【,.;:",
                )
            }
        }
    }

    @Test
    fun `每卡提示不超过 30 字`() {
        cards.forEach { card ->
            assertTrue(
                "提示超长（${card.tip.length} > 30）：${card.id} / ${card.tip}",
                card.tip.length <= 30,
            )
            assertTrue("提示含换行：${card.id}", !card.tip.contains('\n'))
            assertFalse("提示不应自带 💡（由 UI 统一加）", card.tip.contains("💡"))
        }
    }

    @Test
    fun `文案不含实现细节关键词`() {
        val forbidden = listOf(
            "token", "userdata", "buildconfig", "api", "数据库", "字段", "版本号", "测试账号", "开发者", "接口",
        )
        cards.forEach { card ->
            val text = buildString {
                append(card.title).append('\n')
                append(card.subtitle).append('\n')
                append(card.tip).append('\n')
                card.steps.forEach { append(it).append('\n') }
            }.lowercase()
            forbidden.forEach { word ->
                assertFalse(
                    "文案含实现细节「$word」：${card.id}",
                    text.contains(word.lowercase()),
                )
            }
        }
    }

    // ---- 版本 / Android 独有能力 -------------------------------------------

    @Test
    fun `内容版本已更新`() {
        assertEquals("2026-11-16.1", UserGuideContent.VERSION)
        assertFalse(
            "VERSION 仍是旧值",
            UserGuideContent.VERSION == "2026-11-12.1",
        )
    }

    @Test
    fun `必含 Android 独有能力卡片`() {
        listOf("reminder", "calendar", "permissions").forEach { id ->
            val card = UserGuideContent.cardOf(id)
            assertNotNull("缺少 Android 独有卡片：$id", card)
            assertTrue("卡片标题非空：$id", card!!.title.isNotBlank())
        }
        // 提醒 / 写日历的关键事实必须保留在卡片里（契约 §5.2 末段）。
        val reminderText = cardText("reminder")
        assertTrue("提醒卡缺少「上课」提醒", reminderText.contains("上课"))
        assertTrue("提醒卡缺少「作业」提醒", reminderText.contains("作业"))
        assertTrue("日历卡缺少「系统日历」", cardText("calendar").contains("系统日历"))
        assertTrue("权限卡缺少「日历」权限", cardText("permissions").contains("日历"))
    }

    // ---- scene / tone -------------------------------------------------------

    @Test
    fun `scene 与 tone 合法且场景覆盖不少于 8 种`() {
        cards.forEach { card ->
            assertTrue("非法 scene：${card.id}", card.scene in GuideScene.entries)
            assertTrue("非法 tone：${card.id}", card.tone in GuideTone.entries)
            assertTrue("scene id 为空：${card.id}", card.scene.id.isNotBlank())
            assertTrue("tone id 为空：${card.id}", card.tone.id.isNotBlank())
        }
        val usedScenes = cards.map { it.scene }.distinct()
        assertTrue(
            "配图场景覆盖不足 8 种：${usedScenes.map { it.id }}",
            usedScenes.size >= 8,
        )
        assertTrue(
            "scene id 非法",
            usedScenes.all {
                it.id in setOf(
                    "picks", "steps", "grid", "list", "chart", "people",
                    "transfer", "ballot", "droplet", "shield", "slides",
                )
            },
        )
        // 契约 §1.4：学习分类 tone 轮换且同分类不连续重复。
        val studyTones = cards.filter { it.category == GuideCategory.STUDY }.map { it.tone }
        for ((previous, next) in studyTones.zipWithNext()) {
            assertTrue("学习分类 tone 连续重复：$previous", previous != next)
        }
    }

    // ---- 查找函数 -----------------------------------------------------------

    @Test
    fun `按 id 与分类查找`() {
        assertEquals("抽签三步", UserGuideContent.cardOf("draw")?.title)
        assertEquals("常见问题", UserGuideContent.sectionOf("faq")?.title)
        assertNull(UserGuideContent.cardOf("missing"))
        assertNull(UserGuideContent.sectionOf("missing"))
        assertEquals(-1, UserGuideContent.indexOf("missing"))
        assertEquals(2, UserGuideContent.indexOf("draw"))
        assertEquals(5, UserGuideContent.cardsOf(GuideCategory.TEAM).size)
        assertEquals(0, UserGuideContent.firstIndexOf(GuideCategory.START))
        assertEquals(2, UserGuideContent.firstIndexOf(GuideCategory.STUDY))
        assertEquals(12, UserGuideContent.firstIndexOf(GuideCategory.LIFE))
        assertEquals(15, UserGuideContent.firstIndexOf(GuideCategory.TEAM))
        assertEquals(20, UserGuideContent.firstIndexOf(GuideCategory.MORE))
    }

    @Test
    fun `分类首张下标都指向该分类首页`() {
        GuideCategory.entries.forEach { category ->
            val index = UserGuideContent.firstIndexOf(category)
            assertTrue("分类无卡：${category.id}", index >= 0)
            assertEquals("分类首张卡不符：${category.id}", category, cards[index].category)
        }
    }

    private fun cardText(id: String): String {
        val card = UserGuideContent.cardOf(id) ?: error("缺少卡片：$id")
        return buildString {
            append(card.title).append(card.subtitle).append(card.tip)
            card.steps.forEach { append(it) }
        }
    }
}
