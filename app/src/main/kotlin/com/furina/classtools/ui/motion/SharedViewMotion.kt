package com.furina.classtools.ui.motion

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import com.furina.classtools.ToolActivity
import com.furina.classtools.data.session.Session
import com.furina.classtools.data.tool.AppTool
import com.furina.classtools.ui.navigation.ToolTransitionAnchor
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationExtras
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import com.itgsa.animation.sharedview.ViewAnimationType
import java.lang.ref.WeakReference
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 金标联盟 SharedView Kit 的接入层（一镜到底 / 无缝转场）。
 *
 * ## 为什么需要独立 Activity
 * Kit 的公开入口是 `ActivityOptions`，只能在 **Activity 之间**做系统级共享元素；本 App 其余页面
 * 都在单 Activity 的 Compose NavHost 内。因此只把「工具卡 → 工具页」这一条最匹配示例的路径
 * 提升为 [ToolActivity]：起点工具作为目标 Activity 的首屏，返回时 Kit 回到原卡片。
 *
 * ## 降级边界（与官方 README 一致）
 * - `isViewAnimationSupported() == 0` → 返回 false，调用方继续用现有 Compose 容器变换；
 * - 卡片半遮 / 被裁切 / 无有效 bounds → 返回 false；
 * - `makeViewAnimationOptions == null` / 启动异常 → 返回 false；
 * - 返回时源 Activity 已销毁或屏幕尺寸变化 → 不 override，走系统默认返回，绝不硬套旧坐标。
 *
 * ## 参数口径
 * - 半径单位必须是 **px**：Compose 令牌是 dp，这里乘 density 后再传给 Kit；
 * - 不传 `color`：App 有动态壁纸/玻璃背景，没有稳定底色，传错会在返回收尾出现色圈；
 * - bounds 坐标系按设备 `getViewAnimationExtras()` 的声明动态选择，不按机型硬编码。
 */
object SharedViewMotion {
    private const val TAG = "SharedViewMotion"

    @Volatile
    private var loggedCapability = false

    /** 当前设备协议版本：>0 支持；0 不支持。任何异常都按不支持处理。 */
    fun supportVersion(): Int {
        val version = runCatching { ViewAnimationManager.isViewAnimationSupported() }.getOrDefault(0)
        if (!loggedCapability) {
            loggedCapability = true
            Log.i(TAG, "SharedView capability protocol=$version")
        }
        return version
    }

    fun isSupported(): Boolean = supportVersion() > 0

    /**
     * 尝试用 Kit 从工具卡启动 [ToolActivity]。
     *
     * @return true = 已成功 `startActivity`（调用方不要再导航）；false = 调用方走原 Compose 转场。
     */
    fun tryLaunchTool(
        activity: Activity?,
        rootView: View,
        tool: AppTool,
        anchor: ToolTransitionAnchor,
        session: Session,
    ): Boolean {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return false
        if (supportVersion() <= 0) return false
        if (rootView.width <= 0 || rootView.height <= 0) return false
        if (!SharedViewBounds.isFullyVisible(anchor.bounds, rootView.width, rootView.height)) {
            Log.i(TAG, "skip: card not fully visible tool=${tool.id}")
            return false
        }

        val extras = runCatching { ViewAnimationManager.getViewAnimationExtras() }.getOrNull()
        val coordinateToken = SharedViewBounds.parseCoordinateToken(
            extras?.get(ViewAnimationExtras.EXTRA_BOUNDS),
        )
        val rootLocation = IntArray(2)
        if (coordinateToken == SharedViewBounds.WINDOW) {
            rootView.getLocationInWindow(rootLocation)
        } else {
            rootView.getLocationOnScreen(rootLocation)
        }
        val sourceRect = SharedViewBounds.toKitBounds(anchor.bounds, rootLocation[0], rootLocation[1])
        val kitRect = sourceRect.toAndroidRect()
        val bitmap = runCatching { captureRegion(rootView, anchor.bounds) }.getOrNull()
        if (bitmap == null) {
            Log.w(TAG, "skip: capture failed tool=${tool.id}")
            return false
        }

        val radiusPx = anchor.radius.value * rootView.resources.displayMetrics.density
        val bundle = runCatching { buildBundle(extras, radiusPx) }.getOrElse {
            Log.w(TAG, "skip: build bundle failed", it)
            bitmap.recycle()
            return false
        }
        val listener = object : ViewAnimationListener {
            override fun onAnimationStart(animMode: Int) {
                Log.i(TAG, "animation start mode=$animMode tool=${tool.id}")
            }

            override fun onAnimationEnd(animMode: Int) {
                Log.i(TAG, "animation end mode=$animMode tool=${tool.id}")
            }

            override fun onAnimationCancel(animMode: Int) {
                Log.i(TAG, "animation cancel mode=$animMode tool=${tool.id}")
            }
        }
        val options = runCatching {
            ViewAnimationManager.makeViewAnimationOptions(bitmap, kitRect, radiusPx, bundle, listener)
        }.getOrNull()
        if (options == null) {
            Log.w(TAG, "skip: options null tool=${tool.id}")
            bitmap.recycle()
            return false
        }

        SharedViewReturnStore.begin(
            source = activity,
            bounds = kitRect,
            radiusPx = radiusPx,
        )
        return try {
            val intent = ToolActivity.createIntent(activity, tool.id, pick = false, session = session)
            activity.startActivity(intent, options.toBundle())
            true
        } catch (t: Throwable) {
            Log.w(TAG, "skip: startActivity failed tool=${tool.id}", t)
            SharedViewReturnStore.clear()
            false
        }
    }

