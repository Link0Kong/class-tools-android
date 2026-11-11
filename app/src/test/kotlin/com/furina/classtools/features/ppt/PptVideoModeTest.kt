package com.furina.classtools.features.ppt

import com.furina.classtools.data.ppt.PptVideoChapter
import com.furina.classtools.data.ppt.PptVideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.2 原样播放：默认模式/网络分类/章节 seek/续播/降级状态机（契约 §11.3）。 */
class PptVideoModeTest {

    private val now = 1_794_225_600_000L
    private val chapters = listOf(
        PptVideoChapter(0, 0.0),
        PptVideoChapter(1, 8.7),
        PptVideoChapter(2, 15.2),
        PptVideoChapter(5, 42.0), // 允许缺页
    )

    // ------------------------------------------------------------ 默认模式

    @Test
    fun initialMode_matrix() {
        // 无 video → 永远分步
        assertEquals(
            PptPlayMode.STEPS,
            PptModeChooser.initialMode(hasVideo = false, network = PptNetworkQuality.GOOD, preference = null),
        )
        // 无偏好 + 网络良好 → 原样
        assertEquals(
            PptPlayMode.VIDEO,
            PptModeChooser.initialMode(true, PptNetworkQuality.GOOD, null),
        )
        // 无偏好 + 慢网/离线 → 分步
        assertEquals(PptPlayMode.STEPS, PptModeChooser.initialMode(true, PptNetworkQuality.SLOW, null))
        assertEquals(PptPlayMode.STEPS, PptModeChooser.initialMode(true, PptNetworkQuality.OFFLINE, null))
        // 用户偏好优先（仍可切换；失败再回退）
        assertEquals(PptPlayMode.STEPS, PptModeChooser.initialMode(true, PptNetworkQuality.GOOD, PptPlayMode.STEPS))
        assertEquals(PptPlayMode.VIDEO, PptModeChooser.initialMode(true, PptNetworkQuality.SLOW, PptPlayMode.VIDEO))
        assertEquals(PptPlayMode.VIDEO, PptModeChooser.initialMode(true, PptNetworkQuality.OFFLINE, PptPlayMode.VIDEO))
    }

    @Test
    fun classifyNetwork_matrix() {
        assertEquals(
            PptNetworkQuality.OFFLINE,
            PptModeChooser.classifyNetwork(false, false, false, 0, false),
        )
        assertEquals(
            PptNetworkQuality.GOOD,
            PptModeChooser.classifyNetwork(true, true, false, 0, false),
        )
        assertEquals(
            PptNetworkQuality.SLOW,
            PptModeChooser.classifyNetwork(true, true, false, 0, dataSaverEnabled = true),
        )
        assertEquals(
            PptNetworkQuality.SLOW,
            PptModeChooser.classifyNetwork(true, false, true, downstreamKbps = 800, dataSaverEnabled = false),
        )
        assertEquals(
            PptNetworkQuality.GOOD,
            PptModeChooser.classifyNetwork(true, false, true, downstreamKbps = 5_000, dataSaverEnabled = false),
        )
        assertEquals(
            PptNetworkQuality.GOOD,
            PptModeChooser.classifyNetwork(true, false, true, downstreamKbps = 0, dataSaverEnabled = false),
        )
        assertEquals(
            PptNetworkQuality.GOOD,
            PptModeChooser.classifyNetwork(true, false, false, downstreamKbps = 0, dataSaverEnabled = false),
        )
    }

    // ------------------------------------------------------------ 章节 / seek

    @Test
    fun chapterStartAndSeekTarget() {
        assertEquals(0.0, PptVideoLogic.chapterStartOf(chapters, 0)!!, 1e-9)
        assertEquals(8.7, PptVideoLogic.chapterStartOf(chapters, 1)!!, 1e-9)
        assertNull(PptVideoLogic.chapterStartOf(chapters, 3))
        // seek = t + 0.05（契约 §11.3.2）
        assertEquals(8.75, PptVideoLogic.seekTargetForPage(chapters, 1)!!, 1e-9)
        assertEquals(0.05, PptVideoLogic.seekTargetForPage(chapters, 0)!!, 1e-9)
        assertNull(PptVideoLogic.seekTargetForPage(chapters, 3))
    }

