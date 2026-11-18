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
//   4) buildType 默认档：debug → staging（ECS 影子验证）；release → selfhosted（备案后正式域名）
// 三档地址可用 ct.restBase.cloudbase / ct.restBase.staging / ct.restBase.selfhosted 覆盖。
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
val releaseRestUrl = legacyRestOverride
    ?: (if (restProfileOverride != null) restUrlOfProfile(restProfileOverride, REST_SELFHOSTED_DEFAULT)
        else (gradleProp("ct.restBaseUrlRelease") ?: REST_SELFHOSTED_DEFAULT))
val debugRestProfile = if (legacyRestOverride != null) "custom" else (restProfileOverride ?: "staging")
val releaseRestProfile = if (legacyRestOverride != null) "custom" else (restProfileOverride ?: "selfhosted")

// ----------------------------------------------------------------------------
// ECS 现有服务地址（账号 / 云空间 drop / 更新清单 / 校园地图）：
// 备案前只有 IP 入口（debug 构建继续用 http IP）；备案 + HTTPS 后 release 构建走正式域名。
// 显式覆盖：ct.accountBaseUrl / ct.cloudspaceBaseUrl / ct.updateBaseUrl / ct.mapBaseUrl
// （非默认值 = 覆盖两种 buildType）。
// ----------------------------------------------------------------------------
val ECS_HTTP = "http://121.196.47.2"
val ECS_HTTPS = "https://tool.furina.fun"

val accountBaseUrlDefault = "$ECS_HTTP/account"
val accountUrl = gradleProp("ct.accountBaseUrl")
val accountExplicit = accountUrl?.takeIf { it != accountBaseUrlDefault }
val debugAccountUrl = accountExplicit ?: (gradleProp("ct.accountBaseUrlDebug") ?: accountBaseUrlDefault)
val releaseAccountUrl = accountExplicit ?: (gradleProp("ct.accountBaseUrlRelease") ?: "$ECS_HTTPS/account")

val cloudspaceUrlDefault = "http://121.196.47.2/drop"
val cloudspaceUrl = gradleProp("ct.cloudspaceBaseUrl")
val debugCloudspaceUrl = cloudspaceUrl ?: "$ECS_HTTP/drop"
val releaseCloudspaceUrl = cloudspaceUrl ?: "$ECS_HTTPS/drop"

val updateUrlDefault = "http://121.196.47.2/android/latest.json"
val updateUrl = gradleProp("ct.updateBaseUrl")
val debugUpdateUrl = updateUrl ?: "$ECS_HTTP/android/latest.json"
val releaseUpdateUrl = updateUrl ?: "$ECS_HTTPS/android/latest.json"

val mapUrlDefault = "http://121.196.47.2/map/campus_final_25d.html"
val mapUrl = gradleProp("ct.mapBaseUrl")
val debugMapUrl = mapUrl ?: "$ECS_HTTP/map/campus_final_25d.html"
val releaseMapUrl = mapUrl ?: "$ECS_HTTPS/map/campus_final_25d.html"

// PPT 在线媒体 ECS 缓存（2026-11-18 迁移优化）：OSS 香港 /ppt/** → WEB_BASE_URL + /pptfiles/<path>。
// debug 默认 http IP（备案前调试入口，配合 debug 的明文放行）；release 默认 https 正式域名。
// 显式覆盖：ct.webBaseUrl（非默认值 = 覆盖两种 buildType）。
val webBaseUrlOverride = gradleProp("ct.webBaseUrl")
val debugWebBaseUrl = webBaseUrlOverride ?: ECS_HTTP
val releaseWebBaseUrl = webBaseUrlOverride ?: ECS_HTTPS

