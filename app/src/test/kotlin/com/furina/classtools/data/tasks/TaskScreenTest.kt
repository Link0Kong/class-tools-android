package com.furina.classtools.data.tasks

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.furina.classtools.FakeTransport
import com.furina.classtools.data.homework.HomeworkRow
import com.furina.classtools.ui.screens.homework.HomeworkScreen
import com.furina.classtools.testApiClient
import com.furina.classtools.ui.screens.tasks.TaskScreen
import com.furina.classtools.ui.screens.tasks.ImportSharedTask
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-w400dp-h800dp-port-mdpi", instrumentedPackages = ["com.vivo.push.sdk"])
class TaskScreenTest {
    @Test fun quickAddCompleteReopenAndOpenHomework() = exerciseTaskScreen(1f)
    @Test fun quickAddCompleteReopenWithReducedMotion() = exerciseTaskScreen(0f)
    @Test fun sharedLinkDialogAcceptsValidInputOnlyOnPreview() = exerciseTaskScreen(1f, testSharedImport = true)

    @Test fun oldSourceShowsExactHomeworkWithFullRequirements() = exerciseTaskScreen(0f, testHomeworkSource = true)

    @Test
    @Config(qualifiers = "zh-w1200dp-h800dp-land-mdpi")
    fun tabletKeepsCurrentTaskAcrossSplitScreen() = exerciseTaskScreen(0f, tablet = true)