    @Test
    fun pageAt_positionBounds() {
        assertEquals(0, PptVideoLogic.pageAt(chapters, 0.0))
        assertEquals(0, PptVideoLogic.pageAt(chapters, 8.6))
        assertEquals(1, PptVideoLogic.pageAt(chapters, 8.7))
        assertEquals(1, PptVideoLogic.pageAt(chapters, 12.0))
        assertEquals(2, PptVideoLogic.pageAt(chapters, 15.2))
        assertEquals(2, PptVideoLogic.pageAt(chapters, 41.9))
        assertEquals(5, PptVideoLogic.pageAt(chapters, 42.0))
        assertEquals(5, PptVideoLogic.pageAt(chapters, 999.0))
        // 无章节/非法位置
        assertEquals(0, PptVideoLogic.pageAt(emptyList(), 30.0))
        assertEquals(0, PptVideoLogic.pageAt(chapters, Double.NaN))
    }

    @Test
    fun previousAndNextChapterTargets() {
        assertEquals(0.05, PptVideoLogic.previousSeekTarget(chapters, 12.0)!!, 1e-9) // 第 1 页 → 第 0 页
        assertEquals(8.75, PptVideoLogic.previousSeekTarget(chapters, 20.0)!!, 1e-9) // 第 2 页 → 第 1 页
        assertEquals(15.25, PptVideoLogic.previousSeekTarget(chapters, 50.0)!!, 1e-9) // 第 5 页 → 第 2 页（缺页取前一有章节页）
        assertEquals(15.25, PptVideoLogic.nextSeekTarget(chapters, 12.0)!!, 1e-9) // 第 1 页 → 第 2 页
        assertEquals(42.05, PptVideoLogic.nextSeekTarget(chapters, 20.0)!!, 1e-9) // 第 2 页 → 第 5 页
        assertNull(PptVideoLogic.nextSeekTarget(chapters, 50.0)) // 末页无下一页
        assertNull(PptVideoLogic.previousSeekTarget(emptyList(), 1.0))
        assertNull(PptVideoLogic.nextSeekTarget(emptyList(), 1.0))
    }

    @Test
    fun hasPreviousNext_buttons() {
        assertFalse(PptVideoLogic.hasPrevious(chapters, 0.0))
        assertTrue(PptVideoLogic.hasNext(chapters, 0.0))
        assertTrue(PptVideoLogic.hasPrevious(chapters, 0.2)) // 第一页未播完 → 可回页首
        assertTrue(PptVideoLogic.hasPrevious(chapters, 10.0))
        assertTrue(PptVideoLogic.hasNext(chapters, 10.0))
        assertFalse(PptVideoLogic.hasNext(chapters, 50.0))
        assertTrue(PptVideoLogic.hasPrevious(chapters, 50.0))
    }

    @Test
    fun progressAndTimeFormat() {
        assertEquals(0.5f, PptVideoLogic.progress(50.0, 100.0), 1e-6f)
        assertEquals(1f, PptVideoLogic.progress(150.0, 100.0), 1e-6f)
        assertEquals(0f, PptVideoLogic.progress(10.0, 0.0), 1e-6f)
        assertEquals(0f, PptVideoLogic.progress(Double.NaN, 100.0), 1e-6f)

        assertEquals("00:00", PptVideoLogic.formatTime(0.0))
        assertEquals("00:09", PptVideoLogic.formatTime(9.9))
        assertEquals("01:05", PptVideoLogic.formatTime(65.0))
        assertEquals("61:05", PptVideoLogic.formatTime(3665.0))
        assertEquals("00:00", PptVideoLogic.formatTime(-5.0))
        assertEquals("00:00", PptVideoLogic.formatTime(Double.NaN))
    }

    @Test
    fun speedCycleAndLabel() {
        assertEquals(1f, PptVideoLogic.SPEED_OPTIONS.first())
        assertEquals(1.25f, PptVideoLogic.nextSpeed(1f))
        assertEquals(1.5f, PptVideoLogic.nextSpeed(1.25f))
        assertEquals(2f, PptVideoLogic.nextSpeed(1.5f))
        assertEquals(1f, PptVideoLogic.nextSpeed(2f)) // 循环
        assertEquals(1.25f, PptVideoLogic.nextSpeed(0.9f)) // 非法 → 从头循环
        assertEquals("1×", PptVideoLogic.speedLabel(1f))
        assertEquals("1.25×", PptVideoLogic.speedLabel(1.25f))
        assertEquals("1.5×", PptVideoLogic.speedLabel(1.5f))
        assertEquals("2×", PptVideoLogic.speedLabel(2f))
    }

