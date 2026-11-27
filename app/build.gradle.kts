import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

// 本机 release 签名：keystore.properties 存在时启用（该文件与 key/ 目录不进版本库）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

// OPPO PUSH（官方 SDK V4.0.0）密钥：app/oppo.properties（已加入 .gitignore，不入库）。
// 复制 app/oppo.properties.example → app/oppo.properties 后填写；缺失/空值 → BuildConfig 为空串，
// 客户端运行时安全跳过 init/register（设置页显示「未配置」，不崩溃）。
val oppoPropsFile = file("oppo.properties")
val oppoProps = Properties().apply {
    if (oppoPropsFile.isFile) {
        oppoPropsFile.inputStream().use { load(it) }
    }
}
val oppoAppKey = (oppoProps.getProperty("oppo.appKey") ?: "").trim()
val oppoAppSecret = (oppoProps.getProperty("oppo.appSecret") ?: "").trim()

// OPPO 账号服务 OAuth（账号快捷登录 / 学号绑定）：同一本机文件 app/oppo.properties 的
// oppo.accountAppId / oppo.accountAppKey / oppo.accountScope（与推送 AppKey/AppSecret 可能不同；
// 未配置 → 快捷登录入口隐藏、绑定入口隐藏，其它功能不受影响）。
val oppoAccountAppId = (oppoProps.getProperty("oppo.accountAppId") ?: "").trim()
val oppoAccountAppKey = (oppoProps.getProperty("oppo.accountAppKey") ?: "").trim()
// scope 默认仅 openid（官方文档最小要求）；如需昵称/unionid 由开放平台申请权限后在此覆盖（空格分隔）。
val oppoAccountScope = (oppoProps.getProperty("oppo.accountScope") ?: "").trim().ifBlank { "openid" }

// 荣耀账号服务（honorid-opensdk 8.0.1.351）客户端配置：app/honor.properties（不入库）。
// Client ID 由荣耀 SDK 在运行时传入（Manifest 不需要 meta-data）；Client Secret 只在服务端。
val honorPropsFile = file("honor.properties")
val honorProps = Properties().apply {
    if (honorPropsFile.isFile) {
        honorPropsFile.inputStream().use { load(it) }
    }
}
val honorClientId = (honorProps.getProperty("honor.clientId") ?: "").trim()
val honorScope = (honorProps.getProperty("honor.scope") ?: "").trim().ifBlank { "openid profile" }

// vivo 推送客户端配置：app/vivo.properties（不入库）。AppSecret 只在服务端。
val vivoPropsFile = file("vivo.properties")
val vivoProps = Properties().apply {
    if (vivoPropsFile.isFile) {
        vivoPropsFile.inputStream().use { load(it) }
    }
}
val vivoAppId = (vivoProps.getProperty("vivo.appId") ?: "").trim()
val vivoApiKey = (vivoProps.getProperty("vivo.apiKey") ?: "").trim()

// ============================================================================
// 2026-11-18 自建后端切流（P3）：REST base 三档 + ECS 各服务地址按 buildType 区分
// 契约与切换/回滚步骤：deploy/migration/CLIENTS.md
//
// REST base 优先级（高 → 低）：
//   1) ct.restBaseUrl：显式覆盖。默认值 = CloudBase 地址（与仓库旧配置一致）；
//      只有被改成非默认值（≠ 内置 CloudBase）时才视为「显式覆盖」，两种 buildType 都用它
//      → 老部署脚本/本机自定义配置行为不变。
//   2) ct.restProfile：cloudbase | staging | selfhosted（两种 buildType 强制同档；回滚用）
//   3) ct.restBaseUrlDebug / ct.restBaseUrlRelease：按 buildType 的显式 URL
//   4) buildType 默认档：debug → staging（ECS 影子验证）；release → selfhosted（2026-11-24 P4 切流；
//      切流前该域名由 Caddy 同源反代到 CloudBase，所以新版先发布也不会坏）
// 三档地址可用 ct.restBase.cloudbase / ct.restBase.staging / ct.restBase.selfhosted 覆盖。
// 2026-11-24 P4 切流：release 默认档 = selfhosted；回滚用 -Pct.restProfile=cloudbase 重新构建发版。
// ============================================================================
val REST_CLOUDBASE_DEFAULT = "https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/"
val REST_STAGING_DEFAULT = "http://121.196.47.2/v1/rdb/rest-next/"
val REST_SELFHOSTED_DEFAULT = "https://tool.furina.fun/v1/rdb/rest/"

