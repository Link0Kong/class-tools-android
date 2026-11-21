package com.furina.classtools.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// =============================================================================
// 形状体系（Material 3 shape scale + Expressive 语义形状）
//
// 数值全部来自 [CtDesign.kt] 的 [CtRadius]（唯一令牌源）；本文件把 M3 `Shapes`
// 的五档槽位映射为**官方五档**，供 M3 组件（Button/Card/TextField/Dialog…）自动继承：
//
//   extraSmall 4 / small 8 / medium 12 / large 16 / extraLarge 28
//
// 2026-11-21：large 由「卡片档 20/24」改回官方 16；卡片本身用 medium 12
// （M3 Card 默认），需要更大圆角时按十档就地取 20 / 28 并注明档位名。
// =============================================================================

/** Material 3 官方五档形状（MaterialTheme.shapes）。 */
val CtShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 语义形状别名（新代码用这个；旧名 `CtShapeTokens` 见 DesignSystem.kt）。 */
object CtShapesSemantic {
    /** 标准卡片 12dp（M3 Card medium 档）。 */
    val card = RoundedCornerShape(CtRadius.card)
    /** hero 大容器 28dp（extra large）。 */
    val hero = RoundedCornerShape(CtRadius.hero)
    /** 输入 / 列表块 12dp。 */
    val input = RoundedCornerShape(CtRadius.input)
    /** 底部弹层：仅顶部两侧 28dp（非对称形状）。 */
    val sheet = RoundedCornerShape(topStart = CtRadius.sheetTop, topEnd = CtRadius.sheetTop)
    /** 对话框 28dp（extra large）。 */
    val dialog = RoundedCornerShape(CtRadius.dialog)
    /** 图标块 12dp。 */
    val tile = RoundedCornerShape(CtRadius.tile)
    /** Chip 8dp（small）。 */
    val chip = RoundedCornerShape(CtRadius.chip)
    /** 胶囊（Full）。 */
    val pill = CircleShape
}