    @Test
    fun aspectRatioAndNearEnd() {
        val video = PptVideoInfo(src = "deck.mp4", width = 1280, height = 720, durationSec = 100.0)
        assertEquals(1280f / 720f, PptVideoLogic.aspectRatio(video, 1600, 900), 1e-6f)
        assertEquals(4f / 3f, PptVideoLogic.aspectRatio(PptVideoInfo("x", 0, 0), 1600, 1200), 1e-6f)
        assertEquals(16f / 9f, PptVideoLogic.aspectRatio(null, 0, 0), 1e-6f)

        assertTrue(PptVideoLogic.isNearEnd(96.0, 100.0))
        assertTrue(PptVideoLogic.isNearEnd(95.0, 100.0))
        assertFalse(PptVideoLogic.isNearEnd(50.0, 100.0))
        assertFalse(PptVideoLogic.isNearEnd(50.0, 0.0))
        assertTrue(PptVideoLogic.shouldAutoPlay(PptPlayMode.VIDEO))
        assertFalse(PptVideoLogic.shouldAutoPlay(PptPlayMode.STEPS))
    }

    // ------------------------------------------------------------ 续播

    private fun stepCounts() = listOf(1, 3, 2)

    @Test
    fun resumeFreshnessAndBadge() {
        val video = PptResumeRecord(PptPlayMode.VIDEO, videoSec = 12.0, updatedAtMs = now - 1000)
        assertTrue(PptResumeLogic.isFresh(video, now))
        assertTrue(PptResumeLogic.badgeVisible(video, now))
        assertFalse(PptResumeLogic.badgeVisible(video, now + PPT_RESUME_TTL_MS + 1)) // 超过 7 天
        assertFalse(PptResumeLogic.badgeVisible(null, now))
        assertFalse(PptResumeLogic.isFresh(video.copy(updatedAtMs = 0L), now))

        // 视频 <5s 不显示「继续」
        assertFalse(PptResumeLogic.badgeVisible(video.copy(videoSec = 4.9), now))
        // 分步在第一页第一步不显示
        assertFalse(PptResumeLogic.badgeVisible(PptResumeRecord(PptPlayMode.STEPS, 0, 0, updatedAtMs = now), now))
        assertTrue(PptResumeLogic.badgeVisible(PptResumeRecord(PptPlayMode.STEPS, 1, 0, updatedAtMs = now), now))
        assertTrue(PptResumeLogic.badgeVisible(PptResumeRecord(PptPlayMode.STEPS, 0, 2, updatedAtMs = now), now))
    }