fun gradleProp(name: String): String? =
    (project.findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() }

// 兼容旧语义：ct.restBaseUrl 仍是「显式 URL」，默认（= 内置 CloudBase）不算覆盖。
val legacyRestUrl = gradleProp("ct.restBaseUrl") ?: REST_CLOUDBASE_DEFAULT
val legacyRestOverride = legacyRestUrl.takeIf { it != REST_CLOUDBASE_DEFAULT }
val restProfileOverride = gradleProp("ct.restProfile")
val restUrlByProfile: Map<String, String> = mapOf(
    "cloudbase" to (gradleProp("ct.restBase.cloudbase") ?: legacyRestUrl),
    "staging" to (gradleProp("ct.restBase.staging") ?: REST_STAGING_DEFAULT),
    "selfhosted" to (gradleProp("ct.restBase.selfhosted") ?: REST_SELFHOSTED_DEFAULT),
)
fun restUrlOfProfile(profile: String, fallback: String): String = restUrlByProfile[profile] ?: fallback

val debugRestUrl = legacyRestOverride
    ?: (if (restProfileOverride != null) restUrlOfProfile(restProfileOverride, REST_STAGING_DEFAULT)
        else (gradleProp("ct.restBaseUrlDebug") ?: REST_STAGING_DEFAULT))
// 2026-11-24 P4 切流：release 默认档改为 selfhosted（正式域名 https://tool.furina.fun/v1/rdb/rest/）。
// 切流前该域名由 Caddy 同源反代到 CloudBase，所以新版先发布不会坏；Caddy 一切即指向 ECS 自建库。
// 回滚：-Pct.restProfile=cloudbase（或把下面的默认值改回 REST_CLOUDBASE_DEFAULT）。
val releaseRestUrl = legacyRestOverride
    ?: (if (restProfileOverride != null) restUrlOfProfile(restProfileOverride, REST_CLOUDBASE_DEFAULT)
        else (gradleProp("ct.restBaseUrlRelease") ?: REST_SELFHOSTED_DEFAULT))
val debugRestProfile = if (legacyRestOverride != null) "custom" else (restProfileOverride ?: "staging")
val releaseRestProfile = if (legacyRestOverride != null) "custom" else (restProfileOverride ?: "selfhosted")

// ----------------------------------------------------------------------------
// ECS 现有服务地址（账号 / 云空间 drop / 更新清单 / 校园地图 / PPT 媒体 / 法律全文）：
// 2026-11-23 网站 ICP 备案已通过（闽ICP备2026042939号-2），正式域名 https://tool.furina.fun
// （阿里云杭州 ECS + Caddy 自动 HTTPS），同源路由 /account、/drop、/android/latest.json、/map/...、
// /pptfiles/...、/legal/...。
//   - release → 一律 https://tool.furina.fun/...（**绝不能出现明文 IP**）
//   - debug   → 保留 http://121.196.47.2 本机调试入口（staging REST + ECS 调试；
//               只有 debug 的 network_security_config 放行明文）
// ct.ecsProfile 仅为兼容旧脚本保留：release 只接受 selfhosted，其它值（含已移除的 ecsIp）直接构建失败。
// 显式覆盖（优先级最高，非默认值 = 覆盖两种 buildType）：
//   ct.accountBaseUrl / ct.cloudspaceBaseUrl / ct.updateBaseUrl / ct.mapBaseUrl / ct.webBaseUrl
//   ct.accountBaseUrlRelease / ct.cloudspaceBaseUrlRelease ... 按 buildType 覆盖
// release 仍由 :app:verifyReleaseEndpoints 断言 5 个地址都落在 https://tool.furina.fun。
// ----------------------------------------------------------------------------
val ECS_HTTP = "http://121.196.47.2" // 仅 debug / 本机调试
val ECS_HTTPS = "https://tool.furina.fun"

