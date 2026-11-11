package com.furina.classtools.ui.screens

import com.furina.classtools.data.network.NetJson
import com.furina.classtools.features.draw.DrawConfig
import com.furina.classtools.features.draw.DrawEntry
import com.furina.classtools.features.draw.DrawPoolSource
import com.furina.classtools.features.draw.DrawRemoveLog
import com.furina.classtools.features.draw.DrawResult
import com.furina.classtools.features.draw.DrawSortMode
import com.furina.classtools.features.draw.DrawStep
import com.furina.classtools.features.draw.DrawWorkflowCodec
import com.furina.classtools.features.draw.DrawWorkflowState
import com.furina.classtools.features.group.GroupConfig
import com.furina.classtools.features.group.GroupEngine
import com.furina.classtools.features.group.GroupMember
import com.furina.classtools.features.group.GroupMode
import com.furina.classtools.features.group.GroupResult
import com.furina.classtools.features.group.GroupSource
import com.furina.classtools.features.group.GroupStep
import com.furina.classtools.features.group.GroupWorkflowCodec
import com.furina.classtools.features.group.GroupWorkflowState
import com.furina.classtools.features.group.MemberGroup
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抽签/分组 Saver 编解码 round-trip（`DrawWorkflowSaver` / `GroupWorkflowSaver` 的实际编码逻辑）。
 *
 * 覆盖：仅配置 / 候选预览 / 结果 + 剔除重抽 log（draw 4 项）；仅配置 / 结果 + 快照重算 /
 * 空姓名成员 / 容错降级（group 4 项）。
 */
class DrawGroupStateSaverTest {

    // ------------------------------------------------------------ draw

    private fun drawConfig() = DrawConfig(
        start = 2,
        end = 30,
        count = 5,
        allowDuplicates = true,
        excludedText = "3,7,10-12",
        sort = DrawSortMode.ASCENDING,
        poolSource = DrawPoolSource.ROSTER,
    )

    @Test
    fun `draw 仅配置 round-trip 保留全部参数`() {
        val state = DrawWorkflowState(step = DrawStep.CONFIG, config = drawConfig())

        val encoded = DrawWorkflowCodec.encode(state)
        val decoded = DrawWorkflowCodec.decode(encoded)!!

        assertEquals(state, decoded)
        assertTrue(encoded.contains("\"pool\":\"roster\""))
        assertEquals(encoded, DrawWorkflowCodec.encode(decoded)) // 幂等
    }

    @Test
    fun `draw 候选预览 round-trip 保留序号与姓名`() {
        val state = DrawWorkflowState(
            step = DrawStep.PREVIEW,
            config = drawConfig(),
            candidates = listOf(
                DrawEntry(5, null),
                DrawEntry(12, "张伟"),
                DrawEntry(23, "李四"),
            ),
        )

        val decoded = DrawWorkflowCodec.decode(DrawWorkflowCodec.encode(state))!!

        assertEquals(state, decoded)
        assertEquals("张伟", decoded.candidates[1].name)
        assertNull(decoded.candidates[0].name)
    }

    @Test
    fun `draw 结果含剔除重抽 log round-trip`() {
        val config = drawConfig()
        val result = DrawResult(
            entries = listOf(DrawEntry(7, null), DrawEntry(23, "王五")),
            pool = listOf(DrawEntry(5, null), DrawEntry(7, null), DrawEntry(23, "王五")),
            config = config,
            log = listOf(
                DrawRemoveLog(removed = DrawEntry(7, null), added = DrawEntry(9, null)),
                DrawRemoveLog(removed = DrawEntry(23, "王五"), added = null),
            ),
        )
        val state = DrawWorkflowState(
            step = DrawStep.RESULT,
            config = config,
            candidates = result.pool,
            result = result,
        )

        val encoded = DrawWorkflowCodec.encode(state)
        val decoded = DrawWorkflowCodec.decode(encoded)!!

        assertEquals(state, decoded)
        assertEquals(2, decoded.result!!.log.size)
        assertNull(decoded.result!!.log[1].added)
        assertTrue(encoded.contains("\"log\""))
        assertEquals(encoded, DrawWorkflowCodec.encode(decoded))
    }

