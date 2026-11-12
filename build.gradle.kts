// 项目根构建脚本：只声明插件版本，不在此应用。
// AGP 9.x 默认启用 built-in Kotlin：
// - 不再需要 org.jetbrains.kotlin.android 插件；
// - Kotlin 编译版本由声明的 Kotlin Gradle 插件（此处为 Compose 编译器插件 / 序列化插件）决定，
//   因此 compose / serialization 插件必须使用同一个 Kotlin 版本。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}