val ecsProfileOverride = gradleProp("ct.ecsProfile")
val releaseEcsProfile = ecsProfileOverride ?: "selfhosted"
if (releaseEcsProfile != "selfhosted") {
    throw GradleException(
        "ct.ecsProfile=$releaseEcsProfile 已移除：2026-11-23 备案通过后 release 只能使用 $ECS_HTTPS" +
            "（只接受 selfhosted；明文 IP 仅 debug 构建保留）",
    )
}
val releaseEcsBase = ECS_HTTPS

val accountBaseUrlDefault = "$ECS_HTTPS/account"
val accountUrl = gradleProp("ct.accountBaseUrl")
val accountExplicit = accountUrl?.takeIf { it != accountBaseUrlDefault }
val debugAccountUrl = accountExplicit ?: (gradleProp("ct.accountBaseUrlDebug") ?: "$ECS_HTTP/account")
val releaseAccountUrl = accountExplicit ?: (gradleProp("ct.accountBaseUrlRelease") ?: "$releaseEcsBase/account")

val cloudspaceUrl = gradleProp("ct.cloudspaceBaseUrl")
val debugCloudspaceUrl = cloudspaceUrl ?: "$ECS_HTTP/drop"
val releaseCloudspaceUrl = gradleProp("ct.cloudspaceBaseUrlRelease") ?: cloudspaceUrl ?: "$releaseEcsBase/drop"

val updateUrl = gradleProp("ct.updateBaseUrl")
val debugUpdateUrl = updateUrl ?: "$ECS_HTTP/android/latest.json"
val releaseUpdateUrl = gradleProp("ct.updateBaseUrlRelease") ?: updateUrl ?: "$releaseEcsBase/android/latest.json"

val mapUrl = gradleProp("ct.mapBaseUrl")
val debugMapUrl = mapUrl ?: "$ECS_HTTP/map/campus_final_25d.html"
val releaseMapUrl = gradleProp("ct.mapBaseUrlRelease") ?: mapUrl ?: "$releaseEcsBase/map/campus_final_25d.html"

// PPT 在线媒体 ECS 缓存（2026-11-18 迁移优化）：OSS 香港 /ppt/** → WEB_BASE_URL + /pptfiles/<path>。
// 法律全文（legal/android/*.html）也走 WEB_BASE_URL；release 走 https 正式域名，debug 走本机调试 IP。
// 显式覆盖：ct.webBaseUrl（非默认值 = 覆盖两种 buildType）。
val webBaseUrlOverride = gradleProp("ct.webBaseUrl")
val debugWebBaseUrl = webBaseUrlOverride ?: ECS_HTTP
val releaseWebBaseUrl = gradleProp("ct.webBaseUrlRelease") ?: webBaseUrlOverride ?: releaseEcsBase

