package app.minis.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.minis.glass.effects.blur
import app.minis.glass.effects.lens
import app.minis.glass.effects.vibrancy
import app.minis.glass.highlight.Highlight
import app.minis.glass.shadow.InnerShadow
import app.minis.glass.token.GlassTokens

/**
 * [GLASS] 液态玻璃顶栏：真实采样页面内容 + 边缘折射 + 0.5dp 高光 + 垂直渐隐。
 *
 * 为什么不用 Haze：实测在真机上 Haze 的 hazeEffect 完全没有渲染（文字一直清晰到被裁
 * 掉的位置）。本项目自己移植的 backdrop（Backdrop/DrawBackdropModifier）在 demo 里
 * 验证过确实能出效果，所以顶栏也走这套。
 *
 * 三条叠加才像"液态玻璃"：
 *  1. **折射（lens）**：内容在边缘被压弯 —— 这是和"磨砂"最本质的区别；
 *  2. **高光（Highlight）**：0.5dp 亮边，玻璃的"厚度"来自它；
 *  3. **垂直渐隐**：往下逐渐消散，标题区不糊成一片（对齐 SaltUI 的观感）。
 */
@Composable
fun GlassLiquidBar(
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    /** 模糊带高度：只覆盖标题**下方**这一条，标题本身保持清晰（可读性关键）。 */
    blurBand: Dp = 84.dp,
    /** 模糊半径。参考库的条只用 8dp —— 半径一大内容就糊成一团。 */
    blurRadius: Dp = 8.dp,
    refractionHeight: Dp = 24.dp,
    refractionAmount: Dp = 24.dp,
    solidFraction: Float = 0.45f,
    dark: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val quality = LocalGlassQuality.current

    Box(modifier) {
        if (backdrop != null && quality != GlassQuality.Off) {
            Box(
                Modifier
                    // [GLASS] 关键：玻璃只画在标题下方的一条带子里，而不是整块顶栏。
                    // 椒盐音乐的可读性来自这一点 —— 标题所在的区域本身不糊。
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(blurBand)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBackdrop(
                        backdrop = backdrop,
                        // 0dp 圆角的 CornerBasedShape：折射要用 SDF，RectangleShape 会抛异常
                        shape = { RoundedCornerShape(0.dp) },
                        effects = {
                            vibrancy()
                            if (GlassCapability.supportsBlur(quality)) {
                                blur(blurRadius.toPx())
                            }
                            if (GlassCapability.supportsRefraction(quality)) {
                                lens(
                                    refractionHeight = refractionHeight.toPx(),
                                    refractionAmount = refractionAmount.toPx(),
                                )
                            }
                        },
                        // [GLASS] 顶栏是"贴边的渐变模糊"，不是独立的玻璃块：
                        // 高光/内阴影是给 FAB、菜单、卡片那种"浮起来的玻璃"用的。
                        // 顶栏加高光会看到一条假边线（主人指出的问题）。
                        highlight = null,
                        shadow = null,
                        innerShadow = null,
                        onDrawSurface = {
                            drawRect(
                                if (dark) Color.Black.copy(alpha = 0.10f)
                                else Color.White.copy(alpha = 0.10f),
                            )
                        },
                    )
                    .drawWithContent {
                        drawContent()
                        // 垂直渐隐：只遮玻璃层，不影响顶栏文字
                        // 带内：上实下虚，1 → 0
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                0.35f to Color.Black.copy(alpha = 0.75f),
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        // [GLASS] 顶部不透明渐隐：状态栏/标题所在的区域不应该直接露出裸内容
        // （之前"上面缺一块"就是这里没底）。
        Box(
            Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .fillMaxWidth()
                .height(height - blurBand)
                .background(
                    Brush.verticalGradient(
                        0f to (if (dark) Color.Black.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f)),
                        1f to (if (dark) Color.Black.copy(alpha = 0f) else Color.White.copy(alpha = 0f)),
                    ),
                ),
        )
        content()
    }
}

/** 顶栏尺寸常量，供调用方与列表内边距对齐。 */
object GlassBarMetrics {
    val ChatBarHeight = 140.dp
    val ListBarHeight = 132.dp
}

/** 供 :app 侧引用的默认档位（避免调用方硬编码能力判断）。 */
val DefaultBarLevel get() = GlassTokens.Bar
