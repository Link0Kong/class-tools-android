package com.furina.classtools.features.ppt

import com.furina.classtools.BuildConfig

/**
 * PPT 在线媒体 ECS `/pptfiles` 缓存映射（2026-11-18 迁移优化；与网页 `pptEcsUrl()`/`resolveMediaUrl()` 同口径）。
 *
 * 背景：PPT 媒体（分步图 WebP、缩略图、原样播放 MP4、media_files 单独下载音视频、下载包）存放在
 * 阿里云 OSS 香港 `tool-furina-fun.oss-cn-hongkong.aliyuncs.com/ppt/` 下媒体；跨境首访很慢（实测 13.6MB 视频 35s）。
 * ECS `/pptfiles` 反向代理 + 5GiB LRU 缓存命中后 6–11MB/s（支持 GET/HEAD/Range 206，视频边下边播可用）。
 *
 * 映射规则（仅作用于在线播放/预览/下载 URL，manifest 归一化后统一经 [PptManifest.resolveImage] 走这里）：
 * - 仅当 host = [OSS_HOST] 且 path 以 `/ppt/` 开头时映射；
 * - 目标：[BuildConfig.WEB_BASE_URL] + `/pptfiles` + path（debug = `http://121.196.47.2` 本机调试，
 *   release = `https://tool.furina.fun` 正式域名，2026-11-23 备案已通过）；
 * - `data:` / `file:` / `content:` / 本地路径 / 其它域名 / 非 `/ppt/` 路径 → 原样返回（不误伤外链/内嵌资源）；
 * - 编码路径与 `?query` 原样保留（Caddy 解码后由缓存服务按 OSS pathname 命中；Range 由既有播放器/OkHttp 支持）。
 *
 * 回滚（ECS 缓存故障 / 需要回退 OSS 直链）：把 [ENABLED] 置为 `false` 重新构建即可（其它代码无需改）。
 */
object PptEcsUrl {

    /** 唯一被映射的 OSS Bucket host（其它域名一律原样）。 */
    const val OSS_HOST: String = "tool-furina-fun.oss-cn-hongkong.aliyuncs.com"

    /** 仅映射 OSS 桶内 `/ppt/` 资产（与网页一致）。 */
    const val PPT_PATH_PREFIX: String = "/ppt/"

    /** ECS 缓存路由前缀（Caddy `handle /pptfiles/...` → strip 后反代 127.0.0.1:8092）。 */
    const val ECS_PATH_PREFIX: String = "/pptfiles"

    /** 回滚开关：false = 全部原样返回（OSS 直链，旧行为）。 */
    const val ENABLED: Boolean = true

    /** 默认 ECS 缓存基址：WEB_BASE_URL + `/pptfiles`（debug=本机调试 IP，release=https 正式域名）。 */
    fun defaultBase(): String {
        val web = BuildConfig.WEB_BASE_URL.trim().trimEnd('/')
        return if (web.isEmpty()) "" else web + ECS_PATH_PREFIX
    }

    /**
     * URL 映射（纯函数；单测可注入 [ecsBase]）。
     *
     * @param url 原始媒体地址（可空/相对/data:/其它域名）
     * @param ecsBase ECS 缓存基址（默认 [defaultBase]；空串 = 不映射）
     * @param enabled 全局开关（默认 [ENABLED]）
     */
    fun map(url: String?, ecsBase: String = defaultBase(), enabled: Boolean = ENABLED): String {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty() || !enabled) return raw
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd <= 0) return raw // data:/file:/content:/相对路径 → 原样
        val scheme = raw.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return raw
        val afterScheme = raw.substring(schemeEnd + 3)
        val hostEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val hostPort = if (hostEnd < 0) afterScheme else afterScheme.substring(0, hostEnd)
        val host = hostPort.substringBefore(':').lowercase()
        if (host != OSS_HOST) return raw
        val afterHost = if (hostEnd < 0) "" else afterScheme.substring(hostEnd)
        val pathEnd = afterHost.indexOfFirst { it == '?' || it == '#' }.let { if (it < 0) afterHost.length else it }
        val path = afterHost.substring(0, pathEnd)
        if (!path.startsWith(PPT_PATH_PREFIX)) return raw
        val suffix = afterHost.substring(pathEnd).substringBefore('#') // 保留 ?query；#fragment 丢弃（网页同口径）
        val base = ecsBase.trim().trimEnd('/')
        if (base.isEmpty()) return raw
        return base + path + suffix
    }
}