/** 生成 BuildConfig String 字面量（转义反斜杠/引号；密钥含特殊字符也不会破坏构建脚本）。 */
fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.furina.classtools"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.furina.classtools"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 37
        versionCode = 187
        versionName = "1.0.0-beta102"

        // OPPO 账号 OAuth 官方要求 application 节点配置 MSP APPID（Authing 文档键名：
        // com.heytap.msp.client.appid）；从 app/oppo.properties 读取，空值不崩溃。
        manifestPlaceholders["oppoAccountAppId"] = oppoAccountAppId
        // vivo 推送 SDK 从 Manifest meta-data 读取 app_id / api_key（官方要求，随 APK 分发）。
        manifestPlaceholders["vivoPushAppId"] = vivoAppId
        manifestPlaceholders["vivoPushApiKey"] = vivoApiKey

        // 公开 Publishable Key（role=anon，设计上公开）。REST/ECS 地址按 buildType 注入（见 buildTypes）。
        val publishableKey = (project.findProperty("ct.publishableKey") as String?).orEmpty()
        buildConfigField("String", "PUBLISHABLE_KEY", buildConfigString(publishableKey))
        buildConfigField("String", "HOME_CONTEXT_BASE_URL", buildConfigString(gradleProp("ct.homeContextBaseUrl").orEmpty()))

        // OPPO PUSH 密钥（app/oppo.properties；空串 = 未配置 → 客户端跳过注册/上传）。
        buildConfigField("String", "OPPO_PUSH_APP_KEY", buildConfigString(oppoAppKey))
        buildConfigField("String", "OPPO_PUSH_APP_SECRET", buildConfigString(oppoAppSecret))

        // OPPO 账号服务 OAuth（app/oppo.properties；空串 = 未配置 → 快捷登录/绑定入口隐藏）。
        buildConfigField("String", "OPPO_ACCOUNT_APP_ID", buildConfigString(oppoAccountAppId))
        buildConfigField("String", "OPPO_ACCOUNT_APP_KEY", buildConfigString(oppoAccountAppKey))
        buildConfigField("String", "OPPO_ACCOUNT_SCOPE", buildConfigString(oppoAccountScope))

        // 小布建议端侧卡片。各项由 OPPO 为本应用的课程场景分配。
        for ((field, key) in mapOf(
            "INTENT" to "intentName", "LAUNCHER" to "launcherServiceId",
            "ENTITY" to "entityName", "MILESTONE_CODE" to "milestoneCode",
            "MILESTONE_TEXT" to "milestoneText",
        )) {
            buildConfigField("String", "OPPO_COURSE_$field",
                buildConfigString(oppoProps.getProperty("oppo.courseCard.$key", "").trim()))
        }

        // 荣耀账号服务（app/honor.properties；空串 = 未配置 → 荣耀入口隐藏）。
        buildConfigField("String", "HONOR_ACCOUNT_CLIENT_ID", buildConfigString(honorClientId))
        buildConfigField("String", "HONOR_ACCOUNT_SCOPE", buildConfigString(honorScope))

        // vivo 推送（app/vivo.properties；空串 = 未配置 → 客户端跳过初始化/注册/上传）。
        buildConfigField("String", "VIVO_PUSH_APP_ID", buildConfigString(vivoAppId))
        buildConfigField("String", "VIVO_PUSH_API_KEY", buildConfigString(vivoApiKey))

        // 测试版本标识（当前为「测试版本 8」）：App 内版本行使用。
        buildConfigField("int", "TEST_VERSION", "21")
        buildConfigField("String", "TEST_VERSION_LABEL", buildConfigString("测试版本"))
    }

    signingConfigs {
        if (keystorePropsFile.isFile) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // 2026-11-18 自建后端切流：调试构建默认连 ECS staging 影子库（http IP，仅 debug 放行明文）。
            // 也用于 ECS 账号/云空间/更新/地图服务的本机 IP 调试入口（2026-11-23 备案已通过，
            // 正式版不出现明文 IP；正式域名见 release）。
            buildConfigField("String", "REST_BASE_URL", buildConfigString(debugRestUrl))
            buildConfigField("String", "REST_PROFILE", buildConfigString(debugRestProfile))
            buildConfigField("String", "ACCOUNT_BASE_URL", buildConfigString(debugAccountUrl))
            buildConfigField("String", "CLOUDSPACE_BASE_URL", buildConfigString(debugCloudspaceUrl))
            buildConfigField("String", "UPDATE_BASE_URL", buildConfigString(debugUpdateUrl))
            buildConfigField("String", "MAP_BASE_URL", buildConfigString(debugMapUrl))
            // PPT 在线媒体 ECS 缓存基址（debug=http IP，release=https 正式域名；见 features/ppt/PptEcsUrl.kt）
            buildConfigField("String", "WEB_BASE_URL", buildConfigString(debugWebBaseUrl))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 只在 keystore.properties 存在时使用正式签名；缺失时保持未签名（不静默回退 debug 签名，
            // 避免把 debug 签名的包当发布包分发；构建/发布脚本应显式校验）。
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // 2026-11-23 备案已通过：release 的 ECS 服务一律走 https://tool.furina.fun 正式域名
            // （不含任何明文 IP）；REST 仍默认 CloudBase，P4 切流前不动（见文件顶部说明）。
            buildConfigField("String", "REST_BASE_URL", buildConfigString(releaseRestUrl))
            buildConfigField("String", "REST_PROFILE", buildConfigString(releaseRestProfile))
            buildConfigField("String", "ACCOUNT_BASE_URL", buildConfigString(releaseAccountUrl))
            buildConfigField("String", "CLOUDSPACE_BASE_URL", buildConfigString(releaseCloudspaceUrl))
            buildConfigField("String", "UPDATE_BASE_URL", buildConfigString(releaseUpdateUrl))
            buildConfigField("String", "MAP_BASE_URL", buildConfigString(releaseMapUrl))
            buildConfigField("String", "WEB_BASE_URL", buildConfigString(releaseWebBaseUrl))
            buildConfigField("String", "HOME_CONTEXT_BASE_URL", buildConfigString(gradleProp("ct.homeContextBaseUrl") ?: releaseEcsBase))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // JVM 单测不 mock android.jar：未 stub 的 Android 方法（如 android.util.Log.w）
            // 返回默认值而不是抛 "not mocked"；纯逻辑单测（OPPO 推送状态机等）可直接覆盖含日志的代码路径。
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * release 构建安全闸门（2026-11-23 备案通过后）：
 * - **REST** 永远必须 https 且不含 staging 明文 IP（CloudBase 网关，或 P4 切流后的
 *   https://tool.furina.fun）；
 * - **ECS 服务**（账号/云空间/更新/地图/PPT 媒体+法律全文）必须全部落在
 *   `https://tool.furina.fun`：严格 https、以正式域名开头、不含 121.196.47.2 明文 IP；
 *   `ct.ecsProfile` 只接受 `selfhosted`（ecsIp 分支已随备案通过移除）。
 * 只做配置校验（不构建 APK / 不打分发包），发布前执行：
 *   .\gradlew.bat :app:verifyReleaseEndpoints
 */
tasks.register("verifyReleaseEndpoints") {
    group = "verification"
    description = "校验 release 的 REST 为 https、5 个 ECS 地址均为 https://tool.furina.fun 正式域名（无明文 IP）"
    // 用 task inputs 传值而不是闭包捕获脚本对象（兼容 org.gradle.configuration-cache）。
    inputs.property("expectedEcsBase", ECS_HTTPS)
    inputs.property("releaseRestUrl", releaseRestUrl)
    inputs.property("releaseAccountUrl", releaseAccountUrl)
    inputs.property("releaseCloudspaceUrl", releaseCloudspaceUrl)
    inputs.property("releaseUpdateUrl", releaseUpdateUrl)
    inputs.property("releaseMapUrl", releaseMapUrl)
    inputs.property("releaseWebBaseUrl", releaseWebBaseUrl)
    inputs.property("releaseRestProfile", releaseRestProfile)
    inputs.property("releaseEcsProfile", releaseEcsProfile)
    inputs.property("debugRestUrl", debugRestUrl)
    inputs.property("debugRestProfile", debugRestProfile)
    doLast {
        val ecsProfile = inputs.properties["releaseEcsProfile"] as String
        val expectedEcsBase = inputs.properties["expectedEcsBase"] as String
        val restUrl = inputs.properties["releaseRestUrl"] as String
        check(ecsProfile == "selfhosted") {
            "release 只接受 ct.ecsProfile=selfhosted（ecsIp 明文分支已移除）：$ecsProfile"
        }
        // REST：始终 https + 不含明文 IP；CloudBase 或 https://tool.furina.fun（P4 切流后）。
        check(restUrl.startsWith("https://")) { "release REST_BASE_URL must be https: $restUrl" }
        check(!restUrl.contains("121.196.47.2")) { "release REST_BASE_URL must not contain staging IP: $restUrl" }
        if (inputs.properties["releaseRestProfile"] == "cloudbase") {
            check(restUrl.contains("tcloudbasegateway.com/v1/rdb/rest/")) {
                "release REST profile=cloudbase but REST_BASE_URL is not CloudBase gateway"
            }
        } else {
            check(restUrl.startsWith("$expectedEcsBase/")) {
                "release REST_BASE_URL must be $expectedEcsBase/ when profile != cloudbase"
            }
        }
        val ecsChecks = listOf(
            "ACCOUNT_BASE_URL" to (inputs.properties["releaseAccountUrl"] as String),
            "CLOUDSPACE_BASE_URL" to (inputs.properties["releaseCloudspaceUrl"] as String),
            "UPDATE_BASE_URL" to (inputs.properties["releaseUpdateUrl"] as String),
            "MAP_BASE_URL" to (inputs.properties["releaseMapUrl"] as String),
            "WEB_BASE_URL" to (inputs.properties["releaseWebBaseUrl"] as String),
        )
        ecsChecks.forEach { (name, url) ->
            check(url.startsWith("https://")) { "release $name must be https: $url" }
            // WEB_BASE_URL 是「基址」（无尾斜杠），其余是完整路径；两者都必须落在正式域名上。
            check(url == expectedEcsBase || url.startsWith("$expectedEcsBase/")) {
                "release $name must point at $expectedEcsBase: $url"
            }
            check(!url.contains("121.196.47.2")) { "release $name must not contain staging IP: $url" }
        }
        println("verifyReleaseEndpoints OK")
        println("  REST_PROFILE=" + (inputs.properties["releaseRestProfile"] as String) +
            "  ECS_PROFILE=" + ecsProfile)
        println("  REST_BASE_URL=$restUrl")
        ecsChecks.forEach { (name, url) -> println("  $name=$url") }
        println("  (debug) REST_BASE_URL=" + (inputs.properties["debugRestUrl"] as String) +
            " REST_PROFILE=" + (inputs.properties["debugRestProfile"] as String))
    }
}

kotlin {
    jvmToolchain(17)
}

// ----------------------------------------------------------------------------
// 任务完成时，显式指定 ct.updateChannel 和 ct.updateNotes，发布合并后的签名包。
// ----------------------------------------------------------------------------
val publishUpdateChannel = tasks.register<Exec>("publishUpdateChannel") {
    group = "release"
    description = "上传 release APK + latest.json 到安卓更新渠道（ECS + OSS）"
    dependsOn("assembleRelease")
    notCompatibleWithConfigurationCache("发布任务会调用外部 Python 脚本并读取工作区路径")
    val scriptFile = rootProject.projectDir.parentFile.resolve("web/deploy/android_release.py")
    val apkFile = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val versionNameValue = android.defaultConfig.versionName ?: "unknown"
    val versionCodeValue = android.defaultConfig.versionCode ?: 0
    val updateNotes = providers.gradleProperty("ct.updateNotes").orElse(versionNameValue).get()
    val updateChannel = providers.gradleProperty("ct.updateChannel").orElse("beta").get()
    val pythonExe = providers.gradleProperty("ct.python").orElse("python").get()
    val skipUpdate = providers.gradleProperty("ct.skipUpdateChannel").orNull?.toBoolean() ?: false
    onlyIf {
        !skipUpdate &&
            scriptFile.isFile &&
            apkFile.get().asFile.isFile
    }
    doFirst {
        println("==> 自动发布更新渠道：${versionNameValue} / vc${versionCodeValue}")
        commandLine(
            pythonExe, scriptFile.absolutePath,
            "--apk", apkFile.get().asFile.absolutePath,
            "--version-name", versionNameValue,
            "--version-code", versionCodeValue.toString(),
            "--notes", updateNotes,
            "--channel", updateChannel,
        )
    }
}

// 只认本次命令行显式指定的渠道，避免机器或仓库的 Gradle 默认属性触发上传。
if (!gradle.startParameter.projectProperties["ct.updateChannel"].isNullOrBlank()) {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(publishUpdateChannel)
    }
}