    private fun exerciseTaskScreen(animationScale: Float, testSharedImport: Boolean = false, testHomeworkSource: Boolean = false, tablet: Boolean = false) {
        // The dropdown tracks its anchor on every vsync. Advance finite frames while it is open.
        ShadowChoreographer.setPaused(true)
        val frameDuration = Duration.ofMillis(16)
        ShadowChoreographer.setFrameDelay(frameDuration)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        android.provider.Settings.Global.putFloat(activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, animationScale)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val store = TaskPreferences(activity.getSharedPreferences("tasks_test_3260799999", 0))
        val repository = TaskRepository("3260799999", store, testApiClient(FakeTransport { error("offline") }), scope) { false }
        val viewport = mutableIntStateOf(1100)
        if (tablet) repository.edit { _, now ->
            listOf("整理读书笔记", "提交实验报告").mapIndexed { index, title ->
                newTask(title, now + index, index.toDouble()).withFields {
                    put("notes", "详情 · " + title)
                    put("starred", true)
                }
            }
        }
        var opened = false
        var preview: SharedTask? = null
        var canceled = false
        activity.setContent {
            if (testHomeworkSource) {
                val source = HomeworkRow("old-assignment", subject = "高等数学", content = "积分练习\n提交完整证明",
                    date = "2023-09-12", time = "18:00", author = "教师")
                HomeworkScreen(onBack = {}, session = null, marksRepository = null, homeworkRepository = null,
                    initialRows = listOf(source), skipLoad = true, initialDetailId = source.id, importedHomework = setOf(source.id),
                    onAddTask = { opened = true })
            } else if (testSharedImport) ImportSharedTask({ canceled = true }, { preview = it })
            else if (tablet) Box(Modifier.requiredWidth(viewport.intValue.dp)) {
                TaskScreen(repository, onBack = {}, onHomework = {}, homeworkOverview = { Text("班级作业示例") })
            } else TaskScreen(repository, onBack = {}, onHomework = { opened = true })
        }
        val decor = activity.window.decorView
        fun frame() {
            repeat(4) {
                ShadowSystemClock.advanceBy(frameDuration)
                shadowOf(Looper.getMainLooper()).idle()
            }
            val width = if (tablet) 1200 else 800
            val height = if (tablet) 800 else 1600
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, width, height)
            ShadowSystemClock.advanceBy(frameDuration)
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun roots(view: View): List<RootForTest> = when (view) {
            is RootForTest -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { roots(view.getChildAt(it)) }
            else -> emptyList()
        }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun find(label: String, predicate: (SemanticsNode) -> Boolean): SemanticsNode {
            var tree = emptyList<SemanticsNode>()
            repeat(6) {
                frame()
                tree = android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed().flatMap { roots(it) }
                    .flatMap { nodes(it.semanticsOwner.rootSemanticsNode) }
                tree.firstOrNull(predicate)?.let { return it }
            }
            error("Missing " + label + " in " + tree.mapNotNull { it.config.getOrNull(SemanticsProperties.Text) }.joinToString())
        }
        fun click(text: String) {
            val node = find(text) { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
                n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
        try {
            if (tablet) {
                fun label(value: String) = find(value) { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
                }
                assertTrue(label("班级作业示例").boundsInRoot.left > label("整理读书笔记").boundsInRoot.left)
                click("整理读书笔记")
                label("详情 · 整理读书笔记")
                click("提交实验报告")
                label("详情 · 提交实验报告")
                find("宽窗详情关闭") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("收起任务详情") == true }
                val listX = label("整理读书笔记").boundsInRoot.left
                val detailPane = find("详情分栏") { it.config.getOrNull(SemanticsProperties.PaneTitle) == "任务详情" }
                assertTrue("详情应在列表右侧", detailPane.boundsInRoot.left > listX)
                viewport.intValue = 620
                label("详情 · 提交实验报告")
                find("窄窗详情返回") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("返回") == true }
                viewport.intValue = 1000
                label("详情 · 提交实验报告")
                click("收起任务详情")
                label("班级作业示例")
                return
            }
            if (testHomeworkSource) {
                for (text in listOf("班级作业详情", "积分练习", "高等数学", "提交完整证明", "2023-09-12 18:00")) {
                    find(text) { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
                }
                click("查看个人任务")
                assertTrue(opened)
                return
            }
            if (testSharedImport) {
                val source = newTask("分享任务", 1, 0.0).withFields {
                    put("steps", buildJsonArray { add(buildJsonObject { put("text", "画图"); put("done", true) }) })
                }
                val link = TaskShareLink.create(source, "学习")
                fun enter(value: String) {
                    val field = find("分享链接输入") { SemanticsActions.SetText in it.config }
                    assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(value)))
                }
                enter("https://example.com")
                click("预览任务")
                assertNull(preview)
                enter(link)
                click("取消")
                assertTrue(canceled)
                assertNull(preview)
                click("预览任务")
                assertTrue(repository.snapshot.value.objects("items").isEmpty())
                assertEquals("分享任务", preview?.title)
                assertEquals("学习", preview?.groupLabel)
                assertEquals(listOf("画图"), preview?.steps)
                return
            }
            val input = find("任务输入框") { SemanticsActions.SetText in it.config }
            assertTrue(input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("模拟任务")))
            click("保存任务")
            assertEquals("模拟任务", repository.snapshot.value.objects("items").single().text("title"))
            val taskList = find("任务列表") {
                SemanticsProperties.VerticalScrollAxisRange in it.config && SemanticsActions.ScrollToIndex in it.config
            }
            assertTrue(taskList.config[SemanticsActions.ScrollToIndex].action!!.invoke(1))
            if (animationScale == 0f) {
                click("模拟任务")
                find("任务详情") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "任务详情" } == true }
                find("截止时间") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "截止时间" } == true }
                click("更多任务操作")
                click("分享可保存链接")
                val chooser = shadowOf(activity).nextStartedActivity
                assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
                val send = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)
                assertEquals(android.content.Intent.ACTION_SEND, send?.action)
                val shared = TaskShareLink.parse(send?.getStringExtra(android.content.Intent.EXTRA_TEXT).orEmpty())
                assertEquals("模拟任务", shared?.title)
                click("编辑任务")
                val field = find("添加步骤") {
                    SemanticsActions.SetText in it.config && it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == "添加步骤" } == true
                }
                assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("画图")))
                click("保存")
                assertEquals("画图", repository.snapshot.value.objects("items").single().objects("steps").single().text("text"))
                click("返回")
                click("展开步骤  0/1")
                click("步骤 · 画图")
                assertTrue(repository.snapshot.value.objects("items").single().objects("steps").single().flag("done"))
            }
            click("标记完成")
            assertTrue(repository.snapshot.value.objects("items").single().flag("done"))
            click("已完成")
            click("标记未完成")
            assertFalse(repository.snapshot.value.objects("items").single().flag("done"))
            click("班级作业")
            assertTrue(opened)
        } finally {
            scope.cancel()
            activity.finish()
        }
    }
}
