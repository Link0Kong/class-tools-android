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
        versionCode = 80
        // 测试版本 1：4.57.0-test1（「我的 → 关于」与更新弹窗显示「测试版本 1」）。
        versionName = "1.0.0-beta1"

        // CloudBase REST 根地址与公开 Publishable Key（role=anon，设计上公开）。
        // 值来自 gradle.properties（ct.restBaseUrl / ct.publishableKey），便于部署时替换。
        val restBaseUrl = (project.findProperty("ct.restBaseUrl") as String?)
            ?: "https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/"
        val publishableKey = (project.findProperty("ct.publishableKey") as String?).orEmpty()
        buildConfigField("String", "REST_BASE_URL", "\"$restBaseUrl\"")
        buildConfigField("String", "PUBLISHABLE_KEY", "\"$publishableKey\"")

        // OPPO PUSH 密钥（app/oppo.properties；空串 = 未配置 → 客户端跳过注册/上传）。
        buildConfigField("String", "OPPO_PUSH_APP_KEY", buildConfigString(oppoAppKey))
        buildConfigField("String", "OPPO_PUSH_APP_SECRET", buildConfigString(oppoAppSecret))

        // 测试版本标识（当前为「测试版本 1」）：App 内版本行与更新弹窗标题使用。
        buildConfigField("int", "TEST_VERSION", "1")
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
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 只在 keystore.properties 存在时使用正式签名；缺失时保持未签名（不静默回退 debug 签名，
            // 避免把 debug 签名的包当发布包分发；构建/发布脚本应显式校验）。
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