/** 生成 BuildConfig String 字面量（转义反斜杠/引号；密钥含特殊字符也不会破坏构建脚本）。 */
fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.furina.classtools"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.furina.classtools"
        minSdk = 26
        targetSdk = 37
        versionCode = 87
        // 测试版本 4：1.0.0-beta4（「我的 → 关于」显示「测试版本 4」）。
        versionName = "1.0.0-beta4"

        // OPPO 账号 OAuth 官方要求 application 节点配置 MSP APPID（Authing 文档键名：
        // com.heytap.msp.client.appid）；从 app/oppo.properties 读取，空值不崩溃。
        manifestPlaceholders["oppoAccountAppId"] = oppoAccountAppId

        // 公开 Publishable Key（role=anon，设计上公开）。REST/ECS 地址按 buildType 注入（见 buildTypes）。
        val publishableKey = (project.findProperty("ct.publishableKey") as String?).orEmpty()
        buildConfigField("String", "PUBLISHABLE_KEY", buildConfigString(publishableKey))

        // OPPO PUSH 密钥（app/oppo.properties；空串 = 未配置 → 客户端跳过注册/上传）。
        buildConfigField("String", "OPPO_PUSH_APP_KEY", buildConfigString(oppoAppKey))
        buildConfigField("String", "OPPO_PUSH_APP_SECRET", buildConfigString(oppoAppSecret))

        // OPPO 账号服务 OAuth（app/oppo.properties；空串 = 未配置 → 快捷登录/绑定入口隐藏）。
        buildConfigField("String", "OPPO_ACCOUNT_APP_ID", buildConfigString(oppoAccountAppId))
        buildConfigField("String", "OPPO_ACCOUNT_APP_KEY", buildConfigString(oppoAccountAppKey))
        buildConfigField("String", "OPPO_ACCOUNT_SCOPE", buildConfigString(oppoAccountScope))

        // 测试版本标识（当前为「测试版本 4」）：App 内版本行使用。
        buildConfigField("int", "TEST_VERSION", "4")
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
            // 也用于 ECS 账号/云空间/更新/地图服务的备案前 IP 调试入口。
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
            // 2026-11-18 自建后端切流：release 默认走备案后 HTTPS 正式域名（不含 staging 明文 IP；
            // 备案前请勿发布 release 构建，或 -Pct.restProfile=cloudbase 回滚到 CloudBase）。
            buildConfigField("String", "REST_BASE_URL", buildConfigString(releaseRestUrl))
            buildConfigField("String", "REST_PROFILE", buildConfigString(releaseRestProfile))
            buildConfigField("String", "ACCOUNT_BASE_URL", buildConfigString(releaseAccountUrl))
            buildConfigField("String", "CLOUDSPACE_BASE_URL", buildConfigString(releaseCloudspaceUrl))
            buildConfigField("String", "UPDATE_BASE_URL", buildConfigString(releaseUpdateUrl))
            buildConfigField("String", "MAP_BASE_URL", buildConfigString(releaseMapUrl))
            buildConfigField("String", "WEB_BASE_URL", buildConfigString(releaseWebBaseUrl))
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
 * release 构建安全闸门（2026-11-18 自建后端切流）：
 * release APK 不得包含 staging 明文 IP（121.196.47.2）或非 https 后端地址；
 * 只做配置校验（不构建 APK / 不打分发包），发布前执行：
 *   .\gradlew.bat :app:verifyReleaseEndpoints
 * 预期输出：REST/账号/云空间/更新/地图 5 个地址均为 https://tool.furina.fun/... 且不含 IP。
 */
tasks.register("verifyReleaseEndpoints") {
    group = "verification"
    description = "校验 release 构建的 REST/ECS 地址均为备案后 HTTPS 域名且不含 staging 明文 IP"
    // 用 task inputs 传值而不是闭包捕获脚本对象（兼容 org.gradle.configuration-cache）。
    inputs.property("releaseRestUrl", releaseRestUrl)
    inputs.property("releaseAccountUrl", releaseAccountUrl)
    inputs.property("releaseCloudspaceUrl", releaseCloudspaceUrl)
    inputs.property("releaseUpdateUrl", releaseUpdateUrl)
    inputs.property("releaseMapUrl", releaseMapUrl)
    inputs.property("releaseWebBaseUrl", releaseWebBaseUrl)
    inputs.property("releaseRestProfile", releaseRestProfile)
    inputs.property("debugRestUrl", debugRestUrl)
    inputs.property("debugRestProfile", debugRestProfile)
    doLast {
        val checks = listOf(
            "REST_BASE_URL" to (inputs.properties["releaseRestUrl"] as String),
            "ACCOUNT_BASE_URL" to (inputs.properties["releaseAccountUrl"] as String),
            "CLOUDSPACE_BASE_URL" to (inputs.properties["releaseCloudspaceUrl"] as String),
            "UPDATE_BASE_URL" to (inputs.properties["releaseUpdateUrl"] as String),
            "MAP_BASE_URL" to (inputs.properties["releaseMapUrl"] as String),
            "WEB_BASE_URL" to (inputs.properties["releaseWebBaseUrl"] as String),
        )
        checks.forEach { (name, url) ->
            check(url.startsWith("https://")) { "release $name must be https: $url" }
            check(!url.contains("121.196.47.2")) { "release $name must not contain staging plaintext IP: $url" }
        }
        println("verifyReleaseEndpoints OK (release has no 121.196.47.2 plaintext endpoint)")
        println("  REST_PROFILE=" + (inputs.properties["releaseRestProfile"] as String))
        checks.forEach { (name, url) -> println("  $name=$url") }
        println("  (debug) REST_BASE_URL=" + (inputs.properties["debugRestUrl"] as String) +
            " REST_PROFILE=" + (inputs.properties["debugRestProfile"] as String))
    }
}

kotlin {
    jvmToolchain(17)
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
