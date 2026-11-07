package com.furina.classtools.features.group

import com.furina.classtools.features.export.GroupsBlock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组配置编解码 + 导出 payload 单测。
 */
class GroupCodecExportTest {

    private fun members(count: Int): List<GroupMember> =
        (1..count).map { seat -> GroupMember(seat = seat, name = "同学$seat") }

    private fun sampleResult(): GroupResult = GroupEngine.group(
        members = members(7),
        config = GroupConfig(mode = GroupMode.GROUPS, n = 3),
        random = SeededGroupRandom(1),
    )

    // ---- userdata.group 编解码 ----------------------------------------------

    @Test
    fun codec_roundTrip() {
        val config = GroupConfig(
            source = GroupSource.NAMES,
            mode = GroupMode.PER_GROUP,
            n = 5,
            shuffle = true,
            selectedSeats = listOf(1, 3, 5),
        )
        val json = GroupConfigCodec.toJson(config)
        assertEquals("names", (json["source"] as JsonPrimitive).content)
        assertEquals("pergroup", (json["mode"] as JsonPrimitive).content)
        assertEquals(5, (json["n"] as JsonPrimitive).content.toInt())
        assertEquals("true", (json["shuffle"] as JsonPrimitive).content)
        assertEquals(3, (json["sel"] as JsonArray).size)
        assertEquals(config, GroupConfigCodec.fromJson(json))
    }

    @Test
    fun codec_legacyVocabulary() {
        val pick = JsonObject(
            mapOf(
                "source" to JsonPrimitive("pick"),
                "mode" to JsonPrimitive("per"),
                "n" to JsonPrimitive(3),
            ),
        )
        val config = GroupConfigCodec.fromJson(pick)
        assertEquals(GroupSource.NAMES, config.source)
        assertEquals(GroupMode.PER_GROUP, config.mode)
        assertEquals(3, config.n)
    }

    @Test
    fun codec_filtersInvalidSeatsAndDefaults() {
        val json = JsonObject(
            mapOf(
                "sel" to JsonArray(
                    listOf(
                        JsonPrimitive(0),
                        JsonPrimitive(101),
                        JsonPrimitive(5),
                        JsonPrimitive(5),
                        JsonPrimitive(2),
                    ),
                ),
            ),
        )
        val config = GroupConfigCodec.fromJson(json)
        assertEquals(listOf(2, 5), config.selectedSeats)
        assertEquals(GroupSource.PREV, GroupConfigCodec.fromJson(null).source)
        assertEquals(GroupConfig.DEFAULT_N, GroupConfigCodec.fromJson(JsonPrimitive("x")).n)
        assertTrue(GroupConfigCodec.isDefault(JsonObject(emptyMap())))
        assertFalse(GroupConfigCodec.isDefault(GroupConfigCodec.toJson(GroupConfig(n = 9))))
    }

    // ---- 导出 payload -------------------------------------------------------

    @Test
    fun export_buildsGroupsBlockAndConfig() {
        val result = sampleResult()
        val payload = GroupExportFactory.create(result, nowMillis = 1_794_016_800_000L, recordSuffix = "ABCD")

        assertEquals("分组结果", payload.title)
        assertEquals("3 组 / 7 人", payload.subtitle)
        assertEquals(5, payload.config.size)
        assertEquals("分组结果", payload.block.label)
        assertEquals(3, payload.block.groups.size)
        assertEquals(7, payload.block.groups.sumOf { it.size })
        assertEquals("1 同学1", payload.block.groups.first().first().let { "${it.n} ${it.nm}" })
        assertEquals("ABCD", payload.recordId.takeLast(4))
        assertTrue(payload.recordId.startsWith("GROUP-"))
        assertTrue(payload.fileName.startsWith("分组结果_"))
        assertTrue(payload.fileName.endsWith(".png"))
        assertTrue(payload.fileName.contains("3组"))
        assertTrue(payload.fileName.contains("7人"))
        assertTrue(payload.fileName.contains("顺序"))
    }

    @Test
    fun export_toCardSpec_keepsGroupsBlock() {
        val payload = GroupExportFactory.create(sampleResult(), nowMillis = 1L, recordSuffix = "WXYZ")
        val spec = payload.toCardSpec()
        assertEquals("分组结果", spec.title)
        assertEquals(1, spec.blocks.size)
        val block = spec.blocks.first()
        assertTrue(block is GroupsBlock)
        assertEquals((block as GroupsBlock).groups.size, payload.block.groups.size)
        assertEquals(payload.config.map { it.label to it.value }, spec.config)
    }

    @Test
    fun export_shuffleLabelAppearsInFileName() {
        val result = GroupEngine.group(
            members = members(9),
            config = GroupConfig(mode = GroupMode.GROUPS, n = 3, shuffle = true),
            random = SeededGroupRandom(5),
        )
        val payload = GroupExportFactory.create(result, nowMillis = 1L, recordSuffix = "AAAA")
        assertTrue(payload.fileName.contains("打乱"))
        assertEquals("打乱后随机分配", payload.config.first { it.label.contains("成员分配") }.value)
    }

    @Test
    fun export_copyText() {
        val result = sampleResult()
        val text = GroupExportFactory.copyText(result)
        assertTrue(text.contains("第 1 组："))
        assertEquals(3, text.lines().size)
        assertEquals("", GroupExportFactory.copyText(GroupEngine.group(emptyList(), GroupConfig(), SeededGroupRandom(1))))
    }

    @Test
    fun export_recordIdIsDeterministicForSuffix() {
        val now = 1_794_016_800_000L
        assertEquals(
            GroupExportFactory.recordId(now, "ABCD"),
            GroupExportFactory.recordId(now, "abcd"),
        )
        assertEquals(4, GroupExportFactory.randomSuffix(42L).length)
        assertEquals(GroupExportFactory.randomSuffix(42L), GroupExportFactory.randomSuffix(42L))
    }
}
