package com.furina.classtools.ui.components.expressive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furina.classtools.ui.theme.rememberReduceMotion
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 加载指示器尺寸档（容器直径 / 元素直径）。
 *
 * [element] 保留给读取该属性的旧调用（点状指示器的圆点直径）；新的形状形变实现统一按
 * `38/48 × 画布短边` 绘制（Material 3 Expressive LoadingIndicator 的 ActiveSize / 容器比例），
 * 不再直接使用 [element]。
 */
enum class CtLoadingIndicatorSize(val container: Dp, val element: Dp) {
    /** 24dp：按钮内联、列表行内。 */
    Small(24.dp, 4.dp),

    /** 48dp：页面级默认（= Material 3 Expressive LoadingIndicator 容器规格）。 */
    Medium(48.dp, 6.dp),

    /** 64dp：hero 卡/大面积加载。 */
    Large(64.dp, 8.dp),
}

// ===========================================================================
// 一、纯 Kotlin 几何模型（只依赖 kotlin.math，可被 JVM 单测直接调用）
// ===========================================================================

/** 形状采样点数。7 个形状必须同长，逐点插值才不会错位。 */
internal const val CT_LOADER_SHAPE_SAMPLES: Int = 96

private const val CT_LOADER_PI: Float = 3.1415927f
private const val CT_LOADER_HALF_PI: Float = 1.5707964f
private const val CT_LOADER_TWO_PI: Float = 6.2831855f
private const val CT_LOADER_DEG_TO_RAD: Float = CT_LOADER_PI / 180f

/**
 * 一个关键形状的极坐标描述（纯 Kotlin 值对象，不引用任何 Android / Compose 类型）。
 *
 * 为什么用「96 个归一化半径 + 三个仿射参数」而不是 Bézier 顶点表：不同形状的控制点数量、
 * 起点与绕向都不同，直接做路径形变（Morph）会崩；而极坐标在固定采样数下对所有形状天然同构，
 * 逐点 lerp 一定得到合法闭合路径。
 *
 * 说明：这是 `data class` 语义的值对象，但 [radii] 是 `FloatArray`，编译器生成的 `equals`
 * 只会比较数组引用，因此这里手写按内容比较的 [equals] / [hashCode]。
 *
 * @property radii 长度必须为 [CT_LOADER_SHAPE_SAMPLES] 的归一化半径（1f = 基准圆），只读
 * @property scaleX 整体横向缩放（pill / oval 用）
 * @property scaleY 整体纵向缩放
 * @property rotationDeg 形状自身旋转（度）
 */
internal class CtLoaderShape(
    val radii: FloatArray,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
) {
    init {
        require(radii.size == CT_LOADER_SHAPE_SAMPLES) {
            "CtLoaderShape.radii.size=${radii.size}，必须是 $CT_LOADER_SHAPE_SAMPLES"
        }
    }

    /**
     * 把单位采样点写入 [out]：`[x0, y0, x1, y1, …]`，1f = 基准圆半径。
     *
     * 已应用 [scaleX] / [scaleY] 与 [rotationDeg]；屏幕坐标 = 画布中心 + 归一化点 × 像素半径。
     * 与 [unitPoints] 一样是纯函数，绘制与单测共用同一份数据。
     */
    fun writeUnitPoints(out: FloatArray) {
        val count = radii.size
        require(out.size >= count * 2) { "out.size=${out.size}，至少需要 ${count * 2}" }
        val radians = rotationDeg * CT_LOADER_DEG_TO_RAD
        val cosRotation = cos(radians)
        val sinRotation = sin(radians)
        for (i in 0 until count) {
            val theta = CT_LOADER_TWO_PI * i / count
            val x = radii[i] * scaleX * cos(theta)
            val y = radii[i] * scaleY * sin(theta)
            val index = i * 2
            out[index] = x * cosRotation - y * sinRotation
            out[index + 1] = x * sinRotation + y * cosRotation
        }
    }

    /** [writeUnitPoints] 的分配版：返回长度 `2 × radii.size` 的单位坐标。 */
    fun unitPoints(): FloatArray = FloatArray(radii.size * 2).also(::writeUnitPoints)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CtLoaderShape) return false
        return radii.contentEquals(other.radii) &&
            scaleX == other.scaleX &&
            scaleY == other.scaleY &&
            rotationDeg == other.rotationDeg
    }

    override fun hashCode(): Int {
        var result = radii.contentHashCode()
        result = 31 * result + scaleX.hashCode()
        result = 31 * result + scaleY.hashCode()
        result = 31 * result + rotationDeg.hashCode()
        return result
    }

    override fun toString(): String =
        "CtLoaderShape(samples=$CT_LOADER_SHAPE_SAMPLES, scaleX=$scaleX, " +
            "scaleY=$scaleY, rotationDeg=$rotationDeg)"
}

