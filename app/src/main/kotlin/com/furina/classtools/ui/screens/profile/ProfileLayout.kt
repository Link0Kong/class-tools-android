package com.furina.classtools.ui.screens.profile

import com.furina.classtools.ui.adaptive.CtAdaptiveSpec

/** 「我的」页可配置卡片（顺序/分栏由 [ProfileLayoutLogic] 决定）。 */
enum class ProfileCard {
    ACCOUNT,
    SYNC,
    PERMISSION,
    DATA_PRIVACY,
    APPEARANCE,
    ABOUT,
    SIGN_OUT,
}

/** 一次布局结果（单栏顺序 + 双栏左右分组）。 */
data class ProfileLayout(
    val twoPane: Boolean,
    val single: List<ProfileCard>,
    val left: List<ProfileCard>,
    val right: List<ProfileCard>,
    /** 左栏占比（expanded 时 0.55/0.58）。 */
    val mainPaneWeight: Float,
    val horizontalPaddingDp: Int,
    val contentMaxWidthDp: Int,
    val headerTopPaddingDp: Int,
    val cardGapDp: Int,
)

/**
 * 「我的」页自适应布局纯逻辑（可单测）：
 * - compact/medium：单栏（顺序：账号 → 同步 → 数据与隐私 → 权限与提醒 → 外观 → 关于 → 退出）；
 * - expanded（≥840dp）：双栏——左「账号 + 同步 + 权限与提醒」，右「数据与隐私 + 外观 + 关于 + 退出」；
 * - 平板收紧顶部留白/卡片间距（P2：大屏信息密度）。
 */
object ProfileLayoutLogic {

    fun layoutFor(spec: CtAdaptiveSpec): ProfileLayout {
        val single = listOf(
            ProfileCard.ACCOUNT,
            ProfileCard.SYNC,
            ProfileCard.DATA_PRIVACY,
            ProfileCard.PERMISSION,
            ProfileCard.APPEARANCE,
            ProfileCard.ABOUT,
            ProfileCard.SIGN_OUT,
        )
        return if (spec.twoPane) {
            ProfileLayout(
                twoPane = true,
                single = single,
                left = listOf(ProfileCard.ACCOUNT, ProfileCard.SYNC, ProfileCard.PERMISSION),
                right = listOf(ProfileCard.DATA_PRIVACY, ProfileCard.APPEARANCE, ProfileCard.ABOUT, ProfileCard.SIGN_OUT),
                mainPaneWeight = spec.mainPaneWeight,
                horizontalPaddingDp = spec.horizontalPadding.value.toInt(),
                contentMaxWidthDp = spec.contentMaxWidth.value.toInt(),
                headerTopPaddingDp = 6,
                cardGapDp = 12,
            )
        } else {
            ProfileLayout(
                twoPane = false,
                single = single,
                left = emptyList(),
                right = emptyList(),
                mainPaneWeight = 1f,
                horizontalPaddingDp = spec.horizontalPadding.value.toInt(),
                contentMaxWidthDp = spec.contentMaxWidth.value.toInt(),
                headerTopPaddingDp = 10,
                cardGapDp = 14,
            )
        }
    }

    /** 双栏卡片完整覆盖（避免漏卡/重复卡）。 */
    fun allCardsCovered(layout: ProfileLayout): Boolean {
        if (!layout.twoPane) return layout.single.toSet() == ProfileCard.entries.toSet()
        return (layout.left + layout.right).toSet() == ProfileCard.entries.toSet() &&
            (layout.left + layout.right).size == ProfileCard.entries.size
    }
}
