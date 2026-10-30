package app.minis.glass.token

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.minis.glass.highlight.Highlight
import app.minis.glass.shadow.InnerShadow
import app.minis.glass.shadow.Shadow

/**
 * [GLASS] 一档玻璃材质。
 *
 * **数值来源：全部取自参考库自带的组件（不是我们拍脑袋定的）。**
 *  - 条 / 工具条 → `LiquidBottomTabs`：vibrancy + blur(8dp) + lens(24, 24) + Highlight.Default + InnerShadow
 *  - 按钮 / FAB → `LiquidButton`：vibrancy + blur(2dp) + lens(12, 24) + Highlight.Default
 *  - 面板 / 卡片 → 无折射（对应 iOS 的 `.regularMaterial`，iOS 也没给卡片上折射）
 *
 * 注意模糊半径都很小（2~8dp）：玻璃采样的是**内容层**，半径一大内容就糊成一团，
 * 观感立刻变"毛玻璃贴纸"。这一点第一批参数我按 iOS 数值放大到了 18dp，是错的。
 */
@Immutable
data class GlassLevel(
    val blurRadius: Dp,
    val refractionHeight: Dp,
    val refractionAmount: Dp,
    /** 默认不上色；需要品牌色时传具体颜色（参考库用 Hue 混合 + 0.75 alpha 叠在表面）。 */
    val tint: Color = Color.Unspecified,
    val highlight: Highlight? = Highlight.Default,
    val innerShadow: InnerShadow? = null,
    val shadow: Shadow? = Shadow.Default,
    val chromaticAberration: Boolean = false,
)

object GlassTokens {

    /** 发丝描边宽度（iOS 全仓 63 处 0.5pt）。 */
    val Hairline = 0.5.dp

    /** 条 / 工具栏 / 顶栏 —— 照抄 LiquidBottomTabs。 */
    val Bar = GlassLevel(
        blurRadius = 8.dp,
        refractionHeight = 24.dp,
        refractionAmount = 24.dp,
        innerShadow = InnerShadow.Default,
    )

    /** 按钮 / FAB —— 照抄 LiquidButton。 */
    val Button = GlassLevel(
        blurRadius = 2.dp,
        refractionHeight = 12.dp,
        refractionAmount = 24.dp,
        // [GLASS] 圆形玻璃上不要高光：Highlight 走的是低分段的路径，
        // 在 56dp 圆钮上会画出一个明显的八边形（真机已复现）。
        highlight = null,
    )

    /** 面板 / 卡片 —— 无折射。 */
    val Panel = GlassLevel(
        blurRadius = 8.dp,
        refractionHeight = 0.dp,
        refractionAmount = 0.dp,
        innerShadow = InnerShadow.Default,
    )

    /** 浮层（Sheet / 菜单 / 弹窗）—— 沿用条的参数。 */
    val Overlay = Bar

    /**
     * 品牌沙色 —— iOS 的新建会话 FAB 用它上色。
     * 上游 Android 侧没有这个颜色，是本次改造唯一新增的色相。
     */
    val BrandSand = Color(0xFFB7AF96)
    val BrandSandDark = Color(0xFFC4B078)
}
