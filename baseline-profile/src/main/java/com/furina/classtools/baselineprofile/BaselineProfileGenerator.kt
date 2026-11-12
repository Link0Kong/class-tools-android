package com.furina.classtools.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成班级工具站应用级 Baseline Profile（启动路径冷启动 + 首屏）。
 *
 * 运行：`gradlew :app:generateBaselineProfile`（需要已连接/启动的模拟器，API 33+）。
 * 产物写入 `app/src/main/generated/baselineProfiles/`（saveInSrc=true）。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)
            // 首屏稳定后再采集一小段，覆盖 Compose 首帧与首屏数据绑定。
            Thread.sleep(1_000)
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.furina.classtools"
    }
}