    /**
     * 目标 Activity 返回前调用：把返回落点刷新为原工具卡 bounds，再 `finish()`。
     * 必须在 `onCreate` 之后、`finish()` 之前调用（官方 README §返回前刷新配置）。
     */
    fun finishWithSharedView(activity: Activity) {
        if (activity.isFinishing) return
        val session = SharedViewReturnStore.take()
        if (session != null && supportVersion() > 0) {
            val source = session.source.get()
            val alive = source != null && !source.isDestroyed && !source.isFinishing
            val metrics = source?.resources?.displayMetrics
            val reusable = alive && metrics != null && SharedViewBounds.canReuseReturnBounds(
                sourceAlive = true,
                sourceWidth = metrics.widthPixels,
                sourceHeight = metrics.heightPixels,
                recordedWidth = session.screenWidth,
                recordedHeight = session.screenHeight,
            )
            if (reusable) {
                runCatching {
                    val config = ViewAnimationConfig()
                        .setBounds(session.bounds)
                        .setRadius(session.radiusPx)
                    ViewAnimationManager.overrideViewAnimationTransition(activity, config.toBundle())
                    Log.i(TAG, "return override bounds=${session.bounds}")
                }.onFailure {
                    // override 失败绝不能阻断 finish（否则页面卡死）。
                    Log.w(TAG, "return override failed, finish without override", it)
                }
            } else {
                Log.i(TAG, "return fallback: source alive=$alive size changed")
            }
        }
        activity.finish()
    }

    /** 仅测试/诊断用：当前是否有待消费的返回会话。 */
    internal fun hasPendingReturnSession(): Boolean = SharedViewReturnStore.peek() != null

    private fun buildBundle(extras: Map<String, String>?, radiusPx: Float): android.os.Bundle {
        val bundle = ViewAnimationConfig().setRadius(radiusPx).toBundle()
        // 设备声明支持 fallback 参数时，给「目标丢失/截图异常」一个中心收缩兜底；不支持就不写。
        val fallbackMeta = extras?.get(ViewAnimationExtras.EXTRA_FALLBACK_ANIM_TYPE)
        if (!fallbackMeta.isNullOrBlank() && supportsExplodeOut(fallbackMeta)) {
            bundle.putInt(
                ViewAnimationExtras.EXTRA_FALLBACK_ANIM_TYPE,
                ViewAnimationType.VIEW_ANIM_EXPLODE_OUT,
            )
        }
        return bundle
    }

    /**
     * metadata 形如 `int|VIEW_ANIM_DEFAULT,VIEW_ANIM_NONE|desc`。
     * 只有明确列出支持集合且不含 EXPLODE_OUT 时放弃；单纯 `int` = 可写但集合未知，允许写推荐值。
     */
    private fun supportsExplodeOut(meta: String): Boolean {
        val parts = meta.split("|", limit = 3)
        if (parts.size >= 2 && parts[1].contains("VIEW_ANIM_")) {
            return parts[1].split(",").any { it.trim() == "VIEW_ANIM_EXPLODE_OUT" }
        }
        return true
    }

    /** 把根 View 的卡片区域画进一张软件 Bitmap（工具卡是静态玻璃卡，不依赖 SurfaceView）。 */
    private fun captureRegion(rootView: View, bounds: androidx.compose.ui.geometry.Rect): Bitmap? {
        val left = floor(bounds.left).toInt()
        val top = floor(bounds.top).toInt()
        val width = bounds.width.roundToInt().coerceAtLeast(1)
        val height = bounds.height.roundToInt().coerceAtLeast(1)
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-left.toFloat(), -top.toFloat())
        rootView.draw(canvas)
        return bitmap
    }
}

/**
 * 起点卡片 → 目标 Activity 的返回会话（进程内）。
 *
 * 只持弱引用，MainActivity 被系统回收后不再强行播放返回动画；bounds 是启动瞬间的快照，
 * 因为目标 Activity 打开期间源页不可交互，不会被滚动改变。
 */
internal object SharedViewReturnStore {
    private var session: ReturnSession? = null

    fun begin(source: Activity, bounds: android.graphics.Rect, radiusPx: Float) {
        val metrics = source.resources.displayMetrics
        session = ReturnSession(
            source = WeakReference(source),
            bounds = android.graphics.Rect(bounds),
            radiusPx = radiusPx,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
        )
    }

    fun peek(): ReturnSession? = session

    fun take(): ReturnSession? {
        val current = session
        session = null
        return current
    }

    fun clear() {
        session = null
    }
}

internal data class ReturnSession(
    val source: WeakReference<Activity>,
    val bounds: android.graphics.Rect,
    val radiusPx: Float,
    val screenWidth: Int,
    val screenHeight: Int,
)