// ----------------------------------------------------------------------------
// OPPOSign（OPPO 开放平台「应用签名」）：每个 release 都必须用官方 OPPOSign.jar 计算。
// OPPOSign.exe 是 JavaFX GUI；scripts/oppo_sign.py 调用 jar 的
// ApkSignatureUtils.getOPPOSign(cert.getEncoded())，无窗口、可进发布流水线。
// Jar 路径可用 -Pct.oppoSignJar=... 覆盖；OPPOSIGN 值写入 app/build/outputs/opposign.txt。
// ----------------------------------------------------------------------------
val oppoSignJar = providers.gradleProperty("ct.oppoSignJar")
    .orElse("C:\\\\Users\\\\Ko_Li\\\\Downloads\\\\cc227743e4c29d116c22d545fe486c29\\\\OPPOSign\\\\bin\\\\OPPOSign.jar")
    .get()
val oppoSignScript = rootProject.projectDir.resolve("scripts/oppo_sign.py")
val computeOppoSign = tasks.register<Exec>("computeOppoSign") {
    group = "release"
    description = "用 OPPO 官方 OPPOSign.jar 计算 release APK 的 OPPOSign"
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
    val pythonExe = providers.gradleProperty("ct.python").orElse("python").get()
    val skip = providers.gradleProperty("ct.skipOppoSign").orNull?.toBoolean() ?: false
    enabled = !skip
    onlyIf { apk.isFile }
    commandLine(
        pythonExe,
        oppoSignScript.absolutePath,
        "--apk", apk.absolutePath,
        "--jar", oppoSignJar,
    )
}
tasks.matching { it.name == "publishUpdateChannel" }.configureEach {
    mustRunAfter(computeOppoSign)
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(computeOppoSign)
}


