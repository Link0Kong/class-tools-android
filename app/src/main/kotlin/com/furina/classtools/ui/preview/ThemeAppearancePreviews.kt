package com.furina.classtools.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.furina.classtools.data.session.Session
import com.furina.classtools.ui.adaptive.CtAdaptive
import com.furina.classtools.ui.components.ExpressiveBackground
import com.furina.classtools.ui.screens.guide.GuideScene
import com.furina.classtools.ui.screens.guide.GuideSceneArt
import com.furina.classtools.ui.screens.guide.GuideTone
import com.furina.classtools.ui.screens.home.HomeHeroCard
import com.furina.classtools.ui.screens.profile.ProfileScreen
import com.furina.classtools.ui.theme.AppearanceMode
import com.furina.classtools.ui.theme.ClassToolsTheme

/**
 * 外观（跟随系统 / 浅色 / 深色）+ 顶部 hero 深色中性化 的预览。
 *
 * 用于本轮验收：浅色 hero 保持原品牌渐变；深色 hero 改中性 tonal（品牌绿只做小面积 accent），
 * 「我的 → 外观」为 3 个单选行 + 隐藏状态栏/壁纸取色开关。
 *
 * 注意：preview 的 ClassToolsTheme 用 `darkTheme` 显式强制（等价于外观模式选了浅色/深色），
 * 与 `ClassToolsTheme(appearance = ...)` 重载同一解析路径（[AppearanceMode.resolveDark]）。
 */

private val appearancePreviewSession = Session(
    sid = "2024000001",
    name = "陈同学",
    token = "preview-token",
    role = "副团支部书记",
    badge = "班委",
    isAdmin = true,
)

private val appearancePreviewNow = 1763174400000L // 2026-11-15 08:00 +08:00

@Composable
private fun AppearanceFrame(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    ClassToolsTheme(darkTheme = dark) {
        ExpressiveBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun HomeHeroPreview(dark: Boolean) {
    AppearanceFrame(dark = dark) {
        HomeHeroCard(
            session = appearancePreviewSession,
            date = java.time.LocalDate.of(2026, 11, 15),
            plan = null,
            loading = false,
            nowMillis = appearancePreviewNow,
            reduceMotion = true,
            onOpenTimetable = {},
        )
    }
}

@Preview(name = "240 外观 · 工作台 hero（浅色）", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun HomeHeroLightPreview() {
    HomeHeroPreview(dark = false)
}

@Preview(name = "241 外观 · 工作台 hero（深色中性）", showBackground = true, widthDp = 412, heightDp = 240)
@Composable
private fun HomeHeroDarkPreview() {
    HomeHeroPreview(dark = true)
}

@Preview(name = "242 外观 · 使用说明 hero（浅色）", showBackground = true, widthDp = 412, heightDp = 320)
@Composable
private fun GuideHeroLightPreview() {
    AppearanceFrame(dark = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GuideSceneArt(
                scene = GuideScene.PICKS,
                tone = GuideTone.MINT,
                modifier = Modifier.fillMaxWidth(0.5f).height(140.dp),
            )
            GuideSceneArt(
                scene = GuideScene.BALLOT,
                tone = GuideTone.MINT,
                modifier = Modifier.weight(1f).height(140.dp),
            )
        }
    }
}

@Preview(name = "243 外观 · 使用说明 hero（深色中性）", showBackground = true, widthDp = 412, heightDp = 320)
@Composable
private fun GuideHeroDarkPreview() {
    AppearanceFrame(dark = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GuideSceneArt(
                scene = GuideScene.PICKS,
                tone = GuideTone.MINT,
                modifier = Modifier.fillMaxWidth(0.5f).height(140.dp),
            )
            GuideSceneArt(
                scene = GuideScene.BALLOT,
                tone = GuideTone.MINT,
                modifier = Modifier.weight(1f).height(140.dp),
            )
        }
    }
}

@Composable
private fun ProfileAppearancePreview(
    dark: Boolean,
    appearanceMode: AppearanceMode,
    hideStatusBar: Boolean,
) {
    ClassToolsTheme(darkTheme = dark) {
        ProfileScreen(
            session = appearancePreviewSession,
            onSignOut = {},
            adaptiveSpec = CtAdaptive.specFor(412),
            appearanceMode = appearanceMode,
            hideStatusBar = hideStatusBar,
        )
    }
}

@Preview(name = "244 我的 · 外观（浅色 · 强制浅色）", showBackground = true, widthDp = 412, heightDp = 2500)
@Composable
private fun ProfileAppearanceLightPreview() {
    ProfileAppearancePreview(dark = false, appearanceMode = AppearanceMode.LIGHT, hideStatusBar = true)
}

@Preview(name = "245 我的 · 外观（深色 · 强制深色）", showBackground = true, widthDp = 412, heightDp = 2500)
@Composable
private fun ProfileAppearanceDarkPreview() {
    ProfileAppearancePreview(dark = true, appearanceMode = AppearanceMode.DARK, hideStatusBar = false)
}
