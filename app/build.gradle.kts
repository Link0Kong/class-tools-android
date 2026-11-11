import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// 本机 release 签名：keystore.properties 存在时启用（该文件与 key/ 目录不进版本库）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.isFile) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.furina.classtools"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.furina.classtools"
        minSdk = 26
        targetSdk = 37
        versionCode = 28
        versionName = "4.12.0"

        // CloudBase REST 根地址与公开 Publishable Key（role=anon，设计上公开）。
        // 值来自 gradle.properties（ct.restBaseUrl / ct.publishableKey），便于部署时替换。
        val restBaseUrl = (project.findProperty("ct.restBaseUrl") as String?)
            ?: "https://tool-d4gosudok46e0e081.api.tcloudbasegateway.com/v1/rdb/rest/"
        val publishableKey = (project.findProperty("ct.publishableKey") as String?).orEmpty()
        buildConfigField("String", "REST_BASE_URL", "\"$restBaseUrl\"")
        buildConfigField("String", "PUBLISHABLE_KEY", "\"$publishableKey\"")
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.window)
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
