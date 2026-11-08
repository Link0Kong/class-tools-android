package com.furina.classtools.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// =============================================================================
// 班级工具站 · 形状体系（Material 3 shape scale + Expressive 语义形状）
//
// 数值来源：material3 1.4.0 `ShapeTokens`（公开 scale：4/8/12/16/28 dp）
// 与 design/tokens.json → shape。品牌约束：卡片圆角 = 16dp（继承网页 16px 语义）。
// =============================================================================

/** Material 3 标准形状（MaterialTheme.shapes）。 */
val CtShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 语义形状（组件直接引用；名称保持稳定，供 screens/components 使用）。
 *
 * - hero：首页/身份页大卡（Expressive 32dp 圆角）
 * - toolCard / toolCardPressed：工具卡（按压时圆角收紧，形成「形状回弹」）
 * - pill / chip / input / sheet / dialog / listItem：按 M3 组件规范
 */
object CtShapeTokens {
    /** Hero 大卡：32dp（Expressive extraLargeIncreased）。 */
    val hero = RoundedCornerShape(32.dp)

    /** 工具卡/普通卡片：16dp（品牌卡片圆角）。 */
    val toolCard = RoundedCornerShape(16.dp)

    /** 工具卡按压态：12dp（按下时形状收紧，配合 scale 0.97）。 */
    val toolCardPressed = RoundedCornerShape(12.dp)

    /** 胶囊（按钮/标签/搜索栏）：全圆角。 */
    val pill = CircleShape

    /** Chip：M3 规范 8dp。 */
    val chip = RoundedCornerShape(8.dp)

    /** 输入框：M3 OutlinedTextField 4dp。 */
    val input = RoundedCornerShape(4.dp)

    /** 底部弹层：顶部 30dp（项目统一值）。 */
    val sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    /** 对话框：28dp（M3 extraLarge）。 */
    val dialog = RoundedCornerShape(28.dp)

    /** 列表项/信息块：12dp（M3 medium）。 */
    val listItem = RoundedCornerShape(12.dp)

    /** 小图标底板（工具卡图标气泡）：15dp。 */
    val iconTile = RoundedCornerShape(15.dp)

    /** 悬浮工具栏：64dp 高容器 → 全圆角。 */
    val toolbar = CircleShape
}