    @Test
    fun shouldResume_matrix() {
        val counts = stepCounts()
        // 视频：>5s 且未到 95%
        assertTrue(
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.VIDEO, videoSec = 30.0, updatedAtMs = now),
                now,
                counts,
                videoDurationSec = 100.0,
            ),
        )
        assertFalse(
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.VIDEO, videoSec = 4.0, updatedAtMs = now),
                now,
                counts,
                videoDurationSec = 100.0,
            ),
        )
        assertFalse(
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.VIDEO, videoSec = 96.0, updatedAtMs = now),
                now,
                counts,
                videoDurationSec = 100.0,
            ),
        )
        // 时长未知：>5s 即可续播
        assertTrue(
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.VIDEO, videoSec = 30.0, updatedAtMs = now),
                now,
                counts,
            ),
        )
        // 分步：不在第一步、不在末步
        assertTrue(
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.STEPS, page = 1, step = 1, updatedAtMs = now),
                now,
                counts,
            ),
        )
        assertFalse( // 第一步
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.STEPS, page = 0, step = 0, updatedAtMs = now),
                now,
                counts,
            ),
        )
        assertFalse( // 末步
            PptResumeLogic.shouldResume(
                PptResumeRecord(PptPlayMode.STEPS, page = 2, step = 1, updatedAtMs = now),
                now,
                counts,
            ),
        )
        assertFalse(PptResumeLogic.shouldResume(null, now, counts))
    }

    @Test
    fun resumeHintText() {
        assertEquals(
            "续播 12:34",
            PptResumeLogic.resumeHint(PptResumeRecord(PptPlayMode.VIDEO, videoSec = 754.0, updatedAtMs = now)),
        )
        assertEquals(
            "续播 第 5 页",
            PptResumeLogic.resumeHint(PptResumeRecord(PptPlayMode.STEPS, page = 4, updatedAtMs = now)),
        )
    }

    @Test
    fun resumeCodec_roundTripAndBadData() {
        val video = PptResumeRecord(PptPlayMode.VIDEO, page = 3, step = 1, videoSec = 42.5, updatedAtMs = now)
        val decoded = PptResumeCodec.decode(PptResumeCodec.encode(video))!!
        assertEquals(video, decoded)

        val steps = PptResumeRecord(PptPlayMode.STEPS, page = 7, step = 2, updatedAtMs = now - 5)
        assertEquals(steps, PptResumeCodec.decode(PptResumeCodec.encode(steps)))

        assertNull(PptResumeCodec.decode(null))
        assertNull(PptResumeCodec.decode(""))
        assertNull(PptResumeCodec.decode("oops"))
        assertNull(PptResumeCodec.decode("""{"m":"x","at":1}"""))
        assertNull(PptResumeCodec.decode("""{"m":"v","at":0}""")) // 无时间戳 = 无效
        // 缺字段容错：p/st/t 默认 0
        val minimal = PptResumeCodec.decode("""{"m":"s","at":$now}""")!!
        assertEquals(PptPlayMode.STEPS, minimal.mode)
        assertEquals(0, minimal.page)
        assertEquals(0, minimal.step)
        assertEquals(0.0, minimal.videoSec, 1e-9)
    }

    // ------------------------------------------------------------ 降级状态机

    @Test
    fun videoHealth_errorRetryOnceThenSwitch() {
        val health = PptVideoHealth()
        assertEquals(PptVideoHealth.Action.RETRY, health.onError())
        assertEquals(PptVideoHealth.Action.SWITCH_TO_STEPS, health.onError())
    }

    @Test
    fun videoHealth_stallTwiceThenRetryThenSwitch() {
        val health = PptVideoHealth(stallTimeoutMs = 4_000L, maxStalls = 2, maxRetries = 1)
        var t = 0L
        fun tick(deltaMs: Long, positionMs: Long, playing: Boolean = true): PptVideoHealth.Action {
            t += deltaMs
            return health.onTick(t, positionMs, isPlaying = playing)
        }
        health.onStart(t)
        // 正常出进度
        assertEquals(PptVideoHealth.Action.NONE, tick(500, 100))
        assertEquals(PptVideoHealth.Action.NONE, tick(500, 600))
        // 卡住 4s → 第 1 次卡顿
        assertEquals(PptVideoHealth.Action.NONE, tick(4_000, 600))
        // 再卡 4s → 达到 2 次 → 重试一次
        assertEquals(PptVideoHealth.Action.RETRY, tick(4_000, 600))
        // 重试后仍持续卡住 → 第一次 tick 因进度归零不算卡，之后两次卡顿 → 切分步
        health.onStart(t)
        assertEquals(PptVideoHealth.Action.NONE, tick(4_000, 0))
        assertEquals(PptVideoHealth.Action.NONE, tick(4_000, 0))
        assertEquals(PptVideoHealth.Action.SWITCH_TO_STEPS, tick(4_000, 0))
    }

    @Test
    fun videoHealth_progressResetsStalls_andPauseNotStall() {
        val health = PptVideoHealth()
        var t = 100L
        fun tick(deltaMs: Long, positionMs: Long, playing: Boolean = true): PptVideoHealth.Action {
            t += deltaMs
            return health.onTick(t, positionMs, isPlaying = playing)
        }
        health.onStart(t)
        assertEquals(PptVideoHealth.Action.NONE, tick(4_000, 0)) // 第 1 次
        // 进度恢复 → 清零
        assertEquals(PptVideoHealth.Action.NONE, tick(500, 500))
        assertEquals(PptVideoHealth.Action.NONE, tick(4_000, 500)) // 又第 1 次
        // 暂停状态卡住不算卡顿
        assertEquals(PptVideoHealth.Action.NONE, tick(10_000, 500, playing = false))
    }
}
