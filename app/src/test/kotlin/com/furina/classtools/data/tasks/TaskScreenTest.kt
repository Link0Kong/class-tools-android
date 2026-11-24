package com.furina.classtools.data.tasks

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import com.furina.classtools.FakeTransport
import com.furina.classtools.testApiClient
import com.furina.classtools.ui.screens.tasks.TaskScreen
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "zh-w400dp-h800dp-port-mdpi", instrumentedPackages = ["com.vivo.push.sdk"])
class TaskScreenTest {
    @Test fun quickAddCompleteReopenAndOpenHomework() = exerciseTaskScreen(1f)
    @Test fun quickAddCompleteReopenWithReducedMotion() = exerciseTaskScreen(0f)

    private fun exerciseTaskScreen(animationScale: Float) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        android.provider.Settings.Global.putFloat(activity.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, animationScale)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val store = TaskPreferences(activity.getSharedPreferences("tasks_test_3260799999", 0))
        val repository = TaskRepository("3260799999", store, testApiClient(FakeTransport { error("offline") }), scope) { false }
        var opened = false
        activity.setContent { TaskScreen(repository, onBack = {}, onHomework = { opened = true }) }
        val decor = activity.window.decorView
        fun frame() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
            decor.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, 800, 1600)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
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
                tree = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { roots(it) }
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
            val input = find("任务输入框") { SemanticsActions.SetText in it.config }
            assertTrue(input.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("模拟任务")))
            click("保存任务")
            assertEquals("模拟任务", repository.snapshot.value.objects("items").single().text("title"))
            find("任务列表") { SemanticsActions.ScrollToIndex in it.config }.config[SemanticsActions.ScrollToIndex].action!!.invoke(1)
            if (animationScale == 0f) {
                click("模拟任务")
                val field = find("添加步骤") {
                    SemanticsActions.SetText in it.config && it.config.getOrNull(SemanticsProperties.Text)?.any { label -> label.text == "添加步骤" } == true
                }
                assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("画图")))
                click("保存")
                assertEquals("画图", repository.snapshot.value.objects("items").single().objects("steps").single().text("text"))
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