/**
 * 官方 Material 3 Expressive LoadingIndicator 的 7 形状序列 + 参数插值。
 *
 * 序列（闭环）：soft burst → cookie9 → pentagon → pill → sunny → cookie4 → oval → 回到起点。
 * 所有形状共享 [SAMPLES] 个采样点，[lerp] 为纯函数（单测覆盖）。
 */
internal object CtLoaderShapes {

    /** 采样点数（= [CT_LOADER_SHAPE_SAMPLES]）。 */
    const val SAMPLES: Int = CT_LOADER_SHAPE_SAMPLES

    /** 7 个关键形状；构造一次后共享只读实例，绘制侧直接 `remember` 缓存即可。 */
    private val SEQUENCE: List<CtLoaderShape> = listOf(
        softBurst(),
        cookie(lobes = 9, amplitude = 0.055f),
        pentagon(),
        pill(),
        sunny(),
        cookie(lobes = 4, amplitude = 0.07f),
        oval(),
    )

    /** 返回 7 个关键形状（顺序即形变顺序，1 → 7 → 1 循环）。 */
    fun sequence(): List<CtLoaderShape> = SEQUENCE

    /**
     * 逐点插值两个形状，`t` 夹到 `0..1`（弹簧过冲不会传到路径上，避免形状「炸开」）。
     *
     * `t <= 0` 返回 [a] 本身，`t >= 1` 返回 [b] 本身，保证端点严格相等。
     */
    fun lerp(a: CtLoaderShape, b: CtLoaderShape, t: Float): CtLoaderShape {
        if (t <= 0f) return a
        if (t >= 1f) return b
        val count = a.radii.size
        val radii = FloatArray(count) { i ->
            a.radii[i] + (b.radii[i] - a.radii[i]) * t
        }
        return CtLoaderShape(
            radii = radii,
            scaleX = a.scaleX + (b.scaleX - a.scaleX) * t,
            scaleY = a.scaleY + (b.scaleY - a.scaleY) * t,
            rotationDeg = a.rotationDeg + (b.rotationDeg - a.rotationDeg) * t,
        )
    }

    // ---- 形状构造：全部只依赖 kotlin.math ----

    /** 按 [SAMPLES] 等角采样一个半径函数。 */
    private fun profile(radiusAt: (Float) -> Float): FloatArray =
        FloatArray(SAMPLES) { i -> radiusAt(CT_LOADER_TWO_PI * i / SAMPLES) }

    /** 12 瓣柔和爆花（官方 SoftBurst 的近似：圆角、无尖刺）。 */
    private fun softBurst(): CtLoaderShape = CtLoaderShape(
        radii = profile { theta -> 1f + 0.10f * cos(12f * theta) },
    )

    /** 圆角饼干：`lobes` 个圆角凸起。官方 Cookie9Sided / Cookie4Sided 的近似。 */
    private fun cookie(lobes: Int, amplitude: Float): CtLoaderShape = CtLoaderShape(
        radii = profile { theta -> 1f + amplitude * cos(lobes * theta) },
    )