baselineProfile {
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(project(":baseline-profile"))

    // OPPO PUSH 官方 SDK V4.0.0（本地 AAR，来自 OPPO 官方 openfs 发布；sha256 见 docs/OPPO_PUSH.md）。
    // 纯本地依赖：SDK 自包含（无 gson/okhttp/commons-codec 传递依赖，已用 javap 核对）。
    implementation(files("libs/com.heytap.msp_V4.0.0.aar"))

    // 荣耀账号服务 SDK（官方 honorid-opensdk 8.0.1.351，本地 AAR；OAuth 授权 + 快捷登录）。
    implementation(files("libs/honorid-opensdk-8.0.1.351.aar"))
    // vivo 推送客户端 SDK（官方 vpush_clientSDK 4.1.7.0_517，本地 AAR；含 libs/*.jar 由 AGP 合并）。
    implementation(files("libs/vpush_clientSDK_v4.1.7.0_517.aar"))

    // OPPO 账号服务 OAuth 授权 SDK（Maven Central；账号快捷登录/绑定学号）。
    // 传递依赖：account-oauth-sdk / account-env / account-service-ipc + appcompat(runtime) + viewbinding，
    // 由 Gradle 解析（settings.gradle.kts 已含 mavenCentral；官方要求的 repositories 配置已满足）。
    implementation(libs.oppo.account.oauth.web)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.window)
    implementation(libs.haze)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // 扫一扫：CameraX 相机预览 + ZXing 本机解码（不依赖 GMS；仅扫码时申请相机权限）
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // 金标联盟（ITGSA）SharedView Kit：荣耀/OPPO/vivo/小米系统侧一镜到底无缝转场。
    // 运行时由 ROM 提供实现；不支持的设备 isViewAnimationSupported()==0，业务回退现有 Compose 转场。
    implementation(libs.sharedview.kit)

    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

































