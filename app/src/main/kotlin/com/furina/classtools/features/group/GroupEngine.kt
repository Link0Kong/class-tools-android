package com.furina.classtools.features.group

import kotlin.random.Random

/**
 * 分组随机源（可注入 seed，便于单测复现）。
 *
 * 与网页/小程序一致：只在「打乱成员再分组」时使用 Fisher-Yates；
 * 未打乱时按原顺序轮流分配（结果确定）。
 */
interface GroupRandom {
    /** 返回 [0, bound) 的整数；bound ≤0 时返回 0。 */
    fun nextInt(bound: Int): Int
}

/** 固定 seed 的随机源（单测/预览复现）。 */
class SeededGroupRandom(seed: Long) : GroupRandom {
    private val random: Random = Random(seed)

    override fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return random.nextInt(bound)
    }
}

/** 系统随机源（生产环境）。 */
class SystemGroupRandom : GroupRandom {
    override fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        return Random.nextInt(bound)
    }
}

fun defaultGroupRandom(): GroupRandom = SystemGroupRandom()

/**
 * 分组引擎（纯函数，与 `mp-class-tools/pages/group/group.js doGroup()` 同算法）：
 *
 * 1. 组数：`groups` 模式 = clamp(n, 2, 成员数)；`pergroup` 模式 = ceil(成员数 / n)；
 * 2. 可选打乱：Fisher-Yates；
 * 3. 轮流分配 `out[i % groups].push(member)` → 各组人数差 ≤1（均衡）；
 * 4. 生成参数快照（导出/摘要用）。
 */
object GroupEngine {

    /** 计算实际组数（供 UI 预览「将分成 N 组」）。 */
    fun groupCount(memberCount: Int, config: GroupConfig): Int {
        if (memberCount <= 0) return 0
        return when (config.mode) {
            GroupMode.GROUPS -> config.n.coerceIn(
                GroupConfig.MIN_GROUPS,
                maxOf(GroupConfig.MIN_GROUPS, memberCount),
            )

            GroupMode.PER_GROUP -> {
                val per = config.n.coerceIn(GroupConfig.MIN_PER_GROUP, GroupConfig.MAX_PER_GROUP)
                ((memberCount + per - 1) / per).coerceAtLeast(1)
            }
        }
    }

    /** 执行分组。成员为空时返回空结果（调用方应先校验）。 */
    fun group(
        members: List<GroupMember>,
        config: GroupConfig,
        random: GroupRandom,
    ): GroupResult {
        val list = if (config.shuffle) shuffle(members, random) else members.toList()
        val groups = groupCount(list.size, config)
        val buckets = MutableList(groups) { mutableListOf<GroupMember>() }
        list.forEachIndexed { index, member ->
            if (groups > 0) buckets[index % groups].add(member)
        }
        val resultGroups = buckets.mapIndexed { index, bucket ->
            MemberGroup(index = index + 1, members = bucket.toList())
        }
        return GroupResult(
            groups = resultGroups,
            config = config,
            snapshot = snapshotOf(config, resultGroups),
        )
    }

    /** Fisher-Yates（原地打乱的副本；与 web/mp 循环写法一致但用可注入随机源）。 */
    fun shuffle(members: List<GroupMember>, random: GroupRandom): List<GroupMember> {
        val list = members.toMutableList()
        for (i in list.indices.reversed()) {
            if (i <= 0) break
            val j = random.nextInt(i + 1).coerceIn(0, i)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
        return list
    }

    /** 参数快照（文案与 web/mp `cfg` 一致）。 */
    fun snapshotOf(config: GroupConfig, groups: List<MemberGroup>): GroupConfigSnapshot {
        val total = groups.sumOf { it.size }
        val sourceLabel = when (config.source) {
            GroupSource.PREV -> "最近抽签（$total 人）"
            GroupSource.NAMES -> "班级名单（$total 人）"
        }
        val modeLabel = when (config.mode) {
            GroupMode.GROUPS -> "按组数(${groups.size} 组)"
            GroupMode.PER_GROUP -> "按每组人数(${config.n} 人)"
        }
        return GroupConfigSnapshot(
            sourceLabel = sourceLabel,
            modeLabel = modeLabel,
            total = total,
            groupCount = groups.size,
            innerLabel = if (config.shuffle) "打乱后随机分配" else "按原顺序轮流分配",
        )
    }
}
