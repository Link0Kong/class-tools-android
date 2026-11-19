package com.furina.classtools.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// =============================================================================
// 形状体系（Material 3 shape scale + Expressive 语义形状）
//
// 数值全部来自 [CtDesign.kt] 的 [CtRadius]（唯一令牌源）；本文件只把 M3 `Shapes`
// 与 `MaterialTheme.shapes` 需要的 5 档映射好，供 M3 组件（Button/Card/TextField…）自动继承。
//
//   extraSmall 4 / small 8 / medium 12 / large 20（卡片）/ extraLarge 28（hero/弹层）
// =============================================================================

/** Material 3 标准形状（MaterialTheme.shapes）。卡片档 = 20（large）、hero/弹层 = 28（extraLarge）。 */
val CtShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(CtRadius.chip),
    medium = RoundedCornerShape(CtRadius.tile),
    large = RoundedCornerShape(CtRadius.card),
    extraLarge = RoundedCornerShape(CtRadius.hero),
)

/** 语义形状别名（新代码用这个；旧名 `CtShapeTokens` 见 DesignSystem.kt）。 */
object CtShapesSemantic {
    val card = RoundedCornerShape(CtRadius.card)
    val hero = RoundedCornerShape(CtRadius.hero)
    val input = RoundedCornerShape(CtRadius.input)
    val sheet = RoundedCornerShape(topStart = CtRadius.sheetTop, topEnd = CtRadius.sheetTop)
    val dialog = RoundedCornerShape(CtRadius.dialog)
    val tile = RoundedCornerShape(CtRadius.tile)
    val pill = CircleShape
}