    /**
     * 正五边形（顶点朝上）：按极角取到最近一条边的径向距离，再 3 点滑动平均做圆角。
     *
     * 正 n 边形半径公式：`r(φ) = cos(π/n) / cos(φ)`，`φ` 为相对边法线方向的偏角，
     * 取值范围 `[-π/n, π/n]`；顶点处 `φ = ±π/n` 得 1，边中点处 `φ = 0` 得 `cos(π/n)`。
     */
    private fun pentagon(): CtLoaderShape {
        val sides = 5
        val step = CT_LOADER_TWO_PI / sides
        // 屏幕坐标 y 向下，-π/2 即「上方」，让五边形尖角朝上。
        val firstVertexAngle = -CT_LOADER_HALF_PI
        val apothem = cos(CT_LOADER_PI / sides)
        val raw = profile { theta ->
            // 相对「最近边法线」的偏角，范围 (-π/n, π/n]：顶点处 ±π/n，边中点处 0。
            val turned = theta - (firstVertexAngle + step / 2f)
            val wrapped = ((turned % step) + step) % step
            val phi = if (wrapped > step / 2f) wrapped - step else wrapped
            apothem / cos(phi)
        }
        return CtLoaderShape(radii = smooth3(raw))
    }

    /** 圆形胶囊（pill）：圆 + 横向拉伸，同时缩小半径保持面积感。 */
    private fun pill(): CtLoaderShape = CtLoaderShape(
        radii = FloatArray(SAMPLES) { 0.72f },
        scaleX = 1.55f,
        scaleY = 1f,
    )

    /**
     * 四角星（sunny）：`1 - 0.24·|sin 2θ|^0.65`。
     *
     * 指数小于 1 让尖端更「尖」、谷底更平缓，接近官方 Sunny 的凹边四角星；
     * 最大值仍是 1（星尖），不会超出基准圆。
     */
    private fun sunny(): CtLoaderShape = CtLoaderShape(
        radii = profile { theta ->
            val valley = abs(sin(2f * theta))
            1f - 0.24f * valley.pow(0.65f)
        },
    )

    /** 横椭圆（oval）：比圆略宽、略扁，面积与圆接近。 */
    private fun oval(): CtLoaderShape = CtLoaderShape(
        radii = FloatArray(SAMPLES) { 0.98f },
        scaleX = 1.22f,
        scaleY = 0.86f,
    )

    /** 环形 3 点滑动平均（权重 0.25 / 0.5 / 0.25），用于把多边形棱角磨圆。 */
    private fun smooth3(radii: FloatArray): FloatArray {
        val count = radii.size
        return FloatArray(count) { i ->
            val previous = radii[(i + count - 1) % count]
            val next = radii[(i + 1) % count]
            radii[i] * 0.5f + (previous + next) * 0.25f
        }
    }
}

// ===========================================================================
// 二、绘制（Canvas + Path，不依赖 material3 1.5 的 LoadingIndicator / MaterialShapes）
// ===========================================================================

/**
 * 在当前 [DrawScope] 里填充一个形状。
 *
 * 采样点用 Catmull-Rom 转三次 Bézier（每段一个 `cubicTo`，张力 1/6）连成平滑闭合路径；
 * 96 段在 19dp 半径下肉眼已看不出折线。
 *
 * @param center 形状中心（画布中心）
 * @param radius 形状基准半径（像素）；单位采样点乘以它得到实际坐标
 * @param path 复用的 Path（避免每帧分配）
 * @param points 复用的坐标缓冲（长度 ≥ `2 × 采样数`；单位坐标读入、像素坐标写出）
 */
