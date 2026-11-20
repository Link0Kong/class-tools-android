pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 金标联盟（ITGSA）SharedView Kit 官方 Nexus。目前仅 HTTP（内测阶段部署），
        // 只放行 gsai.sdk，避免其它依赖走不安全仓库。AAR 自带 consumer proguard 规则。
        maven("http://nexus.itgsa.com:5566/repository/sharedViewKit/") {
            isAllowInsecureProtocol = true
            content { includeGroup("gsai.sdk") }
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "ClassToolsAndroid"
include(":app")
include(":baseline-profile")
