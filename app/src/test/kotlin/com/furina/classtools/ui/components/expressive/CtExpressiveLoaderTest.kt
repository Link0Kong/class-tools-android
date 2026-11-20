package com.furina.classtools.ui.components.expressive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * [CtLoaderShapes] 的纯逻辑单测（JVM，不依赖 Android / Compose）。
 *
 * 只验证「形状序列合法、插值稳定、采样点不越出画布」；绘制与动画不在此覆盖。
 */
class CtExpressiveLoaderTest {

    private val sequence: List<CtLoaderShape> = CtLoaderShapes.sequence()

    @Test
    fun sequenceHasSevenShapes() {
        assertEquals(7, sequence.size)
    }

    @Test
    fun everyShapeHasValidSampledRadii() {
        for ((index, shape) in sequence.withIndex()) {
            assertEquals("shape[$index] 采样点数", CtLoaderShapes.SAMPLES, shape.radii.size)
            for ((sample, radius) in shape.radii.withIndex()) {
                assertTrue(
                    "shape[$index].radii[$sample]=$radius 越界",
                    radius >= 0.3f && radius <= 1.8f,
                )
            }
        }
    }

    @Test
    fun lerpWithSameShapeIsIdentity() {
        for (shape in sequence) {
            val mid = CtLoaderShapes.lerp(shape, shape, 0.5f)
            assertRadiiEquals(shape, mid, 1e-4f)
        }
    }

    @Test
    fun lerpEndpointsReturnExactShapes() {
        for (i in sequence.indices) {
            val a = sequence[i]
            val b = sequence[(i + 1) % sequence.size]
            assertRadiiEquals(a, CtLoaderShapes.lerp(a, b, 0f), 1e-4f)
            assertRadiiEquals(b, CtLoaderShapes.lerp(a, b, 1f), 1e-4f)
        }
    }

    @Test
    fun sequenceCanLoopBackToFirstShape() {
        val first = sequence.first()
        val wrapped = CtLoaderShapes.lerp(sequence.last(), first, 1f)
        assertRadiiEquals(first, wrapped, 1e-4f)
    }

    @Test
    fun sampledPointsStayInsideCanvasWhenRotated() {
        // Medium 档：48dp 画布、38/48 比例 → 形状基准半径 19dp。
        // 形状整体旋转时，只要每个采样点到中心距离 ≤ 24dp（= 19 × 1.263）就不会被裁切；
        // 这里按需求给的宽松上界 1.35 校验（留出 Catmull-Rom 控制点的少量外扩余量）。
        val radius = 19f
        for ((index, shape) in sequence.withIndex()) {
            val points = shape.unitPoints()
            assertEquals("shape[$index] 坐标点数量", CtLoaderShapes.SAMPLES * 2, points.size)
            for (sample in 0 until CtLoaderShapes.SAMPLES) {
                val distance = hypot(points[sample * 2] * radius, points[sample * 2 + 1] * radius)
                assertTrue(
                    "shape[$index] 第 $sample 点到中心 $distance dp，超出画布",
                    distance <= radius * 1.35f,
                )
            }
        }
    }

    @Test
    fun intermediateMorphsKeepRadiiInRange() {
        // 相邻形状之间的任意中间态也必须保持合法（不能自交 / 炸开）。
        for (i in sequence.indices) {
            val a = sequence[i]
            val b = sequence[(i + 1) % sequence.size]
            for (t in listOf(0.25f, 0.5f, 0.75f)) {
                val mid = CtLoaderShapes.lerp(a, b, t)
                for ((sample, radius) in mid.radii.withIndex()) {
                    assertTrue(
                        "morph[$i] t=$t 第 $sample 点半径 $radius 越界",
                        radius in 0.3f..1.8f,
                    )
                }
            }
        }
    }

    @Test
    fun lerpClampsProgressOutsideUnitRange() {
        val first = sequence.first()
        val second = sequence[1]
        // 弹簧会过冲到 0..1 之外，插值必须夹住，否则路径会「炸开」。
        assertRadiiEquals(first, CtLoaderShapes.lerp(first, second, -0.5f), 1e-4f)
        assertRadiiEquals(second, CtLoaderShapes.lerp(first, second, 1.5f), 1e-4f)
    }

    private fun assertRadiiEquals(
        expected: CtLoaderShape,
        actual: CtLoaderShape,
        tolerance: Float,
    ) {
        assertEquals(expected.radii.size, actual.radii.size)
        for (i in expected.radii.indices) {
            assertEquals(
                "radii[$i] 不匹配",
                expected.radii[i].toDouble(),
                actual.radii[i].toDouble(),
                tolerance.toDouble(),
            )
        }
    }
}