internal fun DrawScope.drawCtLoaderShape(
    shape: CtLoaderShape,
    center: Offset,
    radius: Float,
    color: Color,
    path: Path = Path(),
    points: FloatArray = FloatArray(shape.radii.size * 2),
) {
    val count = shape.radii.size
    if (count < 3) return
    shape.writeUnitPoints(points)
    // 单位坐标 → 画布绝对坐标（points 是临时缓冲，直接原地覆盖）。
    for (i in 0 until count) {
        val index = i * 2
        points[index] = center.x + points[index] * radius
        points[index + 1] = center.y + points[index + 1] * radius
    }
    path.reset()
    path.moveTo(points[0], points[1])
    for (i in 0 until count) {
        val previous = ((i + count - 1) % count) * 2
        val current = i * 2
        val next = ((i + 1) % count) * 2
        val after = ((i + 2) % count) * 2
        // Catmull-Rom → 三次 Bézier：c1 = p1 + (p2 - p0)/6，c2 = p2 - (p3 - p1)/6。
        path.cubicTo(
            points[current] + (points[next] - points[previous]) / 6f,
            points[current + 1] + (points[next + 1] - points[previous + 1]) / 6f,
            points[next] - (points[after] - points[current]) / 6f,
            points[next + 1] - (points[after + 1] - points[current + 1]) / 6f,
            points[next],
            points[next + 1],
        )
    }
    path.close()
    drawPath(path = path, color = color)
}

// ===========================================================================
// 三、动效参数与组件
// ===========================================================================

/** 官方 Material 3 Expressive LoadingIndicator 的动效常量（material3 1.5 源码同值）。 */
private object CtLoaderMotion {
    /** 每次形变的节拍：弹簧先到位，剩余时间补 `delay`，保证 7 形状总周期约 4.55s。 */
    const val MorphIntervalMillis: Long = 650L

    /** 叠加的匀速整体旋转周期（360°）。 */
    const val GlobalRotationMillis: Int = 4666

    /** 每次形变叠加的整体旋转角度。 */
    const val QuarterTurnDeg: Float = 90f

    /** 一整圈的角度。 */
    const val FullTurnDeg: Float = 360f

    /** 形状直径 / 画布短边 = 38dp / 48dp（官方 ActiveSize / Container 比例）。 */
    const val ShapeDiameterRatio: Float = 38f / 48f
}

/** 形变弹簧：官方参数，`visibilityThreshold` 放大到 0.1 让低阻尼弹簧更快结束。 */
private val CtLoaderMorphSpring = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = 200f,
    visibilityThreshold = 0.1f,
)

/** 形状最小直径：`Small` 档（24dp）为 19dp，此下限只防御极端调用。 */
private val CtLoaderMinShapeDiameter: Dp = 5.dp

/**
 * Material 3 Expressive 风格加载指示器（自建实现）。
 *
 * 视觉：48dp 画布内约 38dp 的「形状」每 650ms 形变到下一个形状（7 个形状闭环），
 * 每次形变整体顺转 90°，另外叠加一层 4666ms 匀速 360° 旋转；形变进度用
 * `spring(dampingRatio = 0.6, stiffness = 200, visibilityThreshold = 0.1)` 驱动，
 * 但绘制时对 path 做 0..1 夹紧插值，不带弹簧过冲。
 *
 * 为什么不用 material3 的 `LoadingIndicator`：本仓库解析到的 material3 1.4.0 没有该 API，
 * 也没有 `MaterialShapes` / `androidx.graphics.shapes` 依赖；这里用 Canvas + Path 复刻，
 * 不引入任何新依赖。
 *
 * 无障碍：默认带 `contentDescription = "加载中"`，读屏会播报；纯装饰场景可传 null 关闭。
 * 系统开启「移除动画」时（[rememberReduceMotion]）退化为静态形状，不启动任何无限动画。
 *
 * @param modifier 外部修饰符（尺寸仍由组件按 [size] 决定）
 * @param size 尺寸档（默认 [CtLoadingIndicatorSize.Medium]）
 * @param color 指示器颜色（默认 primary）
 * @param contentDescription 无障碍描述；传 null 表示纯装饰
 */
