package com.furina.classtools.features.group

/**
 * 分组校验 + 向导状态机（纯 Kotlin，可单测）。
 *
 * 校验口径（与 web/mp 一致）：
 * - 成员为空：PREV → 「请先在第 1 步抽签」；NAMES → 「请先选择名单」；
 * - 成员 <2：拒绝；
 * - GROUPS 模式 n<2 拒绝；PER_GROUP 模式 n<1 拒绝（上限 99）；
 * - GROUPS 模式 n>成员数：**不拒绝**，按成员数 clamp（网页 doGroup 同）；
 * - NAMES 来源但 selectedSeats 为空：拒绝（ROSTER_EMPTY）。
 */
object GroupValidator {

    fun validate(
        config: GroupConfig,
        members: List<GroupMember>,
        rosterSelected: Boolean = true,
    ): GroupValidation {
        if (config.source == GroupSource.NAMES && !rosterSelected) {
            return GroupValidation.Invalid(
                GroupError(GroupErrorCode.ROSTER_EMPTY, "请先选择名单"),
            )
        }
        if (members.isEmpty()) {
            return GroupValidation.Invalid(
                GroupError(
                    code = GroupErrorCode.NO_MEMBERS,
                    message = when (config.source) {
                        GroupSource.PREV -> "请先在第 1 步抽签"
                        GroupSource.NAMES -> "请先选择名单"
                    },
                ),
            )
        }
        if (members.size < MIN_MEMBERS) {
            return GroupValidation.Invalid(
                GroupError(GroupErrorCode.MEMBERS_TOO_FEW, "名单至少要有 2 人"),
            )
        }
        when (config.mode) {
            GroupMode.GROUPS -> if (config.n < GroupConfig.MIN_GROUPS) {
                return GroupValidation.Invalid(
                    GroupError(GroupErrorCode.COUNT_INVALID, "组数至少为 2"),
                )
            }

            GroupMode.PER_GROUP -> if (config.n < GroupConfig.MIN_PER_GROUP) {
                return GroupValidation.Invalid(
                    GroupError(GroupErrorCode.COUNT_INVALID, "每组人数至少为 1"),
                )
            }
        }
        return GroupValidation.Valid(members)
    }

    const val MIN_MEMBERS: Int = 2
}

/** 分组向导状态机（CONFIG ↔ RESULT）。 */
object GroupWorkflow {

    /** 修改配置（自动回到 CONFIG 步骤并保留旧结果，便于返回对比）。 */
    fun updateConfig(state: GroupWorkflowState, config: GroupConfig): GroupWorkflowState =
        state.copy(step = GroupStep.CONFIG, config = config)

    /** 配置 → 结果：校验通过则执行分组；否则 Rejected。 */
    fun start(
        state: GroupWorkflowState,
        members: List<GroupMember>,
        random: GroupRandom,
        rosterSelected: Boolean = true,
    ): GroupTransition = when (
        val validation = GroupValidator.validate(state.config, members, rosterSelected)
    ) {
        is GroupValidation.Invalid -> GroupTransition.Rejected(validation.error)
        is GroupValidation.Valid -> GroupTransition.Next(
            state.copy(
                step = GroupStep.RESULT,
                result = GroupEngine.group(validation.members, state.config, random),
            ),
        )
    }

    /** 同参数重新分组（新随机源；打乱模式下结果会变，未打乱则不变）。 */
    fun regroup(
        state: GroupWorkflowState,
        members: List<GroupMember>,
        random: GroupRandom,
        rosterSelected: Boolean = true,
    ): GroupTransition = when (
        val validation = GroupValidator.validate(state.config, members, rosterSelected)
    ) {
        is GroupValidation.Invalid -> GroupTransition.Rejected(validation.error)
        is GroupValidation.Valid -> GroupTransition.Next(
            state.copy(
                step = GroupStep.RESULT,
                result = GroupEngine.group(validation.members, state.config, random),
            ),
        )
    }

    /** 返回配置步骤（保留结果，便于「重新分组」前调参）。 */
    fun back(state: GroupWorkflowState): GroupWorkflowState =
        state.copy(step = GroupStep.CONFIG)

    /** 进入结果步骤（已有结果时；无结果返回 Rejected）。 */
    fun openResult(state: GroupWorkflowState): GroupTransition =
        if (state.result == null) {
            GroupTransition.Rejected(
                GroupError(GroupErrorCode.INVALID_STEP, "还没有分组结果"),
            )
        } else {
            GroupTransition.Next(state.copy(step = GroupStep.RESULT))
        }
}
