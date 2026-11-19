package com.furina.classtools.ui.screens.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.furina.classtools.BuildConfig

/**
 * 法律文本（隐私政策 / 用户协议）**完整版**入口（2026-11-19 用户要求）。
 *
 * 背景：端内 [PrivacyTexts] 是合规摘要（弹层展示，离线可用）；正式全文由网页仓维护并部署在 ECS：
 * `legal/android/privacy.html` / `legal/android/terms.html`（文档版本 V1.0 · 生效 2026-11-17）。
 *
 * 本对象只做**纯字符串拼装**（可单测），网络与 Intent 由 [LegalLinkOpener] 负责：
 * - 基址复用 `BuildConfig.WEB_BASE_URL`（debug = `http://121.196.47.2`，release = `https://tool.furina.fun`），
 *   不在代码里硬编码明文 IP，release 的「https + 无明文 IP」闸门（`:app:verifyReleaseEndpoints`）口径不变；
 * - 用系统浏览器 `ACTION_VIEW` 打开：本 App 不发起该请求，因此不受 release 的 cleartext 限制；
 * - 打不开（无浏览器 / 被拦截）时 [LegalLinkOpener] 复制链接并提示，摘要弹层照常可用 = **失败兜底**。
 */
object LegalLinks {

    /** 全文文档版本（与 legal/android/&ast;.html 页头「文档版本 / 生效日期」一致）。 */
    const val DOC_VERSION: String = "V1.0"

    /** 全文生效日期（网页原文：2026年11月17日）。 */
    const val EFFECTIVE_DATE: String = "2026-11-17"

    /** 隐私政策全文路径（相对网页站根）。 */
    const val PATH_PRIVACY: String = "legal/android/privacy.html"

    /** 用户协议全文路径（相对网页站根）。 */
    const val PATH_TERMS: String = "legal/android/terms.html"

    /** 摘要与全文的版本说明（端内摘要、弹层副标题、我的页行副标题共用）。 */
    val VERSION_LABEL: String = "$DOC_VERSION · 生效 $EFFECTIVE_DATE"

    /** 拼接基址与路径：基址可带/不带结尾 `/`，路径可带/不带前导 `/`，结果恰好一个 `/`。 */
    fun url(base: String, path: String): String {
        val left = base.trim().trimEnd('/')
        val right = path.trim().trimStart('/')
        if (left.isEmpty()) return "/$right"
        if (right.isEmpty()) return left
        return left + "/" + right
    }

    fun privacyUrl(base: String = BuildConfig.WEB_BASE_URL): String = url(base, PATH_PRIVACY)

    fun termsUrl(base: String = BuildConfig.WEB_BASE_URL): String = url(base, PATH_TERMS)

    /** 展示用的主机 + 路径（弹层/兜底提示用；不显示查询串，永远没有敏感信息）。 */
    fun displayPath(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        return withoutScheme.trimEnd('/')
    }
}

/**
 * 用系统浏览器打开全文；失败（无浏览器 / Intent 被拒）返回 false，由调用方做兜底（复制链接 + 提示）。
 *
 * 只用 `ACTION_VIEW` + `CATEGORY_BROWSABLE`：不新增权限、不写 WebView、不触发明文网络限制。
 */
object LegalLinkOpener {

    /** 构造打开外部链接的 Intent（纯构造，便于测试/复用）。 */
    fun intent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 打开全文；成功返回 true。异常一律吞掉（法律页打不开不能影响主流程）。 */
    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            context.startActivity(intent(url))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 兜底：把链接复制到剪贴板（用户可手动粘贴到浏览器）。 */
    fun copy(context: Context, url: String): Boolean = try {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager == null) {
            false
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("协议完整版链接", url))
            true
        }
    } catch (_: Throwable) {
        false
    }

    /**
     * 打开全文，失败时复制链接 + Toast 提示（**统一兜底口径**，我的页 / 身份页 / 摘要弹层共用）。
     *
     * @return true = 已交给浏览器；false = 已走兜底（复制成功或失败）。
     */
    fun openOrCopy(context: Context, url: String): Boolean {
        if (open(context, url)) return true
        val copied = copy(context, url)
        val message = if (copied) {
            "未能打开浏览器，链接已复制"
        } else {
            "未能打开浏览器：" + LegalLinks.displayPath(url)
        }
        runCatching { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        return false
    }
}
