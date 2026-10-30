package app.minis.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.minis.glass.effects.blur
import app.minis.glass.effects.vibrancy

/**
 * [GLASS] 顶部「渐变模糊」条：越靠上越糊、越靠下越透明，底部完全消散。
 *
 * 为什么需要它（真机教训）：均匀的一整条模糊在**浅色内容**上根本看不出来 ——
 * 白底糊完还是白底。iOS 和椒盐音乐（SaltUI）那种顶栏之所以有"玻璃感"，
 * 靠的是**渐进式模糊 + 边缘渐隐**，而不是一条均匀的毛玻璃。
 *
 * 实现要点：
 *  - 玻璃层单独放进一个离屏图层（[CompositingStrategy.Offscreen]），这样
 *    `BlendMode.DstIn` 的渐隐蒙版只作用在玻璃上，不会把顶栏文字一起擦掉；
 *  - 模糊半径比普通玻璃大（默认 24dp），因为渐变需要更明显的"糊"才看得出层次；
 *  - 额外叠一层极淡的颜色渐变，模拟 iOS 的 scroll edge effect。
 */
@Composable
fun GlassFadeBar(
    modifier: Modifier = Modifier,
    /** 渐变区总高度。模糊在这个高度内从满强度衰减到 0。 */
    height: Dp = 96.dp,
    /** 模糊半径。渐变模糊比均匀玻璃需要更大的半径才看得出层次。 */
    blurRadius: Dp = 24.dp,
    /** 从顶部开始保持满强度模糊的比例（0~1）。0.35 表示上 1/3 是实的。 */
    solidFraction: Float = 0.35f,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val quality = LocalGlassQuality.current
    val dark = LocalGlassDark.current

    Box(modifier) {
        if (backdrop != null && quality != GlassQuality.Off && GlassCapability.supportsBlur(quality)) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBackdrop(
                        backdrop = backdrop,
                        // 0dp 圆角的 CornerBasedShape：既保持矩形观感，又能让折射着色器算 SDF
                        shape = { RoundedCornerShape(0.dp) },
                        effects = {
                            vibrancy()
                            blur(blurRadius.toPx())
                        },
                        highlight = null,
                        shadow = null,
                        innerShadow = null,
                    )
                    .drawWithContent {
                        drawContent()
                        // 渐隐蒙版：只作用于这块玻璃层
                        drawRect(
                            brush = fadeBrush(dark, solidFraction),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        } else if (backdrop != null) {
            // Lite / 无模糊能力：退化成渐变遮罩，仍有"内容往下淡出"的观感
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(brush = fallbackTint(dark, solidFraction))
                    },
            )
        }
        content()
    }
}

private fun DrawScope.fadeBrush(dark: Boolean, solidFraction: Float): Brush {
    val solid = if (dark) Color.Black else Color.White
    return Brush.verticalGradient(
        0f to Color.Black,                       // alpha=1：满强度
        solidFraction.coerceIn(0.05f, 0.9f) to Color.Black,
        1f to Color.Transparent,                 // alpha=0：完全消散
    )
}

/** 无模糊能力时的兜底：一层极淡的渐变底色，避免顶栏和内容"糊在一起"。 */
private fun DrawScope.fallbackTint(dark: Boolean, solidFraction: Float): Brush {
    val c = if (dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.75f)
    return Brush.verticalGradient(
        0f to c,
        solidFraction to c.copy(alpha = c.alpha * 0.5f),
        1f to Color.Transparent,
    )
}

/** 这些 token 供调用方保持数值一致（顶栏高度等）。 */
object GlassBarMetrics {
    /** 顶栏玻璃的渐变总高度：状态栏 + 标题栏 + 一点余量。 */
    val FadeHeight = 104.dp

    /** 顶栏内容的实际高度（不含状态栏）。 */
    val ContentHeight = 64.dp
}
