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
                    .matchParentSize()
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
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                solidFraction.coerceIn(0.05f, 0.95f) to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
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
