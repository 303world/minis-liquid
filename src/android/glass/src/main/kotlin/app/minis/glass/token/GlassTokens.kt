package app.minis.glass.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.minis.glass.highlight.Highlight
import app.minis.glass.shadow.InnerShadow
import app.minis.glass.shadow.Shadow

/**
 * [GLASS] 一档玻璃材质的完整参数。
 *
 * 数值来源：`docs/glass/design-ios-extract.md` 对 iOS 端 `src/ios` 的实扫结果。
 * iOS 只有三档材质（`.glassEffect(.regular)` 9 处 / `.regularMaterial` 12 处 /
 * `.ultraThinMaterial` 36 处）+ 系统工具栏档（`Material.bar` 8 处），这里一一对应。
 */
@Immutable
data class GlassLevel(
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    val tint: Color,
    val highlight: Highlight?,
    val innerShadow: InnerShadow?,
    val shadow: Shadow?,
    val chromaticAberration: Boolean = false,
)

/**
 * [GLASS] 设计令牌。所有可调参数集中在这里 —— 因为本项目**不能在手机上编译**，
 * 每次调整都要走一轮云端构建（~8 分钟），所以必须一次改多个值、一次验多个点。
 */
object GlassTokens {

    /** 发丝描边：iOS 全仓 63 处 0.5pt，是玻璃质感的一半来源。 */
    val Hairline = 0.5.dp

    /**
     * 三档材质。tint 的 alpha 很轻 —— 玻璃的"磨砂感"主要来自 vibrancy + 模糊，
     * 不是靠不透明度堆出来的（iOS 也是这么做的）。
     */
    object Light {
        val Bar = GlassLevel(
            blurRadius = 18.dp,
            refractionHeight = 12.dp,
            refractionAmount = 24.dp,
            tint = Color.White.copy(alpha = 0.10f),
            highlight = Highlight.Default,
            innerShadow = InnerShadow.Default,
            shadow = Shadow.Default,
        )
        val Panel = GlassLevel(
            blurRadius = 14.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0.dp,
            tint = Color.White.copy(alpha = 0.08f),
            highlight = Highlight.Default,
            innerShadow = InnerShadow.Default,
            shadow = Shadow.Default,
        )
        val Overlay = GlassLevel(
            blurRadius = 26.dp,
            refractionHeight = 8.dp,
            refractionAmount = 16.dp,
            tint = Color.White.copy(alpha = 0.12f),
            highlight = Highlight.Ambient,
            innerShadow = InnerShadow.Default,
            shadow = Shadow.Default,
        )
    }

    object Dark {
        val Bar = GlassLevel(
            blurRadius = 18.dp,
            refractionHeight = 12.dp,
            refractionAmount = 24.dp,
            tint = Color.Black.copy(alpha = 0.18f),
            highlight = Highlight.Default,
            innerShadow = InnerShadow.Default,
            shadow = Shadow.Default,
        )
        val Panel = GlassLevel(
            blurRadius = 14.dp,
            refractionHeight = 0.dp,
            refractionAmount = 0.dp,
            tint = Color.Black.copy(alpha = 0.16f),
            highlight = Highlight.Default,
            innerShadow = InnerShadow.Default,
            shadow = Shadow.Default,
        )
        val Overlay = GlassLevel(
            blurRadius = 26.dp,
            refractionHeight = 8.dp,
            refractionAmount = 16.dp,
            tint = Color.Black.copy(alpha = 0.22f),
            highlight = Highlight.Ambient,
            innerShadow = InnerShadow.Default,
            shadow = Shadow.Default,
        )
    }

    fun bar(isDark: Boolean) = if (isDark) Dark.Bar else Light.Bar
    fun panel(isDark: Boolean) = if (isDark) Dark.Panel else Light.Panel
    fun overlay(isDark: Boolean) = if (isDark) Dark.Overlay else Light.Overlay

    /**
     * 品牌沙色 —— iOS 的 FAB 之所以不像"一个蓝色按钮"，就是因为玻璃 tint 成了这个色。
     * 上游 Android 侧完全没有这个颜色，是本次改造唯一新增的色相。
     */
    val BrandSand = Color(0xFFB7AF96)
    val BrandSandDark = Color(0xFFC4B078)
}