    @Test
    fun `draw 坏 JSON 返回 null 且 RESULT 缺结果降级`() {
        assertNull(DrawWorkflowCodec.decode(null))
        assertNull(DrawWorkflowCodec.decode(""))
        assertNull(DrawWorkflowCodec.decode("not-json"))
        assertNull(DrawWorkflowCodec.decode("[]"))

        val previewJson = """
            {"v":1,"step":"result","config":{"start":1,"end":20,"count":3},
             "candidates":[{"n":5},{"n":12,"nm":"张伟"}]}
        """.trimIndent()
        val preview = DrawWorkflowCodec.decode(previewJson)!!
        assertEquals(DrawStep.PREVIEW, preview.step)
        assertEquals(2, preview.candidates.size)

        val configOnly = DrawWorkflowCodec.decode("""{"v":1,"step":"result","config":{}}""")!!
        assertEquals(DrawStep.CONFIG, configOnly.step)
        assertNull(configOnly.result)
    }

    // ------------------------------------------------------------ group

    private fun groupConfig() = GroupConfig(
        source = GroupSource.NAMES,
        mode = GroupMode.PER_GROUP,
        n = 3,
        shuffle = true,
        selectedSeats = listOf(1, 2, 5, 8),
    )

    @Test
    fun `group 仅配置 round-trip 保留来源模式与名单`() {
        val state = GroupWorkflowState(step = GroupStep.CONFIG, config = groupConfig())

        val encoded = GroupWorkflowCodec.encode(state)
        val decoded = GroupWorkflowCodec.decode(encoded)!!

        assertEquals(state, decoded)
        assertEquals(groupConfig(), decoded.config)
        assertEquals(encoded, GroupWorkflowCodec.encode(decoded))
    }

    @Test
    fun `group 结果 round-trip 且快照按 config 重算一致`() {
        val config = groupConfig()
        val groups = listOf(
            MemberGroup(
                index = 1,
                members = listOf(GroupMember(1, "张伟"), GroupMember(2, "")),
            ),
            MemberGroup(
                index = 2,
                members = listOf(GroupMember(5, "李四"), GroupMember(8, "王五")),
            ),
        )
        val result = GroupResult(groups = groups, config = config, snapshot = GroupEngine.snapshotOf(config, groups))
        val state = GroupWorkflowState(step = GroupStep.RESULT, config = config, result = result)

        val encoded = GroupWorkflowCodec.encode(state)
        val decoded = GroupWorkflowCodec.decode(encoded)!!

        assertEquals(state, decoded)
        assertEquals(groups, decoded.result!!.groups)
        assertEquals(result.snapshot, decoded.result!!.snapshot)
        assertEquals(4, decoded.result!!.totalMembers)
        // 空姓名成员恢复为空串（线格式省略 nm）
        assertEquals("", decoded.result!!.groups[0].members[1].name)
        assertEquals(encoded, GroupWorkflowCodec.encode(decoded))
    }

    @Test
    fun `group 结果步骤缺结果降级为配置`() {
        assertNull(GroupWorkflowCodec.decode("bad"))
        val state = GroupWorkflowCodec.decode(
            """{"v":1,"step":"result","config":{"source":"prev","mode":"groups","n":4}}""",
        )!!
        assertEquals(GroupStep.CONFIG, state.step)
        assertNull(state.result)
    }

    @Test
    fun `group 配置缺字段回落默认值`() {
        val state = GroupWorkflowCodec.decode("""{"v":1,"step":"config","config":{}}""")!!
        assertEquals(GroupStep.CONFIG, state.step)
        assertEquals(GroupSource.PREV, state.config.source)
        assertEquals(GroupMode.GROUPS, state.config.mode)
        assertEquals(GroupConfig().n, state.config.n)
        assertFalse(state.config.shuffle)
    }

    @Test
    fun `draw 编码为紧凑 JSON 字符串且大小可控`() {
        val state = DrawWorkflowState(
            step = DrawStep.RESULT,
            config = drawConfig(),
            candidates = (1..30).map { DrawEntry(it, if (it % 2 == 0) "同学$it" else null) },
            result = DrawResult(
                entries = listOf(DrawEntry(3, "同学3")),
                pool = (1..30).map { DrawEntry(it) },
                config = drawConfig(),
            ),
        )
        val encoded = DrawWorkflowCodec.encode(state)
        assertFalse(encoded.contains("\n"))
        assertNotNull(Json.parseToJsonElement(encoded))
        assertTrue(encoded.length < 16_000)
        // 无 JSON null 噪音（可选字段省略）
        assertFalse(encoded.contains(":null"))
    }
}