@Composable
fun CtLoadingIndicator(
    modifier: Modifier = Modifier,
    size: CtLoadingIndicatorSize = CtLoadingIndicatorSize.Medium,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = "加载中",
) {
    val reduceMotion = rememberReduceMotion()
    val shapes = remember { CtLoaderShapes.sequence() }
    val morphProgress = remember { Animatable(0f) }
    val globalRotation = remember { Animatable(0f) }
    var shapeIndex by remember { mutableIntStateOf(0) }
    // 官方实现从 90° 起步（与形变自身的 progress*90 叠加），每完成一次形变再 +90°。
    var quarterTurn by remember { mutableFloatStateOf(CtLoaderMotion.QuarterTurnDeg) }

    if (reduceMotion) {
        // 移除动画：停在第一帧（soft burst）静态绘制，不启动任何无限动画。
        LaunchedEffect(Unit) {
            morphProgress.snapTo(0f)
            globalRotation.snapTo(0f)
            shapeIndex = 0
            quarterTurn = CtLoaderMotion.QuarterTurnDeg
        }
    } else {
        LaunchedEffect(shapes) {
            // 形变节拍：弹簧（< 650ms）与 delay 并发，官方节奏是「弹簧先到位，节拍仍是 650ms」。
            launch {
                while (true) {
                    val beat = async {
                        val result = morphProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = CtLoaderMorphSpring,
                        )
                        if (result.endReason == AnimationEndReason.Finished) {
                            shapeIndex = (shapeIndex + 1) % shapes.size
                            morphProgress.snapTo(0f)
                            quarterTurn =
                                (quarterTurn + CtLoaderMotion.QuarterTurnDeg) %
                                    CtLoaderMotion.FullTurnDeg
                        }
                    }
                    delay(CtLoaderMotion.MorphIntervalMillis)
                    beat.await()
                }
            }
            // 叠加的匀速整体旋转：4666ms 一圈，无限循环。
            launch {
                globalRotation.animateTo(
                    targetValue = CtLoaderMotion.FullTurnDeg,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = CtLoaderMotion.GlobalRotationMillis,
                            easing = LinearEasing,
                        ),
                        repeatMode = RepeatMode.Restart,
                    ),
                )
            }
        }
    }

    val semanticModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    val path = remember { Path() }
    val points = remember { FloatArray(CtLoaderShapes.SAMPLES * 2) }

    Canvas(modifier = semanticModifier.size(size.container)) {
        val canvasSize = this.size
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val diameter = (canvasSize.minDimension * CtLoaderMotion.ShapeDiameterRatio)
            .coerceAtLeast(CtLoaderMinShapeDiameter.toPx())
        val morph = if (reduceMotion) 0f else morphProgress.value
        val spin = if (reduceMotion) 0f else globalRotation.value
        val shape = CtLoaderShapes.lerp(
            a = shapes[shapeIndex],
            b = shapes[(shapeIndex + 1) % shapes.size],
            t = morph,
        )
        // 整体旋转 = 形变进度 × 90° + 已完成节拍的 90° 累积 + 4666ms 匀速旋转。
        rotate(degrees = morph * CtLoaderMotion.QuarterTurnDeg + quarterTurn + spin, pivot = center) {
            drawCtLoaderShape(
                shape = shape,
                center = center,
                radius = diameter / 2f,
                color = color,
                path = path,
                points = points,
            )
        }
    }
}

/**
 * 带容器的加载指示器（对应 Material 3 的 ContainedLoadingIndicator）。
 *
 * 容器是 48dp 圆形色块（官方 ContainerShape），内部形状按 38/48 比例落在容器正中。
 * 表达性配色由调用方传入：`containerColor = primaryContainer`、
 * `color = onPrimaryContainer`；默认值保持仓库既有的 surfaceContainerHigh / primary。
 *
 * 无障碍：语义挂在内部指示器上，`contentDescription = null` 时整块纯装饰。
 *
 * @param modifier 外部修饰符
 * @param size 尺寸档（决定容器直径；默认 48dp）
 * @param color 内部指示器颜色（默认 primary）
 * @param containerColor 容器颜色（默认 surfaceContainerHigh）
 * @param contentDescription 无障碍描述；传 null 表示纯装饰
 */
@Composable
fun CtContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    size: CtLoadingIndicatorSize = CtLoadingIndicatorSize.Medium,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentDescription: String? = "加载中",
) {
    Box(
        modifier = modifier
            .size(size.container)
            .background(color = containerColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CtLoadingIndicator(
            size = size,
            color = color,
            contentDescription = contentDescription,
        )
    }
}
